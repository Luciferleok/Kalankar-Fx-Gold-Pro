package com.example.livegoldai.data

import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import com.example.livegoldai.model.TradeSetup
import java.util.Locale
import kotlin.math.abs

/**
 * V31-B: risk / reward computed from the exact entry, stop and target shown, never a fixed text.
 * Convention shown on screen: "risk:reward", e.g. TP1 1:1.04 means 1 unit risked for 1.04 units of reward.
 */
object RiskMath {
    fun risk(entry: Double, stop: Double): Double = abs(entry - stop)
    fun reward(entry: Double, target: Double): Double = abs(target - entry)

    /** reward / risk, or NaN when the stop distance is zero or the target is on the wrong side. */
    fun rewardPerRisk(entry: Double, stop: Double, target: Double, signal: Signal): Double {
        val r = risk(entry, stop)
        if (r <= 1e-9) return Double.NaN
        val rightSide = when (signal) {
            Signal.BUY -> target > entry && stop < entry
            Signal.SELL -> target < entry && stop > entry
            Signal.WAIT -> true
        }
        return if (rightSide) reward(entry, target) / r else Double.NaN
    }

    private fun fmt(x: Double) = if (x.isNaN()) "--" else String.format(Locale.US, "1:%.2f", x)

    /** "TP1 1:1.04 • TP2 1:2.08": each target with its own ratio, never TP2's ratio under TP1. */
    fun label(entry: Double, stop: Double, tp1: Double, tp2: Double, signal: Signal): String =
        "TP1 " + fmt(rewardPerRisk(entry, stop, tp1, signal)) + " • TP2 " + fmt(rewardPerRisk(entry, stop, tp2, signal))
}

/**
 * V31-C/E/F: the status of the plan on screen and where its numbers came from.
 *  ACTIVE            the published decision is BUY/SELL and its stop has not been touched
 *  INACTIVE_WAIT     the engine wanted BUY/SELL but the final decision is WAIT: no trade, levels are only a record
 *  SCENARIO          the engine itself said WAIT: levels are a conditional "if it breaks out" scenario
 *  STOPPED_INTRABAR  the recorded call's stop was touched inside the candle: the trade is over even before the close
 *  NO_DATA           no usable live data, or the data is STALE / OFFLINE (V29D)
 *  RESEARCH_SIGNAL   V29D: a published BUY/SELL whose predictive edge is NOT proven (no LIVE QUALIFIED model):
 *                    an unverified research signal; its levels are conditional research, never a trade instruction
 *  EXPIRED           V29D: the prediction horizon has passed
 */
object PlanStamp {
    const val ACTIVE = "ACTIVE"
    const val INACTIVE_WAIT = "INACTIVE_WAIT"
    const val SCENARIO = "SCENARIO"
    const val STOPPED = "STOPPED_INTRABAR"
    const val NO_DATA = "NO_DATA"
    const val RESEARCH = "RESEARCH_SIGNAL"
    const val EXPIRED = "EXPIRED"

    fun isActionable(t: TradeSetup): Boolean = t.planStatus == ACTIVE

    /**
     * V29D: horizon and a STABLE expiry. Before: validUntil = analysis time + horizon, so every 60 s refresh pushed
     * the expiry forward (a 5M call showed 21-25 min forever). Now: the ledger record's expiry, else candle open + horizon.
     */
    fun horizonMin(interval: String): Int = TechnicalEngine.calculateValidityMinutes(interval)
    fun stableExpiry(a: GoldAnalysisResult, rec: LedgerRecord?): Long =
        rec?.expiresAt ?: ShadowLive.barMs(a.lastUpdated)?.let { it + horizonMin(a.interval) * 60_000L } ?: 0L

