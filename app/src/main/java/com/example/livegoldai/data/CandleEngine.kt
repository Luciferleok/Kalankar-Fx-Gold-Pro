package com.example.livegoldai.data

import com.example.livegoldai.model.CandleBar
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * V32 stage 2: one candle builder for every provider.
 *
 * Rules (each one has a test):
 *  - Buckets are aligned to UTC: bucket start = floor(epoch ms / bucket length). For lengths that divide a day
 *    (2, 3, 5, 10, 12, 15, 30 min, 1h…) that is the same as aligning to UTC midnight and to every hour.
 *    7 min does not divide a day, so 7m buckets are aligned to the Unix epoch (00:00 UTC 1970), the only rule that
 *    gives every 7m bar the same length without a short bar each day.
 *  - Only real base candles are used. A minute with no trades is simply absent: it is never filled in.
 *  - A bucket whose first base candle is missing at the START of the data is dropped (its true open is unknown).
 *  - A bucket is COMPLETE only when every base candle in it is complete and its end time has passed.
 *    Only the last bucket may be developing; a developing bucket followed by a newer one is inconsistent and dropped.
 *  - Duplicates (same start time) keep the newest copy. Invalid OHLC (<= 0, NaN, high below open/close…) is dropped.
 *  - Tick counts are NOT traded volume: volume stays empty unless the provider sends real volume for every part.
 */
object CandleAggregator {

    data class Bar(
        val startMs: Long, val open: Double, val high: Double, val low: Double, val close: Double,
        val volume: Double? = null, val buyVolume: Double? = null, val complete: Boolean = true
    )

    data class Agg(val bar: Bar, val parts: Int, val expected: Int) {
        val gaps get() = expected - parts
    }

    data class Result(val bars: List<Agg>, val droppedInvalid: Int, val droppedMisaligned: Int, val droppedDuplicates: Int, val droppedPartialLead: Int)

    fun bucketStart(ms: Long, minutes: Int): Long { val len = minutes * 60_000L; return Math.floorDiv(ms, len) * len }

    fun valid(b: Bar): Boolean {
        val xs = doubleArrayOf(b.open, b.high, b.low, b.close)
        if (xs.any { it.isNaN() || it.isInfinite() || it <= 0.0 }) return false
        return b.high >= max(b.open, b.close) && b.low <= min(b.open, b.close) && b.high >= b.low
    }

    fun aggregate(base: List<Bar>, baseMin: Int, targetMin: Int, nowMs: Long): Result {
        require(baseMin > 0 && targetMin >= baseMin && targetMin % baseMin == 0) { "target $targetMin is not a multiple of base $baseMin" }
        val baseLen = baseMin * 60_000L
        val len = targetMin * 60_000L
        var invalid = 0; var misaligned = 0; var dup = 0
        val byStart = LinkedHashMap<Long, Bar>()
        for (b in base) {
            if (!valid(b)) { invalid++; continue }
            if (Math.floorMod(b.startMs, baseLen) != 0L) { misaligned++; continue }
            if (byStart.put(b.startMs, b) != null) dup++
        }
        val sorted = byStart.values.sortedBy { it.startMs }
        val groups = sorted.groupBy { bucketStart(it.startMs, targetMin) }.toSortedMap()
        val out = mutableListOf<Agg>()
        var lead = 0
        val expected = targetMin / baseMin
        val keys = groups.keys.toList()
        for ((i, k) in keys.withIndex()) {
            val parts = groups.getValue(k)
            if (i == 0 && parts.first().startMs != k) { lead++; continue }   // true open unknown
            val allDone = parts.all { it.complete } && k + len <= nowMs
            if (!allDone && i != keys.lastIndex) continue                      // only the newest bar may be developing
            val vol = if (parts.all { it.volume != null }) parts.sumOf { it.volume!! } else null
            val buy = if (parts.all { it.buyVolume != null }) parts.sumOf { it.buyVolume!! } else null
            out += Agg(Bar(k, parts.first().open, parts.maxOf { it.high }, parts.minOf { it.low }, parts.last().close, vol, buy, allDone), parts.size, expected)
        }
        return Result(out, invalid, misaligned, dup, lead)
    }

    private fun fmt() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    fun label(ms: Long): String = fmt().format(java.util.Date(ms))

