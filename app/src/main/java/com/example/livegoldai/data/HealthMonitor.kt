package com.example.livegoldai.data

import com.example.livegoldai.model.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * SYSTEM HEALTH
 *
 * Every score is the share of real checks that passed on this refresh
 * (OK = 1, WARN = 0.5, FAIL = 0). Nothing is estimated. Components that do not
 * exist (AI providers) are shown as NOT CONFIGURED and left out of the total.
 * The event log covers the current app session.
 */
class HealthMonitor {

    private val events = ArrayDeque<SystemEvent>()
    private var prevSource: String? = null
    private var prevStatus: Map<String, String> = emptyMap()
    private var prevVerified = 0
    private var prevCycleAt = 0L
    private var prevFilters = -1

    @Synchronized
    fun build(a: GoldAnalysisResult, stats: LearningCoordinator.Stats?, mtfCount: Int, now: Long = System.currentTimeMillis()): SystemHealth {
        val feed = a.feed
        val categories = ArrayList<HealthCategory>()

        // ---------------- DATA
        val dataChecks = ArrayList<HealthCheck>()
        if (feed == null) {
            dataChecks.add(HealthCheck("Price source", "FAIL", "No feed information"))
        } else {
            dataChecks.add(HealthCheck("Price source", if (feed.isLive) "OK" else "FAIL",
                if (feed.isLive) feed.source else "All live sources failed: showing OFFLINE DEMO data"))
            dataChecks.add(HealthCheck("Primary source", if (feed.sourcesTried == 1) "OK" else "WARN",
                if (feed.sourcesTried == 1) "Twelve Data answered" else "Backup used (${feed.sourcesTried - 1} source(s) failed first)"))
            dataChecks.add(HealthCheck("Fetch time", if (feed.latencyMs < 5000) "OK" else "WARN", "${feed.latencyMs} ms"))
        }
        val nCandles = a.recentCandles.size
        dataChecks.add(HealthCheck("Candles", if (nCandles >= 50) "OK" else if (nCandles >= 20) "WARN" else "FAIL", "$nCandles candles on ${a.interval}"))
        categories.add(category("DATA", "Market data", dataChecks))

        // ---------------- TIMEFRAMES
        val tfChecks = listOf("5M", "15M", "1H", "4H", "1D").map { tf ->
            val ok = a.mtfMatrix?.timeframes?.any { it.timeframe == tf && it.label.startsWith("PAXG") } == true
            HealthCheck(tf, if (ok) "OK" else "WARN", if (ok) "Real candles loaded" else "Not loaded")
        }
        categories.add(category("TIMEFRAMES", "Timeframes", tfChecks))

        // ---------------- INDICATORS
        val indicatorChecks = a.groups.flatMap { g -> g.indicators.map { selfTest(g.title, it) } }
        categories.add(category("INDICATORS", "Indicators", indicatorChecks))

        // ---------------- BOTS
        val evalLabel = feed?.let { PredictionLedger.utcLabel(it.fetchedAtMs) } ?: "--"
        val bots = a.multiBotEnsemble?.allBots?.map { b ->
            BotActivity(
                id = b.id, name = b.name, signal = b.signal, lastEvaluationLabel = evalLabel,
                healthy = feed?.isLive == true && a.recentCandles.size >= 50,
                verifiedLabel = LearningEngine.accuracyLabel(a.learning, "bot:${b.id}")
            )
        } ?: emptyList()
        val botChecks = bots.map { HealthCheck(it.name, if (it.healthy) "OK" else "FAIL", if (it.healthy) "Evaluated at ${it.lastEvaluationLabel}" else "No live data") }
        categories.add(category("BOTS", "Rule bots", botChecks))

        // ---------------- AI
        categories.add(HealthCategory("AI", "AI providers", -1, "NOT_CONFIGURED", "Not configured",
            listOf(HealthCheck("Providers", "OFF", "No external AI is connected. Add a key in the AI tab. Signals come from local rules on this phone."))))

        // ---------------- LEARNING
        val learnChecks = ArrayList<HealthCheck>()
        if (stats == null) {
            learnChecks.add(HealthCheck("Ledger", "WARN", "Starting…"))
        } else {
            learnChecks.add(HealthCheck("Ledger", "OK", "${stats.records} predictions, ${stats.results} checked, ${stats.pending} active"))
            learnChecks.add(HealthCheck("Price-path fetch",
                when { stats.consecutiveFetchFailures == 0 -> "OK"; stats.consecutiveFetchFailures < 3 -> "WARN"; else -> "FAIL" },
                if (stats.consecutiveFetchFailures == 0) "Working" else "${stats.consecutiveFetchFailures} failed attempts in a row"))
            learnChecks.add(HealthCheck("Overdue checks", if (stats.overdue == 0) "OK" else "WARN",
                if (stats.overdue == 0) "None" else "${stats.overdue} expired predictions waiting to be checked"))
            learnChecks.add(HealthCheck("Learning cycle", "OK",
                if (stats.lastCycleAt == 0L) "No result to learn from yet" else "Last run ${PredictionLedger.utcLabel(stats.lastCycleAt, true)}"))
        }
        categories.add(category("LEARNING", "Learning", learnChecks))

        // ---------------- NEWS & MACRO
        val newsChecks = listOf(
            HealthCheck("Economic calendar", if ((feed?.calendarEvents ?: 0) > 0) "OK" else "WARN",
                if ((feed?.calendarEvents ?: 0) > 0) "${feed?.calendarEvents} USD events loaded" else "Calendar not loaded"),
            HealthCheck("US Dollar Index", if (feed?.dxyAvailable == true) "OK" else "WARN", if (feed?.dxyAvailable == true) "Live" else "Unavailable (no vote)"),
            HealthCheck("US 10Y yield", if (feed?.us10yAvailable == true) "OK" else "WARN", if (feed?.us10yAvailable == true) "Live" else "Unavailable (no vote)")
        )
        categories.add(category("NEWS", "News & macro", newsChecks))

        // ---------------- DATABASE
        val dbChecks = if (stats == null) listOf(HealthCheck("Ledger file", "WARN", "Starting…")) else listOf(
            HealthCheck("Write", if (stats.lastWriteOk) "OK" else "FAIL", if (stats.lastWriteOk) "Last write OK" else "Write failed: ${stats.lastWriteError}"),
            HealthCheck("Read", if (stats.skippedLines == 0) "OK" else "WARN", if (stats.skippedLines == 0) "All lines readable" else "${stats.skippedLines} unreadable lines skipped"),
            HealthCheck("Size", "OK", if (stats.fileBytes >= 0) String.format(Locale.US, "%.1f KB", stats.fileBytes / 1024.0) else "unknown")
        )
        categories.add(category("DATABASE", "Ledger storage", dbChecks))

        val configured = categories.filter { it.score >= 0 }
        val overall = if (configured.isEmpty()) 0 else configured.map { it.score }.average().roundToInt()

        // ---------------- event log (changes since the previous refresh)
        val t = PredictionLedger.utcLabel(now)
        val src = feed?.source
        if (src != null && prevSource != null && src != prevSource) log(t, if (feed.isLive) "WARNING" else "ERROR", "Data source changed: $prevSource → $src")
        if (src != null && prevSource == null) log(t, "INFO", "Connected: $src (${feed.latencyMs} ms)")
        prevSource = src
        for (c in categories) {
            val before = prevStatus[c.key]
            if (before != null && before != c.status) {
                val level = when { c.status == "HEALTHY" -> "RESOLVED"; c.status == "FAILED" -> "ERROR"; else -> "WARNING" }
                log(t, level, "${c.title}: ${before.replace('_', ' ')} → ${c.status.replace('_', ' ')} (${c.summary})")
            }
        }
        prevStatus = categories.associate { it.key to it.status }
        if (stats != null) {
            if (stats.totalVerifiedThisSession > prevVerified) log(t, "INFO", "${stats.totalVerifiedThisSession - prevVerified} prediction outcome(s) verified")
            prevVerified = stats.totalVerifiedThisSession
            if (stats.lastCycleAt > prevCycleAt && prevCycleAt != 0L) log(t, "INFO", "Learning cycle ran")
            prevCycleAt = stats.lastCycleAt
            if (prevFilters >= 0 && stats.activeFilters != prevFilters) log(t, "WARNING", "Active learned filters: $prevFilters → ${stats.activeFilters}")
            prevFilters = stats.activeFilters
        }

        return SystemHealth(
            overallScore = overall,
            overallStatus = statusOf(overall),
            categories = categories,
            indicators = indicatorChecks,
            bots = bots,
            events = events.toList(),
            checkedAtLabel = t
        )
    }

