import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Random

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean) { if (ok) pass++ else { fail++; println("FAIL: $name") } }
const val MIN = 60_000L

/** OANDA-shaped candle JSON for granularity [g] minutes ending at the current (forming) bar */
fun oandaJson(gMin: Int, n: Int, now: Long, seed: Long = 3): String {
    val len = gMin * MIN
    val last = (now / len) * len
    val r = Random(seed); var p = 3995.0
    return "{\"candles\":[" + (0 until n).map { i ->
        val t = last - (n - 1 - i) * len
        val o = p; val c = o + (r.nextDouble() - 0.5) * 2; p = c
        val ts = CandleAggregator.label(t).replace(" ", "T") + ".000000000Z"
        "{\"complete\":${i != n - 1},\"volume\":50,\"time\":\"$ts\",\"mid\":{\"o\":\"%.3f\",\"h\":\"%.3f\",\"l\":\"%.3f\",\"c\":\"%.3f\"}}".format(o, maxOf(o, c) + 0.4, minOf(o, c) - 0.4, c)
    }.joinToString(",") + "]}"
}

fun main() = runBlocking {
    val now = System.currentTimeMillis()

    // ---------- 1. OANDA is the primary feed when a token is saved
    val calls = mutableListOf<String>()
    val api = GoldApiService("")
    api.oandaTransport = OandaTransport { url, tok ->
        calls += url
        check("token handed to transport", tok == "practice-token-for-test")
        val g = Regex("granularity=M(\\d+)").find(url)?.groupValues?.get(1)?.toInt() ?: 1
        if (url.contains("from=")) OandaHttp(200, oandaJson(1, 40, now - 5 * MIN)) else OandaHttp(200, oandaJson(g, 220, now))
    }
    api.setOandaTokenProvider { "practice-token-for-test" }
    val a = api.fetchAnalysis("5m").getOrNull()!!
    check("source is OANDA", a.feed?.source == GoldApiService.OANDA_SOURCE)
    check("provider OANDA, XAU spot, PRIMARY", a.feed?.providerId == "OANDA" && a.feed?.instrumentId == "XAU_USD_SPOT" && a.feed?.sourceType == "PRIMARY")
    check("first source tried", a.feed?.sourcesTried == 1 && a.feed?.primaryIssue == "")
    check("key not missing with OANDA only", a.feed?.keyMissing == false)
    check("live data", !a.isSimulatedFallback && a.feed?.isLive == true)
    check("candles labelled with UTC bar start", a.recentCandles.all { it.datetime.endsWith(":00") && it.datetime.substring(14, 16).toInt() % 5 == 0 })
    check("asked M5, mid prices, UTC alignment", calls.filter { it.contains("/candles") }.single().contains("granularity=M5") && calls.filter { it.contains("/candles") }.single().contains("price=M") && calls.filter { it.contains("/candles") }.single().contains("alignmentTimezone=UTC"))
    check("no volume invented (OANDA tick counts ignored)", a.recentCandles.all { it.volume == null })
    check("badge", SourceRegistry.badge(a.feed?.source) == "XAU/USD • OANDA • PRIMARY")

    // aggregated timeframe from OANDA: 3m = M1×3
    calls.clear()
    val a3 = api.fetchAnalysis("3m").getOrNull()!!
    check("3m from M1, aligned", calls.filter { it.contains("/candles") }.single().contains("granularity=M1&") && a3.recentCandles.all { it.datetime.substring(14, 16).toInt() % 3 == 0 } && a3.feed?.source == GoldApiService.OANDA_SOURCE)

    // ---------- 2. OANDA fails -> next source, reason shown; Twelve Data marked backup
    val api2 = GoldApiService("")
    api2.oandaTransport = OandaTransport { _, _ -> OandaHttp(401, "{\"errorMessage\":\"Insufficient authorization\"}") }
    api2.setOandaTokenProvider { "t" }
    val b = api2.fetchAnalysis("5m").getOrNull()
    check("fallback carries OANDA reason", b == null || (b.feed?.primaryIssue ?: "").contains("OANDA TOKEN_INVALID"))
    check("fallback is not called OANDA", b == null || b.feed?.providerId != "OANDA")
    check("TD backup badge", SourceRegistry.badge("Twelve Data XAU/USD (backup)") == "XAU/USD • TWELVE DATA • BACKUP")
    check("TD normal badge", SourceRegistry.badge("Twelve Data XAU/USD") == "XAU/USD • TWELVE DATA • PRIMARY")
    check("TD backup is still Twelve Data for Shadow Lab", "Twelve Data XAU/USD (backup)".contains(ShadowLive.TWELVE))

    // weekly is not served by OANDA here -> no OANDA call
    calls.clear(); api.fetchAnalysis("1w")
    check("1w not requested from OANDA", calls.isEmpty())

    // no token -> OANDA never called
    val api3 = GoldApiService("")
    var n3 = 0
    api3.oandaTransport = OandaTransport { _, _ -> n3++; OandaHttp(200, "{}") }
    api3.fetchAnalysis("5m")
    check("no token: OANDA not called", n3 == 0 && !api3.oandaConfigured())

    // ---------- 3. verification on the SAME provider
    calls.clear()
    val start = (now / MIN) * MIN - 40 * MIN
    val p = api.fetchPathFor(GoldApiService.OANDA_SOURCE, start, start + 25 * MIN, now)!!
    check("OANDA record checked on OANDA M1", p.source == "OANDA XAU_USD M1" && !p.waiting && p.bars.isNotEmpty())
    check("range request", calls.filter { it.contains("/candles") }.single().contains("granularity=M1") && calls.filter { it.contains("/candles") }.single().contains("from=") && calls.filter { it.contains("/candles") }.single().contains("to="))
    check("only bars inside the window", p.bars.all { it.t >= start - MIN && it.t < start + 25 * MIN })
    calls.clear()
    val td = api.fetchPathFor("Twelve Data XAU/USD", start, start + 25 * MIN, now)!!
    check("Twelve Data record never checked on OANDA", calls.isEmpty() && td.waiting)
    val tdb = api.fetchPathFor("Twelve Data XAU/USD (backup)", start, start + 25 * MIN, now)!!
    check("TD backup record never checked on OANDA", calls.isEmpty() && tdb.waiting)
    val noTok = GoldApiService("")
    check("OANDA record without token waits (never another feed)", noTok.fetchPathFor(GoldApiService.OANDA_SOURCE, start, start + 25 * MIN, now)!!.waiting)
    check("same instrument for the ledger", PredictionLedger.instrumentOf(GoldApiService.OANDA_SOURCE) == "XAU" && PredictionLedger.instrumentOf("OANDA XAU_USD M1") == "XAU")

    // ---------- 4. provider change = source switch (RESTORING until clean fetches)
    val sm = SourceStateMachine()
    val iv = 5 * MIN
    val cs = (0 until 60).map { i -> val t = (now / iv) * iv - (59 - i) * iv; SourceStateMachine.Candle(CandleAggregator.label(t), 4000.0, 4001.0, 3999.0, 4000.5) }
    val closed = PredictionLedger.isMarketClosed(now)
    val d1 = sm.evaluate("5m", "Twelve Data XAU/USD", cs, iv, closed, now, fetchId = 1)
    check("TD active", d1.state == SourceStateMachine.PRIMARY_ACTIVE && d1.allowed)
    val d2 = sm.evaluate("5m", GoldApiService.OANDA_SOURCE, cs, iv, closed, now + 1, fetchId = 2)
    check("switch to OANDA -> RESTORING", d2.state == SourceStateMachine.RESTORING && !d2.allowed && d2.detail.contains("provider changed Twelve Data -> OANDA"))
    val d3 = sm.evaluate("5m", GoldApiService.OANDA_SOURCE, cs, iv, closed, now + 2, fetchId = 3)
    check("2nd clean OANDA fetch -> active", d3.state == SourceStateMachine.PRIMARY_ACTIVE && d3.allowed && d3.detail.startsWith("OANDA XAU/USD"))
    val d4 = sm.evaluate("5m", "Twelve Data XAU/USD (backup)", cs, iv, closed, now + 3, fetchId = 4)
    check("back to TD -> RESTORING again", d4.state == SourceStateMachine.RESTORING && d4.detail.contains("OANDA -> Twelve Data"))
    val d5 = sm.evaluate("5m", "Twelve Data XAU/USD (backup)", cs, iv, closed, now + 4, fetchId = 5)
    check("same provider again -> active", d5.state == SourceStateMachine.PRIMARY_ACTIVE)
    val sm2 = SourceStateMachine()
    check("first ever fetch on OANDA: no switch", sm2.evaluate("5m", GoldApiService.OANDA_SOURCE, cs, iv, closed, now, fetchId = 1).state == SourceStateMachine.PRIMARY_ACTIVE)

    // ---------- 5. a Twelve Data qualification never makes OANDA data ACTIVE
    val st = LedgerState(emptyList(), emptyMap(), emptyList())
    if (!closed) {
        val base = a.copy(overallSignal = Signal.BUY, rawSignal = Signal.BUY, sourceState = SourceStateMachine.PRIMARY_ACTIVE)
        check("OANDA + LIVE QUALIFIED -> RESEARCH", PlanStamp.stamp(base, st, now, "LIVE QUALIFIED").tradeSetup.planStatus == PlanStamp.RESEARCH)
        val tdFeed = base.feed!!.copy(source = "Twelve Data XAU/USD", providerId = "TWELVE_DATA")
        val tdOk = PlanStamp.stamp(base.copy(feed = tdFeed), st, now, "LIVE QUALIFIED").tradeSetup.planStatus
        check("TD + LIVE QUALIFIED unchanged (ACTIVE)", tdOk == PlanStamp.ACTIVE)
        val ps = PresentationBuilder.build(PlanStamp.stamp(base, st, now, "LIVE QUALIFIED"), st, "LIVE QUALIFIED", now)
        check("presentation: no proven edge on OANDA", !ps.provenEdge && ps.secondaryReasons.contains("QUALIFIED_OTHER_FEED"))
        for (l in AppLanguage.values()) check("reason text $l", PlanText.secondaryLabel("QUALIFIED_OTHER_FEED", l) != "QUALIFIED_OTHER_FEED")
    } else println("(market closed now: ACTIVE/RESEARCH checks skipped, they need LIVE freshness)")

    // ---------- 6. health + panel text
    val h = HealthMonitor.javaClass  // just ensure class loads
    val panel = OandaText.panel(null, true, "XAU/USD • OANDA • PRIMARY", now, AppLanguage.ENGLISH)
    check("panel: OANDA primary, no 'not used' note", panel[0].second == "XAU/USD • OANDA • PRIMARY")
    check("panel: TD while token saved explains", OandaText.panel(null, true, "XAU/USD • TWELVE DATA • BACKUP", now, AppLanguage.ENGLISH)[0].second.contains("OANDA not used now"))

    // ---------- 7. static: read-only, token handling, protected files
    val src = File("app/src/main/java").walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.associateWith { it.readText() }
    check("still no live host / order endpoint", src.values.none { it.contains("api-fxtrade") || Regex("oanda[^\\n]*/(orders|trades|positions)", RegexOption.IGNORE_CASE).containsMatchIn(it) })
    val gas = src.entries.first { it.key.name == "GoldApiService.kt" }.value
    check("token not stored in GoldApiService", !Regex("var\\s+oandaToken\\s*:\\s*String").containsMatchIn(gas))
    val svc = src.entries.first { it.key.name == "RecorderService.kt" }.value
    check("recorder gets token provider + TD key", svc.contains("setOandaTokenProvider") && svc.contains("getFeedKey()"))
    check("ShadowLive still TD-only", src.entries.first { it.key.name == "ShadowLive.kt" }.value.contains("if (a.feed?.source?.contains(TWELVE) != true) return emptyList()"))

    println("V32 stage 3: $pass passed, $fail failed")
    if (fail > 0) System.exit(1)
}
