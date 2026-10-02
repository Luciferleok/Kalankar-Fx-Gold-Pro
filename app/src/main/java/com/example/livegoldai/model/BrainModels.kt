package com.example.livegoldai.model

import kotlinx.serialization.Serializable

/**
 * MARKET BRAIN (V10) — one structured market state built only from measured data.
 * The brain never invents a direction: the signal still comes from the quant engine.
 * Anything that cannot be measured yet says so ("UNAVAILABLE", "INSUFFICIENT HISTORY", "untested").
 */

@Serializable
data class CrossAsset(
    val key: String,              // DXY, US10Y, SILVER, SPX, VIX, USDJPY, OIL
    val name: String,
    val symbol: String,
    val health: String,           // OK / STALE / UNAVAILABLE
    val source: String,
    val fetchedAtMs: Long,
    val latencyMs: Long,
    val lastBarMs: Long,
    val last: Double,
    val change24hPct: Double,
    val corr7d: Double,           // rolling correlation of hourly returns with gold; NaN-free: valid only if n7 >= MIN
    val corr30d: Double,
    val n7: Int,
    val n30: Int,
    val relation: String,         // NEGATIVE / POSITIVE / NONE / UNKNOWN
    val strength: String,         // STRONG / MODERATE / WEAK / NONE / UNKNOWN
    val trend: String,            // STABLE / WEAKENING / STRENGTHENING / FLIPPED / UNKNOWN
    val implied: Signal?,         // gold direction implied by this asset's last-24h move and the measured relation
    val divergence: Boolean,      // relation normally holds but gold moved the other way
    val note: String
)

@Serializable
data class CrossMarketReport(
    val available: Boolean,
    val assets: List<CrossAsset>,
    val supportBull: Int,
    val supportBear: Int,
    val summary: String,          // SUPPORTS BULLISH / SUPPORTS BEARISH / MIXED / NEUTRAL / UNAVAILABLE
    val divergences: List<String>,
    val goldRefLast: Double,      // GC=F last, 0 if unavailable
    val goldRefBarMs: Long,       // time of that last bar
    val gold24hPct: Double,
    val fetchedAtMs: Long
)

@Serializable
data class AnomalyCheck(val name: String, val value: String, val severity: Int)   // 0 normal .. 3 extreme

@Serializable
data class AnomalyReport(
    val level: String,            // LOW / MODERATE / HIGH / EXTREME
    val severity: Int,
    val reasons: List<String>,
    val checks: List<AnomalyCheck>
)

@Serializable
data class AnalogReport(
    val status: String,           // OK / INSUFFICIENT
    val historySize: Int,
    val used: Int,
    val upPct: Int,
    val downPct: Int,
    val sidePct: Int,
    val avgMove: Double,
    val similarity: Int,          // 0-100, how close the matches are (not accuracy)
    val note: String
)

@Serializable
data class FamiliarityReport(
    val status: String,           // NORMAL / UNUSUAL / OUT_OF_DISTRIBUTION / UNKNOWN
    val pct: Int,                 // share of features inside the historical 5-95% range, -1 if unknown
    val outside: List<String>,
    val historySize: Int
)

@Serializable
data class UncertaintyItem(val name: String, val level: String, val detail: String)

@Serializable
data class BudgetLine(val label: String, val delta: Int)

@Serializable
data class LabelStat(val label: String, val value: String)

@Serializable
data class FeatureDefUi(val name: String, val formula: String, val inputs: String)

@Serializable
data class BrainReport(
    val builtAtMs: Long,
    val regime: String,
    val transitionRisk: String,           // "42% (N=120)" or "untested (N=3)"
    val anomaly: AnomalyReport,
    val mtfSummary: String,
    val crossMarket: CrossMarketReport,
    val aiSummary: String,
    val botSummary: String,
    val analogs: AnalogReport,
    val familiarity: FamiliarityReport,
    val quantSignal: Signal,
    val baseConfidence: Int,
    val baseLabel: String,                // "calibrated" / "pillar agreement (uncalibrated)"
    val budget: List<BudgetLine>,
    val adjustedConfidence: Int,
    val uncertainty: List<UncertaintyItem>,
    val totalUncertainty: String,
    val qualityIndex: Int,
    val qualityClass: String,
    val qualityParts: List<LabelStat>,
    val edge: String,                     // NO EDGE / WEAK / MODERATE / STRONG
    val edgeNote: String,
    val strongSignal: List<LabelStat>,
    val coverage: String,
    val dayOfWeek: List<LabelStat>,
    val sessions: List<LabelStat>,
    val featureVersion: String,
    val featuresNow: Int,
    val featuresStored: Int,
    val featureDefs: List<FeatureDefUi>,
    val lineage: List<LabelStat>
)
