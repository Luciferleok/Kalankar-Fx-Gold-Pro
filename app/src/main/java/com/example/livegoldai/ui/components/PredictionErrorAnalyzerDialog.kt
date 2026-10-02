package com.example.livegoldai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.model.AccuracyWindow
import com.example.livegoldai.model.BucketStat
import com.example.livegoldai.model.CorrectionCandidate
import com.example.livegoldai.model.FailureReport
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.LearningSnapshot
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*
import java.util.Locale

/**
 * 🧠 PREDICTION LEARNING CENTER
 * (keeps the old function name so existing buttons still open it)
 *
 * Every number on this screen comes from the on-device Prediction Ledger:
 * predictions the app really showed, checked against real prices after they expired.
 */
@Composable
fun PredictionErrorAnalyzerDialog(
    analysis: GoldAnalysisResult,
    onDismiss: () -> Unit,
    onRecalibrate: () -> Unit = {}
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 20.dp)
                .testTag("prediction_error_analyzer_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = ObsidianBackground,
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f))
        ) {
            LearningCenterContent(analysis = analysis, onRecalibrate = onRecalibrate, onDismiss = onDismiss)
        }
    }
}

/** The Learning Center itself; used both in the dialog and as the LEARNING tab. */
@Composable
fun LearningCenterContent(
    analysis: GoldAnalysisResult,
    onRecalibrate: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val lang = LocalAppLanguage.current
    var tab by remember { mutableIntStateOf(0) }
    var recalPressed by remember { mutableStateOf(false) }
    val s = analysis.learning

    val tabs = listOf(
        t(lang, "OVERVIEW", "सारांश", "सारांश"),
        t(lang, "ACCURACY", "सटीकता", "अचूकता"),
        t(lang, "FAILURES", "गलतियाँ", "चुका"),
        t(lang, "CORRECTIONS", "सुधार", "सुधारणा"),
        t(lang, "LIVE LEARNING", "लाइव लर्निंग", "लाइव्ह लर्निंग"),
        t(lang, "MODEL HISTORY", "मॉडल इतिहास", "मॉडेल इतिहास")
    )

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "🧠", fontSize = 22.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = t(lang, "PREDICTION LEARNING CENTER", "प्रेडिक्शन लर्निंग सेंटर", "प्रेडिक्शन लर्निंग सेंटर"),
                    color = GoldLight, fontSize = 15.sp, fontWeight = FontWeight.Black
                )
                Text(
                    text = t(lang, "Real results only • nothing typed in by hand", "सिर्फ असली नतीजे • कोई नंबर हाथ से नहीं", "फक्त खरे निकाल • कोणताही आकडा हाताने नाही"),
                    color = TextSecondary, fontSize = 10.sp
                )
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = { onDismiss() },
                    modifier = Modifier.size(34.dp).background(ObsidianSurfaceElevated, CircleShape).testTag("close_analyzer_dialog")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        // tabs
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEachIndexed { i, label ->
                val selected = i == tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) GoldPrimary else ObsidianSurfaceElevated)
                        .clickable { tab = i }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(text = label, color = if (selected) Color.Black else TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (s == null) {
                Note(t(lang,
                    "The learning engine is starting. Predictions are recorded from the next refresh and checked when their validity time ends.",
                    "लर्निंग इंजन शुरू हो रहा है। अगले रिफ्रेश से प्रेडिक्शन रिकॉर्ड होंगी और उनका समय खत्म होने पर जाँची जाएँगी।",
                    "लर्निंग इंजिन सुरू होत आहे. पुढील रिफ्रेशपासून अंदाज नोंदवले जातील आणि त्यांची वेळ संपल्यावर तपासले जातील."))
            } else {
                when (tab) {
                    0 -> OverviewTab(s, lang, analysis.pulse)
                    1 -> AccuracyTab(s, lang)
                    2 -> FailuresTab(s, lang)
                    3 -> CorrectionsTab(s, lang)
                    4 -> LiveTab(s, lang)
                    else -> HistoryTab(s, lang)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { recalPressed = true; onRecalibrate() },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black)
        ) {
            Text(
                text = t(lang, "🔄 RUN SAFE RECALIBRATION", "🔄 सुरक्षित री-कैलिब्रेशन चलाएँ", "🔄 सुरक्षित री-कॅलिब्रेशन चालवा"),
                fontWeight = FontWeight.Black, fontSize = 12.sp
            )
        }
        Text(
            text = t(lang,
                "Checks expired predictions, looks for patterns, starts shadow tests. Never changes the live model directly.",
                "समय खत्म हुई प्रेडिक्शन जाँचता है, पैटर्न खोजता है, शैडो टेस्ट शुरू करता है। लाइव मॉडल को सीधे कभी नहीं बदलता।",
                "वेळ संपलेले अंदाज तपासते, पॅटर्न शोधते, शॅडो टेस्ट सुरू करते. लाइव्ह मॉडेल थेट कधीच बदलत नाही."),
            color = TextMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp)
        )
        if (recalPressed) {
            Text(
                text = t(lang,
                    "Checking expired predictions… the result appears in LIVE LEARNING after the next refresh.",
                    "समय खत्म हुई प्रेडिक्शन जाँची जा रही हैं… नतीजा अगले रिफ्रेश के बाद लाइव लर्निंग में दिखेगा।",
                    "वेळ संपलेले अंदाज तपासले जात आहेत… निकाल पुढील रिफ्रेशनंतर लाइव्ह लर्निंगमध्ये दिसेल."),
                color = TextMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ------------------------------------------------------------------ tabs

@Composable
private fun OverviewTab(s: LearningSnapshot, lang: AppLanguage, pulse: com.example.livegoldai.model.LedgerPulse? = null) {
    val d30 = s.windows.firstOrNull { it.label == "30 Days" }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Tile(t(lang, "CHECKED", "जाँची गईं", "तपासले"), "${s.correct + s.wrong}", GoldLight, Modifier.weight(1f))
        Tile(t(lang, "CORRECT", "सही", "बरोबर"), "${s.correct}", SignalBuy, Modifier.weight(1f))
        Tile(t(lang, "WRONG", "गलत", "चूक"), "${s.wrong}", SignalSell, Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Tile(t(lang, "NO EDGE", "कोई नतीजा नहीं", "निकाल नाही"), "${s.sideways}", TextSecondary, Modifier.weight(1f))
        Tile(t(lang, "ACTIVE", "चल रही", "चालू"), "${s.active}", GoldPrimary, Modifier.weight(1f))
        Tile(t(lang, "30D ACC.", "30 दिन", "30 दिवस"), d30?.let { pct(it) } ?: "--", GoldLight, Modifier.weight(1f))
    }
    Spacer(modifier = Modifier.height(10.dp))
    InfoRow(t(lang, "Model", "मॉडल", "मॉडेल"), s.modelVersion)
    InfoRow(t(lang, "Engine state", "इंजन स्थिति", "इंजिन स्थिती"), s.engineState.replace('_', ' '))
    InfoRow(t(lang, "Streak", "लगातार", "सलग"), streak(s.currentStreak) + "  •  " + t(lang, "best", "सबसे अच्छा", "सर्वोत्तम") + " +${s.bestStreak}")
    InfoRow(t(lang, "WAIT calls", "WAIT सिग्नल", "WAIT सिग्नल"), "${s.waitCalls} (${s.waitAvoidedMove} " + t(lang, "flat", "सपाट", "सपाट") + ", ${s.waitMissedMove} " + t(lang, "missed a move", "मूव छूटा", "मूव्ह चुकला") + ")")
    InfoRow(t(lang, "Not counted", "गिनती में नहीं", "मोजले नाही"), "${s.marketClosed} " + t(lang, "weekend", "वीकेंड", "वीकेंड") + ", ${s.dataFailures} " + t(lang, "no data", "डेटा नहीं", "डेटा नाही"))
    InfoRow(t(lang, "Next check", "अगली जाँच", "पुढील तपासणी"), s.pendingNote)
    pulse?.let { p ->
        InfoRow(t(lang, "Recorder", "रिकॉर्डर", "रेकॉर्डर"), p.status.replace('_', ' '))
        InfoRow(t(lang, "Last recorded", "आखिरी रिकॉर्ड", "शेवटची नोंद"), p.lastRecorded)
        InfoRow(t(lang, "Last check", "आखिरी जाँच", "शेवटची तपासणी"), p.lastCheck)
        InfoRow(t(lang, "Ledger started", "लेजर शुरू", "लेजर सुरू"), p.ledgerStarted)
    }

    Section(t(lang, "TODAY", "आज", "आज"))
    WindowRow(s.today, lang)
    val bestIv = s.byInterval.filter { it.decided >= 10 }.maxByOrNull { it.accuracyPercent }
    val worstIv = s.byInterval.filter { it.decided >= 10 }.minByOrNull { it.accuracyPercent }
    val bestReg = s.byRegime.filter { it.decided >= 10 }.maxByOrNull { it.accuracyPercent }
    val worstReg = s.byRegime.filter { it.decided >= 10 }.minByOrNull { it.accuracyPercent }
    InfoRow(t(lang, "Best timeframe", "सबसे अच्छा टाइमफ्रेम", "सर्वोत्तम टाइमफ्रेम"), bestIv?.let { "${it.label} ${pctB(it)}" } ?: t(lang, "needs 10+ results", "10+ नतीजे चाहिए", "10+ निकाल हवे"))
    InfoRow(t(lang, "Weakest timeframe", "सबसे कमजोर टाइमफ्रेम", "सर्वात कमकुवत टाइमफ्रेम"), worstIv?.let { "${it.label} ${pctB(it)}" } ?: "--")
    InfoRow(t(lang, "Best market type", "सबसे अच्छा मार्केट", "सर्वोत्तम मार्केट"), bestReg?.let { "${it.label} ${pctB(it)}" } ?: "--")
    InfoRow(t(lang, "Weakest market type", "सबसे कमजोर मार्केट", "सर्वात कमकुवत मार्केट"), worstReg?.let { "${it.label} ${pctB(it)}" } ?: "--")
    InfoRow(t(lang, "Top failure cause", "सबसे आम गलती", "सर्वात सामान्य चूक"), s.failureClusters.firstOrNull()?.let { "${it.label} (${it.decided})" } ?: "--")

    Section(t(lang, "HOW A PREDICTION IS JUDGED", "प्रेडिक्शन कैसे जाँची जाती है", "अंदाज कसा तपासला जातो"))
    Note(t(lang,
        "Each new candle, the main BUY/SELL/WAIT is saved once. When its validity time ends, real minute prices (PAXG/USDT, backup: gold futures) are fetched. CORRECT = price moved at least 0.25×ATR in the predicted direction. WRONG = it moved that much the other way, or the stop-loss distance was hit first. Smaller moves = NO EDGE (not counted). WAIT calls are tracked separately. Weekend gaps are excluded.",
        "हर नई कैंडल पर मुख्य BUY/SELL/WAIT एक बार सेव होता है। उसका समय खत्म होने पर असली मिनट-भाव (PAXG/USDT, बैकअप: गोल्ड फ्यूचर्स) लिए जाते हैं। सही = भाव अनुमानित दिशा में कम से कम 0.25×ATR चला। गलत = उतना ही उल्टा चला, या पहले स्टॉप-लॉस दूरी छू गई। इससे छोटा मूव = कोई नतीजा नहीं (गिना नहीं)। WAIT अलग गिने जाते हैं। वीकेंड हटाया जाता है।",
        "प्रत्येक नवीन कँडलवर मुख्य BUY/SELL/WAIT एकदा जतन होतो. त्याची वेळ संपल्यावर खरे मिनिट-भाव (PAXG/USDT, बॅकअप: गोल्ड फ्युचर्स) घेतले जातात. बरोबर = भाव अंदाजित दिशेने किमान 0.25×ATR गेला. चूक = तितकाच उलट गेला किंवा आधी स्टॉप-लॉस अंतर लागले. लहान हालचाल = निकाल नाही (मोजले नाही). WAIT वेगळे मोजले जातात. वीकेंड वगळला जातो."))
}

@Composable
private fun AccuracyTab(s: LearningSnapshot, lang: AppLanguage) {
    Section(t(lang, "THIS TIMEFRAME", "यह टाइमफ्रेम", "हा टाइमफ्रेम"))
    s.windows.forEach { WindowRow(it, lang) }

    Section(t(lang, "PROBABILITY QUALITY", "प्रॉबेबिलिटी गुणवत्ता", "प्रॉबॅबिलिटी गुणवत्ता"))
    InfoRow("Brier (all / 7D / 30D)", "${brier(s.brierAll)} / ${brier(s.brier7d)} / ${brier(s.brier30d)}")
    InfoRow(t(lang, "Calibration error", "कैलिब्रेशन गलती", "कॅलिब्रेशन चूक"),
        if (s.calibrationErrorPoints < 0) t(lang, "needs 10+ per band", "हर बैंड में 10+ चाहिए", "प्रत्येक बँडमध्ये 10+ हवे") else String.format(Locale.US, "%.1f points", s.calibrationErrorPoints))
    Note(t(lang, "Lower Brier is better (0.25 = coin flip). Calibration compares shown confidence with real hit-rate.",
        "Brier जितना कम उतना अच्छा (0.25 = सिक्का उछालना)। कैलिब्रेशन दिखाए गए कॉन्फिडेंस की असली सफलता से तुलना करता है।",
        "Brier जितका कमी तितके चांगले (0.25 = नाणेफेक). कॅलिब्रेशन दाखवलेला कॉन्फिडन्स खऱ्या यशाशी तुलना करते."))
    s.calibration.forEach { b ->
        val verdict = when {
            b.decided < 10 -> t(lang, "not enough data", "डेटा कम", "डेटा कमी")
            b.avgConfidence - b.accuracyPercent > 8 -> t(lang, "OVERCONFIDENT", "ज़रूरत से ज़्यादा भरोसा", "जास्त आत्मविश्वास")
            b.accuracyPercent - b.avgConfidence > 8 -> t(lang, "UNDERCONFIDENT", "कम भरोसा", "कमी आत्मविश्वास")
            else -> t(lang, "well calibrated", "सही कैलिब्रेटेड", "योग्य कॅलिब्रेटेड")
        }
        BucketLine(t(lang, "Shown ", "दिखाया ", "दाखवले ") + b.label, b, verdict)
    }

    Section(t(lang, "BY TIMEFRAME (never mixed)", "टाइमफ्रेम के अनुसार", "टाइमफ्रेमनुसार"))
    s.byInterval.forEach { BucketLine(it.label.uppercase(), it, "") }
    Section(t(lang, "BY SESSION (all timeframes)", "सेशन के अनुसार (सभी टाइमफ्रेम)", "सेशननुसार (सर्व टाइमफ्रेम)"))
    s.bySession.forEach { BucketLine(it.label, it, "") }
    Section(t(lang, "BY MARKET TYPE", "मार्केट प्रकार के अनुसार", "मार्केट प्रकारानुसार"))
    s.byRegime.forEach { BucketLine(it.label, it, "") }
    Section(t(lang, "NEWS vs NORMAL", "न्यूज़ बनाम सामान्य", "न्यूज विरुद्ध सामान्य"))
    s.byNews.forEach { BucketLine(it.label, it, "") }

    Section(t(lang, "EVERY SIGNAL SOURCE (direction only)", "हर सिग्नल स्रोत (सिर्फ दिशा)", "प्रत्येक सिग्नल स्रोत (फक्त दिशा)"))
    Note(t(lang, "Pillars, the 5 local rules, the 6 rule bots and the playbook are each scored on the same real price moves.",
        "पिलर, 5 लोकल नियम, 6 नियम-बॉट और प्लेबुक, सबको उन्हीं असली मूव पर अंक मिलते हैं।",
        "पिलर, 5 लोकल नियम, 6 नियम-बॉट आणि प्लेबुक, सर्वांना त्याच खऱ्या हालचालींवर गुण मिळतात."))
    s.bySource.forEach { BucketLine(sourceName(it.key), it, "") }
}

@Composable
private fun FailuresTab(s: LearningSnapshot, lang: AppLanguage) {
    if (s.failureClusters.isNotEmpty()) {
        Section(t(lang, "FAILURE CLUSTERS", "गलतियों के समूह", "चुकांचे गट"))
        s.failureClusters.forEach { InfoRow(it.label, "${it.decided}×") }
        Note(t(lang, "Tags are measured facts at the time of the prediction, not guesses. UNKNOWN is allowed.",
            "टैग प्रेडिक्शन के समय के मापे गए तथ्य हैं, अंदाज़ नहीं। UNKNOWN भी मान्य है।",
            "टॅग अंदाजाच्या वेळचे मोजलेले तथ्य आहेत, अंदाज नाही. UNKNOWN सुद्धा मान्य आहे."))
    }
    Section(t(lang, "LATEST WRONG PREDICTIONS", "हाल की गलत प्रेडिक्शन", "अलीकडील चुकीचे अंदाज"))
    if (s.failures.isEmpty()) {
        Note(t(lang, "No wrong prediction recorded yet.", "अभी तक कोई गलत प्रेडिक्शन दर्ज नहीं।", "अजून कोणताही चुकीचा अंदाज नोंदलेला नाही."))
    }
    s.failures.forEach { FailureCard(it, lang) }
}

@Composable
private fun FailureCard(f: FailureReport, lang: AppLanguage) {
    var open by remember(f.id) { mutableStateOf(false) }
    val sigColor = if (f.signal == Signal.BUY) SignalBuy else SignalSell
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurfaceCard)
            .border(1.dp, SignalSell.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable { open = !open }
            .padding(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "${f.signal.name} ${f.confidence}%", color = sigColor, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "${f.interval.uppercase()} • ${f.createdLabel}", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.weight(1f))
            Text(text = f.outcome, color = SignalSell, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        }
        Text(
            text = String.format(Locale.US, "Entry %.2f • move %+.2f (needed %.2f) • best +%.2f • worst -%.2f", f.entryPrice, f.movePoints, f.thresholdPoints, f.maxFavorable, f.maxAdverse),
            color = TextPrimary, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp)
        )
        Text(
            text = f.attribution.entries.joinToString("  •  ") { "${it.key.replace('_', ' ')} ${it.value}%" },
            color = GoldLight, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp)
        )
        Text(text = "${f.regime.replace('_', ' ')} • ${f.session.replace('_', ' ')}${if (f.newsActive) " • NEWS" else ""}", color = TextMuted, fontSize = 9.sp)
        if (open) {
            if (f.warningsIgnored.isNotEmpty()) {
                Text(text = t(lang, "Warned the other way (and were right): ", "उल्टा बताया था (और सही थे): ", "उलट सांगितले होते (आणि बरोबर होते): ") + f.warningsIgnored.joinToString(", "),
                    color = AmberWarning, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
            Text(text = "↪ ${f.counterfactual}", color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            Text(text = t(lang, "REPLAY", "रिप्ले", "रिप्ले") + " (${f.verifySource})", color = GoldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            f.timeline.forEach { ev ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                    Text(text = ev.timeLabel, color = TextMuted, fontSize = 9.sp, modifier = Modifier.width(96.dp))
                    Text(text = ev.text, color = TextPrimary, fontSize = 9.sp, modifier = Modifier.weight(1f))
                }
            }
        } else {
            Text(text = t(lang, "Tap for replay ▾", "रिप्ले के लिए टैप करें ▾", "रिप्लेसाठी टॅप करा ▾"), color = TextMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
private fun CorrectionsTab(s: LearningSnapshot, lang: AppLanguage) {
    Note(t(lang,
        "Each candidate is a simple rule that turns a BUY/SELL into WAIT. It needs ${s.minimumSamples}+ past samples where the blocked predictions were clearly worse (statistically, not by luck), then must win again on NEW predictions in a silent shadow test before it is used. If it stops helping, it is removed automatically.",
        "हर उम्मीदवार एक आसान नियम है जो BUY/SELL को WAIT में बदलता है। इसके लिए ${s.minimumSamples}+ पुराने सैंपल चाहिए जहाँ रोकी गई प्रेडिक्शन साफ तौर पर (किस्मत से नहीं) खराब थीं, फिर नई प्रेडिक्शन पर चुपचाप शैडो टेस्ट पास करना होता है। काम करना बंद करे तो अपने-आप हट जाता है।",
        "प्रत्येक उमेदवार एक सोपा नियम आहे जो BUY/SELL ला WAIT मध्ये बदलतो. त्यासाठी ${s.minimumSamples}+ जुने नमुने हवे जिथे थांबवलेले अंदाज स्पष्टपणे (नशिबाने नाही) वाईट होते, मग नवीन अंदाजांवर शांतपणे शॅडो टेस्ट पास करावी लागते. उपयोग थांबला तर आपोआप काढला जातो."))
    s.candidates.forEach { CandidateCard(it, lang) }
}

@Composable
private fun CandidateCard(c: CorrectionCandidate, lang: AppLanguage) {
    val stageColor = when (c.stage) {
        "PROMOTED" -> SignalBuy
        "SHADOW" -> GoldPrimary
        "CANDIDATE" -> AmberWarning
        "REJECTED", "ROLLED_BACK" -> SignalSell
        else -> TextMuted
    }
    val title = when (lang) { AppLanguage.ENGLISH -> c.titleEnglish; AppLanguage.HINDI -> c.titleHindi; AppLanguage.MARATHI -> c.titleMarathi }
    val needed = if (c.stage == "SHADOW") c.requiredSamples else c.requiredSamples
    val have = if (c.stage == "SHADOW") c.shadowSamples else c.affectedSamples
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurfaceCard)
            .border(1.dp, stageColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "${c.id}  $title", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(text = c.stage.replace('_', ' '), color = stageColor, fontWeight = FontWeight.Black, fontSize = 10.sp)
        }
        Text(text = c.ruleEnglish, color = TextSecondary, fontSize = 10.sp)
        LinearProgressIndicator(
            progress = { (have.toFloat() / needed.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(4.dp),
            color = stageColor,
            trackColor = ObsidianBorder
        )
        Text(
            text = (if (c.stage == "SHADOW") t(lang, "Shadow samples ", "शैडो सैंपल ", "शॅडो नमुने ") else t(lang, "Samples ", "सैंपल ", "नमुने ")) + "$have/$needed",
            color = TextMuted, fontSize = 9.sp
        )
        if (c.affectedSamples > 0) {
            Text(
                text = t(lang, "Blocked ones were ", "रोकी गईं सही थीं ", "थांबवलेले बरोबर होते ") + pctD(c.blockedAccuracy) +
                    t(lang, " correct • now ", " • अभी ", " • सध्या ") + pctD(c.baselineAccuracy) + " → " + pctD(c.keptAccuracy),
                color = TextPrimary, fontSize = 10.sp
            )
        }
        Text(text = necessityText(c.necessity, lang), color = stageColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        if (c.note.isNotBlank()) Text(text = c.note, color = TextMuted, fontSize = 9.sp)
    }
}

@Composable
private fun LiveTab(s: LearningSnapshot, lang: AppLanguage) {
    Section(t(lang, "ENGINE", "इंजन", "इंजिन"))
    InfoRow(t(lang, "State", "स्थिति", "स्थिती"), s.engineState.replace('_', ' '))
    InfoRow(t(lang, "Model", "मॉडल", "मॉडेल"), s.modelVersion)
    InfoRow(t(lang, "Model drift", "मॉडल ड्रिफ्ट", "मॉडेल ड्रिफ्ट"), s.driftLevel.replace('_', ' '))
    Note(s.driftDetail)
    InfoRow(t(lang, "Last update", "आखिरी अपडेट", "शेवटचा अपडेट"), s.generatedAtLabel)

    Section(t(lang, "WHAT THE LAST CYCLE DID", "पिछली साइकिल ने क्या किया", "मागील सायकलने काय केले"))
    s.lastCycleReport.forEach { line ->
        Text(text = "• $line", color = if (line.startsWith("NO SAFE")) GoldLight else TextPrimary, fontSize = 10.sp, modifier = Modifier.padding(vertical = 1.dp))
    }

    Section(t(lang, "SAFETY RULES", "सुरक्षा नियम", "सुरक्षा नियम"))
    Note(t(lang,
        "• One or a few losses never change the model.\n• Only one rule is shadow-tested at a time.\n• At most 3 learned filters can be active.\n• Every change is logged in MODEL HISTORY and can be rolled back.\n• Checks run while the app is open; when you reopen it, missed results are fetched from price history.",
        "• एक या कुछ गलतियों से मॉडल कभी नहीं बदलता।\n• एक समय में सिर्फ एक नियम का शैडो टेस्ट।\n• ज़्यादा से ज़्यादा 3 सीखे फ़िल्टर चालू।\n• हर बदलाव मॉडल इतिहास में दर्ज, वापस लिया जा सकता है।\n• जाँच ऐप खुला रहने पर चलती है; दोबारा खोलने पर छूटे नतीजे प्राइस हिस्ट्री से लिए जाते हैं।",
        "• एक-दोन चुकांनी मॉडेल कधीच बदलत नाही.\n• एका वेळी फक्त एका नियमाची शॅडो टेस्ट.\n• जास्तीत जास्त 3 शिकलेले फिल्टर चालू.\n• प्रत्येक बदल मॉडेल इतिहासात नोंदवला जातो आणि परत घेता येतो.\n• तपासणी ॲप उघडे असताना चालते; पुन्हा उघडल्यावर राहिलेले निकाल किंमत इतिहासातून घेतले जातात."))
}

@Composable
private fun HistoryTab(s: LearningSnapshot, lang: AppLanguage) {
    Section(t(lang, "MODEL HISTORY", "मॉडल इतिहास", "मॉडेल इतिहास"))
    if (s.history.isEmpty()) {
        Note(t(lang, "No model change yet. The base rules are running unchanged.", "अभी तक मॉडल में कोई बदलाव नहीं। बेस नियम बिना बदलाव चल रहे हैं।", "अजून मॉडेलमध्ये बदल नाही. बेस नियम बदलाशिवाय चालू आहेत."))
    }
    s.history.forEach { h ->
        val c = when (h.event) { "PROMOTED" -> SignalBuy; "SHADOW" -> GoldPrimary; else -> SignalSell }
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = h.event.replace('_', ' '), color = c, fontWeight = FontWeight.Black, fontSize = 10.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = h.timeLabel, color = TextMuted, fontSize = 9.sp)
            }
            Text(text = h.detail, color = TextPrimary, fontSize = 10.sp)
        }
    }
}

// ------------------------------------------------------------------ small pieces

@Composable
private fun Tile(label: String, value: String, color: Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurfaceCard)
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(text = label, color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Section(title: String) {
    Text(text = title, color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun Note(text: String) {
    Text(text = text, color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(vertical = 3.dp))
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(0.45f))
        Text(text = value, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.55f))
    }
}

@Composable
private fun WindowRow(w: AccuracyWindow, lang: AppLanguage) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = windowLabel(w.label, lang), color = TextSecondary, fontSize = 10.sp, modifier = Modifier.weight(0.35f))
        LinearProgressIndicator(
            progress = { if (w.decided == 0) 0f else (w.accuracyPercent / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.weight(0.35f).height(5.dp),
            color = if (w.accuracyPercent >= 55) SignalBuy else if (w.accuracyPercent >= 45 || w.decided == 0) GoldPrimary else SignalSell,
            trackColor = ObsidianBorder
        )
        Text(text = "  ${pct(w)}  N=${w.decided}", color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.3f))
    }
}

@Composable
private fun BucketLine(label: String, b: BucketStat, extra: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label, color = TextSecondary, fontSize = 10.sp, modifier = Modifier.weight(0.42f))
        Text(
            text = (if (b.decided == 0) "--" else String.format(Locale.US, "%.1f%%", b.accuracyPercent)) +
                "  N=${b.decided}" + (if (b.ciLowPercent >= 0) String.format(Locale.US, "  (%.0f–%.0f)", b.ciLowPercent, b.ciHighPercent) else ""),
            color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.38f)
        )
        Text(text = extra, color = GoldLight, fontSize = 9.sp, modifier = Modifier.weight(0.2f))
    }
}

