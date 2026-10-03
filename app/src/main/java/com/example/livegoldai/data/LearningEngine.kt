package com.example.livegoldai.data

import com.example.livegoldai.model.AccuracyWindow
import com.example.livegoldai.model.BucketStat
import com.example.livegoldai.model.CorrectionCandidate
import com.example.livegoldai.model.FailureReport
import com.example.livegoldai.model.LearningSnapshot
import com.example.livegoldai.model.ModelHistoryEvent
import com.example.livegoldai.model.Signal
import com.example.livegoldai.model.TimelineEvent
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Turns the Prediction Ledger into real statistics and runs the safe correction pipeline:
 *   DISCOVER (on past ledger data) -> SHADOW (on new predictions only) -> PROMOTE -> monitor -> ROLLBACK.
 * Nothing changes production unless the evidence passes every gate.
 */
object LearningEngine {

    const val MIN_SAMPLES = 50          // blocked predictions needed before a rule can even be a candidate
    const val SHADOW_SAMPLES = 30       // new predictions the rule must also win on before promotion
    const val ROLLBACK_SAMPLES = 30
    const val MIN_IMPROVEMENT = 3.0     // accuracy points
    const val MAX_ACTIVE_FILTERS = 3

    data class Hypothesis(
        val id: String,
        val en: String,
        val hi: String,
        val mr: String,
        val rule: String,
        val fires: (LedgerRecord) -> Boolean
    )

    private val groupNames = linkedMapOf(
        "trend" to Triple("Trend", "ट्रेंड", "ट्रेंड"),
        "smc" to Triple("Smart Money", "स्मार्ट मनी", "स्मार्ट मनी"),
        "momentum" to Triple("Momentum", "मोमेंटम", "मोमेंटम"),
        "sr" to Triple("Support/Resistance", "सपोर्ट/रेजिस्टेंस", "सपोर्ट/रेझिस्टन्स"),
        "volatility" to Triple("Volatility", "वोलैटिलिटी", "व्होलॅटिलिटी"),
        "candlestick" to Triple("Candlestick", "कैंडलस्टिक", "कँडलस्टिक"),
        "macro" to Triple("Macro", "मैक्रो", "मॅक्रो")
    )

    private fun directional(r: LedgerRecord) = r.rawSignal == Signal.BUY || r.rawSignal == Signal.SELL

    val hypotheses: List<Hypothesis> = buildList {
        for (cut in listOf(60, 70, 80)) {
            add(Hypothesis("C-AGR$cut", "Low agreement filter (<$cut%)", "कम सहमति फ़िल्टर (<$cut%)", "कमी सहमती फिल्टर (<$cut%)",
                "If agreement < $cut% -> show WAIT") { directional(it) && it.confidence < cut })
        }
        add(Hypothesis("C-RANGE", "Range market filter", "रेंज मार्केट फ़िल्टर", "रेंज मार्केट फिल्टर",
            "If regime = RANGE -> show WAIT") { directional(it) && it.regime == "RANGE" })
        add(Hypothesis("C-VOLX", "Volatility spike filter", "वोलैटिलिटी स्पाइक फ़िल्टर", "व्होलॅटिलिटी स्पाइक फिल्टर",
            "If last candle range > 2.2x ATR -> show WAIT") { directional(it) && it.regime == "VOLATILITY_EXPANSION" })
        add(Hypothesis("C-ASIAN", "Asian session filter", "एशियन सेशन फ़िल्टर", "एशियन सेशन फिल्टर",
            "If session = ASIAN -> show WAIT") { directional(it) && it.session == "ASIAN" })
        add(Hypothesis("C-NEWS", "News window filter", "न्यूज़ विंडो फ़िल्टर", "न्यूज विंडो फिल्टर",
            "If News Mode active -> show WAIT") { directional(it) && it.newsActive })
        add(Hypothesis("C-HTF", "Higher-timeframe conflict filter", "बड़े टाइमफ्रेम विरोध फ़िल्टर", "मोठ्या टाइमफ्रेम विरोध फिल्टर",
            "If next higher timeframe points the other way -> show WAIT") { r ->
            directional(r) && r.sources["mtf:higher"]?.let { PredictionLedger.opposite(it, r.rawSignal) } == true
        })
        for ((key, names) in groupNames) {
            add(Hypothesis("C-GRP-$key", "${names.first} disagreement filter", "${names.second} विरोध फ़िल्टर", "${names.third} विरोध फिल्टर",
                "If ${names.first} pillar points the other way -> show WAIT") { r ->
                directional(r) && r.sources["grp:$key"]?.let { PredictionLedger.opposite(it, r.rawSignal) } == true
            })
        }
    }

