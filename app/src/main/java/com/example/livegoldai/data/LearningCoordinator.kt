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
    /** V29A: per-timeframe source state (primary / proxy / restoring). Shared by the screen and the recorder. */
    val sourceGate = SourceStateMachine()
    private val pendingEvents = java.util.concurrent.ConcurrentLinkedQueue<LedgerEvent>()

    /** Call only while holding [busy]. */
    private fun flushPendingEvents() {
        val evs = ArrayList<LedgerEvent>()
        while (true) evs.add(pendingEvents.poll() ?: break)
        if (evs.isEmpty()) return
        if (safeAppend(evs.map { PredictionLedger.encodeEvent(it) })) {
            state = LedgerState(state.records, state.results, state.events + evs, state.skippedLines)
        }
    }

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
        const val SOURCE_PAUSE = "D-SOURCE"
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
        now: Long = System.currentTimeMillis(),
        mtfSource: String = "PAXG"
    ): GoldAnalysisResult {
        val mtf = RealityEngine.buildMtf(mtfCandles, mtfSource)
        // V29A source gate: only Twelve Data XAU/USD with clean data may publish or record an XAU/USD call
        val src = (analysis.chartCandles.ifEmpty { analysis.recentCandles }).map { SourceStateMachine.Candle(it.datetime, it.open, it.high, it.low, it.close) }
        val gate = sourceGate.evaluate(interval, analysis.feed?.source, src, RealityEngine.intervalMinutes(interval) * 60_000L,
            PredictionLedger.isMarketClosed(now), now, analysis.feed?.keyMissing == true, analysis.feed?.fetchedAtMs ?: now)
        if (gate.changedFrom != null) {
            // written inside the lock below, so the file and the in-memory ledger always hold the same events
            pendingEvents.add(LedgerEvent(now, "SOURCE_EVENT", interval, gate.state,
                "${gate.changedFrom.ifEmpty { "START" }} -> ${gate.state} • ${analysis.feed?.source ?: "--"} • ${gate.detail}"))
        }
        fun gated(a: GoldAnalysisResult) = a.copy(sourceState = gate.state, gateReason = gate.reason, gateDetail = gate.detail)
        if (!busy.compareAndSet(false, true)) {
            val snap = LearningEngine.snapshot(state, interval, now, lastReport)
            return gated(RealityEngine.apply(analysis, snap, state, mtf, interval, if (gate.allowed) "" else SOURCE_PAUSE))
        }
        try {
            flushPendingEvents()
            val newResults = verifyDue(now)
            if (newResults > 0) runCycle(now, newResults)

            // ---- draft of the prediction being shown now
            val newsActive = analysis.newsMode != null || analysis.newsTradingPlan?.isNewsActive == true || analysis.isNewsModeTriggered
            val ivMin = RealityEngine.intervalMinutes(interval)
            // V29A: the multi-timeframe candles are PAXG/USDT (another instrument): shown as context only,
            // never used inside an XAU/USD decision, filter or record
            val htf: Signal? = null
            // one prediction per candle: same bar id, or (if the data source switched) a record younger than ~one candle
            // V35.3-E: an OLDER candle's record no longer blocks this candle (see RecordLink.sameCandle)
            val existing = state.records.lastOrNull { RecordLink.sameCandle(it, interval, analysis.lastUpdated, now, ivMin) }
            val canRecord = !analysis.isSimulatedFallback && !PredictionLedger.isMarketClosed(now) && analysis.lastUpdated.isNotBlank()

            val draft = buildRecord(analysis, analysis, interval, now, newsActive, htf, "")
            // data integrity comes first: on UNSAFE data no BUY/SELL is published, and nothing is recorded
            val dq = DataQualityEngine.assess(analysis.recentCandles, analysis.feed?.fetchedAtMs ?: 0L, !analysis.isSimulatedFallback, now, PredictionLedger.isMarketClosed(now))
            lastDataQuality = dq
            val filter = when {
                !gate.allowed -> SOURCE_PAUSE
                dq.unsafe && !analysis.isSimulatedFallback -> DATA_PAUSE
                else -> existing?.appliedFilter ?: LearningEngine.firingFilter(state, draft)
            }

            val snap1 = LearningEngine.snapshot(state, interval, now, lastReport)
            val applied = RealityEngine.apply(analysis, snap1, state, mtf, interval, filter)

            if (existing == null && canRecord && filter != DATA_PAUSE && filter != SOURCE_PAUSE) {
                val rec = buildRecord(analysis, applied, interval, now, newsActive, htf, filter)
                if (safeAppend(listOf(PredictionLedger.encodeRecord(rec)))) {
                    state = LedgerState(state.records + rec, state.results, state.events, state.skippedLines)
                }
            }
            val snap2 = LearningEngine.snapshot(state, interval, now, lastReport)
            return gated(applied.copy(learning = snap2))
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

    /**
     * V35.7.1 (owner-confirmed "Reset prediction history"): empties the ledger file and every in-memory result.
     * Waits (up to [timeoutMs]) until no refresh / recorder cycle holds the lock, so nothing is written half-way;
     * returns false if the lock could not be taken (nothing changed then).
     */
    suspend fun resetAll(timeoutMs: Long = 20_000L): Boolean {
        val until = System.currentTimeMillis() + timeoutMs
        while (!busy.compareAndSet(false, true)) {
            if (System.currentTimeMillis() > until) return false
            kotlinx.coroutines.delay(100L)
        }
        try {
            store.clear()
            state = LedgerState(emptyList(), emptyMap(), emptyList())
            pendingEvents.clear()
            lastTriedAt.clear()
            lastReport = listOf("Prediction history was reset by the owner. Waiting for the first predictions to expire.")
            lastVerifyAttemptAt = 0L; lastVerifiedCount = 0; totalVerifiedThisSession = 0; consecutiveFetchFailures = 0; lastCycleAt = 0L
            return true
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

    /** V35.7: when each unchecked record was last tried (memory only), so a stuck one cannot block the queue */
    private val lastTriedAt = HashMap<String, Long>()

    private suspend fun verifyDue(now: Long, limit: Int = MAX_CHECKS_PER_REFRESH): Int {
        // V35.7: least-recently-tried first, then oldest. Before: always the oldest [limit] due records, so records whose
        // price history never comes (e.g. made in the daily trading break) kept every slot for up to 72 h and newer
        // calls stayed "running" for hours (phone: 12 waiting, oldest 12 h).
        val due = state.records.filter { state.resultOf(it.id) == null && now >= it.expiresAt + SETTLE_MS }
            .sortedWith(compareBy({ lastTriedAt[it.id] ?: 0L }, { it.expiresAt }))
            .take(limit)
        if (due.isEmpty()) return 0
        lastVerifyAttemptAt = now
        val newResults = LinkedHashMap<String, LedgerResult>()
        for (r in due) {
            lastTriedAt[r.id] = now
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
                            PredictionLedger.sourceCheck(r, raw, noise).let { checked ->
                                // V34.2: separate plan check (v2); only on the same instrument, where absolute levels are comparable
                                if (r.planMethod.isEmpty()) checked
                                else checked.copy(planOutcome = if (checked.sourceMatch == "SAME") PredictionLedger.planOutcome(r, path) else PredictionLedger.PLAN_NOT_COMPARABLE)
                            }
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
        newResults.keys.forEach { lastTriedAt.remove(it) }
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
        // V35.0: an inactive pillar (Macro outside its news window) is not a vote: no "grp:" entry. Macro's own raw vote
        // is always kept as "ctx:macro" (research only), so its news-window and off-window behaviour can be compared.
        raw.groups.forEach { g -> if (g.active) sources["grp:${g.key}"] = g.verdict }
        raw.groups.firstOrNull { it.key == "macro" }?.let { g ->
            sources["ctx:macro"] = g.rawVerdict ?: g.verdict
            // V35.3-F: votes from the inverted EUR/USD proxy are tracked apart from dollar-index votes
            if (g.indicators.any { it.name == NewsMacro.PROXY_NAME }) sources["ctx:macroProxy"] = g.verdict
        }
        shown.multiAiConsensus?.modelInsights?.forEach { sources["eng:${it.provider.name}"] = it.signal }
        shown.multiBotEnsemble?.allBots?.forEach { sources["bot:${it.id}"] = it.signal }
        shown.multiBotEnsemble?.let { sources["bots:ensemble"] = it.ensembleSignal }
        raw.quantBotSignal?.let { sources["quant"] = it.signal }
        raw.nextPrediction?.let { sources["playbook"] = it.verdict }
        // V29A: no PAXG/USDT multi-timeframe votes in an XAU/USD record
        if (htf != null && PredictionLedger.instrumentOf(raw.feed?.source ?: "") != "XAU") sources["mtf:higher"] = htf
        // V35.8 Precision Lab: the three shadow challengers' calls, decided now and stored with the record (never shown)
        sources.putAll(PrecisionLab.decide(if (filter.isNotEmpty()) Signal.WAIT else rawSig, raw.agreementPercent.toInt(),
            PrecisionLab.stretch(candles, atr), PredictionLedger.regimeOf(candles, newsActive), newsActive, interval))

        return LedgerRecord(
            id = "P" + java.lang.Long.toString(now, 36).uppercase() + interval.uppercase(),
            createdAt = now,
            expiresAt = now + horizon * 60_000L,
            interval = interval,
            horizonMin = horizon,
            barId = raw.lastUpdated,
            price = raw.currentPrice,
            // V35.13: when that price was observed (provider quote time when plausible, else download time)
            priceAtMs = raw.feed?.let { f -> if (f.quoteTimeMs > 0L && f.quoteTimeMs <= now + 60_000L) f.quoteTimeMs else f.fetchedAtMs } ?: 0L,
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
        ).let { r ->
            // V34.2: also keep the exact plan levels of this call (the ones the screen shows), for the separate plan check
            val fin = r.finalSignal
            val ok = planOk && fin == rawSig && fin != Signal.WAIT && listOf(ts.entryPrice, ts.stopLoss, ts.takeProfit1, ts.takeProfit2).all { it.isFinite() && it > 0.0 } &&
                (if (fin == Signal.SELL) ts.stopLoss > ts.entryPrice && ts.takeProfit1 < ts.entryPrice else ts.stopLoss < ts.entryPrice && ts.takeProfit1 > ts.entryPrice)
            if (!ok) r else r.copy(planEntry = ts.entryPrice, planStop = ts.stopLoss, planTp1 = ts.takeProfit1, planTp2 = ts.takeProfit2, planMethod = PredictionLedger.PLAN_METHOD_V21)   // V35.3-D: new records use PLAN-V2.1
        }
    }
}
