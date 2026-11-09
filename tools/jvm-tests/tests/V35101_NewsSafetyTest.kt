import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// V35.10.1 news safety on the NFP 6 Nov 2026 timeline (19:00 IST = 13:30 UTC). SYNTHETIC DETERMINISTIC FIXTURES.
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name :: $d") } }
const val M = 60_000L
val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
val T = sdf.parse("2026-11-06 13:30:00")!!.time
val nfp = EconomicEvent("Non-Farm Employment Change", "USD", "", "", "High", isoTime = "2026-11-06T08:30:00-05:00")   // EST (US DST ended 1 Nov)
val ist = TimeZone.getTimeZone("Asia/Kolkata")
val empty = LedgerState(emptyList(), emptyMap(), emptyList())
val svcM = GoldApiService::class.java.declaredMethods.first { it.name.startsWith("applyNewsMode") && it.parameterCount == 3 }.apply { isAccessible = true }
fun news(a: GoldAnalysisResult, ev: List<EconomicEvent>, now: Long) = svcM.invoke(GoldApiService(), a, ev, now) as GoldAnalysisResult
lateinit var base0: GoldAnalysisResult
/** 5m candles up to the one forming at [now]; closes flat at 4000 before the release, then [after] for bars that open at/after T,
 *  the bar forming at [now] gets [forming] (must never be used); [from] drops bars that opened before it (late start) */
fun candles(now: Long, after: Double, forming: Double = 4100.0, from: Long = 0L, dropOpen: Long = -1L): List<CandleBar> {
    val iv = 5 * M; val cur = (now / iv) * iv
    return (0 until 150).map { k -> cur - (149 - k) * iv }.filter { it >= from && it != dropOpen }.map { o ->
        val c = when { o == cur -> forming; o >= T -> after; else -> 4000.0 }
        CandleBar(sdf.format(Date(o)), 4000.0, maxOf(4000.0, c) + .5, minOf(4000.0, c) - .5, c) }
}
fun a(sig: Signal, now: Long, cs: List<CandleBar>) = base0.copy(overallSignal = sig, rawSignal = null, interval = "5m", chartCandles = cs, recentCandles = cs,
    currentPrice = cs.last().close, lastUpdated = cs.last().datetime, feed = FeedStatus("Twelve Data XAU/USD", now - 5_000, 300, true, true, true, 0, 1))
fun ist(ms: Long) = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = ist }.format(Date(ms))

