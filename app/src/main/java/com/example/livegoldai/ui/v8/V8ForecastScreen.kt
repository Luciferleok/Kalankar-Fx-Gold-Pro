package com.example.livegoldai.ui.v8

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.data.LearningEngine
import com.example.livegoldai.data.RealityEngine
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * FORECAST — the prediction and the exact math behind it.
 * "Why this forecast?" uses the same weighted-vote numbers as the 68% gate.
 */
@Composable
fun V8ForecastScreen(
    analysis: GoldAnalysisResult,
    lang: AppLanguage,
    onOpenPlaybook: () -> Unit,
    onOpenLearning: () -> Unit
) {
    val sig = analysis.overallSignal
    val split = RealityEngine.outcomeSplit(analysis)
    val ins = analysis.insights
    val rawConf = analysis.agreementPercent.roundToInt()
    val calibrated = RealityEngine.calibratedConfidence(analysis.learning, rawConf)
    var showTrace by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(V8.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ---------------- prediction
        V8Card(level = 1, accent = signalColor(sig).copy(alpha = 0.45f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    V8Label("${analysis.interval.uppercase()} " + tr(lang, "forecast", "अनुमान", "अंदाज"))
                    Text(text = arrowOf(sig) + " " + sig.name, color = signalColor(sig), fontSize = 30.sp, fontWeight = FontWeight.Black)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "${calibrated ?: rawConf}%", color = V8.Text1, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text(text = if (calibrated != null) tr(lang, "calibrated", "कैलिब्रेटेड", "कॅलिब्रेटेड") else tr(lang, "uncalibrated", "अनकैलिब्रेटेड", "अनकॅलिब्रेटेड"),
                        color = if (calibrated != null) V8.Green else V8.Amber, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = tr(lang, "Up ", "ऊपर ", "वर ") + "${split.bullish}%", color = V8.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(text = tr(lang, "Flat ", "सपाट ", "सपाट ") + "${split.sideways}%", color = V8.Text2, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(text = tr(lang, "Down ", "नीचे ", "खाली ") + "${split.bearish}%", color = V8.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            V8ThreeWayBar(split.bullish, split.sideways, split.bearish)
            Text(
                text = if (split.fromHistory) tr(lang, "From real results of past BUY/SELL calls", "पिछली BUY/SELL कॉल्स के असली नतीजों से", "मागील BUY/SELL कॉल्सच्या खऱ्या निकालांवरून")
                else tr(lang, "Not enough history yet: these are today's pillar votes, not probabilities", "अभी इतिहास कम है: ये आज के पिलर वोट हैं, संभावना नहीं", "अजून इतिहास कमी: ही आजची पिलर मते आहेत, शक्यता नाही"),
                color = V8.Text3, fontSize = 9.sp
            )
        }

        // ---------------- V10 market brain (measured state, quality, uncertainty, cross-market, analogs)
        analysis.brain?.let { V10BrainSection(it, lang) }

        // ---------------- why this forecast (real weighted vote)
        if (ins != null) {
            V8Card(level = 2) {
                V8Label(tr(lang, "Why this forecast?", "यह अनुमान क्यों?", "हा अंदाज का?"))
                Text(
                    text = String.format(Locale.US, "Bullish %.1f%%  •  Bearish %.1f%%  •  gate %.0f%%", ins.bullishPoints, ins.bearishPoints, ins.gatePercent),
                    color = V8.Text1, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = tr(lang, "Each pillar adds its weight to BUY or SELL. BUY/SELL needs 68% (or 5 of 7 pillars).",
                        "हर पिलर अपना वज़न BUY या SELL में जोड़ता है। BUY/SELL के लिए 68% (या 7 में से 5 पिलर) चाहिए।",
                        "प्रत्येक पिलर आपले वजन BUY किंवा SELL मध्ये जोडतो. BUY/SELL साठी 68% (किंवा 7 पैकी 5 पिलर) हवे."),
                    color = V8.Text3, fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                ins.contributions.sortedByDescending { abs(it.points) }.forEach { c ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = c.title, color = V8.Text2, fontSize = 11.sp, modifier = Modifier.weight(0.42f))
                        Row(modifier = Modifier.weight(0.38f)) {
                            val frac = (abs(c.points) / 20.0).coerceIn(0.02, 1.0).toFloat()
                            Box(
                                modifier = Modifier
                                    .weight(frac)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (c.verdict == Signal.WAIT) V8.Text3 else signalColor(c.verdict))
                                    .padding(vertical = 4.dp)
                            ) {}
                            if (frac < 1f) Spacer(modifier = Modifier.weight(1f - frac))
                        }
                        Text(
                            text = if (c.verdict == Signal.WAIT) "0" else String.format(Locale.US, "%+.1f", c.points),
                            color = signalColor(c.verdict), fontSize = 11.sp, fontWeight = FontWeight.Black,
                            modifier = Modifier.weight(0.2f)
                        )
                    }
                }
            }
        }

        // ---------------- pillars with real accuracy
        V8Card(level = 2) {
            V8Label(tr(lang, "Pillars • live vote • real accuracy", "पिलर • लाइव वोट • असली सटीकता", "पिलर • लाइव्ह मत • खरी अचूकता"))
            analysis.groups.forEach { g ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = g.title, color = V8.Text1, fontSize = 11.sp, modifier = Modifier.weight(0.44f))
                    Text(text = arrowOf(g.verdict) + " " + g.verdict.name, color = signalColor(g.verdict), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.2f))
                    Text(text = LearningEngine.accuracyLabel(analysis.learning, "grp:${g.key}"), color = V8.Text3, fontSize = 10.sp, modifier = Modifier.weight(0.36f))
                }
            }
        }

        // ---------------- prediction trace
        V8Card(level = 3, onClick = { showTrace = !showTrace }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                V8Label(tr(lang, "Prediction trace", "प्रेडिक्शन ट्रेस", "प्रेडिक्शन ट्रेस"))
                Spacer(modifier = Modifier.weight(1f))
                Text(text = if (showTrace) "▴" else "▾", color = V8.Text3, fontSize = 12.sp)
            }
            if (showTrace) {
                val feed = analysis.feed
                val excluded = analysis.health?.indicators?.filter { it.status != "OK" } ?: emptyList()
                V8KeyValue(tr(lang, "Calculated", "गणना", "गणना"), feed?.let { com.example.livegoldai.data.PredictionLedger.utcLabel(it.fetchedAtMs, true) } ?: "--")
                V8KeyValue(tr(lang, "Data source", "डेटा स्रोत", "डेटा स्रोत"), feed?.source ?: "--")
                V8KeyValue(tr(lang, "Last candle", "आखिरी कैंडल", "शेवटची कँडल"), analysis.lastUpdated)
                V8KeyValue(tr(lang, "Model", "मॉडल", "मॉडेल"), analysis.learning?.modelVersion ?: "--")
                V8KeyValue(tr(lang, "Indicators used", "इस्तेमाल इंडिकेटर", "वापरलेले इंडिकेटर"), "${analysis.groups.sumOf { it.indicators.size } - excluded.size}/${analysis.groups.sumOf { it.indicators.size }}")
                V8KeyValue(tr(lang, "Rule bots", "नियम बॉट", "नियम बॉट"), analysis.multiBotEnsemble?.let { "${it.buyVotes} BUY • ${it.sellVotes} SELL • ${it.waitVotes} —" } ?: "--")
                V8KeyValue(tr(lang, "External AI", "बाहरी AI", "बाहेरील AI"), analysis.aiCouncil?.let { c -> "${c.eligible} voting / ${c.configured} connected • ${c.consensus} • advisory only" }
                    ?: tr(lang, "none connected", "कोई कनेक्ट नहीं", "काहीही कनेक्ट नाही"))
                if (excluded.isNotEmpty()) {
                    Text(text = tr(lang, "Not usable this refresh: ", "इस रिफ्रेश में उपयोग नहीं: ", "या रिफ्रेशमध्ये वापरले नाही: ") + excluded.joinToString(", ") { it.name },
                        color = V8.Amber, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                V8Card(level = 3, onClick = onOpenPlaybook) {
                    Text(text = tr(lang, "Playbook →", "प्लेबुक →", "प्लेबुक →"), color = V8.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                V8Card(level = 3, onClick = onOpenLearning) {
                    Text(text = tr(lang, "Real accuracy →", "असली सटीकता →", "खरी अचूकता →"), color = V8.Learn, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.width(1.dp).height(16.dp))
    }
}
