package com.example.livegoldai.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.localization.LocalizationStrings
import com.example.livegoldai.model.Signal
import com.example.livegoldai.model.TradeSetup
import com.example.livegoldai.theme.*
import java.util.Locale

@Composable
fun TradeSetupCard(
    setup: TradeSetup,
    onOpenCalculator: (stopLossPips: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val currentLanguage = LocalAppLanguage.current

    val signalColor = when (setup.signal) {
        Signal.BUY -> SignalBuy
        Signal.SELL -> SignalSell
        Signal.WAIT -> SignalWait
    }

    val signalBg = when (setup.signal) {
        Signal.BUY -> SignalBuyBg
        Signal.SELL -> SignalSellBg
        Signal.WAIT -> SignalWaitBg
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trade_setup_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(signalColor.copy(alpha = 0.5f), ObsidianBorder)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header Row: Title + Confidence + Signal Badge
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
                            AppLanguage.ENGLISH -> "ACTIONABLE TRADE SETUP"
                            AppLanguage.HINDI -> "ट्रेड सेटअप (एक्शन प्लान)"
                            AppLanguage.MARATHI -> "ट्रेड सेटअप (अ‍ॅक्शन प्लॅन)"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = GoldLight,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = signalBg,
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(signalColor, signalColor.copy(alpha = 0.3f))))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (setup.signal == Signal.BUY) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = signalColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val signalLabel = when (setup.signal) {
                            Signal.BUY -> when (currentLanguage) {
                                AppLanguage.ENGLISH -> "STRONG BUY"
                                AppLanguage.HINDI -> "मजबूत खरीदारी (BUY)"
                                AppLanguage.MARATHI -> "मजबूत खरेदी (BUY)"
                            }
                            Signal.SELL -> when (currentLanguage) {
                                AppLanguage.ENGLISH -> "STRONG SELL"
                                AppLanguage.HINDI -> "मजबूत बिकवाली (SELL)"
                                AppLanguage.MARATHI -> "मजबूत विक्री (SELL)"
                            }
                            Signal.WAIT -> when (currentLanguage) {
                                AppLanguage.ENGLISH -> "WAIT / NEUTRAL"
                                AppLanguage.HINDI -> "इंतजार करें (WAIT)"
                                AppLanguage.MARATHI -> "प्रतीक्षा करा (WAIT)"
                            }
                        }
                        Text(
                            text = signalLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = signalColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Grid of Setup Levels: Entry, SL, TP1, TP2
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SetupParamBox(
                    label = when (currentLanguage) {
                        AppLanguage.ENGLISH -> "ENTRY ZONE"
                        AppLanguage.HINDI -> "एंट्री ज़ोन"
                        AppLanguage.MARATHI -> "एंट्री झोन"
                    },
                    value = "$${String.format(Locale.US, "%.2f", setup.entryPrice)}",
                    subtext = when (currentLanguage) {
                        AppLanguage.ENGLISH -> "Market / Limit"
                        AppLanguage.HINDI -> "मार्केट / लिमिट"
                        AppLanguage.MARATHI -> "मार्केट / लिमिट"
                    },
                    accentColor = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                SetupParamBox(
                    label = when (currentLanguage) {
                        AppLanguage.ENGLISH -> "STOP LOSS (SL)"
                        AppLanguage.HINDI -> "स्टॉप लॉस (SL)"
                        AppLanguage.MARATHI -> "स्टॉप लॉस (SL)"
                    },
                    value = "$${String.format(Locale.US, "%.2f", setup.stopLoss)}",
                    subtext = "-${String.format(Locale.US, "%.0f", setup.stopLossPips)} Pips",
                    accentColor = SignalSell,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SetupParamBox(
                    label = when (currentLanguage) {
                        AppLanguage.ENGLISH -> "TAKE PROFIT 1 (TP1)"
                        AppLanguage.HINDI -> "टारगेट 1 (TP1)"
                        AppLanguage.MARATHI -> "टार्गेट 1 (TP1)"
                    },
                    value = "$${String.format(Locale.US, "%.2f", setup.takeProfit1)}",
                    subtext = "+${String.format(Locale.US, "%.0f", setup.takeProfit1Pips)} Pips (1:1)",
                    accentColor = SignalBuy,
                    modifier = Modifier.weight(1f)
                )
                SetupParamBox(
                    label = when (currentLanguage) {
                        AppLanguage.ENGLISH -> "TAKE PROFIT 2 (TP2)"
                        AppLanguage.HINDI -> "टारगेट 2 (TP2)"
                        AppLanguage.MARATHI -> "टार्गेट 2 (TP2)"
                    },
                    value = "$${String.format(Locale.US, "%.2f", setup.takeProfit2)}",
                    subtext = "+${String.format(Locale.US, "%.0f", setup.takeProfit2Pips)} Pips (${setup.riskRewardRatio})",
                    accentColor = GoldLight,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Badges Row: R:R ratio, Confidence, ATR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillBadge(label = "R:R", value = setup.riskRewardRatio, color = GoldPrimary)
                    PillBadge(
                        label = when (currentLanguage) {
                            AppLanguage.ENGLISH -> "CONFIDENCE"
                            AppLanguage.HINDI -> "कॉन्फिडेंस"
                            AppLanguage.MARATHI -> "विश्वास पातळी"
                        },
                        value = "${setup.confidencePercent}%",
                        color = signalColor
                    )
                }
                Text(
                    text = "ATR (14): ${String.format(Locale.US, "%.0f", setup.atrPips)} pips",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Strategy explanation note
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ObsidianSurfaceElevated,
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(ObsidianBorderHighlight, ObsidianBorder))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = setup.getStrategyNote(currentLanguage),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = TextSecondary,
                    modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Copy Plan + Open Lot Calculator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val text = """
                            🏆 KALANKAR FX GOLD PRO SETUP 🏆
                            Symbol: XAU/USD
                            Signal: ${setup.signal.label}
                            Entry: $${String.format(Locale.US, "%.2f", setup.entryPrice)}
                            Stop Loss: $${String.format(Locale.US, "%.2f", setup.stopLoss)} (-${String.format(Locale.US, "%.0f", setup.stopLossPips)} pips)
                            Take Profit 1: $${String.format(Locale.US, "%.2f", setup.takeProfit1)} (+${String.format(Locale.US, "%.0f", setup.takeProfit1Pips)} pips)
                            Take Profit 2: $${String.format(Locale.US, "%.2f", setup.takeProfit2)} (+${String.format(Locale.US, "%.0f", setup.takeProfit2Pips)} pips)
                            Risk:Reward: ${setup.riskRewardRatio} | Confluence: ${setup.confidencePercent}%
                            Analysis: ${setup.strategyNote}
                            (By Rudvay Ujjwal Kalankar)
                        """.trimIndent()
                        clipboardManager.setText(AnnotatedString(text))
                        val copiedToast = when (currentLanguage) {
                            AppLanguage.ENGLISH -> "Trade Setup copied to clipboard!"
                            AppLanguage.HINDI -> "ट्रेड सेटअप क्लिपबोर्ड पर कॉपी हो गया!"
                            AppLanguage.MARATHI -> "ट्रेड सेटअप क्लिपबोर्डवर कॉपी झाले!"
                        }
                        Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("copy_setup_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = GoldLight
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.linearGradient(listOf(GoldPrimary, GoldDark))
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Plan",
                        modifier = Modifier.size(16.dp),
                        tint = GoldLight
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (currentLanguage) {
                            AppLanguage.ENGLISH -> "Copy Plan"
                            AppLanguage.HINDI -> "प्लान कॉपी करें"
                            AppLanguage.MARATHI -> "प्लॅन कॉपी करा"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )
                }

                Button(
                    onClick = { onOpenCalculator(setup.stopLossPips) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("calc_position_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = ObsidianBackground
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = "Lot Calc",
                        modifier = Modifier.size(18.dp),
                        tint = ObsidianBackground
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (currentLanguage) {
                            AppLanguage.ENGLISH -> "Lot & Risk"
                            AppLanguage.HINDI -> "लॉट व रिस्क"
                            AppLanguage.MARATHI -> "लॉट व जोखीम"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = ObsidianBackground
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupParamBox(
    label: String,
    value: String,
    subtext: String,
    accentColor: Color,
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
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = TextMuted,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtext,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun PillBadge(
    label: String,
    value: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(listOf(color.copy(alpha = 0.4f), color.copy(alpha = 0.1f)))
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = TextMuted
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
