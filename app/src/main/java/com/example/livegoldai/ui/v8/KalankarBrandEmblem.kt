package com.example.livegoldai.ui.v8

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.livegoldai.R

/** Where the official emblem is shown. One component, so the logo looks the same on every screen. */
enum class EmblemMode(val size: Dp, val corner: Dp) {
    COMPACT(32.dp, 9.dp),
    HEADER(46.dp, 13.dp),
    ABOUT(72.dp, 20.dp),
    HERO(96.dp, 26.dp),
    SPLASH(168.dp, 0.dp),
    WATERMARK(112.dp, 0.dp)
}

/**
 * KALANKAR BRAND EMBLEM (V23)
 *
 * The official logo, never redrawn or recoloured, mounted on a dark plate:
 *  - obsidian plate with a faint graphite light behind the mark
 *  - hairline machined-metal edge (bronze -> champagne -> pale highlight -> bronze), never a thick gold outline
 *  - [highlightOnce]: one light pass across the plate when it first appears, then it stays still for good
 *  - WATERMARK: only the mark, at very low opacity, no plate and no edge
 */
@Composable
fun KalankarBrandEmblem(
    mode: EmblemMode = EmblemMode.HEADER,
    modifier: Modifier = Modifier,
    highlightOnce: Boolean = false,
    watermarkAlpha: Float = 0.05f,
    splashSweep: Float = 0f
) {
    if (mode == EmblemMode.SPLASH) {
        // launch: the mark alone, no plate; the light passes over the emblem's own pixels only (offscreen + SrcAtop)
        Image(
            painter = painterResource(id = R.drawable.kalankar_logo_mark),
            contentDescription = "Kalankar FX Gold Pro",
            modifier = modifier
                .size(mode.size)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (splashSweep > 0f && splashSweep < 1f) {
                        val band = size.width * 0.22f
                        val cx = size.width * (-0.2f + 1.4f * splashSweep)
                        val cy = size.height * (1.2f - 1.4f * splashSweep)
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.Transparent, Color(0xFFFFF4D6).copy(alpha = 0.42f), Color.Transparent),
                                start = Offset(cx - band, cy + band),
                                end = Offset(cx + band, cy - band)
                            ),
                            blendMode = BlendMode.SrcAtop
                        )
                    }
                },
            contentScale = ContentScale.Fit
        )
        return
    }
    if (mode == EmblemMode.WATERMARK) {
        Image(
            painter = painterResource(id = R.drawable.kalankar_logo_mark),
            contentDescription = null,
            modifier = modifier.size(mode.size).alpha(watermarkAlpha),
            contentScale = ContentScale.Fit
        )
        return
    }
    val shape = RoundedCornerShape(mode.corner)
    val edge = Brush.linearGradient(
        listOf(Color(0xFF4A3A1E), Color(0xFFC9A961), Color(0xFFF1E3BC), Color(0xFF8A6E36), Color(0xFF3A2D17))
    )
    val sweep = remember { Animatable(if (highlightOnce) -0.6f else 2f) }
    if (highlightOnce) {
        LaunchedEffect(Unit) { sweep.animateTo(2f, tween(durationMillis = 900, delayMillis = 250)) }
    }
    Box(
        modifier = modifier
            .size(mode.size)
            .clip(shape)
            .background(Brush.radialGradient(listOf(Color(0xFF1B1D23), Color(0xFF0B0C10), Color(0xFF060709))))
            .border(0.6.dp, edge, shape),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.kalankar_logo_mark),
            contentDescription = "Kalankar FX Gold Pro",
            modifier = Modifier.fillMaxSize().padding(mode.size * 0.06f).clip(RoundedCornerShape(mode.corner * 0.8f)),
            contentScale = ContentScale.Fit
        )
        // inner top light + one-time highlight, both drawn above the mark and very faint
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent), endY = size.height * 0.45f))
            val p = sweep.value
            if (p < 1.6f) {
                val x = size.width * p
                drawRect(
                    Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.20f), Color.Transparent),
                        start = Offset(x - size.width * 0.35f, 0f),
                        end = Offset(x + size.width * 0.05f, size.height)
                    )
                )
            }
        }
    }
}

/** Signature strip under the header: graphite -> champagne highlight -> graphite, one hairline. */
@Composable
fun KalankarSignatureStrip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(0.6.dp)
            .background(Brush.horizontalGradient(listOf(Color.Transparent, V8.Line, V8.Gold.copy(alpha = 0.55f), V8.Line, Color.Transparent)))
    )
}
