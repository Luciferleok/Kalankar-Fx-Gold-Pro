import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// V36-A data and ledger truth. SYNTHETIC FIXTURES.
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name :: $d") } }
const val M = 60_000L
val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
val R = "app/src/main/java/com/example/livegoldai/"
fun rec(at: Long, side: Signal, tp: Double = 0.0, sl: Double = 0.0, id: String = "R$at", regime: String = "TREND_UP") = LedgerRecord(id = id, createdAt = at, expiresAt = at + 25 * M, interval = "5m", horizonMin = 25, barId = id,
    price = 4000.0, rawSignal = side, finalSignal = side, confidence = 80, atr = 4.0, slDistance = sl, regime = regime, session = "S", newsActive = false,
    modelVersion = "", appliedFilter = "", sources = emptyMap(), dataSource = "OANDA XAU_USD", engineVersion = PredictionLedger.ENGINE_VERSION, tpDistance = tp)
fun bar(t: Long, l: Double = 3999.8, h: Double = 4000.2, c: Double = 4000.0) = PathBar(t, 4000.0, h, l, c)
val U = LedgerOutcome.VERIFICATION_UNCERTAIN
fun main() {
    val at = sdf.parse("2026-11-04 10:00:00")!!.time
    fun ev(r: LedgerRecord, bars: List<PathBar>) = PredictionLedger.evaluate(r, PricePath("OANDA XAU_USD M1", bars), at + 40 * M)
    // ---- bar integrity
    ev(rec(at, Signal.BUY, sl = 3.0), (0 until 25).filter { it < 5 || it >= 20 }.map { bar(at + it * M, c = if (it >= 20) 4002.0 else 4000.0) }).let {
        check("15 interior minutes missing -> UNCERTAIN", it.outcome == U && it.timeline.any { l -> l.second.contains("bars missing inside the window") }, "${it.outcome}") }
    ev(rec(at, Signal.BUY), (0 until 25).filter { it !in 11..14 }.map { bar(at + it * M) }).let { check("4-minute hole -> UNCERTAIN", it.outcome == U, "${it.outcome}") }
    ev(rec(at, Signal.BUY), (0 until 25).filter { it != 7 && it != 15 }.map { bar(at + it * M) }).let { check("2 scattered missing minutes tolerated (no-tick minutes)", it.outcome == LedgerOutcome.SIDEWAYS, "${it.outcome}") }
    ev(rec(at, Signal.BUY, sl = 3.0), (0 until 25).flatMap { i -> if (i == 10) listOf(bar(at + i * M), bar(at + i * M, l = 3996.0)) else listOf(bar(at + i * M)) }).let {
        check("same minute twice with different prices -> UNCERTAIN", it.outcome == U && it.timeline.any { l -> l.second.contains("two different bars") }, "${it.outcome}") }
    ev(rec(at, Signal.BUY), (0 until 25).flatMap { i -> if (i == 10) listOf(bar(at + i * M), bar(at + i * M)) else listOf(bar(at + i * M)) }).let {
        check("identical duplicate collapses (not a defect)", it.outcome == LedgerOutcome.SIDEWAYS, "${it.outcome}") }
    // ---- same bar stop + target
    ev(rec(at, Signal.BUY, tp = 3.0, sl = 3.0), (0 until 25).map { if (it == 4) bar(at + it * M, l = 3996.5, h = 4003.5) else bar(at + it * M) }).let {
        check("same-bar target + stop -> AMBIGUOUS (not counted)", it.outcome == U && it.timeline.any { l -> l.second.contains("order unknown, not counted") } && !PredictionLedger.reliable(it.outcome), "${it.outcome}") }
    ev(rec(at, Signal.BUY, tp = 3.0, sl = 3.0), (0 until 25).map { if (it == 4) bar(at + it * M, l = 3996.5) else if (it == 6) bar(at + it * M, h = 4003.5) else bar(at + it * M) }).let {
        check("stop first in an earlier bar still counts as stop", it.outcome == LedgerOutcome.INVALIDATED, "${it.outcome}") }
    // ---- evaluation version
    val good = ev(rec(at, Signal.BUY), (0 until 25).map { bar(at + it * M, c = 4002.0) })
    check("new results stamped EV2", good.evalVersion == PredictionLedger.EVAL_VERSION && good.outcome == LedgerOutcome.CORRECT)
    check("uncertain results stamped too", ev(rec(at, Signal.BUY), emptyList<PathBar>() + bar(at)).evalVersion == PredictionLedger.EVAL_VERSION)
    run { val st = InMemoryLedgerStore(); st.append(listOf(PredictionLedger.encodeRecord(rec(at, Signal.BUY).copy(publishedSignal = Signal.WAIT)), PredictionLedger.encodeResult(good)))
        val s = PredictionLedger.load(st)
        check("evalVersion + publishedSignal saved and read back", s.results[good.id]?.evalVersion == "EV2" && s.records.single().publishedSignal == Signal.WAIT)
        check("old lines have no new keys", !PredictionLedger.encodeResult(good.copy(evalVersion = "")).contains("\"ev\"") && !PredictionLedger.encodeRecord(rec(at, Signal.BUY)).contains("\"pub\"")) }
    // ---- ProvenGate: only EV2 results
    fun book(n: Int, k: Int, evv: String, tag: String) = (0 until n).map { i -> rec(i * 30 * M, Signal.BUY, id = "$tag$i") }.let { rs ->
        rs to rs.mapIndexed { i, r -> LedgerResult(r.id, 1L, if (i < k) LedgerOutcome.CORRECT else LedgerOutcome.INCORRECT, "x", 4000.0, 4001.0, 1.0, 0.5, 1.0, 0.0, 0L, 0.0, emptyList(), emptyMap(), emptyList(), "", emptyList(), evalVersion = evv) } }
    val (oldR, oldS) = book(40, 35, "", "o"); val (newR, newS) = book(12, 8, "EV2", "n")
    val pg = ProvenGate.status(LedgerState(oldR + newR, (oldS + newS).associateBy { it.id }, emptyList()), "5m", "TREND_UP", Signal.BUY)
    check("older-method results never qualify a setup", pg.n == 12 && !pg.proven, "n=${pg.n}")
    val (r30, s30) = book(30, 17, "EV2", "q")
    check("30 EV2 calls at 57% meet the minimum", ProvenGate.status(LedgerState(r30, s30.associateBy { it.id }, emptyList()), "5m", "TREND_UP", Signal.BUY).proven)
    // ---- published call recorded
    val lc = File(R + "data/LearningCoordinator.kt").readText()
    check("record stores the published call (same gate, same ledger)", lc.contains("r.copy(publishedSignal = try { ProvenGate.apply(applied, state).overallSignal }"))
    // ---- wording
    val now = sdf.parse("2026-11-04 10:00:00")!!.time
    val cs = (0 until 150).map { i -> val o = 4000.0 + i * 0.8; CandleBar(sdf.format(Date(now - (149 - i) * 5 * M)), o, o + 1.0, o - 0.3, o + 0.7) }
    val base = TechnicalEngine.analyze(cs, "5m").copy(feed = FeedStatus("OANDA XAU_USD", now - 5_000, 300, true, true, true, 0, 1))
    val held = ProvenGate.apply(base.copy(overallSignal = Signal.BUY, rawSignal = null), LedgerState(newR, newS.associateBy { it.id }, emptyList()))
    check("reason says NOT VALIDATED, not PROVEN", RealityEngine.waitReason(held).second == "NOT VALIDATED • 8/12 (MIN 30 AT 55%)", RealityEngine.waitReason(held).second)
    check("verdict wording", OneLineVerdict.build(held, now, AppLanguage.ENGLISH).headline == "WAIT: BUY not validated yet (this setup 8/12 correct, minimum 30 at 55%)", OneLineVerdict.build(held, now, AppLanguage.ENGLISH).headline)
    val okd = ProvenGate.apply(base.copy(overallSignal = Signal.BUY, rawSignal = null), LedgerState(r30, s30.associateBy { it.id }, emptyList()))
    OneLineVerdict.build(okd, now, AppLanguage.ENGLISH).let { check("met minimum shows its range and 'not proof'", it.detail.contains("minimum sample met: 17/30 correct (95% range ") && it.detail.contains("not proof of an edge"), it.detail) }
    val src = listOf("data/RealityEngine.kt", "data/PlanText.kt", "data/DecisionTraceText.kt", "data/OneLineVerdict.kt").joinToString("\n") { File(R + it).readText() }
    check("no 'NOT PROVEN' / 'proven setup' / 'Proven-only' text left", !src.contains("NOT PROVEN") && !src.contains("proven setup") && !src.contains("Proven-only mode"))
    // ---- list shows engine vs published + method
    val ob = File(R + "ui/components/OutcomeBrowserDialog.kt").readText()
    check("list shows what HOME showed and the check method", ob.contains("Shown on HOME") && ob.contains("Check method"))
    println("V36-A truth: $pass passed, $fail failed")
}
