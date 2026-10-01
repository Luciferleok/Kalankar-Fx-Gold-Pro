package com.example.livegoldai.data.ai

import com.example.livegoldai.data.MiniJson
import com.example.livegoldai.data.PredictionLedger
import com.example.livegoldai.data.TechnicalEngine
import com.example.livegoldai.model.GoldAnalysisResult
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.round

/**
 * The SAME neutral, validated market snapshot goes to every provider.
 * It carries raw measured values only: no BUY/SELL verdict of the app, no pillar votes,
 * no confidence, so no model is anchored on the app's own answer.
 * Anything missing is written as "DATA UNAVAILABLE".
 */
data class MarketSnapshot(
    val id: String,
    val createdAtMs: Long,
    val interval: String,
    val horizonMin: Int,
    val price: Double,
    val json: String,
    val facts: Map<String, Double>   // normalised name -> value, used for the fact check
)

object MarketSnapshotBuilder {

    const val UNAVAILABLE = "DATA UNAVAILABLE"
    private const val CANDLES_SENT = 24

    fun build(a: GoldAnalysisResult, now: Long = System.currentTimeMillis()): MarketSnapshot {
        val interval = a.interval
        val horizon = TechnicalEngine.calculateValidityMinutes(interval)
        val atr = PredictionLedger.atrOf(a.recentCandles)
        val facts = LinkedHashMap<String, Double>()
        fun fact(name: String, v: Double?) { if (v != null && !v.isNaN() && !v.isInfinite()) facts[norm(name)] = v }

        val snap = LinkedHashMap<String, Any?>()
        val bucket = floor(a.currentPrice / max(atr * 0.5, 0.5)).toLong()
        val id = "S-${interval}-${a.lastUpdated.replace(" ", "T")}-$bucket"
        snap["snapshot_id"] = id
        snap["timestamp_utc"] = iso(now)
        snap["symbol"] = "XAU/USD"
        snap["chart_interval"] = interval
        snap["forecast_horizon_minutes"] = horizon
        snap["current_price"] = r2(a.currentPrice); fact("current_price", a.currentPrice); fact("price", a.currentPrice)
        snap["previous_close"] = r2(a.prevClose); fact("previous_close", a.prevClose)
        snap["day_high"] = r2(a.high24h); fact("day_high", a.high24h)
        snap["day_low"] = r2(a.low24h); fact("day_low", a.low24h)
        snap["change_percent"] = r2(a.changePercent)
        snap["atr_14"] = if (atr > 0) r2(atr) else UNAVAILABLE; if (atr > 0) { fact("atr_14", atr); fact("atr", atr) }

        // ---- last candles of this chart (time, open, high, low, close)
        snap["recent_candles_oldest_first"] = a.recentCandles.takeLast(CANDLES_SENT).map {
            listOf(it.datetime, r2(it.open), r2(it.high), r2(it.low), r2(it.close))
        }

        // ---- indicator readouts (values only, no verdicts)
        val dxyOk = a.feed?.dxyAvailable == true
        val us10yOk = a.feed?.us10yAvailable == true
        val readouts = ArrayList<Map<String, Any?>>()
        a.groups.forEach { g ->
            g.indicators.forEach { ind ->
                val isDxy = ind.name.contains("DXY", true) || ind.name.contains("Dollar", true)
                val is10y = ind.name.contains("10-Yr", true) || ind.name.contains("Treasury", true)
                val value = when {
                    isDxy && !dxyOk -> UNAVAILABLE
                    is10y && !us10yOk -> UNAVAILABLE
                    else -> ind.valueDisplay
                }
                readouts.add(linkedMapOf("group" to g.title, "name" to ind.name, "value" to value))
                if (value != UNAVAILABLE) extractFacts(ind.name, ind.valueDisplay).forEach { (k, v) -> facts.putIfAbsent(norm(k), v) }
            }
        }
        snap["indicators"] = readouts

        // ---- levels
        val pv = a.pivotLevels
        snap["support_resistance"] = linkedMapOf(
            "pivot" to r2(pv.pivot), "r1" to r2(pv.r1), "r2" to r2(pv.r2), "s1" to r2(pv.s1), "s2" to r2(pv.s2)
        )
        fact("pivot", pv.pivot); fact("r1", pv.r1); fact("r2", pv.r2); fact("s1", pv.s1); fact("s2", pv.s2)

        // ---- other timeframes (real PAXG/USDT candles, EMA rule state only)
        val mtf = a.mtfMatrix?.timeframes?.filter { it.label.startsWith("PAXG") } ?: emptyList()
        snap["timeframes"] = if (mtf.isEmpty()) UNAVAILABLE else mtf.map {
            linkedMapOf(
                "timeframe" to it.timeframe,
                "ema9_vs_ema21_and_close_vs_ema50" to when (it.signal.name) { "BUY" -> "BOTH_UP"; "SELL" -> "BOTH_DOWN"; else -> "MIXED" },
                "rsi_14" to it.momentumPercent,
                "level" to it.keyLevel
            )
        }

        // ---- regime / structure (measured)
        val ins = a.insights
        snap["market_regime"] = ins?.let {
            linkedMapOf("regime" to it.regime, "trend_efficiency_percent" to it.trendEfficiencyPercent, "last_candle_range_vs_atr" to r2(it.lastRangeVsAtr))
        } ?: UNAVAILABLE

        // ---- session
        snap["session"] = linkedMapOf(
            "utc_session" to PredictionLedger.sessionOf(now),
            "open_markets" to a.marketSessions.filter { it.isOpen }.map { it.name }
        )

        // ---- verified scheduled events only (real calendar)
        val events = a.macroRadar?.upcomingEvents ?: emptyList()
        snap["verified_news_context"] = if ((a.feed?.calendarEvents ?: 0) == 0 && events.isEmpty()) UNAVAILABLE else linkedMapOf(
            "source" to "ForexFactory calendar",
            "upcoming" to events.filter { it.impact.equals("High", true) || it.impact.equals("Medium", true) }.take(6).map {
                linkedMapOf("title" to it.title, "country" to it.country, "impact" to it.impact, "time" to (it.isoTime.ifBlank { it.date + " " + it.time }),
                    "forecast" to it.forecast.ifBlank { UNAVAILABLE }, "previous" to it.previous.ifBlank { UNAVAILABLE })
            }
        )

        // ---- history summary (how often the app was right, NOT what it says now)
        val l = a.learning
        snap["prediction_history_summary"] = if (l == null || (l.correct + l.wrong) == 0) "no verified history yet" else linkedMapOf(
            "verified_calls" to (l.correct + l.wrong),
            "direction_correct_percent" to r2(100.0 * l.correct / max(1, l.correct + l.wrong))
        )

        // ---- data quality
        val feed = a.feed
        snap["data_quality"] = linkedMapOf(
            "price_source" to (feed?.source ?: UNAVAILABLE),
            "live" to (feed?.isLive ?: false),
            "candles_available" to a.recentCandles.size,
            "dxy" to if (dxyOk) "available" else UNAVAILABLE,
            "us10y" to if (us10yOk) "available" else UNAVAILABLE
        )

        return MarketSnapshot(id, now, interval, horizon, a.currentPrice, MiniJson.write(snap), facts)
    }

