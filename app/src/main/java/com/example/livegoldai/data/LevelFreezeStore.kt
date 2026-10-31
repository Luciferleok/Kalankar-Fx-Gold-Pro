package com.example.livegoldai.data

import com.example.livegoldai.model.Signal
import com.example.livegoldai.model.TradeSetup
import java.io.File
import java.util.Base64

/**
 * V33.1R.4-d: the kept plan levels survive an app restart or process death.
 *
 * Before: LevelFreeze lived in a map in memory only, so after a restart inside a window the first levels seen after the
 * restart were kept instead (levels changed for the same forecast window).
 *
 * A small separate file in the app's private storage ("level_freeze_v1.tsv", at most 16 windows). It is NOT the
 * prediction ledger: nothing in the ledger is read or written here. Each line stores the exact numbers that were
 * shown (doubles as written by Kotlin, so they read back bit-identical) and the texts, Base64-encoded.
 * Writes go to a temp file first and are then renamed, so a crash during a write leaves the old file intact.
 * A damaged or unknown file is ignored (the app starts with an empty map) and never crashes the app.
 */
class LevelFreezeStore(private val file: File) {

    companion object {
        private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()
        private fun lockFor(f: File): Any = locks.getOrPut(f.absolutePath) { Any() }
        const val HEADER = "KFX-LEVEL-FREEZE v1"
        private const val FIELDS = 21
        private fun e(s: String) = Base64.getEncoder().encodeToString(s.toByteArray(Charsets.UTF_8))
        private fun d(s: String) = String(Base64.getDecoder().decode(s), Charsets.UTF_8)
        private fun n(x: Double?) = x?.toString() ?: "-"
        private fun nd(s: String) = if (s == "-") null else s.toDouble()

        fun encode(f: LevelFreeze.Frozen): String {
            val s = f.setup
            return listOf(e(f.key), e(f.interval), e(f.barId), f.fixedAtMs.toString(), n(f.botSlPips), n(f.botTpPips),
                s.signal.name, e(s.snapshotId), s.expiresAtMs.toString(),
                s.entryPrice.toString(), s.stopLoss.toString(), s.takeProfit1.toString(), s.takeProfit2.toString(),
                s.stopLossPips.toString(), s.takeProfit1Pips.toString(), s.takeProfit2Pips.toString(),
                e(s.strategyNote), e(s.strategyNoteHindi), e(s.strategyNoteMarathi), e(s.dataSource), e(s.instrument)
            ).joinToString("\t")
        }

        /** null for a line that is not a complete, valid record */
        fun decode(line: String): LevelFreeze.Frozen? = try {
            val p = line.split('\t')
            if (p.size != FIELDS) null else {
                val setup = TradeSetup(
                    signal = Signal.valueOf(p[6]), entryPrice = p[9].toDouble(), stopLoss = p[10].toDouble(),
                    stopLossPips = p[13].toDouble(), takeProfit1 = p[11].toDouble(), takeProfit1Pips = p[14].toDouble(),
                    takeProfit2 = p[12].toDouble(), takeProfit2Pips = p[15].toDouble(), riskRewardRatio = "",
                    confidencePercent = 0, strategyNote = d(p[16]), strategyNoteHindi = d(p[17]), strategyNoteMarathi = d(p[18]),
                    atrPips = 0.0, snapshotId = d(p[7]), expiresAtMs = p[8].toLong(), dataSource = d(p[19]), instrument = d(p[20])
                )
                val ok = listOf(setup.entryPrice, setup.stopLoss, setup.takeProfit1).all { it.isFinite() && it > 0.0 }
                if (!ok) null else LevelFreeze.Frozen(d(p[0]), setup, p[3].toLong(), nd(p[4]), nd(p[5]), d(p[1]), d(p[2]))
            }
        } catch (_: Exception) { null }
    }

    /**
     * Reads the file. Windows whose fixed end time has passed (or that are older than [maxAgeMs] when no end time is
     * stamped) are dropped. Any error -> empty map.
     */
    fun load(now: Long, maxAgeMs: Long = 48 * 3_600_000L): MutableMap<String, LevelFreeze.Frozen> {
        val out = LinkedHashMap<String, LevelFreeze.Frozen>()
        try {
            if (!file.exists()) return out
            val lines = file.readLines(Charsets.UTF_8)
            if (lines.firstOrNull() != HEADER) return out
            for (l in lines.drop(1)) {
                val f = decode(l) ?: continue
                val exp = f.setup.expiresAtMs
                val alive = if (exp > 0L) now < exp else now - f.fixedAtMs < maxAgeMs
                if (alive) out[f.key] = f
            }
        } catch (_: Exception) { out.clear() }
        return out
    }

    /**
     * Atomic replace of the whole file. false when it could not be written (the app keeps working from memory).
     * V34.0: one lock per file path and a unique temp file per write; the old file is never deleted first. Before, two
     * writers shared "<file>.tmp" and a failed rename deleted the old file, so concurrent saves could lose the file.
     */
    fun save(store: Map<String, LevelFreeze.Frozen>): Boolean = synchronized(lockFor(file)) {
        var tmp: File? = null
        try {
            file.parentFile?.mkdirs()
            tmp = File.createTempFile(file.name, ".tmp", file.parentFile ?: File("."))
            tmp.writeText((listOf(HEADER) + store.values.map { encode(it) }).joinToString("\n") + "\n", Charsets.UTF_8)
            try {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (_: Exception) {
            try { tmp?.delete() } catch (_: Exception) { }
            false
        }
    }
}
