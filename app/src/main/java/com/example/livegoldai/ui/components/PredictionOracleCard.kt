package com.example.livegoldai.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.R
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.model.NextPredictionPlaybook
import com.example.livegoldai.model.PredictionOutcomeStatus
import com.example.livegoldai.model.Signal
import com.example.livegoldai.model.TimeframeAccuracyAudit
import com.example.livegoldai.theme.*

@Composable
fun PredictionOracleCard(
    prediction: NextPredictionPlaybook,
    onOpenCalculator: () -> Unit = {},
    logoRes: Int = R.drawable.kalankar_logo_mark,
    onLogoClick: () -> Unit = {},
    timeframeAudit: TimeframeAccuracyAudit? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentLang = LocalAppLanguage.current
    var showEnglishDetails by remember { mutableStateOf(false) }
    var selectedTierIndex by remember { mutableIntStateOf(0) }
    var isVoicePlaying by remember { mutableStateOf(false) }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(prediction.validUntilTimestamp) {
        while (isActive) {
            delay(1000)
            nowMillis = System.currentTimeMillis()
        }
    }
    val remainingMillis = (prediction.validUntilTimestamp - nowMillis).coerceAtLeast(0L)
    val isValid = remainingMillis > 0L || prediction.validUntilTimestamp == 0L
    val remTotalSeconds = remainingMillis / 1000L
    val remHours = remTotalSeconds / 3600L
    val remMinutes = (remTotalSeconds % 3600L) / 60L
    val remSeconds = remTotalSeconds % 60L

    val countdownStr = when {
        remHours > 0 -> "${remHours}h ${remMinutes}m ${remSeconds}s"
        remMinutes > 0 -> "${remMinutes}m ${remSeconds}s"
        else -> "${remSeconds}s"
    }
    val totalValidityMillis = (prediction.validityDurationMinutes * 60_000L).coerceAtLeast(1L)
    val progressFraction = (remainingMillis.toFloat() / totalValidityMillis.toFloat()).coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val signalColor = when (prediction.verdict) {
        Signal.BUY -> NeonGreen
        Signal.SELL -> NeonRed
        Signal.WAIT -> AmberWarning
    }

    val containerBg = when (prediction.verdict) {
        Signal.BUY -> Color(0xFF091610)
        Signal.SELL -> Color(0xFF19090D)
        Signal.WAIT -> Color(0xFF14120B)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("prediction_oracle_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    GoldPrimary.copy(alpha = 0.9f),
                    signalColor.copy(alpha = 0.7f),
                    ObsidianBorderHighlight,
                    GoldDark
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row: Luxury Logo, Title & Win Confluence Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF080A0F))
                            .border(
                                1.5.dp,
                                Brush.sweepGradient(listOf(GoldLight, GoldPrimary, Color(0xFFD4AF37), GoldLight)),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onLogoClick() }
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = logoRes),
                            contentDescription = "Kalankar Royal Gold Emblem",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(signalColor.copy(alpha = pulseAlpha))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AI PROFIT ORACLE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = GoldLight,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = when (currentLang) {
                                AppLanguage.ENGLISH -> "INSTITUTIONAL PREDICTION & PROTOCOL"
                                AppLanguage.HINDI -> "संस्थागत प्रेडिक्शन एवं प्रोटोकॉल"
                                AppLanguage.MARATHI -> "संस्थागत अंदाज आणि प्रोटोकॉल"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                    }
                }

                // Confluence Win Probability Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = signalColor.copy(alpha = 0.18f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(signalColor, GoldPrimary))
                    )
                ) {
                    Text(
                        text = when (currentLang) {
                            AppLanguage.ENGLISH -> "${prediction.winProbabilityPercent}% CONFIDENCE"
                            AppLanguage.HINDI -> "${prediction.winProbabilityPercent}% भरोसा"
                            AppLanguage.MARATHI -> "${prediction.winProbabilityPercent}% विश्वास"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = signalColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // ⏳ LIVE PREDICTION VALIDITY COUNTDOWN BANNER
            Spacer(modifier = Modifier.height(12.dp))
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
                    .testTag("prediction_validity_timer_banner")
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
                                    .background(if (isValid) NeonGreen.copy(alpha = pulseAlpha) else SignalSell)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (currentLang) {
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

                        // Live Ticking Countdown Pill
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = (if (isValid) NeonGreen else SignalSell).copy(alpha = 0.16f),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.linearGradient(
                                    listOf(if (isValid) NeonGreen else SignalSell, Color.Transparent)
                                )
                            )
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

                    // Animated Progress Bar of Validity Remaining
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
                            text = "⏱️ ${prediction.getValidityFormatted(currentLang)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = when (currentLang) {
                                AppLanguage.ENGLISH -> "Target 1 or SL hits = Auto Closed"
                                AppLanguage.HINDI -> "TP1 या SL हिट होने पर स्वतः समाप्त"
                                AppLanguage.MARATHI -> "TP1 किंवा SL हिट झाल्यास पूर्ण"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TextMuted
                        )
                    }

                    // Invalidation Rule line
                    val ruleText = prediction.getInvalidationRule(currentLang)
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

            // 📊 PREDICTION TRACK RECORD: KITNE SAHI / KITNE GALAT
            timeframeAudit?.let { audit ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianSurfaceElevated,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(GoldPrimary.copy(alpha = 0.5f), ObsidianBorder))
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("prediction_scorecard_banner")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Assessment,
                                    contentDescription = null,
                                    tint = GoldLight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (currentLang) {
                                        AppLanguage.ENGLISH -> "${audit.timeframe} PREDICTION TRACK RECORD"
                                        AppLanguage.HINDI -> "${audit.timeframe} प्रेडिक्शन रिपोर्ट (कितने सही / कितने गलत)"
                                        AppLanguage.MARATHI -> "${audit.timeframe} अंदाज निकाल (किती बरोबर / किती चूक)"
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

                        // 3 Scoreboard metrics: Sahi (Won), Galat (Lost), Net Pips P&L
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Sahi (Won)
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
                                        text = when (currentLang) {
                                            AppLanguage.ENGLISH -> "Correct / TP Hit"
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
                                shape = RoundedCornerShape(10.dp),
                                color = SignalSell.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, SignalSell.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f)
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
                                        text = when (currentLang) {
                                            AppLanguage.ENGLISH -> "Failed / SL Hit"
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
                                shape = RoundedCornerShape(10.dp),
                                color = ObsidianSurfaceCard,
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

            // 🧠 AI AUTOMATIC ERROR SELF-CORRECTION (गलतियों से आगे का ऑटोमैटिक सुधार)
            if (prediction.appliedCorrections.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                var showAllCorrections by remember { mutableStateOf(false) }

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
                        .testTag("ai_self_correction_section")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAllCorrections = !showAllCorrections },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(GoldPrimary.copy(alpha = 0.15f))
                                        .border(1.dp, GoldPrimary.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = null,
                                        tint = GoldLight,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = when (currentLang) {
                                                AppLanguage.ENGLISH -> "AI AUTO-CORRECTION ACTIVE"
                                                AppLanguage.HINDI -> "AI स्वचालित गलती सुधार (सक्रिय)"
                                                AppLanguage.MARATHI -> "AI स्वयंचलित चूक सुधारणा (सक्रिय)"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = GoldLight,
                                            letterSpacing = 0.6.sp
                                        )
                                    }
                                    Text(
                                        text = when (currentLang) {
                                            AppLanguage.ENGLISH -> "Learned from past mistakes • Auto-calibrated for next win"
                                            AppLanguage.HINDI -> "पिछली गलतियों से सीखकर यह नया प्रेडिक्शन सुधारा गया"
                                            AppLanguage.MARATHI -> "मागील चुकांमधून शिकून हा पुढील अंदाज सुधारण्यात आला"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
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
                                        text = "${prediction.appliedCorrections.size} GUARDS 🛡️",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = NeonGreen
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = if (showAllCorrections) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Display the first or all applied corrections
                        val displayedCorrections = if (showAllCorrections) prediction.appliedCorrections else prediction.appliedCorrections.take(1)
                        displayedCorrections.forEach { corr ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
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
                                            text = "✅ ${corr.getTitle(currentLang)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = GoldLight
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = SignalBuy.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = corr.badgeTag,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = SignalBuy
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = corr.getDescription(currentLang),
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "🔍 ${corr.getErrorAddressed(currentLang)}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Verdict Banner
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = signalColor.copy(alpha = 0.16f),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(
                        listOf(signalColor.copy(alpha = 0.8f), GoldPrimary.copy(alpha = 0.5f))
                    )
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = prediction.urgencyTag,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = signalColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = prediction.actionHeading,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = signalColor,
                            modifier = Modifier.padding(start = 12.dp)
                        ) {
                            Text(
                                text = prediction.verdict.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = if (prediction.verdict == Signal.BUY) OnAccent else TextPrimary,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Exact Trade & Order Execution Tags
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ObsidianBackground.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = GoldLight,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = when (currentLang) {
                                            AppLanguage.ENGLISH -> "TRADE TYPE"
                                            AppLanguage.HINDI -> "ट्रेड प्रकार"
                                            AppLanguage.MARATHI -> "ट्रेड प्रकार"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = TextMuted
                                    )
                                    Text(
                                        prediction.tradeType,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldLight
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ObsidianBackground.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.FlashOn,
                                    contentDescription = null,
                                    tint = signalColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = when (currentLang) {
                                            AppLanguage.ENGLISH -> "ORDER TYPE"
                                            AppLanguage.HINDI -> "ऑर्डर प्रकार"
                                            AppLanguage.MARATHI -> "ऑर्डर प्रकार"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = TextMuted
                                    )
                                    Text(
                                        prediction.orderExecutionType,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = signalColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. Entry & Stop Loss (SEAL) Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Entry Box
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianSurfaceCard,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.verticalGradient(listOf(ObsidianBorderHighlight, ObsidianBorder))
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "WHERE TO ENTER"
                                    AppLanguage.HINDI -> "कहाँ एंट्री करें"
                                    AppLanguage.MARATHI -> "कुठे एंट्री करावी"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = prediction.recommendedEntryZone,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                        Text(
                            text = when (currentLang) {
                                AppLanguage.ENGLISH -> "Pullback / Limit Zone"
                                AppLanguage.HINDI -> "पुलबैक / लिमिट ज़ोन"
                                AppLanguage.MARATHI -> "पुलबॅक / लिमिट झोन"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = TextGold
                        )
                    }
                }

                // Stop Loss ("SEAL") Box
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF220E12),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.verticalGradient(listOf(NeonRed, ObsidianBorder))
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = NeonRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "STOP LOSS (SEAL)"
                                    AppLanguage.HINDI -> "STOP LOSS (सील)"
                                    AppLanguage.MARATHI -> "STOP LOSS (सील)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = NeonRed.copy(alpha = 0.9f),
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = prediction.stopLossLevel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Black,
                            color = NeonRed
                        )
                        Text(
                            text = when (currentLang) {
                                AppLanguage.ENGLISH -> "Capital Protection Line"
                                AppLanguage.HINDI -> "पूंजी सुरक्षा स्तर"
                                AppLanguage.MARATHI -> "भांडवल संरक्षण स्तर"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = NeonRed.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // SL (SEAL) RATIONALE BANNER - Dynamic Language
            val slRationale = prediction.getStopLossRationale(currentLang)
            val slPrefix = when (currentLang) {
                AppLanguage.ENGLISH -> "🔒 SEAL REASON: "
                AppLanguage.HINDI -> "🔒 सील (SL) का कारण: "
                AppLanguage.MARATHI -> "🔒 सील (SL) चे कारण: "
            }
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1B1115),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(NeonRed.copy(alpha = 0.5f), ObsidianBorder))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        tint = NeonRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$slPrefix$slRationale",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = Color(0xFFFFCDD2),
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Take Profit (TP1, TP2, TP3) Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // TP1
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceCard,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.verticalGradient(listOf(NeonGreen.copy(alpha = 0.6f), ObsidianBorder))
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("TP 1 (50%)", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(prediction.takeProfit1, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = NeonGreen)
                        Text(
                            text = when (currentLang) {
                                AppLanguage.ENGLISH -> "Book half & SL to Entry"
                                AppLanguage.HINDI -> "50% बुक करें व SL एंट्री पर"
                                AppLanguage.MARATHI -> "50% बुक करा व SL एंट्रीवर"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = TextMuted
                        )
                    }
                }

                // TP2
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceCard,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.verticalGradient(listOf(GoldPrimary.copy(alpha = 0.6f), ObsidianBorder))
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("TP 2 (30%)", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(prediction.takeProfit2, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = GoldPrimary)
                        Text(
                            text = when (currentLang) {
                                AppLanguage.ENGLISH -> "Trail remainder"
                                AppLanguage.HINDI -> "30% मुनाफा लॉक करें"
                                AppLanguage.MARATHI -> "30% नफा लॉक करा"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = TextMuted
                        )
                    }
                }

                // TP3 (Runner)
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceCard,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.verticalGradient(listOf(Color(0xFF80D8FF).copy(alpha = 0.6f), ObsidianBorder))
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RocketLaunch, contentDescription = null, tint = Color(0xFF80D8FF), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("TP 3 (20%)", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(if (prediction.takeProfit3.isNotEmpty()) prediction.takeProfit3 else "Runner", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFF80D8FF))
                        Text(
                            text = when (currentLang) {
                                AppLanguage.ENGLISH -> "Runner target"
                                AppLanguage.HINDI -> "बड़ा रनर टारगेट"
                                AppLanguage.MARATHI -> "मोठे रनर टार्गेट"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Step-by-Step Action Box ("EXACT ENTRY & NO-TRADE PROTOCOL")
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF131722),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.horizontalGradient(listOf(GoldPrimary.copy(alpha = 0.5f), ObsidianBorder))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Campaign,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "ENTRY & AVOID PROTOCOL"
                                    AppLanguage.HINDI -> "एंट्री एवं नो-ट्रेड नियम"
                                    AppLanguage.MARATHI -> "एंट्री आणि नो-ट्रेड नियम"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = GoldLight
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    isVoicePlaying = !isVoicePlaying
                                    val msg = when (currentLang) {
                                        AppLanguage.ENGLISH -> if (isVoicePlaying) "🔊 Playing Trade Instructions..." else "🔇 Audio Stopped"
                                        AppLanguage.HINDI -> if (isVoicePlaying) "🔊 हिंदी ट्रेड निर्देश चल रहे हैं..." else "🔇 ऑडियो बंद"
                                        AppLanguage.MARATHI -> if (isVoicePlaying) "🔊 मराठी ट्रेड सूचना सुरू आहेत..." else "🔇 ऑडिओ बंद"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isVoicePlaying) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                                    contentDescription = "Voice Guide",
                                    tint = if (isVoicePlaying) NeonGreen else GoldPrimary
                                )
                            }

                            TextButton(
                                onClick = { showEnglishDetails = !showEnglishDetails },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (showEnglishDetails) {
                                        when (currentLang) {
                                            AppLanguage.ENGLISH -> "Brief"
                                            AppLanguage.HINDI -> "संक्षिप्त"
                                            AppLanguage.MARATHI -> "संक्षिप्त"
                                        }
                                    } else {
                                        when (currentLang) {
                                            AppLanguage.ENGLISH -> "Full Plan"
                                            AppLanguage.HINDI -> "विस्तृत योजना"
                                            AppLanguage.MARATHI -> "सविस्तर योजना"
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. KAHAN ENTRY KAREIN
                    val whereToEnterText = prediction.getWhereToEnter(currentLang)
                    if (whereToEnterText.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF0F2618),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(listOf(NeonGreen.copy(alpha = 0.8f), GoldPrimary.copy(alpha = 0.4f)))
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = NeonGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = when (currentLang) {
                                            AppLanguage.ENGLISH -> "WHERE TO ENTER (EXACT TRIGGER)"
                                            AppLanguage.HINDI -> "कहाँ एंट्री करें (सटीक ट्रिगर)"
                                            AppLanguage.MARATHI -> "कुठे एंट्री करावी (अचूक ट्रिगर)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = NeonGreen
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = whereToEnterText.removePrefix("✅ KAHAN ENTRY KAREIN: "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 17.sp,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 2. KAHAN BILKUL ENTRY NAHI KARNI
                    val whereToAvoidText = prediction.getWhereToAvoid(currentLang)
                    if (whereToAvoidText.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF280F14),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(listOf(NeonRed.copy(alpha = 0.8f), Color(0xFF5D121F)))
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = NeonRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = when (currentLang) {
                                            AppLanguage.ENGLISH -> "WHERE NOT TO ENTER (TRAP ZONE)"
                                            AppLanguage.HINDI -> "कहाँ बिल्कुल एंट्री न करें (ट्रैप ज़ोन)"
                                            AppLanguage.MARATHI -> "कुठे अजिबात एंट्री करू नये (ट्रॅप झोन)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = NeonRed
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = whereToAvoidText.removePrefix("❌ KAHAN BILKUL ENTRY NAHI KARNI: "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFFFCDD2),
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 17.sp,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Optional Full Strategy Guide
                    if (showEnglishDetails) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianBackground.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = when (currentLang) {
                                        AppLanguage.ENGLISH -> "INSTITUTIONAL STRATEGY OVERVIEW"
                                        AppLanguage.HINDI -> "संस्थागत रणनीति का विवरण"
                                        AppLanguage.MARATHI -> "संस्थागत रणनीतीचा तपशील"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldLight
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = prediction.getWhatToDo(currentLang),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    lineHeight = 18.sp,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Voice Audio Bubble
                    val currentAudio = prediction.getAudioAdvice(currentLang)
                    if (isVoicePlaying && currentAudio.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SignalBuyBg,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(listOf(NeonGreen, GoldPrimary))
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentAudio,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = ObsidianBorder)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⏱️ Horizon: ${prediction.timeHorizon}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "🛡️ ${prediction.riskManagementRule}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Interactive Account Balance Matrix (Safe Lot Size & Dollar Risk vs Reward)
            if (prediction.accountTierMatrix.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianSurfaceCard,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.verticalGradient(listOf(ObsidianBorderHighlight, ObsidianBorder))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (currentLang) {
                                        AppLanguage.ENGLISH -> "ACCOUNT EQUITY & EXACT LOT RECOMMENDATION"
                                        AppLanguage.HINDI -> "खाता पूंजी एवं सटीक लॉट साइज़ मार्गदर्शन"
                                        AppLanguage.MARATHI -> "खाते भांडवल आणि अचूक लॉट आकार मार्गदर्शन"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = GoldLight,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Account Balance Tabs ($100, $500, $1k, $5k, $10k)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            prediction.accountTierMatrix.forEachIndexed { idx, tier ->
                                val isSelected = selectedTierIndex == idx
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedTierIndex = idx },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) GoldPrimary else ObsidianSurfaceElevated,
                                    border = if (isSelected) null else CardDefaults.outlinedCardBorder()
                                ) {
                                    Text(
                                        text = tier.balanceLabel.replace(" Account", "").replace(" VIP", ""),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                        color = if (isSelected) OnAccent else TextMuted,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Details for selected tier
                        val activeTier = prediction.accountTierMatrix.getOrNull(selectedTierIndex)
                            ?: prediction.accountTierMatrix[0]

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = when (currentLang) {
                                        AppLanguage.ENGLISH -> "Recommended Lot"
                                        AppLanguage.HINDI -> "सुझावित लॉट"
                                        AppLanguage.MARATHI -> "शिफारस केलेला लॉट"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                                Text(activeTier.safeLotSize, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = GoldLight)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = when (currentLang) {
                                        AppLanguage.ENGLISH -> "Risk If SL (Seal) Hits"
                                        AppLanguage.HINDI -> "SL (सील) हिट होने पर रिस्क"
                                        AppLanguage.MARATHI -> "SL (सील) हिट झाल्यास रिस्क"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                                Text(activeTier.riskAmountDollars, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = NeonRed)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = when (currentLang) {
                                        AppLanguage.ENGLISH -> "Profit At TP1"
                                        AppLanguage.HINDI -> "TP1 पर अनुमानित मुनाफा"
                                        AppLanguage.MARATHI -> "TP1 वर अंदाजित नफा"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                                Text(activeTier.rewardTp1Dollars, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = NeonGreen)
                            }
                        }
                    }
                }
            }

            // 4-Step Execution Checklist with Localized Language support
            val currentRules = prediction.getExecutionRules(currentLang)
            if (currentRules.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianSurfaceCard,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.verticalGradient(listOf(ObsidianBorderHighlight, ObsidianBorder))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Text(
                            text = when (currentLang) {
                                AppLanguage.ENGLISH -> "TRADE EXECUTION CHECKLIST (4 CONFIRMATIONS)"
                                AppLanguage.HINDI -> "ट्रेड निष्पादन चेकलिस्ट (4 पुष्टियां)"
                                AppLanguage.MARATHI -> "ट्रेड अंमलबजावणी चेकलिस्ट (4 खात्री)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = GoldLight,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        currentRules.forEach { rule ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = NeonGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = rule,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons: One-Tap Copy Profit Plan & Lot Calculator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(
                            "Gold Signal Plan",
                            "👑 KALANKAR FX GOLD PRO • OFFICIAL SIGNAL PLAYBOOK\n" +
                                "Pair: XAU/USD (Gold Spot)\n" +
                                "Verdict: ${prediction.verdict.label} (${prediction.urgencyTag})\n" +
                                "Trade Type: ${prediction.tradeType}\n" +
                                "Order: ${prediction.orderExecutionType}\n" +
                                "Entry Zone: ${prediction.recommendedEntryZone}\n" +
                                "Stop Loss (Seal): ${prediction.stopLossLevel}\n" +
                                "Why SL: ${prediction.getStopLossRationale(currentLang)}\n" +
                                "TP 1: ${prediction.takeProfit1} (Book 50% & SL to Entry)\n" +
                                "TP 2: ${prediction.takeProfit2} (Book 30%)\n" +
                                "TP 3: ${prediction.takeProfit3} (Moonbag 20%)\n" +
                                "Confidence: ${prediction.winProbabilityPercent}% (real accuracy: see Learning Center)\n" +
                                "By Mr. Rudvay Ujjwal Kalankar"
                        )
                        clipboard.setPrimaryClip(clip)
                        val copyToast = when (currentLang) {
                            AppLanguage.ENGLISH -> "Full Profit Plan copied to clipboard! 📋"
                            AppLanguage.HINDI -> "प्रॉफिट प्लान क्लिपबोर्ड पर कॉपी हो गया! 📋"
                            AppLanguage.MARATHI -> "प्रॉफिट प्लॅन क्लिपबोर्डवर कॉपी झाला! 📋"
                        }
                        Toast.makeText(context, copyToast, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("copy_profit_plan_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ObsidianSurfaceElevated,
                        contentColor = GoldPrimary
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(GoldPrimary, ObsidianBorderHighlight))
                    )
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (currentLang) {
                            AppLanguage.ENGLISH -> "Copy Profit Plan"
                            AppLanguage.HINDI -> "प्रॉफिट प्लान कॉपी करें"
                            AppLanguage.MARATHI -> "प्रॉफिट प्लॅन कॉपी करा"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onOpenCalculator,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("open_lot_calc_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = OnAccent
                    )
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (currentLang) {
                            AppLanguage.ENGLISH -> "Lot & Pip Calc"
                            AppLanguage.HINDI -> "लॉट व पिप कैलकुलेटर"
                            AppLanguage.MARATHI -> "लॉट व पिप कॅल्क्युलेटर"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
