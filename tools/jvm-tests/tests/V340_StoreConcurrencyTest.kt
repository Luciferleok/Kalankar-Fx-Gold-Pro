import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File
import java.util.concurrent.*

fun main() {
    val dir = File(System.getProperty("java.io.tmpdir"), "kfx_v340_" + System.nanoTime()).apply { mkdirs() }
    val file = File(dir, "level_freeze_v1.tsv")
    fun fr(i: Int) = LevelFreeze.Frozen("S-5m-$i|SELL|TD|X", TradeSetup(Signal.SELL, 4000.0 + i, 4010.0 + i, 100.0, 3985.0, 150.0, 3970.0, 300.0, "", 0, "note $i", atrPips = 0.0, snapshotId = "S-5m-$i", expiresAtMs = Long.MAX_VALUE / 2), 1L, null, null, "5m", "b$i")
    // A: unsynchronized concurrent saves straight on the store (no caller lock)
    val pool = Executors.newFixedThreadPool(8); var bad = 0; var failedSaves = 0
    repeat(200) { round ->
        val futures = (0 until 8).map { t -> pool.submit<Boolean> { LevelFreezeStore(file).save((0..(t % 4)).associate { fr(it).key to fr(it) }) } }
        futures.forEach { if (!it.get()) failedSaves++ }
        val loaded = LevelFreezeStore(file).load(0L)
        val lines = if (file.exists()) file.readLines().size - 1 else -1
        if (loaded.size != lines || loaded.size !in 1..4) bad++
    }
    pool.shutdown()
    println("A unsynchronized: rounds with an inconsistent file = $bad / 200, failed saves = $failedSaves")
    // B: the ViewModel pattern (synchronized on one map)
    val map = mutableMapOf<String, LevelFreeze.Frozen>(); val store = LevelFreezeStore(File(dir, "b.tsv")); var badB = 0
    val pool2 = Executors.newFixedThreadPool(8)
    (0 until 2000).map { i -> pool2.submit { synchronized(map) { map[fr(i % 16).key] = fr(i % 16); store.save(map) } } }.forEach { it.get() }
    pool2.shutdown()
    val lb = store.load(0L); if (lb.size != 16) badB++
    println("B synchronized (ViewModel pattern): entries after 2000 concurrent updates = ${lb.size} (expected 16)")
    val leftovers = dir.listFiles()!!.count { it.name.endsWith(".tmp") }
    println("temp files left: $leftovers")
    dir.deleteRecursively()
    if (bad > 0 || failedSaves > 0 || lb.size != 16 || leftovers > 0) { println("V34.0 store concurrency: FAIL"); kotlin.system.exitProcess(1) }
    println("V34.0 store concurrency: PASS")
}
