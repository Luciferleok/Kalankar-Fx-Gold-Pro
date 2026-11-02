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
 * Nothing here changes a forecast, a level, the ledger, learning or qualification. Past results are not a promise.
 */
object EdgeFinder {
    const val MIN_N = 50
    private const val Z = 3.291            // 99.9% two-sided (many groups are compared: strict on purpose)

    data class Group(val dimension: String, val value: String, val right: Int, val n: Int) {
        val pct: Double get() = if (n == 0) 0.0 else 100.0 * right / n
        val low: Double get() = wilson(right, n).first * 100
        val high: Double get() = wilson(right, n).second * 100
        val verdict: String get() = when {
            n < MIN_N -> "TOO FEW"
            low > 50.0 -> "PROVEN EDGE"
            high < 50.0 -> "WORSE THAN CHANCE"
            else -> "NO PROVEN EDGE"
        }
        fun line(): String = "$dimension $value: ${Math.round(pct)}% right of $n (99.9% range ${Math.round(low)}–${Math.round(high)}%) · $verdict"
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

    /** decided v1 calls with their dimension values */
    private data class Call(val right: Boolean, val keys: Map<String, String>)

    private fun calls(state: LedgerState): List<Call> = state.records.mapNotNull { r ->
        if (r.finalSignal == Signal.WAIT) return@mapNotNull null
        val o = state.resultOf(r.id)?.outcome ?: return@mapNotNull null
        val right = when (o) { LedgerOutcome.CORRECT -> true; LedgerOutcome.INCORRECT, LedgerOutcome.INVALIDATED -> false; else -> return@mapNotNull null }
        Call(right, mapOf("Timeframe" to r.interval.uppercase(), "Session" to r.session.ifBlank { "UNKNOWN" }, "Regime" to r.regime.ifBlank { "UNKNOWN" },
            "Side" to r.finalSignal.name, "Agreement" to band(r.confidence), "Provider" to providerOf(r.dataSource)))
    }

    fun groups(state: LedgerState): List<Group> {
        val cs = calls(state)
        return listOf("Timeframe", "Session", "Regime", "Side", "Agreement", "Provider").flatMap { dim ->
            cs.groupBy { it.keys.getValue(dim) }.map { (v, list) -> Group(dim, v, list.count { it.right }, list.size) }
        }
    }

    /** SYSTEM lines: proven edges first, then worse-than-chance, then a one-line summary */
    fun summary(state: LedgerState): List<String> {
        val gs = groups(state)
        if (gs.isEmpty()) return emptyList()
        val total = calls(state); val all = Group("All", "calls", total.count { it.right }, total.size)
        val proven = gs.filter { it.verdict == "PROVEN EDGE" }.sortedByDescending { it.low }
        val worse = gs.filter { it.verdict == "WORSE THAN CHANCE" }.sortedBy { it.high }
        return listOf(all.line()) + proven.map { it.line() } + worse.map { it.line() } +
            (if (proven.isEmpty()) listOf("No group shows a proven edge yet (99.9% test, N >= $MIN_N).") else emptyList())
    }

    /**
     * The evidence for the forecast on screen: its own timeframe, current session, current regime, side, agreement band
     * and provider. Reports the strongest PROVEN EDGE or WORSE THAN CHANCE among them, else "no proven edge".
     */
    fun forForecast(a: GoldAnalysisResult, state: LedgerState, now: Long): String {
        if (a.overallSignal == Signal.WAIT) return ""
        val newsActive = a.newsMode != null || a.newsTradingPlan?.isNewsActive == true || a.isNewsModeTriggered
        val mine = mapOf("Timeframe" to a.interval.uppercase(), "Session" to PredictionLedger.sessionOf(now),
            "Regime" to PredictionLedger.regimeOf(a.recentCandles, newsActive), "Side" to a.overallSignal.name,
            "Agreement" to band(a.agreementPercent.toInt()), "Provider" to providerOf(a.feed?.source ?: ""))
        val gs = groups(state).filter { mine[it.dimension] == it.value }
        val worse = gs.filter { it.verdict == "WORSE THAN CHANCE" }.minByOrNull { it.high }
        val best = gs.filter { it.verdict == "PROVEN EDGE" }.maxByOrNull { it.low }
        return when {
            worse != null -> "Caution: ${worse.line()}"
            best != null -> best.line()
            else -> "No proven edge for this setup yet (timeframe, session, regime, side, agreement, provider all checked)"
        }
    }
}
