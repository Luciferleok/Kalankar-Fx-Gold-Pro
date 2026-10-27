package com.example.livegoldai.ui.v8

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.data.DecisionTraceText
import com.example.livegoldai.data.PredictionPresentationState
import com.example.livegoldai.localization.AppLanguage

/**
 * V29B Decision Trace: answers, from the shared presentation state only, what was decided, why,
 * on which data, with what evidence and how much the record can be trusted. Nothing here is computed anew.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecisionTraceSheet(p: PredictionPresentationState, lang: AppLanguage, why: List<String> = emptyList(), onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = V8.Bg,
        contentColor = V8.Text1,
        scrimColor = Color(0xFF000000).copy(alpha = 0.72f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(text = DecisionTraceText.title(lang), color = V8.Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.6.sp)
            Spacer(modifier = Modifier.height(10.dp))
            // V29C.1: one gold accent (the decision); research and audit start collapsed
            var openTitles by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(setOf<String>()) }
            DecisionTraceText.sections(p, lang, why = why).forEachIndexed { i, section ->
                val expanded = !section.collapsed || section.title in openTitles
                V8Card(level = 3, accent = if (i == 0) V8.Gold.copy(alpha = 0.45f) else V8.Line,
                    onClick = if (section.collapsed) ({ openTitles = if (section.title in openTitles) openTitles - section.title else openTitles + section.title }) else null) {
                    V8Label(section.title + if (section.collapsed) (if (expanded) "  ▴" else "  ▾") else "", if (i == 0) V8.Gold else V8.Text2)
                    if (expanded) {
                        Spacer(modifier = Modifier.height(4.dp))
                        section.rows.forEach { (k, v) -> V8KeyValue(k, v, if (v.startsWith("⚠")) V8.Amber else V8.Text1) }
                        section.note?.let { Text(text = it, color = V8.Text3, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
