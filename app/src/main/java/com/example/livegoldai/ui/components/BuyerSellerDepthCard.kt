package com.example.livegoldai.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.livegoldai.model.BuyerSellerSentiment
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun BuyerSellerDepthCard(
    sentiment: BuyerSellerSentiment?,
    currentPrice: Double,
    modifier: Modifier = Modifier
) {
    if (sentiment == null) return

    val currentLanguage = LocalAppLanguage.current
    val isBullsDominant = sentiment.buyersPercent >= sentiment.sellersPercent
    val dominantColor = if (isBullsDominant) SignalBuy else SignalSell

    val pulseTransition = rememberInfiniteTransition(label = "tape_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("buyer_seller_depth_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(dominantColor.copy(alpha = 0.6f), ObsidianBorder)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(dominantColor.copy(alpha = 0.15f))
                            .border(1.dp, dominantColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = "Buyers vs Sellers",
                            tint = dominantColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "BUY VS SELL (ESTIMATE)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = GoldLight,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = dominantColor.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = when (currentLanguage) {
                                        AppLanguage.ENGLISH -> "ESTIMATED PRESSURE"
                                        AppLanguage.HINDI -> "अनुमानित खरीदारी vs बिकवाली"
                                        AppLanguage.MARATHI -> "अंदाजित खरेदीदार विरुद्ध विक्रेते"
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = dominantColor
                                )
                            }
                        }
                        Text(
                            text = when (currentLanguage) {
                                AppLanguage.ENGLISH -> "Estimated from candle shapes. Spot gold has no public order book."
                                AppLanguage.HINDI -> "कैंडल के आकार से अनुमान। स्पॉट गोल्ड की कोई पब्लिक ऑर्डर बुक नहीं होती।"
                                AppLanguage.MARATHI -> "कँडलच्या आकारावरून अंदाज. स्पॉट गोल्डची सार्वजनिक ऑर्डर बुक नसते."
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = TextSecondary
                        )
                    }
                }

                // Live Pulse Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = dominantColor.copy(alpha = 0.15f),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(dominantColor, dominantColor.copy(alpha = 0.3f)))
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
                                .background(dominantColor.copy(alpha = pulseAlpha))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "ESTIMATE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Black,
                            color = dominantColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Main Big Tug-Of-War Gauge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurfaceElevated,
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(listOf(ObsidianBorderHighlight, ObsidianBorder))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Percentage labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        // Buyers side
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = SignalBuy,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "BUYERS (LONG)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SignalBuy
                                )
                            }
                            Text(
                                text = "${sentiment.buyersPercent}%",
                                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                                fontWeight = FontWeight.Black,
                                color = SignalBuy
                            )
                        }

                        // Center Status Pill
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = dominantColor.copy(alpha = 0.2f),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.linearGradient(listOf(dominantColor, Color.Transparent))
                            ),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                text = if (isBullsDominant) "+${sentiment.buyersPercent - sentiment.sellersPercent}% BULLISH DELTA" else "+${sentiment.sellersPercent - sentiment.buyersPercent}% BEARISH DELTA",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Black,
                                color = dominantColor
                            )
                        }

                        // Sellers side
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "SELLERS (SHORT)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SignalSell
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = SignalSell,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "${sentiment.sellersPercent}%",
                                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                                fontWeight = FontWeight.Black,
                                color = SignalSell
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Dual Colored Tug-of-war Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(ObsidianBackground)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(sentiment.buyersPercent.toFloat().coerceAtLeast(1f))
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(listOf(SignalBuy.copy(alpha = 0.7f), SignalBuy))
                                )
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Box(
                            modifier = Modifier
                                .weight(sentiment.sellersPercent.toFloat().coerceAtLeast(1f))
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(listOf(SignalSell, SignalSell.copy(alpha = 0.7f)))
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Order Book Count Footers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Buy-side estimate",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TextSecondary
                        )
                        Text(
                            text = "Not real order-flow data",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = dominantColor
                        )
                        Text(
                            text = "Sell-side estimate",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4 Mini Metrics Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metric 1: Retail Crowd Bias
                MetricPill(
                    title = "RETAIL CROWD",
                    value = "${sentiment.retailSentimentBias.name} (${sentiment.buyersPercent}%)",
                    badgeColor = if (sentiment.retailSentimentBias == Signal.BUY) SignalBuy else SignalSell,
                    modifier = Modifier.weight(1f)
                )

                // Metric 2: Institutional Order Flow
                MetricPill(
                    title = "SMART MONEY",
                    value = if (sentiment.institutionalSentimentBias == Signal.BUY) "ACCUMULATING" else "DISTRIBUTING",
                    badgeColor = if (sentiment.institutionalSentimentBias == Signal.BUY) SignalBuy else SignalSell,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Actionable Analysis Banner in Hindi & English
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = dominantColor.copy(alpha = 0.10f),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(listOf(dominantColor.copy(alpha = 0.5f), Color.Transparent))
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ElectricBolt,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = sentiment.strengthLevel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = GoldLight
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "🇮🇳 HINDI INSIGHT: ${sentiment.liveActionHindi}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "🌐 ENGLISH TACTIC: ${sentiment.liveActionEnglish}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    title: String,
    value: String,
    badgeColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ObsidianSurfaceElevated,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(listOf(ObsidianBorderHighlight, ObsidianBorder))
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = TextMuted,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = badgeColor
            )
        }
    }
}
