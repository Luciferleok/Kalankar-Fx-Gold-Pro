package com.example.livegoldai.data

import com.example.livegoldai.model.EconomicEvent
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * V35.2: official US release schedule, used when the live Forex Factory calendar cannot be loaded, so the Macro
 * news window still opens on the biggest gold-moving releases.
 *
 * Sources (copied 3 Nov 2026, Eastern Time):
 *  - BLS release calendar (bls.gov/schedule/2026 and /2027): Employment Situation (NFP), CPI, PPI, all 08:30 ET.
 *  - Federal Reserve FOMC calendar (federalreserve.gov/monetarypolicy/fomccalendars.htm): statement 14:00 ET on the
 *    second meeting day, press conference 14:30 ET. 2027 dates are "tentative until confirmed" by the Fed.
 * Only the dates the agencies published are listed: nothing is guessed. After the last date this list adds nothing
 * (then only the live calendar opens the window). Agencies can move a date; the live calendar wins when it loads.
 */
object OfficialCalendar {
    private val NY = ZoneId.of("America/New_York")
    private val ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
    const val SOURCE_NOTE = "official BLS / Federal Reserve schedule"

    private data class E(val title: String, val et: String)   // "yyyy-MM-dd HH:mm" Eastern

    private val LIST = listOf(
        // BLS (08:30 ET)
        E("Non-Farm Employment Change (BLS)", "2026-11-06 08:30"), E("CPI (BLS)", "2026-11-10 08:30"), E("PPI (BLS)", "2026-11-13 08:30"),
        E("Non-Farm Employment Change (BLS)", "2026-12-04 08:30"), E("CPI (BLS)", "2026-12-10 08:30"), E("PPI (BLS)", "2026-12-15 08:30"),
        E("Non-Farm Employment Change (BLS)", "2027-01-08 08:30"), E("CPI (BLS)", "2027-01-13 08:30"), E("PPI (BLS)", "2027-01-14 08:30"),
        E("Non-Farm Employment Change (BLS)", "2027-02-05 08:30"), E("CPI (BLS)", "2027-02-11 08:30"), E("PPI (BLS)", "2027-02-12 08:30"),
        E("Non-Farm Employment Change (BLS)", "2027-03-05 08:30"), E("CPI (BLS)", "2027-03-10 08:30"), E("PPI (BLS)", "2027-03-12 08:30"),
        E("Non-Farm Employment Change (BLS)", "2027-04-02 08:30"), E("CPI (BLS)", "2027-04-13 08:30"), E("PPI (BLS)", "2027-04-14 08:30"),
    ) + listOf("2026-12-09", "2027-01-27", "2027-03-17", "2027-04-28", "2027-06-09", "2027-07-28", "2027-09-15", "2027-10-27", "2027-12-08")
        .flatMap { d -> listOf(E("FOMC Statement (Fed)", "$d 14:00"), E("FOMC Press Conference (Fed)", "$d 14:30")) }

    fun ms(et: String): Long = LocalDateTime.parse(et.replace(' ', 'T')).atZone(NY).toInstant().toEpochMilli()

    /** All published events as calendar entries (impact High, USD), ISO time with the New York offset of that day. */
    fun events(): List<EconomicEvent> = LIST.map { e ->
        val z = LocalDateTime.parse(e.et.replace(' ', 'T')).atZone(NY)
        EconomicEvent(title = e.title, country = "USD", date = e.et.substring(0, 10), time = e.et.substring(11) + " ET", impact = "High",
            isoTime = z.format(ISO), goldImpact = "High Volatility Spike Expected (from the $SOURCE_NOTE)")
    }.sortedBy { ms(it.date + " " + it.time.removeSuffix(" ET")) }

    /**
     * Live events plus every official event that the live list does not already have (no High USD live event within
     * 45 minutes of it). With no live list, the official list alone.
     */
    fun merge(live: List<EconomicEvent>): List<EconomicEvent> {
        val liveHigh = live.filter { it.impact.equals("High", true) }.mapNotNull { EventTime.parseMs(it.isoTime) }
        val extra = events().filter { o -> val t = EventTime.parseMs(o.isoTime) ?: return@filter false; liveHigh.none { kotlin.math.abs(it - t) <= 45 * 60_000L } }
        return (live + extra).sortedBy { EventTime.parseMs(it.isoTime) ?: Long.MAX_VALUE }
    }
}
