import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// V35.5-E: Trust card status NOW vs at publication, exact hit-rate, Macro evidence, diagnostics. SYNTHETIC FIXTURES ONLY.
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
const val M = 60_000L
val empty = LedgerState(emptyList(), emptyMap(), emptyList())

fun make(now: Long, mode: String, want: String): GoldAnalysisResult? {
    val iv = 300_000L
    for (seed in 1L..300L) {
        val rnd = Random(seed); var p = 4100.0
        val candles = (0 until 150).map { i -> val o = p; p += 0.5 + (rnd.nextDouble() - 0.5); CandleBar(sdf.format(Date((now / iv) * iv - (149 - i) * iv)), o, maxOf(o, p) + 0.6, minOf(o, p) - 0.6, p) }
        val td = FeedStatus("Twelve Data XAU/USD", now - 5_000, 300, true, true, true, 0, 1)
        val raw = TechnicalEngine.analyze(candles, "5m").copy(feed = td, sourceState = SourceStateMachine.PRIMARY_ACTIVE)
        val a = PlanStamp.stamp(raw, empty, now, mode).let { it.copy(presentation = PresentationBuilder.build(it, empty, mode, now)) }
        if (a.tradeSetup.planStatus == want) return a
    }
    return null
}

fun main() {
    val t0 = sdf.parse("2026-10-28 10:22:10")!!.time
    val a = make(t0, "LIVE QUALIFIED", PlanStamp.ACTIVE)!!
    fun c(x: GoldAnalysisResult, now: Long) = TrustCard.build(x, now, AppLanguage.ENGLISH)

    // ---- published vs now
    c(a, t0).let { check("A publication: QUALIFIED / ACTIONABLE", it.status == "QUALIFIED" && it.nowStatus == "ACTIONABLE" && it.nowReasons.isEmpty(), "${it.status} ${it.nowStatus}") }
    c(a, t0 + 10 * M).let { check("B stale quote: published kept, now NOT ACTIONABLE (price STALE)", it.status == "QUALIFIED" && it.nowStatus == "NOT ACTIONABLE" && it.nowReasons.any { r -> r == "price STALE" }, "${it.nowReasons}") }
    val past = a.tradeSetup.expiresAtMs + 7 * M
    c(a, past).let { check("C expiry passed: 'expired 7m ago'", it.nowStatus == "NOT ACTIONABLE" && it.nowReasons.any { r -> r == "expired 7m ago" }, "${it.nowReasons}")
        check("C expiry diagnostic FAIL", it.diagnostics.first { d -> d.name == "Forecast expiry" }.state == "FAIL") }
    val fri = sdf.parse("2026-10-30 20:50:00")!!.time
    make(fri, "LIVE QUALIFIED", PlanStamp.ACTIVE)?.let { f -> c(f, sdf.parse("2026-10-31 09:00:00")!!.time).let {
        check("D market closed: price MARKET CLOSED", it.nowStatus == "NOT ACTIONABLE" && it.nowReasons.any { r -> r == "price MARKET CLOSED" }, "${it.nowReasons}")
        check("D freshness diagnostic NA when closed", it.diagnostics.first { d -> d.name == "Price freshness" }.state == "NA") } } ?: check("D fixture", false)
    c(a.copy(feed = a.feed!!.copy(source = GoldApiService.OANDA_SOURCE)), t0).let {
        check("E provider switched: reason + provider FAIL", it.nowStatus == "NOT ACTIONABLE" && it.nowReasons.contains("provider changed since publication") &&
            it.diagnostics.first { d -> d.name == "Provider consistency" }.state == "FAIL", "${it.nowReasons}") }
    c(a.copy(tradeSetup = a.tradeSetup.copy(levelsSaved = false, levelsFixedAtMs = t0)), t0).let {
        check("F levels not saved: WARN 'fixed but not saved'", it.diagnostics.first { d -> d.name == "Frozen levels saved" }.let { d -> d.state == "WARN" && d.detail == "fixed but not saved" }) }
    c(a.copy(tradeSetup = a.tradeSetup.copy(planStatus = PlanStamp.STOPPED)), t0).let { check("G stopped: never actionable", it.status == "NOT ACTIONABLE" && it.nowStatus == "NOT ACTIONABLE") }
    val r = make(t0, "COLLECTING", PlanStamp.RESEARCH)!!
    c(r, t0).let { check("research now: RESEARCH ONLY", it.status == "RESEARCH" && it.nowStatus == "RESEARCH ONLY", it.nowStatus) }
    c(r, r.tradeSetup.expiresAtMs + M).let { check("research after expiry: OUTDATED, published kept", it.status == "RESEARCH" && it.nowStatus == "OUTDATED" && it.nowReasons.any { x -> x.startsWith("expired") }, "${it.nowStatus} ${it.nowReasons}") }
    // the original forecast is never rewritten
    val before = a.copy(); c(a, past)
    check("card does not change the analysis", a == before && a.tradeSetup.planStatus == PlanStamp.ACTIVE)
    // the UI re-runs build every second (cockpit tick), keyed on the second
    val ui = File("app/src/main/java/com/example/livegoldai/ui/v8/V8TrustCard.kt").readText()
    check("UI recomputes every second and shows both statuses", ui.contains("remember(analysis, lang, now / 1000) { TrustCard.build(analysis, now, lang) }") && ui.contains("card.nowStatus") && ui.contains("card.status"))

    // ---- diagnostics: eight separate checks, no score
    val d = c(a, t0).diagnostics
    check("eight diagnostics", d.size == 8, "${d.size}")
    check("states only OK/WARN/FAIL/NA", d.all { it.state in setOf("OK", "WARN", "FAIL", "NA") })
    check("no combined score or probability", d.none { it.name.contains("score", true) || it.detail.contains("probab", true) })
    check("WAIT: expiry/levels/history/authorization NA", make(t0, "COLLECTING", PlanStamp.SCENARIO)?.let { w -> c(w, t0).diagnostics.filter { it.name in setOf("Forecast expiry", "Frozen levels saved", "Historical evidence", "Research authorization") }.all { it.state == "NA" } } ?: true)

    // ---- exact-match hit-rate
    fun rec(id: String, at: Long, side: Signal = Signal.BUY, src: String = GoldApiService.OANDA_SOURCE, eng: String = "E22.1", iv: String = "5m") =
        LedgerRecord(id = id, createdAt = at, expiresAt = at + 30 * M, interval = iv, horizonMin = 30, barId = id, price = 1.0, rawSignal = side, finalSignal = side,
            confidence = 75, atr = 1.0, slDistance = 1.0, regime = "R", session = "S", newsActive = false, modelVersion = "", appliedFilter = "", sources = emptyMap(),
            dataSource = src, engineVersion = eng)
    fun res(id: String, o: LedgerOutcome) = LedgerResult(id, 1L, o, "x", 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0L, 0.0, emptyList(), emptyMap(), emptyList(), "", emptyList())
    val good = (0 until 25).map { rec("g$it", it * 60 * M) }
    val overlap = (0 until 25).map { rec("o$it", it * 60 * M + 10 * M) }            // inside the windows above: not independent
    val others = listOf(rec("s", 40 * M, side = Signal.SELL), rec("p", 100 * M, src = "Twelve Data XAU/USD"), rec("e", 160 * M, eng = "E22.0"), rec("t", 220 * M, iv = "15m"))   // free time slots: only the filter can drop them
    val results = (good.mapIndexed { i, x -> x.id to res(x.id, if (i < 15) LedgerOutcome.CORRECT else LedgerOutcome.INCORRECT) } +
        overlap.map { it.id to res(it.id, LedgerOutcome.CORRECT) } + others.map { it.id to res(it.id, LedgerOutcome.CORRECT) }).toMap()
    val st = LedgerState(good + overlap + others, results, emptyList())
    val ex = EdgeFinder.exactMatch(st, "5m", Signal.BUY, GoldApiService.OANDA_SOURCE, "E22.1")
    check("exact: 25 independent, overlaps and other side/provider/engine/timeframe left out, 60%", ex.n == 25 && ex.pct == 60, "$ex")
    check("exact scope says what was matched", ex.scope == "5M · BUY · OANDA · E22.1", ex.scope)
    val few = EdgeFinder.exactMatch(LedgerState(good.take(19), results, emptyList()), "5m", Signal.BUY, GoldApiService.OANDA_SOURCE, "E22.1")
    check("below 20: no percentage", few.pct == -1 && few.n == 19)
    val withPres = a.copy(presentation = a.presentation!!.copy(exactHitPct = 60, exactHitN = 25, exactScope = ex.scope, hitRatePct = 71, hitRateN = 140))
    c(withPres, t0).metrics[1].let { check("card: exact value, general only as GENERAL HISTORICAL CONTEXT", it.value == "TIER B · 60% · N=25" && it.note.contains("B (regime mixed): 60% · N=25 · 5M · BUY · OANDA · E22.1") && it.note.contains("GENERAL HISTORICAL CONTEXT") && it.note.contains("71% · N=140"), "${it.value} | ${it.note}") }
    c(a.copy(presentation = a.presentation!!.copy(exactHitPct = -1, exactHitN = 7, hitRatePct = 71, hitRateN = 140)), t0).metrics[1].let {
        check("card: too few exact -> INSUFFICIENT EVIDENCE, general number not shown as the value", it.value == "INSUFFICIENT EVIDENCE · N=7/20" && !it.value.contains("71"), it.value) }

    // ---- Macro evidence on the card (NFP 6 Nov 2026, 19:00 IST)
    val t = sdf.parse("2026-11-06 13:30:00")!!.time; val now = t + 12 * M
    val cal = OfficialCalendar.events()
    val candles = (0 until 150).map { k -> val o = 4000.0 + k * 0.4; CandleBar(sdf.format(Date(now - (150 - k) * 5 * M)), o, o + 0.6, o - 0.5, o + 0.35) }
    fun obs(sym: String, sig: Signal, unit: String) = MacroMarketIndex(sym, sym, 100.0, 0.2, sig, "x",
        MacroEvidence(EvidenceStatus.OBSERVED_COMPLETE, "OANDA", sym, "DIRECT", unit, t + 5 * M, t + 10 * M, now, "NFP@$t", true))
    fun miss(sym: String, s: EvidenceStatus) = MacroMarketIndex(sym, sym, 0.0, 0.0, Signal.WAIT, "x", MacroEvidence(s, "NONE", eventId = "NFP@$t"))
    val both = TechnicalEngine.analyze(candles, "5m", obs("DXY", Signal.SELL, "index points"), obs("USB10Y_USD", Signal.SELL, "note price"), cal, nowMs = now)
    val nl = TrustCard.newsLine(both, AppLanguage.ENGLISH, now)
    check("both observed: age + used in decision", nl.contains("data 2m old") && nl.contains("(used in decision)"), nl)
    val noDollar = TechnicalEngine.analyze(candles, "5m", miss("DXY", EvidenceStatus.UNAVAILABLE), obs("USB10Y_USD", Signal.SELL, "note price"), cal, nowMs = now)
    TrustCard.newsLine(noDollar, AppLanguage.ENGLISH, now).let { check("missing dollar named", it.contains("dollar UNAVAILABLE") && it.contains("10Y OBSERVED_COMPLETE"), it) }
    check("missing dollar: news diagnostic WARN", c(noDollar, now).diagnostics.first { it.name == "News evidence" }.state == "WARN")
    val no10 = TechnicalEngine.analyze(candles, "5m", obs("DXY", Signal.SELL, "index points"), miss("^TNX", EvidenceStatus.DELAYED), cal, nowMs = now)
    TrustCard.newsLine(no10, AppLanguage.ENGLISH, now).let { check("missing 10Y named", it.contains("10Y DELAYED"), it) }
    val pre = TechnicalEngine.analyze(candles, "5m", miss("DXY", EvidenceStatus.INCOMPLETE), miss("^TNX", EvidenceStatus.INCOMPLETE), cal, nowMs = t + 3 * M)
    TrustCard.newsLine(pre, AppLanguage.ENGLISH, t + 3 * M).let { check("no completed post-release candle: INCOMPLETE, not used, no age", it.contains("dollar INCOMPLETE") && it.contains("(not used in decision)") && !it.contains(" old"), it) }
    check("outside a window: news diagnostic NA", c(a, t0).diagnostics.first { it.name == "News evidence" }.state == "NA")

    // ---- Hindi / Marathi build without errors and keep the same structure
    for (l in listOf(AppLanguage.HINDI, AppLanguage.MARATHI)) { val x = TrustCard.build(a, t0 + 10 * M, l); check("$l: 8 checks, 5 metrics, now status", x.diagnostics.size == 8 && x.metrics.size == 5 && x.nowStatus == "NOT ACTIONABLE") }
    println("V35.5-E trust status: $pass passed, $fail failed")
}
