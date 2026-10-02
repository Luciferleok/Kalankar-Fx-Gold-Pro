package com.example.livegoldai.data.brain

import com.example.livegoldai.data.LedgerOutcome
import com.example.livegoldai.data.LedgerState
import com.example.livegoldai.data.PredictionLedger
import com.example.livegoldai.data.RealityEngine
import com.example.livegoldai.model.AiCouncilReport
import com.example.livegoldai.model.BrainReport
import com.example.livegoldai.model.BudgetLine
import com.example.livegoldai.model.CrossMarketReport
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.LabelStat
import com.example.livegoldai.model.Signal
import com.example.livegoldai.model.UncertaintyItem
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/** Verified statistics from the prediction ledger. A number appears only with at least [MIN_N] decided calls. */
object ResearchStats {

    const val MIN_N = 20

    fun label(hits: Int, n: Int): String = when {
        n == 0 -> "untested"
        n < MIN_N -> "untested (N=$n)"
        else -> "${(100.0 * hits / n).roundToInt()}% (N=$n)"
    }

    /** +1 correct, 0 wrong, null not a decided directional call. */
    private fun score(o: LedgerOutcome): Int? = when (o) {
        LedgerOutcome.CORRECT -> 1
        LedgerOutcome.INCORRECT, LedgerOutcome.INVALIDATED -> 0
        else -> null
    }

    /** How often calls shown with at least X% confidence were really right (reveals over-confidence). */
    fun strongSignal(state: LedgerState): List<LabelStat> = listOf(70, 80, 90).map { thr ->
        var h = 0; var n = 0
        for (r in state.records) {
            if (r.finalSignal == Signal.WAIT || r.confidence < thr) continue
            val s = score(state.resultOf(r.id)?.outcome ?: continue) ?: continue
            n++; h += s
        }
        LabelStat("Shown $thr%+", label(h, n))
    }

    /** Share of checked predictions where the model took a side (a model cannot look good by always saying WAIT). */
    fun coverage(state: LedgerState): String {
        var edge = 0; var total = 0
        for (r in state.records) {
            val o = state.resultOf(r.id)?.outcome ?: continue
            if (o == LedgerOutcome.DATA_FAILURE || o == LedgerOutcome.MARKET_CLOSED || o == LedgerOutcome.PENDING) continue
            total++
            if (r.finalSignal != Signal.WAIT) edge++
        }
        return if (total < MIN_N) "untested (N=$total)" else "${(100.0 * edge / total).roundToInt()}% took a side, ${100 - (100.0 * edge / total).roundToInt()}% no edge (N=$total)"
    }

    private fun utc(ms: Long) = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }

    fun dayOfWeek(state: LedgerState): List<LabelStat> {
        val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
        val h = IntArray(5); val n = IntArray(5)
        for (r in state.records) {
            if (r.finalSignal == Signal.WAIT) continue
            val s = score(state.resultOf(r.id)?.outcome ?: continue) ?: continue
            val d = (utc(r.createdAt).get(Calendar.DAY_OF_WEEK) + 5) % 7   // 0 = Monday
            if (d in 0..4) { n[d]++; h[d] += s }
        }
        return names.indices.map { LabelStat(names[it], label(h[it], n[it])) }
    }

    val SESSIONS = listOf(
        "Asia" to (23 to 6), "Pre-London" to (6 to 7), "London open" to (7 to 9), "London" to (9 to 12),
        "NY open" to (12 to 14), "London/NY overlap" to (14 to 16), "New York" to (16 to 21), "Post-NY" to (21 to 23)
    )

    fun sessionOf(ms: Long): String {
        val hr = utc(ms).get(Calendar.HOUR_OF_DAY)
        return SESSIONS.first { (_, w) -> if (w.first > w.second) hr >= w.first || hr < w.second else hr >= w.first && hr < w.second }.first
    }

    fun sessions(state: LedgerState): List<LabelStat> {
        val h = HashMap<String, Int>(); val n = HashMap<String, Int>()
        for (r in state.records) {
            if (r.finalSignal == Signal.WAIT) continue
            val s = score(state.resultOf(r.id)?.outcome ?: continue) ?: continue
            val k = sessionOf(r.createdAt)
            n[k] = (n[k] ?: 0) + 1; h[k] = (h[k] ?: 0) + s
        }
        return SESSIONS.map { (name, _) -> LabelStat(name, label(h[name] ?: 0, n[name] ?: 0)) }
    }

    /** How often the regime was different at the next prediction, given the current regime (same timeframe). */
    fun transitionRisk(state: LedgerState, interval: String, regime: String): String {
        val recs = state.records.filter { it.interval == interval }.sortedBy { it.createdAt }
        var changed = 0; var n = 0
        for (i in 0 until recs.size - 1) {
            if (recs[i].regime != regime) continue
            n++
            if (recs[i + 1].regime != regime) changed++
        }
        return label(changed, n)
    }
}

