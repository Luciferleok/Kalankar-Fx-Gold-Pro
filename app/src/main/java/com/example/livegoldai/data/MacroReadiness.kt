package com.example.livegoldai.data

import com.example.livegoldai.model.EconomicEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * V35.5-F NFP readiness check (owner choice, 4 Nov 2026). On request only (one button in SYSTEM): read-only GETs of the
 * same instruments the Macro news window uses, and the next high-impact USD event from the same calendar. It answers
 * "would Macro have usable data, from which source, if the news were now?" It changes nothing: no vote, no level,
 * no ledger, no setting. A READY result now is not a promise for the release minute (providers can lag at news time).
 */
object MacroReadiness {
    const val READY = "READY"; const val STALE = "STALE"; const val UNAVAILABLE = "UNAVAILABLE"; const val NOT_SET_UP = "NOT SET UP"

    data class Source(val provider: String, val instrument: String, val state: String, val detail: String)
    data class Report(
        val atMs: Long,
        val nextEvent: String,          // "NFP · Fri 06 Nov 19:00 · window 18:00-19:30" (device time zone) or "none this week"
        val dollarPath: String,         // which source the dollar would come from, or "NONE"
        val tenYPath: String,
        val verdict: String,            // READY / PARTIAL / NOT READY
        val sources: List<Source>,
        val note: String
    )

    /** one instrument: [status] null = Yahoo (no status code); bars = (bar start ms, close) */
    fun source(provider: String, instrument: String, status: OandaStatus?, bars: List<Pair<Long, Double>>, now: Long): Source {
        if (status == OandaStatus.NOT_CONFIGURED) return Source(provider, instrument, NOT_SET_UP, "OANDA token not saved")
        if (status != null && status != OandaStatus.CONNECTED) return Source(provider, instrument, UNAVAILABLE, status.name.replace('_', ' '))
        val done = bars.filter { it.first + NewsMacro.BAR_MS <= now && it.second.isFinite() && it.second > 0 }
        val newest = done.maxOfOrNull { it.first } ?: return Source(provider, instrument, UNAVAILABLE, "no completed 5-minute bar")
        val ageMin = (now - (newest + NewsMacro.BAR_MS)) / 60_000L
        return if (now - (newest + NewsMacro.BAR_MS) <= NewsMacro.MAX_AGE_MS) Source(provider, instrument, READY, "${done.size} bars · newest ${ageMin}m old")
            else Source(provider, instrument, STALE, "newest completed bar ${ageMin}m old (limit ${NewsMacro.MAX_AGE_MS / 60_000L}m)")
    }

    /** the same source order the news window uses (GoldApiService.macroIndex) */
    fun evaluate(now: Long, events: List<EconomicEvent>, sources: List<Source>, tz: TimeZone = TimeZone.getDefault()): Report {
        fun ok(p: String, i: String) = sources.any { it.provider == p && it.instrument == i && it.state == READY }
        val dollar = when {
            OandaMacro.DXY_PAIRS.keys.all { ok("OANDA", it) } -> "OANDA computed dollar index (6 pairs)"
            ok("OANDA", "EUR_USD") -> "OANDA EUR/USD proxy (inverted)"
            ok("YAHOO", "DX-Y.NYB") -> "Yahoo dollar index (can be delayed)"
            ok("YAHOO", "EURUSD=X") -> "Yahoo EUR/USD proxy (can be delayed)"
            else -> "NONE"
        }
        val tenY = when {
            ok("OANDA", OandaMacro.NOTE) -> "OANDA 10Y T-Note price"
            ok("YAHOO", "^TNX") -> "Yahoo 10Y yield (can be delayed)"
            else -> "NONE"
        }
        val verdict = when {
            dollar.startsWith("OANDA") && tenY.startsWith("OANDA") -> "READY"
            dollar == "NONE" && tenY == "NONE" -> "NOT READY"
            else -> "PARTIAL"
        }
        return Report(now, nextEvent(events, now, tz), dollar, tenY, verdict, sources,
            "Checked now, not at the release: re-check about 15 minutes before the window opens. Read-only; changes nothing.")
    }

    /** the active or next high-impact USD event, its time and its Macro window, in the phone's time zone */
    fun nextEvent(events: List<EconomicEvent>, now: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val ev = MacroWindow.activeEvent(events, now) ?: events
            .filter { it.impact.equals("High", true) && it.country.equals("USD", true) }
            .mapNotNull { e -> EventTime.parseMs(e.isoTime)?.let { e to it } }
            .filter { it.second > now }.minByOrNull { it.second }?.first
            ?: return "No high-impact USD event in the loaded calendar"
        val t = EventTime.parseMs(ev.isoTime) ?: return "No high-impact USD event in the loaded calendar"
        val day = SimpleDateFormat("EEE dd MMM HH:mm", Locale.US).apply { timeZone = tz }
        val hm = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = tz }
        val zone = tz.getDisplayName(tz.inDaylightTime(Date(t)), TimeZone.SHORT, Locale.US)
        val live = if (MacroWindow.isActive(events, now)) " · WINDOW ACTIVE NOW" else ""
        return "${OfficialCalendar.kind(ev.title)} (${ev.title}) · ${day.format(Date(t))} $zone · window ${hm.format(Date(t - MacroWindow.PRE_MS))}-${hm.format(Date(t + MacroWindow.postMs(ev)))}$live"
    }
}
