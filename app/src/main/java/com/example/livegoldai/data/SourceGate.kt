package com.example.livegoldai.data

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max

/**
 * V29A TRUST CORE: instrument identity, source compatibility, Twelve Data credit budget and the
 * source state machine that decides whether a production BUY/SELL may be published at all.
 *
 * XAU/USD spot (Twelve Data) is the only instrument the production model was built and verified on.
 * PAXG/USDT (Binance) and GC=F (Yahoo) are different markets: they may be SHOWN as context, never
 * silently used to publish or record an XAU/USD call.
 */
enum class InstrumentId { XAU_USD_SPOT, PAXG_USDT, GC_F, SIMULATED, LEGACY_UNKNOWN }
enum class ProviderId { OANDA, TWELVE_DATA, BINANCE, YAHOO, NONE }
enum class SourceType { PRIMARY, BACKUP, PROXY, SIMULATED }
enum class Compatibility { COMPATIBLE, PROXY_ONLY, INCOMPATIBLE }

object SourceRegistry {
    data class Identity(val instrument: InstrumentId, val provider: ProviderId, val symbol: String, val type: SourceType)

    /** Explicit table: nothing is inferred. Unknown labels are LEGACY_UNKNOWN / INCOMPATIBLE. */
    fun identify(feedSource: String?): Identity {
        val s = feedSource ?: ""
        return when {
            // V32: OANDA practice XAU_USD = the same spot instrument from another provider (read-only)
            s.contains("OANDA", true) && s.contains("XAU", true) -> Identity(InstrumentId.XAU_USD_SPOT, ProviderId.OANDA, "XAU_USD", SourceType.PRIMARY)
            s.contains("Twelve", true) && s.contains("XAU", true) -> Identity(InstrumentId.XAU_USD_SPOT, ProviderId.TWELVE_DATA, "XAU/USD", SourceType.PRIMARY)
            s.contains("PAXG", true) -> Identity(InstrumentId.PAXG_USDT, ProviderId.BINANCE, "PAXGUSDT", SourceType.PROXY)
            s.contains("GC=F", true) -> Identity(InstrumentId.GC_F, ProviderId.YAHOO, "GC=F", SourceType.BACKUP)
            s.contains("DEMO", true) || s.contains("SIMULATED", true) -> Identity(InstrumentId.SIMULATED, ProviderId.NONE, "--", SourceType.SIMULATED)
            else -> Identity(InstrumentId.LEGACY_UNKNOWN, ProviderId.NONE, "--", SourceType.SIMULATED)
        }
    }

    /** Compatibility with the XAU/USD production model. */
    fun compatibility(id: InstrumentId): Compatibility = when (id) {
        InstrumentId.XAU_USD_SPOT -> Compatibility.COMPATIBLE
        InstrumentId.PAXG_USDT, InstrumentId.GC_F -> Compatibility.PROXY_ONLY
        else -> Compatibility.INCOMPATIBLE
    }

    /** Short badge for the UI: "XAU/USD • TWELVE DATA • PRIMARY" etc. */
    fun badge(feedSource: String?): String {
        val i = identify(feedSource)
        return when (i.instrument) {
            InstrumentId.XAU_USD_SPOT -> when {
                i.provider == ProviderId.OANDA -> "XAU/USD • OANDA • PRIMARY"
                (feedSource ?: "").contains("backup", true) -> "XAU/USD • TWELVE DATA • BACKUP"
                else -> "XAU/USD • TWELVE DATA • PRIMARY"
            }
            InstrumentId.PAXG_USDT -> "PAXG/USDT • BINANCE • PROXY CONTEXT"
            InstrumentId.GC_F -> "GC=F • YAHOO • BACKUP CONTEXT"
            else -> "NO LIVE DATA"
        }
    }
}

/**
 * Twelve Data credit budget (free plan: 8 calls per minute, 800 per UTC day).
 * Every Twelve Data call asks here first, so the screen, the background recorder and the Shadow Lab
 * never collide and trigger HTTP 429. A 429 from the server blocks the rest of that minute.
 */
class CreditBudget(val perMinute: Int = 8, val perDay: Int = 800) {
    private val stamps = ArrayDeque<Long>()
    private var day = -1L
    private var dayCount = 0
    private var blockedUntil = 0L

    private fun utcDay(now: Long) = now / 86_400_000L

    /** 0 = a credit was taken; otherwise how many ms to wait before asking again (Long.MAX_VALUE = day used up). */
    @Synchronized
    fun tryAcquire(now: Long): Long {
        if (utcDay(now) != day) { day = utcDay(now); dayCount = 0 }
        if (dayCount >= perDay) return Long.MAX_VALUE
        if (now < blockedUntil) return blockedUntil - now
        while (stamps.isNotEmpty() && now - stamps.first() >= 60_000L) stamps.removeFirst()
        if (stamps.size >= perMinute) return 60_000L - (now - stamps.first()) + 50
        stamps.addLast(now); dayCount++
        return 0L
    }

