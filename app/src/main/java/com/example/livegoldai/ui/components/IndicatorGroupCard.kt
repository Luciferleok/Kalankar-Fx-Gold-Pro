package com.example.livegoldai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.model.GroupAnalysis
import com.example.livegoldai.model.IndicatorItem
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*

@Composable
fun IndicatorGroupCard(
    group: GroupAnalysis,
    modifier: Modifier = Modifier,
    onPillarClick: (() -> Unit)? = null
) {
    val currentLanguage = LocalAppLanguage.current
    val verdictColor = when (group.verdict) {
        Signal.BUY -> SignalBuy
        Signal.SELL -> SignalSell
        Signal.WAIT -> SignalWait
    }

    val verdictBg = when (group.verdict) {
        Signal.BUY -> SignalBuyContainer
        Signal.SELL -> SignalSellContainer
        Signal.WAIT -> SignalWaitContainer
    }

    val localizedGroupTitle = when (group.key.lowercase()) {
        "trend" -> when (currentLanguage) {
            AppLanguage.ENGLISH -> "1. TREND STRENGTH & STRUCTURE"
            AppLanguage.HINDI -> "1. ट्रेंड स्ट्रेंथ व मूविंग एवरेज"
            AppLanguage.MARATHI -> "1. ट्रेंड स्ट्रेंथ व मूव्हिंग सरासरी"
        }
        "smart_money", "smc" -> when (currentLanguage) {
            AppLanguage.ENGLISH -> "2. SMART MONEY & LIQUIDITY (SMC)"
            AppLanguage.HINDI -> "2. स्मार्ट मनी व लिक्विडिटी (SMC)"
            AppLanguage.MARATHI -> "2. स्मार्ट मनी व लिक्विडीटी (SMC)"
        }
        "momentum" -> when (currentLanguage) {
            AppLanguage.ENGLISH -> "3. MOMENTUM & ENTRY OSCILLATORS"
            AppLanguage.HINDI -> "3. मोमेंटम व एंट्री ऑसिलेटर्स"
            AppLanguage.MARATHI -> "3. मोमेंटम आणि एन्ट्री ऑसिलेटर्स"
        }
        "levels", "sr" -> when (currentLanguage) {
            AppLanguage.ENGLISH -> "4. SUPPORT, RESISTANCE & PIVOTS"
            AppLanguage.HINDI -> "4. सपोर्ट, रेजिस्टेंस व की-पिवट्स"
            AppLanguage.MARATHI -> "4. सपोर्ट, रेझिस्टन्स आणि मुख्य पिव्हट्स"
        }
        "volatility" -> when (currentLanguage) {
            AppLanguage.ENGLISH -> "5. VOLATILITY BANDS & RISK RANGE"
            AppLanguage.HINDI -> "5. वोलैटिलिटी बैंड्स व रिस्क रेंज"
            AppLanguage.MARATHI -> "5. व्होलॅटिलिटी बँड्स आणि जोखीम मर्यादा"
        }
        "candlestick" -> when (currentLanguage) {
            AppLanguage.ENGLISH -> "6. CANDLESTICK PRICE ACTION"
            AppLanguage.HINDI -> "6. कैंडलस्टिक प्राइस एक्शन"
            AppLanguage.MARATHI -> "6. कॅन्डलस्टिक प्राइस अ‍ॅक्शन"
        }
        "macro" -> when (currentLanguage) {
            AppLanguage.ENGLISH -> "7. MACRO & NEWS SENTIMENT"
            AppLanguage.HINDI -> "7. मैक्रो व न्यूज़ सेंटीमेंट"
            AppLanguage.MARATHI -> "7. मॅक्रो आणि न्यूज सेन्टिमेंट"
        }
        "volume", "order_flow" -> when (currentLanguage) {
            AppLanguage.ENGLISH -> "VOLUME & ORDER FLOW"
            AppLanguage.HINDI -> "वॉल्यूम व ऑर्डर फ्लो"
            AppLanguage.MARATHI -> "व्हॉल्यूम व ऑर्डर फ्लो"
        }
        else -> group.title.uppercase()
    }

    val localizedVerdict = when (group.verdict) {
        Signal.BUY -> when (currentLanguage) {
            AppLanguage.ENGLISH -> "BUY"
            AppLanguage.HINDI -> "BUY (खरीद)"
            AppLanguage.MARATHI -> "BUY (खरेदी)"
        }
        Signal.SELL -> when (currentLanguage) {
            AppLanguage.ENGLISH -> "SELL"
            AppLanguage.HINDI -> "SELL (बिक्री)"
            AppLanguage.MARATHI -> "SELL (विक्री)"
        }
        Signal.WAIT -> when (currentLanguage) {
            AppLanguage.ENGLISH -> "WAIT"
            AppLanguage.HINDI -> "WAIT (इंतज़ार)"
            AppLanguage.MARATHI -> "WAIT (वाट)"
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("group_card_${group.key}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    verdictColor.copy(alpha = 0.35f),
                    ObsidianBorderHighlight,
                    ObsidianBorder
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Group Title & Verdict Badge Header
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
                            .background(verdictColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = localizedGroupTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 0.5.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = verdictBg,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(verdictColor, verdictColor.copy(alpha = 0.3f)))
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (group.verdict) {
                                Signal.BUY -> Icons.Default.ArrowUpward
                                Signal.SELL -> Icons.Default.ArrowDownward
                                Signal.WAIT -> Icons.Default.HourglassEmpty
                            },
                            contentDescription = null,
                            tint = verdictColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = localizedVerdict,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Black,
                            color = verdictColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Indicator Rows
            group.indicators.forEachIndexed { index, indicator ->
                IndicatorRowItem(indicator = indicator, currentLanguage = currentLanguage)
                if (index < group.indicators.size - 1) {
                    HorizontalDivider(
                        color = ObsidianBorder.copy(alpha = 0.5f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }

            if (onPillarClick != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onPillarClick() }
                        .testTag("pillar_deep_dive_btn_${group.key}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🔬", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "INSPECT HISTORICAL ACCURACY & RAW DATA"
                                    AppLanguage.HINDI -> "ऐतिहासिक एक्यूरेसी दर व रॉ डेटा देखें"
                                    AppLanguage.MARATHI -> "ऐतिहासिक अचूकता दर व रॉ डेटा पहा"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Black,
                                color = GoldLight
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Inspect Pillar",
                            tint = GoldLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IndicatorRowItem(
    indicator: IndicatorItem,
    currentLanguage: AppLanguage
) {
    val sigColor = when (indicator.signal) {
        Signal.BUY -> SignalBuy
        Signal.SELL -> SignalSell
        Signal.WAIT -> SignalWait
    }

    val localizedSignal = when (indicator.signal) {
        Signal.BUY -> when (currentLanguage) {
            AppLanguage.ENGLISH -> "BUY"
            AppLanguage.HINDI -> "BUY"
            AppLanguage.MARATHI -> "BUY"
        }
        Signal.SELL -> when (currentLanguage) {
            AppLanguage.ENGLISH -> "SELL"
            AppLanguage.HINDI -> "SELL"
            AppLanguage.MARATHI -> "SELL"
        }
        Signal.WAIT -> when (currentLanguage) {
            AppLanguage.ENGLISH -> "WAIT"
            AppLanguage.HINDI -> "WAIT"
            AppLanguage.MARATHI -> "WAIT"
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = indicator.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = sigColor.copy(alpha = 0.15f),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = Brush.linearGradient(listOf(sigColor.copy(alpha = 0.8f), sigColor.copy(alpha = 0.2f)))
                )
            ) {
                Text(
                    text = localizedSignal,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Bold,
                    color = sigColor
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = indicator.detail,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = TextSecondary,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = indicator.valueDisplay,
                style = MaterialTheme.typography.labelMedium,
                color = GoldLight,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
