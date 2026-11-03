package com.example.livegoldai.data

import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import kotlin.math.sqrt

/**
 * V34.7 Edge Finder: WHERE, if anywhere, the rule direction has been right more often than a coin flip.
 *
 * Read-only statistics on the existing ledger (v1 direction outcome: CORRECT vs INCORRECT / INVALIDATED; WAIT,
 * sideways and uncertain calls are not counted). Calls are grouped by one dimension at a time: timeframe, session,
 * regime, side, rule-agreement band and provider. For each group a 99.9% Wilson interval is computed. Because many groups
 * are compared, the bar is strict on purpose:
 *   PROVEN EDGE      N >= [MIN_N] and the lower 99.9% bound > 50%
 *   WORSE THAN CHANCE N >= [MIN_N] and the upper 99.9% bound < 50%
 *   NO PROVEN EDGE   everything else (including too few calls)
 * V34.8.1: only INDEPENDENT calls are counted. A 15m call is judged over 1 hour, so four back-to-back 15m calls share
 * most of one price move and are not four pieces of evidence (the Wilson interval assumes independent calls). Per
 * timeframe, a call is kept only when it was made at or after the end of the previous kept call's window; calls of other
 * providers on the same moment are dropped the same way. Before: every decided call was counted, so N and the certainty
 * were overstated (screenshot 2 Nov: "15M: 61% right of 319 · PROVEN EDGE" counted overlapping windows).
 * V35.3 (statistical honesty): a group above the 99.9% bound is "HISTORICAL EDGE" (past data only, NOT forward-validated),
 * never "PROVEN". Cross-timeframe groups (session, regime, side, agreement, provider, engine, all) count one call per time
 * window across ALL timeframes (a 5m and a 15m call on the same moment are one piece of evidence). Engine versions are a
 * dimension of their own. For the forecast on screen, a positive group is shown only together with any matching group that
 * points the other way (MIXED EVIDENCE); a lone selected subgroup is never presented as proof for today's call.
 * Forward validation (a frozen rule tested only on calls made after it was frozen) does not exist yet: nothing is labelled so.
 * Nothing here changes a forecast, a level, the ledger, learning or qualification. Past results are not a promise.
 */
object EdgeFinder {
    const val MIN_N = 50
    private const val Z = 3.291            // 99.9% two-sided (many groups are compared: strict on purpose)
    const val HIST = "HISTORICAL EDGE"
    const val WORSE = "WORSE THAN CHANCE"
    const val NONE = "NO EDGE SHOWN"
    const val FEW = "INSUFFICIENT SAMPLE"
    /** dimensions that pool several timeframes: one call per time window across all timeframes */
    private val CROSS = listOf("Session", "Regime", "Side", "Agreement", "Provider", "Engine")

    data class Group(val dimension: String, val value: String, val right: Int, val n: Int) {
        val pct: Double get() = if (n == 0) 0.0 else 100.0 * right / n
        val low: Double get() = wilson(right, n).first * 100
        val high: Double get() = wilson(right, n).second * 100
        val verdict: String get() = when {
            n < MIN_N -> FEW
            low > 50.0 -> HIST
            high < 50.0 -> WORSE
            else -> NONE
        }
        fun line(): String = "$dimension $value: ${Math.round(pct)}% right of $n independent calls (99.9% range ${Math.round(low)}–${Math.round(high)}%) · " +
            if (verdict == HIST) "$HIST (past data only, not forward-validated)" else verdict
    }

    fun wilson(k: Int, n: Int): Pair<Double, Double> {
        if (n <= 0) return 0.0 to 1.0
        val p = k.toDouble() / n; val z2 = Z * Z
        val c = (p + z2 / (2 * n)) / (1 + z2 / n)
        val h = Z * sqrt(p * (1 - p) / n + z2 / (4.0 * n * n)) / (1 + z2 / n)
        return (c - h).coerceAtLeast(0.0) to (c + h).coerceAtMost(1.0)
    }

    fun band(conf: Int): String = when { conf >= 80 -> "80%+"; conf >= 70 -> "70-79%"; conf >= 60 -> "60-69%"; else -> "<60%" }
    fun providerOf(src: String): String = when (SourceRegistry.identify(src).provider) {
        ProviderId.OANDA -> "OANDA"; ProviderId.TWELVE_DATA -> "Twelve Data"; ProviderId.NONE -> "older version"; else -> "proxy"
    }
    fun engineOf(v: String): String = v.ifBlank { "older" }

    /** decided v1 calls with their dimension values */
    private data class Call(val right: Boolean, val keys: Map<String, String>)

    /** V34.8.1: per timeframe, keep a call only when its window starts at or after the previous kept call's window ended. */
    fun independent(records: List<LedgerRecord>): List<LedgerRecord> =
        records.groupBy { it.interval.lowercase() }.values.flatMap { thin(it) }

    /** V35.3: one call per time window across ALL timeframes (for groups that pool timeframes). */
    fun independentAcross(records: List<LedgerRecord>): List<LedgerRecord> = thin(records)

    private fun thin(list: List<LedgerRecord>): List<LedgerRecord> {
        var end = Long.MIN_VALUE
        return list.sortedWith(compareBy({ it.createdAt }, { it.id })).filter { r ->
            if (r.createdAt >= end) { end = maxOf(r.expiresAt, r.createdAt + 1); true } else false
        }
    }

