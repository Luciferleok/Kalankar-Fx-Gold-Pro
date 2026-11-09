import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/** V34.0 additional forensic audit on HEAD (f5d45f8). Read-only: prints DEFECT/OK per case. JVM + stubs, not Android. */
val res = LinkedHashMap<String, String>()
fun r(id: String, defect: Boolean, ev: String) { res[id] = (if (defect) "DEFECT" else "OK") + " — " + ev }

fun main() {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val now = sdf.parse("2026-10-28 10:22:10")!!.time; val iv = 300_000L
    val rnd = Random(3); var p = 4100.0
    val candles = (0 until 150).map { i -> val o = p; p -= 0.35 + (rnd.nextDouble() - 0.5) * 1.2; CandleBar(sdf.format(Date((now / iv) * iv - (149 - i) * iv)), o, maxOf(o, p) + 0.6, minOf(o, p) - 0.6, p) }
    val td = FeedStatus("Twelve Data XAU/USD", now - 5_000, 300, true, true, true, 0, 1)
    val empty = LedgerState(emptyList(), emptyMap(), emptyList())
    fun an(cs: List<CandleBar>, iv: String = "5m", f: FeedStatus = td) = TechnicalEngine.analyze(cs, iv).copy(feed = f, sourceState = SourceStateMachine.PRIMARY_ACTIVE)
    val dir = File(System.getProperty("java.io.tmpdir"), "kfx_v340b_" + System.nanoTime()).apply { mkdirs() }

    // ================= A. crash safety (current b30c0f0 store)
    val a1 = PlanStamp.stamp(an(candles), empty, now, "COLLECTING")
    val m = mutableMapOf<String, LevelFreeze.Frozen>(); LevelFreeze.apply(a1, m, now)
    val f = File(dir, "level_freeze_v1.tsv"); val st = LevelFreezeStore(f)
    st.save(m); val good = f.readText()
    // A1 move failure (destination replaced by a non-empty directory -> Files.move fails): last good file must survive
    val f2 = File(dir, "d2.tsv"); LevelFreezeStore(f2).save(m)
    val asDir = File(dir, "d3.tsv").apply { mkdirs(); File(this, "x").writeText("x") }
    val okMove = LevelFreezeStore(asDir).save(m)
    r("A1 failed replace returns false and deletes nothing", okMove || !asDir.isDirectory || dir.listFiles()!!.any { it.name.endsWith(".tmp") },
        "save() -> $okMove; destination untouched=${asDir.isDirectory}; temp files left=${dir.listFiles()!!.count { it.name.endsWith(".tmp") }}")
    // A2 interruption while writing / process death before commit: a stray temp file next to a good file
    File(dir, "level_freeze_v1.tsv123.tmp").writeText(LevelFreezeStore.HEADER + "\ngarbage")
    r("A2 stray/damaged temp file never read", st.load(now).size != 1, "load() reads only the committed file: ${st.load(now).size} window(s)")
    // A3 truncated file (power cut inside the old non-atomic path or disk corruption)
    f.writeText(good.substring(0, good.length - 30))
    val trunc = st.load(now)
    r("A3 truncated last record rejected (no wrong levels)", trunc.values.any { it.setup.entryPrice != a1.tradeSetup.entryPrice }, "loaded ${trunc.size} window(s) from a truncated file")
    f.writeText(good)
    // A4 durability: no fsync before the move
    val src = File("app/src/main/java/com/example/livegoldai/data/LevelFreezeStore.kt").readText()
    r("A4 data flushed to disk (fsync) before the replace", !src.contains(".sync()") && !src.contains("force(true)"),
        "no FileDescriptor.sync()/FileChannel.force: after a power loss right after the move the new file may be empty on some file systems (Android AtomicFile syncs)")

    // ================= B. save result ignored
    val vm = File("app/src/main/java/com/example/livegoldai/ui/GoldViewModel.kt").readText()
    val ignored = vm.contains("if (frozenLevels.keys != before) levelStore.save(frozenLevels)   // a window was added or dropped")
    r("B save() result ignored; screen still says 'fixed at' after a failed save", ignored,
        "GoldViewModel.kt loadData: return value discarded, no retry, no flag; after restart the window is re-frozen at a later price")

    // ================= C. 16-window cap evicts unexpired windows
    val cap = mutableMapOf<String, LevelFreeze.Frozen>()
    val ivs = listOf("1m", "2m", "3m", "4m", "5m", "10m", "15m", "30m", "1h")
    val feeds = listOf(td, td.copy(source = GoldApiService.OANDA_SOURCE))
    var first: GoldAnalysisResult? = null; var n = 0
    for (fd in feeds) for (i in ivs) {
        val s = PlanStamp.stamp(an(candles, i, fd), empty, now, "COLLECTING")
        if (LevelFreeze.key(s) == null) continue
        if (first == null) first = s
        LevelFreeze.apply(s, cap, now + n++)
    }
    val firstAlive = first != null && first!!.tradeSetup.expiresAtMs > now + n
    r("C 17+ live windows: an unexpired window is evicted", n > 16 && firstAlive && LevelFreeze.key(first!!) !in cap.keys,
        "$n windows across ${ivs.size} timeframes x 2 providers; first (${first?.interval}) still valid=$firstAlive, kept=${first?.let { LevelFreeze.key(it) in cap.keys }}")

    // ================= D. ledger boundaries vs displayed scenario
    val lc = LearningCoordinator(object : LedgerStore { override fun readAll() = emptyList<String>(); override fun append(lines: List<String>) {} }, { _, _ -> null })
    val br = LearningCoordinator::class.java.declaredMethods.first { it.name == "buildRecord" }.apply { isAccessible = true }
    fun record(a: GoldAnalysisResult, at: Long) = br.invoke(lc, a, a, a.interval, at, false, null, "", emptyList<LedgerRecord>()) as LedgerRecord  // V35.14: + recent records (8th arg)
    // recorder-first: the background recorder records at price P1; the screen freezes later in the same candle at P2
    val recA = an(candles)
    val rec = record(recA, now)
    val later = candles.dropLast(1) + candles.last().let { it.copy(close = it.close - 1.8, low = minOf(it.low, it.close - 1.8)) }
    val shown = LevelFreeze.apply(PlanStamp.stamp(an(later), LedgerState(listOf(rec), emptyMap(), emptyList()), now + 40_000, "COLLECTING"), mutableMapOf(), now + 40_000).tradeSetup
    val sell = rec.finalSignal == Signal.SELL
    val ledgerStop = if (sell) rec.price + rec.slDistance else rec.price - rec.slDistance
    val ledgerTp = if (sell) rec.price - rec.tpDistance else rec.price + rec.tpDistance
    r("D1 ledger stop/TP1 differ from the levels the user saw (recorder-first)", Math.abs(ledgerStop - shown.stopLoss) > 0.01 || Math.abs(ledgerTp - shown.takeProfit1) > 0.01,
        "ledger: from ${"%.2f".format(rec.price)} stop ${"%.2f".format(ledgerStop)} TP1 ${"%.2f".format(ledgerTp)}; shown: entry ${"%.2f".format(shown.entryPrice)} stop ${"%.2f".format(shown.stopLoss)} TP1 ${"%.2f".format(shown.takeProfit1)}")
    // same moment (UI-first): absolute stop/TP equal, but the ledger measures from the CURRENT price, not the shown pullback entry
    val same = LevelFreeze.apply(PlanStamp.stamp(recA, LedgerState(listOf(rec), emptyMap(), emptyList()), now, "COLLECTING"), mutableMapOf(), now).tradeSetup
    r("D2 same moment: ledger starts at current price, user sees a pullback entry", Math.abs(rec.price - same.entryPrice) > 0.01,
        "ledger start ${"%.2f".format(rec.price)} vs shown entry ${"%.2f".format(same.entryPrice)}; stop equal=${Math.abs(ledgerStop - same.stopLoss) < 0.01}, TP1 equal=${Math.abs(ledgerTp - same.takeProfit1) < 0.01}; a TP1 hit counts CORRECT even if the entry was never reached")
    // fallback: plan stop closer than 0.5 ATR -> ledger silently uses 1.5 ATR
    r("D3 ledger replaces a near stop/target by 1.5 ATR (not what was shown)", File("app/src/main/java/com/example/livegoldai/data/LearningCoordinator.kt").readText()
        .contains("val sl = if (planOk && ts.stopLoss > 0 && slSide >= 0.5 * atr) slSide else 1.5 * atr"), "LearningCoordinator.buildRecord")

    // ================= E. grant revalidation ignores levels
    val act = PlanStamp.stamp(recA, empty, now, "LIVE QUALIFIED").let { it.copy(presentation = PresentationBuilder.build(it, empty, "LIVE QUALIFIED", now)) }
    val g = ResearchGate.authorize(act, act.tradeSetup.snapshotId, act.interval, now).grant
    if (g != null) {
        val moved = act.copy(tradeSetup = act.tradeSetup.copy(entryPrice = act.tradeSetup.entryPrice + 3, stopLoss = act.tradeSetup.stopLoss + 3, takeProfit1 = act.tradeSetup.takeProfit1 + 3))
        r("E old grant stays valid after the levels changed (same snapshot)", ResearchGate.stillValid(g, moved, now + 1_000),
            "stillValid compares snapshot id + timeframe only; calculator would size with entry ${"%.2f".format(g.entry)} while the screen shows ${"%.2f".format(moved.tradeSetup.entryPrice)}")
    } else r("E grant", false, "NOT VERIFIED: fixture did not produce an ACTIVE grant")

    // ================= F. requote collisions
    val coll = LevelFreeze.requote("Pivot 4049.87 · SL 4049.87", mapOf("4049.87" to "4051.00"))
    r("F requote changes an unrelated equal number in the same text", coll == "Pivot 4051.00 · SL 4051.00",
        "only in synthetic text: the 4 real fields it touches (orderType, executionCommandEng, suggestedOrder, executionTactic*) contain plan levels only")

    // ================= G. overflow
    val std = PositionSizer.BrokerSpec(100.0, 0.01, 0.01, 50.0, 100.0, "USD", null)
    val big = PositionSizer.size(PositionSizer.Input("1e308", "50", "", 4000.0, 4010.0, 3985.0, 3970.0, 4000.0), std)
    val bigBudget = (big as? PositionSizer.Result.Ok)?.budget
    r("G1 huge balance -> Infinity budget shown", bigBudget != null && !bigBudget.isFinite(), "balance 1e308 x 50% -> budget $bigBudget (${big::class.simpleName})")
    val hugeContract = PositionSizer.size(PositionSizer.Input("5000", "1", "", 4000.0, 4010.0, 3985.0, 3970.0, 4000.0), std.copy(contractSize = 1e308))
    val inf = (hugeContract as? PositionSizer.Result.NoSafeSize)?.riskAtMinimum
    r("G2 huge contract size -> Infinity risk shown", inf != null && !inf.isFinite(), "contract 1e308 -> riskAtMinimum $inf (${hugeContract::class.simpleName})")
    val crash = try { PositionSizer.size(PositionSizer.Input("5000", "1", "", 4000.0, 4010.0, 3985.0, 3970.0, 4000.0), std.copy(volumeStep = 1e-320)); "no crash" } catch (e: Throwable) { "CRASH ${e::class.simpleName}" }
    r("G3 extreme step does not crash", crash.startsWith("CRASH"), crash)

    dir.deleteRecursively()
    for ((k, v) in res) println("$k\n    $v")
    println("DEFECTS: ${res.values.count { it.startsWith("DEFECT") }} of ${res.size}")
}
