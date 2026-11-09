import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// V35.13 strict window + published-price anchor + read-only reconciliation. SYNTHETIC FIXTURES.
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name :: $d") } }
const val M = 60_000L
val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
fun t(s: String) = sdf.parse(s)!!.time
fun rec(at: Long, price: Double, side: Signal, atr: Double, horizon: Int = 25, tp: Double = 0.0, sl: Double = 0.0, ds: String = "OANDA XAU_USD", pxt: Long = 0L) = LedgerRecord(
    id = "R$at", createdAt = at, expiresAt = at + horizon * M, interval = "5m", horizonMin = horizon, barId = "b", price = price,
    rawSignal = side, finalSignal = side, confidence = 84, atr = atr, slDistance = sl, regime = "TRANSITION", session = "S", newsActive = false,
    modelVersion = "", appliedFilter = "", sources = emptyMap(), dataSource = ds, engineVersion = "E22.1", tpDistance = tp, priceAtMs = pxt)
fun flat(from: Long, k: Int, px: Double, over: Map<Int, PathBar> = emptyMap(), src: String = "OANDA XAU_USD M1") =
    PricePath(src, (0 until k).map { i -> over[i] ?: PathBar(from + i * M, px, px + 0.1, px - 0.1, px) })
val U = LedgerOutcome.VERIFICATION_UNCERTAIN

