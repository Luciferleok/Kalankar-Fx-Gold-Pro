import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// V35.6: DELAYED label (permission unchanged), thin-first evidence tiers, snapshot identity, Macro event match. SYNTHETIC.
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
const val M = 60_000L
val empty = LedgerState(emptyList(), emptyMap(), emptyList())

fun make(now: Long, iv: String = "5m"): GoldAnalysisResult? {
    val ivMs = RealityEngine.intervalMinutes(iv) * M
    for (seed in 1L..300L) {
        val rnd = Random(seed); var p = 4100.0
        val candles = (0 until 150).map { i -> val o = p; p += 0.5 + (rnd.nextDouble() - 0.5); CandleBar(sdf.format(Date((now / ivMs) * ivMs - (149 - i) * ivMs)), o, maxOf(o, p) + 0.6, minOf(o, p) - 0.6, p) }
        val td = FeedStatus("Twelve Data XAU/USD", now - 5_000, 300, true, true, true, 0, 1)
        val raw = TechnicalEngine.analyze(candles, iv).copy(feed = td, sourceState = SourceStateMachine.PRIMARY_ACTIVE)
        val a = PlanStamp.stamp(raw, empty, now, "LIVE QUALIFIED").let { it.copy(presentation = PresentationBuilder.build(it, empty, "LIVE QUALIFIED", now)) }
        if (a.tradeSetup.planStatus == PlanStamp.ACTIVE) return a
    }
    return null
}
fun rec(id: String, at: Long, side: Signal, regime: String = "R", src: String = "Twelve Data XAU/USD", bar: String = id) =
    LedgerRecord(id = id, createdAt = at, expiresAt = at + 30 * M, interval = "5m", horizonMin = 30, barId = bar, price = 1.0, rawSignal = side, finalSignal = side,
        confidence = 75, atr = 1.0, slDistance = 1.0, regime = regime, session = "S", newsActive = false, modelVersion = "", appliedFilter = "", sources = emptyMap(),
        dataSource = src, engineVersion = PredictionLedger.ENGINE_VERSION)
fun res(id: String, o: LedgerOutcome) = LedgerResult(id, 1L, o, "x", 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0L, 0.0, emptyList(), emptyMap(), emptyList(), "", emptyList())

