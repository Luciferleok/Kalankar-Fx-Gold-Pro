package com.example.livegoldai.ui.components

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

@Composable
fun LuxurySplashScreen(
    ready: Boolean = false,
    offline: Boolean = false,
    onSplashFinished: () -> Unit
) {
    // 0 = nothing yet, 1 = emblem, 2 = KALANKAR, 3 = FX GOLD PRO, 4 = tagline + status
    var step by remember { mutableIntStateOf(0) }
    var revealDone by remember { mutableStateOf(false) }
    var timedOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        step = 1
        delay(280)
        step = 2
        delay(110)
        step = 3
        delay(110)
        step = 4
        delay(400)
        revealDone = true
        delay(1100)
        timedOut = true
    }
    LaunchedEffect(revealDone, ready, offline, timedOut) {
        if (revealDone && (ready || offline || timedOut)) {
            if (ready) delay(220)      // just long enough to read SYSTEM READY
            onSplashFinished()
        }
    }

    val logoAlpha by animateFloatAsState(targetValue = if (step >= 1) 1f else 0f, animationSpec = tween(250), label = "logo_alpha")
    val logoScale by animateFloatAsState(targetValue = if (step >= 1) 1f else 0.96f, animationSpec = tween(450), label = "logo_scale")
    val lineFraction by animateFloatAsState(
        targetValue = if (ready) 1f else if (step >= 4) 0.38f else 0f,
        animationSpec = tween(500), label = "status_line"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Obsidian)
            .testTag("luxury_splash_screen")
    ) {
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
                        .background(Brush.radialGradient(listOf(Champagne.copy(alpha = 0.10f), Color.Transparent)))
                )
                Image(
                    painter = painterResource(id = R.drawable.kalankar_logo_mark),
                    contentDescription = "Kalankar FX Gold Pro",
                    modifier = Modifier
                        .size(168.dp)
                        .alpha(logoAlpha)
                        .scale(logoScale),
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.width(132.dp).height(1.dp).background(Hairline)) {
                        Box(modifier = Modifier.fillMaxWidth(lineFraction).height(1.dp).background(if (offline) Platinum else Champagne))
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = when {
                            ready -> "SYSTEM READY"
                            offline -> "MARKET DATA OFFLINE"
                            else -> "CONNECTING MARKET DATA"
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
