import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }

val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
fun ms(s: String) = sdf.parse(s)!!.time
// ForexFactory style: New York local time with offset
fun ev(title: String, iso: String, impact: String = "High", country: String = "USD") = EconomicEvent(title = title, country = country, date = "", time = "", impact = impact, isoTime = iso)
fun macro(sig: Signal, id: String = "") = MacroMarketIndex("DXY", "US Dollar Index", 100.0, if (sig == Signal.BUY) -0.5 else 0.5, sig, "test", MacroEvidence(EvidenceStatus.OBSERVED_COMPLETE, "YAHOO", "DX-Y.NYB", "DIRECT", "index points", eventId = id, voteEligible = true))

fun main() {
    // NFP 13:30 UTC = 08:30-05:00 (NY winter)
    val nfp = ev("Non-Farm Employment Change", "2026-11-06T08:30:00-05:00")
    val t = ms("2026-11-06 13:30:00")
    // 1. window edges: 60 min before .. 30 min after
    check("61 min before: OFF", !MacroWindow.isActive(listOf(nfp), t - 61 * 60_000L))
    check("60 min before: ON", MacroWindow.isActive(listOf(nfp), t - 60 * 60_000L))
    check("at release: ON", MacroWindow.isActive(listOf(nfp), t))
    check("30 min after: ON", MacroWindow.isActive(listOf(nfp), t + 30 * 60_000L))
    check("31 min after: OFF", !MacroWindow.isActive(listOf(nfp), t + 31 * 60_000L))
    check("medium impact: OFF", !MacroWindow.isActive(listOf(ev("x", "2026-11-06T08:30:00-05:00", "Medium")), t))
    check("non-USD event: OFF", !MacroWindow.isActive(listOf(ev("x", "2026-11-06T08:30:00-05:00", country = "EUR")), t))
    check("no calendar: OFF", !MacroWindow.isActive(emptyList(), t))
    check("bad time string: OFF", !MacroWindow.isActive(listOf(ev("x", "Tentative")), t))
    check("weights: 15.5 with Macro, 13.0 without", MacroWindow.weights(true).values.sum() == 15.5 && MacroWindow.weights(false).values.sum() == 13.0 && "macro" !in MacroWindow.weights(false))

    // 2. engine: same candles, Macro BUY, outside vs inside the window
    val base = ms("2026-11-06 10:00:00")
    val candles = (0 until 150).map { k -> val o = 4000.0 + k * 0.4 + (if (k % 3 == 0) -0.3 else 0.2); CandleBar(sdf.format(Date(base + k * 300_000L)), o, o + 0.6, o - 0.5, o + 0.35) }
    val off = TechnicalEngine.analyze(candles, "5m", macro(Signal.BUY, MacroWindow.eventId(nfp)), macro(Signal.BUY, MacroWindow.eventId(nfp)), listOf(nfp), nowMs = t - 3 * 3_600_000L)
    val on = TechnicalEngine.analyze(candles, "5m", macro(Signal.BUY, MacroWindow.eventId(nfp)), macro(Signal.BUY, MacroWindow.eventId(nfp)), listOf(nfp), nowMs = t - 30 * 60_000L)
    val gOff = off.groups.first { it.key == "macro" }; val gOn = on.groups.first { it.key == "macro" }
    check("outside window: Macro inactive, verdict WAIT, raw vote kept", !gOff.active && gOff.verdict == Signal.WAIT && gOff.rawVerdict == Signal.BUY)
    check("inside window: Macro active and votes BUY", gOn.active && gOn.verdict == Signal.BUY)
    check("outside window: 6 pillars counted", off.totalGroups == 6, "${off.totalGroups}")
    check("inside window: 7 pillars counted", on.totalGroups == 7, "${on.totalGroups}")
    // agreement outside the window must equal the 6-pillar weighted share computed by hand
    val six = off.groups.filter { it.active }
    val w = TechnicalEngine.PILLAR_WEIGHTS
    val buyPts = six.filter { it.verdict == Signal.BUY }.sumOf { w[it.key]!! }; val sellPts = six.filter { it.verdict == Signal.SELL }.sumOf { w[it.key]!! }
    val expect = maxOf(buyPts, sellPts) / 13.0 * 100
    if (off.overallSignal != Signal.WAIT && maxOf(buyPts, sellPts) / 13.0 * 100 >= 68.0)
        check("outside window: agreement = 6-pillar weighted share (/13.0)", kotlin.math.abs(off.agreementPercent - expect) < 1e-6, "${off.agreementPercent} vs $expect")
    else check("outside window: decision follows the 6-pillar gate", off.overallSignal == Signal.WAIT || off.buyCount >= 5 || off.sellCount >= 5)
    println("outside: ${off.overallSignal} ${off.agreementPercent} • inside: ${on.overallSignal} ${on.agreementPercent}")
    // Macro cannot change the decision outside the window: flipping its input gives the same forecast
    val offSell = TechnicalEngine.analyze(candles, "5m", macro(Signal.SELL, MacroWindow.eventId(nfp)), macro(Signal.SELL, MacroWindow.eventId(nfp)), listOf(nfp), nowMs = t - 3 * 3_600_000L)
    check("outside window: Macro input has NO effect on signal or agreement", offSell.overallSignal == off.overallSignal && offSell.agreementPercent == off.agreementPercent)
    val onSell = TechnicalEngine.analyze(candles, "5m", macro(Signal.SELL, MacroWindow.eventId(nfp)), macro(Signal.SELL, MacroWindow.eventId(nfp)), listOf(nfp), nowMs = t - 30 * 60_000L)
    check("inside window: Macro input does count", onSell.agreementPercent != on.agreementPercent || onSell.overallSignal != on.overallSignal)
    val noCal = TechnicalEngine.analyze(candles, "5m", macro(Signal.BUY, MacroWindow.eventId(nfp)), macro(Signal.BUY, MacroWindow.eventId(nfp)), emptyList(), nowMs = t)
    check("no calendar at release time: Macro OFF", !noCal.groups.first { it.key == "macro" }.active)
    // screens still get only upcoming events
    val week = listOf(ev("old", "2026-11-02T08:30:00-05:00"), nfp)
    val shown = TechnicalEngine.analyze(candles, "5m", null, null, week, nowMs = t - 3 * 3_600_000L).macroRadar?.upcomingEvents ?: emptyList()
    check("screen list drops events released more than 1 h ago", shown.map { it.title } == listOf("Non-Farm Employment Change"), "${shown.map { it.title }}")

    // 3. cockpit contributions: Macro weight 0 outside the window
    val ins = HealthMonitor::class.java   // compile reference only
    val src = File("app/src/main/java/com/example/livegoldai")
    check("engine version E22.1", PredictionLedger.ENGINE_VERSION == "E22.1")
    val lc = File(src, "data/LearningCoordinator.kt").readText()
    check("ledger: inactive pillar not written as a vote; raw Macro kept as ctx:macro", lc.contains("if (g.active) sources[\"grp:\${g.key}\"]") && lc.contains("sources[\"ctx:macro\"]"))
    val hm = File(src, "data/HealthMonitor.kt").readText()
    check("cockpit contributions use the active weights", hm.contains("MacroWindow.weights(") && hm.contains("if (g.active)"))
    val api = File(src, "data/GoldApiService.kt").readText()
    check("calendar cached (1 h) and shared", api.contains("calendarCache") && api.contains("CALENDAR_TTL_MS = 60 * 60_000L") && api.contains("synchronized(calendarLock)"))

    // 4. Pillar check after the change
    fun rec(i: Int, src: Map<String, Signal>) = LedgerRecord(id = "r$i", createdAt = i * 3_600_000L, expiresAt = i * 3_600_000L + 3_600_000L, interval = "1h", horizonMin = 60,
        barId = "b$i", price = 4000.0, rawSignal = Signal.BUY, finalSignal = Signal.BUY, confidence = 70, atr = 1.0, slDistance = 3.0, regime = "RANGE", session = "LONDON",
        newsActive = false, modelVersion = "", appliedFilter = "", sources = src)
    fun res(id: String, mv: Double) = LedgerResult(id, 1L, LedgerOutcome.CORRECT, "x", 0.0, 0.0, mv, 1.0, 0.0, 0.0, 0L, 0.0, emptyList(), emptyMap(), emptyList(), "", emptyList())
    val recs = ArrayList<LedgerRecord>(); val out = HashMap<String, LedgerResult>()
    repeat(10) { i -> val r = rec(i, mapOf("grp:macro" to Signal.BUY, "grp:trend" to Signal.BUY)); recs += r; out[r.id] = res(r.id, -3.0) }          // old rule
    repeat(10) { i -> val r = rec(100 + i, mapOf("grp:macro" to Signal.BUY, "ctx:macro" to Signal.BUY, "grp:trend" to Signal.BUY)); recs += r; out[r.id] = res(r.id, 3.0) } // window
    repeat(10) { i -> val r = rec(200 + i, mapOf("ctx:macro" to Signal.SELL, "grp:trend" to Signal.BUY)); recs += r; out[r.id] = res(r.id, 3.0) }  // outside, not used
    val lines = PillarCheck.summary(LedgerState(recs, out, emptyList()))
    println(lines.filter { it.startsWith("Macro") }.joinToString("\n"))
    check("old-rule Macro line counts only old records (0 of 10 right)", lines.any { it.startsWith("Macro & News (old rule, always on): own vote 0% right of 10") })
    check("news-window Macro line (10 of 10 right)", lines.any { it.startsWith("Macro & News (news window, used): own vote 100% right of 10") })
    check("outside-window raw Macro line (not used)", lines.any { it.startsWith("Macro & News (outside window, NOT used): own vote 0% right of 10") })
    check("gate check: a record without Macro uses the 6-pillar total", PillarCheck.survivesWithout(mapOf("trend" to Signal.BUY, "smc" to Signal.BUY, "sr" to Signal.BUY, "momentum" to Signal.BUY, "volatility" to Signal.WAIT, "candlestick" to Signal.WAIT), Signal.BUY, "candlestick"))
    println("V35.0 macro window: $pass passed, $fail failed")
    if (fail > 0) kotlin.system.exitProcess(1)
}
