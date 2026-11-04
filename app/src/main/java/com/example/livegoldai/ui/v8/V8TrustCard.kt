package com.example.livegoldai.ui.v8

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.example.livegoldai.data.TrustCard
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * V35.5-D/E Forecast Trust card. Display only: every value comes from TrustCard.build, re-run every second, so the NOW
 * status follows the clock while the PUBLISHED status stays what it was. Narrow-screen layout: no row holds more than
 * one badge next to text, long values wrap below their label, details only when expanded.
 */
@Composable
internal fun V8TrustCard(analysis: GoldAnalysisResult, lang: AppLanguage, now: Long) {
    val card = remember(analysis, lang, now / 1000) { TrustCard.build(analysis, now, lang) }
    var open by remember { mutableStateOf(false) }
    val decisionColor = signalColor(card.decision)
    val publishedColor = when (card.status) { "QUALIFIED" -> V8.Green; "RESEARCH" -> V8.Amber; "WAIT" -> V8.Text2; else -> V8.Red }
    val nowColor = when (card.nowStatus) { "ACTIONABLE" -> V8.Green; "RESEARCH ONLY" -> V8.Amber; "WAIT" -> V8.Text2; else -> V8.Red }

    V8Card(level = 2, accent = V8.Gold.copy(alpha = 0.45f), onClick = { open = !open }) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) { V8Label(tr(lang, "Forecast trust", "फ़ोरकास्ट भरोसा", "अंदाज विश्वास"), V8.Gold) }
            V8Badge(card.decision.name, decisionColor)
        }
        Spacer(modifier = Modifier.height(8.dp))
        // published (the record) and now (re-checked) on separate lines
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = tr(lang, "Published", "प्रकाशित", "प्रकाशित"), color = V8.Text3, fontSize = 10.sp, modifier = Modifier.width(72.dp))
            V8Badge(card.status, publishedColor)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = tr(lang, "Now", "अभी", "आता"), color = V8.Text3, fontSize = 10.sp, modifier = Modifier.width(72.dp))
            V8Badge(card.nowStatus, nowColor)
        }
        if (card.nowReasons.isNotEmpty()) Text(text = card.nowReasons.joinToString(" · "), color = nowColor, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp))
        Text(text = card.statusNote, color = V8.Text2, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(modifier = Modifier.height(6.dp))

        card.metrics.forEach { m -> MetricRow(m, lang, open) }

        Spacer(modifier = Modifier.height(4.dp))
        V8Hairline(V8.Line)
        Text(
            text = if (open) tr(lang, "HIDE DETAILS  ˄", "विवरण छिपाएँ  ˄", "तपशील लपवा  ˄") else tr(lang, "CHECKS • FACTORS • LEVELS  ˅", "जाँच • कारण • लेवल  ˅", "तपासणी • कारणे • लेव्हल  ˅"),
            color = V8.Gold, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp,
            modifier = Modifier.padding(vertical = 6.dp)
        )
        if (open) {
            V8Label(tr(lang, "Trust diagnostics · separate checks, no total score", "भरोसा जाँच · अलग-अलग, कोई कुल स्कोर नहीं", "विश्वास तपासणी · वेगवेगळ्या, एकूण स्कोअर नाही"))
            card.diagnostics.forEach { DiagRow(it) }
            Spacer(modifier = Modifier.height(10.dp))
            // stacked, not side by side: readable at 360dp and with large fonts
            FactorBlock(card.forTitle, card.forSide, if (card.decision == Signal.WAIT) V8.Green else decisionColor, lang)
            Spacer(modifier = Modifier.height(6.dp))
            FactorBlock(card.againstTitle, card.againstSide, if (card.decision == Signal.WAIT) V8.Red else V8.Amber, lang)
            Spacer(modifier = Modifier.height(8.dp))
            val lv = card.levels
            if (lv != null) {
                V8Label(tr(lang, "One frozen scenario", "एक फ़्रोज़न सिनेरियो", "एक गोठवलेला सिनेरियो"))
                V8KeyValue(tr(lang, "Entry", "एंट्री", "एंट्री"), px(lv.entry))
                V8KeyValue(tr(lang, "Invalidation (stop)", "इनवैलिडेशन (स्टॉप)", "इनव्हॅलिडेशन (स्टॉप)"), px(lv.stop), V8.Red)
                V8KeyValue("TP1 / TP2", "${px(lv.tp1)} / ${px(lv.tp2)}", V8.Green)
                V8KeyValue(tr(lang, "Levels fixed at", "लेवल तय समय", "लेव्हल निश्चित वेळ"), lv.fixedAt + if (lv.saved) "" else tr(lang, " (not saved yet)", " (अभी सेव नहीं)", " (अजून सेव्ह नाही)"))
            }
            if (card.expiresAtMs > 0L) V8KeyValue(tr(lang, "Forecast expiry", "फ़ोरकास्ट समाप्ति", "अंदाज मुदत"),
                localTime(card.expiresAtMs) + if (now < card.expiresAtMs) " (" + fmtAge(card.expiresAtMs - now) + ")"
                    else tr(lang, " (expired)", " (समाप्त)", " (मुदत संपली)"), if (now < card.expiresAtMs) V8.Text1 else V8.Red)
            V8KeyValue(tr(lang, "Market regime", "मार्केट रेजीम", "मार्केट रेजीम"), card.regime)
            V8KeyValue(tr(lang, "Timeframe alignment", "टाइमफ़्रेम तालमेल", "टाइमफ्रेम ताळमेळ"), card.alignment)
            if (card.evidence.isNotBlank()) V8KeyValue(tr(lang, "Historical group evidence", "पुराना समूह प्रमाण", "जुना गट पुरावा"), card.evidence)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = card.footer, color = V8.Text3, fontSize = 9.sp)
        }
    }
}

