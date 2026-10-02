package com.example.livegoldai.data.brain

import com.example.livegoldai.data.MiniJson
import com.example.livegoldai.model.CrossAsset
import com.example.livegoldai.model.CrossMarketReport
import com.example.livegoldai.model.Signal
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * CROSS-MARKET INTELLIGENCE
 *
 * Real hourly closes (Yahoo Finance chart API) for assets that often move with or against gold.
 * No fixed rule like "dollar up = gold down": the relationship is MEASURED as the rolling
 * correlation of hourly returns with gold futures over the last 7 and 30 days, and it is allowed
 * to weaken, flip or break. If a source does not answer, the asset is UNAVAILABLE — never filled in.
 */
data class XmSeries(
    val symbol: String,
    val times: List<Long>,       // ms, ascending
    val closes: List<Double>,
    val fetchedAtMs: Long,
    val latencyMs: Long
)

data class XmAssetDef(val key: String, val name: String, val symbol: String)

object CrossMarketCatalog {
    const val GOLD_REF = "GC=F"
    val ASSETS = listOf(
        XmAssetDef("DXY", "US Dollar Index", "DX-Y.NYB"),
        XmAssetDef("US10Y", "US 10Y Yield", "^TNX"),
        XmAssetDef("SILVER", "Silver", "SI=F"),
        XmAssetDef("SPX", "S&P 500", "^GSPC"),
        XmAssetDef("VIX", "VIX", "^VIX"),
        XmAssetDef("USDJPY", "USD/JPY", "JPY=X"),
        XmAssetDef("OIL", "Crude Oil", "CL=F")
    )
}