    // ------------------------------------------------------------------ basic counting

    private fun isFinalDecided(o: LedgerOutcome) =
        o == LedgerOutcome.CORRECT || o == LedgerOutcome.INCORRECT || o == LedgerOutcome.INVALIDATED

    private fun hasPriceData(o: LedgerOutcome) = PredictionLedger.reliable(o)

    private fun pct(c: Int, n: Int): Double = if (n == 0) -1.0 else c * 100.0 / n

    private fun wilson(c: Int, n: Int): Pair<Double, Double> {
        if (n < 10) return -1.0 to -1.0
        val z = 1.96
        val p = c.toDouble() / n
        val den = 1 + z * z / n
        val centre = (p + z * z / (2 * n)) / den
        val half = z * sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / den
        return (centre - half) * 100 to (centre + half) * 100
    }

    private fun bucket(key: String, label: String, c: Int, n: Int, avgConf: Double = -1.0): BucketStat {
        val (lo, hi) = wilson(c, n)
        return BucketStat(key, label, n, c, pct(c, n), lo, hi, avgConf)
    }

    /** (record, result) pairs where the shown signal was BUY/SELL and the result is decided. */
    private fun finalDecided(state: LedgerState): List<Pair<LedgerRecord, LedgerResult>> =
        state.records.mapNotNull { r -> state.resultOf(r.id)?.let { res -> if (isFinalDecided(res.outcome)) r to res else null } }

    /** Raw-signal direction score for candidate testing: +1 / 0 / -1(undecided). */
    private fun rawScore(r: LedgerRecord, res: LedgerResult): Int =
        if (!hasPriceData(res.outcome)) -1 else PredictionLedger.directionScore(r.rawSignal, res.move, res.threshold)

    // ------------------------------------------------------------------ candidate pipeline

    private data class Eval(val blockedN: Int, val blockedC: Int, val keptN: Int, val keptC: Int, val totalN: Int, val totalC: Int) {
        val baseline get() = pct(totalC, totalN)
        val blockedAcc get() = pct(blockedC, blockedN)
        val keptAcc get() = pct(keptC, keptN)
        val improvement get() = if (keptN == 0 || totalN == 0) 0.0 else keptAcc - baseline
        /** 95% upper bound of the blocked predictions' accuracy is below the baseline -> not just luck. */
        val significant get() = blockedN >= 10 && upperBound(blockedC, blockedN) < baseline
    }

    private fun upperBound(c: Int, n: Int): Double = wilsonRaw(c, n).second

    private fun wilsonRaw(c: Int, n: Int): Pair<Double, Double> {
        if (n == 0) return 0.0 to 100.0
        val z = 1.96
        val p = c.toDouble() / n
        val den = 1 + z * z / n
        val centre = (p + z * z / (2 * n)) / den
        val half = z * sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / den
        return (centre - half) * 100 to (centre + half) * 100
    }

    private fun evaluate(h: Hypothesis, state: LedgerState, since: Long): Eval {
        var bn = 0; var bc = 0; var kn = 0; var kc = 0
        for (r in state.records) {
            if (r.createdAt < since || !directional(r)) continue
            val res = state.resultOf(r.id) ?: continue
            val s = rawScore(r, res)
            if (s < 0) continue
            if (h.fires(r)) { bn++; bc += s } else { kn++; kc += s }
        }
        return Eval(bn, bc, kn, kc, bn + kn, bc + kc)
    }

