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
    modifier: Modifier = Modifier,
    shadow: com.example.livegoldai.data.ShadowLab.Report? = null,
    shadowRunning: Boolean = false,
    shadowError: String? = null,
    shadowInterval: String = analysis.interval,
    onRunShadow: ((Boolean) -> Unit)? = null,
    shadowAll: Map<String, com.example.livegoldai.data.ShadowLab.Report> = emptyMap(),
    shadowAllProgress: String? = null,
    shadowLive: List<com.example.livegoldai.data.ShadowLive.Stats> = emptyList(),
    exportNote: String? = null,
    onRunShadowAll: () -> Unit = {},
    onRefreshShadowLive: () -> Unit = {},
    onExportCsv: (android.net.Uri) -> Unit = {},
    onDeepenHistory: () -> Unit = {}
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
    ) + (if (onRunShadow != null) listOf(t(lang, "SHADOW LAB", "शैडो लैब", "शॅडो लॅब")) else emptyList())

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // header
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = t(lang, "PREDICTION LEARNING CENTER", "प्रेडिक्शन लर्निंग सेंटर", "प्रेडिक्शन लर्निंग सेंटर"),
                    color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp
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
                    Text(text = label, color = if (selected) OnAccent else TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            if (tab == 6 && onRunShadow != null) {
                ShadowLabTab(shadow, shadowRunning, shadowError, shadowInterval, s, lang, onRunShadow,
                    shadowAll, shadowAllProgress, shadowLive, exportNote, onRunShadowAll, onRefreshShadowLive, onExportCsv, onDeepenHistory)
            } else if (s == null) {
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

        if (tab != 6) {
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { recalPressed = true; onRecalibrate() },
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(12.dp),
            // dark premium button, gold only as the accent (not a big yellow block)
            colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceElevated, contentColor = GoldLight),
            border = BorderStroke(0.75.dp, GoldPrimary.copy(alpha = 0.6f))
        ) {
            Text(
                text = t(lang, "RUN SAFE RECALIBRATION", "सुरक्षित री-कैलिब्रेशन चलाएँ", "सुरक्षित री-कॅलिब्रेशन चालवा"),
                fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 1.sp
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
        }   // tab != 6 (the Shadow Lab tab has its own buttons)
    }
}

// ------------------------------------------------------------------ tabs