    /**
     * @param researchStatus Shadow Lab live status of this timeframe. Only "LIVE QUALIFIED" makes a BUY/SELL actionable.
     */
    fun stamp(a: GoldAnalysisResult, state: LedgerState?, now: Long = System.currentTimeMillis(), researchStatus: String? = null): GoldAnalysisResult {
        // V34.1a: a ledger record belongs to this plan only when it is the SAME provider's record of the same candle.
        // Before: interval + candle only, so an OANDA plan took a Twelve Data record's id, expiry and stop check.
        val sameCandle = state?.records?.filter { it.interval.equals(a.interval, true) && it.barId == a.lastUpdated } ?: emptyList()
        val provider = SourceRegistry.identify(a.feed?.source).provider
        val rec = sameCandle.lastOrNull { provider != ProviderId.NONE && SourceRegistry.identify(it.dataSource).provider == provider }
        val otherRec = if (rec == null) sameCandle.lastOrNull() else null
        val raw = a.rawSignal ?: a.overallSignal
        val fresh = Freshness.assess(a, now)
        val expiry = stableExpiry(a, rec)
        var touched = ""
        val status = when {
            a.isSimulatedFallback || a.feed?.isLive != true -> NO_DATA
            fresh == Freshness.STALE || fresh == Freshness.OFFLINE || fresh == Freshness.CLOSED -> NO_DATA
            a.overallSignal == Signal.WAIT -> if (raw != Signal.WAIT) INACTIVE_WAIT else SCENARIO
            expiry in 1 until now -> EXPIRED
            rec != null && rec.finalSignal == a.overallSignal && rec.slDistance > 0 &&
                stopTouch(a, rec.price, rec.slDistance, rec.barId).also { touched = it }.isNotEmpty() -> STOPPED
            researchStatus != "LIVE QUALIFIED" -> RESEARCH
            // V29D-H1: Shadow Lab models are trained and qualified on Twelve Data XAU/USD only. A qualification never
            // carries over to another instrument, or to a source that the gate has not fully restored.
            SourceRegistry.compatibility(SourceRegistry.identify(a.feed?.source).instrument) != Compatibility.COMPATIBLE -> RESEARCH
            a.sourceState.isNotEmpty() && a.sourceState != SourceStateMachine.PRIMARY_ACTIVE -> RESEARCH
            // V32: Shadow Lab qualifies on Twelve Data XAU/USD only; OANDA data stays research until qualified on OANDA itself
            SourceRegistry.identify(a.feed?.source).provider != ProviderId.TWELVE_DATA -> RESEARCH
            else -> ACTIVE
        }
        val t = a.tradeSetup
        return a.copy(nextPrediction = a.nextPrediction?.copy(validUntilTimestamp = if (expiry > 0) expiry else a.nextPrediction.validUntilTimestamp),
            tradeSetup = t.copy(
            horizonMin = horizonMin(a.interval),
            expiresAtMs = expiry,
            freshness = fresh,
            planStatus = status,
            snapshotId = rec?.snapshotId?.ifBlank { null } ?: "S-${a.interval}-${a.lastUpdated.replace(" ", "T")}",
            instrument = SourceRegistry.badge(a.feed?.source),
            dataSource = a.feed?.source ?: "UNKNOWN",
            sourceTimeMs = a.feed?.fetchedAtMs ?: 0L,
            generatedAtMs = now,
            modelVersion = "${PredictionLedger.ENGINE_VERSION} • ${a.learning?.modelVersion ?: "--"}",
            stopTouched = touched,
            // V34.1a: this candle is already in the ledger from another provider (one record per candle; nothing hidden)
            recordedByOtherProvider = otherRec?.let { SourceRegistry.badge(it.dataSource).ifBlank { it.dataSource.ifBlank { "an older app version" } } } ?: "",
            // V31-B: the ratio always matches the entry, stop and targets of this same snapshot
            riskRewardRatio = RiskMath.label(t.entryPrice, t.stopLoss, t.takeProfit1, t.takeProfit2, t.signal)
        ))
    }

    /** Candle time (from this snapshot's own candles) at which the recorded stop was touched, "" if never. */
    fun stopTouch(a: GoldAnalysisResult, recPrice: Double, slDistance: Double, fromBar: String): String {
        val buy = a.overallSignal == Signal.BUY
        val level = if (buy) recPrice - slDistance else recPrice + slDistance
        val bars = a.recentCandles.filter { it.datetime >= fromBar }
        val hit = bars.firstOrNull { if (buy) it.low <= level else it.high >= level } ?: return ""
        return hit.datetime
    }
}

/**
 * V29D: one freshness verdict for the data behind a decision. Thresholds follow how this app actually gets data:
 *  the screen re-fetches every 60 s (LIVE = quote <= 120 s old), and when credits are low it may reuse one fetch for
 *  up to one candle (V29A.11) (DELAYED = <= one candle). Older than that, or a newest candle older than 2 candles + 2 min
 *  (the same limit as the source gate), is STALE: the decision may describe a market that has moved on.
 */
object Freshness {
    const val LIVE = "LIVE"; const val DELAYED = "DELAYED"; const val STALE = "STALE"; const val OFFLINE = "OFFLINE"; const val CLOSED = "MARKET CLOSED"

    fun assess(a: GoldAnalysisResult, now: Long): String {
        val f = a.feed
        if (f == null || !f.isLive || a.isSimulatedFallback) return OFFLINE
        if (PredictionLedger.isMarketClosed(now)) return CLOSED
        val ivMs = RealityEngine.intervalMinutes(a.interval) * 60_000L
        val quoteAge = now - f.fetchedAtMs
        val candleAge = ShadowLive.barMs(a.lastUpdated)?.let { now - it } ?: Long.MAX_VALUE
        return when {
            f.fetchedAtMs <= 0L -> OFFLINE
            candleAge > 2 * ivMs + 120_000L -> STALE
            quoteAge <= 120_000L -> LIVE
            quoteAge <= maxOf(120_000L, ivMs) -> DELAYED
            else -> STALE
        }
    }
}
