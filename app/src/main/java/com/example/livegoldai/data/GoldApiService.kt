package com.example.livegoldai.data

import com.example.livegoldai.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class GoldApiService(
    private var apiKey: String = "8e1493529b8e42d9b0a9e557c3451db0"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val memoryCache = mutableMapOf<String, Pair<Long, GoldAnalysisResult>>()
    private val cacheTtlMs = 15_000L // 15 seconds cache to avoid API burnout

    fun setApiKey(newKey: String) {
        apiKey = newKey.trim()
        memoryCache.clear()
    }

    fun getApiKey(): String = apiKey

    private fun aggregateCandles(candles: List<CandleBar>, groupSize: Int): List<CandleBar> {
        if (candles.size < groupSize || groupSize <= 1) return candles
        val result = mutableListOf<CandleBar>()
        val chunks = candles.chunked(groupSize)
        for (chunk in chunks) {
            if (chunk.isEmpty()) continue
            val open = chunk.first().open
            val close = chunk.last().close
            val high = chunk.maxOf { it.high }
            val low = chunk.minOf { it.low }
            val volume = chunk.sumOf { it.volume ?: 0.0 }
            val buyVolume = chunk.sumOf { it.buyVolume ?: ((it.volume ?: 0.0) * (if (it.close >= it.open) 0.6 else 0.4)) }
            val dt = chunk.last().datetime
            result.add(CandleBar(datetime = dt, open = open, high = high, low = low, close = close, volume = volume, buyVolume = buyVolume))
        }
        return result
    }

    suspend fun fetchAnalysis(interval: String = "4h", symbol: String = "XAU/USD"): Result<GoldAnalysisResult> = withContext(Dispatchers.IO) {
        val normInterval = interval.lowercase().trim()

        // Check 15-sec cache first to prevent rate limiting
        val cached = memoryCache[normInterval]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.first) < cacheTtlMs) {
            return@withContext Result.success(cached.second)
        }

        // Asynchronously fetch Macro Drivers in parallel so analysis has zero delay
        val dxyDeferred = async { fetchLiveDxy() }
        val us10yDeferred = async { fetchLiveUs10y() }
        val eventsDeferred = async { fetchLiveEconomicEvents() }

        // Step 1: TwelveData API (if API key is active and within limit)
        if (apiKey.isNotBlank()) {
            try {
                val (tdInterval, tdGroup) = when (normInterval) {
                    "1m", "1min" -> Pair("1min", 1)
                    "2m" -> Pair("1min", 2)
                    "3m" -> Pair("1min", 3)
                    "4m" -> Pair("1min", 4)
                    "5m" -> Pair("5min", 1)
                    "10m" -> Pair("5min", 2)
                    "15m", "15min" -> Pair("15min", 1)
                    "30m" -> Pair("30min", 1)
                    "45m" -> Pair("45min", 1)
                    "1h" -> Pair("1h", 1)
                    "2h" -> Pair("2h", 1)
                    "3h" -> Pair("1h", 3)
                    "4h" -> Pair("4h", 1)
                    "5h" -> Pair("1h", 5)
                    "6h" -> Pair("2h", 3)
                    "1d", "1day" -> Pair("1day", 1)
                    "1w", "1week" -> Pair("1week", 1)
                    "2w", "2week" -> Pair("1week", 2)
                    "3w", "3week" -> Pair("1week", 3)
                    "1mo", "1month" -> Pair("1month", 1)
                    else -> Pair("4h", 1)
                }
                val outputSize = (200 * tdGroup).coerceAtMost(5000)
                val tdUrl = "https://api.twelvedata.com/time_series?symbol=$symbol&interval=$tdInterval&outputsize=$outputSize&apikey=$apiKey&order=ASC"
                val request = Request.Builder()
                    .url(tdUrl)
                    .header("User-Agent", "KalankarFXGoldPro/1.0")
                    .build()
                val response = client.newCall(request).execute()
                val bodyString = response.body?.string()

                if (response.isSuccessful && !bodyString.isNullOrBlank()) {
                    val jsonElement = json.parseToJsonElement(bodyString).jsonObject
                    val status = jsonElement["status"]?.jsonPrimitive?.content
                    val valuesArray = jsonElement["values"]?.jsonArray
                    if (status != "error" && valuesArray != null && valuesArray.size >= 10) {
                        val rawList = mutableListOf<CandleBar>()
                        for (item in valuesArray) {
                            val obj = item.jsonObject
                            val dt = obj["datetime"]?.jsonPrimitive?.content ?: ""
                            val o = obj["open"]?.jsonPrimitive?.double ?: 0.0
                            val h = obj["high"]?.jsonPrimitive?.double ?: 0.0
                            val l = obj["low"]?.jsonPrimitive?.double ?: 0.0
                            val c = obj["close"]?.jsonPrimitive?.double ?: 0.0
                            val v = obj["volume"]?.jsonPrimitive?.double ?: 1000.0
                            if (c > 0.0) {
                                val rng = (h - l).coerceAtLeast(0.01)
                                val buyRatio = if (c >= o) (0.52 + 0.38 * (c - o) / rng) else (0.48 - 0.38 * (o - c) / rng)
                                val buyV = v * buyRatio.coerceIn(0.12, 0.88)
                                rawList.add(CandleBar(datetime = dt, open = o, high = h, low = l, close = c, volume = v, buyVolume = buyV))
                            }
                        }
                        val candleList = if (tdGroup > 1) aggregateCandles(rawList, tdGroup) else rawList
                        if (candleList.size >= 10) {
                            val analysis = TechnicalEngine.analyze(
                                candles = candleList,
                                interval = interval,
                                customDxy = dxyDeferred.await(),
                                customUs10y = us10yDeferred.await(),
                                customEvents = eventsDeferred.await()
                            )
                            val finalResult = applyNewsMode(analysis.copy(isSimulatedFallback = false), eventsDeferred.await())
                            memoryCache[normInterval] = Pair(System.currentTimeMillis(), finalResult)
                            return@withContext Result.success(finalResult)
                        }
                    }
                }
            } catch (_: Exception) {
                // TwelveData rate-limited (e.g. >8 req/min or 800/day), seamless auto-failover
            }
        }

        // Step 2: High-availability live Gold stream (Binance PAXG/USDT - 100% LBMA Gold Bullion Spot)
        // Completely free, NO API key required, 1200 req/min limit, 24/7 second-by-second live updates
        try {
            val (binanceInterval, binanceGroup, limit) = when (normInterval) {
                "1m", "1min" -> Triple("1m", 1, 70)
                "2m" -> Triple("1m", 2, 120)
                "3m" -> Triple("3m", 1, 70)
                "4m" -> Triple("1m", 4, 160)
                "5m" -> Triple("5m", 1, 70)
                "10m" -> Triple("5m", 2, 120)
                "15m", "15min" -> Triple("15m", 1, 70)
                "30m" -> Triple("30m", 1, 70)
                "45m" -> Triple("15m", 3, 120)
                "1h" -> Triple("1h", 1, 70)
                "2h" -> Triple("2h", 1, 70)
                "3h" -> Triple("1h", 3, 120)
                "4h" -> Triple("4h", 1, 70)
                "5h" -> Triple("1h", 5, 160)
                "6h" -> Triple("6h", 1, 70)
                "1d", "1day" -> Triple("1d", 1, 70)
                "1w", "1week" -> Triple("1w", 1, 70)
                "2w", "2week" -> Triple("1w", 2, 120)
                "3w", "3week" -> Triple("1w", 3, 160)
                "1mo", "1month" -> Triple("1M", 1, 70)
                else -> Triple("4h", 1, 70)
            }
            val liveUrl = "https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=$binanceInterval&limit=$limit"
            val request = Request.Builder()
                .url(liveUrl)
                .header("User-Agent", "KalankarFXGoldPro/1.0")
                .build()
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string()

            if (response.isSuccessful && !bodyString.isNullOrBlank()) {
                val klines = json.parseToJsonElement(bodyString).jsonArray
                if (klines.size >= 10) {
                    val rawList = mutableListOf<CandleBar>()
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                    for (item in klines) {
                        val arr = item.jsonArray
                        val timeMs = arr[0].jsonPrimitive.content.toLongOrNull() ?: 0L
                        val o = arr[1].jsonPrimitive.content.toDoubleOrNull() ?: 0.0
                        val h = arr[2].jsonPrimitive.content.toDoubleOrNull() ?: 0.0
                        val l = arr[3].jsonPrimitive.content.toDoubleOrNull() ?: 0.0
                        val c = arr[4].jsonPrimitive.content.toDoubleOrNull() ?: 0.0
                        val v = arr[5].jsonPrimitive.content.toDoubleOrNull() ?: 0.0
                        val takerBuyV = if (arr.size > 9) arr[9].jsonPrimitive.content.toDoubleOrNull() else null
                        val buyV = takerBuyV ?: (v * (if (c >= o) 0.58 else 0.42))
                        if (c > 0.0) {
                            rawList.add(
                                CandleBar(
                                    datetime = sdf.format(Date(timeMs)),
                                    open = o,
                                    high = h,
                                    low = l,
                                    close = c,
                                    volume = v,
                                    buyVolume = buyV
                                )
                            )
                        }
                    }
                    val candleList = if (binanceGroup > 1) aggregateCandles(rawList, binanceGroup) else rawList
                    if (candleList.size >= 10) {
                        val analysis = TechnicalEngine.analyze(
                            candles = candleList,
                            interval = interval,
                            customDxy = dxyDeferred.await(),
                            customUs10y = us10yDeferred.await(),
                            customEvents = eventsDeferred.await()
                        )
                        val finalResult = applyNewsMode(analysis.copy(isSimulatedFallback = false), eventsDeferred.await())
                        memoryCache[normInterval] = Pair(System.currentTimeMillis(), finalResult)
                        return@withContext Result.success(finalResult)
                    }
                }
            }
        } catch (_: Exception) {
            // Live PAXG failover attempted
        }

        // Step 2.5: Third Live Backup - Yahoo Finance (GC=F Gold Spot Futures, Zero API Key)
        try {
            val (yfInterval, yfRange) = when (normInterval) {
                "1m", "1min", "2m", "3m", "4m" -> Pair("1m", "1d")
                "5m", "10m" -> Pair("5m", "1d")
                "15m", "30m", "45m" -> Pair("15m", "5d")
                "1h", "2h", "3h", "4h", "5h", "6h" -> Pair("60m", "1mo")
                "1d", "1day" -> Pair("1d", "3mo")
                "1w", "1week", "2w", "2week", "3w", "3week" -> Pair("1wk", "1y")
                "1mo", "1month" -> Pair("1mo", "2y")
                else -> Pair("60m", "1mo")
            }
            val yfUrl = "https://query1.finance.yahoo.com/v8/finance/chart/GC=F?interval=$yfInterval&range=$yfRange"
            val request = Request.Builder()
                .url(yfUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string()

            if (response.isSuccessful && !bodyString.isNullOrBlank()) {
                val root = json.parseToJsonElement(bodyString).jsonObject
                val resultObj = root["chart"]?.jsonObject?.get("result")?.jsonArray?.get(0)?.jsonObject
                val timestampArr = resultObj?.get("timestamp")?.jsonArray
                val indicators = resultObj?.get("indicators")?.jsonObject
                val quote = indicators?.get("quote")?.jsonArray?.get(0)?.jsonObject
                val opens = quote?.get("open")?.jsonArray
                val highs = quote?.get("high")?.jsonArray
                val lows = quote?.get("low")?.jsonArray
                val closes = quote?.get("close")?.jsonArray
                val volumes = quote?.get("volume")?.jsonArray

                if (timestampArr != null && closes != null && timestampArr.size >= 10) {
                    val rawList = mutableListOf<CandleBar>()
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                    for (i in timestampArr.indices) {
                        val ts = timestampArr[i].jsonPrimitive.content.toLongOrNull() ?: continue
                        val c = closes[i].jsonPrimitive.content.toDoubleOrNull() ?: continue
                        val o = opens?.get(i)?.jsonPrimitive?.content?.toDoubleOrNull() ?: c
                        val h = highs?.get(i)?.jsonPrimitive?.content?.toDoubleOrNull() ?: maxOf(o, c)
                        val l = lows?.get(i)?.jsonPrimitive?.content?.toDoubleOrNull() ?: minOf(o, c)
                        val v = volumes?.get(i)?.jsonPrimitive?.content?.toDoubleOrNull() ?: 1000.0
                        if (c > 0.0) {
                            val rng = (h - l).coerceAtLeast(0.01)
                            val buyRatio = if (c >= o) (0.52 + 0.38 * (c - o) / rng) else (0.48 - 0.38 * (o - c) / rng)
                            val buyV = v * buyRatio.coerceIn(0.12, 0.88)
                            rawList.add(CandleBar(datetime = sdf.format(Date(ts * 1000L)), open = o, high = h, low = l, close = c, volume = v, buyVolume = buyV))
                        }
                    }
                    if (rawList.size >= 10) {
                        val analysis = TechnicalEngine.analyze(
                            candles = rawList.takeLast(70),
                            interval = interval,
                            customDxy = dxyDeferred.await(),
                            customUs10y = us10yDeferred.await(),
                            customEvents = eventsDeferred.await()
                        )
                        val finalResult = applyNewsMode(analysis.copy(isSimulatedFallback = false), eventsDeferred.await())
                        memoryCache[normInterval] = Pair(System.currentTimeMillis(), finalResult)
                        return@withContext Result.success(finalResult)
                    }
                }
            }
        } catch (_: Exception) {}

        // Step 3: Offline cached fallback engine (updated to current market prices)
        val fallback = TechnicalEngine.fallbackAnalysis(interval)
        Result.success(fallback.copy(isSimulatedFallback = true))
    }

    /**
     * Daily change of a Yahoo symbol, computed from the real price and previous close.
     * Returns null when data is unavailable — never a made-up value.
     */
    private fun fetchYahooDailyChange(symbol: String): Pair<Double, Double>? {
        return try {
            val req = Request.Builder()
                .url("https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=1d&range=5d")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string()
            if (!resp.isSuccessful || body.isNullOrBlank()) return null
            val root = json.parseToJsonElement(body).jsonObject
            val meta = root["chart"]?.jsonObject?.get("result")?.jsonArray?.get(0)?.jsonObject?.get("meta")?.jsonObject ?: return null
            val price = meta["regularMarketPrice"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
            val prev = (meta["chartPreviousClose"] ?: meta["previousClose"])?.jsonPrimitive?.content?.toDoubleOrNull()
            val pct = meta["regularMarketChangePercent"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: if (prev != null && prev != 0.0) (price - prev) / prev * 100.0 else return null
            price to pct
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchLiveDxy(): MacroMarketIndex? {
        val (price, changePct) = fetchYahooDailyChange("DX-Y.NYB") ?: return null
        val impact = if (changePct < -0.05) Signal.BUY else if (changePct > 0.05) Signal.SELL else Signal.WAIT
        val expl = when (impact) {
            Signal.BUY -> "US Dollar down ${String.format(Locale.US, "%.2f", changePct)}% today (usually supportive for gold)"
            Signal.SELL -> "US Dollar up +${String.format(Locale.US, "%.2f", changePct)}% today (usually a headwind for gold)"
            Signal.WAIT -> "US Dollar almost flat (${String.format(Locale.US, "%.2f", changePct)}%), no vote"
        }
        return MacroMarketIndex("DXY", "US Dollar Index", price, changePct, impact, expl)
    }

    private fun fetchLiveUs10y(): MacroMarketIndex? {
        val (yieldVal, changePct) = fetchYahooDailyChange("%5ETNX") ?: return null
        val impact = if (changePct < -0.3) Signal.BUY else if (changePct > 0.3) Signal.SELL else Signal.WAIT
        val expl = when (impact) {
            Signal.BUY -> "10Y yield down ${String.format(Locale.US, "%.2f", changePct)}% today (usually supportive for gold)"
            Signal.SELL -> "10Y yield up +${String.format(Locale.US, "%.2f", changePct)}% today (usually a headwind for gold)"
            Signal.WAIT -> "10Y yield almost flat (${String.format(Locale.US, "%.2f", changePct)}%), no vote"
        }
        return MacroMarketIndex("^TNX", "US 10-Yr Yield", yieldVal, changePct, impact, expl)
    }

    private fun fetchLiveEconomicEvents(): List<EconomicEvent> {
        try {
            val req = Request.Builder()
                .url("https://nfs.faireconomy.media/ff_calendar_thisweek.json")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string()
            if (resp.isSuccessful && !body.isNullOrBlank()) {
                val arr = json.parseToJsonElement(body).jsonArray
                val list = mutableListOf<EconomicEvent>()
                for (elem in arr) {
                    val obj = elem.jsonObject
                    val country = obj["country"]?.jsonPrimitive?.content ?: ""
                    if (country.equals("USD", ignoreCase = true)) {
                        val title = obj["title"]?.jsonPrimitive?.content ?: ""
                        val date = obj["date"]?.jsonPrimitive?.content ?: ""
                        val impact = obj["impact"]?.jsonPrimitive?.content ?: "Low"
                        val forecast = obj["forecast"]?.jsonPrimitive?.content ?: ""
                        val previous = obj["previous"]?.jsonPrimitive?.content ?: ""
                        val evMs = parseFfTimeMs(date)
                        val timeStr = if (evMs != null) utcFormat("HH:mm", evMs) + " UTC" else "Intraday"
                        val dateStr = if (evMs != null) utcFormat("yyyy-MM-dd", evMs) else "This Week"
                        val istStr = if (evMs != null) {
                            val istFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US).apply {
                                timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
                            }
                            istFormat.format(java.util.Date(evMs)) + " IST"
                        } else "IST (भारत समय)"
                        list.add(
                            EconomicEvent(
                                title = title,
                                country = "USD",
                                date = dateStr,
                                time = timeStr,
                                impact = impact,
                                forecast = forecast,
                                previous = previous,
                                isoTime = date,
                                indiaTime = istStr,
                                goldImpact = when (impact.lowercase()) {
                                    "high" -> "High Volatility Spike Expected"
                                    "medium" -> "Moderate Price Reaction"
                                    else -> "Low Immediate Impact"
                                }
                            )
                        )
                    }
                }
                if (list.isNotEmpty()) {
                    val cutoff = System.currentTimeMillis() - 60 * 60_000L
                    val upcoming = list
                        .filter { (parseFfTimeMs(it.isoTime) ?: Long.MAX_VALUE) >= cutoff }
                        .sortedBy { parseFfTimeMs(it.isoTime) ?: Long.MAX_VALUE }
                    return upcoming.ifEmpty { list.takeLast(8) }
                }
            }
        } catch (_: Exception) {}

        return emptyList() // calendar unavailable: show nothing rather than invented events
    }

    // ---------------- REAL VERIFICATION & MULTI-TIMEFRAME DATA ----------------

    private fun fetchBinanceKlines(interval: String, limit: Int, startMs: Long? = null, endMs: Long? = null): List<PathBar>? {
        return try {
            val sb = StringBuilder("https://api.binance.com/api/v3/klines?symbol=PAXGUSDT&interval=$interval&limit=$limit")
            if (startMs != null) sb.append("&startTime=$startMs")
            if (endMs != null) sb.append("&endTime=$endMs")
            val req = Request.Builder().url(sb.toString()).header("User-Agent", "KalankarFXGoldPro/1.0").build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string()
            if (!resp.isSuccessful || body.isNullOrBlank()) return null
            json.parseToJsonElement(body).jsonArray.mapNotNull { el ->
                val a = el.jsonArray
                val t = a[0].jsonPrimitive.content.toLongOrNull() ?: return@mapNotNull null
                PathBar(
                    t = t,
                    o = a[1].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null,
                    h = a[2].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null,
                    l = a[3].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null,
                    c = a[4].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchYahooGoldPath(startMs: Long, endMs: Long, yfInterval: String): List<PathBar>? {
        return try {
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/GC=F?interval=$yfInterval&period1=${startMs / 1000}&period2=${endMs / 1000 + 60}"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)").build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string()
            if (!resp.isSuccessful || body.isNullOrBlank()) return null
            val res = json.parseToJsonElement(body).jsonObject["chart"]?.jsonObject?.get("result")?.jsonArray?.get(0)?.jsonObject ?: return null
            val ts = res["timestamp"]?.jsonArray ?: return null
            val q = res["indicators"]?.jsonObject?.get("quote")?.jsonArray?.get(0)?.jsonObject ?: return null
            val o = q["open"]?.jsonArray; val h = q["high"]?.jsonArray; val l = q["low"]?.jsonArray; val c = q["close"]?.jsonArray
            if (o == null || h == null || l == null || c == null) return null
            ts.indices.mapNotNull { i ->
                val t = ts[i].jsonPrimitive.content.toLongOrNull() ?: return@mapNotNull null
                PathBar(
                    t = t * 1000L,
                    o = o[i].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null,
                    h = h[i].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null,
                    l = l[i].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null,
                    c = c[i].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Real price path between two moments, used to check a prediction after it expires.
     * Source 1: Binance PAXG/USDT (gold-backed token, free, minute data).
     * Source 2: Yahoo GC=F gold futures.
     * Only relative moves are used, so the small price difference between sources does not matter.
     */
    suspend fun fetchPricePath(startMs: Long, endMs: Long): PricePath? = withContext(Dispatchers.IO) {
        val spanMin = (endMs - startMs) / 60_000L
        val (bInterval, stepMin) = when {
            spanMin <= 990 -> "1m" to 1L
            spanMin <= 4900 -> "5m" to 5L
            else -> "15m" to 15L
        }
        val limit = (spanMin / stepMin + 2).toInt().coerceIn(2, 1000)
        val b = fetchBinanceKlines(bInterval, limit, startMs, endMs)
        if (!b.isNullOrEmpty()) return@withContext PricePath("PAXG/USDT $bInterval", b)
        val yInterval = when { spanMin <= 990 -> "1m"; spanMin <= 4900 -> "5m"; else -> "15m" }
        val y = fetchYahooGoldPath(startMs, endMs, yInterval)
        if (!y.isNullOrEmpty()) return@withContext PricePath("GC=F $yInterval", y)
        null
    }

    private var mtfCache: Pair<Long, Map<String, List<PathBar>>>? = null

    /** Real candles for 5m / 15m / 1h / 4h / 1d (PAXG/USDT). Cached for 60 seconds. */
    suspend fun fetchMtfCandles(): Map<String, List<PathBar>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        mtfCache?.let { if (now - it.first < 60_000L) return@withContext it.second }
        val tfs = listOf("5m", "15m", "1h", "4h", "1d")
        val jobs = tfs.map { tf -> async { tf to fetchBinanceKlines(tf, 120) } }
        val result = jobs.mapNotNull { j -> val (tf, bars) = j.await(); if (bars.isNullOrEmpty()) null else tf to bars }.toMap()
        if (result.isNotEmpty()) mtfCache = now to result
        result
    }

    // ---------------- NEWS MODE ----------------
    // High-impact USD news ke 30 min pehle se 30 min baad tak app "News Mode" mein rehta hai.
    private val newsBaseline = mutableMapOf<String, Double>()

    // ForexFactory time "2026-09-24T08:30:00-04:00" (New York time) -> UTC millis
    private fun parseFfTimeMs(iso: String): Long? {
        return try {
            if (!iso.contains("T") || iso.length < 19) return null
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val localMs = sdf.parse(iso.substring(0, 19))?.time ?: return null
            val tz = iso.substring(19)
            val offsetMin = if (tz.length >= 6 && (tz[0] == '+' || tz[0] == '-')) {
                val sign = if (tz[0] == '-') -1 else 1
                sign * (tz.substring(1, 3).toInt() * 60 + tz.substring(4, 6).toInt())
            } else 0
            localMs - offsetMin * 60_000L
        } catch (_: Exception) {
            null
        }
    }

    private fun utcFormat(pattern: String, ms: Long): String {
        val f = SimpleDateFormat(pattern, Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(ms))
    }

    private fun applyNewsMode(result: GoldAnalysisResult, events: List<EconomicEvent>): GoldAnalysisResult {
        val now = System.currentTimeMillis()
        val preWindow = 15 * 60_000L // Automatically activates 15 minutes prior to high-impact release
        val postWindow = 45 * 60_000L // Stays active during high-volatility 45 min post-release window
        val hit = events.mapNotNull { ev ->
            if (!ev.impact.equals("High", ignoreCase = true)) null
            else parseFfTimeMs(ev.isoTime)?.let { t -> 
                val diff = t - now
                if (diff in -postWindow..preWindow) Pair(ev, t) else null 
            }
        }.minByOrNull { abs(it.second - now) } ?: return result

        val ev = hit.first
        val t = hit.second
        val price = result.currentPrice
        val tech = result.overallSignal
        val fcst = if (ev.forecast.isNotBlank()) " [Est: ${ev.forecast} | Prev: ${ev.previous.ifBlank { "N/A" }}]" else ""

        val status = if (t > now) {
            newsBaseline[ev.isoTime] = price
            val mins = (t - now) / 60_000L + 1
            NewsModeStatus(
                phase = "PRE",
                eventTitle = ev.title,
                minutes = mins,
                newsSignal = Signal.WAIT,
                technicalSignal = tech,
                headline = "USD ${ev.title} - In $mins min",
                detail = "High-impact news in $mins min. Volatility spikes and spread expansion expected. Avoid fresh entries.$fcst",
                headlineEnglish = "USD ${ev.title} • Releasing in $mins min",
                headlineHindi = "USD ${ev.title} • ठीक $mins मिनट में रिलीज़ होगी",
                headlineMarathi = "USD ${ev.title} • बरोबर $mins मिनिटांत प्रसिद्ध होणार",
                detailEnglish = "High-impact economic news approaching in $mins min. Broker spreads widen rapidly and high-frequency algorithms hunt stop losses. Freeze fresh market entries until release settles.$fcst",
                detailHindi = "हाई-इम्पैक्ट न्यूज़ ठीक $mins मिनट में आने वाली है। ब्रोकर स्प्रेड तेजी से फैलता है और दोनों तरफ स्पाइक आ सकते हैं। नई ट्रेड लेने से बचें, पहले कैंडल को स्थिर होने दें।$fcst",
                detailMarathi = "हाय-इम्पॅक्ट न्यूज बरोबर $mins मिनिटांत येणार आहे. स्प्रेड वेगाने वाढतो आणि दोन्ही बाजूंना तीव्र स्पाइक येऊ शकतात. नवीन ट्रेड घेणे टाळा, आधी मार्केट स्थिर होऊ द्या.$fcst"
            )
        } else {
            val since = (now - t) / 60_000L
            val hadBase = newsBaseline.containsKey(ev.isoTime)
            val base = newsBaseline.getOrPut(ev.isoTime) { price }
            val move = price - base
            val mv = String.format(Locale.US, "%+.2f", move)
            val sig = when {
                since < 5 -> Signal.WAIT
                move >= 5.0 -> Signal.BUY
                move <= -5.0 -> Signal.SELL
                else -> Signal.WAIT
            }
            val whyEng = when {
                since < 5 -> "First 5 minutes is high-risk spike phase. Algorithmic wicks active. Wait for initial 5-min candle to close."
                move >= 5.0 -> "Gold surged $mv from pre-release baseline. Market interpreting release as strongly bullish for Gold."
                move <= -5.0 -> "Gold dropped $mv from pre-release baseline. Strong USD momentum pressuring Gold lower."
                else -> "Initial price reaction neutral ($mv move). Awaiting clean breakout direction."
            }
            val whyHin = when {
                since < 5 -> "शुरुआती 5 मिनट स्पाइक चरण होता है, फेक मूव बहुत आते हैं। पहली 5 मिनट कैंडल बंद होने का इंतज़ार करें।"
                move >= 5.0 -> "गोल्ड में $mv की तेजी आई है। मार्केट इस न्यूज़ को गोल्ड के लिए सकारात्मक मान रहा है।"
                move <= -5.0 -> "गोल्ड में $mv की गिरावट आई है। डॉलर मजबूती से गोल्ड पर दबाव बना हुआ है।"
                else -> "अभी कोई स्पष्ट दिशा नहीं ($mv उतार-चढ़ाव)। स्पष्ट दिशा बनने की प्रतीक्षा करें।"
            }
            val whyMar = when {
                since < 5 -> "सुरुवातीची 5 मिनिटे स्पाइकचा काळ असतो, खोटे मूव्ह येतात. पहिली 5 मिनिटांची कॅन्डल पूर्ण होण्याची वाट पहा."
                move >= 5.0 -> "गोल्डमध्ये $mv ची वाढ झाली आहे. मार्केट या बातमीला गोल्डसाठी सकारात्मक मानत आहे."
                move <= -5.0 -> "गोल्डमध्ये $mv ची घसरण झाली आहे. डॉलरच्या मजबुतीमुळे गोल्डवर दबाव आहे."
                else -> "अद्याप कोणतीही स्पष्ट दिशा नाही ($mv चढ-उतार). दिशा स्पष्ट होईपर्यंत थांबा."
            }
            NewsModeStatus(
                phase = "POST",
                eventTitle = ev.title,
                minutes = since,
                newsSignal = sig,
                technicalSignal = tech,
                headline = "USD ${ev.title} - Released $since min ago",
                detail = whyEng,
                headlineEnglish = "USD ${ev.title} • Released $since min ago",
                headlineHindi = "USD ${ev.title} • $since मिनट पहले रिलीज़ हुई",
                headlineMarathi = "USD ${ev.title} • $since मिनिटांपूर्वी प्रसिद्ध झाली",
                detailEnglish = whyEng,
                detailHindi = whyHin,
                detailMarathi = whyMar
            )
        }
        val updatedPlan = result.newsTradingPlan?.copy(
            isNewsActive = true,
            eventName = ev.title,
            releaseCountdownFormatted = if (t > now) "⏰ Release in ${(t - now) / 60_000L + 1}m (Auto-Active)" else "🚨 LIVE NEWS SPIKE WINDOW ACTIVE (${(now - t) / 60_000L} min elapsed)",
            phase = if (t > now) NewsPhase.PRE_NEWS_COIL else NewsPhase.LIVE_NEWS_SPIKE
        )
        return result.copy(
            overallSignal = status.newsSignal,
            newsMode = status,
            isNewsModeTriggered = true,
            newsTradingPlan = updatedPlan
        )
    }
}