@Composable
private fun MetricRow(m: TrustCard.Metric, lang: AppLanguage, open: Boolean) {
    val kindColor = when (m.kind) {
        TrustCard.Kind.RULE_AGREEMENT -> V8.Gold
        TrustCard.Kind.HISTORICAL_HIT_RATE -> V8.Info
        TrustCard.Kind.CALIBRATED_PROBABILITY -> V8.Text3
        TrustCard.Kind.DATA_HEALTH -> if (m.good == true) V8.Green else V8.Amber
        TrustCard.Kind.EXPERIMENTAL -> Color(0xFFA78BFA)
    }
    // label above value: a long value or a large font wraps instead of squeezing the badge
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        V8Badge(tr(lang, m.kind.en, m.kind.hi, m.kind.mr), kindColor)
        Text(text = m.value, color = V8.Text1, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
        if (open) Text(text = m.note, color = V8.Text3, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun DiagRow(d: TrustCard.Diag) {
    val c = when (d.state) { "OK" -> V8.Green; "WARN" -> V8.Amber; "FAIL" -> V8.Red; else -> V8.Text3 }
    val mark = when (d.state) { "OK" -> "✓"; "WARN" -> "!"; "FAIL" -> "✕"; else -> "–" }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(text = mark, color = c, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = d.name, color = V8.Text1, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(text = d.detail, color = V8.Text3, fontSize = 10.sp)
        }
    }
}

@Composable
private fun FactorBlock(title: String, lines: List<String>, color: Color, lang: AppLanguage) {
    Column(modifier = Modifier.fillMaxWidth()) {
        V8Label(title, color)
        if (lines.isEmpty()) Text(text = tr(lang, "none", "कोई नहीं", "काही नाही"), color = V8.Text3, fontSize = 10.sp)
        lines.forEach { Text(text = "• $it", color = V8.Text2, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp)) }
    }
}

private fun px(v: Double) = String.format(Locale.US, "%.2f", v)
private fun localTime(ms: Long) = SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(ms))