private fun t(lang: AppLanguage, en: String, hi: String, mr: String): String = when (lang) {
    AppLanguage.ENGLISH -> en
    AppLanguage.HINDI -> hi
    AppLanguage.MARATHI -> mr
}

private fun pct(w: AccuracyWindow): String = if (w.decided == 0) "--" else String.format(Locale.US, "%.1f%%", w.accuracyPercent)
private fun pctB(b: BucketStat): String = if (b.decided == 0) "--" else String.format(Locale.US, "%.1f%% (N=%d)", b.accuracyPercent, b.decided)
private fun pctD(v: Double): String = if (v < 0) "--" else String.format(Locale.US, "%.1f%%", v)
private fun brier(v: Double): String = if (v < 0) "--" else String.format(Locale.US, "%.3f", v)
private fun streak(n: Int): String = when { n > 0 -> "+$n ✅"; n < 0 -> "$n ❌"; else -> "0" }

private fun windowLabel(label: String, lang: AppLanguage): String = when (lang) {
    AppLanguage.ENGLISH -> label
    else -> when (label) {
        "Last 20" -> "पिछली 20"; "Last 50" -> "पिछली 50"; "Last 100" -> "पिछली 100"; "Last 250" -> "पिछली 250"
        "Today" -> "आज"; "7 Days" -> "7 दिन"; "30 Days" -> "30 दिन"; "All Time" -> "शुरू से"
        else -> label
    }
}

