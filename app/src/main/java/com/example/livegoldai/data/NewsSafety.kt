package com.example.livegoldai.data

import com.example.livegoldai.model.CandleBar
import com.example.livegoldai.model.EconomicEvent
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal

/**
 * V35.10.1 news safety (owner, 5 Nov 2026, before NFP: "Sirf info + WAIT safety").
 *
 * Before: GoldApiService.applyNewsMode() REPLACED the pillar-gated decision with a gold-price reaction signal from
 * 15 min before to 45 min after ANY high-impact event (any country). It could turn a pillar WAIT into BUY / SELL and a
 * SELL into BUY, measured the move from an in-memory "baseline" that was the first price it happened to see (also a
 * post-release price, or another provider's price), on the live tick, not a completed candle.
 *
 * Now: the published direction ALWAYS comes from the pillars. News can only HOLD a pillar BUY / SELL as WAIT, never
 * create or flip one. One window and one event for everything: [MacroWindow] (high-impact USD, 60 min before to 30 min
 * after the release, Fed press 60 min). Holds:
 *  SPIKE_FREEZE : the last [SPIKE_FREEZE_MS] before the release (spreads widen; unchanged from the old rule)
 *  FIRST_BAR    : after the release, until [FIRST_BAR_MS] have passed AND one candle that OPENED at / after the release
 *                 has COMPLETED (old rule: 5 minutes by the wall clock only)
 *  NO_BASELINE  : no completed candle that closed at / before the release in this data (e.g. app started late):
 *                 the reaction is INCOMPLETE, never a made-up move
 *  FLAT         : the completed reaction is smaller than [REACTION_MIN] dollars (unchanged threshold)
 *  AGAINST      : the completed reaction points the other way from the pillars
 * The reaction is measured on ONE candle list (one provider, one instrument): last completed close at / before the
 * release -> last completed close after it. No look-ahead: a candle counts only once its close time has passed.
 */
object NewsSafety {
    const val VERSION = "NS1"
    const val SPIKE_FREEZE_MS = 15 * 60_000L
    const val FIRST_BAR_MS = 5 * 60_000L
    const val REACTION_MIN = 5.0

    enum class Reaction { UP, DOWN, FLAT, INCOMPLETE }

    data class Check(
        val event: EconomicEvent,
        val eventId: String,
        val releaseMs: Long,
        val post: Boolean,
        val reaction: Reaction,
        val move: Double?,          // completed reaction in dollars, null when INCOMPLETE
        val baseBar: String,        // candle the baseline close came from ("" = none)
        val reactBar: String,       // last completed candle after the release ("" = none)
        val pillar: Signal,         // the pillar-gated decision (published unless held)
        val hold: String,           // "" or SPIKE_FREEZE / FIRST_BAR / NO_BASELINE / FLAT / AGAINST
        val final: Signal
    )

    fun evaluate(a: GoldAnalysisResult, events: List<EconomicEvent>, now: Long): Check? {
        val ev = MacroWindow.activeEvent(events, now) ?: return null
        val t = EventTime.parseMs(ev.isoTime) ?: return null
        val pillar = a.overallSignal
        val post = now >= t
        val ivMs = RealityEngine.intervalMinutes(a.interval) * 60_000L
        val bars = a.chartCandles.ifEmpty { a.recentCandles }
            .mapNotNull { c -> GoldApiService.parseCandleUtc(c.datetime)?.let { it to c } }
            .filter { (open, _) -> open + ivMs <= now }          // completed candles only (no look-ahead)
            .sortedBy { it.first }
        val base: Pair<Long, CandleBar>? = bars.lastOrNull { (open, _) -> open + ivMs <= t }
        val react: Pair<Long, CandleBar>? = if (post) bars.lastOrNull { (open, _) -> open >= t } else null
        val move = if (base != null && react != null) react.second.close - base.second.close else null
        val reaction = when {
            move == null -> Reaction.INCOMPLETE
            move >= REACTION_MIN -> Reaction.UP
            move <= -REACTION_MIN -> Reaction.DOWN
            else -> Reaction.FLAT
        }
        val hold = when {
            pillar == Signal.WAIT -> ""
            !post -> if (t - now <= SPIKE_FREEZE_MS) "SPIKE_FREEZE" else ""
            now < t + FIRST_BAR_MS || react == null -> "FIRST_BAR"
            base == null -> "NO_BASELINE"
            reaction == Reaction.FLAT -> "FLAT"
            (reaction == Reaction.UP && pillar == Signal.SELL) || (reaction == Reaction.DOWN && pillar == Signal.BUY) -> "AGAINST"
            else -> ""
        }
        return Check(ev, MacroWindow.eventId(ev), t, post, reaction, move, base?.second?.datetime ?: "", react?.second?.datetime ?: "",
            pillar, hold, if (hold.isEmpty()) pillar else Signal.WAIT)
    }

    /** the research reaction as a signal (information only, never published) */
    fun reactionSignal(r: Reaction): Signal = when (r) { Reaction.UP -> Signal.BUY; Reaction.DOWN -> Signal.SELL; else -> Signal.WAIT }
}
