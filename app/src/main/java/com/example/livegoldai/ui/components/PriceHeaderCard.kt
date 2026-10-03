package com.example.livegoldai.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.localization.LocalizationStrings
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.theme.*
import java.util.Locale

@Composable
fun PriceHeaderCard(
    analysis: GoldAnalysisResult,
    selectedInterval: String,
    countdownSeconds: Int,
    isRefreshing: Boolean,
    onIntervalSelected: (String) -> Unit,
    onRefreshClick: () -> Unit,
    alertTargetPrice: Double? = null,
    isAlertTriggered: Boolean = false,
    onAlertClick: () -> Unit = {},
    onOpenSpotInspector: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentLanguage = LocalAppLanguage.current
    val isPositive = analysis.changeAmount >= 0
    val changeColor = if (isPositive) SignalBuy else SignalSell

    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("price_header_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    GoldPrimary.copy(alpha = 0.5f),
                    ObsidianBorderHighlight,
                    ObsidianBorder
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Brand bar & attribution + Action pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "KALANKAR FX",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = GoldLight,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = GoldPrimary,
                        ) {
                            Text(
                                text = "PRO",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Black,
                                color = ObsidianBackground
                            )
                        }
                    }
                    Text(
                        text = "Mr. Rudvay Ujjwal Kalankar",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                        color = TextSecondary
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Alert target chip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isAlertTriggered) SignalSell.copy(alpha = 0.2f) else ObsidianSurfaceElevated,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(
                                if (isAlertTriggered) listOf(SignalSell, GoldPrimary)
                                else if (alertTargetPrice != null) listOf(GoldPrimary, ObsidianBorder)
                                else listOf(ObsidianBorderHighlight, ObsidianBorder)
                            )
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onAlertClick() }
                            .testTag("price_alert_header_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Price Alert",
                                tint = if (isAlertTriggered) SignalSell else if (alertTargetPrice != null) GoldPrimary else TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            if (alertTargetPrice != null) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$${String.format(Locale.US, "%.0f", alertTargetPrice)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAlertTriggered) SignalSell else GoldLight
                                )
                            }
                        }
                    }

                    // Sync countdown / manual refresh button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurfaceElevated,
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(ObsidianBorderHighlight, ObsidianBorder))),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onRefreshClick() }
                            .testTag("refresh_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = "Refresh",
                                tint = GoldPrimary,
                                modifier = Modifier
                                    .size(14.dp)
                                    .then(if (isRefreshing) Modifier.rotate(spinAngle) else Modifier)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${countdownSeconds}s",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Main Price Display (Clickable to open Gold Spot Inspector)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenSpotInspector() }
                    .testTag("clickable_price_inspector_row"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // weight(1f): the change pill on the right is measured first and keeps its natural width
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "XAU/USD • GOLD SPOT",
                            style = MaterialTheme.typography.labelMedium,
                            color = GoldLight,
                            letterSpacing = 1.sp,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = GoldPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "INSPECT",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                fontWeight = FontWeight.Bold,
                                color = GoldLight
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "$${String.format(Locale.US, "%,.2f", analysis.currentPrice)}",
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 36.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        fontFamily = FontFamily.SansSerif
                    )
                }

                // 24h Change Pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = changeColor.copy(alpha = 0.15f),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(changeColor, changeColor.copy(alpha = 0.3f)))),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${if (isPositive) "+" else ""}${String.format(Locale.US, "%.2f", analysis.changeAmount)}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = changeColor,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "(${if (isPositive) "+" else ""}${String.format(Locale.US, "%.2f", analysis.changePercent)}%)",
                            style = MaterialTheme.typography.labelMedium,
                            color = changeColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Range meter (Low vs High)
            val range = analysis.high24h - analysis.low24h
            val progress = if (range > 0) ((analysis.currentPrice - analysis.low24h) / range).toFloat().coerceIn(0f, 1f) else 0.5f

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${LocalizationStrings.low24h(currentLanguage)}: $${String.format(Locale.US, "%,.2f", analysis.low24h)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                    Text(
                        text = "${LocalizationStrings.high24h(currentLanguage)}: $${String.format(Locale.US, "%,.2f", analysis.high24h)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = GoldPrimary,
                    trackColor = ObsidianBorder
                )
            }

            // Real-Time Buy vs Sell Ratio Live Snapshot (Kitna Buy ho raha hai vs Kitna Sell)
            analysis.buyerSellerRatio?.let { bs ->
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceElevated,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(SignalBuy.copy(alpha = 0.5f), SignalSell.copy(alpha = 0.5f)))
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("live_buy_sell_strip")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SignalBuy))
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "BUY: ${bs.buyersPercent}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        color = SignalBuy,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "Up candles"
                                            AppLanguage.HINDI -> "ऊपर की कैंडल"
                                            AppLanguage.MARATHI -> "वरच्या कँडल"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = TextSecondary
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = (if (bs.buyersPercent >= bs.sellersPercent) SignalBuy else SignalSell).copy(alpha = 0.2f),
                                border = CardDefaults.outlinedCardBorder().copy(
                                    brush = Brush.linearGradient(
                                        listOf(
                                            if (bs.buyersPercent >= bs.sellersPercent) SignalBuy else SignalSell,
                                            Color.Transparent
                                        )
                                    )
                                )
                            ) {
                                Text(
                                    text = if (bs.buyersPercent == bs.sellersPercent) {
                                        when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "BALANCED"
                                            AppLanguage.HINDI -> "बराबर"
                                            AppLanguage.MARATHI -> "समान"
                                        }
                                    } else if (bs.buyersPercent > bs.sellersPercent) {
                                        when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "BUY PRESSURE"
                                            AppLanguage.HINDI -> "खरीद दबाव"
                                            AppLanguage.MARATHI -> "खरेदीचा दबाव"
                                        }
                                    } else {
                                        when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "SELL PRESSURE"
                                            AppLanguage.HINDI -> "बिक्री दबाव"
                                            AppLanguage.MARATHI -> "विक्रीचा दबाव"
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.Black,
                                    color = if (bs.buyersPercent >= bs.sellersPercent) SignalBuy else SignalSell
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "SELL: ${bs.sellersPercent}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        color = SignalSell,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "Down candles"
                                            AppLanguage.HINDI -> "नीचे की कैंडल"
                                            AppLanguage.MARATHI -> "खालच्या कँडल"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = TextSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SignalSell))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Dual Color Tug-of-war Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(ObsidianBackground)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(bs.buyersPercent.toFloat().coerceAtLeast(1f))
                                    .fillMaxHeight()
                                    .background(SignalBuy)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Box(
                                modifier = Modifier
                                    .weight(bs.sellersPercent.toFloat().coerceAtLeast(1f))
                                    .fillMaxHeight()
                                    .background(SignalSell)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = LocalizationStrings.liveOrderFlowBalance(currentLanguage),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = TextMuted
                            )
                            Text(
                                // spot gold has no public order book: no bid/ask counts or lot numbers are shown
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "not a real order book"
                                    AppLanguage.HINDI -> "असली ऑर्डर बुक नहीं"
                                    AppLanguage.MARATHI -> "खरे ऑर्डर बुक नाही"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Timeframe selection chips (1m, 2m, 3m, 4m, 5m, 10m, 15m, 30m, 45m, 1h, 2h, 3h, 4h, 5h, 6h, 1day, 1w, 2w, 3w, 1mo)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val intervals = listOf(
                    "1m", "2m", "3m", "4m", "5m", "10m", "15m", "30m", "45m",
                    "1h", "2h", "3h", "4h", "5h", "6h",
                    "1day",
                    "1w", "2w", "3w",
                    "1mo"
                )
                val labels = mapOf(
                    "1m" to "1M",
                    "2m" to "2M",
                    "3m" to "3M",
                    "4m" to "4M",
                    "5m" to "5M",
                    "10m" to "10M",
                    "15m" to "15M",
                    "30m" to "30M",
                    "45m" to "45M",
                    "1h" to "1H",
                    "2h" to "2H",
                    "3h" to "3H",
                    "4h" to "4H",
                    "5h" to "5H",
                    "6h" to "6H",
                    "1day" to "1D",
                    "1w" to "1W",
                    "2w" to "2W",
                    "3w" to "3W",
                    "1mo" to "1MO"
                )

                intervals.forEach { interval ->
                    val isSelected = interval == selectedInterval
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) GoldPrimary else ObsidianSurfaceElevated,
                        border = if (isSelected) null else CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(ObsidianBorderHighlight, ObsidianBorder))),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onIntervalSelected(interval) }
                            .testTag("timeframe_$interval")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = labels[interval] ?: interval,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                color = if (isSelected) ObsidianBackground else TextSecondary
                            )
                        }
                    }
                }
            }

            if (analysis.isSimulatedFallback) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SignalWaitContainer,
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(SignalWait.copy(alpha = 0.5f), Color.Transparent))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = LocalizationStrings.offlineModeCached(currentLanguage),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                        color = SignalWaitText
                    )
                }
            }
        }
    }
}
