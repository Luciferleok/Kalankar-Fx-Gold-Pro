package com.example.livegoldai.data.brain

import com.example.livegoldai.model.AnomalyCheck
import com.example.livegoldai.model.AnomalyReport
import com.example.livegoldai.model.CrossMarketReport
import com.example.livegoldai.model.GoldAnalysisResult
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * MARKET ANOMALY ENGINE
 * Compares the newest candle with this chart's own recent normal (median of the previous 50 candles),
 * so thresholds follow volatility instead of fixed point values.
 * Severity: 0 normal, 1 moderate, 2 high, 3 extreme. The level is the worst check.
 */
object AnomalyEngine {

    private fun sev(ratio: Double, a: Double, b: Double, c: Double) = if (ratio >= c) 3 else if (ratio >= b) 2 else if (ratio >= a) 1 else 0

    fun build(a: GoldAnalysisResult, xm: CrossMarketReport?, now: Long = System.currentTimeMillis()): AnomalyReport {
        val checks = ArrayList<AnomalyCheck>()
        val reasons = ArrayList<String>()
        val c = a.recentCandles
        if (c.size >= 25) {
            val last = c.last()
            val prev = c.dropLast(1).takeLast(50)
            val ranges = prev.map { it.high - it.low }.sorted()
            val medRange = ranges[ranges.size / 2]
            val moves = prev.zipWithNext { x, y -> abs(y.close - x.close) }.sorted()
            val medMove = if (moves.isEmpty()) 0.0 else moves[moves.size / 2]
            val prevClose = c[c.size - 2].close

            if (medRange > 0) {
                val r = (last.high - last.low) / medRange
                val s = sev(r, 2.5, 4.0, 6.0)
                checks.add(AnomalyCheck("Candle size", fmt(r) + "× normal", s))
                if (s > 0) reasons.add("Abnormal candle size: ${fmt(r)}× the usual range")
            }
            if (medMove > 0) {
                val r = abs(last.close - prevClose) / medMove
                val s = sev(r, 4.0, 7.0, 12.0)
                checks.add(AnomalyCheck("Velocity", fmt(r) + "× normal", s))
                if (s > 0) reasons.add("Abnormal speed: ${fmt(r)}× the usual candle-to-candle move")
            }
            fun tr(i: Int) = max(c[i].high - c[i].low, max(abs(c[i].high - c[i - 1].close), abs(c[i].low - c[i - 1].close)))
            val n = c.size
            val recent = (n - 5 until n).map { tr(it) }.average()
            val baseIdx = (max(1, n - 55) until n - 5)
            val base = if (baseIdx.isEmpty()) 0.0 else baseIdx.map { tr(it) }.average()
            if (base > 0) {
                val r = recent / base
                val s = sev(r, 1.8, 2.5, 4.0)
                checks.add(AnomalyCheck("Volatility expansion", fmt(r) + "× baseline", s))
                if (s > 0) reasons.add("Volatility ${fmt(r)}× its 50-candle baseline")
                val gap = abs(last.open - prevClose) / base
                val gs = sev(gap, 1.0, 2.0, 4.0)
                checks.add(AnomalyCheck("Gap", fmt(gap) + " ATR", gs))
                if (gs > 0) reasons.add("Price gap of ${fmt(gap)} ATR")
            }
        } else {
            checks.add(AnomalyCheck("Candle history", "only ${c.size} candles", 0))
        }

        // price-feed sanity: spot shown in the app vs gold futures (different instruments, normally within ~1%)
        // (only when the futures bar is fresh: a stale weekend bar would be a false alarm)
        if (xm != null && xm.goldRefLast > 0 && a.currentPrice > 0 && a.feed?.isLive == true && now - xm.goldRefBarMs < 2 * 3_600_000L) {
            val diff = abs(a.currentPrice - xm.goldRefLast) / xm.goldRefLast * 100.0
            val s = sev(diff, 1.5, 3.0, 6.0)
            checks.add(AnomalyCheck("Feed vs gold futures", String.format(Locale.US, "%.2f%% apart", diff), s))
            if (s > 0) reasons.add(String.format(Locale.US, "Price feed is %.2f%% away from gold futures", diff))
        }

        // indicator disagreement: pillars split almost evenly between BUY and SELL
        if (a.buyCount >= 3 && a.sellCount >= 3) {
            checks.add(AnomalyCheck("Pillar disagreement", "${a.buyCount} BUY vs ${a.sellCount} SELL", 1))
            reasons.add("Pillars are split: ${a.buyCount} BUY vs ${a.sellCount} SELL")
        } else checks.add(AnomalyCheck("Pillar disagreement", "${a.buyCount} BUY vs ${a.sellCount} SELL", 0))

        // correlation breaks
        val div = xm?.divergences?.size ?: 0
        if (xm != null && xm.available) {
            val s = if (div >= 3) 2 else if (div >= 1) 1 else 0
            checks.add(AnomalyCheck("Cross-asset divergence", "$div of ${xm.assets.count { it.health != "UNAVAILABLE" }}", s))
            if (s > 0) reasons.add("$div cross-asset relation(s) not holding")
        }

        // rapid regime transition
        if (a.insights?.regime == "TRANSITION") {
            checks.add(AnomalyCheck("Regime", "TRANSITION", 1))
            reasons.add("Market regime is changing")
        }

        val severity = checks.maxOfOrNull { it.severity } ?: 0
        val level = when (severity) { 0 -> "LOW"; 1 -> "MODERATE"; 2 -> "HIGH"; else -> "EXTREME" }
        return AnomalyReport(level, severity, reasons, checks)
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.1f", v)
}
