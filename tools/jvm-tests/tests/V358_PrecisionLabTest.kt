import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File
import java.util.*

// V35.8 Precision Lab (shadow only) + Why Wrong. SYNTHETIC FIXTURES ONLY.
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
const val M = 60_000L
fun rec(id: String, at: Long, iv: String = "5m", conf: Int = 75, regime: String = "TRANSITION", news: Boolean = false, ch: Map<String, Signal>? = null) =
    LedgerRecord(id = id, createdAt = at, expiresAt = at + 25 * M, interval = iv, horizonMin = 25, barId = id, price = 4000.0, rawSignal = Signal.BUY, finalSignal = Signal.BUY,
        confidence = conf, atr = 1.0, slDistance = 1.0, regime = regime, session = "S", newsActive = news, modelVersion = "", appliedFilter = "",
        sources = (ch ?: emptyMap()) + ("grp:trend" to Signal.BUY), dataSource = GoldApiService.OANDA_SOURCE, engineVersion = "E22.1")
fun res(id: String, ok: Boolean, tags: List<String> = emptyList()) = LedgerResult(id, 1L, if (ok) LedgerOutcome.CORRECT else LedgerOutcome.INCORRECT, "x", 4000.0, 4001.0,
    if (ok) 2.0 else -2.0, 0.5, 2.0, 1.0, 0L, 0.0, tags, emptyMap(), emptyList(), "", emptyList())

