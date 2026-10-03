package com.example.livegoldai.ui.v8

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*

/** V8 design tokens: graphite surfaces, subtle borders, gold only for structure. */
/* V13: every token comes from the selected theme. */
internal object V8 {
    private val p get() = ActivePalette.current
    val Bg: Color get() = p.background
    val Card: Color get() = p.surfaceCard          // level 2 surface
    val CardTop: Color get() = p.surface           // top of the card gradient (soft inner highlight)
    val Card2: Color get() = p.surfaceElevated     // level 1 flat / inset surface
    val Hero: Color get() = p.surfaceElevated      // level 3 hero, top of gradient
    val Line: Color get() = p.border               // hairline border
    val Info: Color get() = p.info
    val Learn: Color get() = p.learn
    val Green: Color get() = p.signalBuy
    val Red: Color get() = p.signalSell
    val Amber: Color get() = p.signalWait
    val Gold: Color get() = p.primaryGold
    val GoldDeep: Color get() = p.darkGold
    val Text1: Color get() = p.textPrimary
    val Text2: Color get() = p.textSecondary
    val Text3: Color get() = p.textMuted
}

/**
 * Haptic language (V18). Android itself honours the phone's "touch feedback" setting.
 * Light = a selection changed. Medium = an important action. Nothing vibrates on scroll, price ticks or animation.
 */
internal object V8Haptics {
    val Light = HapticFeedbackType.TextHandleMove
    val Medium = HapticFeedbackType.LongPress
}

/** Motion tokens (milliseconds) for the V8 screens: short and calm, no bounce. */
internal object V8Motion {
    const val TabSwitch = 180
    const val Reveal = 260
    const val Emphasis = 420
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
    val pad = when (level) { 1 -> 20.dp; 2 -> 16.dp; else -> 12.dp }
    val shape = RoundedCornerShape(when (level) { 1 -> 24.dp; 2 -> 20.dp; else -> 16.dp })
    // machined-surface look: a very soft top-to-bottom gradient and a hairline border, never a thick outline
    val surface = when (level) {
        1 -> Brush.verticalGradient(listOf(V8.Hero, V8.Card))
        2 -> Brush.verticalGradient(listOf(V8.CardTop, V8.Card))
        else -> Brush.verticalGradient(listOf(V8.Card2, V8.Card2))
    }
    var m = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(surface, shape)
        .border(0.75.dp, accent, shape)
    if (onClick != null) m = m.clickable { onClick() }
    Column(modifier = m.padding(pad), content = content)
}

@Composable
internal fun V8Label(text: String, color: Color = V8.Text3) {
    Text(text = text.uppercase(), color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.4.sp)
}

@Composable
internal fun V8Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.10f))
            .border(0.75.dp, color.copy(alpha = 0.32f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, color = color, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
    }
}

@Composable
internal fun V8KeyValue(label: String, value: String, valueColor: Color = V8.Text1) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = V8.Text3, fontSize = 11.sp, modifier = Modifier.weight(0.45f))
        Text(text = value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.55f))
    }
}

@Composable
internal fun V8Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) V8.Gold.copy(alpha = 0.12f) else V8.Card2)
            .border(0.75.dp, if (selected) V8.Gold.copy(alpha = 0.7f) else V8.Line, RoundedCornerShape(12.dp))
            .clickable {
                if (!selected) haptic.performHapticFeedback(V8Haptics.Light)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = text, color = if (selected) V8.Gold else V8.Text3, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, letterSpacing = 0.8.sp)
    }
}

/** One horizontal bar split into BUY / SIDEWAYS / SELL shares. */
@Composable
internal fun V8ThreeWayBar(bull: Int, side: Int, bear: Int) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf(bull to V8.Green, side to V8.Line, bear to V8.Red).forEach { (v, c) ->
            Box(
                modifier = Modifier
                    .weight(v.coerceAtLeast(1).toFloat())
                    .clip(RoundedCornerShape(3.dp))
                    .background(c)
                    .padding(vertical = 2.dp)
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

/** Tiny steady status light. Never blinks. */
@Composable
internal fun V8Dot(color: Color, size: Dp = 6.dp) {
    Box(modifier = Modifier.size(size).clip(CircleShape).background(color)) {}
}

/** Thin gold-to-nothing separator used under hero headings. */
@Composable
internal fun V8Hairline(color: Color = V8.Gold) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color.copy(alpha = 0.0f))))
            .padding(vertical = 0.4.dp)
    ) {}
}

/**
 * Instrument ring: one thin arc for one real number (0..100). The centre shows that same number.
 * percent < 0 = no value yet: only the empty track is drawn.
 */
@Composable
internal fun V8Ring(percent: Int, color: Color, label: String, diameter: Dp = 104.dp) {
    Box(modifier = Modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = 5.dp.toPx()
            val d = size.minDimension - stroke
            val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            drawArc(color = V8.Line, startAngle = 135f, sweepAngle = 270f, useCenter = false, topLeft = tl, size = Size(d, d), style = Stroke(width = stroke, cap = StrokeCap.Round))
            if (percent > 0) {
                drawArc(color = color, startAngle = 135f, sweepAngle = 270f * percent.coerceIn(0, 100) / 100f, useCenter = false, topLeft = tl, size = Size(d, d), style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = if (percent < 0) "--" else "$percent%", color = V8.Text1, fontSize = 24.sp, fontWeight = FontWeight.Light)
            Text(text = label.uppercase(), color = V8.Text3, fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
        }
    }
}
