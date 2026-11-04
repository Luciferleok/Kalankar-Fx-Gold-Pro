package com.example.livegoldai.data

import com.example.livegoldai.model.EconomicEvent

/**
 * V35.0 (owner decision, 2 Nov 2026): the Macro & News pillar votes ONLY around high-impact USD news.
 *
 * Evidence (Pillar check, phone 2 Nov): Macro's own vote was right 42% of 660 independent windows (99.9% range
 * 36-49%), i.e. wrong more often than chance. It votes from the DAY's DXY / 10Y-yield change on every 5m-1d candle.
 *
 * Rule: the pillar is ACTIVE from [PRE_MS] (1 hour) before a high-impact USD event until [POST_MS] (30 minutes)
 * after it (V35.3-G: 60 minutes after the start of a Fed press conference, so it covers the whole conference). At all other times it takes no part in the forecast: no vote, no weight in the 68% gate, not counted in
 * the 5-of-7 rule (the gate then uses the other 6 pillars). With no calendar loaded, it stays OFF (no evidence of news).
 * Its data is still shown as context, and its raw vote is still recorded ("ctx:macro") for research.
 */
object MacroWindow {
    const val PRE_MS = 60 * 60_000L
    const val POST_MS = 30 * 60_000L

    /**
     * The high-impact USD event whose window contains [now], or null (Macro OFF).
     * V35.3-F: deterministic when windows overlap: the most recent event ALREADY released (its reaction is what the market
     * is trading), else the earliest upcoming one. Before: the nearest in time, so 16 min after an FOMC statement the press
     * conference (14 min ahead) was chosen and the statement's reaction was read as "pre-release" drift.
     */
    /** V35.3-G (owner, 3 Nov 2026): Macro stays on through the Fed press conference (about an hour), then switches off */
    const val PRESS_POST_MS = 60 * 60_000L
    fun postMs(ev: EconomicEvent): Long = if (OfficialCalendar.kind(ev.title) == "FOMC_PRESS") PRESS_POST_MS else POST_MS

    fun activeEvent(events: List<EconomicEvent>, now: Long): EconomicEvent? {
        val c = events.filter { it.impact.equals("High", true) && it.country.equals("USD", true) }
            .mapNotNull { ev -> EventTime.parseMs(ev.isoTime)?.let { ev to it } }
            .filter { (ev, t) -> now >= t - PRE_MS && now <= t + postMs(ev) }
        return (c.filter { it.second <= now }.maxByOrNull { it.second } ?: c.minByOrNull { it.second })?.first
    }

    /** stable id of an event: kind of release + its time */
    fun eventId(ev: EconomicEvent): String = OfficialCalendar.kind(ev.title) + "@" + (EventTime.parseMs(ev.isoTime) ?: 0L)

    fun isActive(events: List<EconomicEvent>, now: Long): Boolean = activeEvent(events, now) != null

    /**
     * V35.5-A: the news phase an analysis was made in: "OFF", "<eventId>:PRE" (window open, before the release) or
     * "<eventId>:POST" (release out). A cached analysis is only reused inside the SAME phase.
     */
    fun phase(events: List<EconomicEvent>, now: Long): String {
        val ev = activeEvent(events, now) ?: return "OFF"
        val t = EventTime.parseMs(ev.isoTime) ?: return "OFF"
        return eventId(ev) + if (now >= t) ":POST" else ":PRE"
    }

    /** inside a window a new 5-minute bar can change Macro's evidence, so a cached analysis lives at most this long */
    const val IN_WINDOW_CACHE_MS = 5 * 60_000L

    /** V35.5-A: may the screen reuse a cached analysis? Never across a phase change (OFF -> PRE -> POST -> OFF). */
    fun cacheReusable(cachedAtMs: Long, cachedPhase: String?, now: Long, phaseNow: String, ttlMs: Long): Boolean {
        if (cachedPhase != phaseNow) return false
        val ttl = if (phaseNow != "OFF") minOf(ttlMs, IN_WINDOW_CACHE_MS) else ttlMs
        return now - cachedAtMs in 0 until ttl
    }

    /** Weights that take part in the gate: Macro's weight only while it is active. */
    fun weights(macroActive: Boolean): Map<String, Double> =
        if (macroActive) TechnicalEngine.PILLAR_WEIGHTS else TechnicalEngine.PILLAR_WEIGHTS - "macro"
}
