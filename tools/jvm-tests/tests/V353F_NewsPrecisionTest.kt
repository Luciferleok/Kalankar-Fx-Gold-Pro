import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
fun u(s: String) = sdf.parse(s)!!.time
const val M = 60_000L


// V35.6: the service stamps the window's event id on each reading (GoldApiService.macroIndex); tests do it here
fun stampEv(m: MacroMarketIndex?, events: List<EconomicEvent>, now: Long) =
    m?.let { it.copy(evidence = it.evidence.copy(eventId = MacroWindow.activeEvent(events, now)?.let { e -> MacroWindow.eventId(e) } ?: "")) }

fun main() {
    val e = u("2026-11-06 13:30")
    fun bars(upTo: Long, f: (Long) -> Double) = (0 until 60).map { k -> u("2026-11-06 11:00") + k * 5 * M }.filter { it <= upTo }.map { it to f(it) }
    val step = bars(e + 12 * M) { t -> if (t < e) 100.0 else if (t == e) 100.25 else 100.30 }
    // completed bars only
    check("+3 min: no completed post-release bar -> no move", NewsMacro.move(step.filter { it.first <= e + 3 * M }, e, e + 3 * M) == null)
    val m7 = NewsMacro.move(step.filter { it.first <= e + 7 * M }, e, e + 7 * M)
    check("+7 min: 13:30 bar completed -> +0.25 measured from the 13:25 close", m7 != null && kotlin.math.abs(m7.change - 0.25) < 1e-9 && m7.fromMs == e - 5 * M)
    val m8 = NewsMacro.move(step, e, e + 8 * M)
    check("+8 min: 13:35 bar still forming -> latest COMPLETED = 13:30 bar (+0.25)", m8 != null && m8.toMs == e && kotlin.math.abs(m8.change - 0.25) < 1e-9)
    check("+12 min: 13:35 bar completed -> +0.30", kotlin.math.abs((NewsMacro.move(step, e, e + 12 * M)?.change ?: 0.0) - 0.30) < 1e-9)
    check("baseline never the 13:30 bar", (m7?.fromMs ?: e) + 5 * M <= e)
    check("missing pre-release baseline -> no move", NewsMacro.move(step.filter { it.first >= e }, e, e + 12 * M) == null)
    check("duplicate timestamp with two values -> untrustworthy, no move", NewsMacro.move(step + (e to 99.0), e, e + 16 * M) == null)
    check("exact duplicate rows are harmless", NewsMacro.move(step + step.last(), e, e + 16 * M) != null)
    check("out-of-order input handled", NewsMacro.move(step.shuffled(Random(3)), e, e + 16 * M)?.change?.let { kotlin.math.abs(it - 0.30) < 1e-9 } == true)
    check("future-stamped bar ignored", NewsMacro.move(step + ((e + 60 * M) to 500.0), e, e + 16 * M)?.change?.let { kotlin.math.abs(it - 0.30) < 1e-9 } == true)
    check("delayed feed (newest completed bar ended > 20 min ago) -> no move", NewsMacro.move(bars(e - 40 * M) { 100.0 }, e, e - 5 * M) == null)
    // proxy identity
    val eur = bars(e + 12 * M) { t -> if (t < e) 1.1000 else 1.0970 }
    val px = NewsMacro.usdProxy(eur, e, e + 12 * M, "NFP")!!
    check("proxy keeps its own identity and the real EUR/USD price", px.symbol == NewsMacro.PROXY_SYMBOL && px.name == NewsMacro.PROXY_NAME && kotlin.math.abs(px.value - 1.0970) < 1e-9)
    check("proxy: EUR/USD down -> dollar up -> SELL gold; text says proxy, not validated", px.impactOnGold == Signal.SELL && px.explanation.contains("USD DIRECTION PROXY — INVERTED EUR/USD") && px.explanation.contains("not separately validated"))
    // overlapping FOMC: deterministic selection, no baseline mixing
    val cal = OfficialCalendar.events()
    fun at(s: String) = MacroWindow.activeEvent(cal, u(s))?.title ?: "none"
    check("13:30 ET (pre-statement): the upcoming statement", at("2026-12-09 18:30").startsWith("FOMC Statement"))
    check("14:16 ET: the released statement (not the press conference 14 min ahead)", at("2026-12-09 19:16").startsWith("FOMC Statement"), at("2026-12-09 19:16"))
    check("14:31 ET: the released press conference", at("2026-12-09 19:31").startsWith("FOMC Press Conference"))
    check("event ids are stable and distinct", MacroWindow.eventId(cal.first { it.title.startsWith("FOMC Statement") && it.date == "2026-12-09" }) != MacroWindow.eventId(cal.first { it.title.startsWith("FOMC Press") && it.date == "2026-12-09" }))
    // calendar coverage
    check("BLS backup extended to Oct 2027 (copied from bls.gov)", cal.any { it.title.startsWith("Non-Farm") && it.date == "2027-10-08" } && cal.any { it.title.startsWith("CPI") && it.date == "2027-08-11" })
    val cov = OfficialCalendar.coverage(u("2026-11-03 06:00"))
    check("coverage text says PARTIAL and its end dates", cov.startsWith("PARTIAL CALENDAR COVERAGE") && cov.contains("15 Oct 2027") && cov.contains("8 Dec 2027") && !cov.contains("ENDED"))
    check("after the last BLS date the text says the list ended", OfficialCalendar.coverage(u("2027-11-01 00:00")).contains("ENDED"))
    // engine shows the proxy as a proxy
    val base = u("2026-11-06 01:00")
    val candles = (0 until 150).map { k -> val o = 4000.0 + k * 0.4; CandleBar(sdf.format(Date(base + k * 5 * M)) + ":00", o, o + 0.6, o - 0.5, o + 0.35) }
    val a = TechnicalEngine.analyze(candles, "5m", stampEv(px, cal, e + 12 * M), null, cal, nowMs = e + 12 * M)
    val ind = a.groups.first { it.key == "macro" }.indicators.first()
    check("engine indicator named as proxy with EUR/USD value", ind.name == NewsMacro.PROXY_NAME && ind.valueDisplay.startsWith("EUR/USD 1.0970"))
    check("SYSTEM line says dollar via proxy", CockpitInsightsBuilder.macroWindowCheck(a).detail.contains("EUR/USD proxy"))
    check("macro title carries the event id", a.groups.first { it.key == "macro" }.title.contains("[NFP@"))
    // Edge Finder: per-group thinning
    fun rec(id: String, iv: String, c: Long, x: Long, src: String) = LedgerRecord(id = id, createdAt = c, expiresAt = x, interval = iv, horizonMin = 0, barId = id, price = 1.0,
        rawSignal = Signal.BUY, finalSignal = Signal.BUY, confidence = 70, atr = 1.0, slDistance = 1.0, regime = "R", session = "S", newsActive = false, modelVersion = "",
        appliedFilter = "", sources = emptyMap(), dataSource = src)
    fun res(id: String) = LedgerResult(id, 1L, LedgerOutcome.CORRECT, "x", 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0L, 0.0, emptyList(), emptyMap(), emptyList(), "", emptyList())
    val rs = (0 until 10).flatMap { k -> listOf(rec("o$k", "5m", k * 60 * M, k * 60 * M + 25 * M, GoldApiService.OANDA_SOURCE), rec("t$k", "15m", k * 60 * M + M, k * 60 * M + 61 * M, "Twelve Data XAU/USD")) }
    val g = EdgeFinder.groups(LedgerState(rs, rs.associate { it.id to res(it.id) }, emptyList()))
    check("each provider keeps its own 10 clean calls", g.filter { it.dimension == "Provider" }.associate { it.value to it.n } == mapOf("OANDA" to 10, "Twelve Data" to 10))
    check("pooled groups still one call per window inside a group", g.first { it.dimension == "Side" }.n == 10)
    // ledger + pillar check keep proxy votes apart
    val src = File("app/src/main/java/com/example/livegoldai/data")
    check("ledger marks proxy votes, Pillar check has a proxy line", File(src, "LearningCoordinator.kt").readText().contains("sources[\"ctx:macroProxy\"]") &&
        File(src, "PillarCheck.kt").readText().contains("dollar via EUR/USD proxy"))
    println("V35.3-F news precision: $pass passed, $fail failed")
    if (fail > 0) kotlin.system.exitProcess(1)
}
