package com.example.livegoldai.ui.v8

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.BrainReport
import com.example.livegoldai.model.Signal
import java.util.Locale

private fun levelColor(level: String): Color = when (level) {
    "LOW", "NORMAL", "STRONG", "OK" -> V8.Green
    "MODERATE", "UNUSUAL", "WEAK", "STALE", "MIXED" -> V8.Amber
    "HIGH", "EXTREME", "OUT_OF_DISTRIBUTION", "NO EDGE" -> V8.Red
    else -> V8.Text3
}

private fun qualityColor(q: Int): Color = if (q >= 75) V8.Green else if (q >= 60) V8.Gold else if (q >= 40) V8.Amber else V8.Red

/** Small brain summary for the cockpit. */
@Composable
fun V10BrainStrip(b: BrainReport, lang: AppLanguage, onOpen: () -> Unit) {
    V8Card(level = 2, onClick = onOpen, accent = qualityColor(b.qualityIndex).copy(alpha = 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            V8Label(tr(lang, "Market brain", "मार्केट ब्रेन", "मार्केट ब्रेन"))
            Spacer(modifier = Modifier.weight(1f))
            Text(text = b.qualityClass, color = qualityColor(b.qualityIndex), fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(text = "  ›", color = V8.Text3, fontSize = 14.sp)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            MiniStat(tr(lang, "QUALITY", "क्वालिटी", "क्वालिटी"), "${b.qualityIndex}", qualityColor(b.qualityIndex), Modifier.weight(1f))
            MiniStat("EDGE", b.edge, levelColor(b.edge), Modifier.weight(1.2f))
            MiniStat(tr(lang, "ANOMALY", "गड़बड़ी", "विसंगती"), b.anomaly.level, levelColor(b.anomaly.level), Modifier.weight(1.2f))
            MiniStat(tr(lang, "UNCERTAINTY", "अनिश्चितता", "अनिश्चितता"), b.totalUncertainty, levelColor(b.totalUncertainty), Modifier.weight(1.4f))
        }
        Text(text = tr(lang, "Quality index is not accuracy.", "क्वालिटी इंडेक्स सटीकता नहीं है।", "क्वालिटी इंडेक्स अचूकता नाही."), color = V8.Text3, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun MiniStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(text = label, color = V8.Text3, fontSize = 8.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(text = value.replace('_', ' '), color = color, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

/** Full brain: every line is a measured value, or says that it cannot be measured yet. */
@Composable
fun V10BrainSection(b: BrainReport, lang: AppLanguage) {
    var showFeatures by remember { mutableStateOf(false) }
    var showLineage by remember { mutableStateOf(false) }
    var showResearch by remember { mutableStateOf(false) }

    // ================= MARKET BRAIN
    V8Card(level = 1, accent = V8.Gold.copy(alpha = 0.45f)) {
        V8Label(tr(lang, "Market brain", "मार्केट ब्रेन", "मार्केट ब्रेन"), V8.Gold)
        Spacer(modifier = Modifier.height(4.dp))
        V8KeyValue(tr(lang, "Regime", "स्थिति", "स्थिती"), b.regime.replace('_', ' '))
        V8KeyValue(tr(lang, "Regime changes next", "अगली बार स्थिति बदली", "पुढे स्थिती बदलली"), b.transitionRisk)
        V8KeyValue(tr(lang, "Anomaly risk", "गड़बड़ी जोखिम", "विसंगती जोखीम"), b.anomaly.level, levelColor(b.anomaly.level))
        V8KeyValue(tr(lang, "Timeframes", "टाइमफ्रेम", "टाइमफ्रेम"), b.mtfSummary)
        V8KeyValue(tr(lang, "Cross-market", "क्रॉस-मार्केट", "क्रॉस-मार्केट"), b.crossMarket.summary,
            when (b.crossMarket.summary) { "SUPPORTS BULLISH" -> V8.Green; "SUPPORTS BEARISH" -> V8.Red; "MIXED" -> V8.Amber; else -> V8.Text3 })
        V8KeyValue("AI", b.aiSummary)
        V8KeyValue(tr(lang, "Rule bots", "नियम बॉट", "नियम बॉट"), b.botSummary)
        V8KeyValue(tr(lang, "Historical analogs", "पुराने मिलते हालात", "जुन्या समान स्थिती"),
            if (b.analogs.status == "OK") "↑${b.analogs.upPct}% ↔${b.analogs.sidePct}% ↓${b.analogs.downPct}% (N=${b.analogs.used})" else tr(lang, "INSUFFICIENT HISTORY", "इतिहास कम है", "इतिहास कमी"),
            if (b.analogs.status == "OK") V8.Text1 else V8.Text3)
        Spacer(modifier = Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = tr(lang, "QUANT SIGNAL  ", "क्वांट सिग्नल  ", "क्वांट सिग्नल  "), color = V8.Text3, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(text = arrowOf(b.quantSignal) + " " + b.quantSignal.name + " ${b.baseConfidence}%", color = signalColor(b.quantSignal), fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
        Text(text = b.baseLabel + tr(lang, " • the brain describes the market, it does not change the signal", " • ब्रेन मार्केट बताता है, सिग्नल नहीं बदलता", " • ब्रेन मार्केट सांगतो, सिग्नल बदलत नाही"), color = V8.Text3, fontSize = 9.sp)
    }

    // ================= QUALITY + EDGE
    V8Card(level = 2, accent = qualityColor(b.qualityIndex).copy(alpha = 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                V8Label(tr(lang, "Prediction quality index", "प्रेडिक्शन क्वालिटी इंडेक्स", "प्रेडिक्शन क्वालिटी इंडेक्स"))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "${b.qualityIndex}", color = qualityColor(b.qualityIndex), fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text(text = " / 100", color = V8.Text3, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                V8Label("EDGE")
                Text(text = b.edge, color = levelColor(b.edge), fontSize = 18.sp, fontWeight = FontWeight.Black)
            }
        }
        LinearProgressIndicator(
            progress = { (b.qualityIndex / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(5.dp),
            color = qualityColor(b.qualityIndex), trackColor = V8.Line
        )
        Text(text = b.qualityClass, color = qualityColor(b.qualityIndex), fontSize = 12.sp, fontWeight = FontWeight.Black)
        b.qualityParts.forEach { V8KeyValue(it.label, it.value) }
        Text(text = b.edgeNote, color = V8.Text3, fontSize = 10.sp)
        Text(
            text = tr(lang, "A rule formula over real inputs. It is NOT accuracy and its weights are not yet validated by results.",
                "असली इनपुट पर एक नियम-फ़ॉर्मूला। यह सटीकता नहीं है, और इसके वज़न अभी नतीजों से परखे नहीं गए।",
                "खऱ्या इनपुटवर एक नियम-सूत्र. ही अचूकता नाही, आणि याची वजने अजून निकालांवरून तपासलेली नाहीत."),
            color = V8.Text3, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp)
        )
    }

    // ================= UNCERTAINTY + BUDGET
    V8Card(level = 2) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            V8Label(tr(lang, "Uncertainty", "अनिश्चितता", "अनिश्चितता"))
            Spacer(modifier = Modifier.weight(1f))
            V8Badge(b.totalUncertainty, levelColor(b.totalUncertainty))
        }
        b.uncertainty.forEach { u ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(text = u.name, color = V8.Text1, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.34f))
                Text(text = u.level, color = levelColor(u.level), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.22f))
                Text(text = u.detail, color = V8.Text3, fontSize = 10.sp, modifier = Modifier.weight(0.44f))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        V8Label(tr(lang, "Confidence budget", "भरोसे का हिसाब", "विश्वासाचा हिशोब"))
        V8KeyValue(tr(lang, "Base", "आधार", "आधार"), "${b.baseConfidence}  (${b.baseLabel})")
        if (b.budget.isEmpty()) {
            Text(text = tr(lang, "No deduction: none of the risk conditions is true now.", "कोई कटौती नहीं: अभी कोई जोखिम शर्त सच नहीं।", "कपात नाही: आता कोणतीही जोखीम अट खरी नाही."), color = V8.Text3, fontSize = 10.sp)
        }
        b.budget.forEach { V8KeyValue(it.label, "${it.delta}", V8.Amber) }
        V8KeyValue(tr(lang, "After deductions", "कटौती के बाद", "कपातीनंतर"), "${b.adjustedConfidence}", V8.Text1)
        Text(
            text = tr(lang, "Deductions are fixed caution rules, each tied to a condition that is true right now. They are not yet validated by results, so the main confidence shown elsewhere is unchanged.",
                "कटौतियाँ तय सावधानी-नियम हैं, हर एक अभी सच शर्त से जुड़ी है। ये अभी नतीजों से परखी नहीं गईं, इसलिए बाकी जगह दिखा मुख्य भरोसा वही है।",
                "कपाती ठराविक सावधगिरीचे नियम आहेत, प्रत्येक आता खऱ्या असलेल्या अटीशी जोडलेला. त्या अजून निकालांवरून तपासलेल्या नाहीत, म्हणून इतरत्र दाखवलेला मुख्य विश्वास तसाच आहे."),
            color = V8.Text3, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp)
        )
    }

    // ================= CROSS-MARKET
    val xm = b.crossMarket
    V8Card(level = 2) {
        V8Label(tr(lang, "Cross-market (measured relation with gold)", "क्रॉस-मार्केट (सोने से नापा गया रिश्ता)", "क्रॉस-मार्केट (सोन्याशी मोजलेला संबंध)"))
        if (!xm.available) {
            Text(text = "CROSS-MARKET DATA UNAVAILABLE", color = V8.Text3, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                listOf("" to 0.24f, "24H" to 0.17f, "30D" to 0.15f, "7D" to 0.15f, tr(lang, "IMPLIES", "संकेत", "संकेत") to 0.29f).forEach { (h, w) ->
                    Text(text = h, color = V8.Text3, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(w))
                }
            }
            xm.assets.forEach { x ->
                if (x.health == "UNAVAILABLE") {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(text = x.key, color = V8.Text3, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.24f))
                        Text(text = tr(lang, "unavailable", "उपलब्ध नहीं", "उपलब्ध नाही"), color = V8.Text3, fontSize = 10.sp, modifier = Modifier.weight(0.76f))
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = x.key + if (x.health == "STALE") "*" else "", color = V8.Text1, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.24f))
                        Text(text = String.format(Locale.US, "%+.2f%%", x.change24hPct), color = if (x.change24hPct >= 0) V8.Green else V8.Red, fontSize = 10.sp, modifier = Modifier.weight(0.17f))
                        Text(text = if (x.relation == "UNKNOWN") "--" else String.format(Locale.US, "%+.2f", x.corr30d), color = V8.Text1, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.15f))
                        Text(text = if (x.trend == "UNKNOWN") "--" else String.format(Locale.US, "%+.2f", x.corr7d), color = V8.Text2, fontSize = 10.sp, modifier = Modifier.weight(0.15f))
                        val imp = x.implied
                        Text(
                            text = if (x.divergence) "⚠ " + tr(lang, "BREAK", "टूटा", "तुटला") else if (imp == null) "—" else if (imp == Signal.BUY) tr(lang, "gold ↑", "सोना ↑", "सोने ↑") else tr(lang, "gold ↓", "सोना ↓", "सोने ↓"),
                            color = if (x.divergence) V8.Amber else if (imp == null) V8.Text3 else signalColor(imp),
                            fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.29f)
                        )
                    }
                    if (x.trend == "FLIPPED" || x.trend == "WEAKENING" || x.trend == "STRENGTHENING") {
                        Text(text = "   " + x.note, color = V8.Text3, fontSize = 9.sp)
                    }
                }
            }
            xm.divergences.forEach { Text(text = "⚠ $it", color = V8.Amber, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp)) }
            Text(
                text = tr(lang, "30D / 7D = correlation of hourly returns with gold futures. No fixed rule like \"dollar up = gold down\": only what was measured. Source: Yahoo Finance • checked ",
                    "30D / 7D = सोने के फ्यूचर्स के साथ घंटे-घंटे की चाल का कोरिलेशन। \"डॉलर ऊपर = सोना नीचे\" जैसा तय नियम नहीं: सिर्फ जो नापा गया। स्रोत: Yahoo Finance • जाँचा ",
                    "30D / 7D = सोन्याच्या फ्युचर्ससोबत तासागणिक चालीचे कोरिलेशन. \"डॉलर वर = सोने खाली\" असा ठराविक नियम नाही: फक्त जे मोजले. स्रोत: Yahoo Finance • तपासले ") +
                    fmtAge(System.currentTimeMillis() - xm.fetchedAtMs) + tr(lang, " ago", " पहले", " आधी"),
                color = V8.Text3, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    // ================= ANOMALY
    V8Card(level = 2, accent = if (b.anomaly.severity >= 2) V8.Red.copy(alpha = 0.5f) else V8.Line) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            V8Label(tr(lang, "Anomaly detector", "गड़बड़ी डिटेक्टर", "विसंगती डिटेक्टर"))
            Spacer(modifier = Modifier.weight(1f))
            V8Badge(b.anomaly.level, levelColor(b.anomaly.level))
        }
        b.anomaly.checks.forEach { c ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp)) {
                Text(text = if (c.severity == 0) "✓" else "⚠", color = if (c.severity == 0) V8.Green else if (c.severity == 1) V8.Amber else V8.Red, fontSize = 11.sp, modifier = Modifier.width(18.dp))
                Text(text = c.name, color = V8.Text1, fontSize = 10.sp, modifier = Modifier.weight(0.5f))
                Text(text = c.value, color = V8.Text3, fontSize = 10.sp, modifier = Modifier.weight(0.5f))
            }
        }
        Text(text = tr(lang, "Compared with this chart's own last 50 candles, so limits follow volatility.", "इसी चार्ट की पिछली 50 कैंडल से तुलना, इसलिए सीमा वोलैटिलिटी के साथ बदलती है।", "याच चार्टच्या मागील 50 कँडलशी तुलना, म्हणून मर्यादा व्होलॅटिलिटीसोबत बदलते."), color = V8.Text3, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
    }

    // ================= ANALOGS + FAMILIARITY
    V8Card(level = 2) {
        V8Label(tr(lang, "Historical analogs", "पुराने मिलते-जुलते हालात", "जुन्या समान स्थिती"), V8.Learn)
        if (b.analogs.status == "OK") {
            V8ThreeWayBar(b.analogs.upPct, b.analogs.sidePct, b.analogs.downPct)
            Text(text = "↑ ${b.analogs.upPct}%   ↔ ${b.analogs.sidePct}%   ↓ ${b.analogs.downPct}%", color = V8.Text1, fontSize = 12.sp, fontWeight = FontWeight.Black)
            V8KeyValue(tr(lang, "Average real move", "औसत असली चाल", "सरासरी खरी हालचाल"), String.format(Locale.US, "%+.2f", b.analogs.avgMove))
            V8KeyValue(tr(lang, "Similarity score", "समानता स्कोर", "समानता स्कोर"), "${b.analogs.similarity}/100")
        }
        Text(text = b.analogs.note, color = V8.Text3, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(modifier = Modifier.height(6.dp))
        V8KeyValue(tr(lang, "Market familiarity", "मार्केट की पहचान", "मार्केटची ओळख"),
            if (b.familiarity.pct < 0) tr(lang, "UNKNOWN (history ", "अज्ञात (इतिहास ", "अज्ञात (इतिहास ") + "${b.familiarity.historySize}/40)" else "${b.familiarity.pct}% • ${b.familiarity.status.replace('_', ' ')}",
            levelColor(b.familiarity.status))
        if (b.familiarity.outside.isNotEmpty()) Text(text = tr(lang, "Outside usual range: ", "सामान्य सीमा से बाहर: ", "नेहमीच्या मर्यादेबाहेर: ") + b.familiarity.outside.joinToString(", "), color = V8.Amber, fontSize = 10.sp)
    }

    // ================= RESEARCH (verified)
    V8Card(level = 3, onClick = { showResearch = !showResearch }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            V8Label(tr(lang, "Research: verified results", "रिसर्च: जाँचे हुए नतीजे", "रिसर्च: तपासलेले निकाल"), V8.Learn)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = if (showResearch) "▴" else "▾", color = V8.Text3, fontSize = 12.sp)
        }
        V8KeyValue(tr(lang, "Coverage", "कवरेज", "कव्हरेज"), b.coverage)
        b.strongSignal.forEach { V8KeyValue(it.label + tr(lang, " → really right", " → सच में सही", " → खरेच बरोबर"), it.value) }
        if (showResearch) {
            Spacer(modifier = Modifier.height(6.dp))
            V8Label(tr(lang, "By day (UTC)", "दिन के हिसाब से (UTC)", "दिवसानुसार (UTC)"))
            b.dayOfWeek.forEach { V8KeyValue(it.label, it.value) }
            Spacer(modifier = Modifier.height(6.dp))
            V8Label(tr(lang, "By session (UTC)", "सेशन के हिसाब से (UTC)", "सेशननुसार (UTC)"))
            b.sessions.forEach { V8KeyValue(it.label, it.value) }
            Text(text = tr(lang, "A number is shown only after 20+ decided predictions in that bucket.", "संख्या तभी दिखती है जब उस हिस्से में 20+ तय भविष्यवाणियाँ हों।", "त्या गटात 20+ ठरलेले अंदाज झाल्यावरच आकडा दिसतो."), color = V8.Text3, fontSize = 9.sp)
        }
    }

    // ================= FEATURE STORE
    V8Card(level = 3, onClick = { showFeatures = !showFeatures }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            V8Label(tr(lang, "Feature store", "फ़ीचर स्टोर", "फीचर स्टोर"))
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "${b.featuresStored} " + tr(lang, "saved", "सेव", "सेव्ह") + " • ${b.featuresNow} " + tr(lang, "now", "अभी", "आता") + " • ${b.featureVersion}  " + if (showFeatures) "▴" else "▾", color = V8.Text2, fontSize = 10.sp)
        }
        Text(text = tr(lang, "Every recorded prediction saves these numbers. Analogs, familiarity and the coming ML / ablation tests learn from them.",
            "हर रिकॉर्ड हुई भविष्यवाणी ये नंबर सेव करती है। एनालॉग, पहचान और आने वाले ML / एब्लेशन टेस्ट इन्हीं से सीखेंगे।",
            "प्रत्येक नोंदवलेला अंदाज हे आकडे सेव्ह करतो. अॅनालॉग, ओळख आणि येणाऱ्या ML / अॅब्लेशन टेस्ट यांवरून शिकतील."), color = V8.Text3, fontSize = 9.sp)
        if (showFeatures) {
            b.featureDefs.forEach { d ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp)) {
                    Text(text = d.name, color = V8.Text1, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.4f))
                    Text(text = d.formula, color = V8.Text3, fontSize = 9.sp, modifier = Modifier.weight(0.6f))
                }
            }
        }
    }

    // ================= DATA LINEAGE
    V8Card(level = 3, onClick = { showLineage = !showLineage }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            V8Label(tr(lang, "Data lineage: where this came from", "डेटा की जड़: यह कहाँ से आया", "डेटा मूळ: हे कुठून आले"))
            Spacer(modifier = Modifier.weight(1f))
            Text(text = if (showLineage) "▴" else "▾", color = V8.Text3, fontSize = 12.sp)
        }
        if (showLineage) b.lineage.forEach { V8KeyValue(it.label, it.value) }
    }
}