    private fun decided(state: LedgerState): List<LedgerRecord> = state.records.filter { r ->
        r.finalSignal != Signal.WAIT && when (state.resultOf(r.id)?.outcome) {
            LedgerOutcome.CORRECT, LedgerOutcome.INCORRECT, LedgerOutcome.INVALIDATED -> true; else -> false }
    }

    private fun toCall(r: LedgerRecord, state: LedgerState): Call = Call(state.resultOf(r.id)?.outcome == LedgerOutcome.CORRECT,
        mapOf("Timeframe" to r.interval.uppercase(), "Session" to r.session.ifBlank { "UNKNOWN" }, "Regime" to r.regime.ifBlank { "UNKNOWN" },
            "Side" to r.finalSignal.name, "Agreement" to band(r.confidence), "Provider" to providerOf(r.dataSource), "Engine" to engineOf(r.engineVersion)))

    /** per-timeframe thinning (Timeframe groups) */
    private fun calls(state: LedgerState): List<Call> = independent(decided(state)).map { toCall(it, state) }
    /** thinning across timeframes (all other groups and the "All calls" line) */
    private fun callsAcross(state: LedgerState): List<Call> = independentAcross(decided(state)).map { toCall(it, state) }

    fun groups(state: LedgerState): List<Group> {
        val per = calls(state)
        fun of(dim: String, cs: List<Call>) = cs.groupBy { it.keys.getValue(dim) }.map { (v, list) -> Group(dim, v, list.count { it.right }, list.size) }
        // V35.3-F: pooled dimensions are thinned WITHIN each group (group first, then one call per time window across
        // timeframes). Before: one global thinning first, which kept whichever provider / engine came first in a window and
        // under-counted the other (a provider with 10 clean calls showed n=0). Group counts are not additive: no grand total.
        val dec = decided(state)
        val pooled = CROSS.flatMap { dim ->
            dec.groupBy { toCall(it, state).keys.getValue(dim) }.map { (v, rs) ->
                val kept = independentAcross(rs).map { toCall(it, state) }
                Group(dim, v, kept.count { it.right }, kept.size)
            }
        }
        return of("Timeframe", per) + pooled
    }

    /** SYSTEM lines: all calls, historical edges, worse-than-chance groups, the current engine, then a one-line summary */
    fun summary(state: LedgerState): List<String> {
        val gs = groups(state)
        if (gs.isEmpty()) return emptyList()
        val total = callsAcross(state); val all = Group("All", "calls", total.count { it.right }, total.size)
        val hist = gs.filter { it.verdict == HIST }.sortedByDescending { it.low }
        val worse = gs.filter { it.verdict == WORSE }.sortedBy { it.high }
        val eng = gs.firstOrNull { it.dimension == "Engine" && it.value == PredictionLedger.ENGINE_VERSION }
        return listOf(all.line()) + hist.map { it.line() } + worse.map { it.line() } +
            listOf(eng?.let { "Current engine ${PredictionLedger.ENGINE_VERSION}: " + it.line().substringAfter(": ") } ?: "Current engine ${PredictionLedger.ENGINE_VERSION}: no checked calls yet") +
            (if (hist.isEmpty()) listOf("No group shows a historical edge (99.9% test, N >= $MIN_N independent calls).") else emptyList()) +
            "Historical groups are past data only: none is forward-validated, none is today's win chance."
    }

    /**
     * The evidence for the forecast on screen: every matching group (its timeframe, current session, regime, side,
     * agreement band, provider and the current engine). A historical edge is shown only together with any matching group
     * that points the other way; worse-than-chance groups come first.
     */
    fun forForecast(a: GoldAnalysisResult, state: LedgerState, now: Long): String {
        if (a.overallSignal == Signal.WAIT) return ""
        val newsActive = a.newsMode != null || a.newsTradingPlan?.isNewsActive == true || a.isNewsModeTriggered
        val mine = mapOf("Timeframe" to a.interval.uppercase(), "Session" to PredictionLedger.sessionOf(now),
            "Regime" to PredictionLedger.regimeOf(a.recentCandles, newsActive), "Side" to a.overallSignal.name,
            "Agreement" to band(a.agreementPercent.toInt()), "Provider" to providerOf(a.feed?.source ?: ""), "Engine" to PredictionLedger.ENGINE_VERSION)
        val gs = groups(state).filter { mine[it.dimension] == it.value }
        val worse = gs.filter { it.verdict == WORSE }.minByOrNull { it.high }
        val best = gs.filter { it.verdict == HIST }.maxByOrNull { it.low }
        // a matching group with enough calls whose own hit rate is below 50% contradicts a positive group
        val against = gs.filter { it.n >= MIN_N && it.pct < 50.0 && it.verdict != WORSE }.minByOrNull { it.pct }
        return when {
            worse != null -> "Caution: ${worse.line()}"
            best != null && against != null -> "MIXED EVIDENCE: ${best.line()} BUT ${against.line()}"
            best != null -> "HISTORICAL GROUP EVIDENCE: ${best.line()}"
            gs.none { it.n >= MIN_N } -> "$FEW for this setup (fewer than $MIN_N independent calls in every matching group)"
            else -> "No edge shown for this setup (timeframe, session, regime, side, agreement, provider, engine all checked)"
        }
    }
}
