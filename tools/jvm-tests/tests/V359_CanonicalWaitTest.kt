import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.io.File

// V35.9: WAIT wording follows the canonical decision; research example is not a target or a stop; readable text; lab engine filter.
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
const val M = 60_000L
fun main() {
    val base = TradeSetup(signal = Signal.WAIT, entryPrice = 4058.04, stopLoss = 4052.97, stopLossPips = 1.0, takeProfit1 = 4063.10, takeProfit1Pips = 1.0, takeProfit2 = 4068.17,
        takeProfit2Pips = 1.0, riskRewardRatio = "", confidencePercent = 50, strategyNote = "", strategyNoteHindi = "", strategyNoteMarathi = "", atrPips = 1.0)
    val E = AppLanguage.ENGLISH
    check("WAIT + engine WAIT: upside example", PlanText.planTitle(base.copy(planStatus = PlanStamp.SCENARIO), E, Signal.WAIT) == "UPSIDE BREAKOUT EXAMPLE (NOT A LEAN, NOT A TRADE)")
    check("WAIT after a blocked raw SELL: blocked-signal scenario", PlanText.planTitle(base.copy(signal = Signal.SELL, planStatus = PlanStamp.INACTIVE_WAIT), E, Signal.WAIT) == "BLOCKED SELL SIGNAL: SCENARIO ONLY (FINAL = WAIT)")
    for ((st, want) in listOf(PlanStamp.ACTIVE to "ACTION PLAN (ACTIVE)", PlanStamp.RESEARCH to "HYPOTHETICAL RESEARCH SCENARIO", PlanStamp.EXPIRED to "PREDICTION EXPIRED",
        PlanStamp.STOPPED to "PLAN CLOSED: STOP-LOSS HIT", PlanStamp.NO_DATA to "CONDITIONAL SCENARIO (NOT A TRADE)"))
        check("BUY call, $st", PlanText.planTitle(base.copy(signal = Signal.BUY, planStatus = st), E, Signal.BUY) == want, PlanText.planTitle(base.copy(signal = Signal.BUY, planStatus = st), E, Signal.BUY))
    check("canonical WAIT wins over a BUY setup object", PlanText.planTitle(base.copy(signal = Signal.BUY, planStatus = PlanStamp.NO_DATA), E, Signal.WAIT).startsWith("BLOCKED BUY"))
    check("live card: NEXT TARGET only when actionable", PlanText.liveVsLevelTitle(Signal.BUY, true, E).contains("NEXT TARGET") &&
        listOf(Signal.WAIT, Signal.BUY, Signal.SELL).none { PlanText.liveVsLevelTitle(it, false, E).contains("TARGET") && !PlanText.liveVsLevelTitle(it, false, E).contains("NOT A TARGET") })
    check("WAIT live card says example, not target", PlanText.liveVsLevelTitle(Signal.WAIT, false, E) == "LIVE PRICE VS RESEARCH EXAMPLE LEVEL (NOT A TARGET)")
    PlanText.researchInvalidationText(4052.97, E).let { check("research invalidation: level, not a stop, how the ledger scores", it.contains("\$4052.97") && it.contains("not an execution stop") && it.contains("touched first"), it) }
    check("Hindi / Marathi present", PlanText.researchInvalidationText(1.0, AppLanguage.HINDI).contains("असली स्टॉप-लॉस नहीं") && PlanText.researchSandboxTitle(AppLanguage.MARATHI).startsWith("रिसर्च सँडबॉक्स"))

    val kh = File("app/src/main/java/com/example/livegoldai/ui/components/KyaHogaPredictionDialog.kt").readText()
    check("no 'NEXT EXPECTED TARGET' text left", !kh.contains("NEXT EXPECTED TARGET"))
    check("TP2 and risk:reward hidden on WAIT", kh.contains("if (actionable || !(analysis.overallSignal == Signal.WAIT)) Text(\n                                        text = \"TP2:") &&
        kh.contains("if (!(analysis.overallSignal == Signal.WAIT)) { Spacer(modifier = Modifier.width(8.dp))"))
    check("research invalidation used when not actionable", kh.contains("text = if (!actionable) PT.researchInvalidationText(tradeSetup.stopLoss, currentLanguage) else"))
    check("WAIT sandbox title", kh.contains("if (analysis.overallSignal == Signal.WAIT) PT.researchSandboxTitle(currentLanguage) else PT.researchScenarioTitle(currentLanguage)"))
    check("no text under 10sp in the dialog", !Regex("fontSize = [1-9]\\.sp").containsMatchIn(kh))

    // lab: another engine's calls are not mixed in
    fun rec(id: String, at: Long, eng: String) = LedgerRecord(id = id, createdAt = at, expiresAt = at + 25 * M, interval = "5m", horizonMin = 25, barId = id, price = 1.0,
        rawSignal = Signal.BUY, finalSignal = Signal.BUY, confidence = 90, atr = 1.0, slDistance = 1.0, regime = "R", session = "S", newsActive = false, modelVersion = "",
        appliedFilter = "", sources = mapOf(PrecisionLab.LATE to Signal.WAIT), dataSource = GoldApiService.OANDA_SOURCE, engineVersion = eng)
    val recs = (0 until 10).map { rec("a$it", it * 30 * M, PredictionLedger.ENGINE_VERSION) } + (0 until 10).map { rec("b$it", (20 + it) * 30 * M, "E99.0") }
    val out = recs.associate { it.id to LedgerResult(it.id, 1L, LedgerOutcome.CORRECT, "x", 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, 0L, 0.0, emptyList(), emptyMap(), emptyList(), "", emptyList()) }
    check("lab: current engine only", PrecisionLab.evaluate(LedgerState(recs, out, emptyList())).first { it.key == PrecisionLab.LATE }.prodN == 10)
    // V35.9.1: 48 dp tap targets with a spoken label on the four Correct / Wrong tiles
    val du = File("app/src/main/java/com/example/livegoldai/ui/components/DualPredictionAndIndicatorsSection.kt").readText()
    check("tiles: 48 dp + click label", listOf(kh, du).all { src -> src.split(".heightIn(min = 48.dp)").size >= 3 && src.contains("onClickLabel = \"Open the correct calls of this timeframe\"") && src.contains("onClickLabel = \"Open the wrong calls of this timeframe\"") })
    println("V35.9 canonical wait: $pass passed, $fail failed")
}