    /** Same text format as Twelve Data intraday candles ("yyyy-MM-dd HH:mm:ss", UTC, bar START time). */
    fun toCandle(a: Agg): CandleBar = CandleBar(datetime = label(a.bar.startMs), open = a.bar.open, high = a.bar.high, low = a.bar.low,
        close = a.bar.close, volume = a.bar.volume, buyVolume = a.bar.buyVolume)

    /**
     * For the Twelve Data / Binance paths: base candles labelled with their UTC start time. A base candle counts as
     * complete once its own end time has passed; the provider's newest candle is still forming.
     */
    fun alignCandles(candles: List<CandleBar>, baseMin: Int, group: Int, nowMs: Long): List<CandleBar> {
        if (group <= 1) return candles
        val bars = candles.mapNotNull { c ->
            val s = GoldApiService.parseCandleUtc(c.datetime) ?: return@mapNotNull null
            Bar(s, c.open, c.high, c.low, c.close, c.volume, c.buyVolume, s + baseMin * 60_000L <= nowMs)
        }
        return aggregate(bars, baseMin, baseMin * group, nowMs).bars.map { toCandle(it) }
    }
}

/** OANDA candle granularities (fixed list in the V20 docs) and how each app timeframe is built from them. */
object OandaGranularity {
    /** minutes -> OANDA granularity. Seconds, weekly and monthly are not used. */
    val NATIVE = linkedMapOf(1 to "M1", 2 to "M2", 4 to "M4", 5 to "M5", 10 to "M10", 15 to "M15", 30 to "M30",
        60 to "H1", 120 to "H2", 180 to "H3", 240 to "H4", 360 to "H6", 480 to "H8", 720 to "H12", 1440 to "D")

    /** The analysis timeframes asked for, in order. */
    val REQUESTED = listOf(2, 3, 5, 7, 10, 12, 15, 30)

    data class Plan(val targetMin: Int, val granularity: String, val baseMin: Int, val group: Int) {
        val native get() = group == 1
    }

    /** Native when OANDA has it, else the LARGEST native granularity that divides it exactly (fewest parts). */
    fun plan(targetMin: Int): Plan? {
        NATIVE[targetMin]?.let { return Plan(targetMin, it, targetMin, 1) }
        val base = NATIVE.keys.filter { it < targetMin && targetMin % it == 0 }.maxOrNull() ?: return null
        return Plan(targetMin, NATIVE.getValue(base), base, targetMin / base)
    }

    fun minutesOf(interval: String): Int? = when (val m = interval.lowercase().trim()) {
        "1min" -> 1; "15min" -> 15; "1day", "1d" -> 1440
        else -> when {
            m.endsWith("m") && m.dropLast(1).toIntOrNull() != null -> m.dropLast(1).toInt()
            m.endsWith("h") && m.dropLast(1).toIntOrNull() != null -> m.dropLast(1).toInt() * 60
            else -> null
        }
    }
}

/**
 * OANDA candles, read-only. Mid prices ("price=M"), UTC alignment for every granularity
 * (dailyAlignment=0, alignmentTimezone=UTC: without it OANDA aligns H2..D to 17:00 New York).
 */
object OandaCandles {
    const val MAX_COUNT = 5000

    fun url(granularity: String, count: Int) = OandaEndpoints.REST + "/instruments/" + OandaEndpoints.INSTRUMENT +
        "/candles?granularity=$granularity&count=${count.coerceIn(1, MAX_COUNT)}&price=M&dailyAlignment=0&alignmentTimezone=UTC"