    /** Start time of the window holding the last [n] blocked, decided predictions after [since]. */
    private fun recentWindowStart(h: Hypothesis, state: LedgerState, since: Long, n: Int): Long {
        val fired = state.records.filter { r ->
            r.createdAt >= since && directional(r) && h.fires(r) &&
                (state.resultOf(r.id)?.let { rawScore(r, it) >= 0 } == true)
        }
        return if (fired.size <= n) since else fired[fired.size - n].createdAt
    }

    private fun lastEvent(state: LedgerState, cid: String): LedgerEvent? =
        state.events.lastOrNull { it.kind == "cand" && it.candidateId == cid }

    fun activeFilterIds(state: LedgerState): List<String> =
        hypotheses.filter { lastEvent(state, it.id)?.stage == "PROMOTED" }.map { it.id }

    /**
     * Filters the owner switched on directly (V15.2). They do not wait for 50 samples + a shadow test,
     * because the owner asked for them; the ledger still stores the raw BUY/SELL next to the shown WAIT,
     * so the audit can prove later whether the blocked calls really would have been wrong.
     */
    val OWNER_FILTERS = listOf("C-RANGE")
    @Volatile var ownerFiltersOn: Boolean = true
    fun isOwnerFilter(id: String): Boolean = id in OWNER_FILTERS

    /** Returns the id of the first owner / promoted filter that blocks this draft record, or "". */
    fun firingFilter(state: LedgerState, draft: LedgerRecord): String {
        val owner = if (ownerFiltersOn) OWNER_FILTERS else emptyList()
        for (id in (owner + activeFilterIds(state)).distinct()) {
            val h = hypotheses.firstOrNull { it.id == id } ?: continue
            if (h.fires(draft)) return id
        }
        return ""
    }

    fun modelVersion(state: LedgerState): String {
        val n = activeFilterIds(state).size
        return "Base rules v1 + $n learned filter${if (n == 1) "" else "s"}" + if (ownerFiltersOn) " + range filter" else ""
    }

    data class CycleOutput(val newEvents: List<LedgerEvent>, val report: List<String>)

