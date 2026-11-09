import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// V35.11 one-line verdict. SYNTHETIC FIXTURES.
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
const val M = 60_000L
val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
val empty = LedgerState(emptyList(), emptyMap(), emptyList())
fun make(now: Long, mode: String, pred: (GoldAnalysisResult) -> Boolean): GoldAnalysisResult? {
    val iv = 300_000L
    for (seed in 1L..400L) {
        val rnd = Random(seed); var p = 4100.0; val drift = if (seed % 2 == 0L) 0.0 else 0.5
        val candles = (0 until 150).map { i -> val o = p; p += drift + (rnd.nextDouble() - 0.5) * (if (seed % 2 == 0L) 4.0 else 1.0); CandleBar(sdf.format(Date((now / iv) * iv - (149 - i) * iv)), o, maxOf(o, p) + 0.6, minOf(o, p) - 0.6, p) }
        val td = FeedStatus("Twelve Data XAU/USD", now - 5_000, 300, true, true, true, 0, 1)
        val raw = TechnicalEngine.analyze(candles, "5m").copy(feed = td, sourceState = SourceStateMachine.PRIMARY_ACTIVE)
        val a = PlanStamp.stamp(raw, empty, now, mode).let { it.copy(presentation = PresentationBuilder.build(it, empty, mode, now)) }.let { it.copy(insights = CockpitInsightsBuilder.build(it)) }
        if (pred(a)) return a
    }
    return null
}
fun main() {
    val now = sdf.parse("2026-10-28 10:22:10")!!.time
    val E = AppLanguage.ENGLISH
    val buy = make(now, "COLLECTING") { it.overallSignal == Signal.BUY && it.tradeSetup.planStatus == PlanStamp.RESEARCH }!!
    OneLineVerdict.build(buy, now, E).let { v ->
        val votes = buy.groups.count { it.active && it.verdict == Signal.BUY }; val n = buy.groups.count { it.active }
        check("BUY research headline", v.headline == "BUY (research only, not a trade): $votes of $n pillars point up", v.headline)
        check("BUY detail: valid until + record", v.detail.startsWith("valid until ") && v.detail.contains("5M record: no checked call yet"), v.detail)
        check("research is flagged", v.warn) }
    val idx = buy.groups.indexOfFirst { it.active && it.verdict == Signal.BUY }
    val off = buy.copy(groups = buy.groups.mapIndexed { i, g -> if (i == idx) g.copy(active = false) else g })
    OneLineVerdict.build(off, now, E).let { v -> val votes = off.groups.count { it.active && it.verdict == Signal.BUY }; val n = off.groups.count { it.active }
        check("an inactive pillar is not counted", v.headline.endsWith("$votes of $n pillars point up") && n == buy.groups.count { it.active } - 1, v.headline) }
    val q = make(now, "LIVE QUALIFIED") { ResearchGate.executionAllowed(it, now) }!!
    OneLineVerdict.build(q, now, E).let { check("qualified headline, not warned", it.headline.startsWith("${q.overallSignal.name} (qualified)") && !it.warn, it.headline) }
    val wait = buy.copy(overallSignal = Signal.WAIT, insights = buy.insights!!.copy(bullishPoints = 55.4, bearishPoints = -20.0, gatePercent = 68.0))
    check("fixture is BELOW_GATE", RealityEngine.waitReason(wait).first == "BELOW_GATE")
    OneLineVerdict.build(wait, now, E).let { v ->
        check("WAIT below gate: plain reason with %", v.headline == "WAIT: no side is strong enough (55% of 68% needed)", v.headline)
        check("WAIT detail: next check IST", Regex("next check \\d\\d:\\d\\d IST.*").matches(v.detail), v.detail)
        check("WAIT live data not warned", !v.warn) }
    check("gate % comes from the engine, not hardcoded", OneLineVerdict.build(wait.copy(insights = wait.insights!!.copy(gatePercent = 72.0)), now, E).headline.endsWith("(55% of 72% needed)"))
    val range = wait.copy(nextPrediction = wait.nextPrediction!!.copy(urgencyTag = "WAIT • RANGE FILTER"))
    check("RANGE reason", OneLineVerdict.build(range, now, E).headline == "WAIT: sideways market, signals unreliable")
    OneLineVerdict.build(wait.copy(gateReason = "WAIT_NO_DATA_KEY"), now, E).let { check("NO_KEY reason, warned", it.headline == "WAIT: no data key saved (Settings)" && it.warn, it.headline) }
    val replay = buy.copy(timeframeAudit = buy.timeframeAudit?.copy(totalSignalsTested = 7, winCount = 7, aiEngineLearningStatus = "REPLAY ONLY"))
    check("engine replay is never shown as the record", OneLineVerdict.build(replay, now, E).detail.contains("5M record: no checked call yet"))
    val stale = buy.copy(feed = buy.feed!!.copy(fetchedAtMs = now - 60 * M))
    OneLineVerdict.build(stale, now, E).let { check("stale price comes first, warned", it.detail.startsWith("price STALE") && it.warn, it.detail) }
    val few = buy.copy(timeframeAudit = TimeframeAccuracyAudit(timeframe = "5M", totalSignalsTested = 13, winCount = 8, lossCount = 5, activeCount = 0, winRatePercent = 62, netPipsGained = 0.0, lastPredictionOutcome = null, recentSignalAudits = emptyList(), autoCorrectionRules = emptyList(), autoCorrectionRulesHindi = emptyList(), aiEngineLearningStatus = ""))
    check("N<20: counts, 'too few'", OneLineVerdict.build(few, now, E).detail.contains("5M record 8/13 correct (too few to judge)"))
    val many = few.copy(timeframeAudit = few.timeframeAudit!!.copy(totalSignalsTested = 60, winCount = 34, lossCount = 26, winRatePercent = 57))
    check("N>=20: % with N and 'not a promise'", OneLineVerdict.build(many, now, E).detail.contains("5M record 57% correct (N=60, past, not a promise)"))
    check("no 'probability' or 'sure' wording", listOf(buy, q, wait, stale).all { a -> OneLineVerdict.build(a, now, E).let { !(it.headline + it.detail).contains("probab", true) && !(it.headline + it.detail).contains("sure", true) } })
    check("Hindi", OneLineVerdict.build(wait, now, AppLanguage.HINDI).headline.startsWith("WAIT: कोई तरफ़ मज़बूत नहीं"))
    val ck = File("app/src/main/java/com/example/livegoldai/ui/v8/V8CockpitScreen.kt").readText()
    check("verdict is the first card on HOME", ck.indexOf("V8VerdictLine(analysis, lang, now)") in 1 until ck.indexOf("// ---------------- LEVEL 1: price + live feed"))
    println("V35.11 verdict: $pass passed, $fail failed")
}