    /** RFC3339 UTC, e.g. 2026-10-27T08:00:00Z */
    fun rfc(ms: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(java.util.Date(ms))

    /** Candles between two moments (used to check a prediction on the same provider). OANDA allows max 5000 per call. */
    fun rangeUrl(granularity: String, fromMs: Long, toMs: Long) = OandaEndpoints.REST + "/instruments/" + OandaEndpoints.INSTRUMENT +
        "/candles?granularity=$granularity&from=${rfc(fromMs)}&to=${rfc(toMs)}&price=M&dailyAlignment=0&alignmentTimezone=UTC"

    data class Fetch(val status: OandaStatus, val httpCode: Int, val bars: List<CandleAggregator.Bar>, val invalidRows: Int = 0, val fromCache: Boolean = false)

    /** Parses OANDA's candle JSON. Rows with a bad time or price are counted and skipped, never repaired. */
    fun parse(body: String): Pair<List<CandleAggregator.Bar>, Int>? {
        val rows = try { (MiniJson.parse(body) as? Map<*, *>)?.get("candles") as? List<*> } catch (_: Exception) { null } ?: return null
        var bad = 0
        val bars = rows.mapNotNull { r ->
            val m = r as? Map<*, *> ?: run { bad++; return@mapNotNull null }
            val ts = (m["time"] as? String)?.let { OandaDiagnostics.parseTime(it) }
            val mid = m["mid"] as? Map<*, *>
            fun p(k: String) = (mid?.get(k) as? String)?.toDoubleOrNull()
            val o = p("o"); val h = p("h"); val l = p("l"); val c = p("c")
            if (ts == null || o == null || h == null || l == null || c == null) { bad++; return@mapNotNull null }
            // "volume" in OANDA candles is a count of price updates, not traded volume: deliberately not used.
            CandleAggregator.Bar(ts, o, h, l, c, null, null, m["complete"] == true)
        }
        return bars to bad
    }
}

/** Conservative client-side limit for OANDA REST: at most [perSecond] per second and [perMinute] per minute. */
class OandaRateLimiter(private val perSecond: Int = 5, private val perMinute: Int = 120) {
    private val stamps = ArrayDeque<Long>()
    private var blockedUntil = 0L
    var requests = 0; private set
    var limited429 = 0; private set

    /** 0 = go now (and the request is counted); otherwise how many ms to wait. */
    @Synchronized fun tryAcquire(now: Long): Long {
        while (stamps.isNotEmpty() && now - stamps.first() >= 60_000L) stamps.removeFirst()
        if (now < blockedUntil) return blockedUntil - now
        val lastSec = stamps.count { now - it < 1_000L }
        if (lastSec >= perSecond) return 1_000L - (now - stamps.filter { now - it < 1_000L }.minOrNull()!!)
        if (stamps.size >= perMinute) return 60_000L - (now - stamps.first())
        stamps.addLast(now); requests++
        return 0L
    }

    /** OANDA said 429: no request for 10 s. */
    @Synchronized fun onRateLimited(now: Long) { blockedUntil = max(blockedUntil, now + 10_000L); limited429++ }
}

/**
 * Shared OANDA candle cache with request de-duplication: callers asking for the same granularity at the same time
 * wait for ONE request and share its answer. Cached answers live [ttlMs].
 */
class OandaCandleStore(
    private val transport: OandaTransport,
    private val token: () -> String,
    val limiter: OandaRateLimiter = OandaRateLimiter(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ttlMs: Long = 15_000L,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) }
) {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, OandaCandles.Fetch>>()
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()
    var lastLatencyMs = 0L; private set

    fun candles(granularity: String, count: Int, maxWaitMs: Long = 3_000L): OandaCandles.Fetch =
        fetch("$granularity:$count", OandaCandles.url(granularity, count), maxWaitMs)

    /** A fixed past range: cached the same way; [toMs] in the past means the answer cannot change. */
    fun range(granularity: String, fromMs: Long, toMs: Long, maxWaitMs: Long = 3_000L): OandaCandles.Fetch =
        fetch("$granularity:$fromMs-$toMs", OandaCandles.rangeUrl(granularity, fromMs, toMs), maxWaitMs)

    private fun fetch(key: String, url: String, maxWaitMs: Long): OandaCandles.Fetch {
        synchronized(locks.getOrPut(key) { Any() }) {
            val now = clock()
            cache[key]?.let { (at, f) -> if (now - at < ttlMs) return f.copy(fromCache = true) }
            val tok = token()
            if (tok.isBlank()) return OandaCandles.Fetch(OandaStatus.NOT_CONFIGURED, 0, emptyList())
            var waited = 0L
            while (true) {
                val w = limiter.tryAcquire(clock())
                if (w == 0L) break
                if (waited + w > maxWaitMs) return OandaCandles.Fetch(OandaStatus.RATE_LIMITED, 0, emptyList())
                sleep(w); waited += w
            }
            val t0 = clock()
            val r = transport.get(url, tok)
            lastLatencyMs = clock() - t0
            if (r.code == 429) limiter.onRateLimited(clock())
            if (r.code != 200) return OandaCandles.Fetch(OandaDiagnostics.mapCode(r.code, r.error), r.code, emptyList())
            val (bars, bad) = OandaCandles.parse(r.body) ?: return OandaCandles.Fetch(OandaStatus.BAD_RESPONSE, 200, emptyList())
            val f = OandaCandles.Fetch(OandaStatus.CONNECTED, 200, bars, bad)
            cache[key] = clock() to f
            if (cache.size > 200) cache.entries.sortedBy { it.value.first }.take(100).forEach { cache.remove(it.key) }
            return f
        }
    }
}

