package com.example.livegoldai.data

import com.example.livegoldai.model.CandleBar
import kotlin.math.abs

/**
 * DATA QUALITY ENGINE (V21)
 *
 * Checks the candles a forecast is about to be built on. Pure function, no network.
 *   EXCELLENT / GOOD  -> forecast as normal
 *   DEGRADED          -> forecast is shown, with a warning
 *   UNSAFE            -> no BUY/SELL is published or recorded: "FORECAST PAUSED: market data integrity issue"
 *
 * Staleness checks are skipped while the gold market is closed (a frozen weekend feed is normal).
 */
object DataQualityEngine {

    data class Report(val level: String, val issues: List<String>, val checked: Int) {
        val unsafe: Boolean get() = level == "UNSAFE"
        val summary: String get() = if (issues.isEmpty()) level else level + ": " + issues.joinToString("; ")
    }

    const val WINDOW = 60
    const val STALE_WARN_MS = 3 * 60_000L
    const val STALE_UNSAFE_MS = 15 * 60_000L

    fun assess(candles: List<CandleBar>, feedFetchedAtMs: Long, isLive: Boolean, now: Long, marketClosed: Boolean): Report {
        val bad = ArrayList<String>()      // make the data UNSAFE
        val warn = ArrayList<String>()     // make it DEGRADED
        val c = candles.takeLast(WINDOW)

        if (!isLive) bad.add("offline demo data, not a live feed")
        if (c.size < 30) bad.add("only ${c.size} candles (need 30)")

        // ---- every candle must be a possible candle
        val invalid = c.count { it.open <= 0 || it.close <= 0 || it.high <= 0 || it.low <= 0 || it.high < maxOf(it.open, it.close) - 1e-6 || it.low > minOf(it.open, it.close) + 1e-6 || it.high < it.low }
        if (invalid > 0) bad.add("$invalid impossible candle(s) (high/low do not contain open/close)")

        // ---- time order: duplicates and out-of-order stamps (same-format stamps compare as text)
        val stamps = c.map { it.datetime }.filter { it.isNotBlank() }
        val dup = stamps.size - stamps.distinct().size
        if (dup > 2) bad.add("$dup duplicate candle times") else if (dup > 0) warn.add("$dup duplicate candle time(s)")
        var outOfOrder = 0
        for (i in 1 until stamps.size) if (stamps[i].length == stamps[i - 1].length && stamps[i] < stamps[i - 1]) outOfOrder++
        // a single step back can be a daylight-saving clock change on the phone: warn only
        if (outOfOrder >= 2) bad.add("$outOfOrder candles out of time order") else if (outOfOrder == 1) warn.add("1 candle out of time order")

        // ---- abnormal jump between one close and the next open
        if (c.size >= 15) {
            val atr = PredictionLedger.atrOf(c)
            if (atr > 0) {
                var gaps = 0
                for (i in 1 until c.size) if (abs(c[i].open - c[i - 1].close) > 5 * atr) gaps++
                if (gaps > 0) warn.add("$gaps price gap(s) larger than 5× ATR")
            }
        }

        if (!marketClosed) {
            // ---- frozen feed: the last candles are all identical
            val tail = c.takeLast(6)
            if (tail.size == 6 && tail.all { it.close == tail[0].close && it.high == it.low }) warn.add("last 6 candles are flat: feed may be frozen")
            // ---- age of the data on the phone
            if (feedFetchedAtMs > 0) {
                val age = now - feedFetchedAtMs
                if (age > STALE_UNSAFE_MS) bad.add("prices are ${age / 60_000} min old")
                else if (age > STALE_WARN_MS) warn.add("prices are ${age / 60_000} min old")
            }
        }

        val level = when {
            bad.isNotEmpty() -> "UNSAFE"
            warn.size >= 2 -> "DEGRADED"
            warn.size == 1 -> "GOOD"
            else -> "EXCELLENT"
        }
        return Report(level, bad + warn, c.size)
    }
}
