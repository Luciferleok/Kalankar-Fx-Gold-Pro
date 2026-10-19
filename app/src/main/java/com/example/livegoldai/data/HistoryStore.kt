package com.example.livegoldai.data

import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * HISTORICAL MARKET STORE (V28.4)
 *
 * Builds a deeper, deduplicated, same-source XAU/USD history on the phone, one file per provider candle size.
 *  - every download is merged: duplicates removed, order fixed, nothing silently overwritten
 *  - if the provider changes a candle that was already stored, the change is detected and logged
 *    (old data hash, new data hash, changed candles, time) so older research stays reproducible
 *  - a manifest records provider, instrument, first/last candle, count, data hash, gaps and schema
 *  - unchanged history is never downloaded again; "deepen" fetches the 5000 candles BEFORE the oldest stored one
 */
class HistoryStore(private val dir: () -> File) {

    companion object {
        const val SCHEMA = "H1"
        const val PROVIDER = "Twelve Data"
        const val INSTRUMENT = "XAU/USD"
        const val MAX_BARS = 50_000

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { String.format("%02x", it) }

        fun line(b: ShadowLab.Bar) = "${b.time},${b.open},${b.high},${b.low},${b.close}"

        fun dataHash(bars: List<ShadowLab.Bar>): String = sha256(bars.joinToString("\n") { line(it) })

        private fun parse(t: String): Long? = try {
            val f = SimpleDateFormat(if (t.length > 10) "yyyy-MM-dd HH:mm:ss" else "yyyy-MM-dd", Locale.US)
            f.timeZone = TimeZone.getTimeZone("UTC"); f.parse(t)?.time
        } catch (_: Exception) { null }

        /** Gaps longer than 1.5 candles that do not contain a Saturday (weekend closures are normal). */
        fun gapCount(bars: List<ShadowLab.Bar>, candleMs: Long): Int {
            if (candleMs <= 0) return 0
            var gaps = 0
            var prev: Long? = null
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            for (b in bars) {
                val t = parse(b.time) ?: continue
                val p = prev
                prev = t
                if (p == null || t - p <= candleMs * 3 / 2) continue
                var weekend = false
                var x = p
                while (x < t) {
                    cal.timeInMillis = x
                    val d = cal.get(Calendar.DAY_OF_WEEK)
                    if (d == Calendar.SATURDAY || d == Calendar.SUNDAY) { weekend = true; break }
                    x += 6 * 3600_000L
                }
                if (!weekend) gaps++
            }
            return gaps
        }

        /** Merge without silent mutation. Returns merged bars and how many stored candles the provider changed. */
        fun merge(stored: List<ShadowLab.Bar>, incoming: List<ShadowLab.Bar>): Pair<List<ShadowLab.Bar>, Int> {
            val map = java.util.TreeMap<String, ShadowLab.Bar>()
            stored.forEach { map[it.time] = it }
            var changed = 0
            for (b in incoming) {
                if (b.time.isBlank()) continue
                val old = map[b.time]
                if (old != null && (old.open != b.open || old.high != b.high || old.low != b.low || old.close != b.close)) changed++
                map[b.time] = b   // provider's newest version is used; the change is logged by the caller
            }
            val all = map.values.toList()
            return (if (all.size > MAX_BARS) all.takeLast(MAX_BARS) else all) to changed
        }
    }

    data class Revision(val at: Long, val oldHash: String, val newHash: String, val changedBars: Int)

    data class Manifest(
        val provider: String,
        val instrument: String,
        val candle: String,          // provider candle size, e.g. "1h"
        val first: String,
        val last: String,
        val count: Int,
        val downloadedAt: Long,
        val dataHash: String,
        val gapCount: Int,
        val schema: String,
        val revisions: List<Revision>
    )

    private fun file(td: String) = File(dir(), "history_${td.replace('/', '_')}.csv")
    private fun manifestFile(td: String) = File(dir(), "history_${td.replace('/', '_')}.manifest.json")

    @Synchronized
    fun load(td: String): List<ShadowLab.Bar> {
        val f = file(td)
        if (!f.exists()) return emptyList()
        return try {
            f.readLines().mapNotNull { l ->
                val p = l.split(',')
                if (p.size < 5) null else ShadowLab.Bar(p[0], p[1].toDouble(), p[2].toDouble(), p[3].toDouble(), p[4].toDouble())
            }
        } catch (_: Exception) { emptyList() }
    }

    @Synchronized
    fun manifest(td: String): Manifest? = try {
        val f = manifestFile(td)
        if (!f.exists()) null else {
            @Suppress("UNCHECKED_CAST")
            val m = MiniJson.parse(f.readText()) as Map<String, Any?>
            Manifest(m.str("provider"), m.str("instrument"), m.str("candle"), m.str("first"), m.str("last"), m.int("count"),
                m.long("at"), m.str("hash"), m.int("gaps"), m.str("schema"),
                m.list("rev").mapNotNull { r ->
                    @Suppress("UNCHECKED_CAST")
                    val x = r as? Map<String, Any?> ?: return@mapNotNull null
                    Revision(x.long("at"), x.str("old"), x.str("new"), x.int("n"))
                })
        }
    } catch (_: Exception) { null }

    /** Merges a download into the store and writes data + manifest. */
    @Synchronized
    fun add(td: String, incoming: List<ShadowLab.Bar>, candleMs: Long, now: Long): Pair<List<ShadowLab.Bar>, Manifest> {
        val stored = load(td)
        val before = manifest(td)
        val (merged, changed) = merge(stored, incoming)
        val hash = dataHash(merged)
        val revisions = (before?.revisions ?: emptyList()) +
            (if (changed > 0) listOf(Revision(now, before?.dataHash ?: dataHash(stored), hash, changed)) else emptyList())
        val man = Manifest(PROVIDER, INSTRUMENT, td, merged.firstOrNull()?.time ?: "--", merged.lastOrNull()?.time ?: "--",
            merged.size, now, hash, gapCount(merged, candleMs), SCHEMA, revisions.takeLast(50))
        try {
            dir().mkdirs()
            file(td).writeText(merged.joinToString("\n") { line(it) })
            manifestFile(td).writeText(MiniJson.write(linkedMapOf(
                "provider" to man.provider, "instrument" to man.instrument, "candle" to man.candle, "first" to man.first,
                "last" to man.last, "count" to man.count, "at" to man.downloadedAt, "hash" to man.dataHash, "gaps" to man.gapCount,
                "schema" to man.schema,
                "rev" to man.revisions.map { linkedMapOf("at" to it.at, "old" to it.oldHash, "new" to it.newHash, "n" to it.changedBars) }
            )))
        } catch (_: Exception) { }
        return merged to man
    }
}