fun main() {
    val t0 = sdf.parse("2026-10-28 10:22:10")!!.time
    val a = make(t0)!!
    // ---- A: DELAYED is said, permission unchanged
    val delayed = a.copy(feed = a.feed!!.copy(fetchedAtMs = t0 - 3 * M))
    check("fixture DELAYED", Freshness.assess(delayed, t0) == Freshness.DELAYED)
    TrustCard.build(delayed, t0, AppLanguage.ENGLISH).let { c ->
        check("DELAYED data: Now = DELAYED DATA, not ACTIONABLE", c.nowStatus == "DELAYED DATA" && c.nowReasons.any { it.startsWith("price DELAYED") }, "${c.nowStatus} ${c.nowReasons}")
        check("chip DATA says DELAYED", c.chips[2].second == Freshness.DELAYED) }
    check("execution permission unchanged (still allowed on DELAYED)", ResearchGate.executionAllowed(delayed, t0))
    check("LIVE: still ACTIONABLE", TrustCard.build(a, t0, AppLanguage.ENGLISH).nowStatus == "ACTIONABLE")
    val rg = File("app/src/main/java/com/example/livegoldai/data/ResearchGate.kt").readText()
    check("ResearchGate freshness rule untouched", rg.contains("if (fresh != Freshness.LIVE && fresh != Freshness.DELAYED) r += DATA_NOT_FRESH"))

    // ---- B: thin first, then count decided
    val side = Signal.BUY
    fun st(vararg p: Pair<LedgerRecord, LedgerOutcome?>) = LedgerState(p.map { it.first }, p.filter { it.second != null }.associate { it.first.id to res(it.first.id, it.second!!) }, emptyList())
    EdgeFinder.exactMatch(st(rec("s", 0, side) to LedgerOutcome.SIDEWAYS, rec("c", 10 * M, side) to LedgerOutcome.CORRECT), "5m", side, "Twelve Data XAU/USD", PredictionLedger.ENGINE_VERSION).let {
        check("sideways call keeps its window: overlapping decided call not counted", it.n == 0 && it.windows == 1, "$it") }
    EdgeFinder.exactMatch(st(rec("p", 0, side) to null, rec("c", 10 * M, side) to LedgerOutcome.CORRECT), "5m", side, "Twelve Data XAU/USD", PredictionLedger.ENGINE_VERSION).let {
        check("pending call does not hold a window", it.n == 1 && it.windows == 1, "$it") }
    EdgeFinder.exactMatch(st(rec("i", 0, side) to LedgerOutcome.INVALIDATED, rec("u", 40 * M, side) to LedgerOutcome.VERIFICATION_UNCERTAIN, rec("k", 80 * M, side) to LedgerOutcome.CORRECT), "5m", side, "Twelve Data XAU/USD", PredictionLedger.ENGINE_VERSION).let {
        check("invalidated = decided wrong, uncertain = window not decided", it.n == 2 && it.windows == 3, "$it") }
    // clustered sideways simulation (audit): thin-first count
    val r = Random(7); val recs = ArrayList<LedgerRecord>(); val out = HashMap<String, LedgerOutcome>()
    for (i in 0 until 2000) { val id = "r$i"; recs += rec(id, i * 10 * M, side)
        val prevSide = i > 0 && out["r${i - 1}"] == LedgerOutcome.SIDEWAYS
        out[id] = when { r.nextDouble() < (if (prevSide) 0.6 else 0.25) -> LedgerOutcome.SIDEWAYS; r.nextDouble() < 0.55 -> LedgerOutcome.CORRECT; else -> LedgerOutcome.INCORRECT } }
    EdgeFinder.exactMatch(LedgerState(recs, out.mapValues { res(it.key, it.value) }, emptyList()), "5m", side, "Twelve Data XAU/USD", PredictionLedger.ENGINE_VERSION).let {
        check("audit simulation: N 421 of 667 windows (was 521)", it.n == 421 && it.windows == 667 && it.pct == 50, "$it") }

    // ---- tiers on the card: tier A needs this candle's own ledger record (its regime)
    val s2 = a.overallSignal
    val own = rec("own", t0 - 2 * M, s2, regime = "TREND_UP", bar = a.lastUpdated)
    val regA = (0 until 22).map { rec("a$it", t0 - (200 - it * 4) * 60 * M, s2, regime = "TREND_UP") }
    val regB = (0 until 22).map { rec("b$it", t0 - (198 - it * 4) * 60 * M, s2, regime = "RANGE") }
    val outs = (regA.mapIndexed { i, x -> x.id to res(x.id, if (i < 15) LedgerOutcome.CORRECT else LedgerOutcome.INCORRECT) } + regB.map { it.id to res(it.id, LedgerOutcome.INCORRECT) }).toMap()
    val ledger = LedgerState(regA + regB + own, outs, emptyList())
    val withTiers = a.copy(presentation = PresentationBuilder.build(a, ledger, "LIVE QUALIFIED", t0))
    val pr = withTiers.presentation!!
    check("tier A: same regime only (22, 68%)", pr.tierAN == 22 && pr.tierAPct == 68 && pr.tierAScope.endsWith("TREND_UP"), "${pr.tierAN} ${pr.tierAPct} ${pr.tierAScope}")
    check("tier B: regime mixed (44, 34%)", pr.exactHitN == 44 && pr.exactHitPct == 34, "${pr.exactHitN} ${pr.exactHitPct}")
    check("ledger time of this candle", pr.ledgerRecordedAtMs == own.createdAt)
    TrustCard.build(withTiers, t0, AppLanguage.ENGLISH).let { c ->
        check("card value = narrowest tier with enough calls", c.metrics[1].value == "TIER A · 68% · N=22", c.metrics[1].value)
        check("note lists A, B, C, outcome rule, not a probability", c.metrics[1].note.let { n -> n.contains("A (same regime): 68% · N=22 · coverage 100%") && n.contains("B (regime mixed): 34% · N=44") &&
            n.contains("C (GENERAL HISTORICAL CONTEXT") && n.contains(EdgeFinder.OUTCOME_RULE) && n.contains("not a validated probability") }, c.metrics[1].note)
        check("chip EVIDENCE = A · 68%", c.chips[1].second == "A · 68%")
        check("ledger label set", c.ledgerAt == TrustCard.localTs(own.createdAt)) }
    TrustCard.build(a, t0, AppLanguage.ENGLISH).let { c ->
        check("no ledger record: tier A says so, value INSUFFICIENT", c.metrics[1].value == "INSUFFICIENT EVIDENCE · N=0/20" && c.metrics[1].note.contains("this candle not in the ledger yet") && c.ledgerAt.isEmpty(), c.metrics[1].value)
        check("snapshot time = this snapshot's fetch time", c.snapshotAt == TrustCard.localTs(a.feed!!.fetchedAtMs))
        check("three chips", c.chips.size == 3 && c.chips.map { it.first } == listOf("AGREEMENT", "EVIDENCE", "DATA")) }
    val ist = TimeZone.getTimeZone("Asia/Kolkata")
    check("IST label", TrustCard.localTs(sdf.parse("2026-11-06 13:30:00")!!.time, ist) == "19:00 IST")

    // ---- D: Macro evidence must belong to THIS window's event
    val t = sdf.parse("2026-11-06 13:30:00")!!.time; val now = t + 12 * M
    val cal = OfficialCalendar.events()
    val candles = (0 until 150).map { k -> val o = 4000.0 + k * 0.4; CandleBar(sdf.format(Date(now - (150 - k) * 5 * M)), o, o + 0.6, o - 0.5, o + 0.35) }
    fun obs(id: String) = MacroMarketIndex("DXY", "d", 100.0, 0.2, Signal.SELL, "x", MacroEvidence(EvidenceStatus.OBSERVED_COMPLETE, "OANDA", "6 OANDA FX pairs", "COMPUTED", "index points", t + 5 * M, t + 10 * M, now, id, true))
    check("evidence of this event: Macro active", TechnicalEngine.analyze(candles, "5m", obs("NFP@$t"), null, cal, nowMs = now).groups.first { it.key == "macro" }.active)
    check("evidence of another event: no vote", !TechnicalEngine.analyze(candles, "5m", obs("CPI@$t"), null, cal, nowMs = now).groups.first { it.key == "macro" }.active)
    check("evidence without event id: no vote", !TechnicalEngine.analyze(candles, "5m", obs(""), null, cal, nowMs = now).groups.first { it.key == "macro" }.active)
    check("outside a window nothing votes", !TechnicalEngine.analyze(candles, "5m", obs("NFP@$t"), null, emptyList(), nowMs = now).groups.first { it.key == "macro" }.active)

    // ---- UI source: compact collapsed view, 48 dp tap row, animated details, snapshot wording
    val ui = File("app/src/main/java/com/example/livegoldai/ui/v8/V8TrustCard.kt").readText()
    check("UI: chips, 48dp row, AnimatedVisibility, snapshot wording", ui.contains("card.chips.forEach") && ui.contains("heightIn(min = 48.dp)") &&
        ui.contains("AnimatedVisibility(visible = open") && ui.contains("Snapshot status") && !ui.contains("\"Published\""))
    println("V35.6 trust validation: $pass passed, $fail failed")
}