    private fun log(time: String, level: String, text: String) {
        events.addFirst(SystemEvent(time, level, text))
        while (events.size > 60) events.removeLast()
    }

    companion object {
        fun statusOf(score: Int) = when { score >= 90 -> "HEALTHY"; score >= 60 -> "DEGRADED"; else -> "FAILED" }

        /**
         * Replaces the AI category with the real result of the last AI council run.
         * Providers the user has not connected, switched off, or that routing did not need are not counted.
         */
        fun withAi(h: SystemHealth?, r: AiCouncilReport?): SystemHealth? {
            if (h == null || r == null) return h
            val checks = r.providers.filter { it.status !in setOf("NOT_CONFIGURED", "DISABLED", "SKIPPED") }.map { p ->
                val st = when (p.status) {
                    "ONLINE" -> "OK"
                    "STALE", "PENDING", "RATE_LIMITED", "FACT_CONFLICT", "CIRCUIT_OPEN" -> if (p.status == "CIRCUIT_OPEN" && p.statusDetail.contains("change the key")) "FAIL" else "WARN"
                    else -> "FAIL"
                }
                HealthCheck(p.name, st, p.status.replace('_', ' ') + " • " + p.statusDetail.take(80))
            }
            val ai = if (checks.isEmpty()) {
                HealthCategory("AI", "AI providers", -1, "NOT_CONFIGURED", "Not configured",
                    listOf(HealthCheck("Providers", "OFF", "No external AI is connected. Add a key in the AI tab.")))
            } else category("AI", "AI providers", checks).let { c -> c.copy(summary = "${checks.count { it.status == "OK" }}/${checks.size} voting") }
            val cats = h.categories.map { if (it.key == "AI") ai else it }
            val configured = cats.filter { it.score >= 0 }
            val overall = if (configured.isEmpty()) 0 else configured.map { it.score }.average().roundToInt()
            return h.copy(categories = cats, overallScore = overall, overallStatus = statusOf(overall))
        }

        fun category(key: String, title: String, checks: List<HealthCheck>): HealthCategory {
            if (checks.isEmpty()) return HealthCategory(key, title, 0, "FAILED", "No data", checks)
            val pts = checks.sumOf { when (it.status) { "OK" -> 1.0; "WARN" -> 0.5; else -> 0.0 } }
            val score = (pts / checks.size * 100).roundToInt()
            val ok = checks.count { it.status == "OK" }
            return HealthCategory(key, title, score, statusOf(score), "$ok/${checks.size} healthy", checks)
        }

        /** Checks that an indicator produced a usable value this refresh. */
        fun selfTest(group: String, item: IndicatorItem): HealthCheck {
            val v = item.valueDisplay
            val d = item.detail
            val status = when {
                v.isBlank() -> "FAIL"
                v.contains("NaN") || v.contains("Infinity") -> "FAIL"
                v.trim() == "--" || d.startsWith("Need ") || d.contains("unavailable", ignoreCase = true) || v == "0.00 (+0.00%)" -> "WARN"
                else -> "OK"
            }
            val detail = when (status) {
                "OK" -> "$group • $v"
                "WARN" -> "$group • ${d.ifBlank { v }}"
                else -> "$group • no valid value"
            }
            return HealthCheck(item.name, status, detail)
        }
    }
}

