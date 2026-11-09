import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }

fun main() {
    val api = GoldApiService()
    val m = GoldApiService::class.java.getDeclaredMethod("fetchLiveEconomicEvents").apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST") val first = m.invoke(api) as List<EconomicEvent>
    println("issue: ${api.calendarIssue}")
    check("failed download -> only the official schedule, no invented events (V35.2)", first == OfficialCalendar.merge(emptyList()))
    check("issue names both hosts and the reason", api.calendarIssue.contains("nfs.faireconomy.media:") && api.calendarIssue.contains("cdn-nfs.faireconomy.media:") && api.calendarIssue.contains("IOException"))
    check("issue says when and that it retries", api.calendarIssue.contains("UTC, retry in 5 min"))
    val issue1 = api.calendarIssue
    Thread.sleep(1100)
    m.invoke(api)
    check("no new download within 5 min of a failure (same issue text)", api.calendarIssue == issue1)
    check("two hosts configured", GoldApiService.CALENDAR_URLS.size == 2 && GoldApiService.CALENDAR_URLS.all { it.endsWith("/ff_calendar_thisweek.json") })
    check("FeedStatus carries the issue (default empty)", FeedStatus("x", 0, 0, true, true, true, 0, 1).calendarIssue == "")
    val hm = File("app/src/main/java/com/example/livegoldai/data/HealthMonitor.kt").readText()
    check("SYSTEM shows the reason", hm.contains("\"Calendar not loaded\" + (feed?.calendarIssue"))
    println("V35.1 calendar: $pass passed, $fail failed")
    if (fail > 0) kotlin.system.exitProcess(1)
}
