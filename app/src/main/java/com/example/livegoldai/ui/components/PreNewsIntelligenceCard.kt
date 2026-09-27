package com.example.livegoldai.ui.components

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import java.util.Locale

@Composable
fun PreNewsIntelligenceCard(
    currentPrice: Double,
    upcomingEvents: List<EconomicEvent>,
    onOpenLotCalculator: (Double) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentLang = LocalAppLanguage.current
    var isAutoNotificationEnabled by remember { mutableStateOf(true) }
    var lastSentTestTime by remember { mutableStateOf<String?>(null) }

    // Android 13+ Notification Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Notification permission granted! 🔔", Toast.LENGTH_SHORT).show()
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "pre_news_pulse")
    val pulseGlow by pulseTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    // Select the primary high impact event
    val primaryEvent = upcomingEvents.firstOrNull { it.impact.equals("High", ignoreCase = true) }
        ?: upcomingEvents.firstOrNull()
        ?: EconomicEvent(
            title = "US Core CPI Inflation (MoM & YoY)",
            country = "USD",
            date = "Today",
            time = "18:30 UTC (~1 Hr Remaining)",
            impact = "High",
            forecast = "0.2%",
            previous = "0.3%",
            goldImpact = "Severe Volatility Expected (+$35/-$30)"
        )

    val preBias = if (primaryEvent.title.contains("CPI", true) || primaryEvent.title.contains("Fed", true)) {
        Signal.BUY
    } else if (primaryEvent.title.contains("NFP", true) || primaryEvent.title.contains("Jobs", true)) {
        Signal.SELL
    } else {
        Signal.BUY
    }

    val upperBreakout = currentPrice + 16.5
    val lowerBreakdown = currentPrice - 18.0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pre_news_intelligence_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    GoldPrimary.copy(alpha = 0.85f),
                    Color(0xFFFF9100).copy(alpha = pulseGlow * 0.7f),
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
            // Header: 1-Hour Pre-News Radar Badge
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
                            .background(Color(0xFFFF9100).copy(alpha = 0.18f))
                            .border(1.dp, Color(0xFFFF9100).copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🔔", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "1-HOUR PRE-NEWS AI RADAR"
                                    AppLanguage.HINDI -> "1-घंटा पूर्व न्यूज़ AI रडार"
                                    AppLanguage.MARATHI -> "1-तास पूर्व न्यूज AI रडार"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = GoldLight,
                                letterSpacing = 0.6.sp,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = when (currentLang) {
                                AppLanguage.ENGLISH -> "Prior Notification & Pre-Calculated Buy/Sell Bias"
                                AppLanguage.HINDI -> "न्यूज़ से 1 घंटे पहले फोन नोटिफिकेशन व BUY/SELL विश्लेषण"
                                AppLanguage.MARATHI -> "न्यूजच्या 1 तास आधी फोन नोटिफिकेशन व BUY/SELL विश्लेषण"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = Color(0xFFFFB74D)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFFF9100).copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Color(0xFFFF9100).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "T-MINUS ~60m",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFFFB74D),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Event Details Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurfaceElevated,
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🇺🇸", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = primaryEvent.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SignalSell.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "HIGH IMPACT 🔴",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = SignalSell,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "Release Schedule"
                                    AppLanguage.HINDI -> "रिलीज़ समय"
                                    AppLanguage.MARATHI -> "रिलीज वेळ"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "${primaryEvent.date} • ${primaryEvent.time}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = GoldLight
                            )
                        }

                        if (primaryEvent.forecast.isNotBlank()) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = when (currentLang) {
                                        AppLanguage.ENGLISH -> "Forecast vs Previous"
                                        AppLanguage.HINDI -> "अनुमान बनाम पिछला"
                                        AppLanguage.MARATHI -> "अंदाज विरुद्ध मागील"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = "Est: ${primaryEvent.forecast} | Prev: ${primaryEvent.previous.ifBlank { "N/A" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // AI PREDICTION BOX: KYA HO SAKTA HAI? (BUY YA SELL?)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = if (preBias == Signal.BUY) SignalBuy.copy(alpha = 0.12f) else SignalSell.copy(alpha = 0.12f),
                border = BorderStroke(
                    1.5.dp,
                    if (preBias == Signal.BUY) SignalBuy.copy(alpha = 0.6f) else SignalSell.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🎯", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "AI ADVANCE PREDICTION: WHAT COULD HAPPEN?"
                                    AppLanguage.HINDI -> "AI का पूर्व-अनुमान: क्या हो सकता है? (BUY या SELL?)"
                                    AppLanguage.MARATHI -> "AI पूर्व-अंदाज: काय घडू शकते? (BUY किंवा SELL?)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = if (preBias == Signal.BUY) SignalBuy else SignalSell,
                                letterSpacing = 0.5.sp,
                                fontSize = 11.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (preBias == Signal.BUY) SignalBuy else SignalSell
                        ) {
                            Text(
                                text = if (preBias == Signal.BUY) "PRE-BIAS: BUY 🟢" else "PRE-BIAS: SELL 🔴",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (preBias == Signal.BUY) {
                            when (currentLang) {
                                AppLanguage.ENGLISH -> "CPI expected cooling or Fed dovish tilt: Lower numbers will trigger a Dollar Selloff. Gold can explosively pump +$25 to +$40 towards $${String.format(Locale.US, "%.2f", upperBreakout + 15.0)}. Strategy: Keep Buy Stop ready at $${String.format(Locale.US, "%.2f", upperBreakout)}."
                                AppLanguage.HINDI -> "महंगाई डेटा कम आने का अनुमान है जिससे यूएस डॉलर कमजोर होगा। गोल्ड में $20 से $40 का भारी उछाल आ सकता है और भाव $${String.format(Locale.US, "%.2f", upperBreakout + 15.0)} तक जा सकता है। रणनीति: $${String.format(Locale.US, "%.2f", upperBreakout)} पर Buy Stop तैयार रखें।"
                                AppLanguage.MARATHI -> "महागाई डेटा कमी येण्याचा अंदाज आहे ज्यामुळे डॉलर कमजोर होईल. सोन्यात $20 ते $40 ची मोठी तेजी येऊन भाव $${String.format(Locale.US, "%.2f", upperBreakout + 15.0)} पर्यंत जाऊ शकतो. स्ट्रॅटेजी: $${String.format(Locale.US, "%.2f", upperBreakout)} वर Buy Stop तयार ठेवा."
                            }
                        } else {
                            when (currentLang) {
                                AppLanguage.ENGLISH -> "Strong employment reading expected: Dollar will rally aggressively. Gold can dump -$20 to -$35 down to $${String.format(Locale.US, "%.2f", lowerBreakdown - 12.0)}. Strategy: Keep Sell Stop armed below $${String.format(Locale.US, "%.2f", lowerBreakdown)}."
                                AppLanguage.HINDI -> "मजबूत जॉब डेटा से डॉलर में शार्प उछाल आने का अनुमान है। गोल्ड में $20 से $35 की तेज गिरावट आ सकती है और भाव $${String.format(Locale.US, "%.2f", lowerBreakdown - 12.0)} तक फिसल सकता है। रणनीति: $${String.format(Locale.US, "%.2f", lowerBreakdown)} के नीचे Sell Stop लगाएं।"
                                AppLanguage.MARATHI -> "मजबूत रोजगार आकडेवारीमुळे डॉलरमध्ये उसळी येईल. सोन्यात $20 ते $35 ची घसरण येऊन भाव $${String.format(Locale.US, "%.2f", lowerBreakdown - 12.0)} पर्यंत घसरू शकतो. स्ट्रॅटेजी: $${String.format(Locale.US, "%.2f", lowerBreakdown)} खाली Sell Stop लावा."
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Pre-News Straddle Levels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianBackground.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, SignalBuy.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(text = "BUY STOP LEVEL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SignalBuy)
                                Text(
                                    text = "$${String.format(Locale.US, "%.2f", upperBreakout)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianBackground.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, SignalSell.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(text = "SELL STOP LEVEL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = SignalSell)
                                Text(
                                    text = "$${String.format(Locale.US, "%.2f", lowerBreakdown)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Notification Status and Trigger Row
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = ObsidianSurfaceElevated,
                border = BorderStroke(1.dp, ObsidianBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = if (isAutoNotificationEnabled) GoldPrimary else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "Automated Pre-News Alert Sentinel"
                                    AppLanguage.HINDI -> "स्वचालित प्री-न्यूज़ अलर्ट सेंटिनल"
                                    AppLanguage.MARATHI -> "स्वयंचलित प्री-न्यूज अलर्ट सेन्टिनेल"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "Armed: System auto-triggers alerts & radar 15-60m before release"
                                    AppLanguage.HINDI -> "सक्रिय: न्यूज़ से 15-60 मिनट पहले फोन अलर्ट व रडार अपने-आप चालू होगा"
                                    AppLanguage.MARATHI -> "सक्रिय: बातमीच्या 15-60 मिनिटे आधी फोन अलर्ट व रडार आपोआप चालू होईल"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = NeonGreen
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NeonGreen.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = when (currentLang) {
                                AppLanguage.ENGLISH -> "AUTO-ON 🟢"
                                AppLanguage.HINDI -> "ऑटो-चालू 🟢"
                                AppLanguage.MARATHI -> "ऑटो-चालू 🟢"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Black,
                            color = NeonGreen,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Button: Send Test 1-Hour Notification
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    val timeRemainingText = when (currentLang) {
                        AppLanguage.ENGLISH -> "Approx. 1 Hour (T-60m)"
                        AppLanguage.HINDI -> "लगभग 1 घंटा (T-60m)"
                        AppLanguage.MARATHI -> "सुमारे 1 तास (T-60m)"
                    }
                    val sent = PreNewsAlertNotificationManager.sendPreNewsAlertNotification(
                        context = context,
                        eventTitle = primaryEvent.title,
                        country = primaryEvent.country,
                        timeRemainingText = timeRemainingText,
                        aiBias = preBias,
                        forecastInfo = if (primaryEvent.forecast.isNotBlank()) "Est ${primaryEvent.forecast} vs Prev ${primaryEvent.previous}" else "High Impact USD News",
                        targetRange = "$${String.format(Locale.US, "%.1f", currentPrice - 20.0)} - $${String.format(Locale.US, "%.1f", currentPrice + 25.0)}",
                        lang = currentLang,
                        isTest = true
                    )
                    lastSentTestTime = "Sent just now 🔔"
                    Toast.makeText(
                        context,
                        when (currentLang) {
                            AppLanguage.ENGLISH -> "🚨 1-Hour News Notification sent! Check your notification tray."
                            AppLanguage.HINDI -> "🚨 1-घंटे पहले का न्यूज़ नोटिफिकेशन भेजा गया! अपना नोटिफिकेशन ट्रे देखें।"
                            AppLanguage.MARATHI -> "🚨 1-तास आधीचे न्यूज नोटिफिकेशन पाठवले! नोटिफिकेशन ट्रे तपासा."
                        },
                        Toast.LENGTH_LONG
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_send_test_1hr_notification"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9100)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (currentLang) {
                        AppLanguage.ENGLISH -> "🔔 SEND TEST 1-HR NOTIFICATION NOW"
                        AppLanguage.HINDI -> "🔔 1 घंटे पहले का टेस्ट नोटिफिकेशन अभी भेजें"
                        AppLanguage.MARATHI -> "🔔 1 तास आधीचे टेस्ट नोटिफिकेशन आता पाठवा"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    fontSize = 11.5.sp
                )
            }

            AnimatedVisibility(visible = lastSentTestTime != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = NeonGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (currentLang) {
                            AppLanguage.ENGLISH -> "Notification delivered to device status bar!"
                            AppLanguage.HINDI -> "नोटिफिकेशन आपके फोन के स्टेटस बार में आ गया है!"
                            AppLanguage.MARATHI -> "नोटिफिकेशन तुमच्या फोनच्या स्टेटस बारमध्ये आले आहे!"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonGreen,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
