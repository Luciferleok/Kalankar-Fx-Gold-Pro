import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean) { if (ok) pass++ else { fail++; println("FAIL: $name") } }

fun main() {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val now = sdf.parse("2026-10-28 10:22:10")!!.time; val iv = 300_000L
    val r = Random(3); var p = 4100.0
    val candles = (0 until 150).map { i -> val o = p; p -= 0.35 + (r.nextDouble() - 0.5) * 1.2; CandleBar(sdf.format(Date((now / iv) * iv - (149 - i) * iv)), o, maxOf(o, p) + 0.6, minOf(o, p) - 0.6, p) }
    val td = FeedStatus("Twelve Data XAU/USD", now - 5_000, 300, true, true, true, 0, 1)
    val oanda = td.copy(source = GoldApiService.OANDA_SOURCE)
    fun an(f: FeedStatus) = TechnicalEngine.analyze(candles, "5m").copy(feed = f, sourceState = SourceStateMachine.PRIMARY_ACTIVE)
    val base = an(td)
    fun rec(id: String, src: String, snap: String, created: Long = now - 120_000) = LedgerRecord(id = id, createdAt = created, expiresAt = created + 25 * 60_000L,
        interval = "5m", horizonMin = 25, barId = base.lastUpdated, price = base.currentPrice + 7.0, rawSignal = base.overallSignal, finalSignal = base.overallSignal,
        confidence = 70, atr = 1.0, slDistance = 3.0, regime = "", session = "", newsActive = false, modelVersion = "", appliedFilter = "",
        sources = emptyMap(), dataSource = src, snapshotId = snap, tpDistance = 3.0)
    val tdRec = rec("PTD", "Twelve Data XAU/USD", "S-5m-TD")
    val oaRec = rec("POA", GoldApiService.OANDA_SOURCE, "S-5m-OA", now - 60_000)
    fun ls(vararg rs: LedgerRecord) = LedgerState(rs.toList(), emptyMap(), emptyList())

    // OANDA analysis, only a Twelve Data record exists for the candle
    val o1 = PlanStamp.stamp(an(oanda), ls(tdRec), now, "COLLECTING").tradeSetup
    check("OANDA never inherits the TD snapshot id", o1.snapshotId != "S-5m-TD")
    check("OANDA never inherits the TD expiry", o1.expiresAtMs != tdRec.expiresAt)
    check("OANDA expiry = own candle open + horizon", o1.expiresAtMs == ShadowLive.barMs(base.lastUpdated)!! + PlanStamp.horizonMin("5m") * 60_000L)
    check("not silent: plan says the candle is recorded from TD", o1.recordedByOtherProvider.contains("TWELVE DATA"))
    // OANDA analysis with its own OANDA record
    val o2 = PlanStamp.stamp(an(oanda), ls(tdRec, oaRec), now, "COLLECTING").tradeSetup
    check("OANDA uses its own record", o2.snapshotId == "S-5m-OA" && o2.expiresAtMs == oaRec.expiresAt && o2.recordedByOtherProvider.isEmpty())
    // TD analysis keeps working exactly as before
    val t1 = PlanStamp.stamp(an(td), ls(tdRec, oaRec), now, "COLLECTING").tradeSetup
    check("TD uses the TD record", t1.snapshotId == "S-5m-TD" && t1.expiresAtMs == tdRec.expiresAt && t1.recordedByOtherProvider.isEmpty())
    val t0 = PlanStamp.stamp(an(td), ls(), now, "COLLECTING").tradeSetup
    check("no record: unchanged default id", t0.snapshotId == "S-5m-${base.lastUpdated.replace(" ", "T")}" && t0.recordedByOtherProvider.isEmpty())
    // legacy record without a data source is not claimed by any provider, but is reported
    val legacy = rec("PLG", "", "S-5m-LEGACY")
    val l1 = PlanStamp.stamp(an(td), ls(legacy), now, "COLLECTING").tradeSetup
    check("legacy record not used", l1.snapshotId != "S-5m-LEGACY" && l1.recordedByOtherProvider.isNotEmpty())
    // the stop check uses only the same provider's record price
    val farStop = rec("PTD2", "Twelve Data XAU/USD", "S-5m-TD2").copy(price = base.currentPrice, slDistance = 0.01)
    val oStop = PlanStamp.stamp(an(oanda), ls(farStop), now, "COLLECTING").tradeSetup
    check("TD record's stop is not applied to an OANDA plan", oStop.planStatus != PlanStamp.STOPPED)

    // plan text shows the note in all languages, and only when needed
    val lvO = ResearchScenario.of(PlanStamp.stamp(an(oanda), ls(tdRec), now, "COLLECTING"), now)
    if (lvO != null) for (lang in AppLanguage.values()) check("note in steps $lang", ResearchScenario.steps(lvO, lang).lines().size == 6 && ResearchScenario.steps(lvO, lang).contains("TWELVE DATA"))
    val lvT = ResearchScenario.of(PlanStamp.stamp(an(td), ls(tdRec), now, "COLLECTING"), now)
    if (lvT != null) check("no note when own record", ResearchScenario.steps(lvT, AppLanguage.ENGLISH).lines().size == 5)

    // ledger recording untouched: one record per candle across providers (no double counting), history not rewritten
    val lc = File("app/src/main/java/com/example/livegoldai/data/LearningCoordinator.kt").readText()
    check("one record per candle rule unchanged (V35.3-E: exact-candle rule, still one record per candle across providers)", lc.contains("RecordLink.sameCandle(it, interval, analysis.lastUpdated, now, ivMin)"))
    val pl = File("app/src/main/java/com/example/livegoldai/data/PredictionLedger.kt").readText()
    check("ledger record: old fields kept, new plan fields optional (V34.2)", pl.contains("val tpDistance: Double = 0.0,") && pl.contains("val planMethod: String = \"\""))

    println("V34.1a: $pass passed, $fail failed")
    if (fail > 0) System.exit(1)
}
