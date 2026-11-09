import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
fun u(s: String) = sdf.parse(s)!!.time
const val M = 60_000L

fun main() {
    val e = u("2026-11-06 13:30")
    val start = u("2026-11-06 11:00")
    val px = mapOf("EUR_USD" to 1.08, "USD_JPY" to 150.0, "GBP_USD" to 1.27, "USD_CAD" to 1.36, "USD_SEK" to 10.5, "USD_CHF" to 0.88)
    fun series(k: String, after: Double, upTo: Long = e + 20 * M, completeLast: Boolean = true) = (0 until 60).map { i -> start + i * 5 * M }.filter { it <= upTo }
        .map { t -> CandleAggregator.Bar(t, 0.0, 0.0, 0.0, if (t < e) px.getValue(k) else px.getValue(k) * after, complete = completeLast || t < upTo) }
    // ICE formula value
    val flat = OandaMacro.dxy(px.keys.associateWith { k -> series(k, 1.0) })
    val expect = 50.14348112 * Math.pow(1.08, -0.576) * Math.pow(150.0, 0.136) * Math.pow(1.27, -0.119) * Math.pow(1.36, 0.091) * Math.pow(10.5, 0.042) * Math.pow(0.88, 0.036)
    check("ICE DXY formula from six pairs", flat.isNotEmpty() && kotlin.math.abs(flat.first().second - expect) < 1e-9, "${flat.firstOrNull()?.second} vs $expect")
    println("computed DXY = ${"%.3f".format(expect)}")
    // EUR down 0.3% after the release -> dollar index up -> SELL gold
    val pairs = px.keys.associateWith { k -> series(k, if (k == "EUR_USD") 0.997 else 1.0) }
    val d = OandaMacro.dollarIndex(pairs, e, e + 12 * M, "NFP")
    check("EUR/USD -0.3% -> computed DXY up -> SELL gold", d?.impactOnGold == Signal.SELL && d.explanation.contains("computed from 6 OANDA FX pairs"), "$d")
    check("dollar move size follows the formula (~+0.17%)", kotlin.math.abs((d?.changePercent ?: 0.0) - (Math.pow(0.997, -0.576) - 1) * 100) < 1e-6)
    // a missing pair -> no computed index (no filling)
    check("one pair missing -> no index", OandaMacro.dxy(pairs - "USD_SEK").isEmpty())
    // incomplete candles are never used
    val inc = px.keys.associateWith { k -> series(k, 1.0).map { it.copy(complete = false) } }
    check("incomplete candles excluded", OandaMacro.dxy(inc).isEmpty())
    // timestamps present in only some pairs are dropped
    val gap = pairs.mapValues { (k, v) -> if (k == "USD_JPY") v.filter { it.startMs != e } else v }
    check("bar missing in one pair -> that time is skipped, not filled", OandaMacro.dxy(gap).none { it.first == e } && OandaMacro.dxy(gap).isNotEmpty())
    // T-note: price up = yields down = BUY gold
    val note = (0 until 60).map { i -> start + i * 5 * M }.filter { it <= e + 20 * M }.map { t -> CandleAggregator.Bar(t, 0.0, 0.0, 0.0, if (t < e) 110.0 else 110.25) }
    val n = OandaMacro.note(note, e, e + 12 * M, "NFP")
    check("T-note +0.23% -> yields down -> BUY gold", n?.impactOnGold == Signal.BUY && n.symbol == "USB10Y_USD")
    val n2 = OandaMacro.note(note.map { it.copy(close = if (it.startMs < e) 110.0 else 109.8) }, e, e + 12 * M, "NFP")
    check("T-note -0.18% -> yields up -> SELL gold", n2?.impactOnGold == Signal.SELL)
    // press conference window: 14:30 ET start -> on until 15:30 ET
    val cal = OfficialCalendar.events()
    check("Fed press conference: Macro on at 15:29 ET", MacroWindow.isActive(cal, u("2026-12-09 20:29")))
    check("...and off at 15:31 ET", !MacroWindow.isActive(cal, u("2026-12-09 20:31")))
    check("NFP still 30 min after (off at 09:01 ET)", !MacroWindow.isActive(cal, u("2026-11-06 14:01")) && MacroWindow.isActive(cal, u("2026-11-06 13:59")))
    // news window without data: no vote AND no weight (same decision as without Macro)
    val base = u("2026-11-06 01:00")
    val candles = (0 until 150).map { k -> val o = 4000.0 + k * 0.4; CandleBar(sdf.format(Date(base + k * 5 * M)) + ":00", o, o + 0.6, o - 0.5, o + 0.35) }
    val noData = MacroMarketIndex("DXY", "US Dollar Index", 0.0, 0.0, Signal.WAIT, "News window (NFP): MACRO DATA UNAVAILABLE for the US Dollar (no fresh completed 5-minute price): no vote")
    val win = TechnicalEngine.analyze(candles, "5m", noData, noData.copy(symbol = "^TNX"), cal, nowMs = e + 10 * M)
    val off = TechnicalEngine.analyze(candles, "5m", null, null, emptyList(), nowMs = e + 10 * M)
    val g = win.groups.first { it.key == "macro" }
    check("no data in the window -> Macro inactive (no weight): same agreement as without Macro", !g.active && win.agreementPercent == off.agreementPercent && win.overallSignal == off.overallSignal,
        "${win.agreementPercent} vs ${off.agreementPercent}")
    check("title says NEWS WINDOW + MACRO DATA UNAVAILABLE", g.title.contains("NEWS WINDOW: Non-Farm") && g.title.contains("MACRO DATA UNAVAILABLE"))
    check("SYSTEM line for that state", CockpitInsightsBuilder.macroWindowCheck(win).detail.startsWith("NEWS WINDOW") && CockpitInsightsBuilder.macroWindowCheck(win).status == "WARN")
    // with data the window votes and weighs
    // V35.6: the service stamps the event id on each reading (GoldApiService.macroIndex); here done by hand
    val evId = MacroWindow.activeEvent(cal, e + 12 * M)!!.let { MacroWindow.eventId(it) }
    val withData = TechnicalEngine.analyze(candles, "5m", d?.let { it.copy(evidence = it.evidence.copy(eventId = evId)) }, n?.let { it.copy(evidence = it.evidence.copy(eventId = evId)) }, cal, nowMs = e + 12 * M)
    check("with data: Macro active in the window", withData.groups.first { it.key == "macro" }.active && withData.totalGroups == 7)
    // OANDA URL is a read-only candle GET
    val url = OandaCandles.instrumentUrl("EUR_USD", "M5", 40)
    check("OANDA macro URL = read-only candles on the practice host", url.startsWith("https://api-fxpractice.oanda.com/v3/instruments/EUR_USD/candles?") && !url.contains("order", true) && !url.contains("trade", true))
    val api = File("app/src/main/java/com/example/livegoldai/data/GoldApiService.kt").readText()
    check("service tries OANDA first, Yahoo as fallback", api.indexOf("OandaMacro.dollarIndex") in 1 until api.indexOf("yahooIntraday(\"DX-Y.NYB\").let") &&
        api.indexOf("OandaMacro.note") in 1 until api.indexOf("yahooIntraday(\"%5ETNX\")"))
    println("V35.3-G OANDA macro: $pass passed, $fail failed")
    if (fail > 0) kotlin.system.exitProcess(1)
}
