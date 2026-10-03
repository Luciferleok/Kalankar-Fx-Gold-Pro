package com.example.livegoldai.data

import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

/**
 * Glue between the screen refresh loop and the Prediction Ledger:
 *   1. check predictions whose time is over (real price path)
 *   2. run one safe learning cycle
 *   3. record the new prediction (one per candle per timeframe)
 *   4. rewrite the analysis with real numbers (RealityEngine)
 *
 * Call from a background thread. Not re-entrant: if a call is already running,
 * the next one only attaches the last snapshot.
 */
class LearningCoordinator(
    private val store: LedgerStore,
    private val fetchPath: suspend (Long, Long) -> PricePath?
) {
    /** Source-aware path fetch (record, now). When set it is used instead of [fetchPath]. */
    @Volatile var fetchPathFor: (suspend (LedgerRecord, Long) -> PricePath?)? = null

    @Volatile
    private var state: LedgerState = PredictionLedger.load(store)
    @Volatile
    private var lastReport: List<String> = listOf("Learning engine started. Waiting for the first predictions to expire.")
    private val busy = AtomicBoolean(false)

    // ---- observable activity (for the Health screen)
    @Volatile var lastVerifyAttemptAt: Long = 0L; private set
    @Volatile var lastVerifiedCount: Int = 0; private set
    @Volatile var totalVerifiedThisSession: Int = 0; private set
    @Volatile var consecutiveFetchFailures: Int = 0; private set
    @Volatile var lastCycleAt: Long = 0L; private set
    @Volatile var lastWriteOk: Boolean = true; private set
    @Volatile var lastWriteError: String = ""; private set

    data class Stats(
        val records: Int,
        val results: Int,
        val pending: Int,
        val overdue: Int,              // due more than 10 minutes ago and still unchecked
        val skippedLines: Int,
        val fileBytes: Long,
        val lastVerifyAttemptAt: Long,
        val lastVerifiedCount: Int,
        val totalVerifiedThisSession: Int,
        val consecutiveFetchFailures: Int,
        val lastCycleAt: Long,
        val lastWriteOk: Boolean,
        val lastWriteError: String,
        val activeFilters: Int
    )

    fun stats(now: Long = System.currentTimeMillis()): Stats {
        val st = state
        val pending = st.records.filter { st.resultOf(it.id) == null }
        return Stats(
            records = st.records.size,
            results = st.results.size,
            pending = pending.size,
            overdue = pending.count { now > it.expiresAt + SETTLE_MS + 10 * 60_000L },
            skippedLines = st.skippedLines,
            fileBytes = try { store.sizeBytes() } catch (_: Exception) { -1L },
            lastVerifyAttemptAt = lastVerifyAttemptAt,
            lastVerifiedCount = lastVerifiedCount,
            totalVerifiedThisSession = totalVerifiedThisSession,
            consecutiveFetchFailures = consecutiveFetchFailures,
            lastCycleAt = lastCycleAt,
            lastWriteOk = lastWriteOk,
            lastWriteError = lastWriteError,
            activeFilters = LearningEngine.activeFilterIds(st).size
        )
    }

    private fun safeAppend(lines: List<String>): Boolean = try {
        store.append(lines)
        lastWriteOk = true
        lastWriteError = ""
        true
    } catch (e: Exception) {
        lastWriteOk = false
        lastWriteError = e.message ?: e.javaClass.simpleName
        false
    }

    companion object {
        const val DATA_PAUSE = "D-UNSAFE"
        const val MAX_CHECKS_PER_REFRESH = 6
        const val GIVE_UP_AFTER_MS = 72 * 3_600_000L
        const val SETTLE_MS = 90_000L
    }

    fun currentState(): LedgerState = state

    /** Result of the last data-quality check (null before the first forecast). */
    @Volatile var lastDataQuality: DataQualityEngine.Report? = null
        private set

    suspend fun process(
        analysis: GoldAnalysisResult,
        interval: String,
        mtfCandles: Map<String, List<PathBar>>,
        now: Long = System.currentTimeMillis()
    ): GoldAnalysisResult {
        val mtf = RealityEngine.buildMtf(mtfCandles)
        if (!busy.compareAndSet(false, true)) {
            val snap = LearningEngine.snapshot(state, interval, now, lastReport)
            return RealityEngine.apply(analysis, snap, state, mtf, interval, "")
        }
        try {
            val newResults = verifyDue(now)
            if (newResults > 0) runCycle(now, newResults)

            // ---- draft of the prediction being shown now
            val newsActive = analysis.newsMode != null || analysis.newsTradingPlan?.isNewsActive == true || analysis.isNewsModeTriggered
            val ivMin = RealityEngine.intervalMinutes(interval)
            val htf = RealityEngine.higherTimeframeSignal(mtf, ivMin)
            // one prediction per candle: same bar id, or (if the data source switched) a record younger than ~one candle
            val existing = state.records.lastOrNull {
                it.interval == interval && (it.barId == analysis.lastUpdated || now - it.createdAt < ivMin * 60_000L * 9 / 10)
            }
            val canRecord = !analysis.isSimulatedFallback && !PredictionLedger.isMarketClosed(now) && analysis.lastUpdated.isNotBlank()

            val draft = buildRecord(analysis, analysis, interval, now, newsActive, htf, "")
            // data integrity comes first: on UNSAFE data no BUY/SELL is published, and nothing is recorded
            val dq = DataQualityEngine.assess(analysis.recentCandles, analysis.feed?.fetchedAtMs ?: 0L, !analysis.isSimulatedFallback, now, PredictionLedger.isMarketClosed(now))
            lastDataQuality = dq
            val filter = if (dq.unsafe && !analysis.isSimulatedFallback) DATA_PAUSE else (existing?.appliedFilter ?: LearningEngine.firingFilter(state, draft))

            val snap1 = LearningEngine.snapshot(state, interval, now, lastReport)
            val applied = RealityEngine.apply(analysis, snap1, state, mtf, interval, filter)

            if (existing == null && canRecord && filter != DATA_PAUSE) {
                val rec = buildRecord(analysis, applied, interval, now, newsActive, htf, filter)
                if (safeAppend(listOf(PredictionLedger.encodeRecord(rec)))) {
                    state = LedgerState(state.records + rec, state.results, state.events, state.skippedLines)
                }
            }
            val snap2 = LearningEngine.snapshot(state, interval, now, lastReport)
            return applied.copy(learning = snap2)
        } finally {
            busy.set(false)
        }
    }

    /** Background tick when no new prediction is due: only checks expired predictions. Returns how many were checked. */
    suspend fun verifyOnly(now: Long = System.currentTimeMillis()): Int {
        if (!busy.compareAndSet(false, true)) return 0
        try {
            val n = verifyDue(now)
            if (n > 0) runCycle(now, n)
            return n
        } finally {
            busy.set(false)
        }
    }

    /** "Run recalibration" button: checks everything that is due and runs one cycle. Returns what happened. */
    suspend fun recalibrateNow(now: Long = System.currentTimeMillis()): List<String> {
        if (!busy.compareAndSet(false, true)) return listOf("A learning cycle is already running. Try again in a few seconds.")
        try {
            val n = verifyDue(now, limit = 50)
            runCycle(now, n)
            return lastReport
        } finally {
            busy.set(false)
        }
    }

    private fun runCycle(now: Long, newResults: Int) {
        val audit = PredictionAudit.build(state)
        val out = LearningEngine.runCycle(state, now, newResults, audit.promotionLocked, audit.lockReason)
        if (out.newEvents.isNotEmpty()) {
            if (safeAppend(out.newEvents.map { PredictionLedger.encodeEvent(it) })) {
                state = LedgerState(state.records, state.results, state.events + out.newEvents, state.skippedLines)
            }
        }
        lastCycleAt = now
        lastReport = listOf("Cycle at ${PredictionLedger.utcLabel(now, true)}", audit.headline) + out.report
    }

    private suspend fun verifyDue(now: Long, limit: Int = MAX_CHECKS_PER_REFRESH): Int {
        val due = state.records.filter { state.resultOf(it.id) == null && now >= it.expiresAt + SETTLE_MS }
            .sortedBy { it.expiresAt }
            .take(limit)
        if (due.isEmpty()) return 0
        lastVerifyAttemptAt = now
        val newResults = LinkedHashMap<String, LedgerResult>()
        for (r in due) {
            val res: LedgerResult? = when {
                PredictionLedger.windowTouchesClosure(r.createdAt, r.expiresAt) ->
                    PredictionLedger.emptyResult(r, now, LedgerOutcome.MARKET_CLOSED, "weekend")
                else -> {
                    val path = try {
                        val sourceAware = fetchPathFor
                        if (sourceAware != null) sourceAware(r, now) else fetchPath(r.createdAt, r.expiresAt)
                    } catch (_: Exception) { null }
                    // "waiting" = same-source history not published yet: not a failure, simply try again later
                    if (path?.waiting == true && now <= r.expiresAt + GIVE_UP_AFTER_MS) continue
                    if (path == null || path.bars.isEmpty()) consecutiveFetchFailures++ else consecutiveFetchFailures = 0
                    when {
                        path != null && path.bars.isNotEmpty() -> {
                            val raw = PredictionLedger.evaluate(r, path, now)
                            // never judge blindly across instruments
                            val noise = PredictionLedger.basisNoise(
                                LedgerState(state.records, state.results + newResults, state.events),
                                PredictionLedger.instrumentOf(r.dataSource), PredictionLedger.instrumentOf(path.source), now
                            )
                            PredictionLedger.sourceCheck(r, raw, noise)
                        }
                        now > r.expiresAt + GIVE_UP_AFTER_MS -> PredictionLedger.emptyResult(r, now, LedgerOutcome.DATA_FAILURE, "no data")
                        else -> null // try again on the next refresh
                    }
                }
            }
            if (res != null) newResults[r.id] = res
        }
        lastVerifiedCount = newResults.size
        if (newResults.isEmpty()) return 0
        if (!safeAppend(newResults.values.map { PredictionLedger.encodeResult(it) })) return 0
        state = LedgerState(state.records, state.results + newResults, state.events, state.skippedLines)
        totalVerifiedThisSession += newResults.size
        return newResults.size
    }

    private fun buildRecord(
        raw: GoldAnalysisResult,
        shown: GoldAnalysisResult,
        interval: String,
        now: Long,
        newsActive: Boolean,
        htf: Signal?,
        filter: String
    ): LedgerRecord {
        val candles = raw.recentCandles
        val atr = PredictionLedger.atrOf(candles).let { if (it > 0) it else max(raw.tradeSetup.atrPips / 10.0, 0.5) }
        val rawSig = raw.overallSignal
        val ts = raw.tradeSetup
        // Distances are measured from the price the prediction was made at (that is where the check starts),
        // to the trade plan's real stop and first target. A level on the wrong side of the price is ignored.
        val px = raw.currentPrice
        val planOk = ts.signal == rawSig && rawSig != Signal.WAIT
        val slSide = if (rawSig == Signal.BUY) px - ts.stopLoss else ts.stopLoss - px
        val tpSide = if (rawSig == Signal.BUY) ts.takeProfit1 - px else px - ts.takeProfit1
        val sl = if (planOk && ts.stopLoss > 0 && slSide >= 0.5 * atr) slSide else 1.5 * atr
        val tp = if (rawSig == Signal.WAIT) 0.0 else if (planOk && ts.takeProfit1 > 0 && tpSide >= 0.5 * atr) tpSide else 1.5 * atr
        val horizon = com.example.livegoldai.data.TechnicalEngine.calculateValidityMinutes(interval)

        val sources = LinkedHashMap<String, Signal>()
        raw.groups.forEach { sources["grp:${it.key}"] = it.verdict }
        shown.multiAiConsensus?.modelInsights?.forEach { sources["eng:${it.provider.name}"] = it.signal }
        shown.multiBotEnsemble?.allBots?.forEach { sources["bot:${it.id}"] = it.signal }
        shown.multiBotEnsemble?.let { sources["bots:ensemble"] = it.ensembleSignal }
        raw.quantBotSignal?.let { sources["quant"] = it.signal }
        raw.nextPrediction?.let { sources["playbook"] = it.verdict }
        shown.mtfMatrix?.timeframes?.forEach { if (!it.label.startsWith("This chart")) sources["mtf:${it.timeframe}"] = it.signal }
        if (htf != null) sources["mtf:higher"] = htf

        return LedgerRecord(
            id = "P" + java.lang.Long.toString(now, 36).uppercase() + interval.uppercase(),
            createdAt = now,
            expiresAt = now + horizon * 60_000L,
            interval = interval,
            horizonMin = horizon,
            barId = raw.lastUpdated,
            price = raw.currentPrice,
            rawSignal = rawSig,
            finalSignal = if (filter.isNotEmpty()) Signal.WAIT else rawSig,
            confidence = raw.agreementPercent.toInt(),
            atr = atr,
            slDistance = sl,
            regime = PredictionLedger.regimeOf(candles, newsActive),
            session = PredictionLedger.sessionOf(now),
            newsActive = newsActive,
            modelVersion = LearningEngine.modelVersion(state),
            appliedFilter = filter,
            sources = sources,
            dataSource = raw.feed?.source ?: "",
            snapshotId = "S-$interval-${raw.lastUpdated.replace(" ", "T")}",
            engineVersion = PredictionLedger.ENGINE_VERSION,
            featureVersion = com.example.livegoldai.data.brain.FeatureCatalog.VERSION,
            tpDistance = tp
        )
    }
}
