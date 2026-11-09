import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean) { if (ok) pass++ else { fail++; println("FAIL: $name") } }

fun main() {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val now = sdf.parse("2026-10-28 10:22:10")!!.time; val iv = 300_000L
    val rnd = Random(3); var p = 4100.0
    val candles = (0 until 150).map { i -> val o = p; p -= 0.35 + (rnd.nextDouble() - 0.5) * 1.2; CandleBar(sdf.format(Date((now / iv) * iv - (149 - i) * iv)), o, maxOf(o, p) + 0.6, minOf(o, p) - 0.6, p) }
    val td = FeedStatus("Twelve Data XAU/USD", now - 5_000, 300, true, true, true, 0, 1)
    val empty = LedgerState(emptyList(), emptyMap(), emptyList())
    fun an(i: String = "5m", f: FeedStatus = td, cs: List<CandleBar> = candles) = TechnicalEngine.analyze(cs, i).copy(feed = f, sourceState = SourceStateMachine.PRIMARY_ACTIVE)

    // ---- C: 18 live windows (9 timeframes x 2 providers) all kept; restart keeps the first one
    val ivs = listOf("1m", "2m", "3m", "4m", "5m", "10m", "15m", "30m", "1h")
    val feeds = listOf(td, td.copy(source = GoldApiService.OANDA_SOURCE))
    val store = mutableMapOf<String, LevelFreeze.Frozen>(); val keys = ArrayList<String>(); var n = 0
    for (fd in feeds) for (i in ivs) { val s = PlanStamp.stamp(an(i, fd), empty, now, "COLLECTING"); LevelFreeze.key(s)?.let { keys += it; LevelFreeze.apply(s, store, now + n++) } }
    check("18 live windows: none evicted", keys.size > 16 && keys.all { it in store.keys })
    // expired windows are dropped first
    val late = keys.size
    val farFuture = PlanStamp.stamp(an("4h"), empty, now, "COLLECTING")
    val oneMin = store.values.minByOrNull { LevelFreeze.endOf(it) }!!
    LevelFreeze.apply(farFuture, store, LevelFreeze.endOf(oneMin) + 1)
    check("expired windows removed when a new one is added", oneMin.key !in store.keys)
    // above CAP: the window ending soonest goes, never a later one
    val big = mutableMapOf<String, LevelFreeze.Frozen>()
    val base = PlanStamp.stamp(an(), empty, now, "COLLECTING")
    for (k in 0 until LevelFreeze.CAP + 5) LevelFreeze.apply(base.copy(interval = "x$k", tradeSetup = base.tradeSetup.copy(snapshotId = "S-x$k", expiresAtMs = now + 3_600_000L + k * 1000L)), big, now)
    check("never above CAP", big.size <= LevelFreeze.CAP)
    check("soonest-ending dropped first", big.values.none { it.setup.snapshotId == "S-x0" } && big.values.any { it.setup.snapshotId == "S-x${LevelFreeze.CAP + 4}" })

    // ---- E: grant fails closed when the scenario changed under the same snapshot id
    val act = PlanStamp.stamp(an(), empty, now, "LIVE QUALIFIED").let { it.copy(presentation = PresentationBuilder.build(it, empty, "LIVE QUALIFIED", now)) }
    val g = ResearchGate.authorize(act, act.tradeSetup.snapshotId, act.interval, now).grant
    check("fixture: grant", g != null)
    if (g != null) {
        check("unchanged scenario: grant valid", ResearchGate.stillValid(g, act, now + 1_000))
        val t = act.tradeSetup
        check("entry changed -> invalid", !ResearchGate.stillValid(g, act.copy(tradeSetup = t.copy(entryPrice = t.entryPrice + 0.01)), now + 1_000))
        check("stop changed -> invalid", !ResearchGate.stillValid(g, act.copy(tradeSetup = t.copy(stopLoss = t.stopLoss + 0.01)), now + 1_000))
        check("TP1 changed -> invalid", !ResearchGate.stillValid(g, act.copy(tradeSetup = t.copy(takeProfit1 = t.takeProfit1 - 0.01)), now + 1_000))
        check("TP2 changed -> invalid", !ResearchGate.stillValid(g, act.copy(tradeSetup = t.copy(takeProfit2 = t.takeProfit2 - 0.01)), now + 1_000))
        check("expiry changed -> invalid", !ResearchGate.stillValid(g, act.copy(tradeSetup = t.copy(expiresAtMs = t.expiresAtMs + 60_000)), now + 1_000))
        check("null analysis -> invalid", !ResearchGate.stillValid(g, null, now))
    }

    // ---- B: not-saved note in all languages, only when not saved
    val shown = LevelFreeze.apply(PlanStamp.stamp(an(), empty, now, "COLLECTING"), mutableMapOf(), now)
    val ok = ResearchScenario.of(shown, now)
    val notSaved = ResearchScenario.of(shown.copy(tradeSetup = shown.tradeSetup.copy(levelsSaved = false)), now)
    if (ok != null && notSaved != null) {
        check("saved: no note", !ResearchScenario.steps(ok, AppLanguage.ENGLISH).contains("Not saved"))
        for (l in AppLanguage.values()) check("not saved: note $l", ResearchScenario.steps(notSaved, l) != ResearchScenario.steps(ok, l))
    }
    val vm = File("app/src/main/java/com/example/livegoldai/ui/GoldViewModel.kt").readText()
    val initAt = vm.indexOf("\n    init {")
    check("VM: save result kept and retried", vm.contains("if (frozenLevels.keys != before || freezeSaveFailed) freezeSaveFailed = !levelStore.save(frozenLevels)"))
    check("VM: plan marked not saved", vm.contains("out.tradeSetup.copy(levelsSaved = false)"))
    check("VM: flag declared before init", vm.indexOf("private var freezeSaveFailed") in 1 until initAt)
    // simulate: save fails, then succeeds on the next refresh
    val dirF = File(System.getProperty("java.io.tmpdir"), "kfx_fix1_" + System.nanoTime()).apply { mkdirs() }
    val blocker = File(dirF, "lf.tsv").apply { mkdirs(); File(this, "x").writeText("x") }      // destination is a directory -> save fails
    val m = mutableMapOf<String, LevelFreeze.Frozen>(); LevelFreeze.apply(shown, m, now)
    var failed = !LevelFreezeStore(blocker).save(m)
    check("save failure detected", failed)
    blocker.deleteRecursively()
    failed = !LevelFreezeStore(blocker).save(m)
    check("retry succeeds and restart sees the window", !failed && LevelFreezeStore(blocker).load(now).size == 1)
    dirF.deleteRecursively()

    println("V34.0-fix1: $pass passed, $fail failed")
    if (fail > 0) System.exit(1)
}
