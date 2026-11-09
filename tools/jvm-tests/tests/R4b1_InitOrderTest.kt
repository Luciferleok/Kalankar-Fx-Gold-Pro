import java.io.File

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean) { if (ok) pass++ else { fail++; println("FAIL: $name") } }

/**
 * V33.1R.4-b.1: GoldViewModel's init calls loadData() synchronously. Kotlin initializes properties in source order, so
 * any property used by init or by loadData's synchronous start that is declared AFTER init is still null -> crash at
 * app start (fcc9675). This scans the real source.
 */
fun main(args: Array<String>) {
    val vm = File(args.getOrElse(0) { "app/src/main/java/com/example/livegoldai/ui/GoldViewModel.kt" }).readText()
    val initAt = vm.indexOf("\n    init {")
    check("init block found", initAt > 0)
    val initEnd = vm.indexOf("\n    }\n", initAt)
    val initBody = vm.substring(initAt, initEnd)
    val ldAt = vm.indexOf("fun loadData(isInitial: Boolean = false) {")
    // synchronous part of loadData = up to the first suspension (the network call)
    val ldSync = vm.substring(ldAt, vm.indexOf("apiService.fetchAnalysis(", ldAt))
    val after = Regex("\n    (?:private |internal )?(?:val|var) (\\w+)(?!\\s+get\\(\\))").findAll(vm).filter { it.range.first > initAt }.map { it.groupValues[1] }.toList()
    for (name in after) {
        val used = Regex("\\b$name\\b").containsMatchIn(initBody) || Regex("\\b$name\\b").containsMatchIn(ldSync)
        check("'$name' is declared after init but used during init/loadData start", !used)
    }
    check("loadGate declared before init", vm.indexOf("private val loadGate") in 1 until initAt)
    check("frozenLevels declared before init", vm.indexOf("private val frozenLevels") in 1 until initAt)
    println("R.4-b.1 init order: $pass passed, $fail failed")
    if (fail > 0) System.exit(1)
}
