package com.example.livegoldai.ui.components

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
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
import java.util.Locale

/**
 * 🌟 SIMPLE / EASY TRADING VIEW (सरल व व्यवस्थित दृश्य)
 * Designed specifically to eliminate confusion and present a clean,
 * 1-glance trading setup:
 * 1. Live Gold Price & Day Change
 * 2. Big Clear Signal & Trade Action (Entry, SL, TP1, TP2)
 * 3. 1-Tap Lot Calculator & "Kya Hoga?" AI Forecast
 * 4. Clean Candlestick Chart & Key Levels
 * 5. 7-Pillars Quick Health Check (Tap for Deep-Dive)
 * 6. Real-time Buyer vs Seller Depth
 */
@Composable
fun SimpleEasyTradingView(
    analysis: GoldAnalysisResult,
    selectedInterval: String,
    onIntervalSelected: (String) -> Unit,
    onOpenCalculator: (Double) -> Unit,
    onOpenPredictionDialog: () -> Unit,
    onPillarClick: (GroupAnalysis) -> Unit,
    onSwitchToProMode: () -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLanguage = LocalAppLanguage.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val signalColor by animateColorAsState(
        targetValue = when (analysis.overallSignal) {
            Signal.BUY -> SignalBuy
            Signal.SELL -> SignalSell
            Signal.WAIT -> SignalWait
        },
        label = "easySignalColor"
    )

    val signalBg = when (analysis.overallSignal) {
        Signal.BUY -> SignalBuyBg
        Signal.SELL -> SignalSellBg
        Signal.WAIT -> SignalWaitBg
    }

    val signalBorder = when (analysis.overallSignal) {
        Signal.BUY -> SignalBuyBorder
        Signal.SELL -> SignalSellBorder
        Signal.WAIT -> SignalWaitBorder
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("simple_easy_trading_view"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. SPOT PRICE & TIME FRAME BAR ---
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = ObsidianSurfaceCard,
            border = BorderStroke(1.2.dp, ObsidianBorderHighlight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Top row: Symbol & Timeframe pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GoldPrimary.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "XAU/USD",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = GoldLight
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (currentLanguage) {
                                AppLanguage.ENGLISH -> "Gold Spot"
                                AppLanguage.HINDI -> "सोना (लाइव)"
                                AppLanguage.MARATHI -> "सोने (लाईव्ह)"
                            },
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }

                    // Timeframe Chips (15m, 1h, 4h, 1d)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("15m", "1h", "4h", "1d").forEach { tf ->
                            val isSelected = selectedInterval.lowercase() == tf.lowercase()
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) GoldPrimary else ObsidianSurfaceElevated,
                                border = if (isSelected) null else BorderStroke(1.dp, ObsidianBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onIntervalSelected(tf) }
                            ) {
                                Text(
                                    text = tf.uppercase(),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                    color = if (isSelected) Color.Black else TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Price & 24h Change Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", analysis.currentPrice)}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isUp = analysis.priceChange >= 0
                            val changeColor = if (isUp) SignalBuy else SignalSell
                            val arrow = if (isUp) "▲" else "▼"
                            Text(
                                text = "$arrow $${String.format(Locale.US, "%.2f", kotlin.math.abs(analysis.priceChange))} (${String.format(Locale.US, "%+.2f", analysis.priceChangePercent)}%)",
                                color = changeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "24h",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Quick Refresh Button
                    IconButton(
                        onClick = onRefreshClick,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ObsidianSurfaceElevated)
                            .border(1.dp, ObsidianBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = GoldLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // --- 2. HERO CLEAR SIGNAL & TRADE SETUP CARD (ZERO CONFUSION) ---
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = ObsidianSurfaceCard,
            border = BorderStroke(1.5.dp, signalBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Section Title & 1-glance guidance
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
                            text = when (currentLanguage) {
                                AppLanguage.ENGLISH -> "RECOMMENDED ACTION"
                                AppLanguage.HINDI -> "स्पष्ट ट्रेडिंग सुझाव"
                                AppLanguage.MARATHI -> "स्पष्ट ट्रेडिंग सल्ला"
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp,
                            color = GoldLight
                        )
                    }

                    // Confidence Pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = signalColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, signalColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "${analysis.agreementPercent.toInt()}% CONFIDENCE",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = signalColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // BIG VERDICT CALLOUT
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = signalBg,
                    border = BorderStroke(1.dp, signalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val signalTitle = when (analysis.overallSignal) {
                                Signal.BUY -> when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "STRONG BUY 🟢"
                                    AppLanguage.HINDI -> "मजबूत खरीदारी (BUY) 🟢"
                                    AppLanguage.MARATHI -> "मजबूत खरेदी (BUY) 🟢"
                                }
                                Signal.SELL -> when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "STRONG SELL 🔴"
                                    AppLanguage.HINDI -> "मजबूत बिकवाली (SELL) 🔴"
                                    AppLanguage.MARATHI -> "मजबूत विक्री (SELL) 🔴"
                                }
                                Signal.WAIT -> when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "WAIT & WATCH 🟡"
                                    AppLanguage.HINDI -> "इंतजार करें (WAIT) 🟡"
                                    AppLanguage.MARATHI -> "प्रतीक्षा करा (WAIT) 🟡"
                                }
                            }
                            Text(
                                text = signalTitle,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = signalColor
                            )
                            Text(
                                text = when (analysis.overallSignal) {
                                    Signal.BUY -> when (currentLanguage) {
                                        AppLanguage.ENGLISH -> "High probability upside continuation from support."
                                        AppLanguage.HINDI -> "सपोर्ट ज़ोन से ऊपर जाने की बहुत अधिक संभावना।"
                                        AppLanguage.MARATHI -> "सपोर्ट झोनमधून वर जाण्याची उच्च शक्यता."
                                    }
                                    Signal.SELL -> when (currentLanguage) {
                                        AppLanguage.ENGLISH -> "Strong downward pressure from resistance."
                                        AppLanguage.HINDI -> "रेजिस्टेंस ज़ोन से भारी गिरावट का दबाव।"
                                        AppLanguage.MARATHI -> "रेझिस्टन्स झोनमधून मोठ्या घसरणीचा दबाव."
                                    }
                                    Signal.WAIT -> when (currentLanguage) {
                                        AppLanguage.ENGLISH -> "Market is in range. Wait for breakout."
                                        AppLanguage.HINDI -> "मार्केट रेंज में है। ब्रेकआउट की प्रतीक्षा करें।"
                                        AppLanguage.MARATHI -> "बाजार रेंजमध्ये आहे. ब्रेकआउटची वाट पहा."
                                    }
                                },
                                fontSize = 11.sp,
                                color = TextPrimary.copy(alpha = 0.9f)
                            )
                        }

                        // Copy levels button
                        IconButton(
                            onClick = {
                                val tradeText = "XAU/USD Setup: ${analysis.overallSignal.name} @ $${String.format(Locale.US, "%.2f", analysis.tradeSetup.entryZoneStart)} | SL: $${String.format(Locale.US, "%.2f", analysis.tradeSetup.stopLoss)} | TP1: $${String.format(Locale.US, "%.2f", analysis.tradeSetup.tp1)} | TP2: $${String.format(Locale.US, "%.2f", analysis.tradeSetup.tp2)}"
                                clipboardManager.setText(AnnotatedString(tradeText))
                                Toast.makeText(context, "Trade Setup Copied! 📋", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ObsidianSurfaceElevated)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Levels",
                                tint = GoldLight,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4 KEY LEVELS IN CLEAN 2x2 GRID
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Entry Level
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(1.dp, ObsidianBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "🎯 ENTRY ZONE"
                                    AppLanguage.HINDI -> "🎯 एंट्री लेवल्स"
                                    AppLanguage.MARATHI -> "🎯 एन्ट्री लेव्हल्स"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "$${String.format(Locale.US, "%.1f", analysis.tradeSetup.entryZoneStart)} - $${String.format(Locale.US, "%.1f", analysis.tradeSetup.entryZoneEnd)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = GoldLight,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Stop Loss
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SignalSell.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, SignalSell.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "🛑 STOP LOSS"
                                    AppLanguage.HINDI -> "🛑 स्टॉप लॉस"
                                    AppLanguage.MARATHI -> "🛑 स्टॉप लॉस"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SignalSell
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", analysis.tradeSetup.stopLoss)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = SignalSell,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Target 1
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SignalBuy.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, SignalBuy.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "🚀 TARGET 1"
                                    AppLanguage.HINDI -> "🚀 टारगेट 1"
                                    AppLanguage.MARATHI -> "🚀 टार्गेट 1"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SignalBuy
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", analysis.tradeSetup.tp1)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = SignalBuy,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Target 2
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SignalBuy.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, SignalBuy.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "🏆 TARGET 2"
                                    AppLanguage.HINDI -> "🏆 टारगेट 2"
                                    AppLanguage.MARATHI -> "🏆 टार्गेट 2"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SignalBuy
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", analysis.tradeSetup.tp2)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = SignalBuy,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // TWO LARGE ACTION BUTTONS (LOT CALCULATOR & AI FORECAST)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Lot Calculator Button
                    Button(
                        onClick = { onOpenCalculator(analysis.tradeSetup.stopLossPips) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ObsidianSurfaceElevated,
                            contentColor = GoldLight
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.2.dp, GoldPrimary.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("easy_lot_calculator_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = GoldLight
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (currentLanguage) {
                                AppLanguage.ENGLISH -> "Safe Lot Size"
                                AppLanguage.HINDI -> "लॉट साइज निकालें"
                                AppLanguage.MARATHI -> "लॉट साइज काढा"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // "Kya Hoga?" AI Prediction Button
                    Button(
                        onClick = onOpenPredictionDialog,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("easy_kya_hoga_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (currentLanguage) {
                                AppLanguage.ENGLISH -> "Next Move AI"
                                AppLanguage.HINDI -> "आगे क्या होगा?"
                                AppLanguage.MARATHI -> "पुढे काय होईल?"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        // --- 3. CLEAN CANDLESTICK CHART ---
        ProCandleChart(
            candles = analysis.recentCandles,
            buyerSellerRatio = analysis.buyerSellerRatio,
            tradeSetup = analysis.tradeSetup,
            pivotLevels = analysis.pivotLevels,
            currentPrice = analysis.currentPrice
        )

        // --- 4. 7-PILLARS QUICK HEALTH CHECK (INTERACTIVE - TAP FOR DEEP-DIVE) ---
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = ObsidianSurfaceCard,
            border = BorderStroke(1.2.dp, ObsidianBorderHighlight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🏛️", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "7-PILLARS CONSENSUS"
                                    AppLanguage.HINDI -> "7 मुख्य इंडिकेटर पिलर्स"
                                    AppLanguage.MARATHI -> "7 मुख्य इंडिकेटर पिलर्स"
                                },
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = when (currentLanguage) {
                                AppLanguage.ENGLISH -> "Tap any pillar for full deep-dive & telemetry"
                                AppLanguage.HINDI -> "विस्तृत ऑडिट देखने के लिए किसी भी पिलर पर टैप करें"
                                AppLanguage.MARATHI -> "सविस्तर ऑडिट पाहण्यासाठी कोणत्याही पिलरवर टॅप करा"
                            },
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = GoldPrimary.copy(alpha = 0.15f),
                        border = BorderStroke(0.8.dp, GoldPrimary.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "${analysis.groups.count { it.verdict == analysis.overallSignal }}/7 Agree",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 7 Pillar Items (Neat and Clean List)
                analysis.groups.forEachIndexed { index, group ->
                    val pillColor = when (group.verdict) {
                        Signal.BUY -> SignalBuy
                        Signal.SELL -> SignalSell
                        Signal.WAIT -> SignalWait
                    }
                    val pillIcon = when (group.id) {
                        "trend" -> "📈"
                        "momentum" -> "⚡"
                        "smc" -> "🏦"
                        "support_resistance" -> "🎯"
                        "volatility" -> "📊"
                        "candlestick" -> "🕯️"
                        "macro" -> "🌐"
                        else -> "🔍"
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(0.8.dp, ObsidianBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPillarClick(group) }
                            .testTag("easy_pillar_${group.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = pillIcon, fontSize = 15.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = group.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${group.indicators.size} indicators active • ${group.indicators.take(2).joinToString { it.name }}",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = pillColor.copy(alpha = 0.15f),
                                    border = BorderStroke(0.8.dp, pillColor.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = group.verdict.name,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = pillColor
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "View Deep Dive",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 5. BUYER VS SELLER DEPTH CARD ---
        analysis.buyerSellerRatio?.let { bs ->
            BuyerSellerDepthCard(
                sentiment = bs,
                currentPrice = analysis.currentPrice
            )
        }

        // --- 6. SWITCH TO PRO MODE HINT CARD ---
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ObsidianSurfaceElevated,
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onSwitchToProMode() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⚡", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = when (currentLanguage) {
                                AppLanguage.ENGLISH -> "Switch to ⚡ Pro Cockpit"
                                AppLanguage.HINDI -> "⚡ विस्तृत प्रो कॉकपिट पर जाएं"
                                AppLanguage.MARATHI -> "⚡ सविस्तर प्रो कॉकपिटवर जा"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = GoldLight
                        )
                        Text(
                            text = when (currentLanguage) {
                                AppLanguage.ENGLISH -> "Multi-AI Council, News Radar, SMC Blocks & 32 Indicators"
                                AppLanguage.HINDI -> "मल्टी-AI काउंसिल, न्यूज़ रडार, स्मार्ट मनी व 32 इंडिकेटर्स"
                                AppLanguage.MARATHI -> "मल्टी-AI कौन्सिल, न्यूज रडार, स्मार्ट मनी व 32 इंडिकेटर्स"
                            },
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Switch to Pro",
                    tint = GoldPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
