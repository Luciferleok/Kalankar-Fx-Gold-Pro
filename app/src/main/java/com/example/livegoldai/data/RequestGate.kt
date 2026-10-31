package com.example.livegoldai.data

import java.util.concurrent.atomic.AtomicLong

/**
 * V33.1R.4-b: only the newest load may change the screen.
 *
 * Defect (GoldViewModel.loadData at b5baa0f): every call launched an independent coroutine and wrote its result
 * unconditionally. Start 5M, switch to 15M, 15M answers first, 5M answers later -> the 5M analysis replaced the 15M
 * screen (and its loading flag, error, notifications and AI council run).
 *
 * Each load takes a ticket. A result is shown only if its ticket is still the newest one AND the timeframe it was
 * fetched for is still the selected one. Older loads are not cancelled: their ledger work (one record per candle) is
 * allowed to finish, only their screen effects are dropped.
 */
class RequestGate {
    data class Ticket(val generation: Long, val interval: String)

    private val counter = AtomicLong(0)

    fun begin(interval: String): Ticket = Ticket(counter.incrementAndGet(), interval)

    /** true only for the newest ticket whose timeframe is still the selected one */
    fun isCurrent(t: Ticket, selectedInterval: String): Boolean =
        t.generation == counter.get() && t.interval.equals(selectedInterval, ignoreCase = true)

    fun latest(): Long = counter.get()
}
