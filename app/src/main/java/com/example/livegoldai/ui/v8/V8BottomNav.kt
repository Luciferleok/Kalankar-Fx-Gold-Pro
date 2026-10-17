package com.example.livegoldai.ui.v8

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage

/** HOME • BRAIN • INTEL • LEARN • SYSTEM • TOOLS (tab numbers stay the same, only names and order change) */
@Composable
fun V8BottomNav(selected: Int, lang: AppLanguage, healthStatus: String?, onSelect: (Int) -> Unit) {
    fun label(tab: Int) = when (tab) {
        0 -> tr(lang, "Home", "होम", "होम")
        1 -> tr(lang, "Brain", "ब्रेन", "ब्रेन")
        2 -> tr(lang, "Intel", "इंटेल", "इंटेल")
        3 -> tr(lang, "System", "सिस्टम", "सिस्टम")
        4 -> tr(lang, "Learn", "लर्निंग", "लर्निंग")
        else -> tr(lang, "Tools", "टूल्स", "टूल्स")
    }
    val order = listOf(0, 1, 2, 4, 3, 5)
    val haptic = LocalHapticFeedback.current
    Column {
    // docked surface: one faint highlight line instead of a border
    Box(modifier = Modifier.fillMaxWidth().height(0.6.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, V8.Line, V8.Gold.copy(alpha = 0.25f), V8.Line, Color.Transparent))))
    NavigationBar(containerColor = V8.Bg, contentColor = V8.Text2) {
        order.forEach { i ->
            val label = label(i)
            val iconColor = if (i == 3 && healthStatus != null && healthStatus != "HEALTHY") statusColor(healthStatus) else null
            NavigationBarItem(
                selected = selected == i,
                onClick = {
                    if (selected != i) haptic.performHapticFeedback(V8Haptics.Light)
                    onSelect(i)
                },
                icon = {
                    // selected tab: a short champagne line above the icon, no filled pill
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (selected == i) V8.Gold else Color.Transparent)
                                .padding(vertical = 1.dp)
                        ) {}
                        Spacer(modifier = Modifier.height(5.dp))
                        V8NavIcon(index = i, color = iconColor ?: if (selected == i) V8.Gold else V8.Text3)
                    }
                },
                label = { Text(text = label.uppercase(), fontSize = 8.sp, maxLines = 1, letterSpacing = 0.8.sp, fontWeight = if (selected == i) FontWeight.SemiBold else FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = V8.Gold,
                    unselectedTextColor = V8.Text3,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
    }
}

/**
 * One icon language for the navigation (V23): thin line icons drawn in code, same stroke weight and
 * same optical size, so nothing depends on the phone's emoji or symbol font.
 * 0 cockpit (dial) • 1 forecast (rising line) • 2 AI (four-point spark) • 3 health (pulse) • 4 learning (bars) • 5 more (lines)
 */
@Composable
internal fun V8NavIcon(index: Int, color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val sw = w * 0.075f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(w * x1, h * y1), Offset(w * x2, h * y2), strokeWidth = sw, cap = StrokeCap.Round)
        fun poly(vararg p: Float) {
            val path = Path()
            path.moveTo(w * p[0], h * p[1])
            var k = 2
            while (k < p.size) { path.lineTo(w * p[k], h * p[k + 1]); k += 2 }
            drawPath(path, color, style = stroke)
        }
        when (index) {
            0 -> {
                drawCircle(color, radius = w * 0.40f, style = stroke)
                drawCircle(color, radius = w * 0.07f)
                line(0.5f, 0.5f, 0.72f, 0.30f)
            }
            1 -> {
                poly(0.10f, 0.78f, 0.38f, 0.50f, 0.56f, 0.64f, 0.88f, 0.26f)
                poly(0.66f, 0.24f, 0.88f, 0.26f, 0.88f, 0.48f)
            }
            2 -> {
                val path = Path()
                path.moveTo(w * 0.5f, h * 0.08f)
                path.quadraticTo(w * 0.5f, h * 0.5f, w * 0.92f, h * 0.5f)
                path.quadraticTo(w * 0.5f, h * 0.5f, w * 0.5f, h * 0.92f)
                path.quadraticTo(w * 0.5f, h * 0.5f, w * 0.08f, h * 0.5f)
                path.quadraticTo(w * 0.5f, h * 0.5f, w * 0.5f, h * 0.08f)
                drawPath(path, color, style = stroke)
            }
            3 -> poly(0.06f, 0.55f, 0.28f, 0.55f, 0.40f, 0.22f, 0.56f, 0.82f, 0.68f, 0.55f, 0.94f, 0.55f)
            4 -> {
                line(0.20f, 0.84f, 0.20f, 0.58f)
                line(0.50f, 0.84f, 0.50f, 0.36f)
                line(0.80f, 0.84f, 0.80f, 0.16f)
            }
            else -> {
                line(0.16f, 0.28f, 0.84f, 0.28f)
                line(0.16f, 0.50f, 0.84f, 0.50f)
                line(0.16f, 0.72f, 0.60f, 0.72f)
            }
        }
    }
}
