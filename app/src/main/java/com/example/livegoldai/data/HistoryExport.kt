package com.example.livegoldai.data

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * V37: explicit, read-only history export foundation.
 *
 * This does not perform automatic backups, import files, export encrypted API keys,
 * or delete local data. The Android UI must obtain a user-selected destination and
 * call this from a background dispatcher after pausing history writers.
 */
object HistoryExport {
    data class Result(val files: Int, val bytes: Long, val sha256: String)

    // Explicit allowlist: do not archive prefs, tokens, keystore or arbitrary paths.
    val ALLOWED = listOf(
        "prediction_ledger.jsonl", "shadow_ledger.jsonl", "shadow_models.json",
        "level_freeze_v1.tsv"
    )

    fun export(filesDir: File, destination: File): Result {
        require(filesDir.isDirectory) { "Missing app history directory" }
        val sourceFiles = ALLOWED.map { name -> name to File(filesDir, name) }
            .filter { (_, file) -> file.isFile }
        require(sourceFiles.isNotEmpty()) { "No history to export" }
        val canonicalRoot = filesDir.canonicalFile
        require(sourceFiles.all { (_, file) -> file.canonicalFile.parentFile == canonicalRoot }) {
            "History path escapes app directory"
        }
        require(destination.canonicalPath !in sourceFiles.map { it.second.canonicalPath }) {
            "Destination cannot overwrite history"
        }
        require(!destination.exists()) { "Export destination already exists" }
        destination.parentFile?.mkdirs()
        val tmp = File(destination.parentFile ?: File("."), destination.name + ".partial")
        require(!tmp.exists()) { "Incomplete export already exists" }
        require(tmp.canonicalPath !in sourceFiles.map { it.second.canonicalPath }) {
            "Temporary export path cannot overwrite history"
        }
        var count = 0
        var bytes = 0L
        try {
            ZipOutputStream(FileOutputStream(tmp)).use { zip ->
                for ((name, file) in sourceFiles) {
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            zip.write(buffer, 0, n)
                            bytes += n
                        }
                    }
                    zip.closeEntry()
                    count++
                }
            }
            require(tmp.renameTo(destination)) { "Could not finalize export" }
            val digest = MessageDigest.getInstance("SHA-256")
            destination.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return Result(count, bytes, digest.digest().joinToString("") { "%02x".format(it) })
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