    /** The server answered 429: no more calls until the current minute window has passed. */
    @Synchronized
    fun onRateLimited(now: Long) { blockedUntil = max(blockedUntil, now + 61_000L) }

    @Synchronized fun usedToday(now: Long): Int { if (utcDay(now) != day) return 0; return dayCount }
    @Synchronized fun usedLastMinute(now: Long): Int = stamps.count { now - it < 60_000L }

    /**
     * V29A.11 pacing: how long the SCREEN should reuse its last fetch so the day's credits last until UTC midnight.
     * The background recorder and verification need about [reservePerHour] credits per hour; the screen gets the rest,
     * spread evenly over the hours left. 0 = no pacing needed (plenty left).
     */
    @Synchronized
    fun screenPaceMs(now: Long, reservePerHour: Int = 24): Long {
        val used = if (utcDay(now) != day) 0 else dayCount
        val hoursLeft = maxOf(0.25, ((utcDay(now) + 1) * 86_400_000L - now) / 3_600_000.0)
        val spare = perDay - used - reservePerHour * hoursLeft
        val perHour = spare / hoursLeft
        return when {
            perHour >= 60 -> 0L
            perHour <= 1 -> 30 * 60_000L
            else -> (3_600_000.0 / perHour).toLong()
        }
    }

    /** Waits (up to [maxWaitMs]) for a credit. */
    suspend fun acquire(maxWaitMs: Long, now: () -> Long = { System.currentTimeMillis() }): Boolean {
        val start = now()
        while (true) {
            val w = tryAcquire(now())
            if (w == 0L) return true
            if (w == Long.MAX_VALUE || now() - start + w > maxWaitMs) return false
            kotlinx.coroutines.delay(w.coerceAtMost(5_000L))
        }
    }
}

/**
 * Per-timeframe source state machine.
 *  PRIMARY_ACTIVE         Twelve Data XAU/USD, quality checks pass → production allowed
 *  PRIMARY_DEGRADED       Twelve Data data present but a quality check fails → WAIT_DATA_DEGRADED
 *  PROXY_CONTEXT_ACTIVE   showing PAXG/USDT or GC=F → WAIT_SOURCE_SWITCH
 *  INCOMPATIBLE           simulated / unknown → WAIT_SOURCE_SWITCH (never recorded)
 *  RESTORING              Twelve Data back after a switch, needs [RESTORE_PASSES] passing fetches in a row
 */
class SourceStateMachine {
    companion object {
        const val RESTORE_PASSES = 2
        const val PRIMARY_ACTIVE = "PRIMARY_ACTIVE"
        const val PRIMARY_DEGRADED = "PRIMARY_DEGRADED"
        const val PROXY_CONTEXT_ACTIVE = "PROXY_CONTEXT_ACTIVE"
        const val INCOMPATIBLE = "INCOMPATIBLE"
        const val RESTORING = "RESTORING"
    }

    data class Decision(
        val state: String,
        val allowed: Boolean,
        val reason: String,          // "" when allowed, else WAIT_SOURCE_SWITCH / WAIT_DATA_DEGRADED / WAIT_NO_DATA_KEY
        val detail: String,
        val changedFrom: String?     // previous state when this call changed it (for the SOURCE_EVENT log)
    )

    data class Candle(val time: String, val open: Double, val high: Double, val low: Double, val close: Double)

    private class Track(var state: String = "", var passes: Int = 0, var lastGoodClose: Double = 0.0, var lastGoodAt: Long = 0L, var provider: String = "",
                        var lastFetchId: Long = Long.MIN_VALUE, var last: Decision? = null)
    private val tracks = HashMap<String, Track>()

    @Synchronized fun stateOf(interval: String): String = tracks[interval]?.state ?: ""

    /**
     * @param candles the candles of this fetch, oldest first, newest may be forming
     * @param keyMissing true when no Twelve Data key is saved
     */
    @Synchronized
    fun evaluate(interval: String, feedSource: String?, candles: List<Candle>, intervalMs: Long, marketClosed: Boolean, now: Long,
                 keyMissing: Boolean = false, fetchId: Long = now): Decision {
        val t = tracks.getOrPut(interval) { Track() }
        // the same fetch evaluated twice (screen + recorder, or a cached result) must not count as two clean fetches
        t.last?.let { if (t.lastFetchId == fetchId) return it.copy(changedFrom = null) }
        val d = evaluateNew(t, feedSource, candles, intervalMs, marketClosed, now, keyMissing)
        t.lastFetchId = fetchId; t.last = d
        return d
    }

