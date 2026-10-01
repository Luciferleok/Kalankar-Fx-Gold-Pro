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

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_glow")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("kya_hoga_prediction_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = ObsidianSurfaceCard,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(
                    listOf(GoldPrimary, primaryColor.copy(alpha = pulseGlow), ObsidianBorderHighlight, GoldDark)
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                                    fontWeight = FontWeight.Black,
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
                                        text = "${prediction?.winProbabilityPercent ?: baseProb}% CONFIDENCE",
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
                                    AppLanguage.ENGLISH -> "AI Future Forecast & Scenario Breakdown"
                                    AppLanguage.HINDI -> "AI भविष्य का पूर्वानुमान और परिदृश्य संभावना"
                                    AppLanguage.MARATHI -> "AI भविष्यातील अंदाज आणि परिस्थिती विश्लेषण"
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
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
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
                                        AppLanguage.ENGLISH -> "🔮 AI MAIN VERDICT / FORECAST"
                                        AppLanguage.HINDI -> "🔮 AI मुख्य फैसला / भविष्यवाणी"
                                        AppLanguage.MARATHI -> "🔮 AI मुख्य निर्णय / भाकीत"
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
                                        AppLanguage.ENGLISH -> "🟢 GOLD HAS HIGH PROBABILITY OF +${tradeSetup.takeProfit2Pips.toInt()} PIPS BULLISH RALLY!"
                                        AppLanguage.HINDI -> "🟢 GOLD में +${tradeSetup.takeProfit2Pips.toInt()} PIPS की UPWARD RALLY की पूरी संभावना है!"
                                        AppLanguage.MARATHI -> "🟢 GOLD मध्ये +${tradeSetup.takeProfit2Pips.toInt()} PIPS ची जोरदार तेजी येण्याची दाट शक्यता आहे!"
                                    }
                                    isSell -> when (currentLanguage) {
                                        AppLanguage.ENGLISH -> "🔴 GOLD HAS HIGH PROBABILITY OF -${tradeSetup.takeProfit2Pips.toInt()} PIPS BEARISH DROP!"
                                        AppLanguage.HINDI -> "🔴 GOLD में -${tradeSetup.takeProfit2Pips.toInt()} PIPS के BEARISH DROP की संभावना है!"
                                        AppLanguage.MARATHI -> "🔴 GOLD मध्ये -${tradeSetup.takeProfit2Pips.toInt()} PIPS चा मोठा घसरणीचा अंदाज आहे!"
                                    }
                                    else -> when (currentLanguage) {
                                        AppLanguage.ENGLISH -> "🟡 GOLD IS CURRENTLY IN CONSOLIDATION / RANGE-BOUND MOVE!"
                                        AppLanguage.HINDI -> "🟡 GOLD में अभी CONSOLIDATION / RANGE-BOUND चाल रहेगी!"
                                        AppLanguage.MARATHI -> "🟡 GOLD मध्ये सध्या मर्यादित चढ-उतार / रेंज-बाऊंड हालचाल राहील!"
                                    }
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                lineHeight = 22.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = prediction?.actionHeading ?: (
                                    if (isBuy) "Buyers are accumulating heavily at support. Target $${String.format(Locale.US, "%.2f", tradeSetup.takeProfit2)}"
                                    else "Sellers are dominant under resistance. Target $${String.format(Locale.US, "%.2f", tradeSetup.takeProfit2)}"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGold,
                                fontWeight = FontWeight.SemiBold
                            )

                            val whatToDoStr = prediction?.getWhatToDo(currentLanguage) ?: ""
                            if (whatToDoStr.isNotBlank()) {
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

                    // 1.5 LIVE PREDICTION VALIDITY COUNTDOWN BANNER
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isValid) ObsidianSurfaceElevated else ObsidianSurfaceCard,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(
                                if (isValid) listOf(NeonGreen.copy(alpha = 0.8f), GoldPrimary.copy(alpha = 0.6f))
                                else listOf(SignalSell.copy(alpha = 0.6f), ObsidianBorder)
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_validity_timer_banner")
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
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (isValid) NeonGreen.copy(alpha = pulseGlow) else SignalSell)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when (currentLanguage) {
                                            AppLanguage.ENGLISH -> if (isValid) "PREDICTION VALIDITY (ACTIVE)" else "VALIDITY EXPIRED (EVALUATING)"
                                            AppLanguage.HINDI -> if (isValid) "प्रेडिक्शन वैधता (सक्रिय)" else "समय समाप्त (अगला चक्र शुरू)"
                                            AppLanguage.MARATHI -> if (isValid) "प्रेडिक्शन वैधता (सक्रिय)" else "वेळ संपली (पुढील चक्र सुरू)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = if (isValid) GoldLight else TextMuted,
                                        letterSpacing = 0.8.sp
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = (if (isValid) NeonGreen else SignalSell).copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, (if (isValid) NeonGreen else SignalSell).copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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

                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (progressFraction > 0.25f) NeonGreen else AmberWarning,
                                trackColor = ObsidianBorder
                            )

                            Spacer(modifier = Modifier.height(8.dp))

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
                                        AppLanguage.ENGLISH -> "Auto-Evaluated at TP1/SL"
                                        AppLanguage.HINDI -> "TP1 या SL हिट होने पर स्वतः समाप्त"
                                        AppLanguage.MARATHI -> "TP1 किंवा SL हिट झाल्यास पूर्ण"
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
                                                AppLanguage.ENGLISH -> "${audit.timeframe} SCORECARD (CORRECT vs INCORRECT)"
                                                AppLanguage.HINDI -> "${audit.timeframe} प्रेडिक्शन रिपोर्ट (कितने सही / कितने गलत)"
                                                AppLanguage.MARATHI -> "${audit.timeframe} निकाल (किती बरोबर / किती चूक)"
                                            },
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Black,
                                            color = GoldLight,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    Text(
                                        text = if (audit.totalSignalsTested == 0) "NO RESULTS YET" else "${audit.winRatePercent}% REAL WIN RATE (N=${audit.totalSignalsTested})",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Black,
                                        color = SignalBuy
                                    )
                                }

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
                                                    AppLanguage.ENGLISH -> "Correct / TP Hit"
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
                                                text = "${if (audit.netPipsGained >= 0) "+" else ""}${audit.netPipsGained}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Black,
                                                color = if (audit.netPipsGained >= 0) GoldLight else SignalSell
                                            )
                                            Text(
                                                text = "Net Pips P&L",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

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
                                                    AppLanguage.ENGLISH -> "AI SELF-CORRECTION GUARDS"
                                                    AppLanguage.HINDI -> "AI स्वचालित गलती सुधार (सक्रिय)"
                                                    AppLanguage.MARATHI -> "AI स्वयंचलित चूक सुधारणा (सक्रिय)"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Black,
                                                color = GoldLight,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = when (currentLanguage) {
                                                    AppLanguage.ENGLISH -> "Past errors prevented in this prediction"
                                                    AppLanguage.HINDI -> "पिछली गलतियों से सीखकर यह सिग्नल सुधारा गया"
                                                    AppLanguage.MARATHI -> "मागील चुकांमधून शिकून हा सिग्नल सुधारण्यात आला"
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
                                            text = "${corrections.size} SHIELDS 🛡️",
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
                                                AppLanguage.ENGLISH -> "ANALYZE PAST ERRORS & 6 AI FIXES ⚡"
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
                    analysis.multiAiConsensus?.let { consensus ->
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
                                        Text(text = "🤖", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = when (currentLanguage) {
                                                    AppLanguage.ENGLISH -> "RULE COUNCIL CONSENSUS"
                                                    AppLanguage.HINDI -> "5 नियमों की संयुक्त राय "
                                                    AppLanguage.MARATHI -> "5 नियमांची संयुक्त सहमती"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Black,
                                                color = GoldLight,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = "5 local rules • no AI call",
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
                                            text = "${consensus.agreeingModelsCount}/${consensus.totalModelsCount} AGREE",
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
                                        text = when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "POSSIBILITY METER (SCENARIO BREAKDOWN)"
                                            AppLanguage.HINDI -> "संभावना मीटर (किसकी कितनी संभावना है)"
                                            AppLanguage.MARATHI -> "शक्यता मीटर (परिस्थितीनुसार संभाव्यता)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = GoldLight,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            Text(
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> if (split.fromHistory) "Based on what really happened after past BUY/SELL calls (Learning Center)" else "Not enough history yet: these are today's pillar votes, not probabilities"
                                    AppLanguage.HINDI -> if (split.fromHistory) "पिछली BUY/SELL कॉल्स के बाद असल में क्या हुआ, उस पर आधारित" else "अभी इतिहास कम है: ये आज के पिलर वोट हैं, संभावना नहीं"
                                    AppLanguage.MARATHI -> if (split.fromHistory) "मागील BUY/SELL कॉल्सनंतर प्रत्यक्षात काय झाले त्यावर आधारित" else "अजून इतिहास कमी: ही आजची पिलर मते आहेत, शक्यता नाही"
                                },
                                fontSize = 9.sp,
                                color = TextMuted
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Bullish Probability Bar
                            ProbabilityItemRow(
                                title = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "🚀 Bullish Rally (Upward Move)"
                                    AppLanguage.HINDI -> "🚀 Bullish Rally (ऊपर की चाल)"
                                    AppLanguage.MARATHI -> "🚀 Bullish Rally (वर जाणारी तेजी)"
                                },
                                hindiDetail = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "Price likely to test higher resistance levels"
                                    AppLanguage.HINDI -> "Price ऊपर के लेवल्स टेस्ट करेगा"
                                    AppLanguage.MARATHI -> "किंमत वरील लेव्हल्स टेस्ट करेल"
                                },
                                percentage = bullishProb,
                                barColor = SignalBuy,
                                isPrimary = isBuy
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Sideways Probability Bar
                            ProbabilityItemRow(
                                title = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "⏸️ Sideways Range (Consolidation)"
                                    AppLanguage.HINDI -> "⏸️ Sideways Range (कोई बड़ी चाल नहीं)"
                                    AppLanguage.MARATHI -> "⏸️ Sideways Range (मर्यादित चढ-उतार)"
                                },
                                hindiDetail = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "Price moving within a tight band"
                                    AppLanguage.HINDI -> "Price एक तंग दायरे में घूमेगा"
                                    AppLanguage.MARATHI -> "किंमत एका मर्यादित कक्षेत राहील"
                                },
                                percentage = sidewaysProb,
                                barColor = SignalWait,
                                isPrimary = !isBuy && !isSell
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Bearish Probability Bar
                            ProbabilityItemRow(
                                title = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "🔻 Bearish Drop (Downward Move)"
                                    AppLanguage.HINDI -> "🔻 Bearish Drop (नीचे की चाल)"
                                    AppLanguage.MARATHI -> "🔻 Bearish Drop (खाली घसरण)"
                                },
                                hindiDetail = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "Price likely to break lower support"
                                    AppLanguage.HINDI -> "Price नीचे का सपोर्ट ब्रेक कर सकता है"
                                    AppLanguage.MARATHI -> "किंमत खालील सपोर्ट तोडू शकते"
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
                                        color = Color.White
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
                                        text = when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "Next Expected Target"
                                            AppLanguage.HINDI -> "अगला संभावित टारगेट"
                                            AppLanguage.MARATHI -> "पुढील अपेक्षित टार्गेट"
                                        },
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
                                            text = "Next Candle Forecast: ${candleInsight.nextCandleForecast}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

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
                                    text = when (currentLanguage) {
                                        AppLanguage.ENGLISH -> "🎯 RECOMMENDED ACTION PLAN"
                                        AppLanguage.HINDI -> "🎯 आपको क्या करना चाहिए (ACTION PLAN)"
                                        AppLanguage.MARATHI -> "🎯 तुम्ही काय करायला हवे (अ‍ॅक्शन प्लॅन)"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = GoldLight
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = primaryColor.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "R:R ${tradeSetup.riskRewardRatio}",
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
                                    label = "ENTRY ZONE",
                                    value = "$${String.format(Locale.US, "%.2f", tradeSetup.entryPrice)}",
                                    color = GoldLight,
                                    modifier = Modifier.weight(1f)
                                )
                                ActionItemPill(
                                    label = "STOP LOSS",
                                    value = "$${String.format(Locale.US, "%.2f", tradeSetup.stopLoss)}",
                                    color = SignalSell,
                                    modifier = Modifier.weight(1f)
                                )
                                ActionItemPill(
                                    label = "TARGET TP1",
                                    value = "$${String.format(Locale.US, "%.2f", tradeSetup.takeProfit1)}",
                                    color = SignalBuy,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 1-Tap Lot Calculator Button
                            OutlinedButton(
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
                                    text = when (currentLanguage) {
                                        AppLanguage.ENGLISH -> "Calculate Safe Lot Size For My Account"
                                        AppLanguage.HINDI -> "अपने अकाउंट के लिए सुरक्षित लॉट साइज निकालें"
                                        AppLanguage.MARATHI -> "आपल्या खात्यासाठी सुरक्षित लॉट साईझ कॅल्क्युलेट करा"
                                    },
                                    color = GoldLight,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

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
                                        AppLanguage.ENGLISH -> "When is this Prediction Cancelled? (Invalidation Rule)"
                                        AppLanguage.HINDI -> "कब यह प्रेडिक्शन कैंसिल होगी? (Invalidation Rule)"
                                        AppLanguage.MARATHI -> "हा अंदाज कधी रद्द मानला जाईल? (Invalidation Rule)"
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
                                    AppLanguage.ENGLISH -> "🔬 WHY THIS FORECAST? (3 MAIN REASONS)"
                                    AppLanguage.HINDI -> "🔬 ऐसा क्यों होगा? (3 मुख्य कारण)"
                                    AppLanguage.MARATHI -> "🔬 असे का घडेल? (3 मुख्य तांत्रिक कारणे)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = GoldLight
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            ReasonRow(
                                number = "1",
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "Smart Money: Strong wick rejection detected from institutional order block after liquidity grab."
                                    AppLanguage.HINDI -> "Smart Money: Liquidity grab के बाद institutional order block से strong wick rejection मिली है।"
                                    AppLanguage.MARATHI -> "Smart Money: Liquidity grab नंतर institutional order block कडून strong wick rejection मिळाले आहे."
                                }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            ReasonRow(
                                number = "2",
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "Trend Confluence: ${analysis.buyCount} out of ${analysis.totalGroups} major indicator groups confirm ${analysis.overallSignal.name} bias."
                                    AppLanguage.HINDI -> "Trend Confluence: ${analysis.totalGroups} में से ${analysis.buyCount} मुख्य इंडिकेटर ग्रुप्स ${analysis.overallSignal.name} संकेत दिखा रहे हैं।"
                                    AppLanguage.MARATHI -> "Trend Confluence: ${analysis.totalGroups} पैकी ${analysis.buyCount} प्रमुख इंडिकेटर गट ${analysis.overallSignal.name} संकेत दाखवत आहेत."
                                }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            ReasonRow(
                                number = "3",
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "Multi-Timeframe: Momentum expansion has triggered across M15 and H1 charts."
                                    AppLanguage.HINDI -> "Multi-Timeframe: M15 और H1 चार्ट्स पर मोमेंटम एक्सपेंशन शुरू हो चुका है।"
                                    AppLanguage.MARATHI -> "Multi-Timeframe: M15 आणि H1 चार्ट्सवर momentum expansion सुरू झाले आहे."
                                }
                            )
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
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = when (currentLanguage) {
                            AppLanguage.ENGLISH -> "I UNDERSTAND THE PROBABILITIES • GOT IT"
                            AppLanguage.HINDI -> "मुझे समझ आ गया • ठीक है"
                            AppLanguage.MARATHI -> "मला सर्व समजले • ठीक आहे"
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
                    color = if (isPrimary) Color.White else TextSecondary
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
                    text = "$percentage% CHANCE",
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
