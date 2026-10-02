package com.example.livegoldai.ui.v8

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.data.RealityEngine
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import java.util.Locale
import kotlin.math.roundToInt

/**
 * COCKPIT — the whole market state in a few seconds.
 * Every value here is real: live feed timing, the app's actual signal, real multi-timeframe
 * candles and the measured health of each component.
 */
@Composable
fun V8CockpitScreen(
    analysis: GoldAnalysisResult,
    lang: AppLanguage,
    selectedInterval: String,
    tick: Int,
    onIntervalChange: (String) -> Unit,
    onOpenHealth: () -> Unit,
    onOpenForecast: () -> Unit,
    onOpenPlaybook: () -> Unit,
    onOpenAi: () -> Unit = {}
) {
    @Suppress("UNUSED_VARIABLE") val recomposeEverySecond = tick
    val now = System.currentTimeMillis()
    val feed = analysis.feed
    val ageMs = feed?.let { now - it.fetchedAtMs } ?: -1L
    val liveState = when {
        feed == null -> "NO DATA"
        !feed.isLive -> "OFFLINE DEMO"
        ageMs > 180_000 -> "STALE"
        else -> "LIVE"
    }
    val liveColor = when (liveState) { "LIVE" -> V8.Green; "STALE" -> V8.Amber; else -> V8.Red }
    val sig = analysis.overallSignal
    val rawConf = analysis.agreementPercent.roundToInt()
    val calibrated = RealityEngine.calibratedConfidence(analysis.learning, rawConf)
    val ins = analysis.insights
    val health = analysis.health

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(V8.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ---------------- LEVEL 1: price + live feed
        V8Card(level = 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "XAU/USD", color = V8.Text2, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(liveColor)) {}
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = liveState, color = liveColor, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = String.format(Locale.US, "$%,.2f", analysis.currentPrice), color = V8.Text1, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = String.format(Locale.US, "%+.2f (%+.2f%%)", analysis.changeAmount, analysis.changePercent),
                    color = if (analysis.changeAmount >= 0) V8.Green else V8.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = (feed?.source ?: "--") + "  •  " + tr(lang, "age ", "उम्र ", "वय ") + fmtAge(ageMs) +
                    "  •  " + tr(lang, "fetch ", "लोड ", "लोड ") + (feed?.latencyMs?.let { "$it ms" } ?: "--"),
                color = V8.Text3, fontSize = 10.sp
            )
        }

        // ---------------- LEVEL 1: next move
        V8Card(level = 1, accent = signalColor(sig).copy(alpha = 0.45f), onClick = onOpenForecast) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("5m", "15m", "30m", "1h", "4h", "1d").forEach { iv ->
                    V8Chip(text = iv.uppercase(), selected = iv.equals(selectedInterval, ignoreCase = true)) { onIntervalChange(iv) }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            V8Label(tr(lang, "Next move", "अगली चाल", "पुढील हालचाल"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = arrowOf(sig) + " " + when (sig) {
                    Signal.BUY -> tr(lang, "UP", "ऊपर", "वर")
                    Signal.SELL -> tr(lang, "DOWN", "नीचे", "खाली")
                    Signal.WAIT -> tr(lang, "NO TRADE", "कोई ट्रेड नहीं", "ट्रेड नाही")
                }, color = signalColor(sig), fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "${calibrated ?: rawConf}%", color = V8.Text1, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(
                        text = if (calibrated != null) tr(lang, "calibrated confidence", "कैलिब्रेटेड भरोसा", "कॅलिब्रेटेड विश्वास")
                        else tr(lang, "pillar agreement (uncalibrated)", "पिलर सहमति (अनकैलिब्रेटेड)", "पिलर सहमती (अनकॅलिब्रेटेड)"),
                        color = V8.Text3, fontSize = 9.sp
                    )
                }
            }
            val until = analysis.nextPrediction?.validUntilTimestamp ?: 0L
            if (until > now) {
                Text(text = tr(lang, "Valid for ", "मान्य ", "वैध ") + fmtAge(until - now), color = V8.Text2, fontSize = 11.sp)
            }
            if (analysis.quantBotSignal?.statusText?.contains("LEARNED FILTER") == true) {
                Spacer(modifier = Modifier.height(4.dp))
                V8Badge(analysis.quantBotSignal?.statusText ?: "", V8.Learn)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = tr(lang, "Tap for the full forecast →", "पूरा अनुमान देखने के लिए टैप करें →", "पूर्ण अंदाजासाठी टॅप करा →"), color = V8.Gold, fontSize = 10.sp)
        }

        // ---------------- intelligence: quant vs bots vs AI (real answers only)
        val council = analysis.aiCouncil
        V8Card(level = 2, onClick = onOpenAi, accent = when (council?.conflictLevel) {
            "HIGH" -> V8.Red.copy(alpha = 0.5f); "ALIGNED" -> V8.Green.copy(alpha = 0.4f); else -> V8.Line
        }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                V8Label(tr(lang, "Intelligence", "इंटेलिजेंस", "इंटेलिजन्स"))
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = when (council?.conflictLevel) {
                        "HIGH" -> tr(lang, "HIGH CONFLICT", "भारी टकराव", "मोठा संघर्ष")
                        "MINOR" -> tr(lang, "PARTIAL", "आंशिक", "अंशतः")
                        "ALIGNED" -> tr(lang, "STRONG", "मज़बूत", "मजबूत")
                        else -> tr(lang, "AI not connected", "AI कनेक्ट नहीं", "AI कनेक्ट नाही")
                    },
                    color = when (council?.conflictLevel) { "HIGH" -> V8.Red; "MINOR" -> V8.Amber; "ALIGNED" -> V8.Green; else -> V8.Text3 },
                    fontSize = 11.sp, fontWeight = FontWeight.Black
                )
                Text(text = "  ›", color = V8.Text3, fontSize = 14.sp)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val bots = analysis.multiBotEnsemble?.ensembleSignal
                val ai = when (council?.consensus) { "BUY" -> Signal.BUY; "SELL" -> Signal.SELL; "WAIT" -> Signal.WAIT; else -> null }
                listOf(
                    Triple("QUANT", sig as Signal?, ""),
                    Triple(tr(lang, "BOTS", "बॉट", "बॉट"), bots, analysis.multiBotEnsemble?.let { "${maxOf(it.buyVotes, it.sellVotes, it.waitVotes)}/${it.totalBots}" } ?: ""),
                    Triple("AI", ai, council?.let { if (it.consensus == "SPLIT") "split" else if (it.eligible == 0) "0/${it.configured}" else "${maxOf(it.buyVotes, it.sellVotes, it.waitVotes)}/${it.eligible}" } ?: "--")
                ).forEach { (t, s2, sub) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = t, color = V8.Text3, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(text = if (s2 == null) "—" else arrowOf(s2) + " " + s2.name, color = s2?.let { signalColor(it) } ?: V8.Text3, fontSize = 13.sp, fontWeight = FontWeight.Black)
                        if (sub.isNotEmpty()) Text(text = sub, color = V8.Text3, fontSize = 9.sp)
                    }
                }
            }
        }

        // ---------------- V10 market brain summary
        analysis.brain?.let { V10BrainStrip(it, lang, onOpenForecast) }

        // ---------------- health strip
        if (health != null) {
            V8Card(level = 2, onClick = onOpenHealth) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    V8Label(tr(lang, "System", "सिस्टम", "सिस्टम"))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "${health.overallScore}/100", color = statusColor(health.overallStatus), fontSize = 12.sp, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = "›", color = V8.Text3, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("DATA" to "DATA", "INDICATORS" to "IND", "BOTS" to "BOTS", "AI" to "AI", "LEARNING" to "LEARN").forEach { (key, label) ->
                        val c = health.category(key)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = label, color = V8.Text3, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (c == null) "--" else if (c.score < 0) "OFF" else "${statusMark(c.status)} ${c.summary.substringBefore(" ")}",
                                color = if (c == null || c.score < 0) V8.Text3 else statusColor(c.status), fontSize = 11.sp, fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }

        // ---------------- early warnings
        if (ins != null && ins.warnings.isNotEmpty()) {
            V8Card(level = 2, accent = V8.Amber.copy(alpha = 0.5f)) {
                V8Label(tr(lang, "Early warning", "चेतावनी", "इशारा"), V8.Amber)
                ins.warnings.forEach { w ->
                    Text(text = "⚠ $w", color = V8.Text1, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        // ---------------- regime + story
        if (ins != null) {
            V8Card(level = 2) {
                V8Label(tr(lang, "Market regime", "मार्केट की स्थिति", "मार्केटची स्थिती"))
                Text(text = ins.regime.replace('_', ' '), color = V8.Text1, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(
                    text = String.format(Locale.US, "Trend efficiency %d%%  •  ATR %.2f  •  last candle %.1f× ATR", ins.trendEfficiencyPercent, ins.atr, ins.lastRangeVsAtr),
                    color = V8.Text3, fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                V8Label(tr(lang, "What the market is doing", "मार्केट क्या कर रहा है", "मार्केट काय करत आहे"))
                V8KeyValue(tr(lang, "Short term", "छोटा समय", "अल्प काळ"), ins.storyShort)
                V8KeyValue(tr(lang, "Medium term", "मध्यम समय", "मध्यम काळ"), ins.storyMedium)
                V8KeyValue(tr(lang, "Higher timeframe", "बड़ा टाइमफ्रेम", "मोठा टाइमफ्रेम"), ins.storyHigher)
            }
        }

        // ---------------- multi-horizon matrix (real candles)
        val rows = analysis.mtfMatrix?.timeframes?.filter { it.label.startsWith("PAXG") } ?: emptyList()
        V8Card(level = 2) {
            V8Label(tr(lang, "Multi-timeframe (real candles)", "मल्टी-टाइमफ्रेम (असली कैंडल)", "मल्टी-टाइमफ्रेम (खऱ्या कँडल)"))
            if (rows.isEmpty()) {
                Text(text = tr(lang, "Not loaded right now", "अभी लोड नहीं हुआ", "सध्या लोड झाले नाही"), color = V8.Text3, fontSize = 11.sp)
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    listOf("TF" to 0.18f, "DIR" to 0.17f, "RSI" to 0.17f, tr(lang, "LEVEL", "लेवल", "लेव्हल") to 0.48f).forEach { (h, w) ->
                        Text(text = h, color = V8.Text3, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(w))
                    }
                }
                rows.forEach { r ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = r.timeframe, color = V8.Text1, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.18f))
                        Text(text = arrowOf(r.signal) + " " + r.signal.name, color = signalColor(r.signal), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.17f))
                        Text(text = "${r.momentumPercent}", color = V8.Text2, fontSize = 11.sp, modifier = Modifier.weight(0.17f))
                        Text(text = r.keyLevel, color = V8.Text3, fontSize = 10.sp, modifier = Modifier.weight(0.48f))
                    }
                }
                Text(text = tr(lang, "Rule: EMA9 vs EMA21 + close vs EMA50 • PAXG/USDT", "नियम: EMA9 बनाम EMA21 + क्लोज़ बनाम EMA50 • PAXG/USDT", "नियम: EMA9 विरुद्ध EMA21 + क्लोज विरुद्ध EMA50 • PAXG/USDT"), color = V8.Text3, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        V8Card(level = 3, onClick = onOpenPlaybook) {
            Text(text = tr(lang, "Open full playbook (entry, SL, targets) →", "पूरा प्लेबुक खोलें (एंट्री, SL, टारगेट) →", "पूर्ण प्लेबुक उघडा (एंट्री, SL, टार्गेट) →"), color = V8.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
