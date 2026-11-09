import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean) { if (ok) pass++ else { fail++; println("FAIL: $name") } }

fun main() {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val now = sdf.parse("2026-10-28 10:22:10")!!.time; val iv = 300_000L
    val rnd = Random(3); var p = 4100.0
    val candles = (0 until 150).map { i -> val o = p; p -= 0.35 + (rnd.nextDouble() - 0.5) * 1.2; CandleBar(sdf.format(Date((now / iv) * iv - (149 - i) * iv)), o, maxOf(o, p) + 0.6, minOf(o, p) - 0.6, p) }
    val td = FeedStatus("Twelve Data XAU/USD", now - 3_000, 300, true, true, true, 0, 1)
    val oa = td.copy(source = GoldApiService.OANDA_SOURCE)
    fun an(cs: List<CandleBar>, f: FeedStatus) = TechnicalEngine.analyze(cs, "5m").copy(feed = f, sourceState = SourceStateMachine.PRIMARY_ACTIVE)

    // ---- parsing (OANDA v20 pricing format)
    val body = """{"prices":[{"type":"PRICE","instrument":"XAU_USD","time":"2026-10-28T10:22:08.123456789Z","tradeable":true,"bids":[{"price":"4049.71","liquidity":250}],"asks":[{"price":"4050.05","liquidity":250}],"status":"tradeable"}],"time":"2026-10-28T10:22:09.000000000Z"}"""
    val q = OandaPricing.parseQuote(body)
    check("quote parsed", q != null && q.bid == 4049.71 && q.ask == 4050.05 && q.tradeable == true)
    check("provider time (nanoseconds) parsed", q?.timeMs == sdf.parse("2026-10-28 10:22:08")!!.time + 123)
    check("spread", q != null && Math.abs(q.spread - 0.34) < 1e-9)
    check("missing ask -> no quote", OandaPricing.parseQuote(body.replace(""""asks":[{"price":"4050.05","liquidity":250}],""", "")) == null)
    check("ask below bid -> no quote", OandaPricing.parseQuote(body.replace("4050.05", "4049.00")) == null)
    check("bad time -> no quote", OandaPricing.parseQuote(body.replace("2026-10-28T10:22:08.123456789Z", "yesterday")) == null)
    check("garbage -> no quote", OandaPricing.parseQuote("<html>") == null && OandaPricing.parseQuote("") == null)
    check("accounts parsed", OandaPricing.parseAccounts("""{"accounts":[{"id":"101-004-1-001","tags":[]},{"id":"101-004-1-002"}]}""") == listOf("101-004-1-001", "101-004-1-002"))

    // ---- fetch with a fake OANDA: account found once, then reused; read-only GETs only
    val calls = ArrayList<String>()
    val fake = OandaTransport { url, token ->
        calls += url
        check("token sent, never in the URL", token == "T0K" && !url.contains("T0K"))
        when {
            url.endsWith("/accounts") -> OandaHttp(200, """{"accounts":[{"id":"A1"},{"id":"A2"}]}""")
            url.contains("/accounts/A1/pricing") -> OandaHttp(400, """{"errorMessage":"no"}""")
            url.contains("/accounts/A2/pricing") -> OandaHttp(200, body)
            else -> OandaHttp(404, "")
        }
    }
    val (acc, q1) = OandaPricing.fetch(fake, "T0K", null)
    check("first account without pricing skipped", acc == "A2" && q1 != null)
    calls.clear(); val (acc2, q2) = OandaPricing.fetch(fake, "T0K", acc)
    check("account reused: one request", acc2 == "A2" && q2 != null && calls.size == 1)
    check("no token -> nothing called", OandaPricing.fetch(fake, "", null) == (null to null))
    check("network error -> no quote, no crash", OandaPricing.fetch({ _, _ -> throw RuntimeException("x") }, "T0K", "A2") == ("A2" to null))
    check("every OANDA URL is a read-only endpoint", OandaEndpoints.all("A").none { Regex("orders|trades|positions|transactions", RegexOption.IGNORE_CASE).containsMatchIn(it) } &&
        OandaEndpoints.all("A").contains(OandaEndpoints.pricing("A")))
    val api = File("app/src/main/java/com/example/livegoldai/data/OandaHttpTransport.kt").readText()
    check("transport is GET only", !api.contains(".post(") && !api.contains(".put(") && !api.contains(".delete("))

    // ---- freshness from the price, not the download
    val cur = an(candles, td)
    check("Twelve Data, current candle, fetched 3 s ago -> LIVE", Freshness.assess(cur, now) == Freshness.LIVE)
    val noNewCandle = an(candles.dropLast(1), td)                                        // newest candle opened 7.2 min ago
    check("Twelve Data, no new candle for a full interval -> not LIVE (DELAYED)", Freshness.assess(noNewCandle, now) == Freshness.DELAYED)
    val oFresh = an(candles, oa.copy(quoteTimeMs = now - 2_000, bid = 4049.7, ask = 4050.0, tradeable = true))
    check("OANDA quote 2 s old -> LIVE", Freshness.assess(oFresh, now) == Freshness.LIVE)
    val oOldQuote = an(candles, oa.copy(fetchedAtMs = now - 1_000, quoteTimeMs = now - 200_000, bid = 4049.7, ask = 4050.0, tradeable = true))
    check("OANDA: fetched 1 s ago but quote 3.3 min old -> DELAYED, not LIVE", Freshness.assess(oOldQuote, now) == Freshness.DELAYED)
    val oVeryOld = an(candles, oa.copy(fetchedAtMs = now - 1_000, quoteTimeMs = now - 400_000, bid = 1.0, ask = 1.1, tradeable = true))
    check("OANDA quote older than an interval -> STALE", Freshness.assess(oVeryOld, now) == Freshness.STALE)
    check("OANDA not tradeable -> CLOSED", Freshness.assess(an(candles, oa.copy(quoteTimeMs = now - 2_000, tradeable = false)), now) == Freshness.CLOSED)
    check("OANDA without a quote: old rule (download time + candle)", Freshness.assess(an(candles, oa), now) == Freshness.LIVE)
    check("weekend -> CLOSED first", Freshness.assess(cur, sdf.parse("2026-10-31 10:00:00")!!.time) == Freshness.CLOSED)
    check("price age uses provider time", Freshness.priceAgeMs(oOldQuote, now) == 200_000L && Freshness.priceAgeMs(cur, now) == 3_000L)
    // a STALE price blocks the plan (no ACTIVE from an old price)
    check("stale OANDA quote -> plan NO_DATA", PlanStamp.stamp(oVeryOld, LedgerState(emptyList(), emptyMap(), emptyList()), now, "LIVE QUALIFIED").tradeSetup.planStatus == PlanStamp.NO_DATA)

    // ---- wiring
    val gas = File("app/src/main/java/com/example/livegoldai/data/GoldApiService.kt").readText()
    check("quote attached to the OANDA feed only", gas.contains("val finalResult = withOandaQuote(withFeed(") && Regex("withOandaQuote\\(").findAll(gas).count() == 2)
    check("analysis prices not changed by the quote", gas.contains("r.copy(feed = f.copy(quoteTimeMs = q.timeMs, bid = q.bid, ask = q.ask, tradeable = q.tradeable))"))
    val ck = File("app/src/main/java/com/example/livegoldai/ui/v8/V8CockpitScreen.kt").readText()
    check("HOME uses the same freshness rule + price age + spread", ck.contains("Freshness.assess(analysis, now)") && ck.contains("Freshness.priceAgeMs(analysis, now)") && ck.contains("\"SPREAD\""))
    check("HOME has DELAYED in 3 languages", ck.contains("\"DELAYED\" -> tr(lang, \"DELAYED\", \"देरी से\", \"उशिराने\")"))

    println("V34.3 quote truth: $pass passed, $fail failed")
    if (fail > 0) System.exit(1)
}