    // ------------------------------------------------------------------ helpers

    private val labelled = Regex("""([+\-]?[A-Za-z%][A-Za-z0-9 %./+\-]*?)\s*:\s*\$?(-?\d+(?:\.\d+)?)""")
    private val number = Regex("""-?\d+(?:\.\d+)?""")

    /** "EMA9: 3348.57 | EMA21: 3349.24" -> {EMA9=3348.57, EMA21=3349.24}; "43.9 / 100" -> {RSI (14)=43.9}. */
    fun extractFacts(name: String, valueDisplay: String): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        labelled.findAll(valueDisplay).forEach { m ->
            val label = m.groupValues[1].trim()
            val v = m.groupValues[2].toDoubleOrNull()
            if (label.isNotEmpty() && v != null) out[label] = v
        }
        if (out.isEmpty()) {
            val nums = number.findAll(valueDisplay.substringBefore(" / 100")).map { it.value }.toList()
            if (nums.size == 1) nums[0].toDoubleOrNull()?.let { out[name] = it }
        }
        return out
    }

    /** "RSI (14)" -> "rsi14", "+DI" -> "+di", "current_price" -> "currentprice". */
    fun norm(s: String): String = s.lowercase(Locale.US).filter { it.isLetterOrDigit() || it == '+' || it == '-' }

    private fun r2(v: Double) = round(v * 100) / 100.0

    private fun iso(ms: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(java.util.Date(ms))
}
