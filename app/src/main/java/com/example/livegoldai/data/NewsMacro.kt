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
    const val MAX_AGE_MS = 20 * 60_000L

    data class Move(val change: Double, val fromMs: Long, val toMs: Long, val label: String)

    /** [bars] = (time ms, close), any order, nulls removed. */
    fun move(bars: List<Pair<Long, Double>>, eventMs: Long, now: Long): Move? {
        val b = bars.filter { it.first <= now && it.second.isFinite() && it.second > 0 }.sortedBy { it.first }
        val cur = b.lastOrNull() ?: return null
        if (now - cur.first > MAX_AGE_MS) return null
        val (baseT, label) = if (now < eventMs) (now - 60 * 60_000L) to "last 60 min" else eventMs to "since the release"
        val base = b.lastOrNull { it.first <= baseT } ?: return null
        if (base.first >= cur.first) return null
        return Move(cur.second - base.second, base.first, cur.first, label)
    }

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
