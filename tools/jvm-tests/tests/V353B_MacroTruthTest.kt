import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
fun u(s: String) = sdf.parse(s)!!.time
const val M = 60_000L
fun ev(title: String, iso: String) = EconomicEvent(title = title, country = "USD", date = "", time = "", impact = "High", isoTime = iso)


// V35.6: the service stamps the window's event id on each reading (GoldApiService.macroIndex); tests do it here
fun stampEv(m: MacroMarketIndex?, events: List<EconomicEvent>, now: Long) =
    m?.let { it.copy(evidence = it.evidence.copy(eventId = MacroWindow.activeEvent(events, now)?.let { e -> MacroWindow.eventId(e) } ?: "")) }

fun main() {
    val e = u("2026-11-06 13:30")
    fun bars(upTo: Long, f: (Long) -> Double) = (0 until 60).map { k -> u("2026-11-06 11:00") + k * 5 * M }.filter { it <= upTo }.map { it to f(it) }
    // post-release reaction measured from the price AT the release (close of the 13:25 bar)
    val step = bars(e + 7 * M) { t -> if (t < e) 100.0 else 100.25 }
    // V35.3-F: completed bars only -> +3 min no move yet, +7 min the 13:30 bar is complete
    check("3 min after release: no completed bar yet -> no vote; 7 min: +0.25% -> SELL", NewsMacro.dxy(step.filter { it.first <= e }, e, e + 3 * M, "NFP") == null && NewsMacro.dxy(step, e, e + 7 * M, "NFP")?.impactOnGold == Signal.SELL)
    val ramp = bars(e + 12 * M) { t -> if (t < e) 100.0 else if (t == e) 100.25 else 100.30 }
    check("12 min after release: full +0.30% measured", kotlin.math.abs((NewsMacro.dxy(ramp, e, e + 12 * M, "NFP")?.changePercent ?: 0.0) - 0.30) < 1e-9)
    // no bar after the release yet (feed late) -> no vote
    check("after release but newest bar is pre-release -> no vote", NewsMacro.dxy(bars(e - 5 * M) { 100.0 }, e, e + 2 * M, "NFP") == null)
    // before the release: price 60 min ago = close of the bar that ENDED 60 min ago
    val pre = bars(e - 10 * M) { t -> if (t + 5 * M <= e - 70 * M) 100.0 else 100.1 }
    val pm = NewsMacro.move(pre, e, e - 10 * M)!!
    check("pre-release base = bar that ended 60 min ago", pm.fromMs + 5 * M <= e - 70 * M && pm.label == "last 60 min", "${(pm.fromMs - e) / M}")
    // future-stamped bar ignored
    val fut = step + ((e + 30 * M) to 999.0)
    check("future-stamped bar ignored", NewsMacro.dxy(fut, e, e + 7 * M, "NFP")?.value == 100.25)
    // stale feed
    check("newest bar older than 20 min -> no vote", NewsMacro.dxy(bars(e - 30 * M) { 100.0 }, e - 60 * M, e - 2 * M, "NFP") == null)
    // yield sign
    val y = bars(e + 7 * M) { t -> if (t < e) 4.50 else 4.47 }
    check("yield -3 bp after release -> BUY gold", NewsMacro.yield10(y, e, e + 7 * M, "NFP")?.impactOnGold == Signal.BUY)

    // calendar: kinds
    check("kinds", OfficialCalendar.kind("FOMC Statement (Fed)") == "FOMC_RATE" && OfficialCalendar.kind("FOMC Press Conference") == "FOMC_PRESS" &&
        OfficialCalendar.kind("Non-Farm Employment Change") == "NFP" && OfficialCalendar.kind("Core CPI m/m") == "CPI" && OfficialCalendar.kind("PPI m/m") == "PPI")
    val liveStmt = listOf(ev("Federal Funds Rate", "2026-12-09T14:00:00-05:00"))
    val m1 = OfficialCalendar.merge(liveStmt)
    check("live FOMC rate replaces the official statement, press conference kept", m1.count { OfficialCalendar.kind(it.title) == "FOMC_RATE" && it.isoTime.startsWith("2026-12-09") } == 1 &&
        m1.any { it.title.startsWith("FOMC Press Conference") && it.date == "2026-12-09" })
    val liveOther = listOf(ev("ISM Services PMI", "2026-11-06T08:00:00-05:00"))
    check("a different live release near NFP does not remove the official NFP", OfficialCalendar.merge(liveOther).any { it.title.startsWith("Non-Farm") && it.date == "2026-11-06" })
    val liveNfp = listOf(ev("Non-Farm Employment Change", "2026-11-06T08:30:00-05:00"))
    check("same release live + official counted once", OfficialCalendar.merge(liveNfp).count { (EventTime.parseMs(it.isoTime) ?: 0) == e } == 1)

    // service: no minute data in the window -> MACRO DATA UNAVAILABLE, no vote (stub HTTP fails)
    val api = GoldApiService()
    val mi = GoldApiService::class.java.getDeclaredMethod("macroIndex", Boolean::class.javaPrimitiveType, List::class.java).apply { isAccessible = true }
    val now = System.currentTimeMillis()
    val iso = java.time.Instant.ofEpochMilli(now + 10 * M).atOffset(java.time.ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))
    val d = mi.invoke(api, true, listOf(ev("Non-Farm Employment Change", iso))) as MacroMarketIndex?
    check("window active + no minute data -> no vote, says MACRO DATA UNAVAILABLE", d != null && d.impactOnGold == Signal.WAIT && d.explanation.contains("MACRO DATA UNAVAILABLE"), "$d")

    // engine title + SYSTEM states
    val base = u("2026-11-06 01:00")
    val candles = (0 until 150).map { k -> val o = 4000.0 + k * 0.4; CandleBar(sdf.format(Date(base + k * 5 * M)) + ":00", o, o + 0.6, o - 0.5, o + 0.35) }
    val on = OfficialCalendar.merge(emptyList()).let { c -> TechnicalEngine.analyze(candles, "5m", stampEv(NewsMacro.dxy(step, e, e + 7 * M, "NFP"), c, e + 7 * M), null, c, nowMs = e + 7 * M) }
    check("macro title names the event and the schedule source", on.groups.first { it.key == "macro" }.title.let { it.contains("ACTIVE: Non-Farm Employment Change (BLS) [NFP@") && it.contains("], official schedule") })
    val hc = CockpitInsightsBuilder.macroWindowCheck(on)
    println("SYSTEM: ${hc.detail}")
    check("SYSTEM line: active, source, minute data, vote", hc.detail.startsWith("ACTIVE:") && hc.detail.contains("official schedule") && hc.detail.contains("minute data") && hc.detail.contains("Macro votes"))
    val off = TechnicalEngine.analyze(candles, "5m", null, null, emptyList(), nowMs = e)
    check("SYSTEM line when off", CockpitInsightsBuilder.macroWindowCheck(off).detail.startsWith("OFF"))
    println("V35.3-B macro truth: $pass passed, $fail failed")
    if (fail > 0) kotlin.system.exitProcess(1)
}
