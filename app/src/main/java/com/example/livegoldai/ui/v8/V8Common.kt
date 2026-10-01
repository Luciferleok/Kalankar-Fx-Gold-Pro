package com.example.livegoldai.ui.v8

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*

/** V8 design tokens: graphite surfaces, subtle borders, gold only for structure. */
internal object V8 {
    val Bg = Color(0xFF0B0D11)
    val Card = Color(0xFF14171D)
    val Card2 = Color(0xFF1B1F27)
    val Line = Color(0xFF262B35)
    val Info = Color(0xFF4FC3F7)
    val Learn = Color(0xFFB39DDB)
    val Green = SignalBuy
    val Red = SignalSell
    val Amber = Color(0xFFFFB300)
    val Gold = GoldPrimary
    val Text1 = TextPrimary
    val Text2 = TextSecondary
    val Text3 = TextMuted
}

internal fun tr(lang: AppLanguage, en: String, hi: String, mr: String): String = when (lang) {
    AppLanguage.ENGLISH -> en
    AppLanguage.HINDI -> hi
    AppLanguage.MARATHI -> mr
}

internal fun signalColor(s: Signal): Color = when (s) {
    Signal.BUY -> V8.Green
    Signal.SELL -> V8.Red
    Signal.WAIT -> V8.Amber
}

internal fun arrowOf(s: Signal): String = when (s) {
    Signal.BUY -> "↑"
    Signal.SELL -> "↓"
    Signal.WAIT -> "↔"
}

internal fun statusColor(status: String): Color = when (status) {
    "OK", "HEALTHY", "RESOLVED", "PROMOTED" -> V8.Green
    "WARN", "DEGRADED", "WARNING", "SHADOW" -> V8.Amber
    "FAIL", "FAILED", "ERROR", "REJECTED", "ROLLED_BACK" -> V8.Red
    "INFO" -> V8.Info
    else -> V8.Text3
}

internal fun statusMark(status: String): String = when (status) {
    "OK", "HEALTHY" -> "✓"
    "WARN", "DEGRADED" -> "⚠"
    "FAIL", "FAILED" -> "✕"
    else -> "–"
}

/** Level-1/2/3 card. Level 1 = mission critical, 3 = compact diagnostics. */
@Composable
internal fun V8Card(
    modifier: Modifier = Modifier,
    level: Int = 2,
    accent: Color = V8.Line,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val pad = when (level) { 1 -> 16.dp; 2 -> 14.dp; else -> 10.dp }
    var m = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(if (level == 1) 20.dp else 14.dp))
        .background(if (level == 3) V8.Card2 else V8.Card)
        .border(1.dp, accent, RoundedCornerShape(if (level == 1) 20.dp else 14.dp))
    if (onClick != null) m = m.clickable { onClick() }
    Column(modifier = m.padding(pad), content = content)
}

@Composable
internal fun V8Label(text: String, color: Color = V8.Text3) {
    Text(text = text.uppercase(), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}

@Composable
internal fun V8Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
internal fun V8KeyValue(label: String, value: String, valueColor: Color = V8.Text1) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = V8.Text3, fontSize = 11.sp, modifier = Modifier.weight(0.45f))
        Text(text = value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.55f))
    }
}

@Composable
internal fun V8Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) V8.Gold else V8.Card2)
            .border(1.dp, if (selected) V8.Gold else V8.Line, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = text, color = if (selected) Color.Black else V8.Text2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** One horizontal bar split into BUY / SIDEWAYS / SELL shares. */
@Composable
internal fun V8ThreeWayBar(bull: Int, side: Int, bear: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(bull to V8.Green, side to V8.Text3, bear to V8.Red).forEach { (v, c) ->
            Box(
                modifier = Modifier
                    .weight(v.coerceAtLeast(1).toFloat())
                    .clip(RoundedCornerShape(3.dp))
                    .background(c)
                    .padding(vertical = 3.dp)
            ) {}
        }
    }
}

internal fun fmtAge(ms: Long): String = when {
    ms < 0 -> "--"
    ms < 60_000 -> "${ms / 1000}s"
    ms < 3_600_000 -> "${ms / 60_000}m ${(ms % 60_000) / 1000}s"
    else -> "${ms / 3_600_000}h ${(ms % 3_600_000) / 60_000}m"
}
