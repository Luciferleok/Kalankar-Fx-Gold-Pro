package com.example.livegoldai.data

import com.example.livegoldai.model.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * REALITY ENGINE
 *
 * Replaces every accuracy / win-rate / "AI" number on screen with values that come from
 * the real Prediction Ledger or from real market data. Where there is not enough data,
 * it says so instead of inventing a number.
 */
object RealityEngine {

    private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)

    // ------------------------------------------------------------------ multi-timeframe (real)

    private val mtfOrder = listOf("5m" to 5, "15m" to 15, "1h" to 60, "4h" to 240, "1d" to 1440)

    private fun ema(values: List<Double>, period: Int): Double {
        if (values.isEmpty()) return 0.0
        val k = 2.0 / (period + 1)
        val seed = min(period, values.size)
        var e = values.take(seed).average()
        for (i in seed until values.size) e = values[i] * k + e * (1 - k)
        return e
    }

    private fun rsi(values: List<Double>, period: Int = 14): Double {
        if (values.size <= period) return 50.0
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val d = values[i] - values[i - 1]
            if (d >= 0) gain += d else loss -= d
        }
        gain /= period; loss /= period
        for (i in period + 1 until values.size) {
            val d = values[i] - values[i - 1]
            gain = (gain * (period - 1) + max(d, 0.0)) / period
            loss = (loss * (period - 1) + max(-d, 0.0)) / period
        }
        if (loss == 0.0) return 100.0
        val rs = gain / loss
        return 100 - 100 / (1 + rs)
    }

    /** Same rule the user back-tested: EMA9 vs EMA21 plus close vs EMA50. */
    fun trendRule(closes: List<Double>): Signal {
        if (closes.size < 55) return Signal.WAIT
        val e9 = ema(closes, 9); val e21 = ema(closes, 21); val e50 = ema(closes, 50)
        val c = closes.last()
        return when {
            e9 > e21 && c > e50 -> Signal.BUY
            e9 < e21 && c < e50 -> Signal.SELL
            else -> Signal.WAIT
        }
    }

    fun buildMtf(candles: Map<String, List<PathBar>>): MultiTimeframeMatrix? {
        if (candles.isEmpty()) return null
        val rows = mtfOrder.mapNotNull { (tf, _) ->
            val bars = candles[tf] ?: return@mapNotNull null
            val closes = bars.map { it.c }
            val sig = trendRule(closes)
            val e21 = ema(closes, 21)
            val r = rsi(closes)
            TimeframeStatus(
                timeframe = tf.uppercase(),
                label = "PAXG/USDT ${bars.size} candles",
                signal = sig,
                keyLevel = "EMA21 $${f2(e21)}",
                momentumPercent = r.roundToInt(),
                quickAction = when (sig) {
                    Signal.BUY -> "Uptrend: EMA9 > EMA21, close > EMA50 (RSI ${r.roundToInt()})"
                    Signal.SELL -> "Downtrend: EMA9 < EMA21, close < EMA50 (RSI ${r.roundToInt()})"
                    Signal.WAIT -> "Mixed: trend rule not met (RSI ${r.roundToInt()})"
                }
            )
        }
        if (rows.isEmpty()) return null
        val up = rows.count { it.signal == Signal.BUY }
        val down = rows.count { it.signal == Signal.SELL }
        val small = rows.filter { it.timeframe == "5M" || it.timeframe == "15M" }
        val big = rows.filter { it.timeframe == "1H" || it.timeframe == "4H" || it.timeframe == "1D" }
        fun describe(list: List<TimeframeStatus>): String =
            if (list.isEmpty()) "no data" else list.joinToString(" • ") { "${it.timeframe} ${it.signal.name}" }
        return MultiTimeframeMatrix(
            timeframes = rows,
            alignmentSummary = "$up of ${rows.size} timeframes UP, $down DOWN (real PAXG/USDT candles, EMA9/21/50 rule)",
            scalpRecommendation = "Short timeframes: ${describe(small)}",
            swingRecommendation = "Higher timeframes: ${describe(big)}"
        )
    }

    /** Signal of the next timeframe above the one being traded (used as a real conflict check). */
    fun higherTimeframeSignal(mtf: MultiTimeframeMatrix?, intervalMinutes: Int): Signal? {
        if (mtf == null) return null
        val next = mtfOrder.firstOrNull { it.second > intervalMinutes } ?: return null
        return mtf.timeframes.firstOrNull { it.timeframe == next.first.uppercase() }?.signal
    }

    fun intervalMinutes(interval: String): Int = when (interval.lowercase().trim()) {
        "1m", "1min" -> 1; "2m" -> 2; "3m" -> 3; "4m" -> 4; "5m" -> 5; "10m" -> 10
        "15m", "15min" -> 15; "30m" -> 30; "45m" -> 45; "1h" -> 60; "2h" -> 120; "3h" -> 180
        "4h" -> 240; "5h" -> 300; "6h" -> 360; "1d", "1day" -> 1440
        else -> 60
    }

    // ------------------------------------------------------------------ calibration

    /** Real hit-rate for this confidence band if there are >= 20 checked predictions in it, else null. */
    fun calibratedConfidence(snapshot: LearningSnapshot?, rawConfidence: Int): Int? {
        val b = snapshot?.calibration?.firstOrNull {
            val (lo, hi) = it.key.split("-").map { p -> p.toInt() }
            rawConfidence in lo..hi
        } ?: return null
        return if (b.decided >= 20) b.accuracyPercent.roundToInt() else null
    }

    /**
     * Up / sideways / down percentages shown on prediction screens.
     * With 20+ checked predictions: what really happened after past BUY/SELL calls.
     * Otherwise: the live pillar vote shares (real, but not a probability).
     * Returns (bullish, sideways, bearish, fromHistory).
     */
    data class OutcomeSplit(val bullish: Int, val sideways: Int, val bearish: Int, val fromHistory: Boolean)

    fun outcomeSplit(a: GoldAnalysisResult): OutcomeSplit {
        val s = a.learning
        val n = if (s == null) 0 else s.correct + s.wrong + s.sideways
        if (s != null && n >= 20 && a.overallSignal != Signal.WAIT) {
            val good = s.correct * 100 / n
            val side = s.sideways * 100 / n
            val bad = 100 - good - side
            return if (a.overallSignal == Signal.BUY) OutcomeSplit(good, side, bad, true) else OutcomeSplit(bad, side, good, true)
        }
        val total = (a.buyCount + a.sellCount + a.waitCount).coerceAtLeast(1)
        val bull = a.buyCount * 100 / total
        val bear = a.sellCount * 100 / total
        return OutcomeSplit(bull, 100 - bull - bear, bear, false)
    }

    // ------------------------------------------------------------------ main rewrite

    fun apply(
        analysis: GoldAnalysisResult,
        snapshot: LearningSnapshot,
        state: LedgerState,
        mtf: MultiTimeframeMatrix?,
        interval: String,
        appliedFilter: String
    ): GoldAnalysisResult {
        var a = analysis
        val rawConf = analysis.agreementPercent.roundToInt()
        val calibrated = calibratedConfidence(snapshot, rawConf)
        val shownConf = calibrated ?: rawConf

        // learned filter turned the signal into WAIT
        if (appliedFilter.isNotEmpty()) {
            val cand = snapshot.candidates.firstOrNull { it.id == appliedFilter }
            val owner = LearningEngine.isOwnerFilter(appliedFilter)
            val dataPause = appliedFilter == LearningCoordinator.DATA_PAUSE
            a = a.copy(
                overallSignal = Signal.WAIT,
                quantBotSignal = a.quantBotSignal?.copy(
                    signal = Signal.WAIT, isTradeActive = false, orderType = "STANDBY",
                    statusText = if (dataPause) "DATA FILTER: FORECAST PAUSED" else if (owner) "RANGE FILTER $appliedFilter: WAIT" else "🧠 LEARNED FILTER $appliedFilter: WAIT",
                    botReasoningEnglish = if (dataPause) "Forecast paused: market data integrity issue. No BUY/SELL is shown or recorded until the data is clean."
                        else if (owner) "Sideways market (trend efficiency below 20%): the trend rules are unreliable here, so this ${analysis.overallSignal.name} is shown as WAIT."
                        else "Learned filter ${cand?.titleEnglish ?: appliedFilter} blocked this ${analysis.overallSignal.name}. ${cand?.note ?: ""}",
                    botReasoningHindi = if (dataPause) "फोरकास्ट रुका है: मार्केट डेटा में गड़बड़ी है। डेटा साफ़ होने तक BUY/SELL न दिखेगा, न रिकॉर्ड होगा।"
                        else if (owner) "मार्केट साइडवेज़ है (ट्रेंड एफिशिएंसी 20% से कम): यहाँ ट्रेंड के नियम भरोसेमंद नहीं, इसलिए यह ${analysis.overallSignal.name} WAIT दिखाया गया।"
                        else "सीखे गए फ़िल्टर ${cand?.titleHindi ?: appliedFilter} ने यह ${analysis.overallSignal.name} रोका।",
                    botReasoningMarathi = if (dataPause) "फोरकास्ट थांबवला आहे: मार्केट डेटामध्ये त्रुटी आहे. डेटा स्वच्छ होईपर्यंत BUY/SELL दाखवला किंवा नोंदवला जाणार नाही."
                        else if (owner) "मार्केट साइडवेज आहे (ट्रेंड एफिशियन्सी 20% पेक्षा कमी): इथे ट्रेंडचे नियम विश्वासार्ह नाहीत, म्हणून हा ${analysis.overallSignal.name} WAIT दाखवला आहे."
                        else "शिकलेल्या फिल्टर ${cand?.titleMarathi ?: appliedFilter} ने हा ${analysis.overallSignal.name} थांबवला."
                ),
                nextPrediction = a.nextPrediction?.copy(
                    verdict = Signal.WAIT,
                    urgencyTag = if (dataPause) "FORECAST PAUSED • DATA" else if (owner) "WAIT • RANGE MARKET" else "WAIT • LEARNED FILTER $appliedFilter"
                )
            )
        }

        // 1. multi-timeframe: real candles only
        a = a.copy(mtfMatrix = mtf ?: MultiTimeframeMatrix(
            timeframes = listOf(
                TimeframeStatus(interval.uppercase(), "This chart only", analysis.overallSignal, "$${f2(analysis.currentPrice)}", 50,
                    "Other timeframes could not be loaded")
            ),
            alignmentSummary = "Multi-timeframe data unavailable right now",
            scalpRecommendation = "--",
            swingRecommendation = "--"
        ))

        // 2. confidence numbers
        a = a.copy(
            nextPrediction = a.nextPrediction?.copy(
                winProbabilityPercent = shownConf,
                appliedCorrections = realCorrections(analysis.nextPrediction?.appliedCorrections ?: emptyList(), snapshot)
            ),
            candleInsight = a.candleInsight?.copy(confidencePercent = shownConf),
            quantBotSignal = a.quantBotSignal?.copy(winProbabilityPercent = shownConf),
            tradeSetup = a.tradeSetup.copy(confidencePercent = shownConf)
        )

        // 3. rule-engine council with real accuracy
        val lastLoss = lastDecidedWasLoss(state, interval)
        a.multiAiConsensus?.let { c ->
            val insights = c.modelInsights.map { m ->
                val key = "eng:${m.provider.name}"
                val acc = LearningEngine.accuracyLabel(snapshot, key)
                val guardSignal = if (m.provider == AiModelProvider.CLAUDE) {
                    if (lastLoss && analysis.buyCount < 5 && analysis.sellCount < 5) Signal.WAIT else a.overallSignal
                } else m.signal
                m.copy(
                    signal = guardSignal,
                    correctionAppliedEnglish = "Real accuracy: $acc",
                    correctionAppliedHindi = "असली सटीकता: $acc",
                    correctionAppliedMarathi = "खरी अचूकता: $acc"
                )
            }
            a = a.copy(multiAiConsensus = c.copy(modelInsights = insights))
        }

        // 4. bots with real accuracy
        a.multiBotEnsemble?.let { ens ->
            val bots = ens.allBots.map { b ->
                val st = snapshot.sourceAccuracy("bot:${b.id}")
                val pf = profitFactorFor(state, "bot:${b.id}")
                b.copy(
                    backtestedWinRate = if (st != null && st.decided >= 10) st.accuracyPercent else -1.0,
                    profitFactor = pf,
                    confidence = if (st != null && st.decided >= 10) st.accuracyPercent.roundToInt() else 0
                )
            }
            a = a.copy(multiBotEnsemble = ens.copy(allBots = bots))
        }

        // 5. production / learning engine card
        a = a.copy(productionImprovement = productionCard(snapshot, state, interval))

        // 6. accuracy audit for this timeframe
        a = a.copy(timeframeAudit = buildAudit(snapshot, state, interval))

        // 7. failed-prediction autopsy from the latest REAL failure (none -> card hidden)
        a = a.copy(failedPredictionAutopsy = buildAutopsy(snapshot))

        // 8. trading tricks: real detection, no invented win rates
        a = a.copy(tradingTricks = realTricks(a))

        return a.copy(learning = snapshot)
    }

    private fun lastDecidedWasLoss(state: LedgerState, interval: String): Boolean {
        val last = state.records.lastOrNull { r ->
            r.interval == interval && state.resultOf(r.id)?.outcome.let {
                it == LedgerOutcome.CORRECT || it == LedgerOutcome.INCORRECT || it == LedgerOutcome.INVALIDATED
            }
        } ?: return false
        return state.resultOf(last.id)?.outcome != LedgerOutcome.CORRECT
    }

    /** Gross favourable move / gross adverse move for a source, direction-only. -1 if < 10 samples. */
    fun profitFactorFor(state: LedgerState, key: String, interval: String? = null): Double {
        var win = 0.0; var loss = 0.0; var n = 0
        for (r in state.records) {
            if (interval != null && r.interval != interval) continue
            val res = state.resultOf(r.id) ?: continue
            if (!PredictionLedger.reliable(res.outcome)) continue
            val sig = if (key == "main") r.finalSignal else r.sources[key] ?: continue
            if (sig == Signal.WAIT) continue
            val pnl = if (sig == Signal.BUY) res.move else -res.move
            if (pnl >= 0) win += pnl else loss -= pnl
            n++
        }
        if (n < 10) return -1.0
        return if (loss == 0.0) 99.0 else (win / loss)
    }

    private fun realCorrections(builtIn: List<AppliedCorrectionDetail>, s: LearningSnapshot): List<AppliedCorrectionDetail> {
        val learned = s.candidates.filter { it.stage == "PROMOTED" }.map { c ->
            AppliedCorrectionDetail(
                titleEnglish = "Learned: ${c.titleEnglish}",
                titleHindi = "सीखा गया: ${c.titleHindi}",
                titleMarathi = "शिकलेले: ${c.titleMarathi}",
                descriptionEnglish = c.ruleEnglish,
                descriptionHindi = c.ruleEnglish,
                descriptionMarathi = c.ruleEnglish,
                errorAddressedEnglish = c.note,
                errorAddressedHindi = c.note,
                errorAddressedMarathi = c.note,
                badgeTag = "LEARNED ${c.id}"
            )
        }
        val status = AppliedCorrectionDetail(
            titleEnglish = "Learning status: ${s.engineState.replace('_', ' ')}",
            titleHindi = "लर्निंग स्थिति: ${s.engineState.replace('_', ' ')}",
            titleMarathi = "लर्निंग स्थिती: ${s.engineState.replace('_', ' ')}",
            descriptionEnglish = "${s.modelVersion}. ${s.correct + s.wrong} predictions checked so far.",
            descriptionHindi = "${s.modelVersion}। अब तक ${s.correct + s.wrong} प्रेडिक्शन जाँची गईं।",
            descriptionMarathi = "${s.modelVersion}. आतापर्यंत ${s.correct + s.wrong} अंदाज तपासले.",
            errorAddressedEnglish = "A rule changes the model only after ${s.minimumSamples}+ samples and a shadow test.",
            errorAddressedHindi = "कोई नियम ${s.minimumSamples}+ सैंपल और शैडो टेस्ट के बाद ही मॉडल बदलता है।",
            errorAddressedMarathi = "कोणताही नियम ${s.minimumSamples}+ नमुने आणि शॅडो टेस्टनंतरच मॉडेल बदलतो.",
            badgeTag = "REAL LEDGER"
        )
        return builtIn + learned + status
    }

    private fun productionCard(s: LearningSnapshot, state: LedgerState, interval: String): ProductionImprovementEngine {
        val all = s.windows.firstOrNull { it.label == "All Time" }
        val decided = all?.decided ?: 0
        val acc = if (decided >= 10) all!!.accuracyPercent else -1.0
        var sumPips = 0.0; var n = 0
        for (r in state.records) {
            if (r.interval != interval || r.finalSignal == Signal.WAIT) continue
            val res = state.resultOf(r.id) ?: continue
            if (res.outcome != LedgerOutcome.CORRECT && res.outcome != LedgerOutcome.INCORRECT && res.outcome != LedgerOutcome.INVALIDATED) continue
            sumPips += (if (r.finalSignal == Signal.BUY) res.move else -res.move) * 10.0
            n++
        }
        val fixes = s.candidates.filter { it.stage == "PROMOTED" }.map { c ->
            AppliedProductionFix(
                ruleTitle = "${c.id}: ${c.titleEnglish}",
                ruleTitleHindi = "${c.id}: ${c.titleHindi}",
                errorPrevented = c.ruleEnglish,
                errorPreventedHindi = c.ruleEnglish,
                improvementImpact = "Past data: ${f1(c.baselineAccuracy)}% -> ${f1(c.keptAccuracy)}% (blocked ${c.affectedSamples}). Shadow: ${c.shadowSamples} samples.",
                improvementImpactHindi = "पुराना डेटा: ${f1(c.baselineAccuracy)}% -> ${f1(c.keptAccuracy)}% (${c.affectedSamples} रोके)। शैडो: ${c.shadowSamples} सैंपल।"
            )
        }
        val weights = listOf(
            "trend" to 3.0, "smc" to 3.0, "macro" to 2.5, "sr" to 2.0, "candlestick" to 2.0, "momentum" to 1.5, "volatility" to 1.5
        ).map { (k, w) ->
            val acc2 = LearningEngine.accuracyLabel(s, "grp:$k")
            ModelWeightItem(
                pillarName = k.replaceFirstChar { it.uppercase() },
                pillarNameHindi = k.replaceFirstChar { it.uppercase() },
                weightMultiplier = w,
                adjustmentReason = "Fixed weight. Real accuracy: $acc2",
                adjustmentReasonHindi = "तय वज़न। असली सटीकता: $acc2"
            )
        }
        return ProductionImprovementEngine(
            engineVersion = s.modelVersion,
            statusBadge = s.engineState.replace('_', ' '),
            verifiedAccuracyPercent = acc,
            totalBacktestedTrades = decided,
            profitFactor = profitFactorFor(state, "main", interval),
            averagePipGainPerTrade = if (n == 0) 0.0 else sumPips / n,
            dynamicConfidenceThreshold = 68,
            appliedProductionFixes = fixes,
            liveModelWeights = weights,
            isAutoTuningActive = true
        )
    }

    private fun buildAudit(s: LearningSnapshot, state: LedgerState, interval: String): TimeframeAccuracyAudit {
        val all = s.windows.firstOrNull { it.label == "All Time" }
        val decided = all?.decided ?: 0
        val wins = all?.correct ?: 0
        val active = state.records.count { it.interval == interval && state.resultOf(it.id) == null }
        val items = state.records.filter { it.interval == interval && it.finalSignal != Signal.WAIT }
            .takeLast(10).asReversed().map { r -> auditItem(r, state.resultOf(r.id)) }
        var pips = 0.0
        for (r in state.records) {
            if (r.interval != interval || r.finalSignal == Signal.WAIT) continue
            val res = state.resultOf(r.id) ?: continue
            if (res.outcome == LedgerOutcome.CORRECT || res.outcome == LedgerOutcome.INCORRECT || res.outcome == LedgerOutcome.INVALIDATED) {
                pips += (if (r.finalSignal == Signal.BUY) res.move else -res.move) * 10.0
            }
        }
        val promoted = s.candidates.filter { it.stage == "PROMOTED" }
        val rulesEn = if (promoted.isEmpty())
            listOf("No learned rule is active yet. A rule needs ${s.minimumSamples}+ checked samples and a passed shadow test.")
        else promoted.map { "${it.id}: ${it.ruleEnglish}" }
        val rulesHi = if (promoted.isEmpty())
            listOf("अभी कोई सीखा हुआ नियम चालू नहीं है। नियम के लिए ${s.minimumSamples}+ जाँचे गए सैंपल और पास शैडो टेस्ट चाहिए।")
        else promoted.map { "${it.id}: ${it.titleHindi}" }
        val rulesMr = if (promoted.isEmpty())
            listOf("अजून कोणताही शिकलेला नियम चालू नाही. नियमासाठी ${s.minimumSamples}+ तपासलेले नमुने आणि पास शॅडो टेस्ट हवी.")
        else promoted.map { "${it.id}: ${it.titleMarathi}" }
        val diagnosis = s.failureClusters.take(5).map { cl ->
            val related = s.candidates.firstOrNull { c -> clusterMatches(cl.key, c.id) }
            ErrorCorrectionFeedback(
                errorType = cl.label,
                errorTypeHindi = cl.label,
                pastMistakeDescriptionEnglish = "Seen in ${cl.decided} failed predictions (all timeframes).",
                pastMistakeDescriptionHindi = "${cl.decided} गलत प्रेडिक्शन में दिखा (सभी टाइमफ्रेम)।",
                pastMistakeDescriptionMarathi = "${cl.decided} चुकीच्या अंदाजांत दिसले (सर्व टाइमफ्रेम).",
                correctionAppliedEnglish = related?.let { "${it.id}: ${it.stage} (${it.affectedSamples}/${it.requiredSamples} samples)" } ?: "No rule linked yet",
                correctionAppliedHindi = related?.let { "${it.id}: ${it.stage} (${it.affectedSamples}/${it.requiredSamples} सैंपल)" } ?: "अभी कोई नियम नहीं जुड़ा",
                correctionAppliedMarathi = related?.let { "${it.id}: ${it.stage} (${it.affectedSamples}/${it.requiredSamples} नमुने)" } ?: "अजून नियम नाही",
                status = related?.stage ?: "OBSERVING"
            )
        }
        return TimeframeAccuracyAudit(
            timeframe = interval.uppercase(),
            totalSignalsTested = decided,
            winCount = wins,
            lossCount = decided - wins,
            activeCount = active,
            winRatePercent = if (decided == 0) 0 else (wins * 100.0 / decided).roundToInt(),
            netPipsGained = (pips * 10).roundToInt() / 10.0,
            lastPredictionOutcome = items.firstOrNull { it.outcomeStatus != PredictionOutcomeStatus.PENDING_ENTRY },
            recentSignalAudits = items,
            autoCorrectionRules = rulesEn,
            autoCorrectionRulesHindi = rulesHi,
            autoCorrectionRulesMarathi = rulesMr,
            aiEngineLearningStatus = "${s.engineState.replace('_', ' ')} • ${s.modelVersion}",
            autoCorrectionsLearnedCount = promoted.size,
            errorDiagnosisList = diagnosis
        )
    }

    private fun clusterMatches(tag: String, cid: String): Boolean = when (tag) {
        "RANGE_WHIPSAW" -> cid == "C-RANGE"
        "NEWS_SHOCK" -> cid == "C-NEWS"
        "TIMEFRAME_CONFLICT" -> cid == "C-HTF"
        "INSUFFICIENT_EDGE" -> cid == "C-AGR70"
        "VOLATILITY_EXPANSION" -> cid == "C-VOLX"
        "MISSED_CONTRADICTION" -> cid.startsWith("C-GRP-")
        else -> false
    }

    private fun auditItem(r: LedgerRecord, res: LedgerResult?): PastPredictionAuditItem {
        val dir = if (r.finalSignal == Signal.SELL) -1.0 else 1.0
        val thr = max(0.25 * r.atr, 0.30)
        val status = when (res?.outcome) {
            null, LedgerOutcome.PENDING -> PredictionOutcomeStatus.PENDING_ENTRY
            LedgerOutcome.CORRECT -> if (res.mfe >= 2 * thr) PredictionOutcomeStatus.TP2_HIT else PredictionOutcomeStatus.TP1_HIT
            LedgerOutcome.INCORRECT, LedgerOutcome.INVALIDATED -> PredictionOutcomeStatus.STOP_LOSS_HIT
            else -> PredictionOutcomeStatus.IN_PROFIT_ACTIVE // sideways / no data: shown as "no result"
        }
        val pips = if (res == null) 0.0 else ((dir * res.move) * 100).roundToInt() / 10.0
        val whyEn = when (res?.outcome) {
            null, LedgerOutcome.PENDING -> "Waiting for expiry at ${PredictionLedger.utcLabel(r.expiresAt, true)}."
            LedgerOutcome.CORRECT -> "Price moved ${f2(abs(res.move))} the predicted way (needed ${f2(res.threshold)})."
            LedgerOutcome.INCORRECT -> "Price moved ${f2(abs(res.move))} the other way."
            LedgerOutcome.INVALIDATED -> "Stop-loss distance ${f2(r.slDistance)} was hit before expiry."
            LedgerOutcome.SIDEWAYS -> "Price moved only ${f2(res.move)} (less than ${f2(res.threshold)}): no result."
            else -> "Not counted: ${res.outcome.name.replace('_', ' ')}."
        }
        val lessonEn = if (res != null && res.tags.isNotEmpty()) "Evidence: ${res.tags.joinToString(", ")}. ${res.counterfactual}" else "Regime ${r.regime}, session ${r.session}, confidence ${r.confidence}%."
        return PastPredictionAuditItem(
            id = r.id,
            timestamp = PredictionLedger.utcLabel(r.createdAt, true),
            timeAgo = PredictionLedger.utcLabel(r.createdAt, true),
            signal = r.finalSignal,
            entryPrice = r.price,
            target1Price = r.price + dir * thr,
            target2Price = r.price + dir * 2 * thr,
            stopLossPrice = r.price - dir * r.slDistance,
            actualHighLowReached = if (res == null) r.price else r.price + dir * res.mfe,
            pipsResult = pips,
            outcomeStatus = status,
            whyItHappenedHindi = whyEn,
            whyItHappenedEnglish = whyEn,
            whyItHappenedMarathi = whyEn,
            lessonLearnedHindi = lessonEn,
            lessonLearnedEnglish = lessonEn,
            lessonLearnedMarathi = lessonEn,
            indicatorsInvolved = r.sources.filterKeys { it.startsWith("grp:") }.map { "${it.key.removePrefix("grp:")}=${it.value.name}" }
        )
    }

    private fun buildAutopsy(s: LearningSnapshot): FailedPredictionCandleAutopsy? {
        val f = s.failures.firstOrNull() ?: return null
        val dir = if (f.signal == Signal.SELL) -1.0 else 1.0
        val start = f.entryPrice
        val up = if (dir > 0) f.maxFavorable else f.maxAdverse
        val down = if (dir > 0) f.maxAdverse else f.maxFavorable
        val open = start
        val close = start + f.movePoints
        val high = start + up
        val low = start - down
        val topTag = f.attribution.entries.maxByOrNull { it.value }?.key ?: f.tags.firstOrNull() ?: "UNKNOWN"
        val linked = s.candidates.firstOrNull { clusterMatches(topTag, it.id) }
        val actionEn = linked?.let { "No change from one loss. Related rule ${it.id} is ${it.stage}: ${it.affectedSamples}/${it.requiredSamples} samples." }
            ?: "No change from one loss. It is stored as evidence for the learning engine."
        val actionHi = linked?.let { "एक गलती से बदलाव नहीं। जुड़ा नियम ${it.id}: ${it.stage}, ${it.affectedSamples}/${it.requiredSamples} सैंपल।" }
            ?: "एक गलती से बदलाव नहीं। इसे लर्निंग इंजन के सबूत के तौर पर सेव किया गया।"
        val actionMr = linked?.let { "एका चुकीने बदल नाही. संबंधित नियम ${it.id}: ${it.stage}, ${it.affectedSamples}/${it.requiredSamples} नमुने." }
            ?: "एका चुकीने बदल नाही. लर्निंग इंजिनसाठी पुरावा म्हणून जतन केले."
        val evidence = f.attribution.entries.joinToString(", ") { "${it.key.replace('_', ' ')} ${it.value}%" }
        val tl = f.timeline.joinToString(" → ") { "${it.timeLabel}: ${it.text}" }
        return FailedPredictionCandleAutopsy(
            previousTradeId = f.id,
            failedSignal = f.signal,
            entryPrice = start,
            stopLossPrice = start - dir * f.maxAdverse,
            targetPrice = start + dir * f.thresholdPoints,
            pipsLoss = abs(f.movePoints) * 10.0,
            timeAgo = f.createdLabel,
            trapCandleType = topTag.replace('_', ' '),
            candleOpen = open,
            candleHigh = max(high, max(open, close)),
            candleLow = min(low, min(open, close)),
            candleClose = close,
            upperWickPips = (max(high, max(open, close)) - max(open, close)) * 10.0,
            lowerWickPips = (min(open, close) - min(low, min(open, close))) * 10.0,
            bodyPips = abs(close - open) * 10.0,
            volumeSurgeMultiplier = 0.0,
            trapDiagnosisEnglish = "${f.interval} ${f.signal.name} (${f.confidence}%) → ${f.outcome}. Evidence weights: $evidence. ${f.counterfactual}",
            trapDiagnosisHindi = "${f.interval} ${f.signal.name} (${f.confidence}%) → ${f.outcome}। सबूत: $evidence। ${f.counterfactual}",
            trapDiagnosisMarathi = "${f.interval} ${f.signal.name} (${f.confidence}%) → ${f.outcome}. पुरावा: $evidence. ${f.counterfactual}",
            geminiCandleReading = "Signals that warned: " + (if (f.warningsIgnored.isEmpty()) "none" else f.warningsIgnored.joinToString(", ")),
            chatGptCandleReading = "Market: regime ${f.regime.replace('_', ' ')}, session ${f.session.replace('_', ' ')}${if (f.newsActive) ", news window" else ""}",
            claudeCandleReading = "Path: best +${f2(f.maxFavorable)}, worst -${f2(f.maxAdverse)}, final ${String.format(Locale.US, "%+.2f", f.movePoints)} (${f.verifySource})",
            deepSeekCandleReading = "Timeline: $tl",
            recalibrationActionTakenEnglish = actionEn,
            recalibrationActionTakenHindi = actionHi,
            recalibrationActionTakenMarathi = actionMr
        )
    }

    private fun realTricks(a: GoldAnalysisResult): List<TradingTrick> {
        val c = a.recentCandles
        val n = c.size
        // open bullish / bearish fair value gap in the last 15 candles
        var fvg = "NO OPEN FVG"
        if (n >= 3) {
            loop@ for (i in n - 1 downTo max(2, n - 15)) {
                if (c[i].low > c[i - 2].high) {
                    val filled = (i + 1 until n).any { c[it].low <= c[i - 2].high }
                    if (!filled) { fvg = "BULLISH FVG OPEN ${f2(c[i - 2].high)}-${f2(c[i].low)} 🧲"; break@loop }
                }
                if (c[i].high < c[i - 2].low) {
                    val filled = (i + 1 until n).any { c[it].high >= c[i - 2].low }
                    if (!filled) { fvg = "BEARISH FVG OPEN ${f2(c[i].high)}-${f2(c[i - 2].low)} 🧲"; break@loop }
                }
            }
        }
        // hidden bullish divergence: higher price low with lower RSI low (last 30 candles)
        var divergence = "NOT DETECTED"
        if (n >= 35) {
            val closes = c.map { it.close }
            val rsis = (0 until n).map { idx -> if (idx < 15) 50.0 else rsi(closes.subList(0, idx + 1)) }
            val lows = (n - 30 until n - 2).filter { i -> c[i].low < c[i - 1].low && c[i].low < c[i - 2].low && c[i].low < c[i + 1].low && c[i].low < c[i + 2].low }
            if (lows.size >= 2) {
                val p1 = lows[lows.size - 2]; val p2 = lows.last()
                if (c[p2].low > c[p1].low && rsis[p2] < rsis[p1]) divergence = "HIDDEN BULLISH DIVERGENCE FOUND 📈"
            }
        }
        val overlap = a.marketSessions.any { it.isGoldenOverlap }
        return a.tradingTricks.map { t ->
            val status = when (t.id) {
                "trick_fvg" -> fvg
                "trick_rsi_div" -> divergence
                "trick_killzone" -> if (overlap) "LONDON/NY OVERLAP NOW ⚡" else "NOT IN OVERLAP 🕒"
                "trick_wick_trap" -> a.smartMoney?.liquiditySweepAlert?.let { if (it.contains("Liquidity Grab")) "SWEEP DETECTED 🟢" else "NO SWEEP" } ?: t.status
                else -> t.status
            }
            t.copy(winRate = "Win rate not measured", status = status, expectedPipGain = "Not measured")
        }
    }
}
