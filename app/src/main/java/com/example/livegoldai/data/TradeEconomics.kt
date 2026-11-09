package com.example.livegoldai.data

import com.example.livegoldai.model.Signal
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * V36-B money check (owner, 9 Nov 2026: "words best app ... prediction itne sahi"). READ-ONLY and HYPOTHETICAL:
 * nothing here changes a call, a record or a gate. It answers "what would following these calls have done to the
 * account, after costs?", because a hit-rate alone can hide a losing strategy.
 *  - one trade at a time: overlapping windows are thinned (EdgeFinder.independent), like a real account
 *  - each trade by its own first touch: target first = +target, stop first = -stop, else the close move
 *  - minus an ASSUMED cost per trade ([COST_USD]: spread + slippage on XAU/USD); the owner's broker will differ
 *  - results that cannot be checked (UNCERTAIN / AMBIGUOUS / no data) are left out, never guessed
 * Amounts are $ per 1 unit of gold price move (multiply by the position size).
 */
object TradeEconomics {
    const val SPREAD_USD = 0.50
    const val SLIPPAGE_USD = 0.20
    const val COST_USD = SPREAD_USD + SLIPPAGE_USD

    data class Book(
        val trades: Int,          // non-overlapping trades
        val overlapping: Int,     // all decided calls before thinning
        val correct: Int, val wrong: Int,
        val netUsd: Double,       // after costs
        val grossUsd: Double,     // before costs
        val avgUsd: Double,
        val maxDrawdownUsd: Double
    ) {
        val hitPct: Int get() = if (correct + wrong == 0) -1 else (100.0 * correct / (correct + wrong)).roundToInt()
    }
    data class Summary(val interval: String, val all: Book, val current: Book, val shown: Book, val held: Book)

    private val COUNTED = setOf(LedgerOutcome.CORRECT, LedgerOutcome.INCORRECT, LedgerOutcome.INVALIDATED, LedgerOutcome.SIDEWAYS)

    /** gross $ of one call by first touch, null when it cannot be counted */
    fun gross(r: LedgerRecord, res: LedgerResult): Double? {
        if (r.finalSignal == Signal.WAIT || res.outcome !in COUNTED) return null
        val dir = if (r.finalSignal == Signal.SELL) -1.0 else 1.0
        return when {
            res.tpHitAt != 0L && r.tpDistance > 0 -> r.tpDistance
            res.outcome == LedgerOutcome.INVALIDATED && r.slDistance > 0 -> -r.slDistance
            else -> res.move * dir
        }
    }

    fun book(state: LedgerState, records: List<LedgerRecord>): Book {
        val decided = records.filter { r -> state.resultOf(r.id)?.let { gross(r, it) } != null }
        val trades = EdgeFinder.independent(decided).sortedBy { it.createdAt }
        var cum = 0.0; var peak = 0.0; var dd = 0.0; var gross = 0.0; var c = 0; var w = 0
        for (r in trades) {
            val res = state.resultOf(r.id)!!
            val g = gross(r, res)!!
            gross += g; cum += g - COST_USD
            peak = max(peak, cum); dd = max(dd, peak - cum)
            when (res.outcome) { LedgerOutcome.CORRECT -> c++; LedgerOutcome.INCORRECT, LedgerOutcome.INVALIDATED -> w++; else -> {} }
        }
        return Book(trades.size, decided.size, c, w, cum, gross, if (trades.isEmpty()) 0.0 else cum / trades.size, dd)
    }

    fun build(state: LedgerState, interval: String): Summary {
        val mine = state.records.filter { it.interval == interval && it.finalSignal != Signal.WAIT && it.engineVersion == PredictionLedger.ENGINE_VERSION }
        val current = mine.filter { state.resultOf(it.id)?.evalVersion == PredictionLedger.EVAL_VERSION }
        return Summary(interval, book(state, mine), book(state, current),
            book(state, current.filter { it.publishedSignal == it.finalSignal }),
            book(state, current.filter { it.publishedSignal == Signal.WAIT }))
    }

    fun usd(v: Double) = (if (v >= 0) "+" else "-") + "$" + String.format(java.util.Locale.US, "%.2f", kotlin.math.abs(v))
}
