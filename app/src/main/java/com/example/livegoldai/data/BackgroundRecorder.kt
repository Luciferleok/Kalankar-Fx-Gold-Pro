package com.example.livegoldai.data

import com.example.livegoldai.data.brain.AnomalyEngine
import com.example.livegoldai.data.brain.CrossMarketEngine
import com.example.livegoldai.data.brain.CrossMarketService
import com.example.livegoldai.data.brain.FeatureCatalog
import com.example.livegoldai.data.brain.FeatureStore
import com.example.livegoldai.model.GoldAnalysisResult
import java.io.File
import java.util.Calendar

/**
 * One shared engine for the screen and the background recorder, so both write the same ledger
 * through the same lock and never record the same candle twice.
 */
object AppEngine {
    val api = GoldApiService()
    val crossMarket = CrossMarketService()
    @Volatile private var dir: File? = null

    fun init(filesDir: File) { if (dir == null) dir = filesDir }

    // first use reads the ledger file: background threads only
    val ledgerStore: FileLedgerStore by lazy { FileLedgerStore(File(dir ?: File("."), "prediction_ledger.jsonl")) }
    val learning: LearningCoordinator by lazy { LearningCoordinator(ledgerStore) { s, e -> api.fetchPricePath(s, e) } }
    val featureStore: FeatureStore by lazy { FeatureStore(ledgerStore) }
    val recorder: BackgroundRecorder by lazy {
        BackgroundRecorder(
            learning = learning,
            fetchAnalysis = { iv -> api.fetchAnalysis(interval = iv).getOrNull() },
            fetchMtf = { api.fetchMtfCandles() },
            onRecorded = { a, rec, now ->
                // same feature snapshot the screen would store
                val full = a.copy(insights = CockpitInsightsBuilder.build(a))
                val xm = CrossMarketEngine.build(crossMarket.series(), now)
                val feats = FeatureCatalog.compute(full, xm, AnomalyEngine.build(full, xm, now), now)
                featureStore.recordIfAbsent(rec.id, rec.createdAt, rec.interval, feats)
            }
        )
    }
}

/**
 * BACKGROUND RECORDER
 * Called once a minute by the foreground service. For every timeframe it records one prediction per candle
 * (only when a new one is due, to save API calls) and checks every prediction whose time is over.
 * External AI is NOT called here: it costs API quota and runs only while the app is open.
 */
