package com.example.livegoldai.data

import com.example.livegoldai.model.CandleBar
import com.example.livegoldai.model.PrecisionStat
import com.example.livegoldai.model.Signal
import com.example.livegoldai.model.WhyCause
import com.example.livegoldai.model.WhySlice
import com.example.livegoldai.model.WhyWrongReport
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * V35.8 Precision Lab (owner, 4 Nov 2026: "prediction jyadha se jyada correct"). SHADOW ONLY: never changes a shown call.
 *
 * Three challengers, fixed here BEFORE any forward data (hypotheses from the old 1501-call history; tested only on calls
 * recorded from now on, so the old data that suggested them is never used to judge them). Each can only turn the
 * production BUY / SELL into WAIT; its decision is written into the record when the call is made ("ch:" keys), so it is
 * immutable and cannot look ahead. They are compared with production on the SAME decided calls, one call per window.
 *   LATE  : agreement >= 85% AND price already >= 1.5 ATR away from its 20-bar mean   (old data: OVERCONFIDENCE was the
 *           top failure and higher agreement was right LESS often: a "late" signal after the move)
 *   QUIET : market type RANGE or a news event                                         (old data: range / news weakest)
 *   SHORT : 2m / 3m / 4m timeframes                                                   (old data: 2m weakest, 45%)
 * A challenger is PROMISING only with >= 50 kept and >= 20 skipped calls, kept >= production + 3 points and skipped
 * <= production - 3 points. Nothing is promoted automatically: that needs the owner's approval.
 */
object PrecisionLab {
    const val VERSION = "PL1"
    const val LATE = "ch:late"; const val QUIET = "ch:quiet"; const val SHORT = "ch:short"
    const val LATE_AGREEMENT = 85
    const val LATE_STRETCH_ATR = 1.5
    val SHORT_TFS = setOf("2m", "3m", "4m")
    // V35.14 (owner, 7 Nov 2026): two more hypotheses from the owner's failed 5M calls of 5-6 Nov; like the first three
    // they are judged ONLY on calls recorded after they existed, so the calls that suggested them never judge them
    const val CANDLE = "ch:candle"; const val REPEAT = "ch:repeat"
    const val REPEAT_MS = 30 * 60_000L
    const val MIN_KEPT = 50
    const val MIN_SKIPPED = 20

    /** |last close - mean of the last 20 closes| in ATRs (0 when not computable) */
    fun stretch(candles: List<CandleBar>, atr: Double): Double {
        if (candles.size < 20 || atr <= 0.0) return 0.0
        val last20 = candles.takeLast(20).map { it.close }
        return abs(last20.last() - last20.average()) / atr
    }

    /** the three challengers' calls for one record; a production WAIT stays WAIT */
    fun decide(side: Signal, agreement: Int, stretchAtr: Double, regime: String, newsActive: Boolean, interval: String): Map<String, Signal> {
        if (side == Signal.WAIT) return mapOf(LATE to Signal.WAIT, QUIET to Signal.WAIT, SHORT to Signal.WAIT)
        return mapOf(
            LATE to if (agreement >= LATE_AGREEMENT && stretchAtr >= LATE_STRETCH_ATR) Signal.WAIT else side,
            QUIET to if (regime == "RANGE" || regime == "NEWS_EVENT" || newsActive) Signal.WAIT else side,
            SHORT to if (interval.lowercase() in SHORT_TFS) Signal.WAIT else side
        )
    }

    /**
     *  CANDLE : WAIT when the Candlestick pillar votes against the call
     *  REPEAT : WAIT when the engine already made the same call on this timeframe in the last 30 min within 1 ATR of
     *           the price (the same zone again, as in 23:54-00:39 on 5-6 Nov)
     */
    fun decideExtra(side: Signal, candleOpposes: Boolean, repeatZone: Boolean): Map<String, Signal> =
        if (side == Signal.WAIT) mapOf(CANDLE to Signal.WAIT, REPEAT to Signal.WAIT)
        else mapOf(CANDLE to if (candleOpposes) Signal.WAIT else side, REPEAT to if (repeatZone) Signal.WAIT else side)

    private fun decided(state: LedgerState) = state.records.filter { r -> r.finalSignal != Signal.WAIT &&
        when (state.resultOf(r.id)?.outcome) { LedgerOutcome.CORRECT, LedgerOutcome.INCORRECT, LedgerOutcome.INVALIDATED -> true; else -> false } }
    private fun right(state: LedgerState, r: LedgerRecord) = state.resultOf(r.id)?.outcome == LedgerOutcome.CORRECT
    private fun pct(k: Int, n: Int) = if (n == 0) -1 else (100.0 * k / n).roundToInt()

