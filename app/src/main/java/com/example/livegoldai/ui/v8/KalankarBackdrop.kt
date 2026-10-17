package com.example.livegoldai.ui.v8

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.theme.ActivePalette

/**
 * SIGNATURE BACKGROUND (V25)
 *
 * Four still layers drawn once, no animation:
 *  1. the theme's base colour
 *  2. a nearly invisible tonal gradient (slightly lighter graphite at the top)
 *  3. a faint directional light from the top left
 *  4. a very low-contrast ring fragment and ascending line taken from the emblem, in the empty top-right area
 * Context: RISK adds a faint warm amber haze in one corner, REVIEW a muted research tint. Never the whole screen.
 */
@Composable
fun KalankarBackdrop(state: String = "NORMAL", modifier: Modifier = Modifier) {
    val p = ActivePalette.current
    val dark = !p.isLight
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(p.background)
        // 2. tonal plane
        drawRect(Brush.verticalGradient(listOf(lerp(p.background, p.surface, if (dark) 0.55f else 0.35f), p.background), endY = h * 0.6f))
        // 3. directional light
        drawRect(
            Brush.radialGradient(
                listOf((if (dark) Color.White else p.darkGold).copy(alpha = if (dark) 0.035f else 0.03f), Color.Transparent),
                center = Offset(w * 0.05f, -h * 0.05f), radius = w * 1.1f
            )
        )
        // 4. emblem geometry: ring fragment + ascending line
        val ringColor = p.primaryGold.copy(alpha = if (dark) 0.045f else 0.06f)
        drawArc(
            color = ringColor, startAngle = 110f, sweepAngle = 150f, useCenter = false,
            topLeft = Offset(w * 0.52f, -w * 0.18f), size = Size(w * 0.78f, w * 0.78f),
            style = Stroke(width = 1.2f)
        )
        drawLine(ringColor, Offset(w * 0.58f, w * 0.52f), Offset(w * 1.02f, w * 0.08f), strokeWidth = 1.2f)
        // context accent
        val accent = when (state) {
            "RISK" -> p.signalWait.copy(alpha = if (dark) 0.07f else 0.06f)
            "REVIEW" -> p.learn.copy(alpha = 0.05f)
            else -> null
        }
        if (accent != null) drawRect(Brush.radialGradient(listOf(accent, Color.Transparent), center = Offset(w, 0f), radius = w * 0.9f))
    }
}

/** Layout-aware loading: the shape of the price and forecast blocks, no fake numbers, no spinner. */
@Composable
fun V8LoadingSkeleton(lang: AppLanguage) {
    val block = V8.Card2
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(V8.Card).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.fillMaxWidth(0.25f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(block))
            Box(modifier = Modifier.fillMaxWidth(0.6f).height(36.dp).clip(RoundedCornerShape(6.dp)).background(block))
            Box(modifier = Modifier.fillMaxWidth(0.35f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(block))
        }
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(V8.Card).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(modifier = Modifier.fillMaxWidth(0.3f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(block))
            Box(modifier = Modifier.fillMaxWidth(0.45f).height(24.dp).clip(RoundedCornerShape(6.dp)).background(block))
            Box(modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(6.dp)).background(block))
        }
        Text(
            text = tr(lang, "Connecting to the market feed…", "मार्केट फ़ीड से जुड़ रहे हैं…", "मार्केट फीडशी जोडत आहे…"),
            color = V8.Text3, style = KType.Caption, modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/** Designed error: what stopped, what still works, and one clear action. */
@Composable
fun V8ErrorState(lang: AppLanguage, detail: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
            V8Dot(V8.Amber, 8.dp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = tr(lang, "MARKET DATA TEMPORARILY UNAVAILABLE", "मार्केट डेटा अभी उपलब्ध नहीं", "मार्केट डेटा सध्या उपलब्ध नाही"), color = V8.Text1, style = KType.SectionLabel)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = tr(lang, "Live forecast is paused. Nothing is recorded until real prices return. The Learning Center and saved results stay available.",
                    "लाइव अनुमान रुका है। असली भाव लौटने तक कुछ रिकॉर्ड नहीं होगा। लर्निंग सेंटर और पुराने नतीजे उपलब्ध रहेंगे।",
                    "लाइव्ह अंदाज थांबला आहे. खरे भाव परत येईपर्यंत काहीही नोंदले जाणार नाही. लर्निंग सेंटर आणि जुने निकाल उपलब्ध राहतील."),
                color = V8.Text2, style = KType.BodyPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = detail, color = V8.Text3, style = KType.Caption)
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(V8.Card)
                    .v8Press(onClick = onRetry)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(text = tr(lang, "RETRY", "फिर कोशिश करें", "पुन्हा प्रयत्न"), color = V8.Gold, style = KType.SectionLabel)
            }
        }
    }
}
