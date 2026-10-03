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
        "Starting Kalankar FX Gold Pro...",
        "Connecting to price feeds (Twelve Data, PAXG/USDT)...",
        "Harmonizing 7-Pillar Institutional Confluence...",
        "Loading live gold data • Opening Terminal"
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
        // no artificial wait: just long enough for the logo to be seen once
        delay(450)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // always obsidian black, whatever theme is selected: the gold logo is made for it
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF050505),
                        Color(0xFF050505)
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
            // Official full logo: emblem + KALANKAR / FX GOLD PRO / PRIVATE MARKET INTELLIGENCE.
            // Fit keeps the aspect ratio: never stretched or cropped. widthIn keeps it sane on foldables.
            Image(
                painter = painterResource(id = R.drawable.kalankar_full_logo),
                contentDescription = "Kalankar FX Gold Pro",
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Sync Status Pill
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF101215),
                border = BorderStroke(0.75.dp, Color(0xFFD4B56A).copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFFD4B56A),
                        strokeWidth = 1.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Initializing Market Intelligence…",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = Color(0xFFF2F0EA),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

        }
    }
}
