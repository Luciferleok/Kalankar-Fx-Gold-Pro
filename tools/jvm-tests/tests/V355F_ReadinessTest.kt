import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// V35.5-F NFP readiness check: pure evaluation on SYNTHETIC inputs (the real check runs on the phone, read-only)
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
fun u(s: String) = sdf.parse(s)!!.time
const val M = 60_000L

fun main() {
    val ist = TimeZone.getTimeZone("Asia/Kolkata")
    val cal = OfficialCalendar.events()
    val now = u("2026-11-04 10:20:00")                       // Wed 15:50 IST
    val fresh = (0 until 40).map { (Math.floorDiv(now, 5 * M) * 5 * M - (40 - it) * 5 * M) to 1.1 }
    val old = fresh.map { (it.first - 30 * M) to it.second }
    val ne = MacroReadiness.nextEvent(cal, now, ist)
    check("next event: NFP Fri 06 Nov 19:00 IST, window 18:00-19:30", ne.startsWith("NFP (") && ne.contains("Fri 06 Nov 19:00 IST") && ne.contains("window 18:00-19:30") && !ne.contains("ACTIVE"), ne)
    val inWin = MacroReadiness.nextEvent(cal, u("2026-11-06 12:40:00"), ist)
    check("18:10 IST: window active now", inWin.contains("WINDOW ACTIVE NOW") && inWin.contains("19:00 IST"), inWin)
    check("after NFP: next event is a later one", !MacroReadiness.nextEvent(cal, u("2026-11-06 14:30:00"), ist).contains("Fri 06 Nov"))
    check("empty calendar said plainly", MacroReadiness.nextEvent(emptyList(), now, ist).startsWith("No high-impact"))

    // single sources
    check("fresh OANDA bars: READY", MacroReadiness.source("OANDA", "EUR_USD", OandaStatus.CONNECTED, fresh, now).state == MacroReadiness.READY)
    MacroReadiness.source("OANDA", "EUR_USD", OandaStatus.CONNECTED, old, now).let { check("30-min-old bars: STALE with age", it.state == MacroReadiness.STALE && it.detail.contains("30m old"), it.detail) }
    MacroReadiness.source("OANDA", "USB10Y_USD", OandaStatus.NOT_FOUND, emptyList(), now).let { check("instrument missing on account: UNAVAILABLE NOT FOUND", it.state == MacroReadiness.UNAVAILABLE && it.detail == "NOT FOUND") }
    check("no token: NOT SET UP", MacroReadiness.source("OANDA", "EUR_USD", OandaStatus.NOT_CONFIGURED, emptyList(), now).state == MacroReadiness.NOT_SET_UP)
    check("forming bar is not counted", MacroReadiness.source("YAHOO", "^TNX", null, listOf(Math.floorDiv(now, 5 * M) * 5 * M to 4.4), now).state == MacroReadiness.UNAVAILABLE)

    // source order = the news window's order
    fun src(p: String, i: String, ok: Boolean) = MacroReadiness.Source(p, i, if (ok) MacroReadiness.READY else MacroReadiness.UNAVAILABLE, "")
    val all = OandaMacro.DXY_PAIRS.keys.map { src("OANDA", it, true) } + src("OANDA", MacroReadiness.DXY_6, true) + src("OANDA", OandaMacro.NOTE, true)   // V35.7: the index needs common bars
    MacroReadiness.evaluate(now, cal, all, ist).let { check("all OANDA: READY, 6-pair index + T-Note", it.verdict == "READY" && it.dollarPath.startsWith("OANDA computed") && it.tenYPath == "OANDA 10Y T-Note price") }
    val noSek = all.map { if (it.instrument == "USD_SEK" || it.instrument == MacroReadiness.DXY_6) it.copy(state = MacroReadiness.UNAVAILABLE) else it }
    check("one pair missing: EUR/USD proxy", MacroReadiness.evaluate(now, cal, noSek, ist).dollarPath == "OANDA EUR/USD proxy (inverted)")
    val noNote = all.map { if (it.instrument == OandaMacro.NOTE) it.copy(state = MacroReadiness.UNAVAILABLE) else it } + src("YAHOO", "^TNX", true)
    MacroReadiness.evaluate(now, cal, noNote, ist).let { check("no T-Note: Yahoo yield, PARTIAL", it.tenYPath.startsWith("Yahoo") && it.verdict == "PARTIAL") }
    check("Yahoo only: PARTIAL", MacroReadiness.evaluate(now, cal, listOf(src("YAHOO", "DX-Y.NYB", true), src("YAHOO", "^TNX", true)), ist).verdict == "PARTIAL")
    check("nothing: NOT READY", MacroReadiness.evaluate(now, cal, emptyList(), ist).let { it.verdict == "NOT READY" && it.dollarPath == "NONE" && it.tenYPath == "NONE" })
    check("note says checked now, not at release", MacroReadiness.evaluate(now, cal, all, ist).note.startsWith("Checked now, not at the release"))

    // read-only: candle GETs and the calendar only
    val svc = File("app/src/main/java/com/example/livegoldai/data/GoldApiService.kt").readText()
    val body = svc.substringAfter("suspend fun macroReadiness()").substringBefore("/** V35.3-G: OANDA M5 candles")
    check("service: only instrumentCandles / yahooIntraday / calendar", body.contains("oandaStore.instrumentCandles(inst, \"M5\", 40)") && body.contains("yahooIntraday(sym)") &&
        !body.contains("order", true) && !body.contains("post(", true) && !body.contains("memoryCache"))
    println("V35.5-F readiness: $pass passed, $fail failed")
}
