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
    val oanda = td.copy(source = GoldApiService.OANDA_SOURCE)
    val empty = LedgerState(emptyList(), emptyMap(), emptyList())
    fun st(cs: List<CandleBar>, f: FeedStatus, at: Long) = PlanStamp.stamp(TechnicalEngine.analyze(cs, "5m").copy(feed = f, sourceState = SourceStateMachine.PRIMARY_ACTIVE), empty, at, "COLLECTING")
    val tdA = st(candles, td, now)
    check("fixture: a side to freeze", LevelFreeze.key(tdA) != null)

    // provider switch on the same candle: each feed keeps its own levels
    val store = mutableMapOf<String, LevelFreeze.Frozen>()
    val f1 = LevelFreeze.apply(tdA, store, now)
    val oaC = candles.map { it.copy(open = it.open - 2, high = it.high - 2, low = it.low - 2, close = it.close - 2) }
    val oaA = st(oaC, oanda, now + 10_000)
    val f2 = LevelFreeze.apply(oaA, store, now + 10_000)
    check("key carries provider", LevelFreeze.key(tdA)!!.contains("Twelve Data") && LevelFreeze.key(oaA)!!.contains("OANDA"))
    check("OANDA gets its own levels, not TD's", f2.tradeSetup.entryPrice == oaA.tradeSetup.entryPrice && f2.tradeSetup.entryPrice != f1.tradeSetup.entryPrice)
    check("OANDA levels fixed now", f2.tradeSetup.levelsFixedAtMs == now + 10_000)
    // back to TD on the same candle: TD's kept levels come back
    val moved = candles.dropLast(1) + candles.last().let { it.copy(close = it.close - 1.0, low = minOf(it.low, it.close - 1.0)) }
    val f3 = LevelFreeze.apply(st(moved, td, now + 20_000), store, now + 20_000)
    check("back to TD: TD levels from the first freeze", f3.tradeSetup.entryPrice == f1.tradeSetup.entryPrice && f3.tradeSetup.levelsFixedAtMs == now)

    // slow old load after the new candle: never evicts the current window, never stored
    val nextBar = candles.drop(1) + CandleBar(sdf.format(Date((now / iv) * iv + iv)), candles.last().close, candles.last().close + 0.4, candles.last().close - 0.9, candles.last().close - 0.5)
    val s2 = mutableMapOf<String, LevelFreeze.Frozen>()
    val newA = st(nextBar, td.copy(fetchedAtMs = now + iv), now + iv)
    LevelFreeze.apply(newA, s2, now + iv)
    val oldOut = LevelFreeze.apply(tdA, s2, now + iv + 1_000)
    check("current window kept", LevelFreeze.key(newA) in s2.keys)
    check("late old candle not stored", LevelFreeze.key(tdA) !in s2.keys)
    check("late old candle shown unfrozen (screen write dropped anyway)", oldOut.tradeSetup.levelsFixedAtMs == 0L)
    // a newer candle still replaces an older one
    val s3 = mutableMapOf<String, LevelFreeze.Frozen>(); LevelFreeze.apply(tdA, s3, now); LevelFreeze.apply(newA, s3, now + iv)
    check("newer candle evicts older", LevelFreeze.key(tdA) !in s3.keys && LevelFreeze.key(newA) in s3.keys)
    // different month in the snapshot id (old prefix bug): 10m window must not be dropped by a 5m one
    val s4 = mutableMapOf<String, LevelFreeze.Frozen>()
    val tenM = PlanStamp.stamp(TechnicalEngine.analyze(candles, "10m").copy(feed = td, sourceState = SourceStateMachine.PRIMARY_ACTIVE), empty, now, "COLLECTING")
    if (LevelFreeze.key(tenM) != null) {
        LevelFreeze.apply(tenM, s4, now); LevelFreeze.apply(newA, s4, now + iv)
        check("other timeframe untouched", LevelFreeze.key(tenM) in s4.keys)
    }
    check("store bounded", run { val m = mutableMapOf<String, LevelFreeze.Frozen>(); for (k in 0 until 40) LevelFreeze.apply(tdA.copy(tradeSetup = tdA.tradeSetup.copy(snapshotId = "S-x$k")), m, now + k); m.size <= LevelFreeze.CAP })

    // ViewModel: a stale load does not touch the map
    val vm = File("app/src/main/java/com/example/livegoldai/ui/GoldViewModel.kt").readText()
    check("VM: freeze only for the current load", vm.contains("val stamped = if (!isCurrentLoad(ticket)) stamped0 else"))

    println("R.4-c1: $pass passed, $fail failed")
    if (fail > 0) System.exit(1)
}
