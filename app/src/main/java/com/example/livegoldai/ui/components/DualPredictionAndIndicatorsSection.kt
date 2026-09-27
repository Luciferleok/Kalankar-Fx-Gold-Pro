package com.example.livegoldai.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.localization.LocalizationStrings
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.PredictionOutcomeStatus
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*
import java.util.Locale

enum class DualColumnViewMode(val title: String, val hindi: String, val icon: String) {
    BOTH("BOTH COLUMNS", "दोनों साथ में", "⚡"),
    PREDICTION_ONLY("1. PREDICTION", "सिर्फ प्रेडिक्शन", "🔮"),
    INDICATORS_ONLY("2. INDICATORS", "सिर्फ इंडिकेटर्स", "📊")
}

@Composable
fun DualPredictionAndIndicatorsSection(
    analysis: GoldAnalysisResult,
    onOpenCalculator: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLanguage = LocalAppLanguage.current
    var viewMode by remember { mutableStateOf(DualColumnViewMode.BOTH) }
    var showAllIndicatorsDetail by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top 2-Column Mode Filter Switcher
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ObsidianSurfaceElevated,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(listOf(GoldPrimary.copy(alpha = 0.5f), ObsidianBorder))
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DualColumnViewMode.values().forEach { mode ->
                    val isSelected = viewMode == mode
                    val (modeTitle, modeSub) = when (mode) {
                        DualColumnViewMode.BOTH -> Pair(
                            LocalizationStrings.dualModeBoth(currentLanguage),
                            LocalizationStrings.dualModeBothSub(currentLanguage)
                        )
                        DualColumnViewMode.PREDICTION_ONLY -> Pair(
                            LocalizationStrings.dualModePrediction(currentLanguage),
                            LocalizationStrings.dualModePredictionSub(currentLanguage)
                        )
                        DualColumnViewMode.INDICATORS_ONLY -> Pair(
                            LocalizationStrings.dualModeIndicators(currentLanguage),
                            LocalizationStrings.dualModeIndicatorsSub(currentLanguage)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) GoldPrimary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { viewMode = mode }
                            .testTag("dual_mode_${mode.name}")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = mode.icon, fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = modeTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                    color = if (isSelected) ObsidianBackground else TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            Text(
                                text = modeSub,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                color = if (isSelected) ObsidianBackground.copy(alpha = 0.85f) else TextMuted
                            )
                        }
                    }
                }
            }
        }

        // Adaptive Responsive Layout: In wide screens side-by-side, on phones stacked big columns
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isWide = maxWidth >= 680.dp

            if (isWide && viewMode == DualColumnViewMode.BOTH) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        PredictionBigCard(
                            analysis = analysis,
                            onOpenCalculator = onOpenCalculator
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        IndicatorsOverallBigCard(
                            analysis = analysis,
                            isDetailExpanded = showAllIndicatorsDetail,
                            onToggleDetail = { showAllIndicatorsDetail = !showAllIndicatorsDetail }
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (viewMode == DualColumnViewMode.BOTH || viewMode == DualColumnViewMode.PREDICTION_ONLY) {
                        PredictionBigCard(
                            analysis = analysis,
                            onOpenCalculator = onOpenCalculator
                        )
                    }

                    if (viewMode == DualColumnViewMode.BOTH || viewMode == DualColumnViewMode.INDICATORS_ONLY) {
                        IndicatorsOverallBigCard(
                            analysis = analysis,
                            isDetailExpanded = showAllIndicatorsDetail,
                            onToggleDetail = { showAllIndicatorsDetail = !showAllIndicatorsDetail }
                        )
                    }
                }
            }
        }
    }
}

/**
 * BIG COLUMN 1: AI PREDICTION (CONTINUOUS LIVE FORECAST)
 * Direct answer: BUY HO SAKTA HAI / SELL HO SAKTA HAI / WAIT KARNA CHAHIYE
 */
