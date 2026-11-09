import com.example.livegoldai.data.*
import com.example.livegoldai.data.ai.*
import com.example.livegoldai.model.*
import java.io.File
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
fun t(s: String) = sdf.parse(s)!!.time
const val MIN = 60_000L
fun rec(id: String, created: Long, bar: String, src: String, iv: String = "15m") = LedgerRecord(id = id, createdAt = created, expiresAt = created + 60 * MIN, interval = iv,
    horizonMin = 60, barId = bar, price = 4000.0, rawSignal = Signal.BUY, finalSignal = Signal.BUY, confidence = 70, atr = 2.0, slDistance = 3.0, regime = "TREND",
    session = "LONDON", newsActive = false, modelVersion = "", appliedFilter = "", sources = emptyMap(), dataSource = src)
const val TD = "Twelve Data XAU/USD"

fun main() {
    val base = t("2026-11-02 00:00:00")
    val candles = (0 until 150).map { k -> val o = 4000.0 + k * 0.1; CandleBar(sdf.format(Date(base + k * 15 * MIN)), o, o + 0.6, o - 0.4, o + 0.05) }
    val a0 = TechnicalEngine.analyze(candles, "15m", nowMs = t("2026-11-02 10:20:00"))
    fun an(bar: String, src: String) = a0.copy(lastUpdated = bar, feed = FeedStatus(src, t("2026-11-02 10:20:00"), 1, true, true, true, 0, 1))
    val r1000 = rec("R1000", t("2026-11-02 10:00:20"), "2026-11-02 10:00:00", GoldApiService.OANDA_SOURCE)
    // adjacent candle: the 10:15 analysis must not link to the 10:00 record
    check("10:15 analysis + only a 10:00 record -> UNLINKED", RecordLink.forAnalysis(listOf(r1000), an("2026-11-02 10:15:00", GoldApiService.OANDA_SOURCE)) == null)
    // provider switch both ways on the same candle
    val rOa = rec("ROA", t("2026-11-02 10:15:10"), "2026-11-02 10:15:00", GoldApiService.OANDA_SOURCE)
    val rTd = rec("RTD", t("2026-11-02 10:15:10"), "2026-11-02 10:15:00", TD)
    check("Twelve Data analysis + OANDA record -> UNLINKED", RecordLink.forAnalysis(listOf(rOa), an("2026-11-02 10:15:00", TD)) == null)
    check("OANDA analysis + Twelve Data record -> UNLINKED", RecordLink.forAnalysis(listOf(rTd), an("2026-11-02 10:15:00", GoldApiService.OANDA_SOURCE)) == null)
    check("exact match links", RecordLink.forAnalysis(listOf(r1000, rOa), an("2026-11-02 10:15:00", GoldApiService.OANDA_SOURCE))?.id == "ROA")
    check("other timeframe never links", RecordLink.forAnalysis(listOf(rOa.copy(interval = "5m")), an("2026-11-02 10:15:00", GoldApiService.OANDA_SOURCE)) == null)
    // AI snapshot id carries provider; the council of another provider/candle is not shown
    val sO = MarketSnapshotBuilder.build(an("2026-11-02 10:15:00", GoldApiService.OANDA_SOURCE)).id
    val sT = MarketSnapshotBuilder.build(an("2026-11-02 10:15:00", TD)).id
    check("snapshot ids differ by provider", sO != sT && sO.contains("-OANDA-") && sT.contains("-TWELVE_DATA-"), "$sO / $sT")
    check("prefix matches own snapshot only", sO.startsWith(RecordLink.snapshotPrefix(an("2026-11-02 10:15:00", GoldApiService.OANDA_SOURCE))) &&
        !sT.startsWith(RecordLink.snapshotPrefix(an("2026-11-02 10:15:00", GoldApiService.OANDA_SOURCE))))
    // one record per candle: a record from the last seconds of 10:10 (5m) must not block the 10:15 candle
    val late = rec("L", t("2026-11-02 10:14:50"), "2026-11-02 10:10:00", GoldApiService.OANDA_SOURCE, "5m")
    check("older candle's late record does not block the new candle", !RecordLink.sameCandle(late, "5m", "2026-11-02 10:15:00", t("2026-11-02 10:15:10"), 5))
    check("same candle still blocks", RecordLink.sameCandle(late, "5m", "2026-11-02 10:10:00", t("2026-11-02 10:14:55"), 5))
    check("source switch with an unreadable candle id still blocks within ~one candle", RecordLink.sameCandle(late.copy(barId = "x"), "5m", "2026-11-02 10:10:00", t("2026-11-02 10:15:00"), 5))
    check("a NEWER candle's record (late provider on an older candle) blocks the older one", RecordLink.sameCandle(late.copy(barId = "2026-11-02 10:15:00"), "5m", "2026-11-02 10:10:00", t("2026-11-02 10:15:20"), 5))
    val src = File("app/src/main/java/com/example/livegoldai")
    val vm = File(src, "ui/GoldViewModel.kt").readText()
    check("ViewModel: Brain + AI use RecordLink, council filtered by snapshot prefix", vm.contains("RecordLink.forAnalysis(st.records, a)") && vm.contains("RecordLink.forAnalysis(st.records, data)") &&
        vm.contains("aiOrchestrator.latest(currentInterval, com.example.livegoldai.data.RecordLink.snapshotPrefix(learned))") && !vm.contains("now - it.createdAt < 120_000L"))
    check("LearningCoordinator uses RecordLink.sameCandle", File(src, "data/LearningCoordinator.kt").readText().contains("RecordLink.sameCandle(it, interval, analysis.lastUpdated, now, ivMin)"))
    println("V35.3-E record links: $pass passed, $fail failed")
    if (fail > 0) kotlin.system.exitProcess(1)
}
