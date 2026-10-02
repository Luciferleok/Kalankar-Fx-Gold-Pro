package com.example.livegoldai.data.ai

import com.example.livegoldai.data.LedgerOutcome
import com.example.livegoldai.data.LedgerState
import com.example.livegoldai.data.LedgerStore
import com.example.livegoldai.data.MiniJson
import com.example.livegoldai.data.PredictionLedger
import com.example.livegoldai.data.bool
import com.example.livegoldai.data.int
import com.example.livegoldai.data.long
import com.example.livegoldai.data.num
import com.example.livegoldai.data.str
import com.example.livegoldai.model.AiPairStat
import com.example.livegoldai.model.Signal
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * PROVIDER PERFORMANCE LEDGER
 *
 * Every real AI request is appended to the same on-device ledger file as an "aiv" line
 * (the prediction ledger ignores unknown line types, so old versions stay compatible).
 * From these lines we measure availability, latency, schema compliance, fact consistency,
 * usage today, and — once the linked prediction has expired and been checked against real
 * prices — whether the provider's direction matched the real move.
 */
data class AiVoteLine(
    val at: Long,
    val provider: String,
    val model: String,
    val promptVersion: String,
    val snapshotId: String,
    val recordId: String,      // prediction it belongs to ("" if none)
    val status: String,        // final status (ONLINE / STALE / TIMEOUT ...)
    val validation: String,    // validation status when an answer arrived, else ""
    val eligible: Boolean,
    val direction: Signal?,
    val bullish: Int,
    val bearish: Int,
    val sideways: Int,
    val confidence: Int,
    val latencyMs: Long,
    val tokens: Int,
    val price: Double,         // snapshot price the provider saw
    val factConflicts: Int,
    val debate: Boolean
)

class AiPerformanceTracker(private val store: LedgerStore?) {

    private val lines = ArrayList<AiVoteLine>()
    @Volatile var lastWriteOk: Boolean = true; private set

    init {
        if (store != null) {
            try {
                store.readAll().forEach { raw ->
                    if (!raw.contains("\"aiv\"")) return@forEach
                    decode(raw)?.let { lines.add(it) }
                }
            } catch (_: Exception) {
            }
        }
    }

    @Synchronized
    fun append(l: AiVoteLine) {
        lines.add(l)
        if (lines.size > MAX_LINES) lines.subList(0, lines.size - MAX_LINES).clear()
        if (store != null) {
            lastWriteOk = try { store.append(listOf(encode(l))); true } catch (_: Exception) { false }
        }
    }

    @Synchronized
    fun all(): List<AiVoteLine> = ArrayList(lines)

    // ------------------------------------------------------------------ operational stats

    data class OpStats(
        val attempts: Int,
        val availabilityPct: Int,     // API answered
        val schemaOkPct: Int,         // answer passed the schema check
        val factOkPct: Int,           // valid answer without a fact conflict
        val medianLatencyMs: Long,
        val trust: Int,               // -1 below MIN_TRUST_SAMPLES
        val requestsToday: Int,
        val okToday: Int,
        val failedToday: Int,
        val avgLatencyTodayMs: Long,
        val tokensToday: Long
    )

    fun opStats(provider: String, now: Long = System.currentTimeMillis()): OpStats {
        val mine = all().filter { it.provider == provider && !it.debate }
        val recent = mine.takeLast(50)
        val answered = recent.filter { it.validation.isNotEmpty() }
        val schemaOk = answered.filter { it.validation == AiStatus.ONLINE || it.validation == AiStatus.FACT_CONFLICT }
        val factOk = schemaOk.filter { it.validation == AiStatus.ONLINE }
        val lat = answered.map { it.latencyMs }.filter { it >= 0 }.sorted()
        val median = if (lat.isEmpty()) -1L else lat[lat.size / 2]
        val avail = pct(answered.size, recent.size)
        val schema = pct(schemaOk.size, answered.size)
        val fact = pct(factOk.size, schemaOk.size)
        val trust = if (recent.size < MIN_TRUST_SAMPLES) -1 else {
            val latScore = if (median < 0) 0.0 else ((20_000.0 - median) / 18_000.0).coerceIn(0.0, 1.0)
            (0.40 * avail.coerceAtLeast(0) + 0.25 * schema.coerceAtLeast(0) + 0.20 * fact.coerceAtLeast(0) + 15.0 * latScore).roundToInt()
        }
        val dayStart = startOfDay(now)
        val today = all().filter { it.provider == provider && it.at >= dayStart }
        val todayOk = today.filter { it.validation.isNotEmpty() }
        return OpStats(
            attempts = recent.size,
            availabilityPct = avail, schemaOkPct = schema, factOkPct = fact,
            medianLatencyMs = median, trust = trust,
            requestsToday = today.size,
            okToday = today.count { it.status == AiStatus.ONLINE },
            failedToday = today.count { it.validation.isEmpty() || it.validation == AiStatus.SCHEMA_INVALID },
            avgLatencyTodayMs = if (todayOk.isEmpty()) -1 else todayOk.map { it.latencyMs }.average().toLong(),
            tokensToday = today.filter { it.tokens > 0 }.sumOf { it.tokens.toLong() }
        )
    }

    // ------------------------------------------------------------------ verified direction record

