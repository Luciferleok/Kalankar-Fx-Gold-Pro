package com.example.livegoldai.data

import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * V29B: ONE read-only description of the decision on screen, built once per refresh from the authoritative
 * analysis + ledger. Home, Brain and the Decision Trace all read this same object, so they always describe the
 * same snapshot. It never predicts anything itself and never writes to the ledger.
 * Missing values are "UNKNOWN" / "UNAVAILABLE", never invented.
 */
@Serializable
data class PredictionPresentationState(
    val builtAtMs: Long,
    // ---- identity
    val decisionId: String,          // ledger record id, or "NOT RECORDED"
    val snapshotId: String,          // or "UNAVAILABLE"
    val instrument: String,          // "XAU/USD" / "PAXG/USDT" / "GC=F" / "UNKNOWN"
    val provider: String,            // "TWELVE DATA" / "BINANCE" / "YAHOO" / "NONE"
    val sourceType: String,          // PRIMARY / PROXY / BACKUP / SIMULATED / UNKNOWN
    val sourceBadge: String,         // "XAU/USD • TWELVE DATA • PRIMARY"
    val compatibleWithModel: Boolean,
    val timeframe: String,
    val analysisAtMs: Long,          // when this data arrived (0 = unknown)
    val candleTime: String,          // newest candle of the analysis
    // ---- decision
    val rawSignal: Signal?,          // what the quant engine said (null = unknown)
    val finalSignal: Signal,         // what is published
    val reasonCode: String,          // ELIGIBLE / SOURCE / DATA / NO_KEY / RANGE / LEARNED / BELOW_GATE / ENGINE_RULE
    val reasonText: String,          // English, from the same code
    // ---- trust dimensions (kept separate on purpose)
    val dataQuality: String,         // EXCELLENT / GOOD / ... / UNKNOWN
    val sourceState: String,         // gate state or UNKNOWN
    val ledgerAudit: String,         // HEALTHY / WEAK / CRITICAL / VALIDATING / COLLECTING / UNKNOWN (production rules)
    val researchModelStatus: String, // Shadow Lab live status for this timeframe, or UNKNOWN
    val regime: String,
    val uncertainty: String,
    val evidenceAgreementPct: Int,   // pillar agreement (heuristic)
    val ruleSetupStrength: Int,      // the brain's quality index (heuristic), -1 unknown
    val hitRatePct: Int,             // past hit-rate of this agreement band, -1 when not enough
    val hitRateN: Int,
    val headlineKind: String,        // HISTORICAL / HEURISTIC / NONE  (what the big % on Home is)
    val calibration: String,         // UNAVAILABLE / UNRELIABLE / WITHIN_10_PTS
    val calibrationErrorPts: Double, // -1 unknown
    val confidenceInverted: Boolean, // higher agreement was right less often
    // ---- evidence
    val supporting: List<Factor>,
    val conflicting: List<Factor>,
    val neutral: List<String>,
    val attributionAvailable: Boolean,
    val mtfAvailable: Boolean,       // PAXG proxy context: optional, never part of the decision
    val crossMarketAvailable: Boolean,
    val aiStatus: String,            // NOT CONNECTED / AVAILABLE / ERROR
    val botsSummary: String,         // rule bots, never AI
    // ---- audit
    val engineVersion: String,
    val featureVersion: String,
    val modelVersion: String,
    val ledgerSize: Int,
    val ledgerChecked: Int,
    // ---- V29C
    val secondaryReasons: List<String> = emptyList(),   // codes: RANGE_REGIME, NO_PROVEN_EDGE, HIGH_UNCERTAINTY, MTF_UNAVAILABLE, ENGINE_NO_SIDE
    val restorationPending: String = "",                // "1/2" while Twelve Data is back but not yet restored, else ""
    val isTradeSetupActive: Boolean = false,
    val scenarioAvailable: Boolean = false,
    val quoteAtMs: Long = 0L,                           // latest market quote arrived
    val publishedAtMs: Long = 0L,                       // this decision was recorded in the ledger (0 = not recorded)
    val provenEdge: Boolean = false,                    // true only when the research model is LIVE QUALIFIED
    // V29C.1: when this decision is reviewed: the open of the decision's candle + one interval (0 = unknown).
    // Fixed by the snapshot, so refreshing the price never moves it; once passed the screen says REVIEW DUE.
    val nextReviewAtMs: Long = 0L,
    // ---- V29D: plan status + separately defined historical statistics (never merged into one number)
    val planStatus: String = "",
    val freshness: String = "",
    val closeDirectionPct: Int = -1,       // Home VERIFIED: did the close move the called way (all timeframes)
    val closeDirectionN: Int = 0,
    val coverage: String = "",              // share of checked predictions that took a side
    val ledgerFirstAtMs: Long = 0L,
    val ledgerLastAtMs: Long = 0L,
    // ---- V29E: one forecast state + the snapshot's own clock and statistics
    val forecastState: String = "",
    val horizonMin: Int = 0,
    val expiresAtMs: Long = 0L,
    val outcome: String = "",                       // ledger outcome of this forecast once checked ("" = not yet)
    val metrics: List<MetricStat> = emptyList()
) {
    @Serializable
    data class Factor(val title: String, val points: Double)

    val rawDiffersFromFinal: Boolean get() = rawSignal != null && rawSignal != finalSignal
}