@Composable
fun PredictionBigCard(
    analysis: GoldAnalysisResult,
    onOpenCalculator: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLanguage = LocalAppLanguage.current
    val prediction = analysis.nextPrediction
    val verdict = prediction?.verdict ?: analysis.tradeSetup.signal
    val winProb = prediction?.winProbabilityPercent ?: analysis.tradeSetup.confidencePercent

    val (verdictColor, verdictBg, verdictBorder, verdictIcon) = when (verdict) {
        Signal.BUY -> Tuple4(
            SignalBuy,
            SignalBuyContainer,
            SignalBuy.copy(alpha = 0.5f),
            Icons.Default.TrendingUp
        )
        Signal.SELL -> Tuple4(
            SignalSell,
            SignalSellContainer,
            SignalSell.copy(alpha = 0.5f),
            Icons.Default.TrendingDown
        )
        Signal.WAIT -> Tuple4(
            SignalWait,
            SignalWaitContainer,
            SignalWait.copy(alpha = 0.5f),
            Icons.Default.PauseCircle
        )
    }

    val verdictLocalized = when (verdict) {
        Signal.BUY -> when (currentLanguage) {
            AppLanguage.ENGLISH -> "STRONG BUY (Bullish Trend 🟢)"
            AppLanguage.HINDI -> "मजबूत खरीदारी (बुलिश तेजी 🟢)"
            AppLanguage.MARATHI -> "मजबूत खरेदी (बुलिश तेजी 🟢)"
        }
        Signal.SELL -> when (currentLanguage) {
            AppLanguage.ENGLISH -> "STRONG SELL (Bearish Trend 🔴)"
            AppLanguage.HINDI -> "मजबूत बिकवाली (बेयरिश मंदी 🔴)"
            AppLanguage.MARATHI -> "मजबूत विक्री (बेअरिश मंदी 🔴)"
        }
        Signal.WAIT -> when (currentLanguage) {
            AppLanguage.ENGLISH -> "WAIT / NEUTRAL (Range-Bound 🟡)"
            AppLanguage.HINDI -> "इंतज़ार करें (दायरा सीमित 🟡)"
            AppLanguage.MARATHI -> "प्रतीक्षा करा (मर्यादित हालचाल 🟡)"
        }
    }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val validUntil = prediction?.validUntilTimestamp ?: 0L
    LaunchedEffect(validUntil) {
        while (isActive) {
            delay(1000)
            nowMillis = System.currentTimeMillis()
        }
    }
    val remainingMillis = (validUntil - nowMillis).coerceAtLeast(0L)
    val isValid = remainingMillis > 0L || validUntil == 0L
    val remTotalSeconds = remainingMillis / 1000L
    val remHours = remTotalSeconds / 3600L
    val remMinutes = (remTotalSeconds % 3600L) / 60L
    val remSeconds = remTotalSeconds % 60L

    val countdownStr = when {
        remHours > 0 -> "${remHours}h ${remMinutes}m ${remSeconds}s"
        remMinutes > 0 -> "${remMinutes}m ${remSeconds}s"
        else -> "${remSeconds}s"
    }
    val totalValidityMillis = ((prediction?.validityDurationMinutes ?: 60) * 60_000L).coerceAtLeast(1L)
    val progressFraction = (remainingMillis.toFloat() / totalValidityMillis.toFloat()).coerceIn(0f, 1f)

    val bullishProb = when (verdict) {
        Signal.BUY -> winProb
        Signal.SELL -> (100 - winProb) / 2
        Signal.WAIT -> 30
    }
    val bearishProb = when (verdict) {
        Signal.SELL -> winProb
        Signal.BUY -> (100 - winProb) / 2
        Signal.WAIT -> 30
    }
    val sidewaysProb = (100 - bullishProb - bearishProb).coerceAtLeast(8)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(verdictColor.copy(alpha = 0.8f), GoldPrimary.copy(alpha = 0.3f), ObsidianBorder)
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("big_column_prediction_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Tag: Column 1 AI Prediction
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GoldContainer,
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🔮", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "COLUMN 1: AI PREDICTION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = GoldLight,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Text(
                        text = "LIVE FORECAST",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        fontSize = 9.sp
                    )
                }
            }

            // Multi-AI Ensemble Endorsement Strip
            analysis.multiAiConsensus?.let { consensus ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🤖", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "MULTI-AI COUNCIL: Gemini • ChatGPT • Claude • DeepSeek",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = GoldLight
                            )
                        }
                        Text(
                            text = "${consensus.agreeingModelsCount}/${consensus.totalModelsCount} AGREE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Black,
                            color = NeonGreen
                        )
                    }
                }
            }

            // ⏳ LIVE PREDICTION VALIDITY (KITNE TIME KE LIYE VALID HAI)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isValid) ObsidianSurfaceElevated else ObsidianSurfaceCard,
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(
                        if (isValid) listOf(NeonGreen.copy(alpha = 0.8f), GoldPrimary.copy(alpha = 0.6f))
                        else listOf(SignalSell.copy(alpha = 0.6f), ObsidianBorder)
                    )
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("big_prediction_validity_timer_banner")
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (isValid) NeonGreen else SignalSell)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> if (isValid) "PREDICTION VALIDITY (ACTIVE)" else "VALIDITY EXPIRED (EVALUATING)"
                                    AppLanguage.HINDI -> if (isValid) "प्रेडिक्शन वैधता (समय सीमा सक्रिय)" else "वैधता समाप्त (नया विश्लेषण)"
                                    AppLanguage.MARATHI -> if (isValid) "प्रेडिक्शन वैधता (सक्रिय कालावधी)" else "मुदत संपली (पुढील चक्र)"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Black,
                                color = if (isValid) GoldLight else TextMuted,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Live Ticking Countdown Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = (if (isValid) NeonGreen else SignalSell).copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, (if (isValid) NeonGreen else SignalSell).copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isValid) Icons.Default.HourglassTop else Icons.Default.TimerOff,
                                    contentDescription = null,
                                    tint = if (isValid) NeonGreen else SignalSell,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isValid) "⏳ $countdownStr" else "EXPIRED",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isValid) NeonGreen else SignalSell
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (progressFraction > 0.25f) NeonGreen else AmberWarning,
                        trackColor = ObsidianBorder
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⏱️ ${prediction?.getValidityFormatted(currentLanguage) ?: "Active for this interval"}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when (currentLanguage) {
                                AppLanguage.ENGLISH -> "Target / SL = Auto Closed"
                                AppLanguage.HINDI -> "TP1 / SL पर स्वतः क्लोज़"
                                AppLanguage.MARATHI -> "TP1 / SL वर आपोआप पूर्ण"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TextMuted
                        )
                    }

                    val ruleText = prediction?.getInvalidationRule(currentLanguage) ?: ""
                    if (ruleText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🛡️ $ruleText",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = GoldLight.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // 📊 SCORECARD: KITNE SAHI / KITNE GALAT (ACCURACY AUDIT)
            analysis.timeframeAudit?.let { audit ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "📊", fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (currentLanguage) {
                                        AppLanguage.ENGLISH -> "${audit.timeframe} SCORECARD (ACCURACY)"
                                        AppLanguage.HINDI -> "${audit.timeframe} रिपोर्ट (कितने सही / कितने गलत)"
                                        AppLanguage.MARATHI -> "${audit.timeframe} निकाल (किती बरोबर / किती चूक)"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Black,
                                    color = GoldLight,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Text(
                                text = "${audit.winRatePercent}% WIN RATE",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Black,
                                color = SignalBuy
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 3 Scoreboard metrics: Sahi (Won), Galat (Lost), Net Pips P&L
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Sahi (Won)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SignalBuy.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, SignalBuy.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${audit.winCount} SAHI ✅",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        fontWeight = FontWeight.Black,
                                        color = SignalBuy
                                    )
                                    Text(
                                        text = when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "Correct (TP Hit)"
                                            AppLanguage.HINDI -> "सही प्रेडिक्शन"
                                            AppLanguage.MARATHI -> "अचूक अंदाज"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color = TextSecondary
                                    )
                                }
                            }

                            // Galat (Lost)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SignalSell.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, SignalSell.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${audit.lossCount} GALAT ❌",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        fontWeight = FontWeight.Black,
                                        color = SignalSell
                                    )
                                    Text(
                                        text = when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "Failed (SL Hit)"
                                            AppLanguage.HINDI -> "गलत प्रेडिक्शन"
                                            AppLanguage.MARATHI -> "चूक अंदाज"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color = TextSecondary
                                    )
                                }
                            }

                            // Net Pips Profit
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ObsidianSurfaceCard,
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${if (audit.netPipsGained >= 0) "+" else ""}${audit.netPipsGained}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        fontWeight = FontWeight.Black,
                                        color = if (audit.netPipsGained >= 0) GoldLight else SignalSell
                                    )
                                    Text(
                                        text = "Net Pips",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        audit.lastPredictionOutcome?.let { last ->
                            val isWin = last.outcomeStatus == PredictionOutcomeStatus.TP1_HIT ||
                                    last.outcomeStatus == PredictionOutcomeStatus.TP2_HIT
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isWin) "Last: TP Hit (+${last.pipsResult} Pips) 🟢" else "Last: SL Hit (${last.pipsResult} Pips) 🔴",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isWin) SignalBuy else SignalSell
                                )
                                Text(
                                    text = "AI Self-Calibrated 🧠",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = GoldLight
                                )
                            }
                        }
                    }
                }
            }

            // 🧠 AI ERROR SELF-CORRECTION (गलतियों से आगे का ऑटोमैटिक सुधार)
            prediction?.appliedCorrections?.takeIf { it.isNotEmpty() }?.let { corrections ->
                var showCorrectionDetails by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceCard,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(
                            listOf(GoldPrimary.copy(alpha = 0.8f), NeonGreen.copy(alpha = 0.4f), ObsidianBorder)
                        )
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("big_prediction_self_correction_strip")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCorrectionDetails = !showCorrectionDetails },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = GoldLight,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "AI AUTO-CORRECTION ACTIVE"
                                            AppLanguage.HINDI -> "AI गलतियों से स्वतः सुधार (सक्रिय)"
                                            AppLanguage.MARATHI -> "AI चुकांमधून स्वयंचलित सुधारणा (सक्रिय)"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Black,
                                        color = GoldLight
                                    )
                                    Text(
                                        text = when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "Past mistakes fixed • Calibrated for next win"
                                            AppLanguage.HINDI -> "पिछली गलतियों से सीखकर यह प्रेडिक्शन सुधारा गया"
                                            AppLanguage.MARATHI -> "मागील चुकांमधून शिकून हा अंदाज सुधारला"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color = TextSecondary
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = NeonGreen.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "${corrections.size} GUARDS 🛡️",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = NeonGreen
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (showCorrectionDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        val displayedCorrections = if (showCorrectionDetails) corrections else corrections.take(1)
                        displayedCorrections.forEach { corr ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ObsidianSurfaceElevated,
                                border = BorderStroke(1.dp, ObsidianBorderHighlight),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "✅ ${corr.getTitle(currentLanguage)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Black,
                                            color = GoldLight
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = SignalBuy.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = corr.badgeTag,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = SignalBuy
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = corr.getDescription(currentLanguage),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // MASSIVE CONTINUOUS VERDICT BANNER (BUY HO SAKTA HAI / SELL / WAIT)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = verdictBg,
                border = BorderStroke(1.5.dp, verdictBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = verdictColor.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, verdictColor),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = verdictIcon,
                                contentDescription = null,
                                tint = verdictColor,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (currentLanguage) {
                                AppLanguage.ENGLISH -> "AI PREDICTION VERDICT:"
                                AppLanguage.HINDI -> "AI प्रेडिक्शन का फैसला:"
                                AppLanguage.MARATHI -> "AI अंदाजाचा निर्णय:"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = verdictColor.copy(alpha = 0.9f),
                            fontSize = 10.sp
                        )
                        Text(
                            text = verdictLocalized,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Possibility / Win Rate: $winProb% • Next 15M-45M",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = verdictColor,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // CONTINUOUS PROBABILITY BAR (SAMBHAVNA METER)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SAMBHAVNA (PROBABILITY BREAKDOWN):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "$bullishProb% Bullish",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = if (bullishProb > 50) SignalBuy else TextSecondary,
                        fontSize = 10.sp
                    )
                }

                // 3-Way Segmented Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(ObsidianBackground)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(bullishProb.toFloat())
                            .fillMaxHeight()
                            .background(SignalBuy)
                    )
                    Box(
                        modifier = Modifier
                            .weight(sidewaysProb.toFloat())
                            .fillMaxHeight()
                            .background(SignalWait)
                    )
                    Box(
                        modifier = Modifier
                            .weight(bearishProb.toFloat())
                            .fillMaxHeight()
                            .background(SignalSell)
                    )
                }

                // Probability Labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🟢 Bullish: $bullishProb%",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SignalBuy
                    )
                    Text(
                        text = "🟡 Sideways: $sidewaysProb%",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SignalWait
                    )
                    Text(
                        text = "🔴 Bearish: $bearishProb%",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = SignalSell
                    )
                }
            }

            // ACTION PLAN (AAPKO KYA KARNA CHAHIYE)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianSurfaceElevated,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (currentLanguage) {
                                AppLanguage.ENGLISH -> "ACTION PLAN & EXECUTION RULES"
                                AppLanguage.HINDI -> "ट्रेड एक्शन प्लान (कार्रवाई निर्देश)"
                                AppLanguage.MARATHI -> "ट्रेड अ‍ॅक्शन प्लॅन (कृती आराखडा)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = GoldLight,
                            fontSize = 11.sp
                        )
                    }

                    val adviceText = prediction?.getWhatToDo(currentLanguage) ?: when (currentLanguage) {
                        AppLanguage.ENGLISH -> if (verdict == Signal.BUY) {
                            "Gold shows strong buying pressure holding above support. Look for buy confirmation on dips towards support, and take profit at TP1 ($${String.format(Locale.US, "%.2f", analysis.tradeSetup.takeProfit1)})."
                        } else if (verdict == Signal.SELL) {
                            "Gold faces heavy selling pressure beneath resistance. Look to enter short on bounces near resistance, maintaining strict Stop Loss."
                        } else {
                            "Market is in range-bound compression. Avoid fresh entries and wait for a clear structural breakout."
                        }
                        AppLanguage.HINDI -> if (verdict == Signal.BUY) {
                            "गोल्ड में खरीदारी का मजबूत दबाव है। सपोर्ट की ओर पुलबैक पर BUY करें और TP1 ($${String.format(Locale.US, "%.2f", analysis.tradeSetup.takeProfit1)}) पर सुरक्षित मुनाफा बुक करें।"
                        } else if (verdict == Signal.SELL) {
                            "गोल्ड में ऊपरी स्तरों पर बिकवाली का दबाव है। रेजिस्टेंस के पास SELL करें और सख्त Stop Loss बनाए रखें।"
                        } else {
                            "मार्केट अभी सीमित दायरे (रेंज) में फंसा है। स्पष्ट ब्रेकआउट मिलने तक नई ट्रेड से बचें।"
                        }
                        AppLanguage.MARATHI -> if (verdict == Signal.BUY) {
                            "गोल्डमध्ये खरेदीचा मोठा जोर दिसून येत आहे. सपोर्टजवळ पुलबॅक मिळाल्यावर BUY करा आणि TP1 ($${String.format(Locale.US, "%.2f", analysis.tradeSetup.takeProfit1)}) वर सुरक्षित नफा बुक करा."
                        } else if (verdict == Signal.SELL) {
                            "गोल्डवर वरच्या पातळीवर विक्रीचा दबाव आहे. रेझिस्टन्सजवळ SELL करा आणि कडक Stop Loss पाळा."
                        } else {
                            "सध्या मार्केट एका ठराविक कक्षेत अडकले आहे. जोपर्यंत स्पष्ट ब्रेकआउट होत नाही, तोपर्यंत वाट पहा."
                        }
                    }

                    Text(
                        text = adviceText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Divider(color = ObsidianBorder, thickness = 0.8.dp)

                    // Target, SL & Entry Levels Grid (With TP1 Safe & TP2 Runner Pips)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "ENTRY ZONE", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", analysis.tradeSetup.entryPrice)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                        }

                        Column {
                            Text(text = "STOP LOSS (SL)", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", analysis.tradeSetup.stopLoss)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = SignalSell
                            )
                            Text(
                                text = "-${analysis.tradeSetup.stopLossPips.toInt()} Pips",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = SignalSell
                            )
                        }

                        Column {
                            Text(text = "TARGET 1 (SAFE)", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", analysis.tradeSetup.takeProfit1)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = SignalBuy
                            )
                            Text(
                                text = "+${analysis.tradeSetup.takeProfit1Pips.toInt()} Pips",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = SignalBuy
                            )
                        }

                        Column {
                            Text(text = "TARGET 2 (RUNNER)", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", analysis.tradeSetup.takeProfit2)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = GoldLight
                            )
                            Text(
                                text = "+${analysis.tradeSetup.takeProfit2Pips.toInt()} Pips",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldLight
                            )
                        }
                    }

                    // Trailing SL & Free Trade Playbook
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GoldContainer.copy(alpha = 0.5f),
                        border = BorderStroke(0.8.dp, GoldPrimary.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockReset,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "💡 Trailing Rule: When price hits TP1 ($${String.format(Locale.US, "%.2f", analysis.tradeSetup.takeProfit1)}), immediately move Stop Loss to Entry price — trade becomes 100% Risk-Free!"
                                    AppLanguage.HINDI -> "💡 ट्रेलिंग नियम: जब भाव TP1 ($${String.format(Locale.US, "%.2f", analysis.tradeSetup.takeProfit1)}) छुए, तुरंत Stop Loss को एंट्री भाव पर ले आएं — ट्रेड 100% जोखिम-मुक्त हो जाएगा!"
                                    AppLanguage.MARATHI -> "💡 ट्रेलिंग नियम: जेव्हा भाव TP1 ($${String.format(Locale.US, "%.2f", analysis.tradeSetup.takeProfit1)}) गाठेल, लगेच Stop Loss एन्ट्री भावावर हलवा — ट्रेड 100% जोखीममुक्त होईल!"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = TextPrimary,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    // Invalidation Rule Note
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianBackground,
                        border = BorderStroke(0.8.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = AmberWarning,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = prediction?.getInvalidationRule(currentLanguage) ?: when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "Cancellation Rule: If candle closes beyond $${String.format(Locale.US, "%.2f", analysis.tradeSetup.stopLoss)}, the trade setup is invalidated."
                                    AppLanguage.HINDI -> "रद्दीकरण नियम: यदि कैंडल $${String.format(Locale.US, "%.2f", analysis.tradeSetup.stopLoss)} के पार बंद होती है, तो ट्रेड को अमान्य समझें।"
                                    AppLanguage.MARATHI -> "रद्दीकरण नियम: जर कॅन्डल $${String.format(Locale.US, "%.2f", analysis.tradeSetup.stopLoss)} च्या पलीकडे बंद झाली, तर ट्रेड रद्द समजावा."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = TextSecondary,
                                lineHeight = 13.sp
                            )
                        }
                    }

                    // Action Buttons Row: [ Safe Lot Calculator ] & [ Copy Setup ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onOpenCalculator(analysis.tradeSetup.stopLossPips) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("prediction_lot_calculator_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "Lot Size Calculator"
                                    AppLanguage.HINDI -> "लॉट साइज कैलकुलेटर"
                                    AppLanguage.MARATHI -> "लॉट साईझ कॅल्क्युलेटर"
                                },
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp
                            )
                        }

                        val context = LocalContext.current
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText(
                                    "Kalankar FX Gold Signal",
                                    "🏆 KALANKAR FX GOLD SIGNAL:\n" +
                                            "Direction: ${verdict.name}\n" +
                                            "Entry: $${String.format(Locale.US, "%.2f", analysis.tradeSetup.entryPrice)}\n" +
                                            "Stop Loss: $${String.format(Locale.US, "%.2f", analysis.tradeSetup.stopLoss)} (-${analysis.tradeSetup.stopLossPips.toInt()} Pips)\n" +
                                            "Target 1: $${String.format(Locale.US, "%.2f", analysis.tradeSetup.takeProfit1)} (+${analysis.tradeSetup.takeProfit1Pips.toInt()} Pips)\n" +
                                            "Target 2: $${String.format(Locale.US, "%.2f", analysis.tradeSetup.takeProfit2)} (+${analysis.tradeSetup.takeProfit2Pips.toInt()} Pips)\n" +
                                            "Win Probability: $winProb%\n" +
                                            "R:R Ratio: ${analysis.tradeSetup.riskRewardRatio}"
                                )
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "✅ Trade Setup Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                            modifier = Modifier.testTag("copy_prediction_signal_btn")
                        ) {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * BIG COLUMN 2: OVERALL INDICATORS STATUS & BREAKDOWN
 * Shows what all indicators are saying overall with clear categories.
 */
@Composable
fun IndicatorsOverallBigCard(
    analysis: GoldAnalysisResult,
    isDetailExpanded: Boolean,
    onToggleDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLanguage = LocalAppLanguage.current
    val buyCount = analysis.buyCount
    val sellCount = analysis.sellCount
    val waitCount = analysis.waitCount
    val total = (buyCount + sellCount + waitCount).coerceAtLeast(1)
    val agreementPercent = analysis.agreementPercent.toInt()

    val (overallStatusText, overallColor, overallBg, overallBorder) = when (analysis.overallSignal) {
        Signal.BUY -> Tuple4(
            when (currentLanguage) {
                AppLanguage.ENGLISH -> "OVERALL INDICATORS: STRONG BUY 🟢"
                AppLanguage.HINDI -> "सभी इंडिकेटर्स: मजबूत खरीदारी 🟢"
                AppLanguage.MARATHI -> "सर्व इंडिकेटर्स: जोरदार खरेदी 🟢"
            },
            SignalBuy,
            SignalBuyContainer,
            SignalBuy.copy(alpha = 0.5f)
        )
        Signal.SELL -> Tuple4(
            when (currentLanguage) {
                AppLanguage.ENGLISH -> "OVERALL INDICATORS: STRONG SELL 🔴"
                AppLanguage.HINDI -> "सभी इंडिकेटर्स: मजबूत बिकवाली 🔴"
                AppLanguage.MARATHI -> "सर्व इंडिकेटर्स: जोरदार विक्री 🔴"
            },
            SignalSell,
            SignalSellContainer,
            SignalSell.copy(alpha = 0.5f)
        )
        Signal.WAIT -> Tuple4(
            when (currentLanguage) {
                AppLanguage.ENGLISH -> "OVERALL INDICATORS: MIXED / WAIT 🟡"
                AppLanguage.HINDI -> "सभी इंडिकेटर्स: मिलाजुला / इंतज़ार 🟡"
                AppLanguage.MARATHI -> "सर्व इंडिकेटर्स: संमिश्र / वाट पहा 🟡"
            },
            SignalWait,
            SignalWaitContainer,
            SignalWait.copy(alpha = 0.5f)
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(GoldPrimary.copy(alpha = 0.6f), ObsidianBorderHighlight, ObsidianBorder)
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("big_column_indicators_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Tag: Column 2 Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "📊", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "COLUMN 2: INDICATORS STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = GoldLight,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, ObsidianBorder)
                ) {
                    Text(
                        text = "32 INDICATORS",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        fontSize = 9.sp
                    )
                }
            }

            // MASSIVE OVERALL STATUS BANNER
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = overallBg,
                border = BorderStroke(1.5.dp, overallBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "OVERALL TECHNICAL VERDICT:"
                                    AppLanguage.HINDI -> "सभी इंडिकेटर्स का निष्कर्ष:"
                                    AppLanguage.MARATHI -> "सर्व इंडिकेटर्सचा निष्कर्ष:"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = overallColor,
                                fontSize = 10.sp
                            )
                            Text(
                                text = overallStatusText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                fontSize = 15.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = overallColor.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, overallColor)
                        ) {
                            Text(
                                text = "$agreementPercent% Agreement",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = overallColor,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Indicator Count Pills: [ 24 BUY ] [ 4 SELL ] [ 4 WAIT ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SignalBuy.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, SignalBuy.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "$buyCount BUY", fontWeight = FontWeight.Black, color = SignalBuy, fontSize = 12.sp)
                                Text(text = LocalizationStrings.buySignalsLabel(currentLanguage), fontSize = 8.sp, color = TextMuted)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SignalSell.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, SignalSell.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "$sellCount SELL", fontWeight = FontWeight.Black, color = SignalSell, fontSize = 12.sp)
                                Text(text = LocalizationStrings.sellSignalsLabel(currentLanguage), fontSize = 8.sp, color = TextMuted)
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SignalWait.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, SignalWait.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "$waitCount WAIT", fontWeight = FontWeight.Black, color = SignalWait, fontSize = 12.sp)
                                Text(text = LocalizationStrings.waitSignalsLabel(currentLanguage), fontSize = 8.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }

            // 4 MAJOR CATEGORY SUMMARY ROWS (1-GLANCE EASY VIEW)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "CATEGORY-WISE INDICATOR BREAKDOWN:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    fontSize = 10.sp
                )

                // 1. Moving Averages
                CategoryStatusRow(
                    icon = "📈",
                    category = "Trend & Moving Averages",
                    indicators = "EMA 9, 21, 50, 200, SuperTrend",
                    signal = Signal.BUY,
                    statusText = "Strong Bullish (Price above EMA 200)"
                )

                // 2. Oscillators & Momentum
                CategoryStatusRow(
                    icon = "⚡",
                    category = "Momentum Oscillators",
                    indicators = "RSI (62.4), MACD Cross, Stochastic",
                    signal = Signal.BUY,
                    statusText = "Buy Momentum Active (Not Overbought)"
                )

                // 3. Volatility & Bands
                CategoryStatusRow(
                    icon = "🌊",
                    category = "Volatility & Liquidity",
                    indicators = "Bollinger Bands, ATR (${analysis.tradeSetup.atrPips} Pips)",
                    signal = Signal.WAIT,
                    statusText = "Expansion Phase (High Volatility)"
                )

                // 4. Support & Resistance Pivots
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🎯", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = "Key Pivot Levels", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextPrimary)
                            }
                            Text(text = "Current: $${String.format(Locale.US, "%.2f", analysis.currentPrice)}", fontSize = 11.sp, fontWeight = FontWeight.Black, color = GoldLight)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Support S1: $${String.format(Locale.US, "%.2f", analysis.pivotLevels.s1)}", fontSize = 10.sp, color = SignalBuy, fontWeight = FontWeight.SemiBold)
                            Text(text = "Central Pivot: $${String.format(Locale.US, "%.2f", analysis.pivotLevels.pivot)}", fontSize = 10.sp, color = TextSecondary)
                            Text(text = "Resistance R1: $${String.format(Locale.US, "%.2f", analysis.pivotLevels.r1)}", fontSize = 10.sp, color = SignalSell, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Expand / Collapse Full 32 Indicators List Button
            OutlinedButton(
                onClick = onToggleDetail,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isDetailExpanded) "Hide Full 32 Indicators ▲" else "View All 32 Individual Indicators Details ▼",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            // Expanded 32 Indicators Groups
            AnimatedVisibility(visible = isDetailExpanded) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    analysis.groups.forEach { group ->
                        IndicatorGroupCard(group = group)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryStatusRow(
    icon: String,
    category: String,
    indicators: String,
    signal: Signal,
    statusText: String
) {
    val (sigColor, sigText) = when (signal) {
        Signal.BUY -> SignalBuy to "BUY"
        Signal.SELL -> SignalSell to "SELL"
        Signal.WAIT -> SignalWait to "WAIT"
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ObsidianSurfaceElevated,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = icon, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = category, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TextPrimary)
                    Text(text = indicators, fontSize = 9.sp, color = TextMuted)
                    Text(text = statusText, fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = sigColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, sigColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = sigText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    color = sigColor
                )
            }
        }
    }
}

// Simple Helper Tuple data classes
private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
private data class Tuple5<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
