package com.example.livegoldai.data

import java.io.File

/**
 * V35.7.1 (owner, 4 Nov 2026: reinstall brought the old 977 / 705 back; "fresh install = zero").
 *
 * The backup rules (res/xml) stop NEW backups from carrying the prediction history, but a backup made by an older
 * version can still be restored once. This guard runs once per installation, before the ledger is opened:
 *  - FRESH INSTALL (Android's lastUpdateTime == firstInstallTime) whose ledger holds calls made BEFORE this install
 *    -> those files came from a backup: they are MOVED (never deleted) to no_backup/restored_<time>/ and the app starts at zero;
 *  - an UPDATE (lastUpdateTime > firstInstallTime) never touches anything: history survives every update;
 *  - afterwards a marker in no_backup/ (never backed up) says the check was done.
 */
object RestoreGuard {
    val HISTORY_FILES = listOf("prediction_ledger.jsonl", "shadow_ledger.jsonl", "shadow_models.json", "level_freeze_v1.tsv")
    const val MARKER = "install_checked_v1"
    /** a call made this long before the install time cannot have been made by this installation */
    const val SLACK_MS = 10 * 60_000L

    data class Outcome(val action: String, val moved: List<String>, val oldestCallMs: Long)

    fun check(filesDir: File, noBackupDir: File, firstInstallMs: Long, lastUpdateMs: Long, now: Long): Outcome {
        val marker = File(noBackupDir, MARKER)
        if (marker.exists()) return Outcome("ALREADY_CHECKED", emptyList(), 0L)
        if (firstInstallMs <= 0L) return Outcome("UNKNOWN_INSTALL_TIME", emptyList(), 0L)   // try again next start
        val fresh = lastUpdateMs <= firstInstallMs
        val ledger = File(filesDir, HISTORY_FILES[0])
        val oldest = if (ledger.exists()) try {
            PredictionLedger.load(FileLedgerStore(ledger)).records.minOfOrNull { it.createdAt } ?: 0L
        } catch (_: Exception) { 0L } else 0L
        val restored = fresh && oldest in 1 until firstInstallMs - SLACK_MS
        val moved = ArrayList<String>()
        if (restored) {
            val dest = File(noBackupDir, "restored_$now").apply { mkdirs() }
            for (name in HISTORY_FILES) {
                val f = File(filesDir, name)
                if (f.exists() && (f.renameTo(File(dest, name)) || run { f.copyTo(File(dest, name), true); f.delete() })) moved += name
            }
        }
        noBackupDir.mkdirs()
        marker.writeText("firstInstall=$firstInstallMs lastUpdate=$lastUpdateMs checked=$now moved=${moved.joinToString(",")}")
        return Outcome(if (restored) "QUARANTINED_RESTORED_HISTORY" else if (fresh) "FRESH_INSTALL_CLEAN" else "UPDATE_KEPT", moved, oldest)
    }
}
