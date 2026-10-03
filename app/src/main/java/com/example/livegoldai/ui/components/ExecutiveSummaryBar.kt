package com.example.livegoldai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*
import java.util.Locale

@Composable
fun ExecutiveSummaryBar(
    analysis: GoldAnalysisResult,
    onTabSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val prediction = analysis.nextPrediction
    val sigColor = when (analysis.overallSignal) {
        Signal.BUY -> NeonGreen
        Signal.SELL -> NeonRed
        Signal.WAIT -> AmberWarning
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurfaceCard,
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                listOf(GoldPrimary.copy(alpha = 0.7f), ObsidianBorderHighlight, ObsidianBorder)
            )
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: 1-Glance Title & Live Confidence
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
                            .background(sigColor.copy(alpha = pulseAlpha))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "EXECUTIVE SNAPSHOT (1-GLANCE)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = GoldLight,
                        letterSpacing = 0.8.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = sigColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "${prediction?.winProbabilityPercent ?: analysis.tradeSetup.confidencePercent}% CONFIDENCE",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = sigColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4-Pill Quick Metric Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pill 1: Signal & Strategy
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelect(0) },
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceElevated,
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "DIRECTION",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = analysis.overallSignal.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = sigColor,
                            fontSize = 12.sp
                        )
                    }
                }

                // Pill 2: Entry Zone
                Surface(
                    modifier = Modifier
                        .weight(1.1f)
                        .clickable { onTabSelect(0) },
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceElevated,
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "ENTRY ZONE",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = prediction?.recommendedEntryZone?.split("-")?.firstOrNull()?.trim() ?: "$${String.format(Locale.US, "%.1f", analysis.tradeSetup.entryPrice)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                    }
                }

                // Pill 3: SEAL (Stop Loss)
                Surface(
                    modifier = Modifier
                        .weight(1.1f)
                        .clickable { onTabSelect(0) },
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceElevated,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.verticalGradient(listOf(NeonRed.copy(alpha = 0.5f), ObsidianBorder))
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "SEAL (SL)",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonRed
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = prediction?.stopLossLevel ?: "$${String.format(Locale.US, "%.1f", analysis.tradeSetup.stopLoss)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = NeonRed,
                            fontSize = 12.sp
                        )
                    }
                }

                // Pill 4: TARGET (TP1)
                Surface(
                    modifier = Modifier
                        .weight(1.1f)
                        .clickable { onTabSelect(0) },
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceElevated,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.verticalGradient(listOf(NeonGreen.copy(alpha = 0.5f), ObsidianBorder))
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "TARGET (TP1)",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreen
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = prediction?.takeProfit1 ?: "$${String.format(Locale.US, "%.1f", analysis.tradeSetup.takeProfit1)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = NeonGreen,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Quick Micro Ticker: Next Candle & Timeframe Status
            if (analysis.candleInsight != null || analysis.mtfMatrix != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ObsidianBackground)
                        .clickable { onTabSelect(1) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🕯️ Next Candle: ",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight
                        )
                        Text(
                            text = if (analysis.overallSignal == Signal.BUY) "Green Expansion Projected" else if (analysis.overallSignal == Signal.SELL) "Red Breakdown Projected" else "Inside-Bar Testing",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⏱️ MTF Matrix",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = GoldLight
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = GoldLight,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
