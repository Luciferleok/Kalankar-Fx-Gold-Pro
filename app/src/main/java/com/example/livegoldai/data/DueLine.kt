package com.example.livegoldai.data

/**
 * V33.1R.3: "Next result due" names only a result that is still in the future.
 *
 * Before: the earliest expiry among ALL unchecked records was shown, including records already past their time and
 * waiting for prices (weekend, provider gap). The screen then showed an old time with "(now)" for hours. Now the next
 * future result and the records waiting for a check are reported separately. Display only: nothing in the ledger changes.
 */
object DueLine {
    data class Due(val nextAt: Long?, val nextInterval: String?, val waiting: Int, val oldestWaitingAt: Long?)

    /** [pendingExpiries] = (expiresAt, interval) of unchecked records. A record is waiting once expiresAt + settle has passed. */
    fun of(pendingExpiries: List<Pair<Long, String>>, now: Long, settleMs: Long = LearningCoordinator.SETTLE_MS): Due {
        val future = pendingExpiries.filter { it.first + settleMs > now }.minByOrNull { it.first }
        val waiting = pendingExpiries.filter { it.first + settleMs <= now }
        return Due(future?.first, future?.second, waiting.size, waiting.minOfOrNull { it.first })
    }

    private fun span(ms: Long): String = when {
        ms < 3_600_000 -> "${ms / 60_000 + 1}m"
        ms < 86_400_000 -> "${ms / 3_600_000}h ${(ms % 3_600_000) / 60_000}m"
        else -> "${ms / 86_400_000}d ${(ms % 86_400_000) / 3_600_000}h"
    }

    /** "30 Oct 14:47 UTC (in 12m) • 15m · 3 waiting for check (oldest 30 Oct 09:15 UTC, 5h 20m ago)" */
    fun label(d: Due, now: Long, settleMs: Long = LearningCoordinator.SETTLE_MS, withDay: Boolean = true): String {
        val next = if (d.nextAt == null) "No upcoming result" else
            "${PredictionLedger.utcLabel(d.nextAt, withDay)} (in ${span(maxOf(0L, d.nextAt + settleMs - now))})" + (d.nextInterval?.let { " • $it" } ?: "")
        val wait = if (d.waiting == 0 || d.oldestWaitingAt == null) "" else
            " · ${d.waiting} waiting for check (oldest ${PredictionLedger.utcLabel(d.oldestWaitingAt, withDay)}, ${span(now - d.oldestWaitingAt)} ago)"
        return next + wait
    }
}
