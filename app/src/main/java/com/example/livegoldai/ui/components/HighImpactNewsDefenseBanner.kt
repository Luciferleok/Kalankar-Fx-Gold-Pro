package com.example.livegoldai.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.model.EconomicEvent
import com.example.livegoldai.model.Signal
import com.example.livegoldai.notification.PreNewsAlertNotificationManager
import com.example.livegoldai.theme.*

/**
 * 🚨 HIGH-IMPACT NEWS DEFENSE BANNER
 * Automatically activates on the dashboard when high-impact USD economic releases
 * (CPI, NFP, FOMC, PPI, Fed Rate) are approaching.
 * Prominently features India Time (IST), time remaining, defense status, and instant notification test.
 */
@Composable
fun HighImpactNewsDefenseBanner(
    event: EconomicEvent?,
    currentPrice: Double,
    isNewsModeActive: Boolean,
    onOpenNewsPlan: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (event == null) return

    val currentLang = LocalAppLanguage.current
    val context = LocalContext.current
    var isDismissed by remember { mutableStateOf(false) }

    if (isDismissed) return

    val infiniteTransition = rememberInfiniteTransition(label = "news_defense_pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    val alertRed = Color(0xFFFF1744)
    val alertAmber = Color(0xFFFF9100)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurfaceCard,
        border = BorderStroke(
            1.8.dp,
            Brush.linearGradient(
                listOf(
                    alertRed.copy(alpha = pulseGlow),
                    alertAmber.copy(alpha = pulseGlow),
                    GoldPrimary.copy(alpha = 0.5f)
                )
            )
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("high_impact_news_defense_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header: Warning Badge + India Flag + Close Button
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
                            .background(alertRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (currentLang) {
                            AppLanguage.ENGLISH -> "🚨 HIGH-IMPACT NEWS DEFENSE MODE"
                            AppLanguage.HINDI -> "🚨 हाई-इम्पैक्ट न्यूज़ सुरक्षा अलर्ट"
                            AppLanguage.MARATHI -> "🚨 हाय-इम्पॅक्ट न्यूज सुरक्षा अलर्ट"
                        },
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Black,
                        color = alertRed,
                        letterSpacing = 0.5.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = alertRed.copy(alpha = 0.15f),
                    border = BorderStroke(0.6.dp, alertRed)
                ) {
                    Text(
                        text = "HIGH IMPACT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = alertRed,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Event Title
            Text(
                text = event.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Dual Time Display: INDIA TIME (IST) Prominent + UTC
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = ObsidianSurfaceElevated,
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // India Time
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🇮🇳", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "INDIA TIME (IST)"
                                    AppLanguage.HINDI -> "भारत समय (IST)"
                                    AppLanguage.MARATHI -> "भारतीय वेळ (IST)"
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldLight
                            )
                            Text(
                                text = event.getIndiaTimeFormatted().ifBlank { "08:30 PM IST" },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                        }
                    }

                    // UTC Time
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "GLOBAL TIME",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted
                        )
                        Text(
                            text = "${event.date} • ${event.time}",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Defensive Status Text
            Text(
                text = when (currentLang) {
                    AppLanguage.ENGLISH -> "🛡️ Spread Guard Armed: Wider anti-slippage stop loss (+3.5 pips) & straddle pending orders ready."
                    AppLanguage.HINDI -> "🛡️ स्प्रेड शील्ड सक्रिय: अनपेक्षित विक्स से बचने के लिए +3.5 पिप्स अतिरिक्त SL बफर और स्ट्रैडल ऑर्डर्स तैयार हैं।"
                    AppLanguage.MARATHI -> "🛡️ स्प्रेड शील्ड सक्रिय: अनपेक्षित हालचाली टाळण्यासाठी अतिरिक्त SL बफर तयार आहे."
                },
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = alertAmber
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons: Test Notification + Open News Plan
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button 1: Test Notification (Immediate Phone Status Bar Verification)
                OutlinedButton(
                    onClick = {
                        val sent = PreNewsAlertNotificationManager.triggerInstantTestAlert(
                            context = context,
                            eventTitle = event.title,
                            indiaTime = event.getIndiaTimeFormatted(),
                            currentPrice = currentPrice,
                            lang = currentLang
                        )
                        if (sent) {
                            Toast.makeText(context, "🔔 Pre-News Notification sent to phone status bar!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please allow notification permission in phone settings", Toast.LENGTH_LONG).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.8f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = GoldPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (currentLang) {
                            AppLanguage.ENGLISH -> "TEST NOTIF 🔔"
                            AppLanguage.HINDI -> "अलर्ट टेस्ट करें 🔔"
                            AppLanguage.MARATHI -> "अलर्ट तपासा 🔔"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Button 2: Open News Trading Plan
                Button(
                    onClick = onOpenNewsPlan,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = alertRed),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (currentLang) {
                            AppLanguage.ENGLISH -> "STRADDLE PLAN"
                            AppLanguage.HINDI -> "स्ट्रैडल प्लान देखें"
                            AppLanguage.MARATHI -> "स्ट्रॅडल प्लॅन पहा"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}
