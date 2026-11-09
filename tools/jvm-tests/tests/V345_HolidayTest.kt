import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean) { if (ok) pass++ else { fail++; println("FAIL: $name") } }

fun main() {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    fun at(s: String) = sdf.parse(s)!!.time
    // Easter dates (known): 2026-04-05, 2027-03-28, 2028-04-16, 2024-03-31, 2019-04-21
    check("Easter 2026", SessionClock.easterSunday(2026).toString() == "2026-04-05")
    check("Easter 2027", SessionClock.easterSunday(2027).toString() == "2027-03-28")
    check("Easter 2028", SessionClock.easterSunday(2028).toString() == "2028-04-16")
    check("Easter 2024", SessionClock.easterSunday(2024).toString() == "2024-03-31")
    check("Easter 2019", SessionClock.easterSunday(2019).toString() == "2019-04-21")
    check("Christmas 2026 (Friday) closed", SessionClock.holidayName(at("2026-12-25 14:00:00")) == "Christmas" && SessionClock.marketClosed(at("2026-12-25 14:00:00")))
    check("New Year 2027 (Friday) closed", SessionClock.holidayName(at("2027-01-01 10:00:00")) == "New Year")
    check("Good Friday 2027 (26 Mar) closed", SessionClock.holidayName(at("2027-03-26 12:00:00")) == "Good Friday")
    check("normal Wednesday open", SessionClock.holidayName(at("2026-10-28 10:00:00")) == null && !SessionClock.marketClosed(at("2026-10-28 10:00:00")))
    check("24 Dec not a full closure", SessionClock.holidayName(at("2026-12-24 12:00:00")) == null)
    check("Christmas: all sessions closed", SessionClock.sessions(at("2026-12-25 14:00:00")).none { it.isOpen || it.isGoldenOverlap })
    // freshness says MARKET CLOSED on the holiday even with a fresh download
    val now = at("2026-12-25 14:00:10"); val iv = 300_000L
    val candles = (0 until 150).map { i -> val o = 4100.0 - i * 0.2; CandleBar(sdf.format(Date((now / iv) * iv - (149 - i) * iv)), o, o + 0.6, o - 0.6, o - 0.2) }
    val a = TechnicalEngine.analyze(candles, "5m").copy(feed = FeedStatus("Twelve Data XAU/USD", now - 2_000, 300, true, true, true, 0, 1), sourceState = SourceStateMachine.PRIMARY_ACTIVE)
    check("holiday -> MARKET CLOSED", Freshness.assess(a, now) == Freshness.CLOSED)
    check("holiday -> plan NO_DATA (no ACTIVE)", PlanStamp.stamp(a, LedgerState(emptyList(), emptyMap(), emptyList()), now, "LIVE QUALIFIED").tradeSetup.planStatus == PlanStamp.NO_DATA)
    // the ledger's own rule is unchanged
    check("ledger rule untouched", !PredictionLedger.isMarketClosed(at("2026-12-25 14:00:00")) &&
        File("app/src/main/java/com/example/livegoldai/data/PredictionLedger.kt").readText().contains("Calendar.FRIDAY -> hour >= 21"))
    println("V34.5 holidays: $pass passed, $fail failed")
    if (fail > 0) System.exit(1)
}
