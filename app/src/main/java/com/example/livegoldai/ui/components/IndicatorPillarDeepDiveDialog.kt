package com.example.livegoldai.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.model.GroupAnalysis
import com.example.livegoldai.model.IndicatorItem
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*

@Composable
fun IndicatorPillarDeepDiveDialog(
    group: GroupAnalysis,
    currentPrice: Double,
    onDismiss: () -> Unit
) {
    val currentLang = LocalAppLanguage.current
    val verdictColor = when (group.verdict) {
        Signal.BUY -> SignalBuy
        Signal.SELL -> SignalSell
        Signal.WAIT -> SignalWait
    }

    val icon = when (group.key.lowercase()) {
        "trend" -> "📈"
        "smc", "smart_money" -> "🏛️"
        "momentum" -> "⚡"
        "sr", "levels" -> "🎯"
        "volatility" -> "🌊"
        "candlestick" -> "🕯️"
        "macro" -> "🌐"
        else -> "📊"
    }

    val localizedTitle = when (group.key.lowercase()) {
        "trend" -> when (currentLang) {
            AppLanguage.ENGLISH -> "1. Trend & Structure Pillar"
            AppLanguage.HINDI -> "1. ट्रेंड एवं स्ट्रक्चर स्तंभ (Trend & Moving Averages)"
            AppLanguage.MARATHI -> "1. ट्रेंड आणि स्ट्रक्चर स्तंभ (Trend & Moving Averages)"
        }
        "smc", "smart_money" -> when (currentLang) {
            AppLanguage.ENGLISH -> "2. Smart Money Concepts (SMC)"
            AppLanguage.HINDI -> "2. स्मार्ट मनी व लिक्विडिटी (SMC Vault)"
            AppLanguage.MARATHI -> "2. स्मार्ट मनी व लिक्विडीटी (SMC Vault)"
        }
        "momentum" -> when (currentLang) {
            AppLanguage.ENGLISH -> "3. Momentum Oscillators"
            AppLanguage.HINDI -> "3. मोमेंटम ऑसिलेटर्स डीप-डाइव (RSI, MACD, Stoch)"
            AppLanguage.MARATHI -> "3. मोमेंटम ऑसिलेटर्स डीप-डाइव (RSI, MACD, Stoch)"
        }
        "sr", "levels" -> when (currentLang) {
            AppLanguage.ENGLISH -> "4. Support, Resistance & Pivots"
            AppLanguage.HINDI -> "4. सपोर्ट, रेजिस्टेंस एवं संस्थागत पिवट्स"
            AppLanguage.MARATHI -> "4. सपोर्ट, रेझिस्टन्स आणि संस्थागत पिव्हट्स"
        }
        "volatility" -> when (currentLang) {
            AppLanguage.ENGLISH -> "5. Volatility & Risk Range"
            AppLanguage.HINDI -> "5. वोलैटिलिटी व रिस्क बैंड्स (ATR, Bollinger Bands)"
            AppLanguage.MARATHI -> "5. व्होलॅटिलिटी व जोखीम मर्यादा (ATR, Bollinger)"
        }
        "candlestick" -> when (currentLang) {
            AppLanguage.ENGLISH -> "6. Candlestick Price Action"
            AppLanguage.HINDI -> "6. कैंडलस्टिक प्राइस एक्शन व विक विश्लेषण"
            AppLanguage.MARATHI -> "6. कँडलस्टिक प्राईस ॲक्शन व विक विश्लेषण"
        }
        "macro" -> when (currentLang) {
            AppLanguage.ENGLISH -> "7. Macro & Global Drivers"
            AppLanguage.HINDI -> "7. मैक्रो इकोनॉमिक्स व डॉलर इंडेक्स (DXY)"
            AppLanguage.MARATHI -> "7. मॅक्रो इकॉनॉमिक्स व डॉलर इंडेक्स (DXY)"
        }
        else -> group.title
    }

    val institutionalOverview = when (group.key.lowercase()) {
        "trend" -> when (currentLang) {
            AppLanguage.ENGLISH -> "Institutional trend models utilize exponential moving averages (EMA 9, 21, 50, 200), SuperTrend, and volume-weighted average price (VWAP) to define the dominant macro trajectory. If price is above VWAP and EMA9 > EMA21, institutions strictly maintain a long bias."
            AppLanguage.HINDI -> "संस्थागत ट्रेंड मॉडल (EMA 9, 21, 50, 200, SuperTrend और VWAP) मार्केट की मुख्य दिशा तय करते हैं। जब कीमत VWAP के ऊपर हो और EMA9 > EMA21 हो, तो बड़े बैंक सिर्फ BUY साइड पर रहते हैं।"
            AppLanguage.MARATHI -> "संस्थागत ट्रेंड मॉडेल्स (EMA 9, 21, 50, 200, SuperTrend आणि VWAP) बाजाराची मुख्य दिशा ठरवतात. जेव्हा किंमत VWAP च्या वर असते तेव्हा मोठे बँक फक्त खरेदीच्या बाजूने असतात."
        }
        "smc", "smart_money" -> when (currentLang) {
            AppLanguage.ENGLISH -> "Smart Money Concepts track the footprints of central banks and algorithms: Order Blocks (OB), Fair Value Gaps (FVG), and liquidity sweeps beneath retail swing points. Entries are strictly executed at discount retests."
            AppLanguage.HINDI -> "स्मार्ट मनी कॉन्सेप्ट्स (SMC) बड़े बैंकों के ऑर्डर ब्लॉक्स, फेयर वैल्यू गैप्स (FVG) और रिटेल स्टॉप-हंट लिक्विडिटी की पहचान करता है। यहाँ एंट्री हमेशा डिस्काउंट रीटेस्ट पर ली जाती है।"
            AppLanguage.MARATHI -> "स्मार्ट मनी कॉन्सेप्ट्स (SMC) मोठ्या बँकांचे ऑर्डर ब्लॉक्स, फेअर व्हॅल्यू गॅप्स (FVG) आणि लिक्विडिटी ओळखते. येथे एंट्री नेहमी डिस्काउंट झोनमध्ये घेतली जाते."
        }
        "momentum" -> when (currentLang) {
            AppLanguage.ENGLISH -> "Momentum oscillators (RSI-14, MACD, Stochastic RSI) reveal underlying price velocity and institutional exhaustion. Bullish and bearish divergences alert the algorithm to impending reversals."
            AppLanguage.HINDI -> "मोमेंटम ऑसिलेटर्स (RSI 14, MACD, CCI) कीमत की गति और थकावट को मापते हैं। जब कीमत नई ऊंचाई बनाती है लेकिन RSI नहीं बनाता, तो यह संभावित रिवर्सल का संकेत होता है।"
            AppLanguage.MARATHI -> "मोमेंटम ऑसिलेटर्स (RSI 14, MACD, CCI) किंमतीचा वेग आणि संस्थागत हालचाल मोजतात. हे संभाव्य रिव्हर्सलचा अचूक इशारा देतात."
        }
        "sr", "levels" -> when (currentLang) {
            AppLanguage.ENGLISH -> "Calculates mathematical reaction thresholds using Floor S1/R1, Camarilla Pivots, and Fibonacci golden pocket (50% - 61.8%). These zones serve as high-probability liquidity reaction points."
            AppLanguage.HINDI -> "फ्लोर पिवट्स, कैमरीला और 50%-61.8% फिबोनाची स्तर मार्केट के सबसे मजबूत सपोर्ट और रेजिस्टेंस का काम करते हैं जहाँ से बड़ा संस्थागत उछाल आता है।"
            AppLanguage.MARATHI -> "फ्लोअर पिव्हट्स, कॅमरीला आणि 50%-61.8% फिबोनाची लेव्हल्स मार्केटचे सर्वात मजबूत सपोर्ट आणि रेझिस्टन्स म्हणून काम करतात."
        }
        "volatility" -> when (currentLang) {
            AppLanguage.ENGLISH -> "ATR 14 and Bollinger Bands measure the expansion and compression of gold pip swings. Volatility expansion informs the dynamic stop loss buffer (+3.5 to +4.5 pips) to prevent wick stop-outs."
            AppLanguage.HINDI -> "ATR 14 और बॉलिंजर बैंड्स गोल्ड की हलचल की सीमा तय करते हैं। जब वोलैटिलिटी बढ़ती है, तो AI स्वतः Stop Loss को +3.5 pips चौड़ा कर देता है ताकि स्टॉप-हंट न हो।"
            AppLanguage.MARATHI -> "ATR 14 आणि बोलिंजर बँड्स गोल्डच्या चढ-उतारांची मर्यादा मोजतात. वोलॅटिलिटी वाढल्यावर AI आपोआप Stop Loss ला +3.5 pips सुरक्षित बफर देते."
        }
        "candlestick" -> when (currentLang) {
            AppLanguage.ENGLISH -> "Candlestick price action inspects upper/lower rejection shadows, pinbars, and engulfing patterns on live candles to pinpoint exact entry triggers."
            AppLanguage.HINDI -> "कैंडलस्टिक प्राइस एक्शन लंबी विक्स (Rejection Shadows) और इंगल्फिंग कैंडल की जांच करता है ताकि ठीक समय पर एंट्री निष्पादित की जा सके।"
            AppLanguage.MARATHI -> "कँडलस्टिक प्राईस ॲक्शन लांब विक्स आणि इंगल्फिंग कँडलची तपासणी करते जेणेकरून योग्य वेळी अचूक एंट्री घेता येईल."
        }
        "macro" -> when (currentLang) {
            AppLanguage.ENGLISH -> "Gold is inversely correlated to the US Dollar Index (DXY) and US 10-Year Treasury Yields. Softening dollar and central bank spot reserves accumulation provide strong buying tailwinds."
            AppLanguage.HINDI -> "गोल्ड अमेरिकी डॉलर (DXY) और 10-वर्षीय बॉन्ड यील्ड्स के विपरीत चलता है। डॉलर की कमजोरी और केंद्रीय बैंकों की रिकॉर्ड गोल्ड खरीदारी कीमतों को सपोर्ट देती है।"
            AppLanguage.MARATHI -> "गोल्ड अमेरिकन डॉलर (DXY) आणि बाँड यील्ड्सच्या विरुद्ध चालते. डॉलरची घसरण सोन्यासाठी खरेदीची मोठी संधी निर्माण करते."
        }
        else -> group.title
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 24.dp)
                .testTag("indicator_pillar_deep_dive_dialog"),
            shape = RoundedCornerShape(26.dp),
            color = ObsidianBackground,
            border = BorderStroke(
                1.5.dp,
                Brush.linearGradient(
                    listOf(GoldPrimary, verdictColor.copy(alpha = 0.8f), ObsidianBorder)
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
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
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(verdictColor.copy(alpha = 0.15f))
                                .border(1.2.dp, verdictColor, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = icon, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = localizedTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                color = GoldLight,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "${group.indicators.size} Component Indicators Audited",
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
                            .testTag("close_pillar_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Pillar Status Banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianSurfaceCard,
                    border = BorderStroke(1.dp, verdictColor.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "PILLAR CONSENSUS VERDICT",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                            Text(
                                text = "${group.verdict.name} • live indicator values",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = verdictColor
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = verdictColor.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, verdictColor)
                        ) {
                            Text(
                                text = "${group.indicators.count { it.signal == group.verdict }}/${group.indicators.size} CONFIRMED",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.ExtraBold,
                                color = verdictColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Institutional Reading Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(1.dp, ObsidianBorderHighlight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "🏛️", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = when (currentLang) {
                                        AppLanguage.ENGLISH -> "INSTITUTIONAL READING & HEDGE FUND VIEW:"
                                        AppLanguage.HINDI -> "संस्थागत विश्लेषण एवं बैंक एल्गोरिदम दृष्टिकोण:"
                                        AppLanguage.MARATHI -> "संस्थागत विश्लेषण व बँक अल्गोरिदम दृष्टिकोन:"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Black,
                                    color = GoldLight
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = institutionalOverview,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                                color = TextPrimary
                            )
                        }
                    }

                    // Component Indicators Section Header
                    Text(
                        text = when (currentLang) {
                            AppLanguage.ENGLISH -> "ALL ${group.indicators.size} COMPONENT INDICATORS IN THIS PILLAR:"
                            AppLanguage.HINDI -> "इस स्तंभ के सभी ${group.indicators.size} इंडिकेटर्स का लाइव डेटा:"
                            AppLanguage.MARATHI -> "या स्तंभातील सर्व ${group.indicators.size} इंडिकेटर्सचा थेट डेटा:"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Black,
                        color = TextSecondary
                    )

                    // Individual Component Indicators List
                    group.indicators.forEach { ind ->
                        val indColor = when (ind.signal) {
                            Signal.BUY -> SignalBuy
                            Signal.SELL -> SignalSell
                            Signal.WAIT -> SignalWait
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianSurfaceCard,
                            border = BorderStroke(1.dp, indColor.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ind.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Black,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Live Reading: ${ind.valueDisplay}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            fontWeight = FontWeight.Bold,
                                            color = GoldLight
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = indColor.copy(alpha = 0.15f),
                                        border = BorderStroke(1.dp, indColor.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = ind.signal.name,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            fontWeight = FontWeight.Black,
                                            color = indColor
                                        )
                                    }
                                }

                                if (ind.detail.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = ind.detail,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Close Action
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = OnAccent)
                ) {
                    Text(
                        text = when (currentLang) {
                            AppLanguage.ENGLISH -> "BACK TO COCKPIT"
                            AppLanguage.HINDI -> "कॉकपिट पर वापस जाएं"
                            AppLanguage.MARATHI -> "कॉकपिटवर परत जा"
                        },
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