/** Real cockpit facts: regime, multi-timeframe story, warnings and the weighted vote. */
object CockpitInsightsBuilder {

    fun build(a: GoldAnalysisResult, now: Long = System.currentTimeMillis()): CockpitInsights {
        val candles = a.recentCandles
        val newsActive = a.newsMode != null || a.newsTradingPlan?.isNewsActive == true || a.isNewsModeTriggered
        val regime = PredictionLedger.regimeOf(candles, newsActive)
        val atr = PredictionLedger.atrOf(candles)
        val er = efficiency(candles.map { it.close })
        val last = candles.lastOrNull()
        val rangeVsAtr = if (last != null && atr > 0) (last.high - last.low) / atr else 0.0

        val rows = a.mtfMatrix?.timeframes?.filter { it.label.startsWith("PAXG") }?.associateBy { it.timeframe } ?: emptyMap()
        fun story(keys: List<String>): String {
            val present = keys.mapNotNull { k -> rows[k]?.let { k to it.signal } }
            if (present.isEmpty()) return "No data"
            val sigs = present.map { it.second }.toSet()
            val parts = present.joinToString(", ") { "${it.first} ${arrow(it.second)}" }
            return when {
                sigs == setOf(Signal.BUY) -> "Upward pressure ($parts)"
                sigs == setOf(Signal.SELL) -> "Downward pressure ($parts)"
                sigs == setOf(Signal.WAIT) -> "No clear trend ($parts)"
                else -> "Mixed ($parts)"
            }
        }

        val warnings = ArrayList<String>()
        if (a.feed?.isLive == false) warnings.add("Live data unavailable: showing OFFLINE DEMO prices")
        if (a.quantBotSignal?.statusText?.contains("FILTER") == true) warnings.add(if (a.quantBotSignal?.statusText?.contains("RANGE FILTER") == true) "Sideways market: BUY/SELL is shown as WAIT (range filter)" else "A learned filter changed this signal to WAIT")
        val ivMin = RealityEngine.intervalMinutes(a.interval)
        val htf = RealityEngine.higherTimeframeSignal(a.mtfMatrix, ivMin)
        if (htf != null && PredictionLedger.opposite(htf, a.overallSignal)) warnings.add("Higher timeframe points ${htf.name}, against this ${a.overallSignal.name}")
        if (newsActive) warnings.add("High-impact news window: spreads and spikes likely")
        if (rangeVsAtr > 1.5) warnings.add(String.format(Locale.US, "Volatility rising: last candle %.1f× ATR", rangeVsAtr))
        rsiValue(a)?.let { r ->
            if (r >= 70) warnings.add(String.format(Locale.US, "RSI %.0f: overbought", r))
            if (r <= 30) warnings.add(String.format(Locale.US, "RSI %.0f: oversold", r))
        }

        val total = TechnicalEngine.PILLAR_WEIGHTS.values.sum()
        val contributions = a.groups.map { g ->
            val w = TechnicalEngine.PILLAR_WEIGHTS[g.key] ?: 1.0
            val sign = when (g.verdict) { Signal.BUY -> 1.0; Signal.SELL -> -1.0; Signal.WAIT -> 0.0 }
            PillarContribution(g.key, g.title, g.verdict, w, sign * w / total * 100.0)
        }
        return CockpitInsights(
            regime = regime,
            trendEfficiencyPercent = (er * 100).roundToInt(),
            atr = atr,
            lastRangeVsAtr = rangeVsAtr,
            storyShort = story(listOf("5M", "15M")),
            storyMedium = story(listOf("1H")),
            storyHigher = story(listOf("4H", "1D")),
            warnings = warnings.take(3),
            contributions = contributions,
            bullishPoints = contributions.filter { it.points > 0 }.sumOf { it.points },
            bearishPoints = -contributions.filter { it.points < 0 }.sumOf { it.points },
            gatePercent = TechnicalEngine.GATE_PERCENT
        )
    }

    private fun arrow(s: Signal) = when (s) { Signal.BUY -> "↑"; Signal.SELL -> "↓"; Signal.WAIT -> "↔" }

    private fun efficiency(closes: List<Double>): Double {
        if (closes.size < 21) return 0.0
        val w = closes.takeLast(21)
        val net = abs(w.last() - w.first())
        var path = 0.0
        for (i in 1 until w.size) path += abs(w[i] - w[i - 1])
        return if (path > 0) net / path else 0.0
    }

    private fun rsiValue(a: GoldAnalysisResult): Double? {
        val item = a.groups.flatMap { it.indicators }.firstOrNull { it.name.startsWith("RSI") } ?: return null
        return Regex("-?\\d+(\\.\\d+)?").find(item.valueDisplay)?.value?.toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
    }
}