    /** One learning cycle. Only appends events; never edits past data. */
    fun runCycle(state: LedgerState, now: Long, newResults: Int, promotionLocked: Boolean = false, lockReason: String = ""): CycleOutput {
        val events = ArrayList<LedgerEvent>()
        val report = ArrayList<String>()
        report.add("Checked results: $newResults new, ${finalDecided(state).size} decided in total.")

        var shadowRunning = hypotheses.any { lastEvent(state, it.id)?.stage == "SHADOW" }
        var active = activeFilterIds(state).size
        val eligible = ArrayList<Pair<Hypothesis, Eval>>()

        for (h in hypotheses) {
            val last = lastEvent(state, h.id)
            when (last?.stage) {
                "SHADOW" -> {
                    val e = evaluate(h, state, last.at)
                    val passes = e.blockedAcc <= 45.0 && e.keptAcc - e.blockedAcc >= 10.0 && e.improvement >= 2.0
                    val clearlyFails = e.blockedAcc >= e.keptAcc - 2.0
                    if (e.blockedN >= SHADOW_SAMPLES && (passes || clearlyFails || e.blockedN >= 2 * SHADOW_SAMPLES)) {
                        if (passes && promotionLocked) {
                            // model health gate: nothing may change live signals while the engine itself is under review
                            report.add("PROMOTION LOCKED for ${h.id}: $lockReason.")
                        } else if (passes) {
                            events.add(LedgerEvent(now, "cand", h.id, "PROMOTED",
                                "Shadow test passed: blocked ${e.blockedN} predictions that were only ${fmt1(e.blockedAcc)}% correct; accuracy ${fmt1(e.baseline)}% -> ${fmt1(e.keptAcc)}%"))
                            report.add("PROMOTED ${h.id}: ${h.en}.")
                            active++
                        } else {
                            events.add(LedgerEvent(now, "cand", h.id, "REJECTED",
                                "Shadow test failed: blocked predictions were ${fmt1(e.blockedAcc)}% correct vs ${fmt1(e.keptAcc)}% for the rest (needs <= 45% and 10 points lower)"))
                            report.add("REJECTED ${h.id} after shadow test.")
                        }
                        shadowRunning = false
                    } else {
                        report.add("${h.id} in shadow test: ${e.blockedN}/$SHADOW_SAMPLES samples.")
                    }
                }
                "PROMOTED" -> {
                    // judge on the most recent blocked predictions, so a changed market is noticed quickly
                    val e = evaluate(h, state, recentWindowStart(h, state, last.at, 2 * ROLLBACK_SAMPLES))
                    if (e.blockedN >= ROLLBACK_SAMPLES && e.blockedAcc >= e.keptAcc - 2.0) {
                        events.add(LedgerEvent(now, "cand", h.id, "ROLLED_BACK",
                            "After promotion the blocked predictions were ${fmt1(e.blockedAcc)}% correct vs ${fmt1(e.keptAcc)}% for the rest, so the filter stopped helping. Removed."))
                        report.add("ROLLED BACK ${h.id}: it stopped helping.")
                        active--
                    }
                }
                else -> {
                    val since = last?.at ?: 0L
                    val e = evaluate(h, state, since)
                    if (e.blockedN >= MIN_SAMPLES && e.improvement >= MIN_IMPROVEMENT && e.blockedAcc <= 45.0 && e.significant) eligible.add(h to e)
                }
            }
        }

        if (!shadowRunning && active < MAX_ACTIVE_FILTERS && eligible.isNotEmpty()) {
            val (h, e) = eligible.maxByOrNull { it.second.improvement }!!
            events.add(LedgerEvent(now, "cand", h.id, "SHADOW",
                "Found on ${e.blockedN} past predictions: blocked ones were ${fmt1(e.blockedAcc)}% correct; accuracy would go ${fmt1(e.baseline)}% -> ${fmt1(e.keptAcc)}%. Now testing silently on new predictions."))
            report.add("SHADOW TEST STARTED ${h.id}: ${h.en}.")
        }

        if (events.isEmpty()) report.add("NO SAFE RECALIBRATION REQUIRED: no rule has enough evidence to change the model.")
        return CycleOutput(events, report)
    }

    // ------------------------------------------------------------------ snapshot for the UI

