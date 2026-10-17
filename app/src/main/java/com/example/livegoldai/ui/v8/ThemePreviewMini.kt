package com.example.livegoldai.ui.v8

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.livegoldai.theme.AppThemeColors

/**
 * Miniature terminal drawn with the given palette (V24): header with brand, forecast block, chart, navigation.
 * It is a picture of the layout only; the bars are not market data.
 */
@Composable
fun ThemePreviewMini(p: AppThemeColors, selected: Boolean, width: Dp = 76.dp, height: Dp = 104.dp) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .size(width, height)
            .clip(shape)
            .background(p.background)
            .border(if (selected) 1.dp else 0.5.dp, if (selected) p.primaryGold else p.border, shape)
            .padding(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // header: emblem plate + two brand lines
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF0B0C10)).border(0.5.dp, p.darkGold, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(3.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.5.dp)) {
                Box(modifier = Modifier.size(22.dp, 2.dp).background(p.textPrimary))
                Box(modifier = Modifier.size(15.dp, 1.5.dp).background(p.primaryGold))
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(p.border))
        // forecast block
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(p.surfaceCard).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.size(12.dp, 1.5.dp).background(p.textMuted))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(18.dp, 4.dp).background(p.signalSell))
                Spacer(modifier = Modifier.weight(1f))
                Box(modifier = Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).border(1.dp, p.signalSell, RoundedCornerShape(5.dp)))
            }
        }
        // chart
        Canvas(modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(4.dp)).background(p.surface)) {
            val n = 9
            val w = size.width / n
            val h = size.height
            val opens = listOf(0.62f, 0.55f, 0.50f, 0.58f, 0.46f, 0.40f, 0.48f, 0.36f, 0.30f)
            val closes = listOf(0.55f, 0.50f, 0.58f, 0.46f, 0.40f, 0.48f, 0.36f, 0.30f, 0.34f)
            for (i in 0 until n) {
                val o = opens[i] * h; val c = closes[i] * h
                val up = c < o
                val col = if (up) p.signalBuy else p.signalSell
                val x = i * w + w * 0.25f
                drawLine(col, Offset(x + w * 0.25f, minOf(o, c) - h * 0.06f), Offset(x + w * 0.25f, maxOf(o, c) + h * 0.06f), strokeWidth = 1f)
                drawRect(col, topLeft = Offset(x, minOf(o, c)), size = Size(w * 0.5f, maxOf(1f, kotlin.math.abs(c - o))))
            }
            drawLine(p.primaryGold.copy(alpha = 0.7f), Offset(0f, h * 0.34f), Offset(size.width, h * 0.34f), strokeWidth = 0.6f)
        }
        // navigation: five items, the first selected with a short gold line
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for (i in 0 until 5) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(5.dp, 1.dp).background(if (i == 0) p.primaryGold else Color.Transparent))
                    Spacer(modifier = Modifier.height(1.dp))
                    Box(modifier = Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i == 0) p.primaryGold else p.textMuted))
                }
            }
        }
    }
}