    private fun evaluateNew(t: Track, feedSource: String?, candles: List<Candle>, intervalMs: Long, marketClosed: Boolean, now: Long, keyMissing: Boolean): Decision {
        val prev = t.state
        val id = SourceRegistry.identify(feedSource)
        fun set(state: String, allowed: Boolean, reason: String, detail: String): Decision {
            t.state = state
            return Decision(state, allowed, reason, detail, if (prev != state) prev else null)
        }
        if (SourceRegistry.compatibility(id.instrument) != Compatibility.COMPATIBLE) {
            t.passes = 0
            val why = if (keyMissing) "WAIT_NO_DATA_KEY" else "WAIT_SOURCE_SWITCH"
            return if (id.type == SourceType.SIMULATED) set(INCOMPATIBLE, false, why, "no compatible live data")
            else set(PROXY_CONTEXT_ACTIVE, false, why, "${id.symbol} is shown as context only; XAU/USD decision paused")
        }
        // V32: a change of PROVIDER (OANDA <-> Twelve Data) is a source switch too: decisions wait for clean fetches
        val providerChanged = t.provider.isNotEmpty() && t.provider != id.provider.name
        val fromProvider = t.provider
        t.provider = id.provider.name
        if (providerChanged) t.passes = 0
        val name = providerName(id.provider)
        val q = quality(candles, intervalMs, marketClosed, now, t.lastGoodClose, t.lastGoodAt)
        if (q != null) {
            t.passes = 0
            return set(PRIMARY_DEGRADED, false, "WAIT_DATA_DEGRADED", q)
        }
        t.lastGoodClose = candles.last().close; t.lastGoodAt = now
        val wasSwitched = providerChanged || prev == PROXY_CONTEXT_ACTIVE || prev == INCOMPATIBLE || prev == PRIMARY_DEGRADED || prev == RESTORING
        if (wasSwitched) {
            t.passes++
            if (t.passes < RESTORE_PASSES) return set(RESTORING, false, "WAIT_SOURCE_SWITCH",
                (if (providerChanged) "provider changed ${providerName(ProviderId.values().firstOrNull { it.name == fromProvider })} -> $name: " else "$name back: ") +
                    "${t.passes}/$RESTORE_PASSES clean fetches")
        }
        t.passes = RESTORE_PASSES
        return set(PRIMARY_ACTIVE, true, "", "$name XAU/USD, checks passed")
    }

    private fun providerName(p: ProviderId?) = when (p) { ProviderId.OANDA -> "OANDA"; ProviderId.TWELVE_DATA -> "Twelve Data"; ProviderId.BINANCE -> "Binance"; ProviderId.YAHOO -> "Yahoo"; else -> "--" }

    /** null = all checks pass, else the first failing check. */
    fun quality(candles: List<Candle>, intervalMs: Long, marketClosed: Boolean, now: Long, lastGoodClose: Double, lastGoodAt: Long): String? {
        if (candles.size < 30) return "too few candles (${candles.size})"
        for (c in candles.takeLast(50)) {
            if (c.close <= 0 || c.open <= 0 || c.low <= 0) return "zero or negative price"
            if (c.high + 1e-9 < maxOf(c.open, c.close) || c.low - 1e-9 > minOf(c.open, c.close)) return "candle high/low inconsistent at ${c.time}"
        }
        val times = candles.takeLast(21).mapNotNull { ShadowLive.barMs(it.time) }
        if (times.size < 2) return "candle times unreadable"
        for (i in 1 until times.size) if (times[i] <= times[i - 1]) return "candle times out of order"
        if (!marketClosed && intervalMs > 0) {
            val age = now - times.last()
            if (age > 2 * intervalMs + 120_000L) return "newest candle is ${age / 60_000} min old"
            var gaps = 0
            // daily and slower candles: a holiday is one missing day, not a data fault
            val allowed = if (intervalMs >= 86_400_000L) 3 * intervalMs else intervalMs * 3 / 2
            for (i in 1 until times.size) if (times[i] - times[i - 1] > allowed && !spansWeekend(times[i - 1], times[i])) gaps++
            if (gaps > 0) return "$gaps missing candle(s) in the last 20 (weekends excluded)"
        }
        if (lastGoodClose > 0 && now - lastGoodAt < 3 * 86_400_000L) {
            val last = candles.last().close
            val atr = candles.takeLast(15).zipWithNext { a, b -> maxOf(b.high - b.low, abs(b.high - a.close), abs(b.low - a.close)) }.average()
            val limit = maxOf(3 * atr, lastGoodClose * 0.01)
            if (abs(last - lastGoodClose) > limit) return String.format(java.util.Locale.US, "price jumped %.2f since the last good fetch (limit %.2f)", abs(last - lastGoodClose), limit)
        }
        return null
    }

    private fun spansWeekend(a: Long, b: Long): Boolean {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        var x = a
        while (x < b) {
            cal.timeInMillis = x
            val d = cal.get(Calendar.DAY_OF_WEEK)
            if (d == Calendar.SATURDAY || d == Calendar.SUNDAY) return true
            x += 3 * 3600_000L
        }
        return false
    }
}