    /** Wilson 95% interval in whole percent */
    fun wilson(k: Int, n: Int): Pair<Int, Int> {
        if (n == 0) return -1 to -1
        val z = 1.96; val p = k.toDouble() / n
        val d = 1 + z * z / n
        val c = (p + z * z / (2 * n)) / d
        val h = z * sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / d
        return ((c - h) * 100).roundToInt().coerceAtLeast(0) to ((c + h) * 100).roundToInt().coerceAtMost(100)
    }

    fun evaluate(state: LedgerState): List<PrecisionStat> {
        val all = EdgeFinder.independent(decided(state))
        return listOf(
            Triple(LATE, "LATE SIGNAL (agreement ≥ 85% and stretched ≥ 1.5 ATR)", "WAIT when agreement >= 85% and price >= 1.5 ATR from its 20-bar mean"),
            Triple(QUIET, "RANGE / NEWS", "WAIT in a range market or a news event"),
            Triple(SHORT, "SHORT TIMEFRAMES (2m–4m)", "WAIT on 2m, 3m and 4m"),
            Triple(CANDLE, "CANDLESTICK AGAINST", "WAIT when the Candlestick pillar votes against the call"),
            Triple(REPEAT, "SAME ZONE AGAIN", "WAIT when the same call was made in the last 30 min within 1 ATR")
        ).map { (key, title, rule) ->
            // only calls recorded after the challenger existed, and (V35.9) only the current engine: another engine's calls are
            // a different production to compare with
            val pool = all.filter { key in it.sources && it.engineVersion == PredictionLedger.ENGINE_VERSION }
            val kept = pool.filter { it.sources[key] != Signal.WAIT }
            val skipped = pool.filter { it.sources[key] == Signal.WAIT }
            val pH = pct(pool.count { right(state, it) }, pool.size)
            val kH = pct(kept.count { right(state, it) }, kept.size)
            val sH = pct(skipped.count { right(state, it) }, skipped.size)
            val (lo, hi) = wilson(kept.count { right(state, it) }, kept.size)
            val verdict = when {
                kept.size < MIN_KEPT || skipped.size < MIN_SKIPPED -> "COLLECTING"
                kH >= pH + 3 && sH <= pH - 3 -> "PROMISING"
                kH < pH -> "WORSE"
                else -> "NO IMPROVEMENT"
            }
            PrecisionStat(key, title, rule, pool.size, pH, kept.size, kH, skipped.size, sH, lo, hi,
                if (pool.isEmpty()) -1 else 100 * kept.size / pool.size, verdict)
        }
    }

    private val LABELS = mapOf(
        "OVERCONFIDENCE" to "High agreement (≥ 80%) but wrong",
        "RANGE_WHIPSAW" to "Range market whipsaw",
        "STOP_HUNT" to "Stop hit, then the call came true",
        "FAKE_MOVE_REVERSAL" to "Moved the right way first, then reversed",
        "TIMEFRAME_CONFLICT" to "Against the higher timeframe",
        "NEWS_SHOCK" to "News shock",
        "MISSED_CONTRADICTION" to "A pillar warned the other way",
        "INSUFFICIENT_EDGE" to "Low agreement (< 70%)",
        "VOLATILITY_EXPANSION" to "Volatility jumped (> 3 ATR range)"
    )

    /** what the wrong calls had in common (tags written by the verifier) and hit-rate by slice, all timeframes */
    fun whyWrong(state: LedgerState): WhyWrongReport? {
        val dec = decided(state)
        if (dec.isEmpty()) return null
        val wrong = dec.filter { !right(state, it) }
        val tags = wrong.flatMap { state.resultOf(it.id)?.tags ?: emptyList() }.filter { it != "UNKNOWN" }.groupingBy { it }.eachCount()
        val causes = tags.entries.sortedByDescending { it.value }.map { (t, c) -> WhyCause(t, LABELS[t] ?: t.replace('_', ' ').lowercase(), c, pct(c, wrong.size)) }
        fun slice(group: String, key: (LedgerRecord) -> String) = dec.groupBy(key).map { (name, l) -> WhySlice(group, name, l.size, pct(l.count { right(state, it) }, l.size)) }.sortedBy { it.name }
        val slices = slice("Agreement") { r -> when { r.confidence >= 85 -> "85%+"; r.confidence >= 70 -> "70-84%"; else -> "<70%" } } +
            slice("Market type") { it.regime.ifBlank { "UNKNOWN" } } +
            slice("News") { if (it.newsActive) "news window" else "no news" } +
            slice("Timeframe") { it.interval.uppercase() }
        return WhyWrongReport(dec.size, wrong.size, causes, slices)
    }
}