private fun necessityText(n: String, lang: AppLanguage): String = when (n) {
    "CONFIRMED_SYSTEMIC" -> t(lang, "Confirmed pattern • active", "पक्का पैटर्न • चालू", "पक्का पॅटर्न • चालू")
    "LIKELY_SYSTEMIC" -> t(lang, "Likely real pattern", "शायद असली पैटर्न", "बहुधा खरा पॅटर्न")
    "POSSIBLE_ISSUE" -> t(lang, "Possible issue • not proven", "संभावित समस्या • साबित नहीं", "संभाव्य समस्या • सिद्ध नाही")
    "NO_CORRECTION_NEEDED" -> t(lang, "No correction needed", "सुधार की ज़रूरत नहीं", "सुधारणेची गरज नाही")
    else -> t(lang, "Not enough data yet", "अभी डेटा कम", "अजून डेटा कमी")
}

private fun sourceName(key: String): String = when {
    key == "main:raw" -> "Main signal (before filters)"
    key == "playbook" -> "Playbook (Kya Hoga)"
    key == "quant" -> "Rule bot (quant card)"
    key == "bots:ensemble" -> "Bot ensemble"
    key == "mtf:higher" -> "Higher timeframe"
    key.startsWith("grp:") -> "Pillar: " + key.removePrefix("grp:")
    key.startsWith("eng:") -> when (key.removePrefix("eng:")) {
        "GEMINI" -> "Rule 1 Pillar vote"; "CHAT_GPT" -> "Rule 2 Dollar filter"; "CLAUDE" -> "Rule 3 Risk guard"
        "DEEP_SEEK" -> "Rule 4 RSI zone"; "PERPLEXITY" -> "Rule 5 68% gate"; else -> key
    }
    key.startsWith("bot:") -> "Bot: " + key.removePrefix("bot:bot_").replace('_', ' ')
    key.startsWith("mtf:") -> "Timeframe " + key.removePrefix("mtf:")
    else -> key
}
