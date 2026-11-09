import com.example.livegoldai.data.*
import com.example.livegoldai.data.brain.FeatureCatalog
import com.example.livegoldai.model.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Random

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean) { if (ok) pass++ else { fail++; println("FAIL: $name") } }
const val MIN = 60_000L

fun oandaJson(gMin: Int, n: Int, now: Long, seed: Long = 3): String {
    val len = gMin * MIN
    val last = (now / len) * len
    val r = Random(seed); var p = 4000.0
    return "{\"candles\":[" + (0 until n).map { i ->
        val t = last - (n - 1 - i) * len
        val o = p; val c = o + (r.nextDouble() - 0.45) * 3; p = c
        val ts = CandleAggregator.label(t).replace(" ", "T") + ".000000000Z"
        "{\"complete\":${i != n - 1},\"volume\":50,\"time\":\"$ts\",\"mid\":{\"o\":\"%.3f\",\"h\":\"%.3f\",\"l\":\"%.3f\",\"c\":\"%.3f\"}}".format(o, maxOf(o, c) + 0.4, minOf(o, c) - 0.4, c)
    }.joinToString(",") + "]}"
}
val GRAN = mapOf("M5" to 5, "M15" to 15, "H1" to 60, "H4" to 240, "D" to 1440)

fun main() = runBlocking {
    val now = System.currentTimeMillis()

    // ---------- 1. MTF from OANDA XAU/USD when a token is saved
    val asked = mutableListOf<String>()
    val api = GoldApiService("")
    api.oandaTransport = OandaTransport { url, _ ->
        val g = Regex("granularity=(\\w+)").find(url)!!.groupValues[1]; asked += g
        OandaHttp(200, oandaJson(GRAN.getValue(g), 120, now))
    }
    api.setOandaTokenProvider { "tok" }
    val m = api.fetchMtfCandles()
    check("5 timeframes from OANDA", m.keys == setOf("5m", "15m", "1h", "4h", "1d"))
    check("granularities M5 M15 H1 H4 D", asked.toSet() == GRAN.keys)
    check("source = OANDA", api.mtfSourceOf(m) == GoldApiService.MTF_OANDA)
    check("bars UTC aligned", m.getValue("1h").all { it.t % (60 * MIN) == 0L } && m.getValue("4h").all { it.t % (240 * MIN) == 0L })
    check("another map is never called OANDA", api.mtfSourceOf(HashMap(m)) == GoldApiService.MTF_PAXG)
    asked.clear(); val again = api.fetchMtfCandles()
    check("cached for a minute (no new requests)", asked.isEmpty() && again === m)

    // partial OANDA answer: only OANDA rows, never topped up from PAXG
    val api2 = GoldApiService("")
    api2.oandaTransport = OandaTransport { url, _ ->
        val g = Regex("granularity=(\\w+)").find(url)!!.groupValues[1]
        if (g == "H4") OandaHttp(503, "{}") else OandaHttp(200, oandaJson(GRAN.getValue(g), 120, now))
    }
    api2.setOandaTokenProvider { "tok" }
    val m2 = api2.fetchMtfCandles()
    check("partial: 4 OANDA rows, no mixing", m2.keys == setOf("5m", "15m", "1h", "1d") && api2.mtfSourceOf(m2) == "OANDA")

    // no token: OANDA never asked
    val api3 = GoldApiService("")
    var n3 = 0
    api3.oandaTransport = OandaTransport { _, _ -> n3++; OandaHttp(200, "{}") }
    val m3 = api3.fetchMtfCandles()
    check("no token: OANDA not called, not labelled OANDA", n3 == 0 && api3.mtfSourceOf(m3) == "PAXG")

    // ---------- 2. matrix labels follow the source
    val mx = RealityEngine.buildMtf(m, "OANDA")!!
    check("XAU rows labelled", mx.timeframes.size == 5 && mx.timeframes.all { it.label.startsWith(RealityEngine.MTF_XAU_LABEL) && RealityEngine.isMtfRow(it.label) })
    check("XAU summary honest", mx.alignmentSummary.contains("XAU/USD (OANDA") && !mx.alignmentSummary.contains("PAXG"))
    check("mtfIsXau", RealityEngine.mtfIsXau(mx) && RealityEngine.mtfSourceName(mx) == "XAU/USD (OANDA)")
    val mp = RealityEngine.buildMtf(m)!!
    check("default stays PAXG (old behaviour)", mp.timeframes.all { it.label.startsWith("PAXG/USDT") } && !RealityEngine.mtfIsXau(mp) && RealityEngine.isMtfRow(mp.timeframes[0].label))
    check("same rule, same signals: only the label differs", mx.timeframes.map { it.signal } == mp.timeframes.map { it.signal })

    // ---------- 3. feature store keeps the two inputs apart
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    val r = Random(5); var px = 4000.0
    val candles = (0 until 120).map { i -> val o = px; px += (r.nextDouble() - 0.5) * 2; CandleBar(sdf.format(java.util.Date((now / (5 * MIN)) * 5 * MIN - (119 - i) * 5 * MIN)), o, maxOf(o, px) + 1, minOf(o, px) - 1, px) }
    val base = TechnicalEngine.analyze(candles, "5m")
    val fx = FeatureCatalog.compute(base.copy(mtfMatrix = mx), null, null, now)
    val fp = FeatureCatalog.compute(base.copy(mtfMatrix = mp), null, null, now)
    check("XAU context -> mtf_net_xau_v1 only", fx.containsKey("mtf_net_xau_v1") && !fx.containsKey("mtf_net_v1"))
    check("PAXG context -> mtf_net_v1 only (history unchanged)", fp.containsKey("mtf_net_v1") && !fp.containsKey("mtf_net_xau_v1"))
    check("feature defined in catalog", File("app/src/main/java/com/example/livegoldai/data/brain/FeatureStore.kt").readText().contains("FeatureDef(\"mtf_net_xau_v1\""))

    // ---------- 4. presentation + home tile
    val st = LedgerState(emptyList(), emptyMap(), emptyList())
    val pres = PresentationBuilder.build(base.copy(mtfMatrix = mx, feed = FeedStatus(GoldApiService.OANDA_SOURCE, now, 200, true, true, true, 0, 1)), st, null, now)
    check("presentation knows XAU context", pres.mtfAvailable && pres.mtfXau)
    check("tile OANDA", PlanText.sourceTile(GoldApiService.OANDA_SOURCE, "PRIMARY") == "OANDA")
    check("tile TD", PlanText.sourceTile("Twelve Data XAU/USD", "PRIMARY") == "TWELVE DATA")
    check("tile TD backup", PlanText.sourceTile("Twelve Data XAU/USD (backup)", "PRIMARY") == "TD BACKUP")
    check("tile proxy keeps type", PlanText.sourceTile("Binance PAXG/USDT", "PROXY") == "PROXY")
    check("tile none", PlanText.sourceTile(null, "") == "--")
    for (l in com.example.livegoldai.localization.AppLanguage.values()) {
        val rows = DecisionTraceText.sections(pres, l, now, emptyList()).flatMap { it.rows }
        check("trace names XAU context $l", rows.any { it.second.startsWith("XAU/USD (OANDA)") } && rows.none { it.second.startsWith("PAXG/USDT") })
    }

    // ---------- 5. static: one filter everywhere, no stray PAXG-only filter
    val src = File("app/src/main/java").walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.associateWith { it.readText() }
    val stray = src.filter { (f, t) -> f.name != "RealityEngine.kt" && t.contains("label.startsWith(\"PAXG\")") }.keys.map { it.name }
    check("no PAXG-only row filter left ($stray)", stray.isEmpty())
    check("filters use isMtfRow", src.values.sumOf { Regex("RealityEngine\\.isMtfRow\\(").findAll(it).count() } >= 9)
    check("recorder passes the MTF source", src.entries.first { it.key.name == "BackgroundRecorder.kt" }.value.contains("mtfSource = { api.mtfSourceOf(it) }"))
    check("screen passes the MTF source", src.entries.first { it.key.name == "GoldViewModel.kt" }.value.contains("mtfSource = apiService.mtfSourceOf(mtfCandles)"))

    println("V32.3.1 MTF + tile: $pass passed, $fail failed")
    if (fail > 0) System.exit(1)
}
