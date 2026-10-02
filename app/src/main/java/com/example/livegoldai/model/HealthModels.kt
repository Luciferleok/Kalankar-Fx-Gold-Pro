package com.example.livegoldai.model

import kotlinx.serialization.Serializable

/** Where the shown price came from and how fresh it is. Measured, never estimated. */
@Serializable
data class FeedStatus(
    val source: String,          // "Twelve Data XAU/USD", "Binance PAXG/USDT", "Yahoo GC=F", "OFFLINE DEMO"
    val fetchedAtMs: Long,       // when this data arrived on the phone
    val latencyMs: Long,         // how long the fetch + calculation took
    val isLive: Boolean,         // false for the offline demo fallback
    val dxyAvailable: Boolean,
    val us10yAvailable: Boolean,
    val calendarEvents: Int,
    val sourcesTried: Int        // 1 = primary worked, 2+ = backup was used
)

@Serializable
data class HealthCheck(
    val name: String,
    val status: String,          // OK / WARN / FAIL / OFF
    val detail: String
)

@Serializable
data class HealthCategory(
    val key: String,             // DATA, TIMEFRAMES, INDICATORS, BOTS, AI, LEARNING, NEWS, DATABASE
    val title: String,
    val score: Int,              // 0-100 = passed checks share; -1 = not configured (excluded)
    val status: String,          // HEALTHY / DEGRADED / FAILED / NOT_CONFIGURED
    val summary: String,         // e.g. "31/32 healthy"
    val checks: List<HealthCheck>
)

@Serializable
data class BotActivity(
    val id: String,
    val name: String,
    val signal: Signal,
    val lastEvaluationLabel: String,
    val healthy: Boolean,
    val verifiedLabel: String    // real ledger accuracy or "untested (N=3)"
)

@Serializable
data class SystemEvent(
    val timeLabel: String,
    val level: String,           // INFO / WARNING / ERROR / RESOLVED
    val text: String
)

@Serializable
data class SystemHealth(
    val overallScore: Int,
    val overallStatus: String,
    val categories: List<HealthCategory>,
    val indicators: List<HealthCheck>,   // one per indicator, for the grid
    val bots: List<BotActivity>,
    val events: List<SystemEvent>,       // newest first, this app session
    val checkedAtLabel: String
) {
    fun category(key: String): HealthCategory? = categories.firstOrNull { it.key == key }
}

/** Facts shown on the cockpit, all derived from real candles / MTF data. */
@Serializable
data class CockpitInsights(
    val regime: String,                 // TREND_UP / TREND_DOWN / RANGE / TRANSITION / VOLATILITY_EXPANSION / NEWS_EVENT
    val trendEfficiencyPercent: Int,    // Kaufman efficiency ratio of last 20 candles
    val atr: Double,
    val lastRangeVsAtr: Double,         // last candle range / ATR
    val storyShort: String,
    val storyMedium: String,
    val storyHigher: String,
    val warnings: List<String>,         // max 3, most important first
    val contributions: List<PillarContribution>,
    val bullishPoints: Double,          // weighted BUY share, same math as the 68% gate
    val bearishPoints: Double,
    val gatePercent: Double
)

@Serializable
data class PillarContribution(
    val key: String,
    val title: String,
    val verdict: Signal,
    val weight: Double,
    val points: Double                  // +BUY / -SELL share of total weight, in %
)

/** Proof that the prediction recorder / checker is really running. All values come from the ledger file. */
@Serializable
data class LedgerPulse(
    val status: String,           // RECORDING / MARKET_CLOSED / NOT_RECORDING / WRITE_ERROR
    val statusDetail: String,
    val recorded: Int,
    val checked: Int,
    val active: Int,
    val overdue: Int,
    val lastRecorded: String,     // "02 Oct 05:33 UTC • 1h • SELL (6m ago)"
    val nextDue: String,
    val lastCheck: String,
    val ledgerStarted: String,    // first prediction in the file: shows if data was wiped by a reinstall
    val fileSize: String,
    val byInterval: List<LabelStat> = emptyList(),   // per timeframe: right / wrong / no edge / active
    val recent: List<LabelStat> = emptyList(),       // newest checked predictions
    val background: String = ""                      // last run of the background recorder
)
