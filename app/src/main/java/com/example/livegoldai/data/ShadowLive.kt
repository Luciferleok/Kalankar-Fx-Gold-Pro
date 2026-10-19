package com.example.livegoldai.data

import com.example.livegoldai.model.GoldAnalysisResult
import java.io.File
import java.util.Locale

/**
 * LIVE SHADOW RECORDER (V28.1)
 *
 * The Shadow Lab model, run on every new CLOSED candle in real time, next to the live engine.
 * It never changes the live BUY/SELL/WAIT. Each call is saved once (append-only file) and checked later
 * on the same candle series with the same label rule as the lab (L1: close after H candles, 0.25 x ATR).
 *
 *  - the model is (re)trained from real history at most once a day per timeframe (1 Twelve Data credit)
 *  - only Twelve Data XAU/USD candles are used, so lab, live calls and checks are the same instrument
 *  - only timeframes that are real provider candles (not grouped on the phone), so candle times line up
 *  - status PROVEN LIVE needs 100+ decided live calls, the low end of the 95% range above 50%,
 *    a positive average after costs, and a lab verdict of EDGE CANDIDATE
 */
class ShadowLive(
    private val dir: () -> File,
    private val fetchHistory: suspend (String) -> Result<List<ShadowLab.Bar>>
) {
    data class Rec(
        val id: String, val interval: String, val barTime: String, val createdAt: Long,
        val call: Int, val probUp: Double, val close: Double, val atr: Double,
        val horizon: Int, val modelId: String, val modelVerdict: String
    )

    /** R right, W wrong, F flat (no 0.25 x ATR move), N no call (only counts coverage), G candle series gap */
    data class Out(val id: String, val code: String, val move: Double, val checkedAt: Long)

    data class Stats(
        val interval: String,
        val since: Long,            // first live record, 0 = none
        val samples: Int,           // closed candles seen and checked
        val calls: Int,
        val right: Int,
        val wrong: Int,
        val flat: Int,
        val pending: Int,
        val avgNetUsd: Double?,
        val wilsonLow: Double?,
        val wilsonHigh: Double?,
        val status: String,         // NO MODEL YET / COLLECTING / PROVEN LIVE / NOT PROVEN
        val modelVerdict: String,
        val modelTrainedAt: Long,
        val lastCall: String
    ) {
        val decided get() = right + wrong
        val accuracy: Double? get() = if (decided == 0) null else 100.0 * right / decided
    }

    companion object {
        val LIVE_INTERVALS = setOf("1m", "5m", "15m", "30m", "45m", "1h", "2h", "4h", "1d", "1w")
        const val RETRAIN_MS = 24 * 3600_000L
        const val RETRY_MS = 3600_000L
        const val PROVEN_MIN = 100
        const val TWELVE = "Twelve"
    }

    private val store by lazy { FileLedgerStore(File(dir(), "shadow_ledger.jsonl")) }
    private val modelFile get() = File(dir(), "shadow_models.json")
    private var loaded = false
    private val recs = LinkedHashMap<String, Rec>()
    private val outs = HashMap<String, Out>()
    private val seen = HashSet<String>()                    // interval|barTime
    private val models = HashMap<String, ShadowLab.LiveModel>()
    private val reports = HashMap<String, ShadowLab.Report>()
    private val lastAttempt = HashMap<String, Long>()
    private val training = HashSet<String>()

    // ------------------------------------------------------------------ load / save

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            for (line in store.readAll()) {
                if (line.isBlank()) continue
                @Suppress("UNCHECKED_CAST")
                val m = (try { MiniJson.parse(line) } catch (_: Exception) { null }) as? Map<String, Any?> ?: continue
                when (m.str("t")) {
                    "sr" -> {
                        val r = Rec(m.str("id"), m.str("iv"), m.str("bar"), m.long("c"), m.int("call"), m.num("p"),
                            m.num("px"), m.num("atr"), m.int("h", ShadowLab.HORIZON), m.str("mid"), m.str("mv"))
                        recs[r.id] = r; seen.add(r.interval + "|" + r.barTime)
                    }
                    "so" -> outs[m.str("id")] = Out(m.str("id"), m.str("code"), m.num("mv"), m.long("at"))
                }
            }
        } catch (_: Exception) { }
        try {
            if (modelFile.exists()) {
                @Suppress("UNCHECKED_CAST")
                val root = MiniJson.parse(modelFile.readText()) as? Map<String, Any?>
                root?.forEach { (iv, v) ->
                    @Suppress("UNCHECKED_CAST")
                    val m = v as? Map<String, Any?> ?: return@forEach
                    fun dl(k: String) = m.list(k).mapNotNull { (it as? Number)?.toDouble() }
                    models[iv] = ShadowLab.LiveModel(iv, dl("w"), dl("mean"), dl("sd"), m.num("gate"), m.int("h", ShadowLab.HORIZON),
                        m.str("sid"), m.str("verdict"), m.long("at"))
                }
            }
        } catch (_: Exception) { }
    }

    @Synchronized
    private fun saveModels() {
        try {
            val root = LinkedHashMap<String, Any?>()
            models.forEach { (iv, m) ->
                root[iv] = linkedMapOf("w" to m.weights, "mean" to m.mean, "sd" to m.sd, "gate" to m.gate, "h" to m.horizon,
                    "sid" to m.snapshotId, "verdict" to m.verdict, "at" to m.trainedAt)
            }
            modelFile.parentFile?.mkdirs()
            modelFile.writeText(MiniJson.write(root))
        } catch (_: Exception) { }
    }

    // ------------------------------------------------------------------ training

    @Synchronized fun report(interval: String): ShadowLab.Report? { ensureLoaded(); return reports[interval] }
    @Synchronized fun allReports(): Map<String, ShadowLab.Report> { ensureLoaded(); return HashMap(reports) }

    /** Downloads real history, runs the lab, keeps the report and (for live timeframes) the fitted model. */
    suspend fun train(interval: String, now: Long = System.currentTimeMillis()): Result<ShadowLab.Report> {
        synchronized(this) {
            ensureLoaded()
            if (!training.add(interval)) return Result.failure(IllegalStateException("Already running"))
            lastAttempt[interval] = now
        }
        try {
            val bars = fetchHistory(interval).getOrElse { return Result.failure(it) }
            val rep = ShadowLab.run(interval, bars, now)
            synchronized(this) {
                reports[interval] = rep
                val lm = rep.live
                if (lm != null && interval in LIVE_INTERVALS) { models[interval] = lm; saveModels() }
            }
            return Result.success(rep)
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            synchronized(this) { training.remove(interval) }
        }
    }

    // ------------------------------------------------------------------ live observe

    /**
     * Called with every fresh analysis (screen and background recorder). Checks old shadow calls, retrains when due,
     * then records one shadow call for the newest closed candle. Returns calls made on PROVEN LIVE timeframes.
     */
    suspend fun observe(a: GoldAnalysisResult, interval: String, now: Long = System.currentTimeMillis()): List<String> {
        if (interval !in LIVE_INTERVALS || a.isSimulatedFallback) return emptyList()
        if (a.feed?.source?.contains(TWELVE) != true) return emptyList()
        val src = a.chartCandles.ifEmpty { a.recentCandles }
        if (src.size < ShadowLab.WARMUP + 6) return emptyList()
        val bars = src.dropLast(1).map { ShadowLab.Bar(it.datetime, it.open, it.high, it.low, it.close) }   // newest is still forming
        resolve(interval, bars, now)
        val needTrain = synchronized(this) {
            ensureLoaded()
            val m = models[interval]
            (m == null || now - m.trainedAt > RETRAIN_MS) && now - (lastAttempt[interval] ?: 0L) > RETRY_MS
        }
        if (needTrain) train(interval, now)
        return record(interval, bars, now)
    }

    @Synchronized
    private fun record(interval: String, bars: List<ShadowLab.Bar>, now: Long): List<String> {
        val model = models[interval] ?: return emptyList()
        val last = bars.lastOrNull() ?: return emptyList()
        val key = "$interval|${last.time}"
        if (key in seen) return emptyList()
        val f = ShadowLab.factorRows(bars).lastOrNull() ?: return emptyList()
        val atr = ShadowLab.atr14(bars).last()
        if (atr <= 0) return emptyList()
        val (call, p) = model.call(f)
        val r = Rec("sh-$interval-${last.time.replace(' ', 'T')}", interval, last.time, now, call, p, last.close, atr,
            model.horizon, model.id, model.verdict)
        val ok = try {
            store.append(listOf(MiniJson.write(linkedMapOf(
                "t" to "sr", "id" to r.id, "iv" to r.interval, "bar" to r.barTime, "c" to r.createdAt, "call" to r.call,
                "p" to r.probUp, "px" to r.close, "atr" to r.atr, "h" to r.horizon, "mid" to r.modelId, "mv" to r.modelVerdict
            ))))
            true
        } catch (_: Exception) { false }
        if (!ok) return emptyList()
        recs[r.id] = r; seen.add(key)
        if (call == 0) return emptyList()
        return if (statsLocked(interval).status == "PROVEN LIVE")
            listOf(String.format(Locale.US, "%s %s • P(up) %.0f%% • next %d candles", interval, if (call > 0) "UP" else "DOWN", p * 100, model.horizon))
        else emptyList()
    }

    @Synchronized
    private fun resolve(interval: String, bars: List<ShadowLab.Bar>, now: Long) {
        ensureLoaded()
        if (bars.isEmpty()) return
        val pos = HashMap<String, Int>(bars.size * 2)
        bars.forEachIndexed { i, b -> pos[b.time] = i }
        val ivMs = RealityEngine.intervalMinutes(interval) * 60_000L
        val lines = ArrayList<String>()
        for (r in recs.values) {
            if (r.interval != interval || outs.containsKey(r.id)) continue
            val i = pos[r.barTime]
            val out = when {
                i != null && i + r.horizon <= bars.lastIndex -> {
                    val mv = bars[i + r.horizon].close - r.close
                    val y = when { mv >= ShadowLab.FLAT_ATR * r.atr -> 1; mv <= -ShadowLab.FLAT_ATR * r.atr -> -1; else -> 0 }
                    val code = when { r.call == 0 -> "N"; y == 0 -> "F"; y == r.call -> "R"; else -> "W" }
                    Out(r.id, code, mv, now)
                }
                // the candle fell out of the downloaded window without being checked (app/recorder was off too long)
                i == null && bars.first().time > r.barTime && now - r.createdAt > ivMs * (r.horizon + 2) -> Out(r.id, "G", 0.0, now)
                else -> null
            } ?: continue
            outs[r.id] = out
            lines.add(MiniJson.write(linkedMapOf("t" to "so", "id" to out.id, "code" to out.code, "mv" to out.move, "at" to out.checkedAt)))
        }
        if (lines.isNotEmpty()) try { store.append(lines) } catch (_: Exception) { }
    }

    // ------------------------------------------------------------------ stats / export

    @Synchronized fun stats(interval: String): Stats { ensureLoaded(); return statsLocked(interval) }

    @Synchronized fun allStats(): List<Stats> {
        ensureLoaded()
        val ivs = (recs.values.map { it.interval } + models.keys).toSet()
        return LIVE_INTERVALS.filter { it in ivs }.map { statsLocked(it) }
    }

    private fun statsLocked(interval: String): Stats {
        val mine = recs.values.filter { it.interval == interval }
        var samples = 0; var calls = 0; var right = 0; var wrong = 0; var flat = 0; var pending = 0; var net = 0.0
        for (r in mine) {
            val o = outs[r.id]
            if (o == null) { pending++; continue }
            if (o.code == "G") continue
            samples++
            if (r.call == 0) continue
            calls++
            net += r.call * o.move - ShadowLab.COST_USD
            when (o.code) { "R" -> right++; "W" -> wrong++; "F" -> flat++ }
        }
        val w = ResearchLab.wilson(right, right + wrong)
        val m = models[interval]
        val avg = if (calls == 0) null else net / calls
        val status = when {
            m == null -> "NO MODEL YET"
            right + wrong < PROVEN_MIN -> "COLLECTING"
            (w?.first ?: 0.0) > 50.0 && (avg ?: 0.0) > 0 && m.verdict == "EDGE CANDIDATE" -> "PROVEN LIVE"
            else -> "NOT PROVEN"
        }
        val lastCall = mine.lastOrNull()?.let { r ->
            r.barTime + " • " + when (r.call) { 1 -> "UP"; -1 -> "DOWN"; else -> "NO CALL" } + String.format(Locale.US, " (P(up) %.0f%%)", r.probUp * 100)
        } ?: "--"
        return Stats(interval, mine.minOfOrNull { it.createdAt } ?: 0L, samples, calls, right, wrong, flat, pending,
            avg, w?.first, w?.second, status, m?.verdict ?: "--", m?.trainedAt ?: 0L, lastCall)
    }

    @Synchronized
    fun csv(): String {
        ensureLoaded()
        val sb = StringBuilder("id,interval,candle_utc,recorded_at_ms,call,p_up,price,atr,horizon,model_id,model_verdict,result,move_usd\n")
        for (r in recs.values) {
            val o = outs[r.id]
            sb.append(r.id).append(',').append(r.interval).append(',').append(r.barTime).append(',').append(r.createdAt).append(',')
                .append(when (r.call) { 1 -> "UP"; -1 -> "DOWN"; else -> "NO_CALL" }).append(',')
                .append(String.format(Locale.US, "%.4f", r.probUp)).append(',').append(r.close).append(',')
                .append(String.format(Locale.US, "%.4f", r.atr)).append(',').append(r.horizon).append(',').append(r.modelId).append(',')
                .append(r.modelVerdict).append(',')
                .append(when (o?.code) { "R" -> "RIGHT"; "W" -> "WRONG"; "F" -> "FLAT"; "N" -> "NO_CALL"; "G" -> "GAP"; else -> "PENDING" }).append(',')
                .append(o?.let { String.format(Locale.US, "%.2f", it.move) } ?: "").append('\n')
        }
        return sb.toString()
    }
}

