package com.example.livegoldai.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.model.QuantBotTradeSignal
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*
import java.util.Locale

/**
 * 🤖 KALANKAR QUANT AI BOT v6.0 CARD
 * Displays automated institutional bot signals, live order dispatch limits,
 * anti-chop capital preservation standby, and auto-breakeven rules.
 */
@Composable
fun QuantBotCard(
    botSignal: QuantBotTradeSignal?,
    onOpenCalculator: (Double) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (botSignal == null) return

    val currentLanguage = LocalAppLanguage.current
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(true) }

    val signalColor by animateColorAsState(
        targetValue = when (botSignal.signal) {
            Signal.BUY -> SignalBuy
            Signal.SELL -> SignalSell
            Signal.WAIT -> SignalWait
        },
        label = "botSignalColor"
    )

    val signalBg = when (botSignal.signal) {
        Signal.BUY -> SignalBuyBg
        Signal.SELL -> SignalSellBg
        Signal.WAIT -> SignalWaitBg
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quant_bot_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceCard),
        border = BorderStroke(
            1.5.dp,
            Brush.linearGradient(
                listOf(
                    if (botSignal.isTradeActive) signalColor else GoldPrimary.copy(alpha = 0.8f),
                    ObsidianBorderHighlight,
                    GoldPrimary.copy(alpha = 0.3f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: Bot Avatar + Name + Status Pill + Expand Arrow
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GoldPrimary.copy(alpha = 0.15f))
                            .border(1.2.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🤖", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = botSignal.botName,
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = GoldLight
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = GoldPrimary.copy(alpha = 0.2f),
                                border = BorderStroke(0.6.dp, GoldPrimary)
                            ) {
                                Text(
                                    text = "${botSignal.winProbabilityPercent}% WIN RATE",
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = GoldLight
                                )
                            }
                        }
                        Text(
                            text = when (currentLanguage) {
                                AppLanguage.ENGLISH -> "High-Accuracy Confluence Algorithmic Bot"
                                AppLanguage.HINDI -> "उच्च-सटीकता क्वांट ट्रेडिंग बॉट (सटीक सिग्नल)"
                                AppLanguage.MARATHI -> "उच्च-अचूकता क्वांट ट्रेडिंग बॉट"
                            },
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status Banner: Live Order Dispatched vs Capital Preservation Standby
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = signalBg,
                border = BorderStroke(1.dp, signalColor.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(signalColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = botSignal.statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = signalColor
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianSurfaceCard,
                        border = BorderStroke(0.8.dp, signalColor)
                    ) {
                        Text(
                            text = botSignal.signal.name,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = signalColor
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(14.dp))

                    if (botSignal.isTradeActive) {
                        // --- 1. DIRECT COPYABLE EXECUTION COMMAND ---
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianSurfaceElevated,
                            border = BorderStroke(1.dp, signalColor.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "MT4 / MT5 EXECUTION COMMAND"
                                            AppLanguage.HINDI -> "सीधा ब्रोकर ऑर्डर कमांड (MT4 / MT5)"
                                            AppLanguage.MARATHI -> "थेट ब्रोकर ऑर्डर कमांड (MT4 / MT5)"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextMuted
                                    )

                                    // Copy Button
                                    Button(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Bot Command", botSignal.executionCommandEng)
                                            clipboard.setPrimaryClip(clip)
                                            val msg = when (currentLanguage) {
                                                AppLanguage.ENGLISH -> "Bot order command copied!"
                                                AppLanguage.HINDI -> "बॉट ऑर्डर कमांड कॉपी हो गया!"
                                                AppLanguage.MARATHI -> "बॉट ऑर्डर कमांड कॉपी झाले!"
                                            }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = GoldPrimary.copy(alpha = 0.2f),
                                            contentColor = GoldLight
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = GoldLight
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = when (currentLanguage) {
                                                AppLanguage.ENGLISH -> "COPY"
                                                AppLanguage.HINDI -> "कॉपी"
                                                AppLanguage.MARATHI -> "कॉपी"
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = botSignal.executionCommandEng,
                                        modifier = Modifier.padding(10.dp),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = GoldLight
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // --- 2. 2x2 PARAMETERS GRID ---
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Entry Price
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianSurfaceElevated,
                                border = BorderStroke(0.8.dp, ObsidianBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "🎯 LIMIT ENTRY"
                                            AppLanguage.HINDI -> "🎯 लिमिट एंट्री"
                                            AppLanguage.MARATHI -> "🎯 लिमिट एंट्री"
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextMuted
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$${String.format(Locale.US, "%.2f", botSignal.entryPrice)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "50% FVG Discount",
                                        fontSize = 9.sp,
                                        color = SignalBuy
                                    )
                                }
                            }

                            // Stop Loss with Anti-Wick Protection
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianSurfaceElevated,
                                border = BorderStroke(0.8.dp, ObsidianBorder),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "🛑 STOP LOSS"
                                            AppLanguage.HINDI -> "🛑 स्टॉप लॉस"
                                            AppLanguage.MARATHI -> "🛑 स्टॉप लॉस"
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SignalSell
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$${String.format(Locale.US, "%.2f", botSignal.stopLoss)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = SignalSell
                                    )
                                    Text(
                                        text = "-${String.format(Locale.US, "%.1f", botSignal.pipsRisk)} Pips (Wick Shield)",
                                        fontSize = 9.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Target 1
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SignalBuy.copy(alpha = 0.08f),
                                border = BorderStroke(0.8.dp, SignalBuy.copy(alpha = 0.35f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "🚀 TARGET 1 (TP1)"
                                            AppLanguage.HINDI -> "🚀 टारगेट 1 (TP1)"
                                            AppLanguage.MARATHI -> "🚀 टार्गेट 1 (TP1)"
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SignalBuy
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$${String.format(Locale.US, "%.2f", botSignal.takeProfit1)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = SignalBuy
                                    )
                                    Text(
                                        text = "+${String.format(Locale.US, "%.1f", botSignal.pipsRewardTp1)} Pips",
                                        fontSize = 9.sp,
                                        color = SignalBuy
                                    )
                                }
                            }

                            // Target 2 (Runner)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SignalBuy.copy(alpha = 0.08f),
                                border = BorderStroke(0.8.dp, SignalBuy.copy(alpha = 0.35f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "🏆 TARGET 2 (TP2)"
                                            AppLanguage.HINDI -> "🏆 टारगेट 2 (TP2)"
                                            AppLanguage.MARATHI -> "🏆 टार्गेट 2 (TP2)"
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SignalBuy
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "$${String.format(Locale.US, "%.2f", botSignal.takeProfit2)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        color = SignalBuy
                                    )
                                    Text(
                                        text = "+${String.format(Locale.US, "%.1f", botSignal.pipsRewardTp2)} Pips",
                                        fontSize = 9.sp,
                                        color = SignalBuy
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // --- 3. AUTO-BREAKEVEN SHIELD ---
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianSurfaceElevated,
                            border = BorderStroke(0.8.dp, ObsidianBorderHighlight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "🔒", fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = botSignal.getBreakevenRule(currentLanguage),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                            }
                        }
                    } else {
                        // --- BOT STANDBY / CHOP FILTER EXPLANATION ---
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianSurfaceElevated,
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = "🛡️", fontSize = 18.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = when (currentLanguage) {
                                            AppLanguage.ENGLISH -> "CAPITAL PROTECTION PROTOCOL ACTIVE"
                                            AppLanguage.HINDI -> "पूंजी सुरक्षा प्रोटोकॉल सक्रिय (नो-ट्रेड ज़ोन)"
                                            AppLanguage.MARATHI -> "भांडवल संरक्षण प्रोटोकॉल सक्रिय"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = GoldLight
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = botSignal.getBotReasoning(currentLanguage),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = TextPrimary
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.Black.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "💡", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = when (currentLanguage) {
                                                AppLanguage.ENGLISH -> "Why standby? Avoids 50/50 chop losses. The bot only trades when high-conviction institutional confluence is ≥75%."
                                                AppLanguage.HINDI -> "स्टैंडबाय क्यों? चॉपी मार्केट में बार-बार SL कटने से बचाता है। बॉट केवल तभी ट्रेड लगाता है जब 75%+ कन्फर्मेशन हो।"
                                                AppLanguage.MARATHI -> "स्टँडबाय का? चॉपी मार्केटमध्ये तोटा टाळतो. बॉट फक्त 75%+ खात्री असतानाच ट्रेड करतो."
                                            },
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // --- 4. ACCURACY PILLARS SUMMARY (WHY THIS SETUP IS ACCURATE) ---
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.35f),
                        border = BorderStroke(0.6.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "HIGH-ACCURACY ENGINE SAFEGUARDS"
                                    AppLanguage.HINDI -> "सटीक प्रेडिक्शन के 4 सुरक्षा नियम"
                                    AppLanguage.MARATHI -> "अचूक अंदाजाचे 4 सुरक्षा नियम"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = GoldLight
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val rules = when (currentLanguage) {
                                AppLanguage.ENGLISH -> listOf(
                                    "1. Trend-Filtered Oscillators: Prevents false sells during rallies",
                                    "2. 5-AI Council Consensus: Gemini + ChatGPT + Claude + DeepSeek + Perplexity",
                                    "3. Anti-Wick Buffer: SL placed +3.5 pips outside bank liquidity reach",
                                    "4. Pullback Limit Order: Never buys candle peaks or sells bottoms"
                                )
                                AppLanguage.HINDI -> listOf(
                                    "1. ट्रेंड-फिल्टर्ड इंडिकेटर्स: तेजी के दौरान झूठे SELL सिग्नल्स पूरी तरह बंद",
                                    "2. 5-AI काउंसिल सहमति: Gemini, ChatGPT, Claude, DeepSeek व Perplexity",
                                    "3. एंटी-विक शील्ड: बैंकों के स्टॉप हंट से +3.5 pips बाहर सुरक्षित SL",
                                    "4. पुलबैक लिमिट एंट्री: कभी भी शिखर पर खरीदारी या तली पर बिकवाली नहीं"
                                )
                                AppLanguage.MARATHI -> listOf(
                                    "1. ट्रेंड-फिल्टर्ड इंडिकेटर्स: तेजीमध्ये खोटे SELL सिग्नल्स बंद",
                                    "2. 5-AI कौन्सिल सहमती: सर्व AI मॉडेल्सची पडताळणी",
                                    "3. अँटी-विक शील्ड: स्टॉप हंटपासून +3.5 pips बाहेर सुरक्षित SL",
                                    "4. पुलबॅक लिमिट एंट्री: शिखरावर खरेदी किंवा तळाला विक्री नाही"
                                )
                            }
                            rules.forEach { rule ->
                                Text(
                                    text = rule,
                                    fontSize = 10.sp,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
