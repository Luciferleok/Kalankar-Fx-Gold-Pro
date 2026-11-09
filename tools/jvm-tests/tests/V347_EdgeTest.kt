import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean) { if (ok) pass++ else { fail++; println("FAIL: $name") } }

fun main() {
    // Wilson 99% sanity
    val (lo, hi) = EdgeFinder.wilson(805, 1547)    // 52% of 1547 (the phone's number)
    check("52% of 1547: 99% range includes 50% -> no proven edge", lo < 0.5 && hi > 0.5)
    check("Wilson bounds ordered", EdgeFinder.wilson(0, 10).first < 1e-9 && EdgeFinder.wilson(10, 10).second > 1 - 1e-9 && EdgeFinder.wilson(5, 10).let { it.first < 0.5 && it.second > 0.5 })

    fun rec(i: Int, iv: String, ses: String, side: Signal, conf: Int, src: String) = LedgerRecord(id = "r$i", createdAt = i.toLong(), expiresAt = i + 1L,
        interval = iv, horizonMin = 25, barId = "b$i", price = 4000.0, rawSignal = side, finalSignal = side, confidence = conf, atr = 1.0, slDistance = 3.0,
        regime = "TREND", session = ses, newsActive = false, modelVersion = "", appliedFilter = "", sources = emptyMap(), dataSource = src)
    fun res(id: String, ok: Boolean) = LedgerResult(id, 1L, if (ok) LedgerOutcome.CORRECT else LedgerOutcome.INCORRECT, "x", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0L, 0.0, emptyList(), emptyMap(), emptyList(), "", emptyList())
    val rnd = Random(42); val recs = ArrayList<LedgerRecord>(); val out = HashMap<String, LedgerResult>(); var i = 0
    // 15M in LONDON: a real edge (66% right, N=300); everything else a fair coin (N=1200)
    repeat(300) { val r = rec(i++, "15m", "LONDON", Signal.SELL, 72, GoldApiService.OANDA_SOURCE); recs += r; out[r.id] = res(r.id, rnd.nextDouble() < 0.66) }
    repeat(1200) { val r = rec(i++, listOf("5m", "1h", "4h")[it % 3], listOf("ASIAN", "NEW_YORK")[it % 2], if (it % 2 == 0) Signal.BUY else Signal.SELL, 65, "Twelve Data XAU/USD"); recs += r; out[r.id] = res(r.id, rnd.nextDouble() < 0.50) }
    // WAIT and uncertain are not counted
    val w = rec(i++, "15m", "LONDON", Signal.WAIT, 50, GoldApiService.OANDA_SOURCE); recs += w; out[w.id] = res(w.id, true)
    val st = LedgerState(recs, out, emptyList())
    val gs = EdgeFinder.groups(st)
    val tf15 = gs.single { it.dimension == "Timeframe" && it.value == "15M" }
    check("real edge found: 15M PROVEN EDGE", tf15.verdict == "HISTORICAL EDGE" && tf15.n == 300)
    check("London PROVEN EDGE", gs.single { it.dimension == "Session" && it.value == "LONDON" }.verdict == "HISTORICAL EDGE")
    check("coin-flip groups never PROVEN", gs.filter { it.dimension == "Timeframe" && it.value != "15M" }.none { it.verdict == "HISTORICAL EDGE" })
    check("WAIT not counted", gs.filter { it.dimension == "Side" }.sumOf { it.n } == 1500)
    // false-positive rate on pure noise: 200 random ledgers, count any PROVEN EDGE
    var falseHits = 0
    repeat(200) { seed ->
        val r2 = Random(1000L + seed); val rs = ArrayList<LedgerRecord>(); val os = HashMap<String, LedgerResult>()
        repeat(600) { k -> val r = rec(k, listOf("5m", "15m", "1h", "4h")[k % 4], listOf("ASIAN", "LONDON", "NEW_YORK")[k % 3], if (k % 2 == 0) Signal.BUY else Signal.SELL, 60 + k % 30, "Twelve Data XAU/USD"); rs += r; os[r.id] = res(r.id, r2.nextDouble() < 0.5) }
        if (EdgeFinder.groups(LedgerState(rs, os, emptyList())).any { it.verdict == "HISTORICAL EDGE" }) falseHits++
    }
    println("pure-noise ledgers that showed any PROVEN EDGE: $falseHits / 200")
    check("false edges on pure noise are rare (<= 2%)", falseHits <= 4)
    // summary lines
    val sum = EdgeFinder.summary(st)
    check("summary starts with all calls", sum.first().startsWith("All calls:") && sum.first().contains("of 1500"))
    check("summary lists the proven edge", sum.any { it.startsWith("Timeframe 15M") && it.contains("HISTORICAL EDGE") })
    val noise = EdgeFinder.summary(LedgerState(recs.drop(300), out, emptyList()))
    check("no edge -> says so", noise.any { it.startsWith("No group shows a historical edge") } && noise.last().contains("none is forward-validated"))
    // forecast line
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val now = sdf.parse("2026-10-28 09:22:10")!!.time   // London session
    val candles = (0 until 150).map { k -> val o = 4100.0 - k * 0.3; CandleBar(sdf.format(Date((now / 900_000L) * 900_000L - (149 - k) * 900_000L)), o, o + 0.5, o - 0.6, o - 0.3) }
    val a = TechnicalEngine.analyze(candles, "15m").copy(feed = FeedStatus(GoldApiService.OANDA_SOURCE, now, 1, true, true, true, 0, 1))
    if (a.overallSignal != Signal.WAIT) {
        val f = EdgeFinder.forForecast(a, st, now)
        println("forecast line: $f")
        check("forecast line names a proven group", f.contains("HISTORICAL EDGE"))
        check("noise ledger -> no proven edge for this setup", EdgeFinder.forForecast(a, LedgerState(recs.drop(300), out, emptyList()), now).let { it.startsWith("No edge shown") || it.startsWith("INSUFFICIENT SAMPLE") })
    }
    check("WAIT forecast -> no line", EdgeFinder.forForecast(a.copy(overallSignal = Signal.WAIT), st, now) == "")
    val src = File("app/src/main/java/com/example/livegoldai")
    check("shown on HOME, SYSTEM, Passport", File(src, "ui/v8/V8CockpitScreen.kt").readText().contains("analysis.pulse?.edgeForCurrent") &&
        File(src, "ui/v8/V8HealthScreen.kt").readText().contains("p.edges.forEach") && File(src, "data/TrustPassport.kt").readText().contains("Evidence for this setup"))
    check("read-only: learning untouched", listOf("data/LearningEngine.kt", "data/ShadowLive.kt", "data/PlanIntegrity.kt").none { File(src, it).readText().contains("EdgeFinder") })
    println("V34.7 edge finder: $pass passed, $fail failed")
    if (fail > 0) System.exit(1)
}
