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
    val r = Random(3); var p = 4100.0
    val candles = (0 until 150).map { i -> val o = p; p -= 0.35 + (r.nextDouble() - 0.5) * 1.2; CandleBar(sdf.format(Date((now / iv) * iv - (149 - i) * iv)), o, maxOf(o, p) + 0.6, minOf(o, p) - 0.6, p) }
    val td = FeedStatus("Twelve Data XAU/USD", now - 5_000, 300, true, true, true, 0, 1)
    val empty = LedgerState(emptyList(), emptyMap(), emptyList())
    fun st(cs: List<CandleBar>, at: Long) = PlanStamp.stamp(TechnicalEngine.analyze(cs, "5m").copy(feed = td.copy(fetchedAtMs = at - 5_000), sourceState = SourceStateMachine.PRIMARY_ACTIVE), empty, at, "COLLECTING")
    val dir = File(System.getProperty("java.io.tmpdir"), "kfx_r4d_" + System.nanoTime()).apply { mkdirs() }
    val file = File(dir, "level_freeze_v1.tsv")

    // ---- run 1: first refresh freezes and saves
    val a1 = st(candles, now)
    val store1 = LevelFreezeStore(file)
    val m1 = store1.load(now)
    check("fresh install: empty, no crash", m1.isEmpty())
    val shown1 = LevelFreeze.apply(a1, m1, now); store1.save(m1)
    check("file written", file.exists() && file.readLines().first() == LevelFreezeStore.HEADER && file.readLines().size == 2)

    // ---- restart (process death): new store, new map, price moved inside the same window
    val moved = candles.dropLast(1) + candles.last().let { it.copy(close = it.close - 1.5, low = minOf(it.low, it.close - 1.5)) }
    val a2 = st(moved, now + 60_000)
    check("fixture: engine levels moved", a2.tradeSetup.entryPrice != a1.tradeSetup.entryPrice && a2.tradeSetup.snapshotId == a1.tradeSetup.snapshotId)
    val m2 = LevelFreezeStore(file).load(now + 60_000)
    val shown2 = LevelFreeze.apply(a2, m2, now + 60_000)
    val t1 = shown1.tradeSetup; val t2 = shown2.tradeSetup
    check("after restart: identical entry/SL/TP1/TP2 (bit-exact)", t2.entryPrice == t1.entryPrice && t2.stopLoss == t1.stopLoss && t2.takeProfit1 == t1.takeProfit1 && t2.takeProfit2 == t1.takeProfit2)
    check("after restart: pips + fixed-at time kept", t2.stopLossPips == t1.stopLossPips && t2.takeProfit1Pips == t1.takeProfit1Pips && t2.levelsFixedAtMs == now)
    check("after restart: notes kept", t2.strategyNote == t1.strategyNote && t2.strategyNoteHindi == t1.strategyNoteHindi)
    check("after restart: R:R from kept levels", t2.riskRewardRatio == RiskMath.label(t1.entryPrice, t1.stopLoss, t1.takeProfit1, t1.takeProfit2, t1.signal))
    check("after restart: bot pips kept", shown2.multiBotEnsemble?.allBots?.map { it.stopLossPips } == shown1.multiBotEnsemble?.allBots?.map { it.stopLossPips })
    check("after restart: live price not frozen", shown2.currentPrice == a2.currentPrice)

    // ---- expiry: after the window's end the record is not loaded
    val exp = a1.tradeSetup.expiresAtMs
    check("window has an end time", exp > now)
    check("expired window dropped on load", LevelFreezeStore(file).load(exp + 1).isEmpty())
    // ---- a record without end time is dropped after 48 h
    val noExp = LevelFreeze.Frozen("k|SELL|x|y", a1.tradeSetup.copy(expiresAtMs = 0L), now, null, null, "5m", "2026-10-28 10:20:00")
    val f2 = File(dir, "b.tsv"); LevelFreezeStore(f2).save(mapOf(noExp.key to noExp))
    check("no end time: kept within 48 h", LevelFreezeStore(f2).load(now + 3_600_000L).size == 1)
    check("no end time: dropped after 48 h", LevelFreezeStore(f2).load(now + 49 * 3_600_000L).isEmpty())

    // ---- damaged files never crash and never yield bad levels
    val bad = File(dir, "bad.tsv")
    bad.writeText("garbage\n1\t2\n"); check("unknown header -> empty", LevelFreezeStore(bad).load(now).isEmpty())
    bad.writeText(LevelFreezeStore.HEADER + "\nnot\ta\trecord\n" + file.readLines()[1].replace("\t", "\tX", ignoreCase = false).take(40) + "\n")
    check("broken lines skipped", LevelFreezeStore(bad).load(now).isEmpty())
    val line = file.readLines()[1].split('\t').toMutableList(); line[9] = "NaN"
    bad.writeText(LevelFreezeStore.HEADER + "\n" + line.joinToString("\t") + "\n")
    check("NaN entry rejected", LevelFreezeStore(bad).load(now).isEmpty())
    check("missing file -> empty", LevelFreezeStore(File(dir, "none.tsv")).load(now).isEmpty())
    check("unwritable path -> false, no crash", !LevelFreezeStore(File("/proc/kfx_cannot_write/x.tsv")).save(m1))
    // ---- round trip of every field incl. tabs/newlines/unicode in texts
    val weird = LevelFreeze.Frozen("S-5m-x|BUY|Twelve Data XAU/USD|XAU/USD · TWELVE DATA", a1.tradeSetup.copy(strategyNote = "tab\there\nnewline ₹ हिंदी", entryPrice = 4012.345678901234), now, 156.5, null, "5m", "2026-10-28 10:20:00")
    val back = LevelFreezeStore.decode(LevelFreezeStore.encode(weird))
    check("encode/decode exact", back != null && back.key == weird.key && back.setup.strategyNote == weird.setup.strategyNote &&
        back.setup.entryPrice == weird.setup.entryPrice && back.botSlPips == 156.5 && back.botTpPips == null && back.barId == weird.barId)
    // ---- atomic save leaves no temp file
    check("no temp file left", !File(file.path + ".tmp").exists())

    // ---- ViewModel wiring
    val vm = File("app/src/main/java/com/example/livegoldai/ui/GoldViewModel.kt").readText()
    val initAt = vm.indexOf("\n    init {")
    check("store + map declared before init", vm.indexOf("private val levelStore") in 1 until initAt && vm.indexOf("private val frozenLevels") in 1 until initAt)
    check("map loaded from the file", vm.contains("levelStore.load(System.currentTimeMillis())"))
    check("saved when a window is added or dropped", vm.contains("if (frozenLevels.keys != before || freezeSaveFailed) freezeSaveFailed = !levelStore.save(frozenLevels)"))
    check("not the ledger file", !vm.contains("\"level_freeze_v1.tsv\"").not() && !File("app/src/main/java/com/example/livegoldai/data/LevelFreezeStore.kt").readText().contains("PredictionLedger."))

    dir.deleteRecursively()
    println("R.4-d: $pass passed, $fail failed")
    if (fail > 0) System.exit(1)
}
