import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
fun utc(s: String) = sdf.parse(s)!!.time
const val M = 60_000L


// V35.6: the service stamps the window's event id on each reading (GoldApiService.macroIndex); tests do it here
fun stampEv(m: MacroMarketIndex?, events: List<EconomicEvent>, now: Long) =
    m?.let { it.copy(evidence = it.evidence.copy(eventId = MacroWindow.activeEvent(events, now)?.let { e -> MacroWindow.eventId(e) } ?: "")) }

fun main() {
    // 1. official schedule: Eastern time -> UTC with the right summer/winter offset
    val off = OfficialCalendar.events()
    fun at(title: String, day: String) = off.first { it.title.startsWith(title) && it.date == day }.let { EventTime.parseMs(it.isoTime)!! }
    check("NFP 6 Nov 2026 08:30 ET = 13:30 UTC (winter)", at("Non-Farm", "2026-11-06") == utc("2026-11-06 13:30"))
    check("CPI 10 Nov 2026 = 13:30 UTC", at("CPI", "2026-11-10") == utc("2026-11-10 13:30"))
    check("FOMC 9 Dec 2026 14:00 ET = 19:00 UTC", at("FOMC Statement", "2026-12-09") == utc("2026-12-09 19:00"))
    check("FOMC 9 Jun 2027 14:00 ET = 18:00 UTC (summer)", at("FOMC Statement", "2027-06-09") == utc("2027-06-09 18:00"))
    check("CPI 13 Apr 2027 08:30 ET = 12:30 UTC (summer)", at("CPI", "2027-04-13") == utc("2027-04-13 12:30"))
    check("all official events High impact USD", off.all { it.impact == "High" && it.country == "USD" } && off.size == 36 + 18)

    // 2. merge: no duplicates when the live calendar has the same release
    val liveNfp = EconomicEvent(title = "Non-Farm Employment Change", country = "USD", date = "", time = "", impact = "High", isoTime = "2026-11-06T08:30:00-05:00")
    val merged = OfficialCalendar.merge(listOf(liveNfp))
    check("live NFP kept, official NFP of the same time not added", merged.count { (EventTime.parseMs(it.isoTime) ?: 0) == utc("2026-11-06 13:30") } == 1 && merged.contains(liveNfp))
    check("other official events added", merged.size == off.size)
    check("no live calendar -> official list", OfficialCalendar.merge(emptyList()).size == off.size)
    check("window opens from the official schedule alone", MacroWindow.isActive(OfficialCalendar.merge(emptyList()), utc("2026-11-06 12:45")))
    check("...and is closed at 15:00", !MacroWindow.isActive(OfficialCalendar.merge(emptyList()), utc("2026-11-06 15:00")))
    check("FOMC: window covers the whole press conference (until 15:30 ET, V35.3-G)", MacroWindow.isActive(off, utc("2026-12-09 20:25")) && !MacroWindow.isActive(off, utc("2026-12-09 20:31")))

    // 3. news-window votes from the minutes around the release
    val ev = utc("2026-11-06 13:30")
    fun series(from: String, n: Int, f: (Int) -> Double) = (0 until n).map { k -> utc(from) + k * 5 * M to f(k) }
    // dollar flat until the release, then +0.20% within 15 minutes
    val dxy = series("2026-11-06 11:00", 40) { k -> val t = utc("2026-11-06 11:00") + k * 5 * M; if (t <= ev) 100.0 else 100.2 }
    val post = NewsMacro.dxy(dxy, ev, ev + 15 * M, "NFP")!!
    check("after release: dollar +0.20% -> SELL gold", post.impactOnGold == Signal.SELL && post.explanation.contains("since the release"), post.explanation)
    val pre = NewsMacro.dxy(dxy, ev, ev - 30 * M, "NFP")!!
    check("before release: flat last 60 min -> no vote", pre.impactOnGold == Signal.WAIT && pre.explanation.contains("last 60 min"), pre.explanation)
    val down = NewsMacro.dxy(dxy.map { it.first to (if (it.first <= ev) 100.0 else 99.85) }, ev, ev + 10 * M, "NFP")!!
    check("dollar -0.15% -> BUY gold", down.impactOnGold == Signal.BUY)
    check("old price (more than 20 min) -> no vote", NewsMacro.dxy(dxy.filter { it.first <= ev }, ev, ev + 30 * M, "NFP") == null)
    // EUR/USD as dollar proxy: EUR/USD down = dollar up
    val eur = dxy.map { it.first to (if (it.first <= ev) 1.1000 else 1.0970) }
    val viaEur = NewsMacro.dxy(eur.map { it.first to 1.0 / it.second }, ev, ev + 15 * M, "NFP")!!
    check("EUR/USD down 0.27% -> dollar up -> SELL gold", viaEur.impactOnGold == Signal.SELL)
    // 10Y yield in percent: +3 bp -> SELL; -1 bp -> no vote
    val y = series("2026-11-06 11:00", 40) { k -> val t = utc("2026-11-06 11:00") + k * 5 * M; if (t <= ev) 4.50 else 4.53 }
    check("yield +3 bp -> SELL gold", NewsMacro.yield10(y, ev, ev + 15 * M, "NFP")!!.impactOnGold == Signal.SELL)
    check("yield -1 bp -> no vote", NewsMacro.yield10(y.map { it.first to (if (it.first <= ev) 4.50 else 4.49) }, ev, ev + 15 * M, "NFP")!!.impactOnGold == Signal.WAIT)
    // engine: the news-window inputs decide the Macro vote inside the window
    val base = utc("2026-11-06 01:00")
    val candles = (0 until 150).map { k -> val o = 4000.0 + k * 0.4; CandleBar(sdf.format(Date(base + k * 5 * M)) + ":00", o, o + 0.6, o - 0.5, o + 0.35) }
    val a = OfficialCalendar.merge(emptyList()).let { c -> TechnicalEngine.analyze(candles, "5m", stampEv(post, c, ev + 15 * M), stampEv(NewsMacro.yield10(y, ev, ev + 15 * M, "NFP"), c, ev + 15 * M), c, nowMs = ev + 15 * M) }
    val g = a.groups.first { it.key == "macro" }
    check("engine: Macro active in the window and votes SELL from the news move", g.active && g.verdict == Signal.SELL)

    // 4. service: calendar failure -> official schedule in use, reason shown
    val api = GoldApiService()
    val m = GoldApiService::class.java.getDeclaredMethod("fetchLiveEconomicEvents").apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST") val evs = m.invoke(api) as List<EconomicEvent>
    check("Forex Factory down -> official events returned", evs.size == off.size, "${evs.size}")
    check("issue names Forex Factory and the official schedule", api.calendarIssue.startsWith("Forex Factory:") && api.calendarIssue.contains(OfficialCalendar.SOURCE_NOTE), api.calendarIssue)
    val src = File("app/src/main/java/com/example/livegoldai/data")
    check("SYSTEM line for the official-only case", File(src, "HealthMonitor.kt").readText().contains("from the official BLS / Fed schedule only"))
    check("macro inputs wait for the calendar and use the news window", File(src, "GoldApiService.kt").readText().contains("async { macroIndex(true, eventsDeferred.await()) }"))
    println("V35.2 news macro: $pass passed, $fail failed")
    if (fail > 0) kotlin.system.exitProcess(1)
}
