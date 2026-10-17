package com.example.livegoldai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.model.FailedPredictionCandleAutopsy
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*
import java.util.Locale

@Composable
fun AiFailedPredictionAutopsyCard(
    autopsy: FailedPredictionCandleAutopsy?,
    modifier: Modifier = Modifier
) {
    if (autopsy == null) return

    val currentLang = LocalAppLanguage.current
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_failed_prediction_autopsy_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(SignalSell.copy(alpha = 0.8f), GoldPrimary.copy(alpha = 0.5f), ObsidianBorder)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SignalSell.copy(alpha = 0.15f))
                            .border(1.dp, SignalSell.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🔬", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "LAST STOPPED REPLAY • CANDLE CHECK"
                                    AppLanguage.HINDI -> "पिछला Stop हुआ रीप्ले • कैंडल जाँच"
                                    AppLanguage.MARATHI -> "मागील Stop झालेला रीप्ले • कँडल तपासणी"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = SignalSell,
                                letterSpacing = 0.5.sp,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = "Real price path • ${autopsy.timeAgo}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SignalSell.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "-${autopsy.pipsLoss.toInt()} PIPS",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = SignalSell
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Visual Candlestick + Trap Details Box
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ObsidianSurfaceElevated,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Custom Drawn Candle Graphic
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 90.dp)
                            .background(ObsidianBackground, RoundedCornerShape(8.dp))
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 8.dp)) {
                            val w = size.width
                            val h = size.height
                            val centerX = w / 2f

                            // Upper Wick
                            drawLine(
                                color = SignalSell,
                                start = Offset(centerX, 0f),
                                end = Offset(centerX, h * 0.25f),
                                strokeWidth = 2.5f
                            )

                            // Candle Body
                            drawRect(
                                color = SignalSell,
                                topLeft = Offset(centerX - 8f, h * 0.25f),
                                size = Size(16f, h * 0.35f)
                            )

                            // Long Lower Wick (The Trap Wick)
                            drawLine(
                                color = SignalSell,
                                start = Offset(centerX, h * 0.60f),
                                end = Offset(centerX, h),
                                strokeWidth = 3f
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = autopsy.trapCandleType,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = GoldLight,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Stop candle",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = AmberWarning
                            )
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = autopsy.getDiagnosis(currentLang),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Upper Wick: ${autopsy.upperWickPips.toInt()}p | Lower Wick: ${autopsy.lowerWickPips.toInt()}p | Body: ${autopsy.bodyPips.toInt()}p",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = TextMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Recalibration Action Applied (Next prediction fix)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SignalBuy.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, SignalBuy.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SignalBuy,
                        modifier = Modifier.size(16.dp).padding(top = 1.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = when (currentLang) {
                                AppLanguage.ENGLISH -> "WHAT CHANGES BECAUSE OF IT:"
                                AppLanguage.HINDI -> "इससे क्या बदलता है:"
                                AppLanguage.MARATHI -> "यामुळे काय बदलते:"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Black,
                            color = SignalBuy
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = autopsy.getRecalibrationAction(currentLang),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                            color = TextPrimary
                        )
                    }
                }
            }

            // Expandable Multi-AI Model Dissections
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "MEASURED CANDLE (REAL PRICES):",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Black,
                        color = TextMuted
                    )
                    Surface(shape = RoundedCornerShape(8.dp), color = ObsidianSurfaceElevated, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Open ${String.format(Locale.US, "%.2f", autopsy.candleOpen)}  •  High ${String.format(Locale.US, "%.2f", autopsy.candleHigh)}  •  Low ${String.format(Locale.US, "%.2f", autopsy.candleLow)}  •  Close ${String.format(Locale.US, "%.2f", autopsy.candleClose)}",
                            modifier = Modifier.padding(8.dp), fontSize = 10.sp, color = TextPrimary
                        )
                    }
                    Surface(shape = RoundedCornerShape(8.dp), color = ObsidianSurfaceElevated, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Entry ${String.format(Locale.US, "%.2f", autopsy.entryPrice)}  •  Stop ${String.format(Locale.US, "%.2f", autopsy.stopLossPrice)}  •  Target ${String.format(Locale.US, "%.2f", autopsy.targetPrice)}" +
                                if (autopsy.volumeSurgeMultiplier > 0) "  •  Volume ${String.format(Locale.US, "%.1f", autopsy.volumeSurgeMultiplier)}x avg" else "  •  Volume: not available on this feed",
                            modifier = Modifier.padding(8.dp), fontSize = 10.sp, color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}
