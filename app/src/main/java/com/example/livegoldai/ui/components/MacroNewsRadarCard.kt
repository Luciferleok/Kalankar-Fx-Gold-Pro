package com.example.livegoldai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import com.example.livegoldai.model.EconomicEvent
import com.example.livegoldai.model.MacroSentimentRadar
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*
import java.util.Locale
import kotlin.math.abs

@Composable
fun MacroNewsRadarCard(
    radar: MacroSentimentRadar,
    modifier: Modifier = Modifier
) {
    val currentLanguage = LocalAppLanguage.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("macro_news_radar_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(ObsidianBorderHighlight, ObsidianBorder)
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
                            .background(GoldPrimary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (currentLanguage) {
                            AppLanguage.ENGLISH -> "MACRO & NEWS SENTIMENT RADAR"
                            AppLanguage.HINDI -> "मैक्रो और न्यूज़ सेंटिमेंट रडार"
                            AppLanguage.MARATHI -> "मॅक्रो व न्यूज सेंटिमेंट रडार"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = GoldLight,
                        letterSpacing = 0.8.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when (radar.overallBias) {
                        Signal.BUY -> NeonGreen.copy(alpha = 0.18f)
                        Signal.SELL -> NeonRed.copy(alpha = 0.18f)
                        Signal.WAIT -> AmberWarning.copy(alpha = 0.18f)
                    }
                ) {
                    val flowText = when (radar.overallBias) {
                        Signal.BUY -> when (currentLanguage) {
                            AppLanguage.ENGLISH -> "${radar.sentimentScorePercent}% BULLISH FLOW"
                            AppLanguage.HINDI -> "${radar.sentimentScorePercent}% तेजी का बहाव"
                            AppLanguage.MARATHI -> "${radar.sentimentScorePercent}% तेजीचा प्रवाह"
                        }
                        Signal.SELL -> when (currentLanguage) {
                            AppLanguage.ENGLISH -> "${radar.sentimentScorePercent}% BEARISH FLOW"
                            AppLanguage.HINDI -> "${radar.sentimentScorePercent}% मंदी का बहाव"
                            AppLanguage.MARATHI -> "${radar.sentimentScorePercent}% मंदीचा प्रवाह"
                        }
                        Signal.WAIT -> when (currentLanguage) {
                            AppLanguage.ENGLISH -> "${radar.sentimentScorePercent}% NEUTRAL FLOW"
                            AppLanguage.HINDI -> "${radar.sentimentScorePercent}% न्यूट्रल बहाव"
                            AppLanguage.MARATHI -> "${radar.sentimentScorePercent}% तटस्थ प्रवाह"
                        }
                    }
                    Text(
                        text = flowText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = when (radar.overallBias) {
                            Signal.BUY -> NeonGreen
                            Signal.SELL -> NeonRed
                            Signal.WAIT -> AmberWarning
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = radar.getSummaryInsight(currentLanguage),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Dual Live Tickers: DXY (US Dollar) & 10Y Yield
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // DXY Box
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceElevated,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.verticalGradient(
                            listOf(
                                if (radar.dxyIndex.impactOnGold == Signal.BUY) NeonGreen.copy(alpha = 0.4f) else NeonRed.copy(alpha = 0.4f),
                                ObsidianBorder
                            )
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "USD INDEX (DXY)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                            Icon(
                                if (radar.dxyIndex.changePercent < 0) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = if (radar.dxyIndex.changePercent < 0) NeonGreen else NeonRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format(Locale.US, "%.2f", radar.dxyIndex.value),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${if (radar.dxyIndex.changePercent >= 0) "+" else ""}${String.format(Locale.US, "%.2f", radar.dxyIndex.changePercent)}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (radar.dxyIndex.changePercent < 0) NeonGreen else NeonRed
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (radar.dxyIndex.impactOnGold == Signal.BUY) {
                                when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "Tailwind (Bullish Gold)"
                                    AppLanguage.HINDI -> "अनुकूल (सोने में तेजी)"
                                    AppLanguage.MARATHI -> "अनुकूल (सोन्यात तेजी)"
                                }
                            } else {
                                when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "Headwind (Bearish Gold)"
                                    AppLanguage.HINDI -> "प्रतिकूल (सोने में मंदी)"
                                    AppLanguage.MARATHI -> "प्रतिकूल (सोन्यात मंदी)"
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (radar.dxyIndex.impactOnGold == Signal.BUY) NeonGreen else NeonRed
                        )
                    }
                }

                // 10Y Yield Box
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceElevated,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.verticalGradient(
                            listOf(
                                if (radar.us10yYield.impactOnGold == Signal.BUY) NeonGreen.copy(alpha = 0.4f) else NeonRed.copy(alpha = 0.4f),
                                ObsidianBorder
                            )
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "US 10-YR YIELD",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                            Icon(
                                if (radar.us10yYield.changePercent < 0) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = if (radar.us10yYield.changePercent < 0) NeonGreen else NeonRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${String.format(Locale.US, "%.2f", radar.us10yYield.value)}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${if (radar.us10yYield.changePercent >= 0) "+" else ""}${String.format(Locale.US, "%.2f", radar.us10yYield.changePercent)}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (radar.us10yYield.changePercent < 0) NeonGreen else NeonRed
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (radar.us10yYield.impactOnGold == Signal.BUY) {
                                when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "Yields falling (Bullish)"
                                    AppLanguage.HINDI -> "यील्ड गिर रही है (तेजी)"
                                    AppLanguage.MARATHI -> "यील्ड घसरत आहे (तेजी)"
                                }
                            } else {
                                when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "Yields rising (Bearish)"
                                    AppLanguage.HINDI -> "यील्ड बढ़ रही है (मंदी)"
                                    AppLanguage.MARATHI -> "यील्ड वाढत आहे (मंदी)"
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (radar.us10yYield.impactOnGold == Signal.BUY) NeonGreen else NeonRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // High Impact USD Economic Calendar Feed
            Text(
                text = when (currentLanguage) {
                    AppLanguage.ENGLISH -> "UPCOMING HIGH-IMPACT ECONOMIC EVENTS (USD)"
                    AppLanguage.HINDI -> "आगामी हाई-इम्पैक्ट आर्थिक इवेंट्स (USD)"
                    AppLanguage.MARATHI -> "पुढील महत्त्वाच्या आर्थिक घडामोडी (USD)"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = GoldLight,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                radar.upcomingEvents.take(4).forEach { event ->
                    EconomicEventRow(event)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Fundamental Catalysts Feed
            Text(
                text = when (currentLanguage) {
                    AppLanguage.ENGLISH -> "KEY STRUCTURAL GOLD DRIVERS"
                    AppLanguage.HINDI -> "सोने के मुख्य फंडामेंटल फैक्टर्स"
                    AppLanguage.MARATHI -> "सोन्यावरील मुख्य मूलभूत घटक"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = GoldLight,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            radar.newsFeed.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceElevated,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(ObsidianBorderHighlight, ObsidianBorder))
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = NeonGreen.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = item.impactTag,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = NeonGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = item.source,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.getHeadline(currentLanguage),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = item.getReason(currentLanguage),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EconomicEventRow(event: EconomicEvent) {
    val impactColor = when (event.impact.lowercase()) {
        "high" -> NeonRed
        "medium" -> AmberWarning
        else -> TextMuted
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ObsidianSurfaceElevated,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = impactColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${event.impact.uppercase()} • ${event.country}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = impactColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${event.date} • ${event.time}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }

                // India Time (IST) Highlight
                val indiaTimeFormatted = event.getIndiaTimeFormatted()
                if (indiaTimeFormatted.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🇮🇳", fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "भारत समय (IST): $indiaTimeFormatted",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (event.forecast.isNotBlank() || event.previous.isNotBlank()) {
                    Text(
                        text = "Fcst: ${event.forecast.ifBlank { "N/A" }} | Prev: ${event.previous.ifBlank { "N/A" }}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = GoldPrimary.copy(alpha = 0.12f),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = event.goldImpact,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldLight,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