/** CSV export of the main prediction ledger (every recorded prediction and its checked result). */
object LedgerCsv {
    private fun q(s: String) = if (s.contains(',') || s.contains('"') || s.contains('\n')) "\"" + s.replace("\"", "\"\"") + "\"" else s

    fun build(state: LedgerState): String {
        val sb = StringBuilder("id,created_utc,expires_utc,interval,signal_shown,engine_signal,confidence,price,atr,regime,session,news,model,data_source,snapshot_id,result,move_usd,threshold_usd,checked_utc,verify_source\n")
        for (r in state.records) {
            val res = state.resultOf(r.id)
            sb.append(q(r.id)).append(',')
                .append(PredictionLedger.utcLabel(r.createdAt)).append(',')
                .append(PredictionLedger.utcLabel(r.expiresAt)).append(',')
                .append(r.interval).append(',').append(r.finalSignal.name).append(',').append(r.rawSignal.name).append(',')
                .append(r.confidence).append(',').append(r.price).append(',').append(String.format(Locale.US, "%.4f", r.atr)).append(',')
                .append(q(r.regime)).append(',').append(q(r.session)).append(',').append(r.newsActive).append(',')
                .append(q(r.modelVersion)).append(',').append(q(r.dataSource)).append(',').append(q(r.snapshotId)).append(',')
                .append(res?.outcome?.name ?: "PENDING").append(',')
                .append(res?.let { String.format(Locale.US, "%.2f", it.move) } ?: "").append(',')
                .append(res?.let { String.format(Locale.US, "%.2f", it.threshold) } ?: "").append(',')
                .append(res?.let { PredictionLedger.utcLabel(it.checkedAt) } ?: "").append(',')
                .append(q(res?.verifySource ?: "")).append('\n')
        }
        return sb.toString()
    }
}
