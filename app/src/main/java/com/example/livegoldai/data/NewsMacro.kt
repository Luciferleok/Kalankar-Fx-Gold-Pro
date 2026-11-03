package com.example.livegoldai.data

import com.example.livegoldai.model.MacroMarketIndex
import com.example.livegoldai.model.Signal
import java.util.Locale

/**
 * V35.2: Macro's vote INSIDE the news window, from the minutes around the release instead of the whole day's change.
 *
 *  - before the release: the move over the last 60 minutes (how the dollar / yields are positioning into the event);
 *  - after the release: the move from the last price before the release to now (the market's reaction to the number).
 * Dollar up / yields up = headwind for gold (SELL vote); down = support (BUY vote); small moves = no vote.
 * A price older than [MAX_AGE_MS] is not used (Yahoo can be delayed): then no vote, said in the explanation.
 * These are the usual relations, not a promise: the Pillar check measures how often the news-window vote was right.
 */
object NewsMacro {
    const val DXY_MIN_PCT = 0.08          // dollar index move worth a vote
    const val YIELD_MIN_BP = 2.0          // 10Y yield move in basis points worth a vote
    const val MAX_AGE_MS = 20 * 60_000L          // V35.3-F: measured from the END of the newest completed bar

    data class Move(val change: Double, val fromMs: Long, val toMs: Long, val label: String)

    /** Yahoo bars are stamped at their START; a bar's close is the price at its END (or "now" while it is forming). */
    const val BAR_MS = 5 * 60_000L

    /**
     * [bars] = (bar start ms, close), any order. V35.3-B: the price AT a moment is the close of the last bar that ENDED by
     * then (before: the bar that STARTED at the release was taken as the pre-release price, although its close is after the
     * release: 3 min after NFP the move was not measured at all, 12 min after it 0.05% instead of 0.30%).
     *  - before the release: from the price 60 min ago to the latest price;
     *  - after the release: from the price at the release to the latest price, which must come from a bar that started
     *    at or after the release (otherwise there is no post-release price yet: no vote).
     * Bars stamped in the future are ignored; a latest bar older than [MAX_AGE_MS] means no vote.
     */
    fun move(bars: List<Pair<Long, Double>>, eventMs: Long, now: Long): Move? {
        // V35.3-F: two different values for one timestamp = untrustworthy series: no move
        if (bars.groupBy { it.first }.values.any { g -> g.map { it.second }.distinct().size > 1 }) return null
        // completed bars only (a bar's close is final once its 5 minutes are over)
        val b = bars.distinctBy { it.first }.filter { it.first + BAR_MS <= now && it.second.isFinite() && it.second > 0 }.sortedBy { it.first }
        val cur = b.lastOrNull() ?: return null
        if (now - (cur.first + BAR_MS) > MAX_AGE_MS) return null
        val after = now >= eventMs
        val (baseAt, label) = if (!after) (now - 60 * 60_000L) to "last 60 min" else eventMs to "since the release"
        // after the release the latest price must come from a COMPLETED bar that started at or after it
        if (after && cur.first < eventMs) return null
        val base = b.lastOrNull { it.first + BAR_MS <= baseAt } ?: return null
        if (base.first >= cur.first) return null
        return Move(cur.second - base.second, base.first, cur.first, label)
    }

    /**
     * V35.3-F: the US-dollar DIRECTION from EUR/USD (EUR/USD down = dollar up). Not the dollar index: its own symbol, the
     * real EUR/USD price as value, and a label saying so. The 0.08% threshold was set for the dollar index and has not been
     * validated separately for this proxy (said in the text).
     */
    fun usdProxy(eurUsd: List<Pair<Long, Double>>, eventMs: Long, now: Long, event: String): MacroMarketIndex? {
        val r = dxy(eurUsd.map { it.first to 1.0 / it.second }, eventMs, now, event) ?: return null
        val m = move(eurUsd, eventMs, now) ?: return null
        val eur = eurUsd.first { it.first == m.toMs }.second
        return r.copy(symbol = PROXY_SYMBOL, name = PROXY_NAME, value = eur,
            explanation = r.explanation.replace("US Dollar", "USD DIRECTION PROXY — INVERTED EUR/USD (EUR/USD ${String.format(Locale.US, "%.4f", eur)}): dollar") +
                " · proxy threshold not separately validated")
    }
    const val PROXY_SYMBOL = "EURUSD=X (inverted)"
    const val PROXY_NAME = "USD direction proxy (inverted EUR/USD)"

    fun dxy(bars: List<Pair<Long, Double>>, eventMs: Long, now: Long, event: String): MacroMarketIndex? {
        val m = move(bars, eventMs, now) ?: return null
        val base = bars.firstOrNull { it.first == m.fromMs }?.second ?: return null
        val last = bars.first { it.first == m.toMs }.second
        val pct = m.change / base * 100.0
        val sig = when { pct >= DXY_MIN_PCT -> Signal.SELL; pct <= -DXY_MIN_PCT -> Signal.BUY; else -> Signal.WAIT }
        val p = String.format(Locale.US, "%+.2f", pct)
        val why = when (sig) {
            Signal.SELL -> "News window ($event): US Dollar $p% ${m.label} (dollar up = headwind for gold)"
            Signal.BUY -> "News window ($event): US Dollar $p% ${m.label} (dollar down = support for gold)"
            Signal.WAIT -> "News window ($event): US Dollar $p% ${m.label}, too small to vote"
        }
        return MacroMarketIndex("DXY", "US Dollar Index", last, pct, sig, why)
    }

    fun yield10(bars: List<Pair<Long, Double>>, eventMs: Long, now: Long, event: String): MacroMarketIndex? {
        val m = move(bars, eventMs, now) ?: return null
        val last = bars.first { it.first == m.toMs }.second
        val base = bars.first { it.first == m.fromMs }.second
        val bp = m.change * 100.0                 // ^TNX is quoted in percent: 0.01 = 1 bp
        val sig = when { bp >= YIELD_MIN_BP -> Signal.SELL; bp <= -YIELD_MIN_BP -> Signal.BUY; else -> Signal.WAIT }
        val b = String.format(Locale.US, "%+.1f", bp)
        val why = when (sig) {
            Signal.SELL -> "News window ($event): 10Y yield $b bp ${m.label} (yields up = headwind for gold)"
            Signal.BUY -> "News window ($event): 10Y yield $b bp ${m.label} (yields down = support for gold)"
            Signal.WAIT -> "News window ($event): 10Y yield $b bp ${m.label}, too small to vote"
        }
        return MacroMarketIndex("^TNX", "US 10-Yr Yield", last, m.change / base * 100.0, sig, why)
    }
}
