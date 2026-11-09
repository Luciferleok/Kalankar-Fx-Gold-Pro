import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
fun t(s: String) = sdf.parse(s)!!.time
const val MIN = 60_000L
fun rec(id: String, iv: String, created: Long, exp: Long, session: String = "LONDON", eng: String = "E22.0") = LedgerRecord(
    id = id, createdAt = created, expiresAt = exp, interval = iv, horizonMin = ((exp - created) / MIN).toInt(), barId = "b$id", price = 4000.0,
    rawSignal = Signal.BUY, finalSignal = Signal.BUY, confidence = 70, atr = 2.0, slDistance = 3.0, regime = "TREND", session = session, newsActive = false,
    modelVersion = "", appliedFilter = "", sources = emptyMap(), dataSource = GoldApiService.OANDA_SOURCE, engineVersion = eng)
fun res(id: String, ok: Boolean) = LedgerResult(id, 1L, if (ok) LedgerOutcome.CORRECT else LedgerOutcome.INCORRECT, "x", 4000.0, 4000.0, 0.0, 1.0, 0.0, 0.0, 0L, 0.0, emptyList(), emptyMap(), emptyList(), "", emptyList())

fun main() {
    // 1. ONE canonical decision: the playbook never says BUY/SELL while the engine says WAIT
    var mismatch = 0; var lean = 0; var calls = 0; var bad = ""
    for (seed in 1L..3000L) {
        val r = Random(seed); var p = 4000.0; val base = t("2026-11-02 00:00:00")
        val candles = (0 until 150).map { k -> val o = p; p += r.nextGaussian() * 2 + 0.15; CandleBar(sdf.format(Date(base + k * 5 * MIN)), o, maxOf(o, p) + r.nextDouble(), minOf(o, p) - r.nextDouble(), p) }
        val a = TechnicalEngine.analyze(candles, "5m", nowMs = base + 150 * 5 * MIN)
        val pv = a.nextPrediction?.verdict
        if (pv != a.overallSignal) { mismatch++; if (bad.isEmpty()) bad = "seed $seed engine ${a.overallSignal} playbook $pv" }
        if (a.overallSignal == Signal.WAIT && (a.buyCount >= 4 || a.sellCount >= 4)) { lean++; if (a.nextPrediction?.urgencyTag?.contains("RESEARCH LEAN") != true) mismatch++ }
        if (a.overallSignal != Signal.WAIT) calls++
    }
    println("3000 charts: $calls BUY/SELL, $lean research leans, $mismatch mismatches")
    check("playbook direction == engine decision on 3000 charts", mismatch == 0, bad)
    check("4-pillar leans below the gate are named RESEARCH LEAN inside WAIT", lean > 0)
    check("BUY/SELL calls still produced (gate unchanged)", calls > 0)
    // gate itself unchanged: weights and 68%
    check("weights unchanged", TechnicalEngine.PILLAR_WEIGHTS == mapOf("trend" to 3.0, "smc" to 3.0, "macro" to 2.5, "sr" to 2.0, "candlestick" to 2.0, "momentum" to 1.5, "volatility" to 1.5))
    // 6-pillar denominator
    run {
        val r = Random(7); var p = 4000.0; val base = t("2026-11-02 00:00:00")
        var ok = true; var n = 0
        for (s in 1L..400L) {
            val rr = Random(s); p = 4000.0
            val candles = (0 until 150).map { k -> val o = p; p += rr.nextGaussian() * 2 + 0.3; CandleBar(sdf.format(Date(base + k * 5 * MIN)), o, maxOf(o, p) + rr.nextDouble(), minOf(o, p) - rr.nextDouble(), p) }
            val a = TechnicalEngine.analyze(candles, "5m", nowMs = base + 150 * 5 * MIN)
            if (a.overallSignal == Signal.WAIT) continue
            n++
            val raw = TechnicalEngine.analyze(candles, "5m", nowMs = base + 150 * 5 * MIN).nextPrediction!!.winProbabilityPercent
            if (raw != Math.round(maxOf(a.buyCount, a.sellCount) * 100.0 / a.totalGroups).toInt() || a.totalGroups != 6) ok = false
        }
        check("Macro OFF: agreement share uses the 6 voting pillars ($n calls)", ok && n > 0)
    }
    val src = File("app/src/main/java/com/example/livegoldai")
    val all = src.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
    check("no '5 of 7' / '/7 Agree' / '7 pillars' wording left", !all.contains("5 of 7") && !all.contains("/7 Agree") && !all.contains("the 7 pillars") && !all.contains("7 में से 5") && !all.contains("7 पैकी 5"))

    // 2. EdgeFinder honesty
    val rnd = Random(5); val recs = ArrayList<LedgerRecord>(); val rs = HashMap<String, LedgerResult>(); var i = 0
    repeat(300) { val c = i++ * 2 * 3_600_000L; val r = rec("a$i", "15m", c, c + 3_600_000L, "LONDON"); recs += r; rs[r.id] = res(r.id, rnd.nextDouble() < 0.68) }
    repeat(60) { val c = i++ * 2 * 3_600_000L; val r = rec("n$i", "15m", c, c + 3_600_000L, "NEW_YORK"); recs += r; rs[r.id] = res(r.id, rnd.nextDouble() < 0.40) }
    val st = LedgerState(recs, rs, emptyList())
    val gs = EdgeFinder.groups(st)
    check("no verdict is called PROVEN any more", gs.none { it.verdict.contains("PROVEN") } && gs.none { it.line().contains("PROVEN EDGE") })
    check("a strong group is HISTORICAL EDGE and says not forward-validated", gs.first { it.dimension == "Timeframe" }.let { it.verdict == EdgeFinder.HIST && it.line().contains("not forward-validated") })
    val base = t("2026-11-01 00:00:00")
    val candles = (0 until 150).map { k -> val o = 4000.0 + k * 0.5; CandleBar(sdf.format(Date(base + k * 15 * MIN)), o, o + 0.6, o - 0.4, o + 0.45) }
    val now = t("2026-11-02 18:10:00")   // New York session (16-20 UTC)
    val a = TechnicalEngine.analyze(candles, "15m", nowMs = now).copy(overallSignal = Signal.BUY, feed = FeedStatus(GoldApiService.OANDA_SOURCE, now, 1, true, true, true, 0, 1))
    val line = EdgeFinder.forForecast(a, st, now)
    println("forecast line: $line")
    check("contradicting matching group -> MIXED EVIDENCE, both shown", line.startsWith("MIXED EVIDENCE") && line.contains("Session NEW_YORK"))
    val onlyGood = LedgerState(recs.filter { it.session == "LONDON" }, rs, emptyList())
    check("no contradiction -> HISTORICAL GROUP EVIDENCE (never PROVEN)", EdgeFinder.forForecast(a, onlyGood, now).startsWith("HISTORICAL GROUP EVIDENCE"))
    check("tiny ledger -> INSUFFICIENT SAMPLE", EdgeFinder.forForecast(a, LedgerState(recs.take(10), rs, emptyList()), now).startsWith("INSUFFICIENT SAMPLE"))
    // cross-timeframe overlap: 5m + 15m on the same moment are ONE piece of evidence in pooled groups
    val o5 = rec("x5", "5m", 0, 25 * MIN); val o15 = rec("x15", "15m", 0, 60 * MIN)
    val pooled = EdgeFinder.groups(LedgerState(listOf(o5, o15), mapOf("x5" to res("x5", true), "x15" to res("x15", true)), emptyList()))
    check("pooled groups count one call per moment across timeframes", pooled.first { it.dimension == "Side" }.n == 1 && pooled.filter { it.dimension == "Timeframe" }.sumOf { it.n } == 2)
    // engine versions separated
    val eng = EdgeFinder.groups(LedgerState(listOf(o5.copy(id = "e0", engineVersion = "E22.0"), o5.copy(id = "e1", createdAt = 99 * MIN, expiresAt = 124 * MIN, engineVersion = "E22.1")),
        mapOf("e0" to res("e0", true), "e1" to res("e1", false)), emptyList())).filter { it.dimension == "Engine" }
    check("Engine dimension splits E22.0 and E22.1", eng.map { it.value }.toSet() == setOf("E22.0", "E22.1"))
    val sum = EdgeFinder.summary(st)
    check("summary: current engine line and forward-validation disclaimer", sum.any { it.startsWith("Current engine ${PredictionLedger.ENGINE_VERSION}") } && sum.last().contains("none is forward-validated"))
    // screens follow the new labels
    check("HOME colour + Passport use the new labels", File(src, "ui/v8/V8CockpitScreen.kt").readText().contains("startsWith(\"HISTORICAL GROUP EVIDENCE\")") &&
        File(src, "data/TrustPassport.kt").readText().contains("startsWith(\"HISTORICAL GROUP EVIDENCE\")"))
    println("V35.3-A canonical + honest evidence: $pass passed, $fail failed")
    if (fail > 0) kotlin.system.exitProcess(1)
}
