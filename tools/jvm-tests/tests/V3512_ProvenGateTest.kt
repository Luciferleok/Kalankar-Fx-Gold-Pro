import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// V35.12 Proven-only gate. SYNTHETIC FIXTURES.
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name :: $d") } }
const val M = 60_000L
val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
fun rec(id: String, at: Long, side: Signal = Signal.BUY, iv: String = "5m", regime: String = "TREND_UP", eng: String = PredictionLedger.ENGINE_VERSION, final: Signal = side) =
    LedgerRecord(id = id, createdAt = at, expiresAt = at + 25 * M, interval = iv, horizonMin = 25, barId = id, price = 4000.0, rawSignal = side, finalSignal = final,
        confidence = 75, atr = 1.0, slDistance = 1.0, regime = regime, session = "S", newsActive = false, modelVersion = "", appliedFilter = "",
        sources = emptyMap(), dataSource = "Twelve Data XAU/USD", engineVersion = eng)
// V36-A: the gate counts only results checked with the current method (EV2)
fun res(id: String, o: LedgerOutcome) = LedgerResult(id, 1L, o, "x", 4000.0, 4001.0, 2.0, 0.5, 2.0, 1.0, 0L, 0.0, emptyList(), emptyMap(), emptyList(), "", emptyList(), evalVersion = PredictionLedger.EVAL_VERSION)
/** n independent calls (30 min apart), k of them CORRECT, the rest INCORRECT */
fun book(n: Int, k: Int, side: Signal = Signal.BUY, iv: String = "5m", regime: String = "TREND_UP", start: Long = 0L, tag: String = "a", eng: String = PredictionLedger.ENGINE_VERSION, wrong: LedgerOutcome = LedgerOutcome.INCORRECT): Pair<List<LedgerRecord>, List<LedgerResult>> {
    val rs = (0 until n).map { i -> rec("$tag$i", start + i * 30 * M, side, iv, regime, eng) }
    return rs to rs.mapIndexed { i, r -> res(r.id, if (i < k) LedgerOutcome.CORRECT else wrong) }
}
fun state(vararg b: Pair<List<LedgerRecord>, List<LedgerResult>>) = LedgerState(b.flatMap { it.first }, b.flatMap { it.second }.associateBy { it.id }, emptyList())