/** V28.4 Shadow Lab: research integrity + live edge proof. Shadow only, never a trade signal. */
@Composable
private fun ShadowLabTab(
    r: com.example.livegoldai.data.ShadowLab.Report?,
    running: Boolean,
    error: String?,
    interval: String,
    s: LearningSnapshot?,
    lang: AppLanguage,
    onRun: (Boolean) -> Unit,
    all: Map<String, com.example.livegoldai.data.ShadowLab.Report>,
    allProgress: String?,
    live: List<com.example.livegoldai.data.ShadowLive.Stats>,
    exportNote: String?,
    onRunAll: () -> Unit,
    onRefreshLive: () -> Unit,
    onExport: (android.net.Uri) -> Unit,
    onDeepen: () -> Unit
) {
    androidx.compose.runtime.LaunchedEffect(interval) { onRun(false); onRefreshLive() }
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) onExport(uri) }
    val busy = running || allProgress != null
    val L = com.example.livegoldai.data.ShadowLab

    Note(t(lang,
        "Research lab + live proof. Models are tested on real past XAU/USD candles they never saw, against simple rules on the same candles, then keep calling every new closed candle in real time. Neighbouring 4-candle outcomes overlap, so proof uses a block bootstrap and a non-overlapping sample. SHADOW ONLY: the live BUY/SELL/WAIT never changes here.",
        "रिसर्च लैब + लाइव सबूत। मॉडल को असली पुरानी XAU/USD कैंडल पर टेस्ट किया जाता है जो उसने नहीं देखीं, उन्हीं कैंडल पर साधारण नियमों से तुलना, फिर हर नई बंद कैंडल पर असली समय में कॉल। पास-पास के 4-कैंडल नतीजे एक-दूसरे में मिलते हैं, इसलिए सबूत block bootstrap और बिना-overlap सैंपल से। सिर्फ शैडो: लाइव BUY/SELL/WAIT यहाँ कभी नहीं बदलता।",
        "रिसर्च लॅब + लाइव्ह पुरावा. मॉडेल खऱ्या जुन्या XAU/USD कँडलवर तपासले जाते जे त्याने पाहिले नाहीत, त्याच कँडलवर साध्या नियमांशी तुलना, मग प्रत्येक नवीन बंद कँडलवर खऱ्या वेळेत कॉल. शेजारचे 4-कँडल निकाल एकमेकांत मिसळतात, म्हणून पुरावा block bootstrap आणि overlap नसलेल्या नमुन्याने. फक्त शॅडो: लाइव्ह BUY/SELL/WAIT इथे कधीच बदलत नाही."))

    // ---------------- trust summary
    Section(t(lang, "WHY TRUST (OR NOT) • REAL NUMBERS", "भरोसा क्यों (या नहीं) • असली नंबर", "विश्वास का (किंवा नाही) • खरे आकडे"))
    s?.let { ls ->
        val d = ls.correct + ls.wrong
        InfoRow(t(lang, "Live engine (real ledger)", "लाइव इंजन (असली लेजर)", "लाइव्ह इंजिन (खरा लेजर)"),
            if (d == 0) "--" else String.format(Locale.US, "%.1f%% (N=%d, all timeframes)", 100.0 * ls.correct / d, d))
    }
    val edges = all.values.filter { it.verdict == "EDGE CANDIDATE" }.map { it.interval }
    val failed = all.values.filter { it.verdict == "RUN FAILED" }.map { it.interval }
    InfoRow(t(lang, "Lab edge candidates", "लैब edge candidate", "लॅब edge candidate"),
        if (all.isEmpty()) t(lang, "not tested yet", "अभी टेस्ट नहीं", "अजून चाचणी नाही")
        else (if (edges.isEmpty()) "none of ${all.size}" else edges.joinToString(", ") + " / ${all.size}") + (if (failed.isNotEmpty()) " • RUN FAILED: " + failed.joinToString(", ") else ""))
    val q = live.filter { it.status == "LIVE QUALIFIED" }.map { it.interval }
    InfoRow(t(lang, "Live qualified", "लाइव qualified", "लाइव्ह qualified"), if (q.isEmpty()) t(lang, "none", "कोई नहीं", "एकही नाही") else q.joinToString(", "))
    Note(t(lang, "Alerts exist only for LIVE QUALIFIED timeframes. Until then the honest answer is WAIT.",
        "अलर्ट सिर्फ LIVE QUALIFIED टाइमफ्रेम के लिए। तब तक ईमानदार जवाब WAIT है।",
        "अलर्ट फक्त LIVE QUALIFIED टाइमफ्रेमसाठी. तोपर्यंत प्रामाणिक उत्तर WAIT आहे."))

    // ---------------- all timeframes
    Section(t(lang, "ALL TIMEFRAMES (LAB)", "सभी टाइमफ्रेम (लैब)", "सर्व टाइमफ्रेम (लॅब)"))
    if (all.isEmpty()) {
        Text(t(lang, "Press TEST ALL to compare 5m, 15m, 1h, 4h and 1d.", "5m, 15m, 1h, 4h, 1d की तुलना के लिए TEST ALL दबाएँ।", "5m, 15m, 1h, 4h, 1d तुलनेसाठी TEST ALL दाबा."), color = TextSecondary, fontSize = 10.sp)
    } else {
        Cols(listOf("TF" to 0.12f, "VERDICT" to 0.30f, "TOP NON-FLAT" to 0.22f, "MATCHED BASE" to 0.22f, "PROOF N" to 0.14f))
        val order = listOf("1m", "5m", "15m", "30m", "45m", "1h", "2h", "4h", "1d", "1w")
        all.values.sortedBy { order.indexOf(it.interval).let { i -> if (i < 0) 99 else i } }.forEach { rep ->
            val top = rep.models.firstOrNull { it.name == rep.topChallenger }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(rep.interval, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.12f))
                Text(rep.verdict, color = verdictColor(rep.verdict), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.30f))
                Text(top?.nonFlatAcc?.let { String.format(Locale.US, "%.1f%%", it) } ?: "--", color = GoldLight, fontSize = 9.sp, modifier = Modifier.weight(0.22f))
                Text(top?.matchedBaselineAcc?.let { String.format(Locale.US, "%.1f%%", it) } ?: "--", color = TextSecondary, fontSize = 9.sp, modifier = Modifier.weight(0.22f))
                Text("${top?.proofN ?: 0}", color = TextSecondary, fontSize = 9.sp, modifier = Modifier.weight(0.14f))
            }
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    DarkButton(allProgress?.let { t(lang, "TESTING $it…", "टेस्ट हो रहा है $it…", "चाचणी सुरू $it…") }
        ?: t(lang, "TEST ALL TIMEFRAMES (5 credits)", "सभी टाइमफ्रेम टेस्ट करें (5 क्रेडिट)", "सर्व टाइमफ्रेम तपासा (5 क्रेडिट)"), !busy, onRunAll)

    // ---------------- live qualification report
    Section(t(lang, "LIVE QUALIFICATION REPORT", "लाइव qualification रिपोर्ट", "लाइव्ह qualification अहवाल"))
    if (live.isEmpty()) {
        Note(t(lang,
            "Starts by itself: the background recorder trains a model once a day per timeframe and then calls every new closed candle. Results appear here as candles close.",
            "अपने आप शुरू होता है: बैकग्राउंड रिकॉर्डर हर टाइमफ्रेम का मॉडल दिन में एक बार ट्रेन करता है, फिर हर नई बंद कैंडल पर कॉल देता है। कैंडल बंद होने पर नतीजे यहाँ दिखेंगे।",
            "आपोआप सुरू होते: बॅकग्राउंड रेकॉर्डर प्रत्येक टाइमफ्रेमचे मॉडेल दिवसातून एकदा ट्रेन करतो, मग प्रत्येक नवीन बंद कँडलवर कॉल देतो. कँडल बंद झाल्यावर निकाल येथे दिसतील."))
    }
    live.forEach { st ->
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(ObsidianSurfaceCard).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(st.interval, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.25f))
            Text(st.status, color = statusColorOf(st.status), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.75f))
        }
        Note(st.statusWhy)
        InfoRow("Lab status", st.labVerdict)
        InfoRow(t(lang, "Raw calls / proof N", "कुल कॉल / proof N", "एकूण कॉल / proof N"), "${st.calls} calls • ${st.decided} decided • ${st.proofN} non-overlapping • ${st.pending} waiting • ${st.lost} lost")
        InfoRow("Coverage", st.coverage?.let { String.format(Locale.US, "%.0f%% of %d checked candles", it * 100, st.eligible) } ?: "--")
        InfoRow("UP / DOWN / FLAT", if (st.eligible == 0) "--" else String.format(Locale.US, "%.0f%% / %.0f%% / %.0f%% of outcomes", st.outcomeShares.first * 100, st.outcomeShares.second * 100, st.outcomeShares.third * 100))
        InfoRow("Non-flat direction acc.", pctOrDash(st.nonFlatAcc) + (if (st.bootLow != null) String.format(Locale.US, " (block 95%%: %.0f-%.0f)", st.bootLow, st.bootHigh ?: 0.0) else "") + "  • proof " + pctOrDash(st.proofAcc))
        InfoRow("Directional call success", pctOrDash(st.callSuccess) + "  • flat call rate " + pctOrDash(st.flatRate))
        InfoRow("Balanced acc. / UP-call share", pctOrDash(st.balancedAcc) + " / " + pctOrDash(st.upCallShare))
        InfoRow("Matched live baselines", st.matched.joinToString(" • ") { b -> b.name.lowercase(Locale.US) + " " + pctOrDash(b.acc) } +
            (st.pairedDiffLow?.let { String.format(Locale.US, " • paired low %+.1f pts", it) } ?: ""))
        InfoRow("Net research scenarios", L.COSTS.mapIndexed { i, c -> c.first.lowercase(Locale.US) + " " + (st.netByCost.getOrNull(i)?.let { String.format(Locale.US, "%+.2f", it) } ?: "--") }.joinToString(" • "))
        InfoRow("Calibration", st.calibrationState)
        if (st.calibrationNote.isNotBlank()) Note(st.calibrationNote)
        InfoRow("WAIT", (st.waitRate?.let { String.format(Locale.US, "%.0f%% of candles", it) } ?: "--") +
            (st.waitFlatShare?.let { String.format(Locale.US, " • %.0f%% were really flat", it) } ?: "") +
            (if (st.waitReasons.isNotEmpty()) " • " + st.waitReasons.entries.joinToString { "${it.key} ${it.value}" } else ""))
        InfoRow("Model / recipe", "${st.modelId} • ${st.recipe} • ${st.trainingRange}")
        InfoRow(t(lang, "Last shadow decision", "आखिरी शैडो फैसला", "शेवटचा शॅडो निर्णय"), st.lastCall)
    }

    // ---------------- selected timeframe lab detail
    Section(t(lang, "THIS TIMEFRAME (LAB): $interval", "यह टाइमफ्रेम (लैब): $interval", "हा टाइमफ्रेम (लॅब): $interval"))
    if (running) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = GoldPrimary, trackColor = ObsidianSurfaceElevated)
        Text(text = t(lang, "Downloading new candles and testing… ($interval)", "नई कैंडल डाउनलोड और टेस्ट… ($interval)", "नवीन कँडल डाउनलोड व चाचणी… ($interval)"), color = TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
    if (error != null && !busy) Text(text = t(lang, "Could not run: ", "नहीं चला: ", "चालले नाही: ") + error, color = SignalSell, fontSize = 10.sp, modifier = Modifier.padding(vertical = 4.dp))
    if (r == null || r.interval != interval) {
        if (!running) Text(t(lang, "No test for $interval yet.", "$interval का टेस्ट अभी नहीं हुआ।", "$interval ची चाचणी अजून नाही."), color = TextSecondary, fontSize = 10.sp)
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tile(t(lang, "VERDICT", "नतीजा", "निकाल"), r.verdict, verdictColor(r.verdict), Modifier.weight(1.5f))
            Tile(t(lang, "TEST CANDLES", "टेस्ट कैंडल", "चाचणी कँडल"), "${r.models.firstOrNull()?.samples ?: 0}", GoldLight, Modifier.weight(1f))
            Tile(t(lang, "FOLDS", "फोल्ड", "फोल्ड"), "${r.folds}", GoldLight, Modifier.weight(0.7f))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Note(r.verdictDetail)

        Section(t(lang, "PRE-REGISTERED CHECKS • ${r.topChallenger}", "पहले से तय जाँच • ${r.topChallenger}", "आधी ठरवलेल्या तपासण्या • ${r.topChallenger}"))
        r.checks.forEach { c ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(if (c.pass) "✓" else "✗", color = if (c.pass) SignalBuy else SignalSell, fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(c.name, color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(c.detail, color = TextMuted, fontSize = 9.sp)
                }
            }
        }
        InfoRow(t(lang, "Economics", "खर्च के बाद", "खर्चानंतर"), r.economics)

        Section(t(lang, "TOURNAMENT (same unseen candles)", "मुकाबला (वही अनदेखी कैंडल)", "स्पर्धा (त्याच न पाहिलेल्या कँडल)"))
        Cols(listOf("MODEL" to 0.30f, "NON-FLAT" to 0.17f, "CALL OK" to 0.15f, "FLAT" to 0.12f, "COVER" to 0.12f, "BOOT LOW" to 0.14f))
        val kindOrder = mapOf("CHALLENGER" to 0, "BASELINE" to 1, "CONTROL" to 2)
        r.models.sortedWith(compareBy<com.example.livegoldai.data.ShadowLab.ModelScore> { kindOrder[it.kind] ?: 3 }.thenByDescending { it.nonFlatAcc ?: 0.0 }).forEach { m ->
            val c = when (m.kind) { "CHALLENGER" -> GoldLight; "CONTROL" -> TextMuted; else -> if (m.name == r.bestBaseline) TextPrimary else TextSecondary }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(0.30f)) {
                    Text(m.name + (if (m.name == r.bestBaseline) " ★" else ""), color = c, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text(m.kind.lowercase(Locale.US), color = TextMuted, fontSize = 7.sp)
                }
                Text(pctOrDash(m.nonFlatAcc), color = c, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.17f))
                Text(pctOrDash(m.callSuccess), color = TextSecondary, fontSize = 9.sp, modifier = Modifier.weight(0.15f))
                Text(pctOrDash(m.flatRate), color = TextSecondary, fontSize = 9.sp, modifier = Modifier.weight(0.12f))
                Text(String.format(Locale.US, "%.0f%%", m.coverage * 100), color = TextSecondary, fontSize = 9.sp, modifier = Modifier.weight(0.12f))
                Text(pctOrDash(m.bootLow), color = TextSecondary, fontSize = 9.sp, modifier = Modifier.weight(0.14f))
            }
            if (m.kind != "BASELINE") Text(
                "matched ${m.matchedBaseline.lowercase(Locale.US)} ${pctOrDash(m.matchedBaselineAcc)} • paired ${L.f1s(m.pairedDiff)} (low ${L.f1s(m.pairedDiffLow)}) pts • proof N ${m.proofN} • net@ref ${m.netRef?.let { String.format(Locale.US, "%+.2f", it) } ?: "--"} vs baseline ${m.matchedBaselineNet?.let { String.format(Locale.US, "%+.2f", it) } ?: "--"} • UP calls ${m.upCallShare?.let { String.format(Locale.US, "%.0f%%", it) } ?: "--"}",
                color = TextMuted, fontSize = 8.sp)
        }
        Note(t(lang,
            "NON-FLAT = right ÷ (right + wrong): only when the market really moved 0.25×ATR. CALL OK = right ÷ all calls (flat counts against). FLAT = calls followed by no real move. COVER = share of candles with a call. BOOT LOW = block-bootstrap lower bound, corrected for ${r.familySize} challengers. ★ = best global baseline. Matched = baselines on exactly the same candles.",
            "NON-FLAT = सही ÷ (सही + गलत): सिर्फ जब भाव सच में 0.25×ATR चला। CALL OK = सही ÷ सारी कॉल (सपाट भी गिना)। FLAT = जिन कॉल के बाद असली चाल नहीं। COVER = कितनी कैंडल पर कॉल। BOOT LOW = block-bootstrap निचली सीमा, ${r.familySize} मॉडल के लिए सुधारी। ★ = सबसे अच्छा baseline। Matched = ठीक उन्हीं कैंडल पर baseline।",
            "NON-FLAT = बरोबर ÷ (बरोबर + चूक): फक्त भाव खरंच 0.25×ATR हलला तेव्हा. CALL OK = बरोबर ÷ सर्व कॉल (सपाटही मोजले). FLAT = ज्या कॉलनंतर खरी चाल नाही. COVER = किती कँडलवर कॉल. BOOT LOW = block-bootstrap खालची मर्यादा, ${r.familySize} मॉडेलसाठी सुधारित. ★ = सर्वोत्तम baseline. Matched = नेमक्या त्याच कँडलवर baseline."))

        r.confusion?.let { cm ->
            Section("PREDICTED vs ACTUAL • FACTOR GATED")
            Cols(listOf("" to 0.28f, "ACT. UP" to 0.24f, "ACT. DOWN" to 0.24f, "ACT. FLAT" to 0.24f))
            listOf("PRED. UP", "PRED. DOWN", "PRED. WAIT").forEachIndexed { i, nm ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(nm, color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.28f))
                    cm.m[i].forEach { v -> Text("$v", color = TextPrimary, fontSize = 10.sp, modifier = Modifier.weight(0.24f)) }
                }
            }
            val w = cm.m[2]; val wt = w.sum()
            if (wt > 0) InfoRow("WAIT quality", String.format(Locale.US, "WAIT on %.0f%% of candles • %.0f%% then flat • %.0f%% missed a real move",
                100.0 * wt / cm.total.coerceAtLeast(1), 100.0 * w[2] / wt, 100.0 * (w[0] + w[1]) / wt))
            r.regimes.forEach { g -> InfoRow("  " + g.regime + " (${g.samples})", String.format(Locale.US, "WAIT %.0f%%", 100.0 * (g.samples - g.calls) / g.samples.coerceAtLeast(1)) +
                (if (g.right + g.wrong > 0) String.format(Locale.US, " • non-flat %.0f%% • flat calls %d", 100.0 * g.right / (g.right + g.wrong), g.flat) else "")) }
        }

        if (r.coverageCurve.isNotEmpty()) {
            Section(t(lang, "COVERAGE vs ACCURACY (selective risk)", "कवरेज बनाम सटीकता", "कव्हरेज विरुद्ध अचूकता"))
            Cols(listOf("TARGET" to 0.18f, "COVER" to 0.18f, "NON-FLAT" to 0.22f, "CALL OK" to 0.21f, "ERROR" to 0.21f))
            r.coverageCurve.forEach { p ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(String.format(Locale.US, "%.0f%%", p.target * 100), color = TextSecondary, fontSize = 9.sp, modifier = Modifier.weight(0.18f))
                    Text(String.format(Locale.US, "%.0f%%", p.coverage * 100), color = TextSecondary, fontSize = 9.sp, modifier = Modifier.weight(0.18f))
                    Text(pctOrDash(p.nonFlatAcc), color = GoldLight, fontSize = 9.sp, modifier = Modifier.weight(0.22f))
                    Text(pctOrDash(p.callSuccess), color = TextSecondary, fontSize = 9.sp, modifier = Modifier.weight(0.21f))
                    Text(pctOrDash(p.errorRate), color = TextSecondary, fontSize = 9.sp, modifier = Modifier.weight(0.21f))
                }
            }
            Note(t(lang, "Shown only to understand the trade-off. The 30% gate was fixed before the run, not picked from this table.",
                "सिर्फ समझने के लिए। 30% gate टेस्ट से पहले तय था, इस टेबल से नहीं चुना।",
                "फक्त समजण्यासाठी. 30% gate चाचणीपूर्वी ठरलेला होता, या टेबलवरून निवडला नाही."))
        }

        r.models.firstOrNull { it.name == r.topChallenger }?.let { m ->
            Section(t(lang, "COST SCENARIOS (assumptions) • ${m.name}", "खर्च स्थितियाँ (अनुमान) • ${m.name}", "खर्च परिस्थिती (अंदाज) • ${m.name}"))
            L.COSTS.forEachIndexed { i, (nm, c) -> InfoRow("$nm (${L.usd(c)})", m.netByCost.getOrNull(i)?.let { String.format(Locale.US, "%+.2f USD per call", it) } ?: "--") }
            Note(t(lang, "Prediction evidence and money are separate questions. These are assumed costs, not broker quotes, and never a profit promise.",
                "प्रेडिक्शन का सबूत और पैसा अलग सवाल हैं। ये अनुमानित खर्च हैं, ब्रोकर के भाव नहीं, और मुनाफे का वादा कभी नहीं।",
                "अंदाजाचा पुरावा आणि पैसा हे वेगळे प्रश्न. हे अंदाजित खर्च आहेत, ब्रोकरचे भाव नाहीत, आणि नफ्याचे वचन कधीच नाही."))
        }

        if (r.horizons.isNotEmpty()) {
            Section(t(lang, "HOW FAR AHEAD? (exploratory)", "कितना आगे? (जाँच)", "किती पुढे? (तपासणी)"))
            r.horizons.forEach { h ->
                InfoRow("${h.horizon} " + t(lang, "candles", "कैंडल", "कँडल"),
                    pctOrDash(h.score.nonFlatAcc) + " (boot low " + pctOrDash(h.score.bootLow) + ") vs ${h.bestBaseline} " + pctOrDash(h.bestBaselineAcc) +
                        " • cover " + String.format(Locale.US, "%.0f%%", h.score.coverage * 100))
            }
            Note(t(lang, "Own correction family of ${L.SWEEP.size}. Verdict and live shadow use 4 candles only.", "${L.SWEEP.size} की अलग correction family। नतीजा और लाइव सिर्फ 4 कैंडल पर।", "${L.SWEEP.size} ची वेगळी correction family. निकाल आणि लाइव्ह फक्त 4 कँडलवर."))
        }

        if (r.calibration.isNotEmpty()) {
            Section("LAB CALIBRATION (out-of-fold)")
            r.calibration.forEach { c -> InfoRow("score ${c.bin} (N=${c.n})", String.format(Locale.US, "avg score %.0f • really right %.0f%%", c.predicted, c.actual)) }
            Note(t(lang, "Model score is not a probability until live results confirm it. Live calibration is shown in the qualification report.",
                "जब तक लाइव नतीजे पुष्टि न करें, model score प्रॉबेबिलिटी नहीं है। लाइव calibration qualification रिपोर्ट में है।",
                "लाइव्ह निकाल पुष्टी करेपर्यंत model score प्रॉबॅबिलिटी नाही. लाइव्ह calibration qualification अहवालात आहे."))
        }

        r.depth?.let { d ->
            Section(t(lang, "HISTORY DEPTH AUDIT", "हिस्ट्री गहराई जाँच", "हिस्ट्री खोली तपासणी"))
            InfoRow(t(lang, "Candles / span", "कैंडल / अवधि", "कँडल / कालावधी"), String.format(Locale.US, "%d • %.0f days", d.bars, d.spanDays))
            InfoRow(t(lang, "Range", "दायरा", "कालखंड"), "${d.first} → ${d.last}")
            if (d.sessions.isNotEmpty()) InfoRow("Sessions", d.sessions.entries.joinToString(" • ") { "${it.key.lowercase(Locale.US)} ${it.value}" })
            InfoRow(t(lang, "Market types", "मार्केट प्रकार", "मार्केट प्रकार"), d.regimes.entries.joinToString(" • ") { "${it.key.lowercase(Locale.US)} ${it.value}" })
            InfoRow(t(lang, "High-vol share / gaps", "हाई-वोल / गैप", "हाय-व्होल / गॅप"), String.format(Locale.US, "%.0f%% • %d gaps beyond weekends", d.highVolShare * 100, d.gaps))
            InfoRow(t(lang, "Provider revisions", "प्रोवाइडर बदलाव", "प्रोव्हायडर बदल"), "${r.historyRevisions} candles changed after storing (logged)")
        }

        r.current?.let { c ->
            Section(t(lang, "SHADOW READ NOW (not a trade signal)", "अभी का शैडो अनुमान (ट्रेड सिग्नल नहीं)", "आत्ताचा शॅडो अंदाज (ट्रेड सिग्नल नाही)"))
            InfoRow(t(lang, "Last closed candle", "आखिरी बंद कैंडल", "शेवटची बंद कँडल"), c.barTime + " UTC • " + c.regime)
            InfoRow(t(lang, "Shadow decision", "शैडो फैसला", "शॅडो निर्णय"), c.decision + (if (c.waitReason.isNotBlank()) " (${c.waitReason})" else "") +
                (c.score?.let { String.format(Locale.US, " • model score %.2f", it) } ?: ""))
            Note(c.reason)
            Note(c.factorsText)
            if (r.verdict != "EDGE CANDIDATE") Note(t(lang, "No lab edge, so this read means nothing for trading.", "लैब में edge नहीं, इसलिए ट्रेडिंग के लिए इसका कोई मतलब नहीं।", "लॅबमध्ये edge नाही, म्हणून ट्रेडिंगसाठी याला अर्थ नाही."))
        }

        Section(t(lang, "EXPERIMENT (registered before the run)", "प्रयोग (टेस्ट से पहले दर्ज)", "प्रयोग (चाचणीपूर्वी नोंदवलेला)"))
        InfoRow("ID", com.example.livegoldai.data.ShadowLab.Experiment.ID + " • " + com.example.livegoldai.data.ShadowLab.Experiment.REGISTERED)
        Note("Hypothesis: " + com.example.livegoldai.data.ShadowLab.Experiment.HYPOTHESIS)
        Note("Primary: " + com.example.livegoldai.data.ShadowLab.Experiment.PRIMARY)
        Note("Secondary: " + com.example.livegoldai.data.ShadowLab.Experiment.SECONDARY)
        Note("Gate: " + com.example.livegoldai.data.ShadowLab.Experiment.GATE_RANGE)
        Note("Correction: " + com.example.livegoldai.data.ShadowLab.Experiment.CORRECTION + " This run: ${r.familySize} challengers.")
        Note("Attempts: " + com.example.livegoldai.data.ShadowLab.Experiment.ATTEMPTS)
        Note(com.example.livegoldai.data.ShadowLab.Experiment.ATTEMPT_1)

        Section(t(lang, "TEST DATA", "टेस्ट डेटा", "चाचणी डेटा"))
        InfoRow("Snapshot", r.shortId + " • " + r.snapshotId.take(24) + "…")
        InfoRow("Data hash", r.dataHash.take(24) + "…")
        InfoRow(t(lang, "Source", "स्रोत", "स्रोत"), "XAU/USD • Twelve Data • ${r.interval} • closed candles only")
        InfoRow("Outcomes in test", String.format(Locale.US, "UP %.0f%% • DOWN %.0f%% • FLAT %.0f%%", r.outcomeShares.first * 100, r.outcomeShares.second * 100, r.outcomeShares.third * 100))
        InfoRow("Versions", "label ${r.labelVersion} • features ${L.FEATURE_SCHEMA} • recipe ${L.RECIPE_VERSION} • lab ${r.labVersion}")
        InfoRow(t(lang, "Tested", "टेस्ट समय", "चाचणी वेळ"), java.text.SimpleDateFormat("dd MMM HH:mm", Locale.US).format(java.util.Date(r.createdAtMs)))
    }
    Spacer(modifier = Modifier.height(8.dp))
    DarkButton(t(lang, "RUN AGAIN ($interval) • 1 credit", "फिर से चलाएँ ($interval) • 1 क्रेडिट", "पुन्हा चालवा ($interval) • 1 क्रेडिट"), !busy) { onRun(true) }
    Spacer(modifier = Modifier.height(6.dp))
    DarkButton(t(lang, "DEEPEN HISTORY (+5000 older candles) • 1 credit", "हिस्ट्री गहरी करें (+5000 पुरानी कैंडल) • 1 क्रेडिट", "हिस्ट्री खोल करा (+5000 जुन्या कँडल) • 1 क्रेडिट"), !busy, onDeepen)
    Text(t(lang, "Downloaded candles are stored on the phone and never bought twice. Results are reused for 6 hours.",
        "डाउनलोड हुई कैंडल फोन में रहती हैं, दोबारा क्रेडिट नहीं लगता। नतीजे 6 घंटे तक दोबारा उपयोग होते हैं।",
        "डाउनलोड झालेल्या कँडल फोनमध्ये राहतात, पुन्हा क्रेडिट लागत नाही. निकाल 6 तास पुन्हा वापरले जातात."), color = TextMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))

    Section(t(lang, "BACKUP", "बैकअप", "बॅकअप"))
    DarkButton(t(lang, "EXPORT ALL RESULTS (CSV)", "सारे नतीजे निकालें (CSV)", "सर्व निकाल काढा (CSV)"), true) {
        exportLauncher.launch("kalankar_ledger_" + java.text.SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(java.util.Date()) + ".csv")
    }
    Text(
        text = exportNote ?: t(lang, "Every prediction, its result and every live shadow decision, in one file you choose (Drive, Downloads…).",
            "हर प्रेडिक्शन, उसका नतीजा और हर लाइव शैडो फैसला, एक फाइल में जहाँ आप चुनें (Drive, Downloads…)।",
            "प्रत्येक अंदाज, त्याचा निकाल आणि प्रत्येक लाइव्ह शॅडो निर्णय, तुम्ही निवडलेल्या एका फाइलमध्ये (Drive, Downloads…)."),
        color = if (exportNote?.startsWith("Export failed") == true) SignalSell else TextMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun Cols(cols: List<Pair<String, Float>>) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        cols.forEach { (label, w) -> Text(label, color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(w)) }
    }
}

