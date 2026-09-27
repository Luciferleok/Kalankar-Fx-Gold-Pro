package com.example.livegoldai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.livegoldai.model.NewsTradingPlan
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*
import java.util.Locale

@Composable
fun NewsTradingModeCard(
    newsPlan: NewsTradingPlan?,
    isNewsModeActive: Boolean,
    onToggleNewsMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (newsPlan == null) return

    val currentLang = LocalAppLanguage.current

    val infiniteTransition = rememberInfiniteTransition(label = "news_alert_pulse")
    val alertAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alertAlpha"
    )

    val alertRed = Color(0xFFFF1744)
    val alertAmber = Color(0xFFFF9100)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("news_trading_mode_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNewsModeActive) Color(0xFF1E080F) else ObsidianSurfaceCard
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                if (isNewsModeActive) listOf(alertRed, alertAmber, alertRed)
                else listOf(GoldPrimary.copy(alpha = 0.7f), ObsidianBorderHighlight, ObsidianBorder)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: Title & News Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isNewsModeActive) alertRed.copy(alpha = 0.2f) else GoldPrimary.copy(alpha = 0.15f))
                            .border(1.dp, if (isNewsModeActive) alertRed else GoldPrimary, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🚨", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "HIGH-IMPACT NEWS TRADING MODE"
                                    AppLanguage.HINDI -> "हाई-इम्पैक्ट न्यूज़ ट्रेडिंग मोड"
                                    AppLanguage.MARATHI -> "हाय-इम्पॅक्ट न्यूज ट्रेडिंग मोड"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = if (isNewsModeActive) alertRed else GoldLight,
                                letterSpacing = 0.5.sp,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = if (isNewsModeActive) "🚨 NEWS METHOD & COLOR SHIFT ACTIVE" else "CPI, NFP & FOMC Volatility Engine",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = if (isNewsModeActive) alertAmber else TextSecondary
                        )
                    }
                }

                // Switch to manually toggle / test News Mode
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isNewsModeActive) "NEWS ON" else "OFF",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Black,
                        color = if (isNewsModeActive) alertRed else TextMuted
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = isNewsModeActive,
                        onCheckedChange = { onToggleNewsMode(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = alertRed,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = ObsidianBorder
                        ),
                        modifier = Modifier.testTag("news_mode_toggle_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Event Live Countdown Banner
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isNewsModeActive) alertRed.copy(alpha = 0.15f) else ObsidianSurfaceElevated,
                border = BorderStroke(1.dp, if (isNewsModeActive) alertRed.copy(alpha = alertAlpha) else ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isNewsModeActive) alertRed.copy(alpha = alertAlpha) else alertAmber)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = newsPlan.eventName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = if (isNewsModeActive) Color.White else GoldLight,
                                fontSize = 10.sp
                            )
                            Text(
                                text = newsPlan.releaseCountdownFormatted,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = if (isNewsModeActive) alertAmber else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = alertRed.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "🔴 RED FOLDER",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            fontWeight = FontWeight.Black,
                            color = alertRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 1. Strict 90-Seconds Freeze Guard
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = alertAmber.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, alertAmber.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = alertAmber,
                        modifier = Modifier.size(16.dp).padding(top = 1.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "⛔ 90-SECONDS FREEZE GUARD (NO MARKET ORDERS):",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Black,
                            color = alertAmber
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = newsPlan.getFreezeRule(currentLang),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2. News Straddle Levels (Both-way OCO Pending Orders)
            Text(
                text = "⚡ PRE-NEWS STRADDLE PENDING SETUP (SPREAD BUFFER: +${newsPlan.spreadWarningBufferPips}p):",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Black,
                color = TextMuted,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Buy Stop
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SignalBuy.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, SignalBuy.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "BUY STOP PENDING",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = SignalBuy
                        )
                        Text(
                            text = "$${String.format(Locale.US, "%,.2f", newsPlan.straddleUpperLevel)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = SignalBuy
                        )
                        Text(
                            text = "Trigger on bullish CPI drop",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = TextSecondary
                        )
                    }
                }

                // Sell Stop
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SignalSell.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, SignalSell.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "SELL STOP PENDING",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = SignalSell
                        )
                        Text(
                            text = "$${String.format(Locale.US, "%,.2f", newsPlan.straddleLowerLevel)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = SignalSell
                        )
                        Text(
                            text = "Trigger on hot inflation spike",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Second-Wave Retracement Sniper Entry
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ObsidianSurfaceElevated,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🎯 SECOND-WAVE RETRACEMENT ENTRY:",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Black,
                            color = GoldLight
                        )
                        Text(
                            text = newsPlan.getNewsTactic(currentLang),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}