fun main() {
    // ---- rules
    val d = { side: Signal, a: Int, st: Double, reg: String, news: Boolean, iv: String -> PrecisionLab.decide(side, a, st, reg, news, iv) }
    check("production WAIT stays WAIT", d(Signal.WAIT, 99, 9.0, "TREND_UP", false, "1h").values.all { it == Signal.WAIT })
    check("LATE: 85% and 1.5 ATR -> WAIT", d(Signal.BUY, 85, 1.5, "TREND_UP", false, "1h")[PrecisionLab.LATE] == Signal.WAIT)
    check("LATE: 84% keeps", d(Signal.BUY, 84, 3.0, "TREND_UP", false, "1h")[PrecisionLab.LATE] == Signal.BUY)
    check("LATE: 1.49 ATR keeps", d(Signal.SELL, 100, 1.49, "TREND_UP", false, "1h")[PrecisionLab.LATE] == Signal.SELL)
    check("QUIET: RANGE / NEWS -> WAIT, trend keeps", d(Signal.BUY, 70, 0.0, "RANGE", false, "1h")[PrecisionLab.QUIET] == Signal.WAIT &&
        d(Signal.BUY, 70, 0.0, "TRANSITION", true, "1h")[PrecisionLab.QUIET] == Signal.WAIT && d(Signal.BUY, 70, 0.0, "TREND_UP", false, "1h")[PrecisionLab.QUIET] == Signal.BUY)
    check("SHORT: 2m-4m -> WAIT, 1m and 5m keep", listOf("2m", "3m", "4m").all { d(Signal.BUY, 70, 0.0, "TREND_UP", false, it)[PrecisionLab.SHORT] == Signal.WAIT } &&
        listOf("1m", "5m").all { d(Signal.BUY, 70, 0.0, "TREND_UP", false, it)[PrecisionLab.SHORT] == Signal.BUY })
    val bars = (0 until 20).map { CandleBar("t$it", 100.0, 100.0, 100.0, if (it == 19) 103.8 else 100.0) }
    check("stretch = |last - mean20| / ATR", Math.abs(PrecisionLab.stretch(bars, 2.0) - (3.8 - 3.8 / 20) / 2.0) < 1e-9 && PrecisionLab.stretch(bars.take(10), 2.0) == 0.0)

    // ---- evaluation: forward only, same calls, one per window
    fun chFor(i: Int) = d(Signal.BUY, if (i % 3 == 0) 90 else 72, if (i % 3 == 0) 2.0 else 0.5, "TRANSITION", false, "5m")
    val recs = ArrayList<LedgerRecord>(); val out = HashMap<String, LedgerResult>()
    for (i in 0 until 40) { val x = rec("old$i", i * 30 * M); recs += x; out[x.id] = res(x.id, false) }             // old history: no "ch:" keys
    for (i in 0 until 240) {
        val x = rec("n$i", (100 + i) * 30 * M, ch = chFor(i)); recs += x
        out[x.id] = res(x.id, if (i % 3 == 0) i % 10 < 3 else i % 10 < 7)                                               // late calls mostly wrong
    }
    recs += rec("overlap", (100 + 5) * 30 * M + 5 * M, ch = chFor(1)); out["overlap"] = res("overlap", true)             // inside a kept window
    val st = LedgerState(recs, out, emptyList())
    val stats = PrecisionLab.evaluate(st).associateBy { it.key }
    val late = stats.getValue(PrecisionLab.LATE)
    check("old calls (no ch: key) left out, one call per window", late.prodN == 240, "${late.prodN}")
    check("kept / skipped split", late.skippedN == 80 && late.keptN == 160, "${late.keptN}/${late.skippedN}")
    check("late challenger PROMISING (kept 70%, skipped 30%, app 57%)", late.verdict == "PROMISING" && late.keptHit == 70 && late.skippedHit == 30 && late.prodHit == 57, "$late")
    check("95% interval around kept", late.keptLow in 60..69 && late.keptHigh in 71..80, "${late.keptLow}-${late.keptHigh}")
    check("coverage", late.coveragePct == 66)
    val quiet = stats.getValue(PrecisionLab.QUIET)
    check("nothing skipped -> COLLECTING", quiet.skippedN == 0 && quiet.verdict == "COLLECTING")
    val few = PrecisionLab.evaluate(LedgerState(recs.filter { it.id.startsWith("n") }.take(30), out, emptyList())).first { it.key == PrecisionLab.LATE }
    check("small sample -> COLLECTING", few.verdict == "COLLECTING")
    // a challenger that skips the GOOD calls is WORSE
    val bad = (0 until 240).map { i -> rec("b$i", i * 30 * M, ch = mapOf(PrecisionLab.LATE to if (i % 3 == 0) Signal.WAIT else Signal.BUY)) }
    val badOut = bad.mapIndexed { i, x -> x.id to res(x.id, if (i % 3 == 0) i % 10 < 8 else i % 10 < 5) }.toMap()
    check("skipping good calls -> WORSE", PrecisionLab.evaluate(LedgerState(bad, badOut, emptyList())).first { it.key == PrecisionLab.LATE }.verdict == "WORSE")
    check("Wilson 55/100 ≈ 45-64", PrecisionLab.wilson(55, 100) == (45 to 64), "${PrecisionLab.wilson(55, 100)}")

    // ---- the shown call never changes; challengers not counted as sources
    val snap = LearningEngine.snapshot(st, "5m", 400 * 30 * M, emptyList())
    check("snapshot carries the lab (V35.14: 5 challengers)", snap.precision.size == 5 && snap.whyWrong != null)
    check("challengers not listed as signal sources", snap.bySource.none { it.key.startsWith("ch:") })
    check("records keep production BUY even when a challenger says WAIT", recs.filter { it.sources[PrecisionLab.LATE] == Signal.WAIT }.all { it.finalSignal == Signal.BUY })
    val lc = File("app/src/main/java/com/example/livegoldai/data/LearningCoordinator.kt").readText()
    check("decided when the record is made (stored in sources)", lc.contains("sources.putAll(PrecisionLab.decide(if (filter.isNotEmpty()) Signal.WAIT else rawSig"))

    // ---- why wrong
    val wr = listOf(rec("w1", 0, conf = 90, regime = "RANGE"), rec("w2", 30 * M, conf = 90), rec("w3", 60 * M, conf = 60, news = true), rec("w4", 90 * M, conf = 72))
    val wo = mapOf("w1" to res("w1", false, listOf("OVERCONFIDENCE", "RANGE_WHIPSAW")), "w2" to res("w2", false, listOf("OVERCONFIDENCE")),
        "w3" to res("w3", false, listOf("NEWS_SHOCK", "UNKNOWN")), "w4" to res("w4", true))
    val why = PrecisionLab.whyWrong(LedgerState(wr, wo, emptyList()))!!
    check("counts", why.decided == 4 && why.wrong == 3)
    check("top cause = high agreement, share of wrong calls", why.causes.first().let { it.tag == "OVERCONFIDENCE" && it.count == 2 && it.pctOfWrong == 67 && it.label.startsWith("High agreement") })
    check("UNKNOWN tag not shown", why.causes.none { it.tag == "UNKNOWN" })
    check("slices by agreement / market type / news / timeframe", why.slices.map { it.group }.toSet() == setOf("Agreement", "Market type", "News", "Timeframe") &&
        why.slices.first { it.group == "Agreement" && it.name == "85%+" }.let { it.n == 2 && it.hit == 0 })
    check("empty ledger: no card", PrecisionLab.whyWrong(LedgerState(emptyList(), emptyMap(), emptyList())) == null)
    println("V35.8 precision lab: $pass passed, $fail failed")
}