object PresentationBuilder {
    const val UNKNOWN = "UNKNOWN"

    fun build(a: GoldAnalysisResult, state: LedgerState?, researchStatus: String?, now: Long = System.currentTimeMillis()): PredictionPresentationState {
        val id = SourceRegistry.identify(a.feed?.source)
        val instrument = when (id.instrument) {
            InstrumentId.XAU_USD_SPOT -> "XAU/USD"; InstrumentId.PAXG_USDT -> "PAXG/USDT"; InstrumentId.GC_F -> "GC=F"; else -> UNKNOWN
        }
        val provider = when (id.provider) {
            ProviderId.TWELVE_DATA -> "TWELVE DATA"; ProviderId.BINANCE -> "BINANCE"; ProviderId.YAHOO -> "YAHOO"; else -> "NONE"
        }
        // V29E: the record of THIS snapshot (same candle) first, so an expired forecast keeps its identity and outcome
        val rec = state?.records?.lastOrNull { it.interval.equals(a.interval, true) && it.barId == a.lastUpdated }
            ?: state?.records?.lastOrNull { it.interval.equals(a.interval, true) && now < it.expiresAt }
        val (code, text) = RealityEngine.waitReason(a).let { if (it.first.isEmpty()) "ELIGIBLE" to "Published: passed the source, data and filter checks" else it }
        val raw = a.agreementPercent.roundToInt()
        val band = RealityEngine.bandRecord(a.learning, raw)
        val calErr = a.learning?.calibrationErrorPoints ?: -1.0
        val ins = a.insights
        val contrib = ins?.contributions ?: emptyList()
        val sig = a.finalSignalForEvidence()
        val supporting = contrib.filter { it.verdict != Signal.WAIT && sig != Signal.WAIT && sameSide(it.points, sig) }
            .sortedByDescending { abs(it.points) }.map { PredictionPresentationState.Factor(it.title, it.points) }
        val conflicting = contrib.filter { it.verdict != Signal.WAIT && sig != Signal.WAIT && !sameSide(it.points, sig) }
            .sortedByDescending { abs(it.points) }.map { PredictionPresentationState.Factor(it.title, it.points) }
        val neutral = contrib.filter { it.verdict == Signal.WAIT || sig == Signal.WAIT }.map { it.title }
        val council = a.aiCouncil
        val ai = when {
            council == null || council.configured == 0 -> "NOT CONNECTED"
            council.eligible == 0 -> "ERROR"
            else -> "AVAILABLE"
        }
        val bots = a.multiBotEnsemble?.let { "${it.buyVotes} BUY • ${it.sellVotes} SELL • ${it.waitVotes} WAIT (rule bots)" } ?: "UNAVAILABLE"
        val b = a.brain
        return PredictionPresentationState(
            builtAtMs = now,
            decisionId = rec?.id ?: "NOT RECORDED",
            snapshotId = rec?.snapshotId?.ifBlank { null } ?: "UNAVAILABLE",
            instrument = instrument,
            provider = provider,
            sourceType = if (a.feed == null) UNKNOWN else id.type.name,
            sourceBadge = SourceRegistry.badge(a.feed?.source),
            compatibleWithModel = SourceRegistry.compatibility(id.instrument) == Compatibility.COMPATIBLE,
            timeframe = a.interval.uppercase(Locale.US),
            analysisAtMs = a.feed?.fetchedAtMs ?: 0L,
            candleTime = a.lastUpdated.ifBlank { UNKNOWN },
            rawSignal = a.rawSignal,
            finalSignal = a.overallSignal,
            reasonCode = code,
            reasonText = text,
            dataQuality = a.pulse?.dataQuality?.takeIf { it.isNotBlank() && it != "--" } ?: UNKNOWN,
            sourceState = a.sourceState.ifBlank { UNKNOWN },
            ledgerAudit = a.pulse?.modelHealth?.ifBlank { null } ?: UNKNOWN,
            researchModelStatus = researchStatus?.ifBlank { null } ?: UNKNOWN,
            regime = b?.regime ?: ins?.regime ?: UNKNOWN,
            uncertainty = b?.totalUncertainty ?: UNKNOWN,
            evidenceAgreementPct = raw,
            ruleSetupStrength = b?.qualityIndex ?: -1,
            hitRatePct = band?.accuracyPercent?.roundToInt() ?: -1,
            hitRateN = band?.decided ?: 0,
            headlineKind = when { a.overallSignal == Signal.WAIT -> "NONE"; band != null -> "HISTORICAL"; else -> "HEURISTIC" },
            calibration = when { calErr < 0 -> "UNAVAILABLE"; calErr > 10 -> "UNRELIABLE"; else -> "WITHIN_10_PTS" },
            calibrationErrorPts = calErr,
            confidenceInverted = b?.let { com.example.livegoldai.data.brain.ResearchStats.confidenceInverted(it.strongSignal) } ?: false,
            supporting = supporting,
            conflicting = conflicting,
            neutral = neutral,
            attributionAvailable = contrib.isNotEmpty(),
            mtfAvailable = a.mtfMatrix?.timeframes?.any { it.label.startsWith("PAXG") } == true,
            crossMarketAvailable = b?.crossMarket?.available == true,
            aiStatus = ai,
            botsSummary = bots,
            engineVersion = PredictionLedger.ENGINE_VERSION,
            featureVersion = com.example.livegoldai.data.brain.FeatureCatalog.VERSION,
            modelVersion = a.learning?.modelVersion?.ifBlank { null } ?: UNKNOWN,
            ledgerSize = state?.records?.size ?: 0,
            ledgerChecked = state?.results?.size ?: 0,
            secondaryReasons = secondary(a, code, researchStatus),
            restorationPending = if (a.sourceState == SourceStateMachine.RESTORING)
                Regex("(\\d+)/(\\d+)").find(a.gateDetail)?.value ?: "0/${SourceStateMachine.RESTORE_PASSES}" else "",
            isTradeSetupActive = a.tradeSetup.planStatus == PlanStamp.ACTIVE && a.overallSignal != Signal.WAIT,
            scenarioAvailable = (a.tradeSetup.planStatus == PlanStamp.RESEARCH ||
                (a.overallSignal == Signal.WAIT && (a.tradeSetup.planStatus == PlanStamp.SCENARIO || a.tradeSetup.planStatus == PlanStamp.INACTIVE_WAIT))) &&
                SourceRegistry.compatibility(id.instrument) == Compatibility.COMPATIBLE,
            quoteAtMs = a.feed?.fetchedAtMs ?: 0L,
            publishedAtMs = rec?.createdAt ?: 0L,
            provenEdge = researchStatus == "LIVE QUALIFIED",
            planStatus = a.tradeSetup.planStatus,
            forecastState = ForecastState.resolve(a.tradeSetup, a.overallSignal, researchStatus),
            horizonMin = TechnicalEngine.calculateValidityMinutes(a.interval),
            expiresAtMs = a.tradeSetup.expiresAtMs.takeIf { it > 0 } ?: (rec?.expiresAt ?: 0L),
            outcome = rec?.let { state?.resultOf(it.id)?.outcome?.name } ?: "",
            metrics = Metrics.build(a, state),
            freshness = a.tradeSetup.freshness.ifBlank { Freshness.assess(a, now) },
            closeDirectionPct = a.pulse?.directionPct ?: -1,
            closeDirectionN = a.pulse?.directionN ?: 0,
            coverage = a.pulse?.coverage ?: "",
            ledgerFirstAtMs = state?.records?.firstOrNull()?.createdAt ?: 0L,
            ledgerLastAtMs = state?.records?.lastOrNull()?.createdAt ?: 0L,
            nextReviewAtMs = ShadowLive.barMs(a.lastUpdated)?.let { it + RealityEngine.intervalMinutes(a.interval) * 60_000L } ?: 0L
        )
    }

