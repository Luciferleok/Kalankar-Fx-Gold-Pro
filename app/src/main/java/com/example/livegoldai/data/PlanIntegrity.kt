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
 *  NO_DATA           no usable live data
 */
object PlanStamp {
    const val ACTIVE = "ACTIVE"
    const val INACTIVE_WAIT = "INACTIVE_WAIT"
    const val SCENARIO = "SCENARIO"
    const val STOPPED = "STOPPED_INTRABAR"
    const val NO_DATA = "NO_DATA"

    fun isActionable(t: TradeSetup): Boolean = t.planStatus == ACTIVE

    fun stamp(a: GoldAnalysisResult, state: LedgerState?, now: Long = System.currentTimeMillis()): GoldAnalysisResult {
        val rec = state?.records?.lastOrNull { it.interval.equals(a.interval, true) && it.barId == a.lastUpdated }
        val raw = a.rawSignal ?: a.overallSignal
        var touched = ""
        val status = when {
            a.isSimulatedFallback || a.feed?.isLive != true -> NO_DATA
            a.overallSignal == Signal.WAIT -> if (raw != Signal.WAIT) INACTIVE_WAIT else SCENARIO
            rec != null && rec.finalSignal == a.overallSignal && rec.slDistance > 0 -> {
                touched = stopTouch(a, rec.price, rec.slDistance, rec.barId)
                if (touched.isNotEmpty()) STOPPED else ACTIVE
            }
            else -> ACTIVE
        }
        val t = a.tradeSetup
        return a.copy(tradeSetup = t.copy(
            planStatus = status,
            snapshotId = rec?.snapshotId?.ifBlank { null } ?: "S-${a.interval}-${a.lastUpdated.replace(" ", "T")}",
            instrument = SourceRegistry.badge(a.feed?.source),
            dataSource = a.feed?.source ?: "UNKNOWN",
            sourceTimeMs = a.feed?.fetchedAtMs ?: 0L,
            generatedAtMs = now,
            modelVersion = "${PredictionLedger.ENGINE_VERSION} • ${a.learning?.modelVersion ?: "--"}",
            stopTouched = touched,
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
