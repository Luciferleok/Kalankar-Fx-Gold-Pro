package com.example.livegoldai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*
import java.util.Locale

@Composable
fun LiveMarketStatusCard(
    analysis: GoldAnalysisResult,
    onOpenPrediction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSession = analysis.marketSessions.firstOrNull { it.isOpen }
    val candleInsight = analysis.candleInsight
    val isBullish = analysis.overallSignal == Signal.BUY

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_market_status_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    SignalSell.copy(alpha = 0.5f),
                    GoldPrimary.copy(alpha = 0.8f),
                    ObsidianBorderHighlight
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: Live Indicator & Title
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
                            .background(Color(0xFFFF3B30))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ABHI KYA CHAL RAHA HAI?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = GoldLight,
                        letterSpacing = 0.8.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ObsidianBackground,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(GoldPrimary, ObsidianBorder))
                    )
                ) {
                    Text(
                        text = "LIVE STATUS",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = GoldLight,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Current Condition Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Live Price Box
                StatusMetricBox(
                    label = "LIVE PRICE",
                    value = "$${String.format(Locale.US, "%.2f", analysis.currentPrice)}",
                    subtext = "${if (analysis.changeAmount >= 0) "+" else ""}${String.format(Locale.US, "%.2f", analysis.changeAmount)} (${String.format(Locale.US, "%.2f", analysis.changePercent)}%)",
                    valueColor = if (analysis.changeAmount >= 0) SignalBuy else SignalSell,
                    modifier = Modifier.weight(1f)
                )

                // Market Momentum Box
                StatusMetricBox(
                    label = "CURRENT BIAS",
                    value = analysis.overallSignal.name,
                    subtext = "${analysis.agreementPercent.toInt()}% Technical Consensus",
                    valueColor = if (isBullish) SignalBuy else SignalSell,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Running Candle Reality
            if (candleInsight != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🕯️", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Running Candle: ${candleInsight.lastCandleType}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = candleInsight.lastCandleMeaning,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Key Immediate Levels Right Now
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LevelPill(
                    label = "S1 (Support)",
                    price = "$${String.format(Locale.US, "%.1f", analysis.pivotLevels.s1)}",
                    color = SignalBuy,
                    modifier = Modifier.weight(1f)
                )
                LevelPill(
                    label = "PIVOT",
                    price = "$${String.format(Locale.US, "%.1f", analysis.pivotLevels.pivot)}",
                    color = GoldLight,
                    modifier = Modifier.weight(1f)
                )
                LevelPill(
                    label = "R1 (Resistance)",
                    price = "$${String.format(Locale.US, "%.1f", analysis.pivotLevels.r1)}",
                    color = SignalSell,
                    modifier = Modifier.weight(1f)
                )
            }

            if (activeSession != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = GoldLight,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Active Session: ${activeSession.name} (${activeSession.city})",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Text(
                        text = "Volatility: ${activeSession.volatilityLevel}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Direct Call to Action to view Prediction
            Button(
                onClick = onOpenPrediction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("open_prediction_from_status_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldPrimary,
                    contentColor = OnAccent
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DEKHO: AGLA KYA HOGA? (PREDICTION & PROBABILITY)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun StatusMetricBox(
    label: String,
    value: String,
    subtext: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ObsidianSurfaceElevated,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = valueColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtext,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun LevelPill(
    label: String,
    price: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ObsidianBackground,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(color.copy(alpha = 0.4f), ObsidianBorder))
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted
            )
            Text(
                text = price,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}
