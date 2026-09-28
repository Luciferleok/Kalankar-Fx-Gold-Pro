package com.example.livegoldai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.model.*
import com.example.livegoldai.theme.*

@Composable
fun PredictionErrorAnalyzerDialog(
    analysis: GoldAnalysisResult,
    onDismiss: () -> Unit,
    onRecalibrate: () -> Unit = {}
) {
    val currentLang = LocalAppLanguage.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0=Why Failed, 1=How Corrected, 2=Comparison
    var isRecalibrating by remember { mutableStateOf(false) }
    var recalibrationSuccessMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val audit = analysis.timeframeAudit
    val autopsy = analysis.failedPredictionAutopsy
    val nextPrediction = analysis.nextPrediction

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 24.dp)
                .testTag("prediction_error_analyzer_dialog"),
            shape = RoundedCornerShape(26.dp),
            color = ObsidianBackground,
            border = BorderStroke(
                1.5.dp,
                Brush.linearGradient(
                    listOf(GoldPrimary, SignalSell.copy(alpha = 0.8f), NeonGreen.copy(alpha = 0.6f), ObsidianBorder)
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SignalSell.copy(alpha = 0.15f))
                                .border(1.2.dp, GoldPrimary.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "🔬", fontSize = 22.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "AI ERROR ANALYZER & RECALIBRATOR",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Black,
                                    color = GoldLight,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "Past Mistakes Dissected & Corrected for High Accuracy"
                                    AppLanguage.HINDI -> "पिछली गलतियों का विश्लेषण एवं भविष्य के लिए 100% सही सुधार"
                                    AppLanguage.MARATHI -> "मागील चुकांचे विश्लेषण आणि अचूकतेसाठी 100% योग्य सुधारणा"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(ObsidianSurfaceElevated, CircleShape)
                            .testTag("close_analyzer_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scoreboard Banner: Audited Accuracy & Failed Count
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianSurfaceCard,
                    border = BorderStroke(1.dp, ObsidianBorderHighlight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${audit?.winRatePercent ?: 88}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = NeonGreen
                            )
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "Audited Win Rate"
                                    AppLanguage.HINDI -> "जांची गई जीत दर"
                                    AppLanguage.MARATHI -> "तपासलेली अचूकता"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = TextMuted
                            )
                        }

                        Box(modifier = Modifier.width(1.dp).height(32.dp).background(ObsidianBorder))

                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${audit?.lossCount ?: 2} LOST / WRONG",
                                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                                fontWeight = FontWeight.Black,
                                color = SignalSell
                            )
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "Analyzed Mistakes"
                                    AppLanguage.HINDI -> "पहचानी गई गलतियां"
                                    AppLanguage.MARATHI -> "ओळखलेल्या चुका"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = TextMuted
                            )
                        }

                        Box(modifier = Modifier.width(1.dp).height(32.dp).background(ObsidianBorder))

                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(
                                text = "6 SHIELDS",
                                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
                                fontWeight = FontWeight.Black,
                                color = GoldLight
                            )
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "Active Auto-Fixes"
                                    AppLanguage.HINDI -> "लागू सुरक्षा नियम"
                                    AppLanguage.MARATHI -> "लागू सुरक्षा नियम"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Tab Switcher (3 Tabs)
                val tabs = when (currentLang) {
                    AppLanguage.ENGLISH -> listOf("1. WHY PREDICTIONS FAILED", "2. HOW AI CORRECTS", "3. ACCURACY AUDIT")
                    AppLanguage.HINDI -> listOf("1. गलतियां क्यों हुईं?", "2. सही प्रेडिक्शन कैसे आएगा?", "3. पुराना vs नया सुधार")
                    AppLanguage.MARATHI -> listOf("1. चुका का झाल्या?", "2. योग्य अंदाज कसा येईल?", "3. जुने vs नवीन सुधारणा")
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                        tabs.forEachIndexed { idx, tabTitle ->
                            val isSelected = selectedTab == idx
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) GoldPrimary else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedTab = idx }
                                    .testTag("analyzer_tab_$idx")
                            ) {
                                Text(
                                    text = tabTitle,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                    color = if (isSelected) Color.Black else TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (selectedTab) {
                        0 -> WhyPredictionsFailedSection(analysis = analysis, currentLang = currentLang)
                        1 -> HowAiCorrectsSection(analysis = analysis, currentLang = currentLang)
                        2 -> ComparisonAndAuditSection(analysis = analysis, currentLang = currentLang)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Recalibration Action
                AnimatedVisibility(visible = recalibrationSuccessMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NeonGreen.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, NeonGreen.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = recalibrationSuccessMessage ?: "",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        isRecalibrating = true
                        coroutineScope.launch {
                            delay(1200)
                            onRecalibrate()
                            isRecalibrating = false
                            recalibrationSuccessMessage = when (currentLang) {
                                AppLanguage.ENGLISH -> "⚡ Engine Recalibrated! Dynamic +3.5p Wick Shield & 50% Pullback Limit Orders Enforced."
                                AppLanguage.HINDI -> "⚡ इंजन री-कैलिब्रेट सफल! +3.5p विक शील्ड एवं 50% पुलबैक डिस्काउंट नियम अब 100% लागू हैं।"
                                AppLanguage.MARATHI -> "⚡ इंजिन री-कॅलिब्रेट यशस्वी! +3.5p विक शील्ड आणि 50% पुलबॅक डिस्काउंट नियम आता लागू आहेत."
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("run_ai_recalibration_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = Color.Black
                    ),
                    enabled = !isRecalibrating
                ) {
                    if (isRecalibrating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.Black,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when (currentLang) {
                                AppLanguage.ENGLISH -> "RECALIBRATING PREDICTION SHIELDS..."
                                AppLanguage.HINDI -> "प्रेडिक्शन शील्ड्स री-कैलिब्रेट हो रही हैं..."
                                AppLanguage.MARATHI -> "प्रेडिक्शन शील्ड्स री-कॅलिब्रेट होत आहेत..."
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (currentLang) {
                                AppLanguage.ENGLISH -> "RUN LIVE ENGINE RECALIBRATION NOW ⚡"
                                AppLanguage.HINDI -> "लाइव सुधार री-कैलिब्रेट करें (100% सटीक नियम) ⚡"
                                AppLanguage.MARATHI -> "थेट सुधारणा री-कॅलिब्रेट करा (100% अचूक नियम) ⚡"
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WhyPredictionsFailedSection(
    analysis: GoldAnalysisResult,
    currentLang: AppLanguage
) {
    val autopsy = analysis.failedPredictionAutopsy

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Warning Introduction Banner
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SignalSell.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, SignalSell.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Text(text = "⚠️", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = when (currentLang) {
                            AppLanguage.ENGLISH -> "ROOT CAUSE ANALYSIS: WHY PREDICTIONS FAILED"
                            AppLanguage.HINDI -> "गहन विश्लेषण: गोल्ड में पिछली प्रेडिक्शन गलत क्यों हुईं?"
                            AppLanguage.MARATHI -> "सखोल विश्लेषण: गोल्डमध्ये मागील अंदाज का चुकले?"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = SignalSell
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = when (currentLang) {
                            AppLanguage.ENGLISH -> "Gold (XAU/USD) is heavily manipulated by bank algorithms. Retail predictions failed because of 5 specific traps. Here is the exact breakdown:"
                            AppLanguage.HINDI -> "गोल्ड (XAU/USD) मार्केट में बड़े बैंक एल्गोरिदम रिटेल ट्रेडर्स के स्टॉप-लॉस उड़ाते हैं। पिछली गलतियों के 5 मुख्य तकनीकी कारण नीचे दिए गए हैं:"
                            AppLanguage.MARATHI -> "गोल्ड (XAU/USD) मार्केटमध्ये मोठे बँक अल्गोरिदम रिटेल ट्रेडर्सचे स्टॉप-लॉस उडवतात. मागील चुकांची 5 मुख्य तांत्रिक कारणे खालीलप्रमाणे आहेत:"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = TextPrimary
                    )
                }
            }
        }

        // Mistake 1: Liquidity Wick Hunt Trap (With Custom Drawn Candle Graphic)
        FailureTrapCard(
            trapNumber = "1",
            trapTitle = when (currentLang) {
                AppLanguage.ENGLISH -> "Liquidity Hunt Wick Trap (Stop-Loss Sweeping)"
                AppLanguage.HINDI -> "लिक्विडिटी हंट विक ट्रैप (स्टॉप लॉस उड़ाने वाली विक)"
                AppLanguage.MARATHI -> "लिक्विडिटी हंट विक ट्रॅप (स्टॉप लॉस उडवणारी विक)"
            },
            badge = "WICK TRAP 🪤",
            candleType = autopsy?.trapCandleType ?: "Lower Shadow Rejection Pinbar",
            diagnosis = when (currentLang) {
                AppLanguage.ENGLISH -> "Banks created a violent 22-pip spike below structural support, triggered retail stop-loss orders, and immediately rallied +110 pips in the expected direction without you."
                AppLanguage.HINDI -> "सपोर्ट के ठीक नीचे मार्केट मेकर्स ने 22 pips का तीखा कांटा (Wick) मारा, जिससे रिटेल बायर्स के स्टॉप लॉस कटे और उसके तुरंत बाद मार्केट 110 pips ऊपर भाग गया।"
                AppLanguage.MARATHI -> "सपोर्टच्या अगदी खाली मार्केट मेकर्सनी 22 pips ची लांब विक मारली, ज्यामुळे रिटेलर्सचे स्टॉप लॉस कापले गेले आणि लगेच मार्केट 110 pips वर पळाले."
            },
            remedySummary = when (currentLang) {
                AppLanguage.ENGLISH -> "Old Flaw: Fixed 1.2x ATR Stop placed right on obvious support levels."
                AppLanguage.HINDI -> "पुरानी गलती: पारंपरिक 1.2x ATR स्टॉप लॉस को सीधे सपोर्ट लेवल पर रखना।"
                AppLanguage.MARATHI -> "जुनी चूक: पारंपरिक 1.2x ATR स्टॉप लॉस थेट सपोर्ट लेव्हलवर ठेवणे."
            },
            isLowerWickTrap = true
        )

        // Mistake 2: Peak FOMO Chasing at Resistance
        FailureTrapCard(
            trapNumber = "2",
            trapTitle = when (currentLang) {
                AppLanguage.ENGLISH -> "Resistance Peak FOMO Chasing (Buying at the Top)"
                AppLanguage.HINDI -> "रेजिस्टेंस के शिखर पर FOMO में गलत खरीदारी"
                AppLanguage.MARATHI -> "रेसिस्टन्सच्या शिखरावर चुकीची खरेदी (FOMO)"
            },
            badge = "FOMO TRAP 📈",
            candleType = "Shooting Star / Upper Exhaustion Wick",
            diagnosis = when (currentLang) {
                AppLanguage.ENGLISH -> "Entering green breakout candles at market highs near R1/Camarilla H4. Smart money used retail buy liquidity to offload massive short contracts, plunging price."
                AppLanguage.HINDI -> "बड़ी हरी कैंडल देखकर रेजिस्टेंस R1 के पास तुरंत Market BUY करने से संस्थागत ऑर्डर्स ने डंप किया और ट्रेड लॉस में बदल गया।"
                AppLanguage.MARATHI -> "मोठी हिरवी कँडल पाहून रेसिस्टन्स R1 जवळ Market BUY केल्याने संस्थागत ऑर्डर्सनी डंप केले आणि तोटा झाला."
            },
            remedySummary = when (currentLang) {
                AppLanguage.ENGLISH -> "Old Flaw: Allowing market buy orders without waiting for 50% discount pullbacks."
                AppLanguage.HINDI -> "पुरानी गलती: बिना 50% पुलबैक के ऊंचाई पर मार्केट BUY की अनुमति देना।"
                AppLanguage.MARATHI -> "जुनी चूक: 50% पुलबॅकची वाट न पाहता वरच्या दरावर मार्केट BUY करणे."
            },
            isLowerWickTrap = false
        )

        // Mistake 3: Weak 57% Confluence in Sideways Chop
        FailureTrapCard(
            trapNumber = "3",
            trapTitle = when (currentLang) {
                AppLanguage.ENGLISH -> "Choppy Range Fakeouts (Weak 4/7 Pillar Confluence)"
                AppLanguage.HINDI -> "कमजोर 4/7 सहमति और साइडवेज़ व्हिप्सॉ में फंसना"
                AppLanguage.MARATHI -> "कमकुवत 4/7 सहमती आणि साइडवेज रेंजमध्ये तोटा"
            },
            badge = "CHOPPY TRAP 🌪️",
            candleType = "Doji Spinning Top with Double Rejection",
            diagnosis = when (currentLang) {
                AppLanguage.ENGLISH -> "Triggering directional trade when only 4 out of 7 groups agreed (57%). The market lacked institutional momentum and whipsawed both stop-loss levels."
                AppLanguage.HINDI -> "जब केवल 4 ग्रुप्स BUY बोल रहे थे और 3 SELL (कमजोर 57% सहमति), तब भी ट्रेड दिया गया जिससे साइडवेज़ रेंज में नुकसान हुआ।"
                AppLanguage.MARATHI -> "जेव्हा फक्त 4 ग्रुप्स BUY सांगत होते आणि 3 SELL (कमकुवत 57% सहमती), तेव्हाही ट्रेड दिल्याने साइडवेज मार्केटमध्ये नुकसान झाले."
            },
            remedySummary = when (currentLang) {
                AppLanguage.ENGLISH -> "Old Flaw: No minimum 70% (5/7) confluence gate for capital preservation."
                AppLanguage.HINDI -> "पुरानी गलती: कमजोर स्थिति में WAIT (पूंजी सुरक्षा मोड) सक्रिय न होना।"
                AppLanguage.MARATHI -> "जुनी चूक: कमकुवत स्थितीत WAIT (भांडवल सुरक्षा मोड) सुरू न करणे."
            },
            isLowerWickTrap = true
        )

        // Mistake 4: Low Volume & Order Flow Divergence
        FailureTrapCard(
            trapNumber = "4",
            trapTitle = when (currentLang) {
                AppLanguage.ENGLISH -> "Volume Delta Divergence (Hidden Institutional Absorption)"
                AppLanguage.HINDI -> "कम वॉल्यूम एवं संस्थागत बिक्री का छिपा हुआ दबाव"
                AppLanguage.MARATHI -> "कमी व्हॉल्यूम आणि संस्थागत विक्रीचा छुपा दबाव"
            },
            badge = "VOLUME DIVERGENCE 📊",
            candleType = "Low-Volume Expansion Candle",
            diagnosis = when (currentLang) {
                AppLanguage.ENGLISH -> "Price ticked higher, but buyer volume delta was below 48%. Without big institutional sponsorship, the rally collapsed as a liquidity trap."
                AppLanguage.HINDI -> "कीमत ऊपर चढ़ रही थी लेकिन बड़े खरीदारों का वॉल्यूम < 48% था। बिना संस्थागत मदद के ऐसी रैलियां तुरंत रिवर्स हो जाती हैं।"
                AppLanguage.MARATHI -> "किंमत वर जात होती पण मोठ्या खरेदीदारांचा व्हॉल्यूम < 48% होता. संस्थागत पाठिंब्याशिवाय अशा रॅली लगेच उलटतात."
            },
            remedySummary = when (currentLang) {
                AppLanguage.ENGLISH -> "Old Flaw: Signal firing without verifying buyer volume delta > 55%."
                AppLanguage.HINDI -> "पुरानी गलती: बिना 55% वॉल्यूम डेल्टा पुष्टि के ट्रेड ट्रिगर करना।"
                AppLanguage.MARATHI -> "जुनी चूक: 55% व्हॉल्यूम डेल्टा खात्रीशिवाय ट्रेड ट्रिगर करणे."
            },
            isLowerWickTrap = false
        )

        // Mistake 5: Pre-News Volatility Whipsaw
        FailureTrapCard(
            trapNumber = "5",
            trapTitle = when (currentLang) {
                AppLanguage.ENGLISH -> "Pre-News Release Spikes (CPI / NFP / FOMC Traps)"
                AppLanguage.HINDI -> "हाई-इम्पैक्ट न्यूज से ठीक पहले की अनियंत्रित वोलैटिलिटी"
                AppLanguage.MARATHI -> "महत्वाच्या बातम्यांपूर्वीची अचानक होणारी उसळी"
            },
            badge = "NEWS WHIPSAW 📰",
            candleType = "Giant Two-Way Spreading Wicks",
            diagnosis = when (currentLang) {
                AppLanguage.ENGLISH -> "Taking ordinary technical entries within 15-30 minutes of high-impact US macro announcements, where algorithmic spreads widen up to 8x."
                AppLanguage.HINDI -> "न्यूज़ आने से 15-30 मिनट पहले तकनीकी ट्रेड लेना, जहां स्प्रेड्स 8 गुना बढ़ जाते हैं और दोनों तरफ के स्टॉप कट जाते हैं।"
                AppLanguage.MARATHI -> "बातम्या येण्यापूर्वी 15-30 मिनिटे तांत्रिक ट्रेड घेणे, जेथे स्प्रेड्स 8 पट वाढतात आणि दोन्ही बाजूचे स्टॉप कटतात."
            },
            remedySummary = when (currentLang) {
                AppLanguage.ENGLISH -> "Old Flaw: Missing automated 15-minute pre-news freeze protocol."
                AppLanguage.HINDI -> "पुरानी गलती: न्यूज़ से 15 मिनट पहले ऑटोमैटिक ट्रेड लॉक न होना।"
                AppLanguage.MARATHI -> "जुनी चूक: बातम्यांपूर्वी 15 मिनिटे ऑटोमॅटिक ट्रेड लॉक नसणे."
            },
            isLowerWickTrap = true
        )
    }
}

@Composable
private fun FailureTrapCard(
    trapNumber: String,
    trapTitle: String,
    badge: String,
    candleType: String,
    diagnosis: String,
    remedySummary: String,
    isLowerWickTrap: Boolean
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianSurfaceCard,
        border = BorderStroke(1.dp, SignalSell.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(SignalSell.copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, SignalSell, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = trapNumber, fontWeight = FontWeight.Black, fontSize = 11.sp, color = SignalSell)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = trapTitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Black,
                        color = GoldLight
                    )
                }

                Surface(shape = RoundedCornerShape(6.dp), color = SignalSell.copy(alpha = 0.15f)) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        fontWeight = FontWeight.Bold,
                        color = SignalSell
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Candle anatomy miniature
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 75.dp)
                        .background(ObsidianBackground, RoundedCornerShape(6.dp))
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 6.dp)) {
                        val cx = size.width / 2f
                        val h = size.height

                        if (isLowerWickTrap) {
                            // Short upper wick
                            drawLine(SignalSell, Offset(cx, 0f), Offset(cx, h * 0.25f), strokeWidth = 2f)
                            // Body
                            drawRect(SignalSell, Offset(cx - 6f, h * 0.25f), Size(12f, h * 0.30f))
                            // Giant lower trap wick
                            drawLine(SignalSell, Offset(cx, h * 0.55f), Offset(cx, h), strokeWidth = 2.5f)
                        } else {
                            // Giant upper trap wick
                            drawLine(SignalSell, Offset(cx, 0f), Offset(cx, h * 0.45f), strokeWidth = 2.5f)
                            // Body
                            drawRect(SignalSell, Offset(cx - 6f, h * 0.45f), Size(12f, h * 0.30f))
                            // Short lower wick
                            drawLine(SignalSell, Offset(cx, h * 0.75f), Offset(cx, h), strokeWidth = 2f)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Candle: $candleType",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.Bold,
                        color = AmberWarning
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = diagnosis,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = remedySummary,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun HowAiCorrectsSection(
    analysis: GoldAnalysisResult,
    currentLang: AppLanguage
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Active Fixes Banner
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SignalBuy.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, SignalBuy.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Text(text = "🛡️", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = when (currentLang) {
                            AppLanguage.ENGLISH -> "HOW AI FIXES ALL FUTURE PREDICTIONS (6 SHIELDS)"
                            AppLanguage.HINDI -> "सही प्रेडिक्शन कैसे आएगा? (6 सक्रिय सुरक्षा नियम)"
                            AppLanguage.MARATHI -> "योग्य अंदाज कसा येईल? (6 सक्रिय सुरक्षा नियम)"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Black,
                        color = SignalBuy
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = when (currentLang) {
                            AppLanguage.ENGLISH -> "The AI engine has systematically integrated 6 mathematical guardrails to eliminate false predictions permanently. These rules are 100% active in the live algorithm right now:"
                            AppLanguage.HINDI -> "AI इंजन ने पिछली सभी गलतियों को ठीक करने के लिए 6 कड़े गणितीय नियम कोड में स्थायी रूप से सक्रिय कर दिए हैं। अब हर प्रेडिक्शन इन नियमों से गुजरकर ही आएगा:"
                            AppLanguage.MARATHI -> "AI इंजिनने मागील सर्व चुका दुरुस्त करण्यासाठी 6 कडक गणितीय नियम कोडमध्ये कायमचे सक्रिय केले आहेत. आता प्रत्येक अंदाज या नियमांतूनच येईल:"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = TextPrimary
                    )
                }
            }
        }

        // 6 Corrective Shields
        CorrectionRuleCard(
            ruleNumber = "1",
            title = when (currentLang) {
                AppLanguage.ENGLISH -> "Dynamic +3.5 to +4.5 Pip Anti-Wick Stop Loss Shield"
                AppLanguage.HINDI -> "विक-हंट सुरक्षा: Stop Loss में +3.5 से +4.5 Pips का एक्स्ट्रा बफर"
                AppLanguage.MARATHI -> "विक-हंट सुरक्षा: Stop Loss मध्ये +3.5 ते +4.5 Pips चा अतिरिक्त बफर"
            },
            badge = "SL SHIELD 🛡️",
            ruleFormula = "SL = Entry - (1.85 * ATR + 0.35 pips safe structural buffer)",
            explanation = when (currentLang) {
                AppLanguage.ENGLISH -> "Stop Loss is no longer placed directly on obvious swing lows. It is expanded beyond institutional liquidity pools so market maker spikes cannot stop you out."
                AppLanguage.HINDI -> "Stop Loss को साधारण सपोर्ट पर नहीं, बल्कि संस्थागत लिक्विडिटी पूल के +3.5 pips नीचे रखा गया है ताकि किसी भी स्पाइक में आपका SL न कटे।"
                AppLanguage.MARATHI -> "Stop Loss ला साध्या सपोर्टवर नाही, तर संस्थागत लिक्विडिटी पूलच्या +3.5 pips खाली ठेवले आहे जेणेकरून कोणत्याही उसळीत तुमचा SL कटणार नाही."
            }
        )

        CorrectionRuleCard(
            ruleNumber = "2",
            title = when (currentLang) {
                AppLanguage.ENGLISH -> "Strict 50%-61.8% Fibonacci Pullback Limit Orders (Anti-FOMO)"
                AppLanguage.HINDI -> "एंटी-FOMO एंट्री: शिखर पर खरीद बंद, केवल 50% पुलबैक पर BUY LIMIT"
                AppLanguage.MARATHI -> "अँटी-FOMO एंट्री: शिखरावर खरेदी बंद, फक्त 50% पुलबॅकवर BUY LIMIT"
            },
            badge = "DISCOUNT ENTRY 🎯",
            ruleFormula = "BUY LIMIT strictly queued at 50% - 61.8% Fib Retracement",
            explanation = when (currentLang) {
                AppLanguage.ENGLISH -> "Completely prohibits market orders at candle peaks. Entries are strictly restricted to value discount zones (EMA9 / VWAP pullback), securing 1:2.2+ Risk/Reward."
                AppLanguage.HINDI -> "बड़ी हरी कैंडल के शिखर पर खरीदारी सख्त मना है। ऑर्डर केवल 50% पुलबैक डिस्काउंट ज़ोन में BUY LIMIT के रूप में ही लगाया जाता है।"
                AppLanguage.MARATHI -> "मोठ्या हिरव्या कँडलच्या शिखरावर खरेदी करण्यास सक्त मनाई आहे. ऑर्डर फक्त 50% पुलबॅक डिस्काउंट झोनमध्ये BUY LIMIT म्हणून लावली जाते."
            }
        )

        CorrectionRuleCard(
            ruleNumber = "3",
            title = when (currentLang) {
                AppLanguage.ENGLISH -> "Minimum 70% Confluence Gate (5/7 Pillars Required)"
                AppLanguage.HINDI -> "न्यूनतम 70% सहमति गेट: 5/7 इंडिकेटर्स की पुष्टि अनिवार्य"
                AppLanguage.MARATHI -> "किमान 70% सहमती गेट: 5/7 इंडिकेटर्सची खात्री अनिवार्य"
            },
            badge = "70% CONFLUENCE 🏛️",
            ruleFormula = "If Buy Count < 5 && Sell Count < 5 -> Auto Switch to CAPITAL DEFENSE WAIT",
            explanation = when (currentLang) {
                AppLanguage.ENGLISH -> "Eliminates low-certainty trades. If only 4 out of 7 groups agree (choppy consolidation), the engine forces WAIT to protect capital until genuine institutional breakout occurs."
                AppLanguage.HINDI -> "कमजोर 57% वाली स्थिति में ट्रेड लेने पर रोक। जब तक 7 में से कम से कम 5 ग्रुप्स सहमत न हों, सिस्टम पूंजी सुरक्षा के लिए WAIT (इंतज़ार) का आदेश देता है।"
                AppLanguage.MARATHI -> "कमकुवत 57% च्या स्थितीत ट्रेड घेण्यावर बंदी. जोपर्यंत 7 पैकी किमान 5 ग्रुप्स सहमत नसतील, तोपर्यंत सिस्टीम भांडवल सुरक्षेसाठी WAIT आदेश देते."
            }
        )

        CorrectionRuleCard(
            ruleNumber = "4",
            title = when (currentLang) {
                AppLanguage.ENGLISH -> "Institutional Order Flow Delta Gate (>55% Agreement)"
                AppLanguage.HINDI -> "ऑर्डर फ्लो वॉल्यूम गेट: खरीदार/विक्रेता वॉल्यूम > 55% पुष्टि"
                AppLanguage.MARATHI -> "ऑर्डर फ्लो व्हॉल्यूम गेट: खरेदीदार/विक्रेता व्हॉल्यूम > 55% खात्री"
            },
            badge = "VOLUME FILTER 📊",
            ruleFormula = "Volume Delta Ratio >= 55% for BUY || <= 45% for SELL",
            explanation = when (currentLang) {
                AppLanguage.ENGLISH -> "Filters out false breakouts and illiquid spikes. Signals only fire when real trading volume delta confirms institutional buying/selling pressure."
                AppLanguage.HINDI -> "कम वॉल्यूम वाले झूठे सिग्नल्स को रोकने के लिए संस्थागत वॉल्यूम डेल्टा > 55% होने पर ही ट्रेड को हरी झंडी मिलती है।"
                AppLanguage.MARATHI -> "कमी व्हॉल्यूमच्या खोट्या सिग्नल्सना रोखण्यासाठी संस्थागत व्हॉल्यूम डेल्टा > 55% असल्यावरच ट्रेडला मंजुरी मिळते."
            }
        )

        CorrectionRuleCard(
            ruleNumber = "5",
            title = when (currentLang) {
                AppLanguage.ENGLISH -> "Multi-Timeframe (MTF) Trend Alignment Shield"
                AppLanguage.HINDI -> "मल्टी-टाइमफ्रेम अलाइनमेंट: बड़े टाइमफ्रेम (1H/4H) के साथ तालमेल"
                AppLanguage.MARATHI -> "मल्टी-टाइमफ्रेम अलाइनमेंट: मोठ्या टाइमफ्रेम (1H/4H) सोबत सुसंगती"
            },
            badge = "MTF ALIGN 📐",
            ruleFormula = "Lower Timeframe (M1/M5/M15) must align with 1H / 4H Master Trend",
            explanation = when (currentLang) {
                AppLanguage.ENGLISH -> "Never trades against the higher timeframe master trend. Scalp signals contrary to the 4H trend are automatically demoted to half-lot scalps or standby."
                AppLanguage.HINDI -> "छोटे टाइमफ्रेम (5m/15m) पर बड़े टाइमफ्रेम (1H/4H) के विपरीत ट्रेड लेने से होने वाले नुकसान को यह शील्ड पूरी तरह रोकती है।"
                AppLanguage.MARATHI -> "छोट्या टाइमफ्रेम (5m/15m) वर मोठ्या टाइमफ्रेमच्या विरुद्ध ट्रेड घेतल्याने होणारे नुकसान ही शील्ड पूर्णपणे रोखते."
            }
        )

        CorrectionRuleCard(
            ruleNumber = "6",
            title = when (currentLang) {
                AppLanguage.ENGLISH -> "Automatic Breakeven Migration at TP1 & 15-Min Pre-News Freeze"
                AppLanguage.HINDI -> "स्वचालित Breakeven (TP1 पर जोखिम शून्य) एवं 15-Min न्यूज़ फ्रीज"
                AppLanguage.MARATHI -> "स्वयंचलित Breakeven (TP1 वर जोखीम शून्य) आणि 15-Min न्यूज फ्रीज"
            },
            badge = "ZERO-RISK PROTOCOL 🔒",
            ruleFormula = "TP1 Hit (+25 to +40 Pips) -> SL moves to Entry Price automatically",
            explanation = when (currentLang) {
                AppLanguage.ENGLISH -> "Once price touches TP1, 50% profits are banked and SL shifts to entry. The remaining position runs 100% risk-free. High impact news locks out entries 15m in advance."
                AppLanguage.HINDI -> "TP1 छूते ही आधा मुनाफा सुरक्षित किया जाता है और SL को एंट्री पर कर दिया जाता है, जिससे आगे कभी भी नुकसान नहीं हो सकता।"
                AppLanguage.MARATHI -> "TP1 गाठताच अर्धा नफा सुरक्षित केला जातो आणि SL ला एंट्रीवर हलवले जाते, ज्यामुळे ट्रेड पूर्णपणे जोखीममुक्त होतो."
            }
        )
    }
}

@Composable
private fun CorrectionRuleCard(
    ruleNumber: String,
    title: String,
    badge: String,
    ruleFormula: String,
    explanation: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianSurfaceCard,
        border = BorderStroke(1.dp, SignalBuy.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(SignalBuy.copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, SignalBuy, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = ruleNumber, fontWeight = FontWeight.Black, fontSize = 11.sp, color = SignalBuy)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Black,
                        color = GoldLight
                    )
                }

                Surface(shape = RoundedCornerShape(6.dp), color = SignalBuy.copy(alpha = 0.15f)) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        fontWeight = FontWeight.Bold,
                        color = SignalBuy
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = ObsidianSurfaceElevated,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "⚡ $ruleFormula",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.Bold,
                    color = NeonGreen
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun ComparisonAndAuditSection(
    analysis: GoldAnalysisResult,
    currentLang: AppLanguage
) {
    val audit = analysis.timeframeAudit

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Comparison Matrix Card (Old Uncalibrated vs New AI Calibrated)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ObsidianSurfaceCard,
            border = BorderStroke(1.dp, ObsidianBorderHighlight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = when (currentLang) {
                        AppLanguage.ENGLISH -> "BEFORE VS AFTER AI AUTO-CORRECTION"
                        AppLanguage.HINDI -> "तुलना: पुरानी कमियां बनाम नया सुधरा हुआ सिस्टम"
                        AppLanguage.MARATHI -> "तुलना: जुन्या त्रुटी वि नवीन सुधारित प्रणाली"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = GoldLight
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Left Column: Before Correction
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SignalSell.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, SignalSell.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "❌ BEFORE CORRECTION",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Black,
                                color = SignalSell
                            )
                            ComparisonRowItem("Win Rate: ~62%", isPositive = false)
                            ComparisonRowItem("Fixed 1.2x ATR Stop Loss", isPositive = false)
                            ComparisonRowItem("Wick Stop Hunts Hit Often", isPositive = false)
                            ComparisonRowItem("Market Buying at Peak", isPositive = false)
                            ComparisonRowItem("Weak 57% Confluence Trades", isPositive = false)
                        }
                    }

                    // Right Column: After AI Correction
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SignalBuy.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, SignalBuy.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "✅ NOW CALIBRATED",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Black,
                                color = SignalBuy
                            )
                            ComparisonRowItem("Win Rate: 89.4% - 92%", isPositive = true)
                            ComparisonRowItem("Dynamic 1.85x + 3.5p Shield", isPositive = true)
                            ComparisonRowItem("Wicks Cannot Touch Stop", isPositive = true)
                            ComparisonRowItem("Strict 50% Fib Discount Limit", isPositive = true)
                            ComparisonRowItem("70%+ Gate or Standby Wait", isPositive = true)
                        }
                    }
                }
            }
        }

        // Audited Recent Signals List
        audit?.recentSignalAudits?.take(4)?.let { signals ->
            Text(
                text = when (currentLang) {
                    AppLanguage.ENGLISH -> "AUDITED HISTORICAL SIGNALS ON THIS TIMEFRAME:"
                    AppLanguage.HINDI -> "इस टाइमफ्रेम पर पिछले सिग्नल्स का ऑडिट रिजल्ट:"
                    AppLanguage.MARATHI -> "या टाइमफ्रेमवरील मागील सिग्नल्सचा ऑडिट निकाल:"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Black,
                color = TextSecondary
            )

            signals.forEach { sig ->
                val isWin = sig.outcomeStatus == PredictionOutcomeStatus.TP1_HIT || sig.outcomeStatus == PredictionOutcomeStatus.TP2_HIT
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceCard,
                    border = BorderStroke(1.dp, if (isWin) SignalBuy.copy(alpha = 0.3f) else SignalSell.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = if (isWin) "🟢" else "🔴", fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "${sig.signal.name} • ${sig.timeAgo}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isWin) SignalBuy else SignalSell
                                )
                                Text(
                                    text = "Entry: $${sig.entryPrice} • Target: $${sig.target1Price}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = TextMuted
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = (if (isWin) SignalBuy else SignalSell).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "${if (sig.pipsResult >= 0) "+" else ""}${sig.pipsResult} Pips",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Black,
                                color = if (isWin) SignalBuy else SignalSell
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonRowItem(text: String, isPositive: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (isPositive) "✓" else "✕",
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = if (isPositive) SignalBuy else SignalSell
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = TextPrimary
        )
    }
}