    fun snapshot(state: LedgerState, interval: String, now: Long, lastReport: List<String>): LearningSnapshot {
        val all = state.records
        val mine = all.filter { it.interval == interval }
        val results = state.results

        fun outcomeOf(r: LedgerRecord) = results[r.id]?.outcome ?: LedgerOutcome.PENDING

        val decidedMine = mine.filter { isFinalDecided(outcomeOf(it)) }.sortedBy { it.createdAt }
        fun isWin(r: LedgerRecord) = outcomeOf(r) == LedgerOutcome.CORRECT

        fun window(label: String, list: List<LedgerRecord>): AccuracyWindow {
            val c = list.count { isWin(it) }
            return AccuracyWindow(label, list.size, c, pct(c, list.size))
        }

        val dayStart = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val day = 86_400_000L
        val today = window("Today", decidedMine.filter { it.createdAt >= dayStart })
        val windows = listOf(
            window("Last 20", decidedMine.takeLast(20)),
            window("Last 50", decidedMine.takeLast(50)),
            window("Last 100", decidedMine.takeLast(100)),
            window("Last 250", decidedMine.takeLast(250)),
            today,
            window("7 Days", decidedMine.filter { it.createdAt >= now - 7 * day }),
            window("30 Days", decidedMine.filter { it.createdAt >= now - 30 * day }),
            // same scope as the CHECKED / CORRECT / WRONG tiles: every timeframe
            window("30 Days All", all.filter { isFinalDecided(outcomeOf(it)) && it.createdAt >= now - 30 * day }),
            window("All Time", decidedMine)
        )

        // streaks
        var cur = 0; var best = 0
        for (r in decidedMine) {
            cur = if (isWin(r)) (if (cur >= 0) cur + 1 else 1) else (if (cur <= 0) cur - 1 else -1)
            if (cur > best) best = cur
        }

        // Brier
        fun brier(list: List<LedgerRecord>): Double {
            if (list.isEmpty()) return -1.0
            return list.map { r ->
                val p = (r.confidence / 100.0).coerceIn(0.01, 0.99)
                val y = if (isWin(r)) 1.0 else 0.0
                (p - y) * (p - y)
            }.average()
        }

        // calibration
        val bands = listOf(50 to 59, 60 to 69, 70 to 79, 80 to 89, 90 to 100)
        val calibration = bands.map { (lo, hi) ->
            val list = decidedMine.filter { it.confidence in lo..hi }
            val c = list.count { isWin(it) }
            bucket("$lo-$hi", "$lo-$hi%", c, list.size, if (list.isEmpty()) -1.0 else list.map { it.confidence }.average())
        }
        val calibUsable = calibration.filter { it.decided >= 10 }
        val calibErr = if (calibUsable.isEmpty()) -1.0 else
            calibUsable.sumOf { abs(it.avgConfidence - it.accuracyPercent) * it.decided } / calibUsable.sumOf { it.decided }

        // breakdowns (all timeframes)
        val decidedAll = all.filter { isFinalDecided(outcomeOf(it)) }
        fun breakdown(keyOf: (LedgerRecord) -> String): List<BucketStat> =
            decidedAll.groupBy(keyOf).map { (k, list) -> bucket(k, k.replace('_', ' '), list.count { isWin(it) }, list.size) }
                .sortedByDescending { it.decided }

        val byInterval = breakdown { it.interval }
        val bySession = breakdown { it.session }
        val byRegime = breakdown { it.regime }
        val byNews = breakdown { if (it.newsActive) "NEWS_WINDOW" else "NORMAL" }

        // every signal source, direction-only accuracy
        val srcCount = HashMap<String, IntArray>()
        for (r in all) {
            val res = results[r.id] ?: continue
            if (!hasPriceData(res.outcome)) continue
            val entries = r.sources.entries.toMutableList()
            entries.add(java.util.AbstractMap.SimpleEntry("main:raw", r.rawSignal))
            for ((k, s) in entries) {
                val score = PredictionLedger.directionScore(s, res.move, res.threshold)
                if (score < 0) continue
                val arr = srcCount.getOrPut(k) { IntArray(2) }
                arr[0]++; arr[1] += score
            }
        }
        val bySource = srcCount.map { (k, a) -> bucket(k, PredictionLedger.prettySource(k), a[1], a[0]) }.sortedBy { it.key }

        // failures
        val failures = all.asReversed().mapNotNull { r ->
            val res = results[r.id] ?: return@mapNotNull null
            if (res.outcome != LedgerOutcome.INCORRECT && res.outcome != LedgerOutcome.INVALIDATED) return@mapNotNull null
            FailureReport(
                id = r.id, createdLabel = PredictionLedger.utcLabel(r.createdAt, withDay = true), interval = r.interval,
                signal = r.finalSignal, confidence = r.confidence, entryPrice = r.price, movePoints = res.move,
                thresholdPoints = res.threshold, maxFavorable = res.mfe, maxAdverse = res.mae, outcome = res.outcome.name,
                regime = r.regime, session = r.session, newsActive = r.newsActive, tags = res.tags,
                attribution = res.attribution, warningsIgnored = res.warnings.map { PredictionLedger.prettySource(it) },
                counterfactual = res.counterfactual,
                timeline = res.timeline.map { TimelineEvent(PredictionLedger.utcLabel(it.first, withDay = r.horizonMin > 600), it.second) },
                verifySource = res.verifySource
            )
        }
        val clusterCounts = LinkedHashMap<String, Int>()
        for (f in failures) for (t in f.tags) clusterCounts[t] = (clusterCounts[t] ?: 0) + 1
        val clusters = clusterCounts.entries.sortedByDescending { it.value }
            .map { BucketStat(it.key, it.key.replace('_', ' '), it.value, 0, -1.0) }

        // candidates
        val candidates = hypotheses.map { h -> candidateView(h, state) }
            .sortedWith(compareBy({ stageOrder(it.stage) }, { -it.affectedSamples }))

        val history = state.events.filter { it.kind == "cand" }.asReversed().map {
            ModelHistoryEvent(PredictionLedger.utcLabel(it.at, withDay = true), it.stage, "${it.candidateId}: ${it.detail}")
        }

        // drift
        val (driftLevel, driftDetail) = if (decidedAll.size < 100) {
            "NOT_ENOUGH_DATA" to "Needs 100 decided predictions (have ${decidedAll.size})."
        } else {
            val sorted = decidedAll.sortedBy { it.createdAt }
            val recent = sorted.takeLast(50)
            val prior = sorted.dropLast(50)
            val ra = pct(recent.count { isWin(it) }, recent.size)
            val pa = pct(prior.count { isWin(it) }, prior.size)
            val drop = pa - ra
            val lvl = when { drop >= 10 -> "HIGH"; drop >= 5 -> "MODERATE"; else -> "LOW" }
            lvl to "Last 50: ${fmt1(ra)}% vs earlier: ${fmt1(pa)}%"
        }

        val engineState = when {
            decidedAll.isEmpty() -> "OBSERVING"
            candidates.any { it.stage == "SHADOW" } -> "SHADOW_TESTING"
            candidates.any { it.stage == "CANDIDATE" } -> "CANDIDATE_FOUND"
            history.firstOrNull()?.let { it.event == "PROMOTED" } == true && state.events.last().at > now - day -> "PROMOTED"
            history.firstOrNull()?.let { it.event == "ROLLED_BACK" } == true && state.events.last().at > now - day -> "ROLLED_BACK"
            else -> "COLLECTING_SAMPLE"
        }

        val pending = all.filter { outcomeOf(it) == LedgerOutcome.PENDING }
        val nextDue = pending.minOfOrNull { it.expiresAt }
        val pendingNote = if (nextDue == null) "No active predictions." else
            "${pending.size} active. Next result due ${PredictionLedger.utcLabel(nextDue, withDay = true)}."

        fun countOf(vararg o: LedgerOutcome) = all.count { outcomeOf(it) in o }

        return LearningSnapshot(
            generatedAtLabel = PredictionLedger.utcLabel(now, withDay = true),
            modelVersion = modelVersion(state),
            engineState = engineState,
            totalRecorded = all.size,
            active = pending.size,
            correct = countOf(LedgerOutcome.CORRECT),
            wrong = countOf(LedgerOutcome.INCORRECT, LedgerOutcome.INVALIDATED),
            invalidated = countOf(LedgerOutcome.INVALIDATED),
            sideways = countOf(LedgerOutcome.SIDEWAYS),
            waitCalls = countOf(LedgerOutcome.WAIT_FLAT, LedgerOutcome.WAIT_MISSED),
            waitAvoidedMove = countOf(LedgerOutcome.WAIT_FLAT),
            waitMissedMove = countOf(LedgerOutcome.WAIT_MISSED),
            dataFailures = countOf(LedgerOutcome.DATA_FAILURE),
            marketClosed = countOf(LedgerOutcome.MARKET_CLOSED),
            currentStreak = cur,
            bestStreak = best,
            windows = windows,
            today = today,
            brierAll = brier(decidedMine),
            brier7d = brier(decidedMine.filter { it.createdAt >= now - 7 * day }),
            brier30d = brier(decidedMine.filter { it.createdAt >= now - 30 * day }),
            calibrationErrorPoints = calibErr,
            calibration = calibration,
            byInterval = byInterval,
            bySession = bySession,
            byRegime = byRegime,
            byNews = byNews,
            bySource = bySource,
            failures = failures.take(40),
            failureClusters = clusters,
            candidates = candidates,
            history = history,
            driftLevel = driftLevel,
            driftDetail = driftDetail,
            minimumSamples = MIN_SAMPLES,
            lastCycleReport = lastReport,
            pendingNote = pendingNote
        )
    }

