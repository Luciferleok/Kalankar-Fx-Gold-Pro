import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * V33.1R.4-c pre-audit on cd616fd. Each case prints DEFECT (reproduced) or OK. Exit code = number of defects reproduced.
 * Nothing here changes source, ledger or rules.
 */
val results = LinkedHashMap<String, String>()
fun defect(id: String, reproduced: Boolean, evidence: String) { results[id] = (if (reproduced) "DEFECT" else "OK") + " — " + evidence }

fun main() {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val now = sdf.parse("2026-10-28 10:22:10")!!.time      // Wednesday, market open
    val iv = 300_000L
    val r = Random(3); var p = 4100.0
    val candles = (0 until 150).map { i -> val o = p; p -= 0.35 + (r.nextDouble() - 0.5) * 1.2; CandleBar(sdf.format(Date((now / iv) * iv - (149 - i) * iv)), o, maxOf(o, p) + 0.6, minOf(o, p) - 0.6, p) }
    val td = FeedStatus("Twelve Data XAU/USD", now - 5_000, 300, true, true, true, 0, 1)
    val oanda = td.copy(source = GoldApiService.OANDA_SOURCE)
    fun analyze(cs: List<CandleBar>, feed: FeedStatus) = TechnicalEngine.analyze(cs, "5m").copy(feed = feed, sourceState = SourceStateMachine.PRIMARY_ACTIVE)
    val empty = LedgerState(emptyList(), emptyMap(), emptyList())

    // ================= 1. provider-blind ledger matching
    val tdA = analyze(candles, td)
    val tdRec = LedgerRecord(id = "PTD5M", createdAt = now - 120_000, expiresAt = now - 120_000 + 25 * 60_000L, interval = "5m", horizonMin = 25,
        barId = tdA.lastUpdated, price = tdA.currentPrice + 7.0, rawSignal = tdA.overallSignal, finalSignal = tdA.overallSignal, confidence = 70,
        atr = 1.0, slDistance = 3.0, regime = "", session = "", newsActive = false, modelVersion = "", appliedFilter = "", sources = emptyMap(),
        dataSource = "Twelve Data XAU/USD", snapshotId = "S-5m-TD-RECORD", tpDistance = 3.0)
    val st = LedgerState(listOf(tdRec), emptyMap(), emptyList())
    val oaStamped = PlanStamp.stamp(analyze(candles, oanda), st, now, "COLLECTING")
    defect("1a PlanStamp matches a Twelve Data record to an OANDA analysis",
        oaStamped.tradeSetup.dataSource == GoldApiService.OANDA_SOURCE && oaStamped.tradeSetup.snapshotId == "S-5m-TD-RECORD",
        "OANDA analysis got snapshotId=${oaStamped.tradeSetup.snapshotId}, expiry=${oaStamped.tradeSetup.expiresAtMs == tdRec.expiresAt} from the TD record (PlanIntegrity.kt stamp(): match by interval+barId only)")
    val lc = File("app/src/main/java/com/example/livegoldai/data/LearningCoordinator.kt").readText()
    val existingLine = lc.lines().indexOfFirst { it.contains("val existing = state.records.lastOrNull {") } + 1
    val providerBlindRecord = lc.contains("it.interval == interval && (it.barId == analysis.lastUpdated || now - it.createdAt < ivMin * 60_000L * 9 / 10)")
    defect("1b LearningCoordinator one-record-per-candle ignores provider",
        providerBlindRecord, "LearningCoordinator.kt:$existingLine 'existing' = interval && (barId || <0.9 candle): after a TD->OANDA switch the OANDA forecast is NOT recorded (no duplicate, but the shown OANDA plan has no record of its own)")
    defect("1c duplicate counting on a source switch", false,
        "not reproduced: the same rule allows only one record per interval per candle, whichever provider comes first")

    // ================= 2. LevelFreeze
    val tdStamped = PlanStamp.stamp(tdA, empty, now, "COLLECTING")
    val store = mutableMapOf<String, LevelFreeze.Frozen>()
    val f1 = LevelFreeze.apply(tdStamped, store, now)
    // OANDA prices 2.00 lower on the same candle
    val oaCandles = candles.map { it.copy(open = it.open - 2, high = it.high - 2, low = it.low - 2, close = it.close - 2) }
    val oaFresh = PlanStamp.stamp(analyze(oaCandles, oanda), empty, now + 10_000, "COLLECTING")
    val f2 = LevelFreeze.apply(oaFresh, store, now + 10_000)
    defect("2a frozen Twelve Data levels survive a switch to OANDA",
        oaFresh.tradeSetup.snapshotId == tdStamped.tradeSetup.snapshotId && f2.tradeSetup.entryPrice == f1.tradeSetup.entryPrice && f2.tradeSetup.entryPrice != oaFresh.tradeSetup.entryPrice,
        "key '${LevelFreeze.key(tdStamped)}' has no provider/instrument; OANDA shows TD entry ${"%.2f".format(f2.tradeSetup.entryPrice)} instead of its own ${"%.2f".format(oaFresh.tradeSetup.entryPrice)}")
    // restart: a new empty map, price moved 1.50 inside the same window
    val moved = candles.dropLast(1) + candles.last().let { it.copy(close = it.close - 1.5, low = minOf(it.low, it.close - 1.5)) }
    val afterRestart = LevelFreeze.apply(PlanStamp.stamp(analyze(moved, td), empty, now + 30_000, "COLLECTING"), mutableMapOf(), now + 30_000)
    defect("2b levels change after an app restart inside the same window",
        afterRestart.tradeSetup.snapshotId == f1.tradeSetup.snapshotId && afterRestart.tradeSetup.entryPrice != f1.tradeSetup.entryPrice,
        "in-memory map (GoldViewModel.frozenLevels): before ${"%.2f".format(f1.tradeSetup.entryPrice)}, after restart ${"%.2f".format(afterRestart.tradeSetup.entryPrice)}")
    // stale async load: the NEW candle finishes first, the OLD candle's load finishes after it
    val nextBar = candles.drop(1) + CandleBar(sdf.format(Date((now / iv) * iv + iv)), candles.last().close, candles.last().close + 0.4, candles.last().close - 0.9, candles.last().close - 0.5)
    val s3 = mutableMapOf<String, LevelFreeze.Frozen>()
    val newA = PlanStamp.stamp(analyze(nextBar, td.copy(fetchedAtMs = now + iv)), empty, now + iv, "COLLECTING")
    val newKeyed = LevelFreeze.key(newA) != null
    LevelFreeze.apply(newA, s3, now + iv)                          // new load, first
    val newKey = LevelFreeze.key(newA)
    LevelFreeze.apply(tdStamped, s3, now + iv + 1_000)             // old load, finishes later (its screen write is dropped by RequestGate)
    defect("2c an old async load still edits the freeze map and evicts the current window",
        newKeyed && newKey !in s3.keys,
        "LevelFreeze.apply runs inside loadData's IO block BEFORE the RequestGate check (GoldViewModel.kt loadData); its pruning removed key $newKey; next refresh re-freezes the current window at a later price")

    // ================= 3. PositionSizer volume grid anchored at the minimum
    val grid = PositionSizer.BrokerSpec(100.0, 0.05, 0.02, 50.0, null, "USD", null)    // valid volumes: 0.05, 0.07, 0.09 ...
    val g = PositionSizer.size(PositionSizer.Input("8000", "1", "", 4000.0, 4010.0, 3985.0, 3970.0, 4000.0), grid)   // budget 80 -> raw 0.08
    val vol = (g as? PositionSizer.Result.Ok)?.volume
    val onGrid = vol != null && Math.abs(((vol - 0.05) / 0.02) - Math.round((vol - 0.05) / 0.02)) < 1e-9
    defect("3 volume not on a min-anchored broker grid (min 0.05, step 0.02)", vol != null && !onGrid,
        "PositionSizer.floorToStep is zero-anchored: returned $vol; a min+N*step broker accepts 0.07 (still under budget)")

    // ================= 4. ACTIVE without a separate user authorization
    val act = PlanStamp.stamp(tdA, empty, now, "LIVE QUALIFIED")
    defect("4 Shadow Lab LIVE QUALIFIED makes a plan ACTIVE with no user switch",
        act.tradeSetup.planStatus == PlanStamp.ACTIVE,
        "PlanIntegrity.kt stamp(): researchStatus == \"LIVE QUALIFIED\" + Twelve Data + fresh -> ACTIVE; ShadowLive.kt sets LIVE QUALIFIED automatically; no user-authorization field exists (POLICY DECISION, not a code bug)")

    // ================= 5. freshness from fetch time, not quote time
    val noNewBar = candles.dropLast(1)                       // newest candle opened 2 intervals ago: no tick for >= 1 full candle
    val stale = analyze(noNewBar, td.copy(fetchedAtMs = now - 3_000))
    val fr = Freshness.assess(stale, now)
    val newestAgeMin = (now - ShadowLive.barMs(stale.lastUpdated)!!) / 60_000.0
    defect("5 a quote with no new candle for a full interval is LIVE because the HTTP fetch was 3 s ago",
        fr == Freshness.LIVE,
        "Freshness.assess (PlanIntegrity.kt) -> $fr; newest candle opened ${"%.1f".format(newestAgeMin)} min ago on a 5m chart; FeedStatus has no source quote time field")

    for ((k, v) in results) println("$k\n    $v")
    val n = results.values.count { it.startsWith("DEFECT") }
    println("REPRODUCED DEFECTS: $n of ${results.size}")
}
