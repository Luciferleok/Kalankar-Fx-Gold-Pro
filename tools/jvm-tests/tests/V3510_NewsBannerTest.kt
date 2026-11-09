import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// V35.10 News Mode banner on the NFP 6 Nov 2026 timeline (19:00 IST). SYNTHETIC FIXTURES.
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
const val M = 60_000L
val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
fun main() {
    val ist = TimeZone.getTimeZone("Asia/Kolkata")
    val cal = OfficialCalendar.events()
    val t = sdf.parse("2026-11-06 13:30:00")!!.time
    fun at(now: Long, dxy: MacroMarketIndex? = null): GoldAnalysisResult {
        val candles = (0 until 150).map { k -> val o = 4000.0 + k * 0.4; CandleBar(sdf.format(Date(now - (150 - k) * 5 * M)), o, o + 0.6, o - 0.5, o + 0.35) }
        // V35.10.1: the banner checks the analysis's publication time, so the fixture is published at [now]
        return TechnicalEngine.analyze(candles, "5m", dxy, null, cal, nowMs = now).copy(feed = FeedStatus("Twelve Data XAU/USD", now, 300, true, true, true, 0, 1))
    }
    fun b(now: Long, dxy: MacroMarketIndex? = null) = NewsModeBanner.build(at(now, dxy), now, AppLanguage.ENGLISH, ist)
    check("14:59 IST: no banner", b(t - 4 * 60 * M - M) == null)
    b(t - 2 * 60 * M)!!.let { check("17:00 IST: NEWS AHEAD, opens 18:00 IST in 1h 0m", it.stage == NewsModeBanner.Stage.UPCOMING && it.countdown == "Macro window opens 18:00 IST (in 1h 0m)" && it.dollar == "--" && it.macro.startsWith("Macro OFF"), "$it") }
    b(t - 50 * M)!!.let { check("18:10 IST: PRE, release in 50m, closes 19:30", it.stage == NewsModeBanner.Stage.PRE && it.countdown == "Release in 50m · window closes 19:30 IST" && it.event.startsWith("NFP (") && it.releaseAt == "Fri 06 Nov 19:00 IST", "$it") }
    b(t - 50 * M)!!.let { check("no minute data: no vote, status shown", it.macro.startsWith("Macro no vote") && it.dollar == "UNAVAILABLE", "${it.dollar} ${it.macro}") }
    val obs = MacroMarketIndex("DXY", "d", 100.0, 0.2, Signal.SELL, "x", MacroEvidence(EvidenceStatus.OBSERVED_COMPLETE, "OANDA", "6 OANDA FX pairs", "COMPUTED", "index points", t, t + 5 * M, t + 7 * M, "NFP@$t", true))
    b(t + 7 * M, obs)!!.let { check("19:07 IST: POST, released 7m ago, closes in 23m, Macro votes", it.stage == NewsModeBanner.Stage.POST && it.countdown == "Released 7m ago · window closes in 23m" &&
        it.dollar == "OBSERVED COMPLETE (OANDA COMPUTED)" && it.macro.startsWith("Macro votes"), "$it") }
    check("19:30:01 IST: gone", b(t + 30 * M + 1000) == null)
    check("span rounds up to the minute", NewsModeBanner.span(59_000) == "1m" && NewsModeBanner.span(61 * M) == "1h 1m")
    check("Hindi builds", NewsModeBanner.build(at(t - 50 * M), t - 50 * M, AppLanguage.HINDI, ist)!!.countdown.startsWith("रिलीज़ 50m"))
    val ck = File("app/src/main/java/com/example/livegoldai/ui/v8/V8CockpitScreen.kt").readText()
    check("banner on HOME above the trust card", ck.indexOf("V8NewsBanner(analysis, lang, now)") in 1 until ck.indexOf("V8TrustCard(analysis, lang, now)"))
    println("V35.10 news banner: $pass passed, $fail failed")
}