    private fun stageOrder(s: String) = when (s) {
        "PROMOTED" -> 0; "SHADOW" -> 1; "CANDIDATE" -> 2; "COLLECTING" -> 3; "ROLLED_BACK" -> 4; "REJECTED" -> 5; else -> 6
    }

    private fun candidateView(h: Hypothesis, state: LedgerState): CorrectionCandidate {
        val last = lastEvent(state, h.id)
        val stageFromEvent = last?.stage
        val since = if (stageFromEvent == "REJECTED" || stageFromEvent == "ROLLED_BACK") last.at else 0L
        val e = evaluate(h, state, since)
        val shadow = if (stageFromEvent == "SHADOW" || stageFromEvent == "PROMOTED") evaluate(h, state, last.at) else null
        val stage = when (stageFromEvent) {
            "SHADOW" -> "SHADOW"
            "PROMOTED" -> "PROMOTED"
            else -> when {
                e.blockedN < MIN_SAMPLES -> if (stageFromEvent != null) stageFromEvent else "COLLECTING"
                e.improvement >= MIN_IMPROVEMENT && e.blockedAcc <= 45.0 && e.significant -> "CANDIDATE"
                else -> stageFromEvent ?: "COLLECTING"
            }
        }
        val necessity = when {
            stage == "PROMOTED" -> "CONFIRMED_SYSTEMIC"
            e.blockedN < MIN_SAMPLES -> "NOT_ENOUGH_DATA"
            e.improvement >= MIN_IMPROVEMENT && e.blockedAcc <= 45.0 && e.significant -> "LIKELY_SYSTEMIC"
            e.improvement >= MIN_IMPROVEMENT && e.blockedAcc <= 45.0 -> "POSSIBLE_ISSUE"
            e.blockedAcc >= e.baseline - 2.0 -> "NO_CORRECTION_NEEDED"
            else -> "POSSIBLE_ISSUE"
        }
        return CorrectionCandidate(
            id = h.id, titleEnglish = h.en, titleHindi = h.hi, titleMarathi = h.mr, ruleEnglish = h.rule,
            stage = stage, necessity = necessity, affectedSamples = e.blockedN,
            requiredSamples = if (stage == "SHADOW") SHADOW_SAMPLES else MIN_SAMPLES,
            blockedAccuracy = e.blockedAcc, baselineAccuracy = e.baseline, keptAccuracy = e.keptAcc,
            improvementPoints = e.improvement, shadowSamples = shadow?.blockedN ?: 0,
            shadowBlockedAccuracy = shadow?.blockedAcc ?: -1.0, note = last?.detail ?: ""
        )
    }

    private fun fmt1(v: Double) = if (v < 0) "--" else String.format(java.util.Locale.US, "%.1f", v)

    /** Real accuracy text for any source key, e.g. "58.0% (N=31)" or "untested (N=4)". */
    fun accuracyLabel(snapshot: LearningSnapshot?, key: String, minN: Int = 10): String {
        val b = snapshot?.sourceAccuracy(key) ?: return "untested (N=0)"
        return if (b.decided < minN) "untested (N=${b.decided})" else "${fmt1(b.accuracyPercent)}% (N=${b.decided})"
    }

    fun max0(v: Double) = max(0.0, v)
}
