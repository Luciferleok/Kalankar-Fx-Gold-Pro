package com.example.livegoldai.data

import com.example.livegoldai.model.EconomicEvent

/**
 * V35.0 (owner decision, 2 Nov 2026): the Macro & News pillar votes ONLY around high-impact USD news.
 *
 * Evidence (Pillar check, phone 2 Nov): Macro's own vote was right 42% of 660 independent windows (99.9% range
 * 36-49%), i.e. wrong more often than chance. It votes from the DAY's DXY / 10Y-yield change on every 5m-1d candle.
 *
 * Rule: the pillar is ACTIVE from [PRE_MS] (1 hour) before a high-impact USD event until [POST_MS] (30 minutes)
 * after it. At all other times it takes no part in the forecast: no vote, no weight in the 68% gate, not counted in
 * the 5-of-7 rule (the gate then uses the other 6 pillars). With no calendar loaded, it stays OFF (no evidence of news).
 * Its data is still shown as context, and its raw vote is still recorded ("ctx:macro") for research.
 */
object MacroWindow {
    const val PRE_MS = 60 * 60_000L
    const val POST_MS = 30 * 60_000L

    /** The high-impact USD event whose window contains [now], or null (Macro OFF). */
    fun activeEvent(events: List<EconomicEvent>, now: Long): EconomicEvent? =
        events.filter { it.impact.equals("High", true) && it.country.equals("USD", true) }
            .mapNotNull { ev -> EventTime.parseMs(ev.isoTime)?.let { ev to it } }
            .filter { (_, t) -> now >= t - PRE_MS && now <= t + POST_MS }
            .minByOrNull { (_, t) -> kotlin.math.abs(t - now) }?.first

    fun isActive(events: List<EconomicEvent>, now: Long): Boolean = activeEvent(events, now) != null

    /** Weights that take part in the gate: Macro's weight only while it is active. */
    fun weights(macroActive: Boolean): Map<String, Double> =
        if (macroActive) TechnicalEngine.PILLAR_WEIGHTS else TechnicalEngine.PILLAR_WEIGHTS - "macro"
}
