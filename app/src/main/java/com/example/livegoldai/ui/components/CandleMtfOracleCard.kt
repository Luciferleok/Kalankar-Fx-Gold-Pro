package com.example.livegoldai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import com.example.livegoldai.model.CandleReadingInsight
import com.example.livegoldai.model.MultiTimeframeMatrix
import com.example.livegoldai.model.Signal
import com.example.livegoldai.model.TradingTrick
import com.example.livegoldai.theme.*

@Composable
fun CandleMtfOracleCard(
    candleInsight: CandleReadingInsight,
    mtfMatrix: MultiTimeframeMatrix,
    tradingTricks: List<TradingTrick>,
    modifier: Modifier = Modifier
) {
    val currentLanguage = LocalAppLanguage.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Next Candle AI, 1: Multi-Timeframe, 2: Trading Tricks
    var expandedTrickId by remember { mutableStateOf<String?>(tradingTricks.firstOrNull()?.id) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("candle_mtf_oracle_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(GoldPrimary.copy(alpha = 0.8f), ObsidianBorderHighlight, GoldDark)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header
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
                            .background(GoldPrimary.copy(alpha = pulseAlpha))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PRICE ACTION & TIMEFRAME AI",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = GoldLight,
                        letterSpacing = 0.8.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NeonGreen.copy(alpha = 0.16f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(NeonGreen, GoldPrimary))
                    )
                ) {
                    Text(
                        text = "${candleInsight.confidencePercent}% CONFIDENCE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeonGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-Navigation Tabs: 1. Candle Reading & Next Candle | 2. 1M-1D Timeframe | 3. Trading Tricks
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabLabels = when (currentLanguage) {
                    AppLanguage.ENGLISH -> listOf("🕯️ Candle AI", "⏱️ Timeframes", "⚡ Pro Tricks")
                    AppLanguage.HINDI -> listOf("🕯️ कैंडल AI", "⏱️ टाइमफ्रेम्स", "⚡ प्रो ट्रिक्स")
                    AppLanguage.MARATHI -> listOf("🕯️ कँडल AI", "⏱️ टाइमफ्रेम्स", "⚡ प्रो ट्रिक्स")
                }
                tabLabels.forEachIndexed { index, label ->
                    val isSelected = selectedTab == index
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = index },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) GoldPrimary else ObsidianSurfaceElevated,
                        border = if (isSelected) null else CardDefaults.outlinedCardBorder()
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            color = if (isSelected) Color.Black else TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(vertical = 7.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (selectedTab) {
                0 -> {
                    // TAB 0: DEEP CANDLESTICK READING & NEXT CANDLE FORECAST
                    // Last Candle Analysis Banner
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurfaceElevated,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(GoldPrimary.copy(alpha = 0.4f), ObsidianBorder))
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PREVIOUS CANDLE PROFILE (PATTERN IDENTIFIED)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = GoldLight
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = SignalBuyBg
                                ) {
                                    Text(
                                        text = candleInsight.bodyMomentum,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonGreen,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = candleInsight.lastCandleType,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = candleInsight.lastCandleMeaning,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Wick Pressure & Rejection Meters
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianBackground,
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("LOWER WICK (BUY DEFENSE)", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextMuted)
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(candleInsight.lowerWickRejection, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = NeonGreen)
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianBackground,
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("UPPER WICK (SELLER PRESSURE)", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, color = TextMuted)
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(candleInsight.upperWickPressure, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = GoldLight)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // NEXT CANDLE PREDICTION BOX ("AGLI CANDLE KYA KAREGI")
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF131A26),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(NeonGreen.copy(alpha = 0.6f), GoldPrimary.copy(alpha = 0.4f)))
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
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = GoldLight, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "NEXT CANDLE PROJECTION (AI FORECAST)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = GoldLight,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = candleInsight.nextCandleForecast,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ObsidianBackground.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "🎯 " + candleInsight.nextCandleExpectedRange,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldLight,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "💡 " + candleInsight.getNextCandleTradeTactic(currentLanguage),
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonGreen,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                1 -> {
                    // TAB 1: MULTI-TIMEFRAME ALIGNMENT MATRIX (1M, 5M, 15M, 1H, 4H, 1D)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF131822),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.horizontalGradient(listOf(GoldPrimary.copy(alpha = 0.5f), ObsidianBorder))
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Layers, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "MULTI-TIMEFRAME CONFLUENCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = GoldLight
                                )
                                Text(
                                    text = mtfMatrix.alignmentSummary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonGreen
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Timeframe Rows (1M, 5M, 15M, 1H, 4H, 1D)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        mtfMatrix.timeframes.forEach { tf ->
                            val sigColor = when (tf.signal) {
                                Signal.BUY -> NeonGreen
                                Signal.SELL -> NeonRed
                                Signal.WAIT -> AmberWarning
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = ObsidianSurfaceElevated,
                                border = CardDefaults.outlinedCardBorder(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = sigColor.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = tf.timeframe,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Black,
                                                color = sigColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = tf.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = tf.keyLevel,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = TextMuted
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = tf.signal.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = sigColor,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = tf.quickAction,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Recommendations Summary
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("⚡ " + mtfMatrix.scalpRecommendation, style = MaterialTheme.typography.bodySmall, color = GoldLight, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("🎯 " + mtfMatrix.swingRecommendation, style = MaterialTheme.typography.bodySmall, color = NeonGreen, fontSize = 11.sp)
                        }
                    }
                }

                2 -> {
                    // TAB 2: ADVANCED TRADING TRICKS & CHEAT CODES ("PREDICTION TRICKS")
                    Text(
                        text = "PRO TRICKS TO MAXIMIZE PREDICTION ACCURACY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = GoldLight,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tradingTricks.forEach { trick ->
                            val isExpanded = expandedTrickId == trick.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianSurfaceElevated,
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = Brush.horizontalGradient(
                                        listOf(
                                            if (isExpanded) GoldPrimary.copy(alpha = 0.6f) else ObsidianBorderHighlight,
                                            ObsidianBorder
                                        )
                                    )
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedTrickId = if (isExpanded) null else trick.id
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = trick.name,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White
                                                )
                                            }
                                            Text(
                                                text = "${trick.winRate} • Target ${trick.expectedPipGain}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 10.sp,
                                                color = GoldLight
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = SignalBuyBg
                                        ) {
                                            Text(
                                                text = trick.status,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeonGreen,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    if (isExpanded) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Divider(color = ObsidianBorder)
                                        Spacer(modifier = Modifier.height(8.dp))

                                        val conditionLabel = when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "📌 Condition: "
                                            AppLanguage.HINDI -> "📌 शर्त: "
                                            AppLanguage.MARATHI -> "📌 अट: "
                                        }
                                        val actionLabel = when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "🎯 ACTION TRIGGER: "
                                            AppLanguage.HINDI -> "🎯 एक्शन ट्रिगर: "
                                            AppLanguage.MARATHI -> "🎯 ॲक्शन ट्रिगर: "
                                        }

                                        Text(
                                            text = "$conditionLabel${trick.getTriggerCondition(currentLanguage)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "$actionLabel${trick.getHowToTrade(currentLanguage)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp
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
}
