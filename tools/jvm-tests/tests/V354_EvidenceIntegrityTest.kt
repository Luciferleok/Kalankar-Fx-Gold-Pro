import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
fun t(s: String) = sdf.parse(s)!!.time
const val M = 60_000L
val iso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.000000000'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

fun main() {
    // 1. PillarCheck gate == live engine (E22.1, Macro on/off), canonical decision during the news window
    var agree = 0; var n = 0; var canon = 0
    for (seed in 1L..1500L) {
        val r = Random(seed); var p = 4000.0; val base = t("2026-11-02 00:00:00")
        val candles = (0 until 150).map { k -> val o = p; p += r.nextGaussian() * 2 + 0.1; CandleBar(sdf.format(Date(base + k * 5 * M)), o, maxOf(o, p) + r.nextDouble(), minOf(o, p) - r.nextDouble(), p) }
        val on = seed % 2 == 0L
        val mac = MacroMarketIndex("DXY", "d", 100.0, 0.2, if (r.nextBoolean()) Signal.BUY else Signal.SELL, "News window (x): ok", MacroEvidence(EvidenceStatus.OBSERVED_COMPLETE, "YAHOO", "DX-Y.NYB", "DIRECT", "index points", voteEligible = true))
        val a = TechnicalEngine.analyze(candles, "5m", if (on) mac else null, null, if (on) OfficialCalendar.events() else emptyList(), nowMs = if (on) t("2026-11-06 13:10:00") else base + 150 * 5 * M)
        val votes = a.groups.filter { it.active }.associate { it.key to it.verdict }
        for (side in listOf(Signal.BUY, Signal.SELL)) { n++; if (PillarCheck.survivesWithout(votes, side, "none") == (a.overallSignal == side)) agree++ }
        if (a.nextPrediction?.verdict != a.overallSignal) canon++
    }
    check("rebuilt gate == live engine on 3000 decisions (Macro on/off)", agree == n, "$agree/$n")
    check("news window: playbook == canonical decision", canon == 0, "$canon")
    check("gate weights known per engine version (same since V14)", listOf("", "E22.0", "E22.1").all { PillarCheck.gateWeights(it) == TechnicalEngine.PILLAR_WEIGHTS } && PillarCheck.gateWeights("E99.0") == null)
    // unknown future engine: left out of 'needed', still counted in own vote
    fun rec(id: String, c: Long, eng: String, src: Map<String, Signal>) = LedgerRecord(id = id, createdAt = c, expiresAt = c + 60 * M, interval = "1h", horizonMin = 60, barId = id, price = 1.0,
        rawSignal = Signal.BUY, finalSignal = Signal.BUY, confidence = 70, atr = 1.0, slDistance = 1.0, regime = "R", session = "S", newsActive = false, modelVersion = "",
        appliedFilter = "", sources = src, engineVersion = eng)
    fun res(id: String, ok: Boolean) = LedgerResult(id, 1L, LedgerOutcome.CORRECT, "x", 0.0, 0.0, if (ok) 3.0 else -3.0, 1.0, 0.0, 0.0, 0L, 0.0, emptyList(), emptyMap(), emptyList(), "", emptyList())
    // 4 BUY votes incl. trend: trend needed (without it 3 votes, 7/15.5 = 45%)
    val v = mapOf("grp:trend" to Signal.BUY, "grp:smc" to Signal.BUY, "grp:sr" to Signal.BUY, "grp:candlestick" to Signal.BUY, "grp:momentum" to Signal.WAIT, "grp:volatility" to Signal.WAIT, "grp:macro" to Signal.WAIT)
    val recs = (0 until 10).map { rec("a$it", it * 120 * M, "E22.0", v) } + (0 until 10).map { rec("b$it", (20 + it) * 120 * M, "E22.1", v - "grp:macro" + ("ctx:macro" to Signal.WAIT)) } + (0 until 10).map { rec("c$it", (40 + it) * 120 * M, "E99.0", v) }
    val rs = recs.associate { it.id to res(it.id, !it.id.startsWith("b")) }
    val ps = PillarCheck.pillars(LedgerState(recs, rs, emptyList())).first { it.key == "trend" }
    check("own vote counts every version (30 windows)", ps.ownVote.n == 30, "${ps.ownVote.n}")
    check("incremental leaves the unknown engine out (20, not 30)", ps.needed.n == 20, "${ps.needed.n}")
    check("incremental shown per engine version", ps.neededByEngine.contains("E22.0 100% of 10") && ps.neededByEngine.contains("E22.1 0% of 10"), ps.neededByEngine)
    check("summary names 'own vote' and 'incremental' apart", PillarCheck.summary(LedgerState(recs, rs, emptyList())).first { it.startsWith("Trend:") }.let { it.contains("own vote") && it.contains("incremental (calls that needed it)") })
    // 2. T-note display
    val e = t("2026-11-06 13:30:00")
    val note = (0 until 40).map { i -> e - 100 * M + i * 5 * M }.map { s -> CandleAggregator.Bar(s, 0.0, 0.0, 0.0, if (s < e) 110.0 else 110.25) }
    val idx = OandaMacro.note(note, e, e + 12 * M, "NFP")!!
    val candles = (0 until 150).map { k -> val o = 4000.0 + k * 0.4; CandleBar(sdf.format(Date(t("2026-11-06 01:00:00") + k * 5 * M)), o, o + 0.6, o - 0.5, o + 0.35) }
    val ind = TechnicalEngine.analyze(candles, "5m", null, idx, OfficialCalendar.events(), nowMs = e + 12 * M).groups.first { it.key == "macro" }.indicators[1]
    check("T-note shown as price, not yield %", ind.name.startsWith("US 10Y T-Note price") && ind.valueDisplay == "110.25 (price +0.23%)", "${ind.name} ${ind.valueDisplay}")
    val yld = MacroMarketIndex("^TNX", "US 10-Yr Yield", 4.5, 0.4, Signal.WAIT, "x")
    check("real yield still shown in %", TechnicalEngine.analyze(candles, "5m", null, yld, emptyList(), nowMs = e).groups.first { it.key == "macro" }.indicators[1].valueDisplay.startsWith("4.50%"))
    // 3. copy text label
    val card = File("app/src/main/java/com/example/livegoldai/ui/components/TradeSetupCard.kt").readText()
    check("copy text says what the % is (score tag), no 'Confluence: confidencePercent'", !card.contains("Confluence: \${setup.confidencePercent}%") && card.contains("\${scoreTag ?: \"Score\"}: \${scoreValue ?: \"--\"}"))
    // 4. NFP readiness through the real service with a fake OANDA
    val now = System.currentTimeMillis(); val bar0 = Math.floorDiv(now, 5 * M) * 5 * M; val ev = bar0 - 10 * M
    val times = (1..40).map { bar0 - it * 5 * M }.reversed()
    val px = mapOf("EUR_USD" to 1.08, "USD_JPY" to 150.0, "GBP_USD" to 1.27, "USD_CAD" to 1.36, "USD_SEK" to 10.5, "USD_CHF" to 0.88, "USB10Y_USD" to 110.0)
    fun body(inst: String) = "{\"candles\":[" + times.joinToString(",") { s -> val c = if (s < ev) px.getValue(inst) else px.getValue(inst) * (if (inst == "EUR_USD") 0.997 else if (inst == "USB10Y_USD") 0.998 else 1.0)
        "{\"complete\":true,\"time\":\"${iso.format(Date(s))}\",\"mid\":{\"o\":\"$c\",\"h\":\"$c\",\"l\":\"$c\",\"c\":\"$c\"}}" } + "]}"
    val urls = ArrayList<String>()
    fun svc(missing: Set<String>) = GoldApiService().apply { setOandaTokenProvider { "t" }; oandaTransport = OandaTransport { u, _ -> urls.add(u)
        val i = u.substringAfter("/instruments/").substringBefore("/"); if (i in missing || i !in px) OandaHttp(400, "{}") else OandaHttp(200, body(i)) } }
    val evIso = java.time.Instant.ofEpochMilli(ev).atOffset(java.time.ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"))
    val events = listOf(EconomicEvent(title = "Non-Farm Employment Change", country = "USD", date = "", time = "", impact = "High", isoTime = evIso))
    val mi = GoldApiService::class.java.getDeclaredMethod("macroIndex", Boolean::class.javaPrimitiveType, List::class.java).apply { isAccessible = true }
    val s1 = svc(emptySet())
    val d = mi.invoke(s1, true, events) as MacroMarketIndex; val y = mi.invoke(s1, false, events) as MacroMarketIndex
    check("all OANDA inputs: dollar index computed (SELL), T-note price down (SELL)", d.impactOnGold == Signal.SELL && d.explanation.contains("computed from 6 OANDA FX pairs") && y.symbol == OandaMacro.NOTE && y.impactOnGold == Signal.SELL, "${d.explanation} | ${y.explanation}")
    val s2 = svc(setOf("USD_SEK", "USB10Y_USD"))
    val d2 = mi.invoke(s2, true, events) as MacroMarketIndex; val y2 = mi.invoke(s2, false, events) as MacroMarketIndex
    check("one pair missing -> OANDA EUR/USD proxy, labelled", d2.symbol == NewsMacro.PROXY_SYMBOL && d2.explanation.contains("(OANDA, EUR/USD"), d2.explanation)
    check("no T-note and no Yahoo -> 10Y MACRO DATA UNAVAILABLE (never assumed)", y2.impactOnGold == Signal.WAIT && y2.explanation.contains("MACRO DATA UNAVAILABLE"))
    check("OANDA calls are read-only candle GETs on the practice host", urls.isNotEmpty() && urls.all { it.startsWith("https://api-fxpractice.oanda.com/v3/instruments/") && it.contains("/candles?") })
    // replay 6 Nov: window 18:00-19:30 IST
    val cal = OfficialCalendar.merge(emptyList())
    check("6 Nov window: off 17:59 IST, on 18:00 IST, on 19:30 IST, off 19:31 IST",
        !MacroWindow.isActive(cal, t("2026-11-06 12:29:00")) && MacroWindow.isActive(cal, t("2026-11-06 12:30:00")) && MacroWindow.isActive(cal, t("2026-11-06 14:00:00")) && !MacroWindow.isActive(cal, t("2026-11-06 14:01:00")))
    println("V35.4 evidence integrity: $pass passed, $fail failed")
    if (fail > 0) kotlin.system.exitProcess(1)
}