/** Fetches and caches the series. Blocking: call from a background thread. */
class CrossMarketService(
    private val fetchBody: ((String) -> String?)? = null,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
    }
    private val pool by lazy { Executors.newSingleThreadExecutor { r -> Thread(r, "cross-market").apply { isDaemon = true } } }
    private val fetchPool by lazy { Executors.newFixedThreadPool(4) { r -> Thread(r, "cross-market-io").apply { isDaemon = true } } }
    private val cache = HashMap<String, XmSeries>()
    @Volatile private var lastRefreshAt = 0L

    private fun httpGet(url: String): String? = try {
        val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)").build()
        val resp = client.newCall(req).execute()
        val body = resp.body?.string()
        if (resp.isSuccessful && !body.isNullOrBlank()) body else null
    } catch (_: Exception) {
        null
    }

    @Volatile private var refreshing: java.util.concurrent.Future<*>? = null

    /**
     * Latest series per symbol. Never blocks the screen for long: a refresh (at most every [TTL_MS]) runs in the
     * background; only the very first call waits up to [FIRST_WAIT_MS]. A failed symbol keeps its older series,
     * which the engine then marks STALE.
     */
    fun series(): Map<String, XmSeries> {
        val now = clock()
        var job: java.util.concurrent.Future<*>? = null
        synchronized(this) {
            val running = refreshing?.isDone == false
            if (!running && now - lastRefreshAt >= TTL_MS) {
                lastRefreshAt = now
                refreshing = pool.submit { refresh() }
            }
            if (cache.isEmpty()) job = refreshing
        }
        if (job != null) {
            try { job!!.get(FIRST_WAIT_MS, TimeUnit.MILLISECONDS) } catch (_: Exception) { }
        }
        synchronized(this) { return HashMap(cache) }
    }

    private fun refresh() {
        val symbols = listOf(CrossMarketCatalog.GOLD_REF) + CrossMarketCatalog.ASSETS.map { it.symbol }
        val tasks = symbols.map { sym ->
            Callable {
                val t0 = clock()
                val url = "https://query1.finance.yahoo.com/v8/finance/chart/" + sym.replace("^", "%5E") + "?interval=1h&range=1mo"
                val body = (fetchBody ?: ::httpGet)(url)
                sym to body?.let { parse(sym, it, clock(), clock() - t0) }
            }
        }
        try {
            fetchPool.invokeAll(tasks, 20, TimeUnit.SECONDS).forEach { f ->
                try {
                    if (!f.isCancelled) {
                        val (sym, sr) = f.get()
                        if (sr != null && sr.closes.size >= 10) synchronized(this) { cache[sym] = sr }
                    }
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        const val TTL_MS = 10 * 60_000L
        const val FIRST_WAIT_MS = 4_000L

        @Suppress("UNCHECKED_CAST")
        fun parse(symbol: String, body: String, fetchedAt: Long, latency: Long): XmSeries? = try {
            val root = MiniJson.parse(body) as? Map<String, Any?>
            val res = ((root?.get("chart") as? Map<String, Any?>)?.get("result") as? List<*>)?.firstOrNull() as? Map<String, Any?>
            val ts = res?.get("timestamp") as? List<*>
            val quote = ((res?.get("indicators") as? Map<String, Any?>)?.get("quote") as? List<*>)?.firstOrNull() as? Map<String, Any?>
            val closes = quote?.get("close") as? List<*>
            if (ts == null || closes == null) null else {
                val t = ArrayList<Long>(); val c = ArrayList<Double>()
                for (i in ts.indices) {
                    val tv = (ts[i] as? Number)?.toLong() ?: continue
                    val cv = (closes.getOrNull(i) as? Number)?.toDouble() ?: continue
                    if (cv > 0) { t.add(tv * 1000L); c.add(cv) }
                }
                XmSeries(symbol, t, c, fetchedAt, latency)
            }
        } catch (_: Exception) {
            null
        }
    }
}

object CrossMarketEngine {

    const val MIN_PAIRS_30D = 40
    const val MIN_PAIRS_7D = 20
    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR

    fun build(series: Map<String, XmSeries>, now: Long): CrossMarketReport {
        val gold = series[CrossMarketCatalog.GOLD_REF]
        val goldChg = gold?.let { change24h(it) }
        val goldSigma = gold?.let { dailySigma(it) }
        val assets = CrossMarketCatalog.ASSETS.map { def -> asset(def, series[def.symbol], gold, goldChg, goldSigma, now) }
        val ok = assets.filter { it.health != "UNAVAILABLE" }
        val bull = ok.count { it.implied == Signal.BUY }
        val bear = ok.count { it.implied == Signal.SELL }
        val summary = when {
            gold == null || ok.isEmpty() -> "UNAVAILABLE"
            bull == 0 && bear == 0 -> "NEUTRAL"
            bull >= bear + 2 || (bear == 0 && bull >= 1) -> "SUPPORTS BULLISH"
            bear >= bull + 2 || (bull == 0 && bear >= 1) -> "SUPPORTS BEARISH"
            else -> "MIXED"
        }
        return CrossMarketReport(
            available = gold != null && ok.isNotEmpty(),
            assets = assets,
            supportBull = bull, supportBear = bear,
            summary = summary,
            divergences = ok.filter { it.divergence }.map { "${it.name}: relation normally ${it.relation.lowercase(Locale.US)} (30D ${fmt(it.corr30d)}) but gold moved the other way" },
            goldRefLast = gold?.closes?.lastOrNull() ?: 0.0,
            goldRefBarMs = gold?.times?.lastOrNull() ?: 0L,
            gold24hPct = goldChg ?: 0.0,
            fetchedAtMs = gold?.fetchedAtMs ?: 0L
        )
    }

    private fun asset(def: XmAssetDef, s: XmSeries?, gold: XmSeries?, goldChg: Double?, goldSigma: Double?, now: Long): CrossAsset {
        if (s == null || s.closes.size < 10) {
            return CrossAsset(def.key, def.name, def.symbol, "UNAVAILABLE", "Yahoo Finance", 0, -1, 0, 0.0, 0.0, 0.0, 0.0, 0, 0,
                "UNKNOWN", "UNKNOWN", "UNKNOWN", null, false, "CROSS-MARKET DATA UNAVAILABLE")
        }
        val health = if (now - s.fetchedAtMs > 30 * 60_000L) "STALE" else "OK"
        val chg = change24h(s) ?: 0.0
        val (c7, n7) = if (gold == null) 0.0 to 0 else corr(gold, s, now - 7 * DAY)
        val (c30, n30) = if (gold == null) 0.0 to 0 else corr(gold, s, now - 31 * DAY)
        val ok30 = n30 >= MIN_PAIRS_30D
        val ok7 = n7 >= MIN_PAIRS_7D
        val relation = if (!ok30) "UNKNOWN" else if (c30 <= -0.2) "NEGATIVE" else if (c30 >= 0.2) "POSITIVE" else "NONE"
        val a = abs(c30)
        val strength = if (!ok30) "UNKNOWN" else if (a >= 0.6) "STRONG" else if (a >= 0.4) "MODERATE" else if (a >= 0.2) "WEAK" else "NONE"
        val trend = when {
            !ok30 || !ok7 -> "UNKNOWN"
            abs(c7) >= 0.2 && a >= 0.2 && (c7 > 0) != (c30 > 0) -> "FLIPPED"
            abs(c7) < a - 0.15 -> "WEAKENING"
            abs(c7) > a + 0.15 -> "STRENGTHENING"
            else -> "STABLE"
        }
        val sigma = dailySigma(s)
        val significant = sigma != null && sigma > 0 && abs(chg) > 0.5 * sigma
        val implied: Signal? = if (ok30 && a >= 0.3 && significant) {
            val up = (chg > 0) == (c30 > 0)
            if (up) Signal.BUY else Signal.SELL
        } else null
        val goldSignificant = goldChg != null && goldSigma != null && goldSigma > 0 && abs(goldChg) > 0.5 * goldSigma
        val divergence = implied != null && a >= 0.4 && goldSignificant && ((goldChg!! > 0) != (implied == Signal.BUY))
        val note = when {
            !ok30 -> "Not enough overlapping hours to measure the relation (N=$n30)"
            relation == "NONE" -> "No measurable relation with gold right now"
            trend == "FLIPPED" -> "Relation flipped in the last 7 days"
            trend == "WEAKENING" -> "Relation weakening"
            trend == "STRENGTHENING" -> "Relation strengthening"
            else -> "Relation stable"
        }
        return CrossAsset(
            key = def.key, name = def.name, symbol = def.symbol, health = health, source = "Yahoo Finance",
            fetchedAtMs = s.fetchedAtMs, latencyMs = s.latencyMs, lastBarMs = s.times.last(),
            last = s.closes.last(), change24hPct = chg,
            corr7d = if (ok7) c7 else 0.0, corr30d = if (ok30) c30 else 0.0, n7 = n7, n30 = n30,
            relation = relation, strength = strength, trend = trend, implied = implied, divergence = divergence, note = note
        )
    }

    /** % change from the close at least 24h before the last bar to the last bar. */
    fun change24h(s: XmSeries): Double? {
        if (s.closes.size < 2) return null
        val lastT = s.times.last()
        val idx = s.times.indexOfLast { it <= lastT - DAY }
        val base = if (idx >= 0) s.closes[idx] else return null
        return (s.closes.last() - base) / base * 100.0
    }

    /** Daily volatility estimate in %, from hourly log returns. */
    fun dailySigma(s: XmSeries): Double? {
        val r = ArrayList<Double>()
        for (i in 1 until s.closes.size) if (s.times[i] - s.times[i - 1] <= 3 * HOUR) r.add(ln(s.closes[i] / s.closes[i - 1]))
        if (r.size < 20) return null
        val m = r.average()
        val sd = sqrt(r.sumOf { (it - m) * (it - m) } / (r.size - 1))
        return sd * sqrt(24.0) * 100.0
    }

    /** Pearson correlation of hourly log returns on hours present in both series, since [from]. Returns (r, pairs). */
    fun corr(a: XmSeries, b: XmSeries, from: Long): Pair<Double, Int> {
        val bm = HashMap<Long, Double>()
        for (i in b.times.indices) bm[b.times[i] / HOUR] = b.closes[i]
        val xs = ArrayList<Double>(); val ys = ArrayList<Double>()
        var prevH = -1L; var prevA = 0.0; var prevB = 0.0
        for (i in a.times.indices) {
            if (a.times[i] < from) continue
            val h = a.times[i] / HOUR
            val bv = bm[h] ?: continue
            if (prevH >= 0 && h - prevH <= 3) {
                xs.add(ln(a.closes[i] / prevA)); ys.add(ln(bv / prevB))
            }
            prevH = h; prevA = a.closes[i]; prevB = bv
        }
        val n = xs.size
        if (n < 3) return 0.0 to n
        val mx = xs.average(); val my = ys.average()
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0
        for (i in 0 until n) { val dx = xs[i] - mx; val dy = ys[i] - my; sxy += dx * dy; sxx += dx * dx; syy += dy * dy }
        if (sxx <= 0 || syy <= 0) return 0.0 to n
        return (sxy / sqrt(sxx * syy)) to n
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%+.2f", v)
}
