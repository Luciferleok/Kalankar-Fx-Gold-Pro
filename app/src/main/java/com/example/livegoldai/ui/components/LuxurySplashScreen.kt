package com.example.livegoldai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.R
import kotlinx.coroutines.delay

/*
 * LAUNCH EXPERIENCE (V16)
 *
 * One calm brand moment on obsidian black: emblem, then KALANKAR / FX GOLD PRO / tagline, one status line.
 * - The colours are fixed (not themed): the gold logo is made for a black background, and the Android
 *   system splash before this screen uses the same black, so there is no flash and no colour change.
 * - The status line is real: it follows the first market-data load of the app (ready / offline).
 * - No artificial wait: the reveal takes about 0.9 s; the screen leaves as soon as data is ready,
 *   and never stays longer than about 2 s even if the network is slow (the home screen then shows loading).
 */
private val Obsidian = Color(0xFF050505)
private val Champagne = Color(0xFFD4B56A)
private val Platinum = Color(0xFFB9B4A8)
private val Ivory = Color(0xFFF2F0EA)
private val Hairline = Color(0xFF22262C)
private val TraceLight = Color(0xFFFFF4D6)

/** All launch timings in one place (milliseconds). No bounce, no overshoot: fast-out-slow-in only. */
private object LaunchMotion {
    const val Silence = 110L          // phase 01: black only
    const val AfterEmblem = 280L
    const val WordGap = 110L
    const val AfterTagline = 400L
    const val MaxExtraWait = 1100L    // slow network: leave anyway, the home screen shows loading
    const val ReadyHold = 240L        // long enough to read the ready line
    const val BrandFade = 260
    const val BrandScale = 520
    const val BrandSweep = 650
    const val SweepDelay = 180
    const val ContentReveal = 420
}

@Composable
fun LuxurySplashScreen(
    ready: Boolean = false,
    offline: Boolean = false,
    live: Boolean = false,           // a real, live market quote has arrived (not demo data)
    onSplashFinished: () -> Unit
) {
    // 0 = nothing yet, 1 = emblem, 2 = KALANKAR, 3 = FX GOLD PRO, 4 = tagline + status
    var step by remember { mutableIntStateOf(0) }
    var revealDone by remember { mutableStateOf(false) }
    var timedOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(LaunchMotion.Silence)
        step = 1
        delay(LaunchMotion.AfterEmblem)
        step = 2
        delay(LaunchMotion.WordGap)
        step = 3
        delay(LaunchMotion.WordGap)
        step = 4
        delay(LaunchMotion.AfterTagline)
        revealDone = true
        delay(LaunchMotion.MaxExtraWait)
        timedOut = true
    }
    LaunchedEffect(revealDone, ready, offline, timedOut) {
        if (revealDone && (ready || offline || timedOut)) {
            if (ready) delay(LaunchMotion.ReadyHold)
            onSplashFinished()
        }
    }

    val logoAlpha by animateFloatAsState(targetValue = if (step >= 1) 1f else 0f, animationSpec = tween(LaunchMotion.BrandFade), label = "logo_alpha")
    val logoScale by animateFloatAsState(
        targetValue = if (step >= 1) 1f else 0.975f,
        animationSpec = tween(LaunchMotion.BrandScale, easing = FastOutSlowInEasing), label = "logo_scale"
    )
    // one light trace across the emblem, lower-left (K) to upper-right (arrow). Runs once, never loops.
    val sweep by animateFloatAsState(
        targetValue = if (step >= 1) 1f else 0f,
        animationSpec = tween(LaunchMotion.BrandSweep, delayMillis = LaunchMotion.SweepDelay, easing = FastOutSlowInEasing), label = "logo_sweep"
    )
    // thin line under the tagline: grows from the centre, completes when the system is ready
    val lineFraction by animateFloatAsState(
        targetValue = if (ready) 1f else if (step >= 4) 0.45f else 0f,
        animationSpec = tween(LaunchMotion.ContentReveal, easing = FastOutSlowInEasing), label = "signature_line"
    )
    val gridAlpha by animateFloatAsState(targetValue = if (step >= 4) 1f else 0f, animationSpec = tween(LaunchMotion.ContentReveal), label = "grid_alpha")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Obsidian)
            .testTag("luxury_splash_screen")
    ) {
        // barely visible analytical grid: the first hint of the terminal, appears with the tagline
        Canvas(modifier = Modifier.fillMaxSize().alpha(gridAlpha)) {
            val gap = 56.dp.toPx()
            val c = Champagne.copy(alpha = 0.035f)
            var x = (size.width % gap) / 2f
            while (x < size.width) { drawLine(color = c, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1f); x += gap }
            var y = (size.height % gap) / 2f
            while (y < size.height) { drawLine(color = c, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f); y += gap }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
        ) {
            // emblem with a very soft warm light behind it (no box, no ring, no glow animation)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(232.dp)) {
                Box(
                    modifier = Modifier
                        .size(232.dp)
                        .clip(CircleShape)
                        .alpha(logoAlpha)
                        .background(Brush.radialGradient(listOf(Champagne.copy(alpha = 0.08f), Color.Transparent)))
                )
                Image(
                    painter = painterResource(id = R.drawable.kalankar_logo_mark),
                    contentDescription = "Kalankar FX Gold Pro",
                    modifier = Modifier
                        .size(168.dp)
                        .alpha(logoAlpha)
                        .scale(logoScale)
                        // offscreen layer + SrcAtop: the light only touches the emblem's own pixels, never a rectangle
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            if (sweep > 0f && sweep < 1f) {
                                val band = size.width * 0.22f
                                val cx = size.width * (-0.2f + 1.4f * sweep)
                                val cy = size.height * (1.2f - 1.4f * sweep)
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color.Transparent, TraceLight.copy(alpha = 0.42f), Color.Transparent),
                                        start = Offset(cx - band, cy + band),
                                        end = Offset(cx + band, cy - band)
                                    ),
                                    blendMode = BlendMode.SrcAtop
                                )
                            }
                        },
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Staged(visible = step >= 2) {
                Text(
                    text = "KALANKAR",
                    color = Ivory,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 8.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Staged(visible = step >= 3) {
                Text(
                    text = "FX GOLD PRO",
                    color = Champagne,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 6.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Staged(visible = step >= 4) {
                Text(
                    text = "PRIVATE MARKET INTELLIGENCE",
                    color = Platinum.copy(alpha = 0.75f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .width((148f * lineFraction).dp)
                    .height(1.dp)
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, (if (offline) Platinum else Champagne).copy(alpha = 0.55f), Color.Transparent)))
            )
        }

        // ---- one real status line near the bottom
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 56.dp)
        ) {
            Staged(visible = step >= 4) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // the dot appears only for a real live quote
                    if (ready && live) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Champagne))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = when {
                            ready && live -> "XAUUSD  •  LIVE MARKET"
                            ready -> "SYSTEM READY"
                            offline -> "MARKET CONNECTION UNAVAILABLE"
                            else -> "INITIALIZING MARKET INTELLIGENCE"
                        },
                        color = Platinum.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.5.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Soft fade + 6dp rise, once. */
@Composable
private fun Staged(visible: Boolean, content: @Composable () -> Unit) {
    val a by animateFloatAsState(targetValue = if (visible) 1f else 0f, animationSpec = tween(260), label = "staged_alpha")
    val dy by animateFloatAsState(targetValue = if (visible) 0f else 6f, animationSpec = tween(260), label = "staged_rise")
    Box(modifier = Modifier.alpha(a).offset(y = dy.dp)) { content() }
}
