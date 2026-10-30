package com.example.livegoldai.data

import com.example.livegoldai.model.MarketSession
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * V33.1R.2: trading-session hours from each city's own clock, so summer/winter time changes are followed.
 *
 * Before: fixed UTC hours (London 08-17, New York 13-22, Tokyo 00-09, Sydney 22-07). Those are right for only part of
 * the year: London moves to 07-16 UTC in summer, New York to 12-21 UTC (US summer time; ends 1 Nov 2026), Sydney to
 * 23-08 UTC in its winter. The local hours are the same ones the old UTC values gave in each city's standard time.
 * Weekend closure uses the ledger's own rule (PredictionLedger.isMarketClosed), which is not changed here.
 */
object SessionClock {

    data class Def(val name: String, val city: String, val zone: String, val open: LocalTime, val close: LocalTime, val vol: String)

    val DEFS = listOf(
        Def("London", "London (LSE)", "Europe/London", LocalTime.of(8, 0), LocalTime.of(17, 0), "High"),
        Def("New York", "New York (COMEX)", "America/New_York", LocalTime.of(8, 0), LocalTime.of(17, 0), "High"),
        Def("Tokyo", "Tokyo (Tocom)", "Asia/Tokyo", LocalTime.of(9, 0), LocalTime.of(18, 0), "Moderate"),
        Def("Sydney", "Sydney (ASX)", "Australia/Sydney", LocalTime.of(9, 0), LocalTime.of(18, 0), "Normal")
    )

    private val HM = DateTimeFormatter.ofPattern("HH:mm")

    /** Session window that contains [now], or the one that starts today in that city, as UTC instants. */
    fun window(d: Def, now: Long): Pair<Long, Long> {
        val zone = ZoneId.of(d.zone)
        val localDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start = ZonedDateTime.of(localDate, d.open, zone).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(localDate, d.close, zone).toInstant().toEpochMilli()
        return start to end
    }

    fun isOpen(d: Def, now: Long): Boolean {
        if (PredictionLedger.isMarketClosed(now)) return false
        val (s, e) = window(d, now)
        return now in s until e
    }

    fun utcLabel(d: Def, now: Long): String {
        val (s, e) = window(d, now)
        val f = { ms: Long -> Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).format(HM) }
        return "${f(s)} - ${f(e)} UTC"
    }

    fun sessions(now: Long): List<MarketSession> {
        val london = DEFS[0]; val ny = DEFS[1]
        val overlap = isOpen(london, now) && isOpen(ny, now)
        return DEFS.map { d ->
            val inOverlap = overlap && (d === london || d === ny)
            MarketSession(
                name = d.name, city = d.city, timeWindowUtc = utcLabel(d, now), isOpen = isOpen(d, now),
                volatilityLevel = if (inOverlap) "Extreme (Overlap)" else d.vol, isGoldenOverlap = inOverlap
            )
        }
    }
}