/**
 * Candle sync check for the Market Data Health panel: fetches the base granularities once (shared), builds the
 * requested timeframes and reports what is really there. Optionally compares one completed bar with Twelve Data.
 */
object CandleSync {
    const val BARS = 60

    data class Row(
        val targetMin: Int, val plan: String, val status: OandaStatus, val completed: Int = 0,
        val lastCompleteMs: Long = 0L, val lastClose: Double = Double.NaN, val developing: Boolean = false,
        val gappedBars: Int = 0, val misaligned: Int = 0, val invalid: Int = 0
    ) { val ok get() = status == OandaStatus.CONNECTED && completed > 0 && misaligned == 0 }

    data class Agreement(val interval: String, val barMs: Long, val oanda: Double, val twelve: Double) {
        val diff get() = oanda - twelve
        val pct get() = if (twelve > 0) abs(diff) / twelve * 100.0 else Double.NaN
        /** 0.05% of ~4000 = 2 USD: normal spread/feed difference between two quote sources. */
        val agrees get() = pct <= 0.05
    }

    data class Report(val rows: List<Row>, val atMs: Long, val requests: Int, val agreement: Agreement? = null, val agreementNote: String = "") {
        val okCount get() = rows.count { it.ok }
    }

    fun run(store: OandaCandleStore, now: () -> Long, targets: List<Int> = OandaGranularity.REQUESTED): Report {
        val plans = targets.mapNotNull { OandaGranularity.plan(it) }
        // one request per base granularity, big enough for the largest group that uses it
        val need = plans.groupBy { it.granularity }.mapValues { (_, ps) -> ps.maxOf { (BARS + 1) * it.group + it.group } }
        val before = store.limiter.requests
        val fetched = need.mapValues { (g, n) -> store.candles(g, n) }
        val t = now()
        val rows = plans.map { p ->
            val f = fetched.getValue(p.granularity)
            val label = if (p.native) p.granularity else "${p.granularity}×${p.group}"
            if (f.status != OandaStatus.CONNECTED) Row(p.targetMin, label, f.status)
            else {
                val r = CandleAggregator.aggregate(f.bars, p.baseMin, p.targetMin, t)
                val done = r.bars.filter { it.bar.complete }
                val last = done.lastOrNull()
                Row(p.targetMin, label, OandaStatus.CONNECTED, done.size, last?.bar?.startMs ?: 0L, last?.bar?.close ?: Double.NaN,
                    r.bars.lastOrNull()?.bar?.complete == false, done.takeLast(BARS).count { it.gaps > 0 }, r.droppedMisaligned, r.droppedInvalid + f.invalidRows)
            }
        }
        return Report(rows, t, store.limiter.requests - before)
    }

    /**
     * Compare the newest COMPLETED bar that both feeds have at the same UTC start time. The Twelve Data series' last
     * bar is still forming, so it is never used. Nothing is merged: this is a check only.
     */
    fun agreement(interval: String, twelve: List<CandleBar>, store: OandaCandleStore, now: Long): Pair<Agreement?, String> {
        val mins = OandaGranularity.minutesOf(interval) ?: return null to "timeframe not comparable"
        val p = OandaGranularity.plan(mins) ?: return null to "timeframe not comparable"
        if (twelve.size < 3) return null to "no Twelve Data candles on screen"
        val tdDone = twelve.dropLast(1).mapNotNull { c -> GoldApiService.parseCandleUtc(c.datetime)?.let { it to c.close } }.toMap()
        val f = store.candles(p.granularity, (BARS + 1) * p.group + p.group)
        if (f.status != OandaStatus.CONNECTED) return null to f.status.name
        val oa = CandleAggregator.aggregate(f.bars, p.baseMin, p.targetMin, now).bars.filter { it.bar.complete }
        val both = oa.lastOrNull { tdDone.containsKey(it.bar.startMs) } ?: return null to "no common completed bar"
        return Agreement(interval, both.bar.startMs, both.bar.close, tdDone.getValue(both.bar.startMs)) to ""
    }
}
