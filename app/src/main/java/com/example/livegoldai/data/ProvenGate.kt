package com.example.livegoldai.data

import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.ProvenStatus
import com.example.livegoldai.model.Signal
import kotlin.math.roundToInt

/**
 * V35.12 Proven-only mode (owner, 6 Nov 2026: "prediction galat na ho ... Ha karo").
 *
 * A BUY / SELL is PUBLISHED only when this exact setup has proven itself on the owner's own checked calls:
 *   setup  = timeframe + market type (PredictionLedger.regimeOf, the same value every record stores) + side
 *   record = the current engine's calls of that setup whose shown signal was that side, checked with the SAME rule as
 *            the Correct / Wrong tiles (CORRECT vs INCORRECT / INVALIDATED), one call per window (EdgeFinder.independent)
 *   proven = at least [MIN_N] such calls AND at least [MIN_PCT]% correct
 * Otherwise the engine's call is shown as WAIT ("not proven yet"), and kept as rawSignal so it stays visible.
 *
 * PUBLICATION ONLY: it runs after the ledger record and after Shadow Lab has seen the engine's call, so the ledger keeps
 * recording the engine's own call (that is what builds the record this gate needs), Shadow Lab and Precision Lab compare
 * with the unchanged engine, and no threshold, weight or old record changes. It can only turn BUY / SELL into WAIT.
 * A past record is no promise: even a setup that met the minimum can be wrong. V36-A: 30 calls at 55% is a MINIMUM
 * SAMPLE, not proof of an edge (17/30 has a 95% range of about 39-73%), so it is shown as "minimum sample met" with its
 * range, never as "proven".
 */
object ProvenGate {
    const val VERSION = "PG1"
    const val MIN_N = 30
    const val MIN_PCT = 55

    fun newsActive(a: GoldAnalysisResult) = a.newsMode != null || a.newsTradingPlan?.isNewsActive == true || a.isNewsModeTriggered
    fun setupKey(interval: String, regime: String, side: Signal) = interval.lowercase() + "|" + regime + "|" + side.name

    fun status(state: LedgerState, interval: String, regime: String, side: Signal): ProvenStatus {
        val pool = EdgeFinder.independent(state.records.filter { r ->
            r.interval.equals(interval, true) && r.regime == regime && r.finalSignal == side &&
                r.engineVersion == PredictionLedger.ENGINE_VERSION &&
                // V36-A: only results checked with the current method (EV2); older methods are not mixed in
                state.resultOf(r.id)?.evalVersion == PredictionLedger.EVAL_VERSION &&
                when (state.resultOf(r.id)?.outcome) { LedgerOutcome.CORRECT, LedgerOutcome.INCORRECT, LedgerOutcome.INVALIDATED -> true; else -> false }
        })
        val n = pool.size
        val k = pool.count { state.resultOf(it.id)?.outcome == LedgerOutcome.CORRECT }
        val pct = if (n == 0) -1 else (100.0 * k / n).roundToInt()
        val (lo, hi) = PrecisionLab.wilson(k, n)
        val proven = n >= MIN_N && 100 * k >= MIN_PCT * n
        return ProvenStatus(setupKey(interval, regime, side), side, n, k, pct, lo, hi, MIN_N, MIN_PCT, proven, false)
    }

    /** Publish the engine's BUY / SELL only for a proven setup; WAIT stays WAIT. [state] null = unknown -> fail closed. */
    fun apply(a: GoldAnalysisResult, state: LedgerState?): GoldAnalysisResult {
        val side = a.overallSignal
        if (side != Signal.BUY && side != Signal.SELL) return a
        val regime = PredictionLedger.regimeOf(a.recentCandles, newsActive(a))
        val st = state?.let { status(it, a.interval, regime, side) }
            ?: ProvenStatus(setupKey(a.interval, regime, side), side, 0, 0, -1, -1, -1, MIN_N, MIN_PCT, false, false)
        return if (st.proven) a.copy(proven = st)
        else a.copy(overallSignal = Signal.WAIT, rawSignal = a.rawSignal ?: side, proven = st.copy(held = true))
    }

    /** "TREND UP" etc. for text */
    fun regimeLabel(key: String) = key.split('|').getOrNull(1)?.replace('_', ' ') ?: "--"
}