fun main() {
    // ---------- Record A (SELL 6 Nov 00:39 IST), synthetic minute path
    val atA = t("2025-11-05 19:09:20")
    fun pathA(bar0: PathBar) = flat(t("2025-11-05 19:09:00"), 26, 4100.9, mapOf(0 to bar0, 24 to PathBar(t("2025-11-05 19:33:00"), 4101.4, 4101.7, 4101.3, 4101.60)))
    val rA = rec(atA, 4101.67, Signal.SELL, 1.34 / 0.25)
    PredictionLedger.evaluate(rA, pathA(PathBar(t("2025-11-05 19:09:00"), 4100.24, 4101.9, 4100.1, 4101.5)), atA + 40 * M).let {
        check("A: published price confirmed by its own minute -> measured from it", it.startPx == 4101.67, "${it.startPx}")
        check("A: from the published price the close (-0.07) is no edge, not INCORRECT", it.outcome == LedgerOutcome.SIDEWAYS, "${it.outcome} move=${it.move}") }
    PredictionLedger.evaluate(rA, pathA(PathBar(t("2025-11-05 19:09:00"), 4100.24, 4100.5, 4100.1, 4100.3)), atA + 40 * M).let {
        check("A: published price NOT inside its minute -> VERIFICATION_UNCERTAIN (not counted)", it.outcome == U && it.timeline.any { l -> l.second.contains("outside the provider's own bar") }, "${it.outcome} ${it.timeline}") }
    check("uncertain is not counted right or wrong", !PredictionLedger.reliable(U))
    // ---------- boundaries (BUY, atr 4 -> threshold 1.0)
    val pub = t("2025-11-04 10:00:45")
    PredictionLedger.evaluate(rec(pub, 4000.0, Signal.BUY, 4.0, tp = 3.0, sl = 3.0),
        flat(t("2025-11-04 10:00:00"), 26, 4000.0, mapOf(0 to PathBar(t("2025-11-04 10:00:00"), 4000.0, 4003.5, 3999.9, 4000.0))), pub + 40 * M).let {
        check("mid-candle publication: a high made before publication never hits the target", it.tpHitAt == 0L && it.outcome != LedgerOutcome.CORRECT, "${it.outcome}") }
    val at0 = t("2025-11-04 10:00:00")
    PredictionLedger.evaluate(rec(at0, 4000.0, Signal.BUY, 4.0, tp = 3.0, sl = 3.0),
        PricePath("OANDA XAU_USD M1", listOf(PathBar(t("2025-11-04 09:59:00"), 3996.0, 3996.2, 3995.0, 3999.9)) + flat(at0, 25, 4000.0).bars), at0 + 40 * M).let {
        check("boundary publication: the minute before is not used", it.startPx == 4000.0 && it.outcome == LedgerOutcome.SIDEWAYS, "${it.startPx} ${it.outcome}") }
    run { val bars = flat(t("2025-11-04 10:00:00"), 26, 4000.0).bars; val last = bars.last()   // 10:25 bar closes 10:26 > expiry 10:25:45
        val r = PredictionLedger.evaluate(rec(pub, 4000.0, Signal.BUY, 4.0), PricePath("OANDA XAU_USD M1", bars.dropLast(1) + last.copy(h = 4002.0, c = 4001.8)), pub + 40 * M)
        check("mid-candle expiry: the bar that closes after expiry is not used", r.outcome == LedgerOutcome.SIDEWAYS && r.endPx == 4000.0, "${r.outcome} end=${r.endPx}") }
    PredictionLedger.evaluate(rec(pub, 4000.0, Signal.BUY, 4.0, tp = 3.0, sl = 3.0),
        flat(t("2025-11-04 10:00:00"), 26, 4000.0, mapOf(4 to PathBar(t("2025-11-04 10:04:00"), 4000.0, 4003.5, 3996.5, 4000.0))), pub + 40 * M).let {
        check("same-bar target + stop: explicit (V36-A: AMBIGUOUS, not counted)", it.outcome == LedgerOutcome.VERIFICATION_UNCERTAIN && it.timeline.any { l -> l.second.contains("same bar") }) }
    PredictionLedger.evaluate(rec(pub, 4000.0, Signal.BUY, 4.0), flat(t("2025-11-04 10:03:00"), 23, 4002.0), pub + 40 * M).let {
        check("missing opening bars -> UNCERTAIN, never a later anchor", it.outcome == U, "${it.outcome}") }
    PredictionLedger.evaluate(rec(pub, 4000.0, Signal.BUY, 4.0), flat(t("2025-11-04 10:00:00"), 20, 4000.0), pub + 40 * M).let {
        check("missing closing bars -> UNCERTAIN", it.outcome == U, "${it.outcome}") }
    PredictionLedger.evaluate(rec(pub, 4001.5, Signal.BUY, 4.0, tp = 3.0, sl = 3.0),
        flat(t("2025-11-04 10:00:00"), 26, 4000.0, mapOf(0 to PathBar(t("2025-11-04 10:00:00"), 4000.0, 4001.8, 3999.9, 4000.0), 3 to PathBar(t("2025-11-04 10:03:00"), 4000.0, 4000.2, 3998.3, 3999.0))), pub + 40 * M).let {
        check("stop measured from the same anchor it was set from (4001.50 - 3 = 3998.50)", it.slHitAt != 0L && it.outcome == LedgerOutcome.INVALIDATED, "${it.outcome}") }
    // ---------- delayed quote / clock
    PredictionLedger.evaluate(rec(pub, 4000.0, Signal.BUY, 4.0, pxt = pub - 6 * M), flat(t("2025-11-04 09:50:00"), 40, 4000.0), pub + 40 * M).let {
        check("price older than 5 min at publication -> UNCERTAIN", it.outcome == U && it.timeline.any { l -> l.second.contains("min old") }) }
    PredictionLedger.evaluate(rec(pub, 4003.0, Signal.BUY, 4.0, pxt = pub - 50_000L),
        flat(t("2025-11-04 09:59:00"), 27, 4000.0, mapOf(0 to PathBar(t("2025-11-04 09:59:00"), 4002.8, 4003.2, 4002.5, 4001.0))), pub + 40 * M).let {
        check("price time known: checked against the minute it was observed in", it.startPx == 4003.0, "${it.outcome} ${it.startPx}") }
    PredictionLedger.evaluate(rec(pub, 4003.0, Signal.BUY, 4.0), flat(t("2025-11-04 09:59:00"), 27, 4000.0), pub + 40 * M).let {
        check("clock skew / stale price not matching its minute -> UNCERTAIN, never guessed", it.outcome == U) }
    // ---------- other instrument / legacy record
    PredictionLedger.evaluate(rec(pub, 4000.0, Signal.BUY, 4.0, ds = ""), flat(t("2025-11-04 10:00:00"), 26, 4100.0), pub + 40 * M).let {
        check("legacy record without a source: first eligible open (no XAU confirmation possible)", it.startPx == 4100.0 && it.outcome == LedgerOutcome.SIDEWAYS, "${it.startPx}") }
    // ---------- plan check: PLAN-V2.1 (new records) already had the strict window; PLAN-V2 (older records) kept on purpose
    run { val r = rec(pub, 4000.0, Signal.BUY, 4.0).copy(planEntry = 4000.0, planStop = 3997.0, planTp1 = 4003.0, planMethod = "PLAN-V2.1")
        val p = flat(t("2025-11-04 10:00:00"), 26, 4000.0, mapOf(0 to PathBar(t("2025-11-04 10:00:00"), 4000.0, 4000.1, 3996.0, 4000.0)))
        check("plan V2.1: a pre-publication low is not a stop", PredictionLedger.planOutcome(r, p) != PredictionLedger.PLAN_STOP, PredictionLedger.planOutcome(r, p)) }
    // ---------- price time persisted
    run { val r = rec(pub, 4000.0, Signal.BUY, 4.0, pxt = pub - 3_000L); val st = InMemoryLedgerStore(); st.append(listOf(PredictionLedger.encodeRecord(r)))
        check("priceAtMs saved and read back", PredictionLedger.load(st).records.single().priceAtMs == pub - 3_000L)
        val old = PredictionLedger.encodeRecord(r.copy(priceAtMs = 0L)); check("old records unchanged (no new key)", !old.contains("pxt")) }
    check("path starts at the price minute (<= 5 min back)", PredictionLedger.pathStart(rec(pub, 4000.0, Signal.BUY, 4.0, pxt = pub - 90_000L)) == t("2025-11-04 09:59:00") &&
        PredictionLedger.pathStart(rec(pub, 4000.0, Signal.BUY, 4.0, pxt = pub - 9 * M)) == pub && PredictionLedger.pathStart(rec(pub, 4000.0, Signal.BUY, 4.0)) == pub)
    check("recorder fetches from pathStart", File("app/src/main/java/com/example/livegoldai/data/BackgroundRecorder.kt").readText().contains("api.fetchPathFor(r.dataSource, PredictionLedger.pathStart(r)"))
    check("buildRecord stores the price time", File("app/src/main/java/com/example/livegoldai/data/LearningCoordinator.kt").readText().contains("priceAtMs = raw.feed?.let"))
    // ---------- read-only reconciliation
    fun res(r: LedgerRecord, o: LedgerOutcome, start: Double, end: Double, thr: Double, tpAt: Long = 0L, slAt: Long = 0L) =
        LedgerResult(r.id, 1L, o, "OANDA XAU_USD M1", start, end, end - start, thr, 0.0, 0.0, slAt, 0.0, emptyList(), emptyMap(), emptyList(), "", emptyList(), tpHitAt = tpAt)
    val a = rec(atA, 4101.67, Signal.SELL, 5.36)
    val b = rec(t("2025-11-05 18:24:15"), 4099.94, Signal.SELL, 5.12)
    val c = rec(t("2025-11-05 17:00:00"), 4090.00, Signal.BUY, 4.0)
    val d = rec(t("2025-11-05 16:00:00"), 4080.00, Signal.BUY, 4.0)
    val e = rec(t("2025-11-05 15:00:00"), 4070.00, Signal.SELL, 4.0, ds = "Binance PAXG/USDT")
    val f = rec(t("2025-11-05 14:00:00"), 4000.00, Signal.BUY, 4.0)    // 0.20 apart (inside the tolerance): kept as recorded
    val results = listOf(res(a, LedgerOutcome.INCORRECT, 4100.24, 4101.60, 1.34), res(b, LedgerOutcome.INCORRECT, 4098.38, 4100.99, 1.28),
        res(c, LedgerOutcome.CORRECT, 4090.10, 4092.00, 1.0), res(d, LedgerOutcome.INVALIDATED, 4081.50, 4079.0, 1.0, slAt = 5L), res(e, LedgerOutcome.CORRECT, 4071.0, 4068.0, 1.0),
        res(f, LedgerOutcome.SIDEWAYS, 4000.20, 4001.10, 1.0))
    val state = LedgerState(listOf(a, b, c, d, e, f), results.associateBy { it.id }, emptyList())
    val snapshot = state.results.toMap()
    val s = LedgerReconcile.build(state, "5m")
    check("screenshot records A and B: INCORRECT -> NO EDGE", s.rows.filter { it.cls == LedgerReconcile.Cls.CHANGED }.map { it.id }.toSet() == setOf(a.id, b.id) &&
        s.rows.first { it.id == a.id }.hypothetical == "NO EDGE", "${s.rows}")
    check("agreeing prices: unchanged", s.rows.first { it.id == c.id }.cls == LedgerReconcile.Cls.UNCHANGED)
    check("stop touch from another price: uncertain", s.rows.first { it.id == d.id }.cls == LedgerReconcile.Cls.UNCERTAIN)
    check("other instrument: uncertain", s.rows.first { it.id == e.id }.cls == LedgerReconcile.Cls.UNCERTAIN)
    check("prices within tolerance: recorded result kept (not re-judged)", s.rows.first { it.id == f.id }.let { it.cls == LedgerReconcile.Cls.UNCHANGED && it.hypothetical == "NO EDGE" })
    check("counts", s.checked == 6 && s.unchanged == 2 && s.changed == 2 && s.uncertain == 2 && s.before["WRONG"] == 3 && s.after["NO EDGE"] == 3 && s.after["UNCERTAIN"] == 2, "$s")
    check("read-only: stored results untouched", state.results == snapshot)
    check("tiles keep the recorded results", OutcomeBrowser.build(state, "5m", OutcomeBrowser.Kind.WRONG).counts[OutcomeBrowser.Kind.WRONG] == 3)
    check("browser page carries the reconciliation", OutcomeBrowser.build(state, "5m", OutcomeBrowser.Kind.WRONG).reconcile?.changed == 2)
    println("V35.13 anchor: $pass passed, $fail failed")
}