    /**
     * First independent (round-1) eligible vote per (provider, prediction) made in the first half of the
     * forecast window. Debate answers are excluded so every provider is judged on its own first view.
     */
    private fun linkedVotes(state: LedgerState): List<Pair<AiVoteLine, com.example.livegoldai.data.LedgerRecord>> {
        val recs = state.records.associateBy { it.id }
        val seen = HashSet<String>()
        val out = ArrayList<Pair<AiVoteLine, com.example.livegoldai.data.LedgerRecord>>()
        for (l in all().filter { it.eligible && !it.debate && it.recordId.isNotEmpty() && it.direction != null }.sortedBy { it.at }) {
            val r = recs[l.recordId] ?: continue
            if (l.at - r.createdAt > (r.expiresAt - r.createdAt) / 2) continue   // too late to count as a forecast
            val k = l.provider + "|" + l.recordId
            if (!seen.add(k)) continue
            out.add(l to r)
        }
        return out
    }

    /** (hits, decided) for one provider: vote direction vs real move from the price it saw to the end of the window. */
    fun directionRecord(provider: String, state: LedgerState): Pair<Int, Int> {
        var hits = 0; var decided = 0
        for ((l, r) in linkedVotes(state)) {
            if (l.provider != provider) continue
            val res = state.resultOf(r.id) ?: continue
            if (!PredictionLedger.reliable(res.outcome)) continue
            val move = res.endPx - (if (l.price > 0) l.price else res.startPx)
            when (PredictionLedger.directionScore(l.direction!!, move, res.threshold)) {
                1 -> { hits++; decided++ }
                0 -> decided++
            }
        }
        return hits to decided
    }

    /** Quant engine accuracy split by whether the AI council agreed with it (real, from the ledger). */
    fun quantVsAi(state: LedgerState): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        val byRecord = linkedVotes(state).groupBy { it.second.id }
        var aH = 0; var aN = 0; var dH = 0; var dN = 0
        for ((rid, votes) in byRecord) {
            val r = votes.first().second
            val res = state.resultOf(rid) ?: continue
            if (r.rawSignal == Signal.WAIT) continue
            if (!PredictionLedger.reliable(res.outcome)) continue
            val dirs = votes.map { it.first.direction!! }
            val top = dirs.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: continue
            if (top.value * 2 <= dirs.size) continue          // no majority
            val score = PredictionLedger.directionScore(r.rawSignal, res.move, res.threshold)
            if (score < 0) continue
            if (top.key == r.rawSignal) { aN++; if (score == 1) aH++ }
            else if (PredictionLedger.opposite(top.key, r.rawSignal)) { dN++; if (score == 1) dH++ }
        }
        return (aH to aN) to (dH to dN)
    }

    /** How often two providers gave the same direction on the same prediction. */
    fun pairs(state: LedgerState): List<AiPairStat> {
        val byRecord = linkedVotes(state).groupBy { it.second.id }
        val together = HashMap<String, IntArray>()
        for ((_, votes) in byRecord) {
            val v = votes.map { it.first }.sortedBy { it.provider }
            for (i in v.indices) for (j in i + 1 until v.size) {
                val k = v[i].provider + "|" + v[j].provider
                val c = together.getOrPut(k) { IntArray(2) }
                c[0]++
                if (v[i].direction == v[j].direction) c[1]++
            }
        }
        return together.entries.sortedByDescending { it.value[0] }.map { (k, c) ->
            AiPairStat(k.substringBefore("|"), k.substringAfter("|"), c[0], (100.0 * c[1] / c[0]).roundToInt())
        }
    }

    // ------------------------------------------------------------------ codec

    fun encode(l: AiVoteLine): String = MiniJson.write(
        linkedMapOf(
            "t" to "aiv", "at" to l.at, "p" to l.provider, "m" to l.model, "pv" to l.promptVersion,
            "sid" to l.snapshotId, "rid" to l.recordId, "st" to l.status, "vst" to l.validation,
            "el" to l.eligible, "d" to (l.direction?.name ?: ""), "pb" to l.bullish, "pr" to l.bearish,
            "ps" to l.sideways, "c" to l.confidence, "lat" to l.latencyMs, "tok" to l.tokens,
            "px" to l.price, "fc" to l.factConflicts, "r2" to l.debate
        )
    )

    @Suppress("UNCHECKED_CAST")
    fun decode(raw: String): AiVoteLine? = try {
        val m = MiniJson.parse(raw) as? Map<String, Any?>
        if (m == null || m.str("t") != "aiv") null else AiVoteLine(
            at = m.long("at"), provider = m.str("p"), model = m.str("m"), promptVersion = m.str("pv"),
            snapshotId = m.str("sid"), recordId = m.str("rid"), status = m.str("st"), validation = m.str("vst"),
            eligible = m.bool("el"),
            direction = m.str("d").let { d -> if (d.isEmpty()) null else runCatching { Signal.valueOf(d) }.getOrNull() },
            bullish = m.int("pb"), bearish = m.int("pr"), sideways = m.int("ps"), confidence = m.int("c"),
            latencyMs = m.long("lat"), tokens = m.int("tok"), price = m.num("px"), factConflicts = m.int("fc"),
            debate = m.bool("r2")
        )
    } catch (_: Exception) {
        null
    }

    companion object {
        const val MAX_LINES = 20000
        const val MIN_TRUST_SAMPLES = 5
        const val MIN_ACCURACY_SAMPLES = 20

        fun pct(a: Int, b: Int): Int = if (b == 0) -1 else (100.0 * a / b).roundToInt()

        fun label(hits: Int, n: Int): String = when {
            n == 0 -> "untested"
            n < MIN_ACCURACY_SAMPLES -> "untested (N=$n)"
            else -> "${(100.0 * hits / n).roundToInt()}% (N=$n)"
        }

        fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