@Composable
private fun DarkButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(40.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceElevated, contentColor = GoldLight),
        border = BorderStroke(0.75.dp, GoldPrimary.copy(alpha = 0.6f))
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
    }
}

private fun pctOrDash(v: Double?) = if (v == null) "--" else String.format(Locale.US, "%.1f%%", v)

private fun verdictColor(v: String) = when (v) { "EDGE CANDIDATE" -> SignalBuy; "NO EDGE", "RUN FAILED" -> SignalSell; else -> TextSecondary }

private fun statusColorOf(s: String) = when (s) {
    "LIVE QUALIFIED" -> SignalBuy
    "DEMOTED", "UNDER REVIEW" -> SignalSell
    "LIVE VALIDATION" -> GoldLight
    else -> TextSecondary
}

@Composable
private fun OverviewTab(s: LearningSnapshot, lang: AppLanguage, pulse: com.example.livegoldai.model.LedgerPulse? = null) {
    val d30 = s.windows.firstOrNull { it.label == "30 Days All" } ?: s.windows.firstOrNull { it.label == "30 Days" }
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
        if (p.modelHealthLine.isNotEmpty()) InfoRow(t(lang, "Engine audit", "इंजन की जाँच", "इंजिन तपासणी"), p.modelHealthLine)
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
        "Each new candle, the main BUY/SELL/WAIT is saved once with its stop and first target. It is then checked on real minute prices of the same instrument it was made on. Like a real trade, the FIRST touch decides: target reached first = CORRECT; stop reached first = WRONG. If neither is touched before the time ends: CORRECT if price moved at least 0.25×ATR the predicted way, WRONG if that much the other way, smaller = NO EDGE (not counted). WAIT calls are tracked separately. Weekend gaps are excluded.",
        "हर नई कैंडल पर मुख्य BUY/SELL/WAIT अपने स्टॉप और पहले टारगेट के साथ एक बार सेव होता है। फिर उसी इंस्ट्रूमेंट के असली मिनट-भाव पर जाँच होती है जिस पर वह बना था। असली ट्रेड की तरह जो पहले छुआ वही फैसला: पहले टारगेट = सही; पहले स्टॉप-लॉस = गलत। समय खत्म होने तक दोनों न छुएँ तो: भाव अनुमानित दिशा में कम से कम 0.25×ATR चला = सही, उतना उल्टा = गलत, इससे छोटा = कोई नतीजा नहीं (गिना नहीं)। WAIT अलग गिने जाते हैं। वीकेंड हटाया जाता है।",
        "प्रत्येक नवीन कँडलवर मुख्य BUY/SELL/WAIT त्याच्या स्टॉप आणि पहिल्या टार्गेटसह एकदा जतन होतो. मग ज्या इन्स्ट्रुमेंटवर तो बनला त्याच्याच खऱ्या मिनिट-भावांवर तपासणी होते. खऱ्या ट्रेडप्रमाणे जे आधी लागले तेच ठरवते: आधी टार्गेट = बरोबर; आधी स्टॉप-लॉस = चूक. वेळ संपेपर्यंत दोन्ही न लागल्यास: भाव अंदाजित दिशेने किमान 0.25×ATR गेला = बरोबर, तितकाच उलट = चूक, त्याहून लहान = निकाल नाही (मोजले नाही). WAIT वेगळे मोजले जातात. वीकेंड वगळला जातो."))
}

@Composable
private fun AccuracyTab(s: LearningSnapshot, lang: AppLanguage) {
    Section(t(lang, "THIS TIMEFRAME", "यह टाइमफ्रेम", "हा टाइमफ्रेम"))
    s.windows.filter { it.label != "30 Days All" }.forEach { WindowRow(it, lang) }

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
