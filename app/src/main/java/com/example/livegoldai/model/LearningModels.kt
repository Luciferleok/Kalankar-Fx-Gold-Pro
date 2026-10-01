package com.example.livegoldai.model

import kotlinx.serialization.Serializable

/**
 * REAL prediction-learning data.
 *
 * Everything in these classes is computed from the on-device Prediction Ledger
 * (predictions the app actually made, checked against real prices after they expired).
 * No number here is typed in by hand.
 */

@Serializable
data class AccuracyWindow(
    val label: String,          // "Last 20", "7 Days", "All Time"
    val decided: Int,           // CORRECT + WRONG (sideways / no-data excluded)
    val correct: Int,
    val accuracyPercent: Double // -1.0 when decided == 0
)

@Serializable
data class BucketStat(
    val key: String,            // "4h", "LONDON", "TREND_UP", "grp:trend", "70-79" ...
    val label: String,
    val decided: Int,
    val correct: Int,
    val accuracyPercent: Double,     // -1.0 when decided == 0
    val ciLowPercent: Double = -1.0, // 95% Wilson interval, -1 when not enough data
    val ciHighPercent: Double = -1.0,
    val avgConfidence: Double = -1.0 // for calibration buckets
)

@Serializable
data class TimelineEvent(
    val timeLabel: String,  // "14:05 UTC"
    val text: String
)

@Serializable
data class FailureReport(
    val id: String,
    val createdLabel: String,
    val interval: String,
    val signal: Signal,
    val confidence: Int,
    val entryPrice: Double,
    val movePoints: Double,          // price change over the horizon (verification source)
    val thresholdPoints: Double,
    val maxFavorable: Double,
    val maxAdverse: Double,
    val outcome: String,             // INCORRECT / INVALIDATED
    val regime: String,
    val session: String,
    val newsActive: Boolean,
    val tags: List<String>,          // measurable error tags, may be ["UNKNOWN"]
    val attribution: Map<String, Int>, // tag -> % (sums to 100)
    val warningsIgnored: List<String>, // sources that voted against and were right
    val counterfactual: String,
    val timeline: List<TimelineEvent>,
    val verifySource: String
)

@Serializable
data class CorrectionCandidate(
    val id: String,              // "C-AGR70", "C-GRP-momentum" ...
    val titleEnglish: String,
    val titleHindi: String,
    val titleMarathi: String,
    val ruleEnglish: String,     // "If agreement < 70% -> WAIT"
    val stage: String,           // COLLECTING / REJECTED / CANDIDATE / SHADOW / PROMOTED / ROLLED_BACK
    val necessity: String,       // NO_CORRECTION_NEEDED / POSSIBLE_ISSUE / LIKELY_SYSTEMIC / CONFIRMED_SYSTEMIC
    val affectedSamples: Int,    // decided predictions the rule would have blocked
    val requiredSamples: Int,
    val blockedAccuracy: Double, // accuracy of the predictions the rule blocks (-1 if none)
    val baselineAccuracy: Double,
    val keptAccuracy: Double,    // accuracy if the rule had been active
    val improvementPoints: Double,
    val shadowSamples: Int,
    val shadowBlockedAccuracy: Double,
    val note: String
)

@Serializable
data class ModelHistoryEvent(
    val timeLabel: String,
    val event: String,           // PROMOTED / ROLLED_BACK / REJECTED / SHADOW_STARTED
    val detail: String
)

@Serializable
data class LearningSnapshot(
    val generatedAtLabel: String,
    val modelVersion: String,             // "Base rules + 0 learned filters"
    val engineState: String,              // OBSERVING / COLLECTING_SAMPLE / CANDIDATE_FOUND / SHADOW_TESTING / PROMOTED ...
    val totalRecorded: Int,
    val active: Int,
    val correct: Int,
    val wrong: Int,                       // INCORRECT + INVALIDATED
    val invalidated: Int,
    val sideways: Int,
    val waitCalls: Int,                   // WAIT predictions (not counted in accuracy)
    val waitAvoidedMove: Int,             // WAIT and market really stayed flat
    val waitMissedMove: Int,              // WAIT but market moved
    val dataFailures: Int,
    val marketClosed: Int,
    val currentStreak: Int,               // +n wins / -n losses
    val bestStreak: Int,
    val windows: List<AccuracyWindow>,    // Last 20/50/100/250, Today, 7D, 30D, All
    val today: AccuracyWindow,
    val brierAll: Double,                 // -1 when no data
    val brier7d: Double,
    val brier30d: Double,
    val calibrationErrorPoints: Double,   // avg |confidence - real accuracy|, -1 when not enough data
    val calibration: List<BucketStat>,    // 50-59, 60-69 ...
    val byInterval: List<BucketStat>,
    val bySession: List<BucketStat>,
    val byRegime: List<BucketStat>,
    val byNews: List<BucketStat>,
    val bySource: List<BucketStat>,       // groups, rule engines, bots, playbook (real accuracy of each)
    val failures: List<FailureReport>,    // newest first
    val failureClusters: List<BucketStat>,// tag -> count (decided = count, accuracy unused)
    val candidates: List<CorrectionCandidate>,
    val history: List<ModelHistoryEvent>,
    val driftLevel: String,               // LOW / MODERATE / HIGH / NOT_ENOUGH_DATA
    val driftDetail: String,
    val minimumSamples: Int,
    val lastCycleReport: List<String>,    // what the last learning cycle actually did
    val pendingNote: String               // e.g. "Next check: 18:00 UTC"
) {
    fun sourceAccuracy(key: String): BucketStat? = bySource.firstOrNull { it.key == key }
}