class BackgroundRecorder(
    private val learning: LearningCoordinator,
    private val fetchAnalysis: suspend (String) -> GoldAnalysisResult?,
    private val fetchMtf: suspend () -> Map<String, List<PathBar>>,
    private val onRecorded: (GoldAnalysisResult, LedgerRecord, Long) -> Unit = { _, _, _ -> }
) {
    data class Problem(val code: String, val title: String, val text: String)

    data class TickReport(
        val at: Long,
        val marketClosed: Boolean,
        val recordedNow: List<String>,      // "1h SELL"
        val checkedNow: List<String>,       // "15m BUY ✓"
        val recorded: Int,
        val checked: Int,
        val active: Int,
        val todayCorrect: Int,
        val todayWrong: Int,
        val lastRecorded: String,
        val nextDue: String,
        val problems: List<Problem>
    )

    @Volatile var lastReport: TickReport? = null; private set
    private var dataFailures = 0
    private val nextAttemptAt = HashMap<String, Long>()   // back-off per timeframe when the provider has no new candle yet

    suspend fun tick(now: Long = System.currentTimeMillis()): TickReport {
        val closed = PredictionLedger.isMarketClosed(now)
        val before = learning.currentState()
        val recordedNow = ArrayList<String>()
        var attempted = 0
        var failed = 0

        if (!closed) {
            val due = INTERVALS.filter { iv ->
                val last = before.records.lastOrNull { it.interval == iv }
                now >= (nextAttemptAt[iv] ?: 0L) &&
                    (last == null || now - last.createdAt >= RealityEngine.intervalMinutes(iv) * 60_000L * 9 / 10)
            }.take(MAX_PER_TICK)
            var mtf: Map<String, List<PathBar>>? = null
            for (iv in due) {
                attempted++
                // if this attempt records nothing (no new candle yet / no data), wait before asking again
                nextAttemptAt[iv] = now + maxOf(60_000L, RealityEngine.intervalMinutes(iv) * 60_000L / 12)
                val a = try { fetchAnalysis(iv) } catch (_: Exception) { null }
                if (a == null || a.isSimulatedFallback) { failed++; continue }
                if (mtf == null) mtf = try { fetchMtf() } catch (_: Exception) { emptyMap() }
                val n0 = learning.currentState().records.size
                val learned = try { learning.process(a, iv, mtf ?: emptyMap(), now) } catch (_: Exception) { null }
                val st = learning.currentState()
                if (learned != null && st.records.size > n0) {
                    val rec = st.records.last()
                    recordedNow.add("${rec.interval} ${rec.finalSignal.name}")
                    try { onRecorded(learned, rec, now) } catch (_: Exception) { }
                }
            }
        }
        // always check what has expired (process() already does it when something was recorded)
        if (recordedNow.isEmpty()) try { learning.verifyOnly(now) } catch (_: Exception) { }

        if (attempted > 0 && failed == attempted) dataFailures++ else if (attempted > 0) dataFailures = 0

        val st = learning.currentState()
        val stats = learning.stats(now)
        val newIds = st.results.keys - before.results.keys
        val recById = st.records.associateBy { it.id }
        val checkedNow = newIds.mapNotNull { id ->
            val r = recById[id] ?: return@mapNotNull null
            "${r.interval} ${r.finalSignal.name} ${mark(st.results[id]!!.outcome)}"
        }
        val dayStart = Calendar.getInstance().apply {
            timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        var ok = 0; var bad = 0
        for (res in st.results.values) {
            if (res.checkedAt < dayStart) continue
            when (res.outcome) {
                LedgerOutcome.CORRECT -> ok++
                LedgerOutcome.INCORRECT, LedgerOutcome.INVALIDATED -> bad++
                else -> {}
            }
        }
        val problems = ArrayList<Problem>()
        if (!stats.lastWriteOk) problems.add(Problem("LEDGER_WRITE", "Prediction record cannot be saved", "Ledger file write failed: ${stats.lastWriteError}"))
        if (dataFailures >= 3) problems.add(Problem("NO_LIVE_DATA", "No live gold price", "All price sources failed $dataFailures times in a row. Nothing is being recorded. Check the internet connection."))
        if (stats.consecutiveFetchFailures >= 5) problems.add(Problem("VERIFY_FAIL", "Predictions cannot be checked", "Price history could not be loaded ${stats.consecutiveFetchFailures} times in a row. Results are waiting, nothing is lost."))

        val last = st.records.lastOrNull()
        val next = st.records.filter { st.resultOf(it.id) == null }.minByOrNull { it.expiresAt }
        val report = TickReport(
            at = now, marketClosed = closed, recordedNow = recordedNow, checkedNow = checkedNow,
            recorded = stats.records, checked = stats.results, active = stats.pending,
            todayCorrect = ok, todayWrong = bad,
            lastRecorded = if (last == null) "--" else "${PredictionLedger.utcLabel(last.createdAt)} • ${last.interval} ${last.finalSignal.name}",
            nextDue = if (next == null) "--" else "${PredictionLedger.utcLabel(next.expiresAt)} • ${next.interval}",
            problems = problems
        )
        lastReport = report
        return report
    }

    companion object {
        val INTERVALS = listOf("5m", "15m", "30m", "1h", "4h", "1d")
        const val MAX_PER_TICK = 3

        fun mark(o: LedgerOutcome): String = when (o) {
            LedgerOutcome.CORRECT -> "✓"
            LedgerOutcome.INCORRECT, LedgerOutcome.INVALIDATED -> "✗"
            LedgerOutcome.SIDEWAYS, LedgerOutcome.WAIT_FLAT -> "↔"
            LedgerOutcome.WAIT_MISSED -> "missed"
            LedgerOutcome.MARKET_CLOSED -> "closed"
            LedgerOutcome.DATA_FAILURE -> "no data"
            LedgerOutcome.PENDING -> "…"
        }
    }
}
