package com.example.livegoldai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.livegoldai.theme.*
import kotlinx.coroutines.delay

@Composable
fun LuxurySplashScreen(
    onSplashFinished: () -> Unit
) {
    var progressStep by remember { mutableIntStateOf(0) }
    val progressTexts = listOf(
        "Initializing 24K Sovereign Bullion Engine...",
        "Connecting Real-Time LBMA Spot & ECN Spreads...",
        "Harmonizing 7-Pillar Institutional Confluence...",
        "Multi-AI Committee Calibrated & Armed • Opening Terminal"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "luxury_splash")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val borderRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "border_rotation"
    )

    LaunchedEffect(Unit) {
        delay(400)
        progressStep = 1
        delay(500)
        progressStep = 2
        delay(500)
        progressStep = 3
        delay(400)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF04060A),
                        Color(0xFF090D15),
                        Color(0xFF06080D)
                    )
                )
            )
            .testTag("luxury_splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            // Pure Vector Royal Medallion Emblem with Glow & Shimmer
            Box(
                modifier = Modifier
                    .size(118.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(Color(0xFF0D111A))
                    .border(
                        2.5.dp,
                        Brush.sweepGradient(
                            listOf(GoldLight, GoldPrimary, Color(0xFFD4AF37), GoldLight)
                        ),
                        CircleShape
                    )
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_kalankar_royal_emblem),
                    contentDescription = "Kalankar Sovereign Bullion Emblem",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Brand Title
            Text(
                text = "KALANKAR FX",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 24.sp,
                    letterSpacing = 2.sp
                ),
                fontWeight = FontWeight.Black,
                color = GoldLight
            )

            Text(
                text = "XAU/USD PRO • INSTITUTIONAL TERMINAL",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp
                ),
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Sync Status Pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurfaceElevated,
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = GoldPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = progressTexts.getOrElse(progressStep) { progressTexts.last() },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "24K Sovereign Bullion • Pure Vector Integrated Core",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = TextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}
