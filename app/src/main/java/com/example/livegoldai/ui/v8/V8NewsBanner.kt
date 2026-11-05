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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.data.NewsModeBanner
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.GoldAnalysisResult

/** V35.10 News Mode banner: shown only around a high-impact USD event; display only (see NewsModeBanner). */
@Composable
internal fun V8NewsBanner(analysis: GoldAnalysisResult, lang: AppLanguage, now: Long) {
    val b = remember(analysis, lang, now / 1000) { NewsModeBanner.build(analysis, now, lang) } ?: return
    val accent = when (b.stage) { NewsModeBanner.Stage.UPCOMING -> V8.Gold; NewsModeBanner.Stage.PRE -> V8.Amber; NewsModeBanner.Stage.POST -> V8.Red }
    val stage = when (b.stage) {
        NewsModeBanner.Stage.UPCOMING -> tr(lang, "NEWS AHEAD", "न्यूज़ आने वाली", "न्यूज येणार")
        NewsModeBanner.Stage.PRE -> tr(lang, "NEWS WINDOW · BEFORE RELEASE", "न्यूज़ विंडो · रिलीज़ से पहले", "न्यूज विंडो · रिलीजपूर्वी")
        NewsModeBanner.Stage.POST -> tr(lang, "NEWS WINDOW · AFTER RELEASE", "न्यूज़ विंडो · रिलीज़ के बाद", "न्यूज विंडो · रिलीजनंतर")
    }
    V8Card(level = 2, accent = accent.copy(alpha = 0.6f)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            V8Dot(accent, 8.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) { V8Label(stage, accent) }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = b.event, color = V8.Text1, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
        Text(text = b.releaseAt, color = V8.Text2, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Text(text = b.countdown, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
        if (b.stage != NewsModeBanner.Stage.UPCOMING) {
            Spacer(modifier = Modifier.height(6.dp))
            V8KeyValue(tr(lang, "Dollar data", "डॉलर डेटा", "डॉलर डेटा"), b.dollar, if (b.dollar.startsWith("OBSERVED")) V8.Green else V8.Amber)
            V8KeyValue(tr(lang, "10Y data", "10Y डेटा", "10Y डेटा"), b.tenY, if (b.tenY.startsWith("OBSERVED")) V8.Green else V8.Amber)
        }
        Text(text = b.macro, color = if (b.refresh) V8.Amber else V8.Text1, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        // V35.10.1: what news safety did to THIS analysis's pillar decision (never a BUY / SELL of its own)
        if (b.safety.isNotEmpty()) Text(text = b.safety, color = V8.Text1, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Text(text = b.note, color = V8.Text3, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