fun main() {
    base0 = TechnicalEngine.analyze(candles(T - 3 * 60 * M, 0.0), "5m")
    val ev = listOf(nfp)
    fun run(label: String, sig: Signal, now: Long, after: Double, expFinal: Signal, expHold: String?, forming: Double = 4100.0, from: Long = 0L, dropOpen: Long = -1L): GoldAnalysisResult {
        val r = news(a(sig, now, candles(now, after, forming, from, dropOpen)), ev, now)
        check("$label (${ist(now)} IST) pillar $sig -> $expFinal hold=${expHold ?: "no news mode"}",
            r.overallSignal == expFinal && (if (expHold == null) r.newsMode == null else r.newsMode?.hold == expHold),
            "final=${r.overallSignal} hold=${r.newsMode?.hold} reaction=${r.newsMode?.reaction} phase=${r.newsMode?.phase}")
        return r
    }
    // ---- NFP timeline (pillar BUY)
    run("15:00 NEWS AHEAD: outside the window", Signal.BUY, T - 4 * 60 * M, 0.0, Signal.BUY, null)
    run("18:00 Macro PRE opens", Signal.BUY, T - 60 * M, 0.0, Signal.BUY, "")
    run("18:44 PRE, 16 min ahead", Signal.BUY, T - 16 * M, 0.0, Signal.BUY, "")
    val frz = run("18:46 PRE, 14 min ahead: spike freeze", Signal.BUY, T - 14 * M, 0.0, Signal.WAIT, "SPIKE_FREEZE")
    check("held BUY kept as rawSignal", frz.rawSignal == Signal.BUY)
    run("19:00 release", Signal.BUY, T, 4010.0, Signal.WAIT, "FIRST_BAR")
    run("19:04 first bar still forming", Signal.BUY, T + 4 * M, 4010.0, Signal.WAIT, "FIRST_BAR")
    run("19:05:30 first bar complete, +10 up: agrees", Signal.BUY, T + 5 * M + 30_000, 4010.0, Signal.BUY, "")
    run("19:07 reaction -10 down: against BUY", Signal.BUY, T + 7 * M, 3990.0, Signal.WAIT, "AGAINST")
    run("19:07 reaction +2: too small", Signal.BUY, T + 7 * M, 4002.0, Signal.WAIT, "FLAT")
    run("19:07 provider missing the 19:00 bar", Signal.BUY, T + 7 * M, 4010.0, Signal.WAIT, "FIRST_BAR", dropOpen = T)
    run("19:10 app started after release (no pre-release candle)", Signal.BUY, T + 10 * M, 4010.0, Signal.WAIT, "NO_BASELINE", from = T)
    run("19:29 still in window", Signal.BUY, T + 29 * M, 4010.0, Signal.BUY, "")
    run("19:31 Macro OFF: news mode off", Signal.BUY, T + 31 * M, 3990.0, Signal.BUY, null)
    run("19:40 old legacy window: nothing", Signal.WAIT, T + 40 * M, 3990.0, Signal.WAIT, null)
    // ---- never creates or flips
    run("pillar WAIT + reaction up: stays WAIT", Signal.WAIT, T + 7 * M, 4010.0, Signal.WAIT, "")
    run("pillar SELL + reaction up: WAIT, never BUY", Signal.SELL, T + 7 * M, 4010.0, Signal.WAIT, "AGAINST")
    run("pillar SELL + reaction down: SELL stands", Signal.SELL, T + 7 * M, 3990.0, Signal.SELL, "")
    // ---- no look-ahead: a huge spike in the bar forming now is ignored
    val la = run("forming-bar spike ignored", Signal.BUY, T + 7 * M, 3990.0, Signal.WAIT, "AGAINST", forming = 4500.0)
    check("reaction measured from completed bars only", la.newsMode?.reaction == "DOWN", "${la.newsMode?.reaction}")
    // ---- research reaction is information only
    val info = news(a(Signal.WAIT, T + 7 * M, candles(T + 7 * M, 4010.0)), ev, T + 7 * M)
    check("reaction UP shown as research newsSignal, final stays WAIT", info.newsMode?.newsSignal == Signal.BUY && info.overallSignal == Signal.WAIT)
    // ---- event filter, overlap, DST, id
    check("high-impact EUR event: no news mode", news(a(Signal.BUY, T + 7 * M, candles(T + 7 * M, 3990.0)), listOf(nfp.copy(country = "EUR", title = "German Ifo")), T + 7 * M).newsMode == null)
    val two = listOf(nfp, nfp.copy(title = "Unemployment Rate"))
    val o1 = news(a(Signal.BUY, T + 7 * M, candles(T + 7 * M, 4010.0)), two, T + 7 * M); val o2 = news(a(Signal.BUY, T + 7 * M, candles(T + 7 * M, 4010.0)), two.reversed(), T + 7 * M)
    check("overlapping events at the same time: one event, one id", o1.newsMode?.eventId?.isNotEmpty() == true && o1.newsMode?.eventId?.endsWith("@$T") == true, "${o1.newsMode?.eventId} / ${o2.newsMode?.eventId}")
    check("DST: 08:30 EST = 19:00 IST", EventTime.parseMs(nfp.isoTime) == T)
    check("event id = MacroWindow id", o1.newsMode?.eventId == MacroWindow.eventId(MacroWindow.activeEvent(two, T + 7 * M)!!))
    // ---- deterministic, no hidden state, no in-memory baseline
    check("no in-memory baseline field", GoldApiService::class.java.declaredFields.none { it.name.contains("newsBaseline") })
    val x = a(Signal.BUY, T + 7 * M, candles(T + 7 * M, 3990.0))
    check("same input -> same output (no state)", news(x, ev, T + 7 * M) == news(x, ev, T + 7 * M))
    // ---- downstream: plan, gate, reason, verdict, ledger
    val held = news(a(Signal.BUY, T + 7 * M, candles(T + 7 * M, 3990.0)), ev, T + 7 * M)
    val st = PlanStamp.stamp(held, empty, T + 7 * M, "COLLECTING")
    check("plan status INACTIVE_WAIT (blocked BUY, scenario only)", st.tradeSetup.planStatus == PlanStamp.INACTIVE_WAIT, st.tradeSetup.planStatus)
    check("execution gate closed", !ResearchGate.executionAllowed(st, T + 7 * M))
    check("wait reason NEWS", RealityEngine.waitReason(held).first == "NEWS", RealityEngine.waitReason(held).toString())
    OneLineVerdict.build(held, T + 7 * M, AppLanguage.ENGLISH).let { check("verdict says why", it.headline == "WAIT: news reaction goes against the pillar BUY" && it.warn, it.headline) }
    val lc = File("app/src/main/java/com/example/livegoldai/data/LearningCoordinator.kt").readText()
    check("ledger records the published decision (raw = analysis.overallSignal)", lc.contains("val rawSig = raw.overallSignal"))
    val gs = File("app/src/main/java/com/example/livegoldai/data/GoldApiService.kt").readText()
    check("no path publishes the news reaction", !gs.contains("overallSignal = status.newsSignal") && gs.contains("overallSignal = c.final"))
    // ---- banner snapshot consistency
    val cal = OfficialCalendar.events()
    fun ta(now: Long, fetched: Long) = TechnicalEngine.analyze(candles(now, 4010.0), "5m", null, null, cal, nowMs = now).copy(feed = FeedStatus("Twelve Data XAU/USD", fetched, 300, true, true, true, 0, 1))
    NewsModeBanner.build(ta(T - 10 * M, T - 10 * M), T + 3 * M, AppLanguage.ENGLISH, ist)!!.let {
        check("old PRE analysis shown after release: REFRESH REQUIRED, no current vote", it.stage == NewsModeBanner.Stage.POST && it.refresh && it.dollar == "REFRESH REQUIRED" && it.macro == "Macro vote not current (analysis phase: PRE). REFRESH REQUIRED", "$it") }
    NewsModeBanner.build(ta(T + 3 * M, T + 3 * M - 5_000), T + 3 * M, AppLanguage.ENGLISH, ist)!!.let { check("fresh POST analysis: no refresh", !it.refresh, "$it") }
    check("fixture: both POST", MacroWindow.phase(cal, T + 6 * M) == MacroWindow.phase(cal, T + 12 * M))
    NewsModeBanner.build(ta(T + 12 * M, T + 6 * M), T + 12 * M, AppLanguage.ENGLISH, ist).let { check("same phase (POST) but 6 min old: refresh", it?.refresh == true, "$it") }
    NewsModeBanner.build(ta(T + 12 * M, T + 8 * M), T + 12 * M, AppLanguage.ENGLISH, ist).let { check("same phase, 4 min old: no refresh", it?.refresh == false, "$it") }
    check("old POST analysis after the window: banner gone", NewsModeBanner.build(ta(T + 20 * M, T + 20 * M), T + 31 * M, AppLanguage.ENGLISH, ist) == null)
    val hb = news(ta(T + 3 * M, T + 3 * M - 5_000).copy(overallSignal = Signal.BUY), cal, T + 3 * M)
    NewsModeBanner.build(hb, T + 3 * M, AppLanguage.ENGLISH, ist)!!.let { check("banner shows the safety line of this analysis", it.safety.startsWith("News safety: the pillar BUY is held as WAIT until the first candle"), it.safety) }
    check("Hindi / Marathi safety text", news(x, ev, T + 7 * M).newsMode!!.detailHindi.startsWith("न्यूज़ सुरक्षा") && news(x, ev, T + 7 * M).newsMode!!.detailMarathi.startsWith("न्यूज सुरक्षा"))
    println("V35.10.1 news safety: $pass passed, $fail failed")
}