fun main() {
    val now = sdf.parse("2026-11-04 10:00:00")!!.time
    // a deterministic up-trend analysis on 5m so regimeOf = TREND_UP
    val cs = (0 until 150).map { i -> val o = 4000.0 + i * 0.8; CandleBar(sdf.format(Date(now - (149 - i) * 5 * M)), o, o + 1.0, o - 0.3, o + 0.7) }
    val base = TechnicalEngine.analyze(cs, "5m").copy(feed = FeedStatus("Twelve Data XAU/USD", now - 5_000, 300, true, true, true, 0, 1))
    val reg = PredictionLedger.regimeOf(base.recentCandles, false)
    check("fixture regime", reg == "TREND_UP", reg)
    val buy = base.copy(overallSignal = Signal.BUY, rawSignal = null)
    fun g(st: LedgerState?, a: GoldAnalysisResult = buy) = ProvenGate.apply(a, st)

    // ---- the rule
    check("no record -> WAIT, BUY kept as raw", g(state()).let { it.overallSignal == Signal.WAIT && it.rawSignal == Signal.BUY && it.proven?.held == true && it.proven?.n == 0 })
    check("29 calls at 70% -> not enough calls", g(state(book(29, 21))).overallSignal == Signal.WAIT)
    check("30 calls at 55% (17/30=56.7%) -> published", g(state(book(30, 17))).let { it.overallSignal == Signal.BUY && it.proven?.proven == true && it.proven?.held == false })
    check("30 calls 16/30 (53%) -> WAIT", g(state(book(30, 16))).overallSignal == Signal.WAIT)
    check("40 calls 22/40 (55.0% exactly) -> published", g(state(book(40, 22))).overallSignal == Signal.BUY)
    check("INVALIDATED counts as wrong", g(state(book(30, 16, wrong = LedgerOutcome.INVALIDATED))).let { it.proven?.n == 30 && it.overallSignal == Signal.WAIT })
    // ---- the setup must match
    check("other side's record does not prove BUY", g(state(book(40, 35, side = Signal.SELL))).overallSignal == Signal.WAIT)
    check("other timeframe's record does not prove 5m", g(state(book(40, 35, iv = "15m"))).overallSignal == Signal.WAIT)
    check("other market type does not prove TREND_UP", g(state(book(40, 35, regime = "RANGE"))).overallSignal == Signal.WAIT)
    check("old engine's record does not count", g(state(book(40, 35, eng = "E22.0"))).overallSignal == Signal.WAIT)
    // ---- one call per window: 60 calls 1 min apart overlap -> only a few independent
    val dense = (0 until 60).map { i -> rec("d$i", i * M) }
    g(LedgerState(dense, dense.associate { it.id to res(it.id, LedgerOutcome.CORRECT) }, emptyList())).let { check("overlapping calls are thinned (not 60)", it.proven!!.n in 1..5 && it.overallSignal == Signal.WAIT, "n=${it.proven?.n}") }
    // ---- pending / no-data results do not count
    val (pr, _) = book(40, 40)
    check("unchecked calls do not count", g(LedgerState(pr, pr.associate { it.id to res(it.id, LedgerOutcome.PENDING) }, emptyList())).proven?.n == 0)
    // ---- WAIT records (filtered) do not prove a side
    val fr = (0 until 40).map { i -> rec("f$i", i * 30 * M, final = Signal.WAIT) }
    check("held / filtered WAIT records do not count", g(LedgerState(fr, fr.associate { it.id to res(it.id, LedgerOutcome.CORRECT) }, emptyList())).proven?.n == 0)
    // ---- never creates or flips, fail closed
    check("WAIT stays WAIT (untouched)", g(state(book(40, 35)), base.copy(overallSignal = Signal.WAIT)).let { it.overallSignal == Signal.WAIT && it.proven == null })
    check("SELL proven only by SELL record", g(state(book(40, 30, side = Signal.SELL)), base.copy(overallSignal = Signal.SELL)).overallSignal == Signal.SELL)
    check("unknown state -> fail closed", g(null).overallSignal == Signal.WAIT)
    check("news window -> its own NEWS_EVENT setup", ProvenGate.apply(buy.copy(isNewsModeTriggered = true), state(book(40, 35))).proven?.setupKey == "5m|NEWS_EVENT|BUY")
    // ---- downstream
    val held = g(state(book(12, 8)))
    check("wait reason UNPROVEN", RealityEngine.waitReason(held).first == "UNPROVEN", RealityEngine.waitReason(held).toString())
    val st = PlanStamp.stamp(held.copy(lastUpdated = cs.last().datetime), LedgerState(emptyList(), emptyMap(), emptyList()), now, "LIVE QUALIFIED")
    check("plan INACTIVE_WAIT, gate closed even when LIVE QUALIFIED", st.tradeSetup.planStatus == PlanStamp.INACTIVE_WAIT && !ResearchGate.executionAllowed(st, now), st.tradeSetup.planStatus)
    OneLineVerdict.build(held, now, AppLanguage.ENGLISH).let { check("verdict says not proven with the record", it.headline == "WAIT: BUY not validated yet (this setup 8/12 correct, minimum 30 at 55%)", it.headline) }
    OneLineVerdict.build(g(state(book(40, 26))), now, AppLanguage.ENGLISH).let { check("proven BUY shows its record + range", it.detail.contains("minimum sample met: 26/40 correct (95% range ") && it.detail.contains("not proof of an edge"), it.detail) }
    check("Hindi verdict", OneLineVerdict.build(held, now, AppLanguage.HINDI).headline.startsWith("WAIT: BUY अभी जाँचा-परखा नहीं"))
    // ---- wiring: after the ledger + Shadow Lab, fail closed on error
    val vm = File("app/src/main/java/com/example/livegoldai/ui/GoldViewModel.kt").readText()
    val iProc = vm.indexOf("val engineCall = learning.process("); val iShadow = vm.indexOf("shadowLive.observe(engineCall"); val iGate = vm.indexOf("ProvenGate.apply(engineCall")
    check("order: ledger record -> Shadow Lab sees engine call -> gate", iProc in 1 until iShadow && iShadow < iGate, "$iProc $iShadow $iGate")
    check("error path fails closed", vm.contains("ProvenGate.apply(rawAnalysis, null)"))
    check("no threshold / weight change", File("app/src/main/java/com/example/livegoldai/data/ProvenGate.kt").readText().let { !it.contains("PILLAR_WEIGHTS") && !it.contains("gatePercent") })
    println("V35.12 proven gate: $pass passed, $fail failed")
}