/**
 * MARKET BRAIN
 * Collects every measured input into one report. It does not create a direction.
 * The quality index, edge meter and confidence budget are transparent rule formulas over real inputs;
 * their constants are fixed rules, shown as such, and are not presented as accuracy.
 */
object MarketBrain {

    private const val RANK_LOW = 1
    private fun rank(level: String) = when (level) { "LOW" -> 1; "MODERATE" -> 2; "HIGH" -> 3; "EXTREME" -> 4; else -> 0 }

    fun build(
        a: GoldAnalysisResult,
        xm: CrossMarketReport,
        features: Map<String, Double>,
        fs: FeatureStore,
        state: LedgerState,
        council: AiCouncilReport?,
        now: Long = System.currentTimeMillis()
    ): BrainReport {
        val sig = a.overallSignal
        val anomaly = AnomalyEngine.build(a, xm, now)
        val regime = a.insights?.regime ?: PredictionLedger.regimeOf(a.recentCandles, a.newsMode != null)
        val analogs = AnalogEngine.analogs(features, fs, state, a.interval, now)
        val fam = AnalogEngine.familiarity(features, fs, state, a.interval, now)

        // ---- summaries
        val mtf = a.mtfMatrix?.timeframes?.filter { it.label.startsWith("PAXG") } ?: emptyList()
        val up = mtf.count { it.signal == Signal.BUY }; val dn = mtf.count { it.signal == Signal.SELL }
        val mtfSummary = when {
            mtf.isEmpty() -> "NOT LOADED"
            up == mtf.size -> "ALL UP ($up/${mtf.size})"
            dn == mtf.size -> "ALL DOWN ($dn/${mtf.size})"
            up > dn -> "MIXED, MORE UP ($up up, $dn down)"
            dn > up -> "MIXED, MORE DOWN ($dn down, $up up)"
            else -> "MIXED ($up up, $dn down)"
        }
        val aiSummary = when {
            council == null || council.configured == 0 -> "NOT CONNECTED"
            council.eligible == 0 -> "NO ELIGIBLE VOTE (${council.configured} connected)"
            else -> "${council.consensus} • ${council.agreementLevel} (${council.eligible} voting)"
        }
        val bots = a.multiBotEnsemble
        val botSummary = bots?.let { "${it.ensembleSignal.name} (${it.buyVotes} buy, ${it.sellVotes} sell, ${it.waitVotes} wait)" } ?: "NOT AVAILABLE"

        // ---- confidence budget (fixed rules, each line is a real condition that is true now)
        val raw = a.agreementPercent.roundToInt()
        val cal = RealityEngine.calibratedConfidence(a.learning, raw)
        val base = cal ?: raw
        val budget = ArrayList<BudgetLine>()
        val htf = RealityEngine.higherTimeframeSignal(a.mtfMatrix, RealityEngine.intervalMinutes(a.interval))
        if (sig != Signal.WAIT && htf != null && PredictionLedger.opposite(htf, sig)) budget.add(BudgetLine("Higher timeframe points the other way", -5))
        when (anomaly.level) {
            "MODERATE" -> budget.add(BudgetLine("Anomaly risk moderate", -3))
            "HIGH" -> budget.add(BudgetLine("Anomaly risk high", -6))
            "EXTREME" -> budget.add(BudgetLine("Anomaly risk extreme", -12))
        }
        if (council != null && council.eligible > 0) {
            if (council.conflictLevel == "HIGH") budget.add(BudgetLine("AI council disagrees with quant/bots", -4))
            else if (council.dispersionLevel == "HIGH") budget.add(BudgetLine("AI answers far apart", -3))
        }
        if (xm.available && sig != Signal.WAIT) {
            val against = (sig == Signal.BUY && xm.summary == "SUPPORTS BEARISH") || (sig == Signal.SELL && xm.summary == "SUPPORTS BULLISH")
            if (against) budget.add(BudgetLine("Cross-market points the other way", -3))
        }
        if (xm.divergences.isNotEmpty()) budget.add(BudgetLine("Cross-asset relation break (${xm.divergences.size})", -3))
        when (fam.status) {
            "UNUSUAL" -> budget.add(BudgetLine("Unusual market vs history", -4))
            "OUT_OF_DISTRIBUTION" -> budget.add(BudgetLine("Market outside historical range", -8))
        }
        val dataCat = a.health?.category("DATA")
        if (a.feed?.isLive != true) budget.add(BudgetLine("No live price feed", -15))
        else if (dataCat != null && dataCat.status == "DEGRADED") budget.add(BudgetLine("Data quality degraded", -5))
        val news = a.newsMode != null || a.newsTradingPlan?.isNewsActive == true || a.isNewsModeTriggered
        if (news) budget.add(BudgetLine("High-impact news window", -5))
        val deductions = -budget.sumOf { it.delta }
        val adjusted = (base - deductions).coerceIn(0, 100)

        // ---- uncertainty decomposition
        val unc = ArrayList<UncertaintyItem>()
        val dataLevel = when {
            a.feed?.isLive != true -> "HIGH"
            dataCat == null -> "MODERATE"
            dataCat.score >= 90 -> "LOW"
            dataCat.score >= 60 -> "MODERATE"
            else -> "HIGH"
        }
        unc.add(UncertaintyItem("Data", dataLevel, dataCat?.let { "${it.summary} • ${a.feed?.source ?: "--"}" } ?: "health not checked yet"))
        val calErr = a.learning?.calibrationErrorPoints ?: -1.0
        val modelLevel = if (calErr < 0) "UNKNOWN" else if (calErr <= 8) "LOW" else if (calErr <= 15) "MODERATE" else "HIGH"
        unc.add(UncertaintyItem("Model", modelLevel,
            if (calErr < 0) "not enough checked predictions to measure calibration" else String.format(Locale.US, "confidence is off by %.1f points on average", calErr)))
        val marketLevel = when {
            anomaly.severity >= 2 -> "HIGH"
            anomaly.severity == 1 || regime == "TRANSITION" || regime == "NEWS_EVENT" || regime == "VOLATILITY_EXPANSION" -> "MODERATE"
            else -> "LOW"
        }
        unc.add(UncertaintyItem("Market", marketLevel, "regime ${regime.replace('_', ' ')} • anomaly ${anomaly.level}"))
        val aiLevel = when {
            council == null || council.eligible == 0 -> "N/A"
            council.conflictLevel == "HIGH" || council.dispersionLevel == "HIGH" -> "HIGH"
            council.consensus == "SPLIT" || council.conflictLevel == "MINOR" || council.dispersionLevel == "MODERATE" -> "MODERATE"
            else -> "LOW"
        }
        unc.add(UncertaintyItem("AI disagreement", aiLevel,
            if (council == null || council.eligible == 0) "no eligible AI vote" else "${council.consensus}, dispersion ${council.dispersionLevel}, ${council.conflictLevel}"))
        val regLevel = when (fam.status) { "NORMAL" -> "LOW"; "UNUSUAL" -> "MODERATE"; "OUT_OF_DISTRIBUTION" -> "HIGH"; else -> "UNKNOWN" }
        unc.add(UncertaintyItem("Regime familiarity", regLevel,
            if (fam.pct < 0) "history too short (${fam.historySize}/${AnalogEngine.MIN_HISTORY})" else "${fam.pct}% of features inside the usual range"))
        val worst = unc.maxOf { rank(it.level) }
        val totalUnc = when { worst >= 3 -> "HIGH"; worst == 2 -> "MODERATE"; worst == RANK_LOW -> "LOW"; else -> "UNKNOWN" }

        // ---- prediction quality index (NOT accuracy)
        val ins = a.insights
        val sep = if (ins == null) 0.0 else abs(ins.bullishPoints - ins.bearishPoints)
        val pData = when { a.feed?.isLive != true -> 0.0; dataCat != null -> dataCat.score / 100.0; else -> 0.5 }
        val pEdge = if (sig == Signal.WAIT) 0.0 else (sep / 60.0).coerceIn(0.0, 1.0)
        val pCal = if (calErr < 0) 0.5 else (1.0 - calErr / 25.0).coerceIn(0.0, 1.0)
        val pUnc = (1.0 - deductions / 30.0).coerceIn(0.0, 1.0)
        val pFam = if (fam.pct < 0) 0.5 else fam.pct / 100.0
        val quality = (100.0 * (0.25 * pData + 0.25 * pEdge + 0.15 * pCal + 0.20 * pUnc + 0.15 * pFam)).roundToInt()
        val qClass = when {
            quality >= 90 -> "EXCELLENT CONDITIONS"
            quality >= 75 -> "STRONG CONDITIONS"
            quality >= 60 -> "USABLE"
            quality >= 40 -> "LOW QUALITY"
            else -> "NO EDGE / PAUSE"
        }
        fun pc(v: Double) = "${(v * 100).roundToInt()}/100"
        val parts = listOf(
            LabelStat("Data quality (25%)", pc(pData)),
            LabelStat("Edge: pillar separation (25%)", pc(pEdge)),
            LabelStat("Calibration (15%)", if (calErr < 0) "50/100 (unmeasured: neutral)" else pc(pCal)),
            LabelStat("Uncertainty budget (20%)", pc(pUnc)),
            LabelStat("Market familiarity (15%)", if (fam.pct < 0) "50/100 (unmeasured: neutral)" else pc(pFam))
        )

        // ---- edge meter
        val sepAdj = sep * quality / 100.0
        val edge = when {
            sig == Signal.WAIT || quality < 40 -> "NO EDGE"
            sepAdj < 20 -> "WEAK"
            sepAdj < 40 -> "MODERATE"
            else -> "STRONG"
        }
        val edgeNote = if (sig == Signal.WAIT) "Quant engine sees no side" else String.format(Locale.US, "pillar separation %.0f pts × quality %d%%", sep, quality)

        // ---- lineage
        val indicators = a.groups.sumOf { it.indicators.size }
        val usable = a.health?.indicators?.count { it.status == "OK" }
        val xmOk = xm.assets.count { it.health != "UNAVAILABLE" }
        val lineage = listOf(
            LabelStat("Final signal", "${sig.name} ← quant engine (7 pillars, 68% gate" + (if (a.quantBotSignal?.statusText?.contains("LEARNED FILTER") == true) ", learned filter applied)" else ")")),
            LabelStat("Candles", "${a.recentCandles.size} × ${a.interval} • ${a.feed?.source ?: "--"} • last ${a.lastUpdated}"),
            LabelStat("Indicators", if (usable != null) "$usable/$indicators usable" else "$indicators"),
            LabelStat("Timeframes", if (mtf.isEmpty()) "not loaded" else "${mtf.size} real (PAXG/USDT)"),
            LabelStat("Rule bots", bots?.let { "${it.totalBots} evaluated" } ?: "none"),
            LabelStat("External AI", if (council == null) "none connected" else "${council.eligible} voting / ${council.configured} connected (advisory)"),
            LabelStat("Cross-market", if (xm.available) "$xmOk/${xm.assets.size} assets • Yahoo Finance" else "CROSS-MARKET DATA UNAVAILABLE"),
            LabelStat("News", "${a.feed?.calendarEvents ?: 0} calendar events"),
            LabelStat("Ledger", "${state.records.size} predictions, ${state.results.size} checked"),
            LabelStat("Feature store", "${fs.size()} snapshots • ${FeatureCatalog.VERSION}")
        )

        return BrainReport(
            builtAtMs = now,
            regime = regime,
            transitionRisk = ResearchStats.transitionRisk(state, a.interval, regime),
            anomaly = anomaly,
            mtfSummary = mtfSummary,
            crossMarket = xm,
            aiSummary = aiSummary,
            botSummary = botSummary,
            analogs = analogs,
            familiarity = fam,
            quantSignal = sig,
            baseConfidence = base,
            baseLabel = if (cal != null) "calibrated from real results" else "pillar agreement (uncalibrated)",
            budget = budget,
            adjustedConfidence = adjusted,
            uncertainty = unc,
            totalUncertainty = totalUnc,
            qualityIndex = quality,
            qualityClass = qClass,
            qualityParts = parts,
            edge = edge,
            edgeNote = edgeNote,
            strongSignal = ResearchStats.strongSignal(state),
            coverage = ResearchStats.coverage(state),
            dayOfWeek = ResearchStats.dayOfWeek(state),
            sessions = ResearchStats.sessions(state),
            featureVersion = FeatureCatalog.VERSION,
            featuresNow = features.size,
            featuresStored = fs.size(),
            featureDefs = FeatureCatalog.ui(),
            lineage = lineage
        )
    }
}
