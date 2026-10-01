package com.example.livegoldai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.localization.LocalizationStrings
import com.example.livegoldai.model.PastPredictionAuditItem
import com.example.livegoldai.model.PredictionOutcomeStatus
import com.example.livegoldai.model.Signal
import com.example.livegoldai.model.TimeframeAccuracyAudit
import com.example.livegoldai.theme.*
import java.util.Locale

@Composable
fun PredictionAccuracyAuditCard(
    audit: TimeframeAccuracyAudit?,
    selectedInterval: String,
    onOpenPredictionErrorAnalyzer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (audit == null) return

    val currentLanguage = LocalAppLanguage.current
    var expandedTradeId by remember { mutableStateOf<String?>(null) }
    var showAllRules by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_accuracy")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_audit"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("prediction_accuracy_audit_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    GoldPrimary.copy(alpha = 0.8f),
                    ObsidianBorderHighlight,
                    SignalBuy.copy(alpha = 0.4f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Title, Timeframe tag & AI Calibration badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GoldPrimary.copy(alpha = 0.15f))
                            .border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "AI Self-Correction Engine",
                            tint = GoldLight,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "REAL ACCURACY & LEARNING",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = GoldLight,
                                letterSpacing = 0.6.sp
                            )
                        }
                        Text(
                            text = when (currentLanguage) {
                                AppLanguage.ENGLISH -> "${audit.timeframe} Timeframe Accuracy & Self-Correction"
                                AppLanguage.HINDI -> "${audit.timeframe} टाइमफ्रेम का रिजल्ट और गलती सुधार"
                                AppLanguage.MARATHI -> "${audit.timeframe} टाइमफ्रेमचा निकाल आणि चूक सुधारणा"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SignalBuy.copy(alpha = 0.15f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(SignalBuy, SignalBuy.copy(alpha = 0.3f)))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(SignalBuy.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "${audit.timeframe} AUDITED",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Black,
                            color = SignalBuy
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Big 3-Metric Scoreboard (Win Rate %, Won/Lost Count, Net Pips PnL)
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = ObsidianSurfaceElevated,
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(listOf(ObsidianBorderHighlight, ObsidianBorder))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Win Rate Metric
                    Column {
                        Text(
                            text = "ACCURACY RATE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (audit.totalSignalsTested == 0) "--" else "${audit.winRatePercent}%",
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                            fontWeight = FontWeight.Black,
                            color = SignalBuy
                        )
                        Text(
                            text = when (currentLanguage) {
                                AppLanguage.ENGLISH -> "WIN RATE (${audit.timeframe})"
                                AppLanguage.HINDI -> "सफलता दर (${audit.timeframe})"
                                AppLanguage.MARATHI -> "यशाचा दर (${audit.timeframe})"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TextSecondary
                        )
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .width(1.dp)
                            .background(ObsidianBorder)
                    )

                    // Win / Loss Count
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SIGNALS AUDIT",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${audit.winCount}W",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = SignalBuy
                            )
                            Text(
                                text = " / ",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextMuted
                            )
                            Text(
                                text = "${audit.lossCount}L",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = SignalSell
                            )
                        }
                        Text(
                            text = "${audit.totalSignalsTested} checked • ${audit.activeCount} waiting",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TextSecondary
                        )
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .width(1.dp)
                            .background(ObsidianBorder)
                    )

                    // Net Pips PnL
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "NET PIPS GAINED",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${if (audit.netPipsGained >= 0) "+" else ""}${String.format(Locale.US, "%.1f", audit.netPipsGained)}",
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                            fontWeight = FontWeight.Black,
                            color = if (audit.netPipsGained >= 0) GoldLight else SignalSell
                        )
                        Text(
                            text = "Pips Munafa",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // AI Error Analyzer & Recalibrator Launcher Button
            Button(
                onClick = onOpenPredictionErrorAnalyzer,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("audit_open_error_analyzer_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ObsidianSurfaceElevated,
                    contentColor = GoldLight
                ),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.7f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🔬", fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (currentLanguage) {
                            AppLanguage.ENGLISH -> "ANALYZE WRONG PREDICTIONS & SEE AI FIXES ⚡"
                            AppLanguage.HINDI -> "गलतियां क्यों हुईं? असली लर्निंग सेंटर देखें ⚡"
                            AppLanguage.MARATHI -> "चुका का झाल्या? खरे लर्निंग सेंटर पहा ⚡"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Black,
                        color = GoldLight
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Last Prediction Spotlight Banner (Kitna Sahi / Galat Tha)
            audit.lastPredictionOutcome?.let { last ->
                val isWin = last.outcomeStatus == PredictionOutcomeStatus.TP1_HIT ||
                        last.outcomeStatus == PredictionOutcomeStatus.TP2_HIT ||
                        last.outcomeStatus == PredictionOutcomeStatus.ALL_TARGETS_HIT
                val statusColor = if (isWin) SignalBuy else if (last.outcomeStatus == PredictionOutcomeStatus.IN_PROFIT_ACTIVE) GoldPrimary else SignalSell
                val statusTitle = when (last.outcomeStatus) {
                    PredictionOutcomeStatus.TP2_HIT -> "✅ LAST PREDICTION: CORRECT, BIG MOVE (${last.pipsResult} Pips)"
                    PredictionOutcomeStatus.TP1_HIT -> "✅ LAST PREDICTION: CORRECT (${last.pipsResult} Pips)"
                    PredictionOutcomeStatus.ALL_TARGETS_HIT -> "✅ LAST PREDICTION: CORRECT (${last.pipsResult} Pips)"
                    PredictionOutcomeStatus.STOP_LOSS_HIT -> "❌ LAST PREDICTION: WRONG (${last.pipsResult} Pips)"
                    PredictionOutcomeStatus.IN_PROFIT_ACTIVE -> "⚪ LAST PREDICTION: NO RESULT / SIDEWAYS (${last.pipsResult} Pips)"
                    PredictionOutcomeStatus.PENDING_ENTRY -> "⏳ LAST PREDICTION: WAITING FOR EXPIRY"
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(statusColor.copy(alpha = 0.6f), Color.Transparent))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isWin) Icons.Default.CheckCircle else if (last.outcomeStatus == PredictionOutcomeStatus.IN_PROFIT_ACTIVE) Icons.Default.TrendingUp else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = statusTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = statusColor
                                )
                            }
                            Text(
                                text = last.timeAgo,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = TextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Root Cause: Kyu sahi hua ya kyu galat hua
                        val diagnosisTitle = when (currentLanguage) {
                            AppLanguage.ENGLISH -> "🔍 WHY IT HAPPENED (DIAGNOSIS): "
                            AppLanguage.HINDI -> "🔍 क्यों हुआ था (कारण): "
                            AppLanguage.MARATHI -> "🔍 का घडले होते (कारण): "
                        }
                        val translatedWhy = last.getWhyItHappened(currentLanguage)

                        Text(
                            text = "$diagnosisTitle $translatedWhy",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Self-Correction: Aage kya galti nahi honi chahiye
                        val lessonHeader = when (currentLanguage) {
                            AppLanguage.ENGLISH -> "AI SELF-CORRECTION (LESSON LEARNED):"
                            AppLanguage.HINDI -> "AI ने क्या सुधार किया (आगे से क्या नहीं होगा):"
                            AppLanguage.MARATHI -> "AI ने काय सुधारणा केली (पुढे काय होणार नाही):"
                        }
                        val translatedLesson = last.getLessonLearned(currentLanguage)

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianSurfaceElevated,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(16.dp).padding(top = 1.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = lessonHeader,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        fontWeight = FontWeight.Black,
                                        color = GoldLight
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = translatedLesson,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // AI Self-Correction Safeguard Rules (Aage Galti Na Hone Ke 4 Niyam)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurfaceElevated,
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(listOf(ObsidianBorderHighlight, ObsidianBorder))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAllRules = !showAllRules },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "AI SAFEGUARDS & ERROR PREVENTION (4 ACTIVE)"
                                    AppLanguage.HINDI -> "गलती रोकथाम के 4 AI सुरक्षा नियम"
                                    AppLanguage.MARATHI -> "चूक प्रतिबंधाचे 4 AI सुरक्षा नियम"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = GoldLight
                            )
                        }
                        Icon(
                            imageVector = if (showAllRules) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = showAllRules || true) {
                        Column(
                            modifier = Modifier.padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            audit.getAutoCorrectionRules(currentLanguage).forEach { rule ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 5.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(GoldPrimary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = rule,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Historical Verified Signals in this Timeframe (Pichhle Signals Ka History Log)
            Text(
                text = when (currentLanguage) {
                    AppLanguage.ENGLISH -> "${audit.timeframe} HISTORICAL SIGNALS AUDIT LOG"
                    AppLanguage.HINDI -> "${audit.timeframe} पिछले सिग्नल्स का ऑडिट लॉग"
                    AppLanguage.MARATHI -> "${audit.timeframe} मागील सिग्नल्सचा ऑडिट लॉग"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = TextMuted,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                audit.recentSignalAudits.forEach { item ->
                    val isExpanded = expandedTradeId == item.id
                    val isWin = item.outcomeStatus == PredictionOutcomeStatus.TP1_HIT ||
                            item.outcomeStatus == PredictionOutcomeStatus.TP2_HIT ||
                            item.outcomeStatus == PredictionOutcomeStatus.ALL_TARGETS_HIT
                    val itemColor = if (isWin) SignalBuy else if (item.outcomeStatus == PredictionOutcomeStatus.IN_PROFIT_ACTIVE) GoldPrimary else SignalSell
                    val signalBadge = if (item.signal == Signal.BUY) "BUY 🟢" else "SELL 🔴"

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurfaceElevated,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(
                                if (isExpanded) listOf(itemColor, ObsidianBorder)
                                else listOf(ObsidianBorderHighlight, ObsidianBorder)
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                expandedTradeId = if (isExpanded) null else item.id
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = (if (item.signal == Signal.BUY) SignalBuy else SignalSell).copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            text = signalBadge,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Black,
                                            color = if (item.signal == Signal.BUY) SignalBuy else SignalSell
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Entry: $${String.format(Locale.US, "%,.2f", item.entryPrice)}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = item.timeAgo,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = TextMuted
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = itemColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = when (item.outcomeStatus) {
                                                PredictionOutcomeStatus.TP2_HIT -> "CORRECT+"
                                                PredictionOutcomeStatus.TP1_HIT -> "CORRECT"
                                                PredictionOutcomeStatus.ALL_TARGETS_HIT -> "CORRECT"
                                                PredictionOutcomeStatus.STOP_LOSS_HIT -> "WRONG"
                                                PredictionOutcomeStatus.IN_PROFIT_ACTIVE -> "NO RESULT"
                                                PredictionOutcomeStatus.PENDING_ENTRY -> "WAITING"
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            fontWeight = FontWeight.Black,
                                            color = itemColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${if (item.pipsResult >= 0) "+" else ""}${item.pipsResult} Pips",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        color = itemColor
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Expanded Deep Diagnosis & Lesson
                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                ) {
                                    Divider(color = ObsidianBorder, thickness = 0.8.dp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Targets & SL overview
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Needed move: $${String.format(Locale.US, "%,.2f", item.target1Price)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = SignalBuy
                                        )
                                        Text(
                                            text = "Stop Loss: $${String.format(Locale.US, "%,.2f", item.stopLossPrice)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = SignalSell
                                        )
                                        Text(
                                            text = "Best point: $${String.format(Locale.US, "%,.2f", item.actualHighLowReached)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = GoldLight
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    val whyLabel = when (currentLanguage) {
                                        AppLanguage.ENGLISH -> "💡 Reason: "
                                        AppLanguage.HINDI -> "💡 कारण: "
                                        AppLanguage.MARATHI -> "💡 कारण: "
                                    }
                                    val lessonLabel = when (currentLanguage) {
                                        AppLanguage.ENGLISH -> "🛡️ Guardrail: "
                                        AppLanguage.HINDI -> "🛡️ सुरक्षा नियम: "
                                        AppLanguage.MARATHI -> "🛡️ सुरक्षा नियम: "
                                    }
                                    Text(
                                        text = "$whyLabel${item.getWhyItHappened(currentLanguage)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                                        color = TextPrimary
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "$lessonLabel${item.getLessonLearned(currentLanguage)}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                                        color = GoldLight
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
