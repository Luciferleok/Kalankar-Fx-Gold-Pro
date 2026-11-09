import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
fun t(s: String) = sdf.parse(s)!!.time
const val MIN = 60_000L

fun main() {
    val now = t("2026-11-03 10:12:00")   // Tuesday, market open
    val candles = (0 until 150).map { k -> val o = 3990.0 + k * 0.01; CandleBar(sdf.format(Date(t("2026-11-03 00:00:00") + k * 5 * MIN)), o, o + 0.6, o - 0.4, o + 0.05) }
    val a0 = TechnicalEngine.analyze(candles, "5m", nowMs = now)
    val px = a0.currentPrice
    fun feed(q: Long, bid: Double = px - 0.15, ask: Double = px + 0.15) = FeedStatus(GoldApiService.OANDA_SOURCE, now - 3_000L, 1, true, true, true, 0, 1, quoteTimeMs = q, bid = bid, ask = ask, tradeable = true)
    val current = a0.copy(lastUpdated = "2026-11-03 10:10:00")
    check("current candle + fresh quote + price matches -> LIVE", Freshness.assess(current.copy(feed = feed(now - 5_000L)), now) == Freshness.LIVE)
    val missing = a0.copy(lastUpdated = "2026-11-03 10:01:00")   // newest candle 11 min old on a 5m chart
    check("fresh quote cannot make a chart with a missing candle LIVE", Freshness.assess(missing.copy(feed = feed(now - 5_000L)), now) == Freshness.DELAYED,
        Freshness.assess(missing.copy(feed = feed(now - 5_000L)), now))
    check("shown price far from the quote -> not LIVE", Freshness.assess(current.copy(feed = feed(now - 5_000L, px - 20.15, px - 19.85)), now) == Freshness.DELAYED)
    check("small difference (within max(3, 10 x spread)) still LIVE", Freshness.assess(current.copy(feed = feed(now - 5_000L, px + 1.85, px + 2.15)), now) == Freshness.LIVE)
    // quote stamped 10 minutes in the future: provider time ignored, download time (3 s ago) used, chart current -> LIVE
    // but the shown price age must not be negative-based LIVE from the future stamp
    val fut = current.copy(feed = feed(now + 10 * MIN))
    check("future quote time ignored (download time used)", Freshness.assess(fut, now) == Freshness.LIVE)
    val futOld = current.copy(feed = feed(now + 10 * MIN).copy(fetchedAtMs = now - 10 * MIN))
    check("future quote time + old download -> not LIVE", Freshness.assess(futOld, now) != Freshness.LIVE, Freshness.assess(futOld, now))
    check("tradeable=false -> CLOSED", Freshness.assess(current.copy(feed = feed(now - 5_000L).copy(tradeable = false)), now) == Freshness.CLOSED)
    check("no bid/ask (Twelve Data) still follows the candle rule", Freshness.assess(current.copy(feed = feed(0L, 0.0, 0.0)), now) == Freshness.LIVE)
    println("V35.3-C freshness: $pass passed, $fail failed")
    if (fail > 0) kotlin.system.exitProcess(1)
}
