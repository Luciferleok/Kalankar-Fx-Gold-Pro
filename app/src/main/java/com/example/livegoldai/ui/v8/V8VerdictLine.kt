package com.example.livegoldai.ui.v8

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.data.OneLineVerdict
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.GoldAnalysisResult

/** V35.11: the decision in one plain line, first on HOME (display only, see OneLineVerdict). */
@Composable
internal fun V8VerdictLine(analysis: GoldAnalysisResult, lang: AppLanguage, now: Long) {
    val v = remember(analysis, lang, now / 1000) { OneLineVerdict.build(analysis, now, lang) }
    val c = signalColor(v.signal)
    V8Card(level = 2, accent = c.copy(alpha = 0.5f)) {
        Column {
            Text(text = v.headline, color = c, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, modifier = Modifier.semantics { heading() })
            Text(text = v.detail, color = if (v.warn) V8.Amber else V8.Text2, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
