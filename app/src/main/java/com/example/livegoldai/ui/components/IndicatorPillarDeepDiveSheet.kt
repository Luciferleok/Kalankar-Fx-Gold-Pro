package com.example.livegoldai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.model.GroupAnalysis
import com.example.livegoldai.model.LearningSnapshot
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*
import java.util.Locale

/**
 * Pillar deep-dive. Shows the pillar's LIVE indicator values (computed from real candles)
 * and its REAL accuracy from the Prediction Ledger. No stored or invented percentages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndicatorPillarDeepDiveSheet(
    group: GroupAnalysis,
    currentPrice: Double,
    onDismiss: () -> Unit,
    onOpenLotCalculator: ((Double) -> Unit)? = null,
    learning: LearningSnapshot? = null
) {
    val lang = LocalAppLanguage.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val verdictColor = when (group.verdict) {
        Signal.BUY -> SignalBuy
        Signal.SELL -> SignalSell
        Signal.WAIT -> SignalWait
    }
    val acc = learning?.sourceAccuracy("grp:${group.key}")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianBackground,
        contentColor = TextPrimary,
        scrimColor = OnAccent.copy(alpha = 0.78f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.testTag("indicator_pillar_deep_dive_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = group.title.uppercase(), color = GoldLight, fontSize = 15.sp, fontWeight = FontWeight.Black)
                    Text(text = pillarMeaning(group.key, lang), color = TextSecondary, fontSize = 10.sp)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(verdictColor.copy(alpha = 0.15f))
                        .border(1.dp, verdictColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(text = group.verdict.name, color = verdictColor, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = when (lang) {
                    AppLanguage.ENGLISH -> "REAL ACCURACY OF THIS PILLAR"
                    AppLanguage.HINDI -> "इस पिलर की असली सटीकता"
                    AppLanguage.MARATHI -> "या पिलरची खरी अचूकता"
                },
                color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (acc == null || acc.decided < 10) {
                Text(
                    text = when (lang) {
                        AppLanguage.ENGLISH -> "Not enough checked predictions yet (N=${acc?.decided ?: 0}, needs 10). The app is recording this pillar's votes and will show its real hit-rate here."
                        AppLanguage.HINDI -> "अभी पर्याप्त जाँची गई प्रेडिक्शन नहीं (N=${acc?.decided ?: 0}, 10 चाहिए)। ऐप इस पिलर के वोट रिकॉर्ड कर रहा है, असली सफलता दर यहाँ दिखेगी।"
                        AppLanguage.MARATHI -> "अजून पुरेसे तपासलेले अंदाज नाहीत (N=${acc?.decided ?: 0}, 10 हवे). ॲप या पिलरची मते नोंदवत आहे, खरा यश दर इथे दिसेल."
                    },
                    color = TextSecondary, fontSize = 10.sp
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = String.format(Locale.US, "%.1f%%", acc.accuracyPercent), color = if (acc.accuracyPercent >= 50) SignalBuy else SignalSell, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "${acc.correct}/${acc.decided} " + (if (acc.ciLowPercent >= 0) String.format(Locale.US, "• 95%% range %.0f–%.0f%%", acc.ciLowPercent, acc.ciHighPercent) else ""),
                        color = TextSecondary, fontSize = 10.sp
                    )
                }
                LinearProgressIndicator(
                    progress = { (acc.accuracyPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp),
                    color = if (acc.accuracyPercent >= 50) SignalBuy else SignalSell,
                    trackColor = ObsidianBorder
                )
                Text(
                    text = when (lang) {
                        AppLanguage.ENGLISH -> "Direction-only score on real price moves, all timeframes. 50% = no better than a coin flip."
                        AppLanguage.HINDI -> "असली भाव मूव पर सिर्फ दिशा का स्कोर, सभी टाइमफ्रेम। 50% = सिक्का उछालने जितना।"
                        AppLanguage.MARATHI -> "खऱ्या भाव हालचालींवर फक्त दिशेचा स्कोर, सर्व टाइमफ्रेम. 50% = नाणेफेकीइतके."
                    },
                    color = TextMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = when (lang) {
                    AppLanguage.ENGLISH -> "LIVE INDICATOR VALUES"
                    AppLanguage.HINDI -> "लाइव इंडिकेटर वैल्यू"
                    AppLanguage.MARATHI -> "लाइव्ह इंडिकेटर व्हॅल्यू"
                },
                color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black
            )
            group.indicators.forEach { item ->
                val c = when (item.signal) { Signal.BUY -> SignalBuy; Signal.SELL -> SignalSell; Signal.WAIT -> SignalWait }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurfaceCard)
                        .border(1.dp, c.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = item.name, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(text = item.signal.name, color = c, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                    Text(text = item.valueDisplay, color = GoldLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(text = item.detail, color = TextSecondary, fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = String.format(Locale.US, "Price now: %.2f", currentPrice) + when (lang) {
                    AppLanguage.ENGLISH -> " • values recalculated every refresh from real candles"
                    AppLanguage.HINDI -> " • हर रिफ्रेश पर असली कैंडल से दोबारा गणना"
                    AppLanguage.MARATHI -> " • प्रत्येक रिफ्रेशवर खऱ्या कँडलवरून पुन्हा गणना"
                },
                color = TextMuted, fontSize = 9.sp
            )

            if (onOpenLotCalculator != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { onOpenLotCalculator(85.0) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = OnAccent)
                ) {
                    Text(
                        text = when (lang) {
                            AppLanguage.ENGLISH -> "Open lot calculator"
                            AppLanguage.HINDI -> "लॉट कैलकुलेटर खोलें"
                            AppLanguage.MARATHI -> "लॉट कॅल्क्युलेटर उघडा"
                        },
                        fontWeight = FontWeight.Bold, fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun pillarMeaning(key: String, lang: AppLanguage): String {
    val en = when (key) {
        "trend" -> "EMAs, VWAP, EMA50/100, SuperTrend direction"
        "smc" -> "SuperTrend, VWAP side, MFI, liquidity sweep / structure"
        "momentum" -> "RSI, MACD, Stochastic, CCI, Williams %R, ROC"
        "sr" -> "Floor pivots and Fibonacci levels of recent swing"
        "volatility" -> "Bollinger / Keltner bands and ATR"
        "candlestick" -> "Last candle pattern: engulfing, hammer, direction"
        "macro" -> "US Dollar Index and US 10-year yield daily change"
        else -> "Indicator group"
    }
    return when (lang) {
        AppLanguage.ENGLISH -> en
        AppLanguage.HINDI -> "यह पिलर देखता है: $en"
        AppLanguage.MARATHI -> "हा पिलर पाहतो: $en"
    }
}