    /** V29C: secondary factors, never shown as the main reason. Deterministic order. */
    fun secondary(a: GoldAnalysisResult, primary: String, researchStatus: String?): List<String> {
        val out = ArrayList<String>()
        if (primary == "SOURCE" || primary == "DATA" || primary == "NO_KEY") {
            if ((a.rawSignal ?: a.overallSignal) == Signal.WAIT) out.add("ENGINE_NO_SIDE")
        }
        val regime = a.brain?.regime ?: a.insights?.regime
        if (regime.equals("RANGE", true) && primary != "RANGE") out.add("RANGE_REGIME")
        if (researchStatus != "LIVE QUALIFIED") out.add("NO_PROVEN_EDGE")
        if (a.brain?.totalUncertainty == "HIGH") out.add("HIGH_UNCERTAINTY")
        if (a.mtfMatrix?.timeframes?.any { it.label.startsWith("PAXG") } != true) out.add("MTF_UNAVAILABLE")
        return out
    }

    /** Evidence is judged against what the engine itself said (raw), so a filtered WAIT still shows its support/conflict. */
    private fun GoldAnalysisResult.finalSignalForEvidence(): Signal = rawSignal ?: overallSignal

    private fun sameSide(points: Double, s: Signal) = (s == Signal.BUY && points > 0) || (s == Signal.SELL && points < 0)
}
