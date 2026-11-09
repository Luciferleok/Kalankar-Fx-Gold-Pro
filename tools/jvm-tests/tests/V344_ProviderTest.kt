import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean) { if (ok) pass++ else { fail++; println("FAIL: $name") } }

fun main() {
    fun rec(id: String, src: String) = LedgerRecord(id = id, createdAt = 1L, expiresAt = 2L, interval = "5m", horizonMin = 25, barId = "b$id", price = 4000.0,
        rawSignal = Signal.SELL, finalSignal = Signal.SELL, confidence = 70, atr = 1.0, slDistance = 3.0, regime = "", session = "", newsActive = false,
        modelVersion = "", appliedFilter = "", sources = emptyMap(), dataSource = src)
    fun res(id: String, o: LedgerOutcome, plan: String = "") = LedgerResult(id, 3L, o, "x", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0L, 0.0, emptyList(), emptyMap(), emptyList(), "", emptyList(), planOutcome = plan)
    val recs = ArrayList<LedgerRecord>(); val results = HashMap<String, LedgerResult>()
    // OANDA: 40 decided (26 right), 10 plan entered (6 TP1)
    repeat(26) { recs += rec("o$it", GoldApiService.OANDA_SOURCE); results["o$it"] = res("o$it", LedgerOutcome.CORRECT, if (it < 6) "TP1" else "") }
    repeat(14) { recs += rec("ow$it", GoldApiService.OANDA_SOURCE); results["ow$it"] = res("ow$it", if (it % 2 == 0) LedgerOutcome.INCORRECT else LedgerOutcome.INVALIDATED, if (it < 4) "STOP" else "") }
    // not counted: WAIT, sideways, uncertain, unchecked
    recs += rec("os", GoldApiService.OANDA_SOURCE); results["os"] = res("os", LedgerOutcome.SIDEWAYS)
    recs += rec("ou", GoldApiService.OANDA_SOURCE); results["ou"] = res("ou", LedgerOutcome.VERIFICATION_UNCERTAIN, "NO_ENTRY")
    recs += rec("op", GoldApiService.OANDA_SOURCE)
    // Twelve Data: 5 decided (4 right)
    repeat(4) { recs += rec("t$it", "Twelve Data XAU/USD"); results["t$it"] = res("t$it", LedgerOutcome.CORRECT) }
    recs += rec("tw", "Twelve Data XAU/USD"); results["tw"] = res("tw", LedgerOutcome.INVALIDATED)
    // legacy
    recs += rec("l1", ""); results["l1"] = res("l1", LedgerOutcome.CORRECT)
    val lines = LedgerPulseBuilder.providerLines(LedgerState(recs, results, emptyList()))
    println(lines.joinToString("\n"))
    val o = lines.single { it.startsWith("OANDA") }; val t = lines.single { it.startsWith("Twelve Data") }
    check("OANDA v1: 65% of 40, trusted", o.contains("v1 direction right 65% of 40") && !o.substringBefore(" · v2").contains("too few"))
    check("OANDA v2: 60% of 10, too few", o.contains("v2 TP1 before stop 60% of 10 (too few to trust)"))
    check("Twelve Data separate: 80% of 5, too few", t.contains("v1 direction right 80% of 5 (too few to trust)") && t.contains("v2 TP1 before stop: none yet"))
    check("legacy separate", lines.any { it.startsWith("older app version") && it.contains("100% of 1") })
    check("providers never mixed (no combined line)", lines.size == 3)
    check("nothing recorded -> no lines", LedgerPulseBuilder.providerLines(LedgerState(emptyList(), emptyMap(), emptyList())).isEmpty())
    val src = File("app/src/main/java/com/example/livegoldai")
    check("shown on SYSTEM", File(src, "ui/v8/V8HealthScreen.kt").readText().contains("p.byProvider.forEach"))
    check("learning untouched", listOf("data/LearningEngine.kt", "data/ShadowLive.kt", "data/ShadowLab.kt").none { File(src, it).readText().contains("providerLines") })
    println("V34.4 provider accuracy: $pass passed, $fail failed")
    if (fail > 0) System.exit(1)
}
