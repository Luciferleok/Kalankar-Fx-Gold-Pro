package com.example.livegoldai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.localization.LocalizationStrings
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.PredictionOutcomeStatus
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*
import java.util.Locale

@Composable
fun KyaHogaPredictionDialog(
    analysis: GoldAnalysisResult,
    onOpenLotCalculator: (Double) -> Unit,
    onOpenPredictionErrorAnalyzer: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val currentLanguage = LocalAppLanguage.current
    val prediction = analysis.nextPrediction
    val candleInsight = analysis.candleInsight
    val tradeSetup = analysis.tradeSetup
    // V31-C: only an ACTIVE plan of a published BUY/SELL is actionable; everything else is a scenario or closed
    val actionable = com.example.livegoldai.data.PlanStamp.isActionable(tradeSetup) && analysis.overallSignal != Signal.WAIT
    val PT = com.example.livegoldai.data.PlanText
    var showResearch by remember { mutableStateOf(false) }

    // Probabilities calculation
    val baseProb = prediction?.winProbabilityPercent ?: tradeSetup.confidencePercent
    val isBuy = analysis.overallSignal == Signal.BUY
    val isSell = analysis.overallSignal == Signal.SELL

    // Real split: past outcomes from the Prediction Ledger, or live pillar votes until there is enough history
    val split = com.example.livegoldai.data.RealityEngine.outcomeSplit(analysis)
    val bullishProb: Int = split.bullish
    val sidewaysProb: Int = split.sideways
    val bearishProb: Int = split.bearish

    val primaryColor = when {
        isBuy -> SignalBuy
        isSell -> SignalSell
        else -> SignalWait
    }

    // V29E: no constant glow animation (it recomposed the modal every frame)

    // V29E: no per-second state here (the timer lives in ForecastTimer), so the modal does not recompose every second
    val nowMillis = System.currentTimeMillis()
    val validUntil = prediction?.validUntilTimestamp ?: 0L
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.92f).dp)
                .navigationBarsPadding()
                .testTag("kya_hoga_prediction_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = ObsidianSurfaceCard,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(
                    listOf(GoldPrimary, primaryColor.copy(alpha = 0.6f), ObsidianBorderHighlight, GoldDark)
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(primaryColor.copy(alpha = 0.2f))
                                .border(1.5.dp, primaryColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (currentLanguage) {
                                        AppLanguage.ENGLISH -> "WHAT HAPPENS NEXT?"
                                        AppLanguage.HINDI -> "अगला क्या होगा?"
                                        AppLanguage.MARATHI -> "पुढे काय घडणार?"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GoldLight,
                                    letterSpacing = 0.8.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = primaryColor.copy(alpha = 0.2f),
                                    border = CardDefaults.outlinedCardBorder().copy(
                                        brush = Brush.horizontalGradient(listOf(primaryColor, GoldLight))
                                    )
                                ) {
                                    Text(
                                        maxLines = 1,
                                        softWrap = false,
                                        text = if (analysis.overallSignal == Signal.WAIT) "NO CALL"
                                            else "${prediction?.winProbabilityPercent ?: baseProb}% " + when (analysis.presentation?.headlineKind) { "HISTORICAL" -> "hit-rate"; else -> "agr" },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = primaryColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "Rule-based forecast and scenarios • pillar agreement, not a probability"
                                    AppLanguage.HINDI -> "नियम-आधारित अनुमान • पिलर सहमति है, संभावना नहीं"
                                    AppLanguage.MARATHI -> "नियम-आधारित अंदाज • पिलर सहमती आहे, शक्यता नाही"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // V31-G: ONE authoritative decision banner at the top
                    val (bannerHead, bannerSub) = PT.banner(analysis, currentLanguage)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = primaryColor.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Text(text = bannerHead, color = primaryColor, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = bannerSub, color = if (actionable) TextPrimary else SignalWait, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // 1. Direct Forecast Big Answer Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ObsidianSurfaceElevated,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(
                                listOf(primaryColor.copy(alpha = 0.8f), ObsidianBorderHighlight)
                            )
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (currentLanguage) {
                                        AppLanguage.ENGLISH -> "MAIN VERDICT"
                                        AppLanguage.HINDI -> "मुख्य फैसला"
                                        AppLanguage.MARATHI -> "मुख्य निर्णय"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = GoldLight,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = prediction?.urgencyTag ?: "HIGH CONVICTION",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = primaryColor
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = when {
                                    isBuy -> when (currentLanguage) {
                                        AppLanguage.ENGLISH -> "Rules point UP • target 2 is ${tradeSetup.takeProfit2Pips.toInt()} pips away"
                                        AppLanguage.HINDI -> "नियम ऊपर की ओर हैं • टारगेट 2, ${tradeSetup.takeProfit2Pips.toInt()} पिप्स दूर"
                                        AppLanguage.MARATHI -> "नियम वरच्या दिशेने आहेत • टार्गेट 2, ${tradeSetup.takeProfit2Pips.toInt()} पिप्स दूर"
                                    }
                                    isSell -> when (currentLanguage) {
                                        AppLanguage.ENGLISH -> "Rules point DOWN • target 2 is ${tradeSetup.takeProfit2Pips.toInt()} pips away"
                                        AppLanguage.HINDI -> "नियम नीचे की ओर हैं • टारगेट 2, ${tradeSetup.takeProfit2Pips.toInt()} पिप्स दूर"
                                        AppLanguage.MARATHI -> "नियम खालच्या दिशेने आहेत • टार्गेट 2, ${tradeSetup.takeProfit2Pips.toInt()} पिप्स दूर"
                                    }
                                    else -> PT.waitHeadline(analysis, currentLanguage)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                lineHeight = 22.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            if (actionable) Text(
                                text = prediction?.actionHeading ?: (
                                    if (isBuy) "Buyers are accumulating heavily at support. Target $${String.format(Locale.US, "%.2f", tradeSetup.takeProfit2)}"
                                    else "Sellers are dominant under resistance. Target $${String.format(Locale.US, "%.2f", tradeSetup.takeProfit2)}"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGold,
                                fontWeight = FontWeight.SemiBold
                            )

                            // V33.1R: generated from tradeSetup (the cards' levels), never the engine's separately anchored text
                            val whatToDoStr = com.example.livegoldai.data.ResearchScenario.of(analysis, System.currentTimeMillis())
                                ?.let { com.example.livegoldai.data.ResearchScenario.steps(it, currentLanguage) } ?: ""
                            if (actionable && whatToDoStr.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = ObsidianBackground,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(text = "💡", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = whatToDoStr,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (analysis.overallSignal != Signal.WAIT) {
                    // V29E: one state-driven validity card. Title and colours come from ForecastState (never ACTIVE for
                    // research); only the small timer below ticks, so the heavy modal is not recomposed every second.
                    val fState = analysis.presentation?.forecastState ?: com.example.livegoldai.data.ForecastState.resolve(tradeSetup, analysis.overallSignal, null)
                    val FS = com.example.livegoldai.data.ForecastState
                    val stateColor = when (fState) { FS.APPROVED_ACTIVE -> SignalBuy; FS.STALE, FS.OFFLINE, FS.STOPPED -> SignalSell; else -> SignalWait }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ObsidianSurfaceCard,
                        border = BorderStroke(1.dp, stateColor.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_validity_timer_banner")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(text = FS.validityTitle(fState, currentLanguage), color = stateColor, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = FS.validitySubtitle(fState, currentLanguage), color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = FS.intervalLine(analysis.interval, tradeSetup.horizonMin.takeIf { it > 0 } ?: (prediction?.validityDurationMinutes ?: 0), currentLanguage),
                                color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            ForecastTimer(
                                expiresAtMs = analysis.presentation?.expiresAtMs?.takeIf { it > 0 } ?: validUntil,
                                outcome = analysis.presentation?.outcome ?: "",
                                language = currentLanguage
                            )
                        }
                    }

                    } else {
                        // V29C: a WAIT has no active validity: show the real next review instead of a green countdown
                        Surface(shape = RoundedCornerShape(16.dp), color = ObsidianSurfaceCard, border = BorderStroke(1.dp, SignalWait.copy(alpha = 0.45f)), modifier = Modifier.fillMaxWidth().testTag("dialog_no_active_signal")) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(text = PT.noActiveSignal(currentLanguage), color = SignalWait, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                // V29C.1: fixed review time of this decision's snapshot; "REVIEW DUE" once passed (never re-invented)
                                val reviewText = PT.reviewLine(analysis.presentation?.nextReviewAtMs ?: 0L, System.currentTimeMillis(), currentLanguage)
                                if (reviewText.isNotBlank()) Text(text = reviewText, color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                    // 6. Why This Prediction (3 Technical Reasons)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurfaceElevated,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(ObsidianBorderHighlight, ObsidianBorder))
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> PT.whyTitle(AppLanguage.ENGLISH)
                                    AppLanguage.HINDI -> PT.whyTitle(AppLanguage.HINDI)
                                    AppLanguage.MARATHI -> PT.whyTitle(AppLanguage.MARATHI)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = GoldLight
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // V29C.1: a WAIT is explained by its gate first; pillar votes live in Research Details
                            PT.whyReasons(analysis, currentLanguage).forEachIndexed { n, reason ->
                                if (n > 0) Spacer(modifier = Modifier.height(6.dp))
                                ReasonRow(number = "${n + 1}", text = reason)
                            }
                        }
                    }
                    // 1.6 PREDICTION TRACK RECORD: KITNE SAHI / KITNE GALAT
                    analysis.timeframeAudit?.let { audit ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ObsidianSurfaceCard,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.linearGradient(listOf(GoldPrimary.copy(alpha = 0.6f), ObsidianBorderHighlight))
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_scorecard_banner")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "📊", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = when (currentLanguage) {
                                                AppLanguage.ENGLISH -> "${audit.timeframe} " + PT.directionalTitle(AppLanguage.ENGLISH)
                                                AppLanguage.HINDI -> "${audit.timeframe} " + PT.directionalTitle(AppLanguage.HINDI)
                                                AppLanguage.MARATHI -> "${audit.timeframe} " + PT.directionalTitle(AppLanguage.MARATHI)
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Black,
                                            color = GoldLight,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    Text(
                                        text = if (audit.totalSignalsTested == 0) "NO RESULTS YET" else PT.hitRateBadge(audit.winRatePercent, audit.totalSignalsTested, currentLanguage),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Black,
                                        color = GoldLight
                                    )
                                }

                                // V29C.1: what the hit-rate means (window + outcome definition), and that it is not profit
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = PT.outcomeDefinition(com.example.livegoldai.data.TechnicalEngine.calculateValidityMinutes(analysis.interval), currentLanguage),
                                    color = TextSecondary, fontSize = 10.sp, lineHeight = 14.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = SignalBuy.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, SignalBuy.copy(alpha = 0.4f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "${audit.winCount} SAHI ✅",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Black,
                                                color = SignalBuy
                                            )
                                            Text(
                                                text = when (currentLanguage) {
                                                    AppLanguage.ENGLISH -> "Correct (target / direction)"
                                                    AppLanguage.HINDI -> "सही प्रेडिक्शन"
                                                    AppLanguage.MARATHI -> "अचूक अंदाज"
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = SignalSell.copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, SignalSell.copy(alpha = 0.4f)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                onDismiss()
                                                onOpenPredictionErrorAnalyzer()
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "${audit.lossCount} GALAT ❌",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Black,
                                                color = SignalSell
                                            )
                                            Text(
                                                text = when (currentLanguage) {
                                                    AppLanguage.ENGLISH -> "Analyze (Tap) 🔬"
                                                    AppLanguage.HINDI -> "जांचें (क्लिक करें) 🔬"
                                                    AppLanguage.MARATHI -> "तपासा (क्लिक) 🔬"
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                                color = SignalSell
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = ObsidianSurfaceElevated,
                                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = PT.pointsValue(audit.netPipsGained),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Black,
                                                color = if (audit.netPipsGained >= 0) GoldLight else SignalSell
                                            )
                                            Text(
                                                text = PT.pointsLabel(currentLanguage),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // V29C: historical evidence never reads as a proven edge
                    if (analysis.presentation?.provenEdge != true) Text(text = PT.noProvenEdge(currentLanguage), color = SignalWait, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
                    // V29C Section D: research details, collapsed by default
                    Text(
                        text = PT.researchToggle(showResearch, currentLanguage),
                        color = GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { showResearch = !showResearch }.background(ObsidianSurfaceElevated).padding(horizontal = 14.dp, vertical = 14.dp)
                    )
                    if (showResearch) {
                    // 1.7 AI AUTOMATIC ERROR SELF-CORRECTION (गलतियों से आगे का ऑटोमैटिक सुधार)
                    prediction?.appliedCorrections?.takeIf { it.isNotEmpty() }?.let { corrections ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ObsidianSurfaceCard,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.linearGradient(
                                    listOf(GoldPrimary.copy(alpha = 0.8f), NeonGreen.copy(alpha = 0.4f), ObsidianBorder)
                                )
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_ai_self_correction_section")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(GoldPrimary.copy(alpha = 0.15f))
                                                .border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Psychology,
                                                contentDescription = null,
                                                tint = GoldLight,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = when (currentLanguage) {
                                                    AppLanguage.ENGLISH -> "RULE GUARDS (NOT AI)"
                                                    AppLanguage.HINDI -> "नियम सुरक्षा (AI नहीं)"
                                                    AppLanguage.MARATHI -> "नियम सुरक्षा (AI नाही)"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Black,
                                                color = GoldLight,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = when (currentLanguage) {
                                                    AppLanguage.ENGLISH -> PT.safetyChecksNote(AppLanguage.ENGLISH)
                                                    AppLanguage.HINDI -> PT.safetyChecksNote(AppLanguage.HINDI)
                                                    AppLanguage.MARATHI -> PT.safetyChecksNote(AppLanguage.MARATHI)
                                                },
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = NeonGreen.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = PT.safetyChecksBadge(corrections.size, currentLanguage),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = NeonGreen
                                        )
                                    }
                                }

                                corrections.forEach { corr ->
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = ObsidianSurfaceElevated,
                                        border = BorderStroke(1.dp, ObsidianBorderHighlight),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "✓ ${corr.getTitle(currentLanguage)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Black,
                                                    color = GoldLight
                                                )
                                                Text(
                                                    text = corr.badgeTag,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                                    color = SignalBuy,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = corr.getDescription(currentLanguage),
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                                color = TextPrimary
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "🔍 ${corr.getErrorAddressed(currentLanguage)}",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                color = TextMuted
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onOpenPredictionErrorAnalyzer()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .testTag("dialog_open_error_analyzer_btn"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ObsidianSurfaceElevated,
                                        contentColor = GoldLight
                                    ),
                                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.7f))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "🔬", fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = when (currentLanguage) {
                                                AppLanguage.ENGLISH -> "ANALYZE PAST ERRORS"
                                                AppLanguage.HINDI -> "गलतियां क्यों हुईं? असली लर्निंग सेंटर देखें ⚡"
                                                AppLanguage.MARATHI -> "चुका का झाल्या? खरे लर्निंग सेंटर पहा ⚡"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Black,
                                            color = GoldLight
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 1.8 MULTI-AI ENSEMBLE CONSENSUS (Gemini + ChatGPT + Claude + DeepSeek + Perplexity)
                    analysis.multiAiConsensus?.takeIf { com.example.livegoldai.data.MultiAiConsensusEngine.consistent(it) }?.let { consensus ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ObsidianSurfaceCard,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.linearGradient(
                                    listOf(GoldPrimary.copy(alpha = 0.8f), primaryColor.copy(alpha = 0.5f), ObsidianBorder)
                                )
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_multi_ai_council_section")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "⚙", fontSize = 16.sp, color = GoldLight)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = when (currentLanguage) {
                                                    AppLanguage.ENGLISH -> PT.councilTitle(consensus.totalModelsCount, AppLanguage.ENGLISH)
                                                    AppLanguage.HINDI -> PT.councilTitle(consensus.totalModelsCount, AppLanguage.HINDI)
                                                    AppLanguage.MARATHI -> PT.councilTitle(consensus.totalModelsCount, AppLanguage.MARATHI)
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Black,
                                                color = GoldLight,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = PT.councilNote(currentLanguage),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = primaryColor.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = PT.councilBadge(consensus.agreeingModelsCount, consensus.totalModelsCount, consensus.consensusSignal, currentLanguage),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = primaryColor
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Quick Model Badges
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    consensus.modelInsights.forEach { model ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = ObsidianSurfaceElevated,
                                            border = BorderStroke(0.8.dp, ObsidianBorderHighlight),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(text = model.provider.iconEmoji, fontSize = 12.sp)
                                                Text(
                                                    text = model.provider.displayName.substringBefore(" "),
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                                val voteColor = if (model.signal == Signal.BUY) SignalBuy else if (model.signal == Signal.SELL) SignalSell else SignalWait
                                                Text(
                                                    text = model.signal.name,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                                    fontWeight = FontWeight.Black,
                                                    color = voteColor
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Consensus summary note
                                Text(
                                    text = consensus.getSummary(currentLanguage),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    // 2. Multi-Scenario Possibility Meter (Kiski Kitni Sambhavna Hai)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ObsidianSurfaceElevated,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(listOf(ObsidianBorderHighlight, ObsidianBorder))
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Analytics,
                                        contentDescription = null,
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = PT.evidenceTitle(split.fromHistory, currentLanguage),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = GoldLight,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            Text(
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> PT.evidenceNote(split.fromHistory, AppLanguage.ENGLISH)
                                    AppLanguage.HINDI -> PT.evidenceNote(split.fromHistory, AppLanguage.HINDI)
                                    AppLanguage.MARATHI -> PT.evidenceNote(split.fromHistory, AppLanguage.MARATHI)
                                },
                                fontSize = 9.sp,
                                color = TextMuted
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Bullish Probability Bar
                            ProbabilityItemRow(
                                title = PT.shareRow(Signal.BUY, split.fromHistory, currentLanguage, analysis.overallSignal),
                                hindiDetail = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> if (!split.fromHistory) "share of indicator pillars voting this way" else if (analysis.overallSignal == Signal.BUY) "past calls: went the called way" else "past calls: went against the call"
                                    AppLanguage.HINDI -> if (!split.fromHistory) "इस ओर वोट करने वाले इंडिकेटर पिलर" else if (analysis.overallSignal == Signal.BUY) "पिछली कॉल: बताई दिशा में गईं" else "पिछली कॉल: उल्टी गईं"
                                    AppLanguage.MARATHI -> if (!split.fromHistory) "या दिशेने मत देणारे इंडिकेटर पिलर" else if (analysis.overallSignal == Signal.BUY) "मागील कॉल: सांगितलेल्या दिशेने गेले" else "मागील कॉल: उलट गेले"
                                },
                                percentage = bullishProb,
                                barColor = SignalBuy,
                                isPrimary = isBuy
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Sideways Probability Bar
                            ProbabilityItemRow(
                                title = PT.shareRow(Signal.WAIT, split.fromHistory, currentLanguage, analysis.overallSignal),
                                hindiDetail = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> if (split.fromHistory) "past calls: no real move" else "share of pillars voting WAIT"
                                    AppLanguage.HINDI -> if (split.fromHistory) "पिछली कॉल: कोई असली चाल नहीं" else "WAIT वोट करने वाले पिलर"
                                    AppLanguage.MARATHI -> if (split.fromHistory) "मागील कॉल: खरी हालचाल नाही" else "WAIT मत देणारे पिलर"
                                },
                                percentage = sidewaysProb,
                                barColor = SignalWait,
                                isPrimary = !isBuy && !isSell
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Bearish Probability Bar
                            ProbabilityItemRow(
                                title = PT.shareRow(Signal.SELL, split.fromHistory, currentLanguage, analysis.overallSignal),
                                hindiDetail = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> if (!split.fromHistory) "share of indicator pillars voting this way" else if (analysis.overallSignal == Signal.SELL) "past calls: went the called way" else "past calls: went against the call"
                                    AppLanguage.HINDI -> if (!split.fromHistory) "इस ओर वोट करने वाले इंडिकेटर पिलर" else if (analysis.overallSignal == Signal.SELL) "पिछली कॉल: बताई दिशा में गईं" else "पिछली कॉल: उल्टी गईं"
                                    AppLanguage.MARATHI -> if (!split.fromHistory) "या दिशेने मत देणारे इंडिकेटर पिलर" else if (analysis.overallSignal == Signal.SELL) "मागील कॉल: सांगितलेल्या दिशेने गेले" else "मागील कॉल: उलट गेले"
                                },
                                percentage = bearishProb,
                                barColor = SignalSell,
                                isPrimary = isSell
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Composite Visual Stack Bar
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(bullishProb.toFloat().coerceAtLeast(1f))
                                            .fillMaxHeight()
                                            .background(SignalBuy)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(sidewaysProb.toFloat().coerceAtLeast(1f))
                                            .fillMaxHeight()
                                            .background(SignalWait)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(bearishProb.toFloat().coerceAtLeast(1f))
                                            .fillMaxHeight()
                                            .background(SignalSell)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "🟢 Buy $bullishProb%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SignalBuy
                                    )
                                    Text(
                                        text = "🟡 Range $sidewaysProb%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SignalWait
                                    )
                                    Text(
                                        text = "🔴 Sell $bearishProb%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SignalSell
                                    )
                                }
                            }
                        }
                    }

                    // 3. Current Live Status vs Future Target Glance
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ObsidianSurfaceElevated,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(ObsidianBorderHighlight, ObsidianBorder))
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "🔴 LIVE STATUS VS NEXT EXPECTED TARGET"
                                    AppLanguage.HINDI -> "🔴 अभी क्या चल रहा है vs अगला टारगेट"
                                    AppLanguage.MARATHI -> "🔴 सध्या काय स्थिती आहे विरुद्ध पुढील टार्गेट"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = GoldLight
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "Current Live Price"
                                            AppLanguage.HINDI -> "अभी का लाइव भाव"
                                            AppLanguage.MARATHI -> "सध्याचा लाईव्ह भाव"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                    Text(
                                        text = "$${String.format(Locale.US, "%.2f", analysis.currentPrice)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${if (analysis.changeAmount >= 0) "+" else ""}${String.format(Locale.US, "%.2f", analysis.changeAmount)} (${String.format(Locale.US, "%.2f", analysis.changePercent)}%)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = if (analysis.changeAmount >= 0) SignalBuy else SignalSell,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(GoldPrimary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingFlat,
                                        contentDescription = null,
                                        tint = GoldLight,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = PT.targetLabel(tradeSetup, currentLanguage),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                    Text(
                                        text = "$${String.format(Locale.US, "%.2f", tradeSetup.takeProfit1)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = primaryColor
                                    )
                                    Text(
                                        text = "TP2: $${String.format(Locale.US, "%.2f", tradeSetup.takeProfit2)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = GoldLight,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (candleInsight != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ObsidianBackground,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "🕯️", fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = PT.nextCandleLabel(currentLanguage) + candleInsight.nextCandleForecast,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    }
                    if (actionable || showResearch) {
                        if (!actionable) Text(text = PT.researchScenarioTitle(currentLanguage), color = SignalWait, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp, modifier = Modifier.padding(horizontal = 4.dp))
                        if (actionable || analysis.presentation?.compatibleWithModel != false) {
                    // 4. Action Playbook (Entry, SL, TP)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ObsidianSurfaceElevated,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(primaryColor, GoldPrimary))
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = PT.planTitle(tradeSetup, currentLanguage),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = GoldLight
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = primaryColor.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "risk:reward ${tradeSetup.riskRewardRatio}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = primaryColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionItemPill(
                                    label = if (actionable) "ENTRY ZONE" else "HYP. ENTRY",
                                    value = "$${String.format(Locale.US, "%.2f", tradeSetup.entryPrice)}",
                                    color = GoldLight,
                                    modifier = Modifier.weight(1f)
                                )
                                ActionItemPill(
                                    label = if (actionable) "STOP LOSS" else "HYP. INVALIDATION",
                                    value = "$${String.format(Locale.US, "%.2f", tradeSetup.stopLoss)}",
                                    color = SignalSell,
                                    modifier = Modifier.weight(1f)
                                )
                                ActionItemPill(
                                    label = if (actionable) "TARGET TP1" else "HYP. PROJECTED",
                                    value = "$${String.format(Locale.US, "%.2f", tradeSetup.takeProfit1)}",
                                    color = SignalBuy,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 1-Tap Lot Calculator Button (V31-C: only for an ACTIVE plan)
                            if (actionable) OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onOpenLotCalculator(tradeSetup.stopLossPips)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = GoldPrimary.copy(alpha = 0.15f)
                                ),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = Brush.horizontalGradient(listOf(GoldPrimary, GoldLight))
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = null,
                                    tint = GoldLight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = com.example.livegoldai.data.HonestText.lotButton(com.example.livegoldai.data.PlanStamp.isActionable(analysis.tradeSetup), currentLanguage),
                                    color = GoldLight,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // V31-E: where every price above came from (same snapshot)
                    Text(
                        text = PT.provenance(tradeSetup, currentLanguage),
                        color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )

                        } else Text(text = PT.levelsSuppressed(currentLanguage), color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp))
                        // V29C: invalidation only for an active trade, or for a valid research scenario
                        if (actionable || analysis.presentation?.scenarioAvailable == true) {
                    // 5. Invalidation Warning Alert
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SignalSell.copy(alpha = 0.12f),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(SignalSell, ObsidianBorder))
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = SignalSell,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = when (currentLanguage) {
                                        AppLanguage.ENGLISH -> if (actionable) "When is this Prediction Cancelled? (Invalidation Rule)" else PT.scenarioInvalidationTitle(AppLanguage.ENGLISH)
                                        AppLanguage.HINDI -> if (actionable) "कब यह प्रेडिक्शन कैंसिल होगी? (Invalidation Rule)" else PT.scenarioInvalidationTitle(AppLanguage.HINDI)
                                        AppLanguage.MARATHI -> if (actionable) "हा अंदाज कधी रद्द मानला जाईल? (Invalidation Rule)" else PT.scenarioInvalidationTitle(AppLanguage.MARATHI)
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = SignalSell
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = when (currentLanguage) {
                                        AppLanguage.ENGLISH -> "If a candle closes beyond $${String.format(Locale.US, "%.2f", tradeSetup.stopLoss)}, this trade setup is immediately invalid. Always maintain Stop Loss."
                                        AppLanguage.HINDI -> "अगर कैंडल $${String.format(Locale.US, "%.2f", tradeSetup.stopLoss)} के पार क्लोज हो जाए, तो सेटअप तुरंत इनवैलिड हो जाएगा। स्टॉप लॉस लगाना अनिवार्य है।"
                                        AppLanguage.MARATHI -> "जर कँडल $${String.format(Locale.US, "%.2f", tradeSetup.stopLoss)} च्या पलीकडे बंद झाली, तर हा सेटअप तत्काळ रद्द होईल. स्टॉप लॉस लावणे अत्यंत आवश्यक आहे."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action: Got It / Close
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("close_prediction_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = OnAccent
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = when (currentLanguage) {
                            AppLanguage.ENGLISH -> "GOT IT"
                            AppLanguage.HINDI -> "ठीक है"
                            AppLanguage.MARATHI -> "ठीक आहे"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ProbabilityItemRow(
    title: String,
    hindiDetail: String,
    percentage: Int,
    barColor: Color,
    isPrimary: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isPrimary) FontWeight.Black else FontWeight.Bold,
                    color = if (isPrimary) TextPrimary else TextSecondary
                )
                Text(
                    text = hindiDetail,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = barColor.copy(alpha = if (isPrimary) 0.25f else 0.15f),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(barColor, barColor))
                )
            ) {
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = barColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = { (percentage / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = ObsidianBorder
        )
    }
}

@Composable
private fun ActionItemPill(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = ObsidianBackground,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0.5f), ObsidianBorder))
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}

@Composable
private fun ReasonRow(
    number: String,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(GoldPrimary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = GoldLight
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp,
            color = TextSecondary,
            lineHeight = 14.sp
        )
    }
}

/** V29E: the only per-second state of the modal: remaining time to a FIXED expiry, then the real outcome status. */
@Composable
private fun ForecastTimer(expiresAtMs: Long, outcome: String, language: AppLanguage) {
    if (expiresAtMs <= 0L) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAtMs) {
        while (isActive && now < expiresAtMs) { delay(1000); now = System.currentTimeMillis() }
    }
    val PT = com.example.livegoldai.data.PlanText
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = if (now < expiresAtMs) PT.remainingLine(expiresAtMs, now, language) else com.example.livegoldai.data.ForecastState.outcomeLine(outcome, language),
        color = if (now < expiresAtMs) TextSecondary else SignalWait, fontSize = 11.sp, fontWeight = FontWeight.SemiBold
    )
}
