package com.example.livegoldai.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.GroupAnalysis
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*

@Composable
fun HeroConsensusCard(
    analysis: GoldAnalysisResult,
    modifier: Modifier = Modifier,
    onPillarClick: ((GroupAnalysis) -> Unit)? = null
) {
    val currentLang = LocalAppLanguage.current
    val signalColor by animateColorAsState(
        targetValue = when (analysis.overallSignal) {
            Signal.BUY -> SignalBuy
            Signal.SELL -> SignalSell
            Signal.WAIT -> SignalWait
        },
        label = "signalColor"
    )

    val signalBg = when (analysis.overallSignal) {
        Signal.BUY -> SignalBuyContainer
        Signal.SELL -> SignalSellContainer
        Signal.WAIT -> SignalWaitContainer
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_consensus_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    signalColor.copy(alpha = 0.6f),
                    GoldPrimary.copy(alpha = 0.3f),
                    ObsidianBorder
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            analysis.newsMode?.let { nm ->
                val bannerColor = if (nm.phase == "PRE") AmberWarning else NeonRed
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = bannerColor.copy(alpha = 0.15f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(bannerColor, bannerColor.copy(alpha = 0.3f)))
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (nm.phase == "PRE") {
                                when (currentLang) {
                                    AppLanguage.ENGLISH -> "⚠️ HIGH-IMPACT NEWS RADAR • APPROACHING"
                                    AppLanguage.HINDI -> "⚠️ हाई-इम्पैक्ट न्यूज़ रडार • रिलीज़ होने वाली है"
                                    AppLanguage.MARATHI -> "⚠️ हाय-इम्पॅक्ट न्यूज रडार • प्रसिद्ध होणार आहे"
                                }
                            } else {
                                when (currentLang) {
                                    AppLanguage.ENGLISH -> "📰 LIVE NEWS MODE • HIGH-VOLATILITY WINDOW"
                                    AppLanguage.HINDI -> "📰 लाइव न्यूज़ मोड • हाई-वोलैटिलिटी विंडो"
                                    AppLanguage.MARATHI -> "📰 लाईव्ह न्यूज मोड • हाय-व्होलॅटिलिटी विंडो"
                                }
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = bannerColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = nm.getLocalizedHeadline(currentLang),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = nm.getLocalizedDetail(currentLang),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (currentLang) {
                                AppLanguage.ENGLISH -> "Standard Technical Signal (Suspended during news): ${nm.technicalSignal.name}"
                                AppLanguage.HINDI -> "सामान्य तकनीकी सिग्नल (न्यूज़ के दौरान स्थगित): ${nm.technicalSignal.name}"
                                AppLanguage.MARATHI -> "सामान्य तांत्रिक सिग्नल (न्यूज दरम्यान स्थगित): ${nm.technicalSignal.name}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = TextMuted
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Header with luxury badge
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
                            .background(signalColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (analysis.newsMode != null) "NEWS-BASED SIGNAL" else "OVERALL SIGNAL CONSENSUS",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted,
                        letterSpacing = 1.2.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GoldContainer,
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(GoldPrimary, GoldDark)))
                ) {
                    Text(
                        text = "${analysis.groups.size} GROUPS",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = GoldLight,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Pulsing Verdict Badge
            Box(
                modifier = Modifier
                    .scale(pulseScale)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(
                                signalColor.copy(alpha = 0.25f),
                                signalBg
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.horizontalGradient(listOf(signalColor, signalColor.copy(alpha = 0.4f))),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 32.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = when (analysis.overallSignal) {
                            Signal.BUY -> Icons.Default.ArrowUpward
                            Signal.SELL -> Icons.Default.ArrowDownward
                            Signal.WAIT -> Icons.Default.HourglassEmpty
                        },
                        contentDescription = "Signal Icon",
                        tint = signalColor,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when (analysis.overallSignal) {
                            Signal.BUY -> "STRONG BUY"
                            Signal.SELL -> "STRONG SELL"
                            Signal.WAIT -> "WAIT / NEUTRAL"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = signalColor,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Consensus Meter Bar & Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Group Agreement",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "${analysis.agreementPercent.toInt()}% Consensus",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    GroupTallyPill(count = analysis.buyCount, label = "BUY", color = SignalBuy, bg = SignalBuyContainer)
                    Spacer(modifier = Modifier.width(6.dp))
                    GroupTallyPill(count = analysis.sellCount, label = "SELL", color = SignalSell, bg = SignalSellContainer)
                    Spacer(modifier = Modifier.width(6.dp))
                    GroupTallyPill(count = analysis.waitCount, label = "WAIT", color = SignalWait, bg = SignalWaitContainer)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar showing consensus strength
            LinearProgressIndicator(
                progress = { (analysis.agreementPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = signalColor,
                trackColor = ObsidianBorder
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5 Groups Quick Status Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                analysis.groups.forEach { group ->
                    val pillColor = when (group.verdict) {
                        Signal.BUY -> SignalBuy
                        Signal.SELL -> SignalSell
                        Signal.WAIT -> SignalWait
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = onPillarClick != null) { onPillarClick?.invoke(group) }
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = group.title.take(5).uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = pillColor.copy(alpha = 0.15f),
                            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(pillColor, pillColor.copy(alpha = 0.4f))))
                        ) {
                            Text(
                                text = group.verdict.name,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = pillColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupTallyPill(
    count: Int,
    label: String,
    color: Color,
    bg: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(color.copy(alpha = 0.6f), color.copy(alpha = 0.2f))))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                color = TextSecondary
            )
        }
    }
}
