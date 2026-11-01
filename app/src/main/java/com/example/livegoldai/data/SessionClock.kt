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

    /**
     * V34.5: full-day spot-gold closures (UTC calendar day): Christmas (25 Dec), New Year (1 Jan) and Good Friday.
     * Only days when the market does not trade at all; shortened sessions (e.g. US Thanksgiving) are NOT modelled.
     * Display / freshness only: the ledger's own weekend rule (PredictionLedger.isMarketClosed) is unchanged.
     */
    fun holidayName(now: Long): String? {
        val d = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate()
        return when {
            d.monthValue == 12 && d.dayOfMonth == 25 -> "Christmas"
            d.monthValue == 1 && d.dayOfMonth == 1 -> "New Year"
            d == easterSunday(d.year).minusDays(2) -> "Good Friday"
            else -> null
        }
    }

    /** Gregorian Easter Sunday (anonymous Gregorian algorithm). */
    fun easterSunday(y: Int): java.time.LocalDate {
        val a = y % 19; val b = y / 100; val c = y % 100; val d = b / 4; val e = b % 4; val f = (b + 8) / 25; val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30; val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451; val month = (h + l - 7 * m + 114) / 31; val day = ((h + l - 7 * m + 114) % 31) + 1
        return java.time.LocalDate.of(y, month, day)
    }

    /** weekend (ledger rule) or a full-day holiday */
    fun marketClosed(now: Long): Boolean = PredictionLedger.isMarketClosed(now) || holidayName(now) != null

    fun isOpen(d: Def, now: Long): Boolean {
        if (marketClosed(now)) return false
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
