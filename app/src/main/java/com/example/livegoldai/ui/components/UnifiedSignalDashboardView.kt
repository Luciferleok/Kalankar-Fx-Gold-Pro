package com.example.livegoldai.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.GroupAnalysis
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * 🎛️ UNIFIED SIGNAL DASHBOARD VIEW
 * Displays real-time signal strength (Buy / Sell / Hold) based on the combined
 * AI indicator analysis:
 * 1. Live Central Signal Strength Gauge (Speedometer Arc from Strong Sell -> Hold -> Strong Buy)
 * 2. Combined AI Consensus Breakdown (Gemini + ChatGPT + Claude + DeepSeek + Perplexity)
 * 3. 7-Pillar Institutional Indicator Weight Matrix (Trend, SMC, Macro, S/R, Candle, Momentum, Volatility)
 * 4. Precision Pullback Trade Setup & 1-Tap Copy Command
 * 5. Autonomous Kalankar Quant Bot Live Protocol
 * 6. Multi-Timeframe Alignment Radar
 */
@Composable
fun UnifiedSignalDashboardView(
    analysis: GoldAnalysisResult,
    selectedInterval: String,
    onIntervalSelected: (String) -> Unit,
    onOpenCalculator: (Double) -> Unit,
    onOpenPredictionDialog: () -> Unit,
    onOpenPredictionErrorAnalyzer: () -> Unit = {},
    onPillarClick: (GroupAnalysis) -> Unit,
    onSwitchMode: (String) -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentLanguage = LocalAppLanguage.current
    val context = LocalContext.current

    // Combined Signal Score (0 to 100):
    // 0 = Max Bearish Sell, 50 = Neutral Hold, 100 = Max Bullish Buy
    val signalScore = when (analysis.overallSignal) {
        Signal.BUY -> (50.0 + (analysis.agreementPercent * 0.50)).coerceIn(50.0, 100.0)
        Signal.SELL -> (50.0 - (analysis.agreementPercent * 0.50)).coerceIn(0.0, 50.0)
        Signal.WAIT -> 50.0
    }

    val animatedScore by animateFloatAsState(
        targetValue = signalScore.toFloat(),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "gauge_needle_score"
    )

    val signalColor by animateColorAsState(
        targetValue = when (analysis.overallSignal) {
            Signal.BUY -> SignalBuy
            Signal.SELL -> SignalSell
            Signal.WAIT -> SignalWait
        },
        label = "unified_signal_color"
    )

    val signalBg = when (analysis.overallSignal) {
        Signal.BUY -> SignalBuyBg
        Signal.SELL -> SignalSellBg
        Signal.WAIT -> SignalWaitBg
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("unified_signal_dashboard_view"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. SPOT TICKER & TIMEFRAME SELECTOR ---
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = ObsidianSurfaceCard,
            border = BorderStroke(1.2.dp, ObsidianBorderHighlight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header row: Symbol + Live Pulse + Refresh
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GoldPrimary.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "XAU/USD GOLD SPOT",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                color = GoldLight
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SignalBuy)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SignalBuy
                        )
                    }

                    // Refresh Button
                    IconButton(
                        onClick = onRefreshClick,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(ObsidianSurfaceElevated)
                            .border(1.dp, ObsidianBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = GoldLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Price and Delta
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "$${String.format(Locale.US, "%.2f", analysis.currentPrice)}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isUp = analysis.changeAmount >= 0
                            val changeColor = if (isUp) SignalBuy else SignalSell
                            val arrow = if (isUp) "▲" else "▼"
                            Text(
                                text = "$arrow $${String.format(Locale.US, "%.2f", kotlin.math.abs(analysis.changeAmount))} (${String.format(Locale.US, "%+.2f", analysis.changePercent)}%)",
                                color = changeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "24h change",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // 24h High / Low
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "H: $${String.format(Locale.US, "%.2f", analysis.high24h)}",
                            fontSize = 11.sp,
                            color = SignalBuy,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "L: $${String.format(Locale.US, "%.2f", analysis.low24h)}",
                            fontSize = 11.sp,
                            color = SignalSell,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Timeframe quick pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val intervals = listOf("1m", "5m", "15m", "1h", "4h", "1d")
                    intervals.forEach { tf ->
                        val isSelected = selectedInterval.equals(tf, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) GoldPrimary else ObsidianSurfaceElevated,
                            border = BorderStroke(
                                0.8.dp,
                                if (isSelected) GoldPrimary else ObsidianBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onIntervalSelected(tf) }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tf.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                    color = if (isSelected) OnAccent else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 2. CENTRAL REAL-TIME SIGNAL STRENGTH GAUGE (BUY / SELL / HOLD) ---
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ObsidianSurfaceCard,
            border = BorderStroke(1.5.dp, signalColor.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Card Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "⚡", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (currentLanguage) {
                                AppLanguage.ENGLISH -> "REAL-TIME SIGNAL STRENGTH"
                                AppLanguage.HINDI -> "रीयल-टाइम सिग्नल सामर्थ्य (ताकत)"
                                AppLanguage.MARATHI -> "रिअल-टाइम सिग्नल ताकद"
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = GoldLight,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = GoldPrimary.copy(alpha = 0.15f),
                        border = BorderStroke(0.6.dp, GoldPrimary.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "28 INDICATORS",
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = GoldLight
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Canvas Speedometer Gauge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .height(170.dp)
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val strokeWidth = 24.dp.toPx()
                        val arcRadius = (canvasWidth / 2f) - (strokeWidth / 2f) - 10f
                        val centerOffset = Offset(canvasWidth / 2f, canvasHeight * 0.95f)

                        // 1. Background Arc Track
                        drawArc(
                            color = ObsidianSurfaceElevated,
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = Offset(centerOffset.x - arcRadius, centerOffset.y - arcRadius),
                            size = Size(arcRadius * 2, arcRadius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // 2. Coloured Zone Segments:
                        // Strong Sell (Red): 180° to 225° (sweep 45°)
                        drawArc(
                            color = SignalSell,
                            startAngle = 180f,
                            sweepAngle = 45f,
                            useCenter = false,
                            topLeft = Offset(centerOffset.x - arcRadius, centerOffset.y - arcRadius),
                            size = Size(arcRadius * 2, arcRadius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )

                        // Moderate Sell (Coral): 225° to 255° (sweep 30°)
                        drawArc(
                            color = SignalSell.copy(alpha = 0.65f),
                            startAngle = 225f,
                            sweepAngle = 30f,
                            useCenter = false,
                            topLeft = Offset(centerOffset.x - arcRadius, centerOffset.y - arcRadius),
                            size = Size(arcRadius * 2, arcRadius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )

                        // Hold / Neutral Chop (Amber): 255° to 285° (sweep 30°)
                        drawArc(
                            color = SignalWait,
                            startAngle = 255f,
                            sweepAngle = 30f,
                            useCenter = false,
                            topLeft = Offset(centerOffset.x - arcRadius, centerOffset.y - arcRadius),
                            size = Size(arcRadius * 2, arcRadius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )

                        // Moderate Buy (Mint): 285° to 315° (sweep 30°)
                        drawArc(
                            color = SignalBuy.copy(alpha = 0.65f),
                            startAngle = 285f,
                            sweepAngle = 30f,
                            useCenter = false,
                            topLeft = Offset(centerOffset.x - arcRadius, centerOffset.y - arcRadius),
                            size = Size(arcRadius * 2, arcRadius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )

                        // Strong Buy (Neon Green): 315° to 360° (sweep 45°)
                        drawArc(
                            color = SignalBuy,
                            startAngle = 315f,
                            sweepAngle = 45f,
                            useCenter = false,
                            topLeft = Offset(centerOffset.x - arcRadius, centerOffset.y - arcRadius),
                            size = Size(arcRadius * 2, arcRadius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // 3. Pointer Needle
                        val needleAngleDeg = 180f + (animatedScore / 100f * 180f)
                        val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())
                        val needleLength = arcRadius - 16f
                        val needleEnd = Offset(
                            x = centerOffset.x + (needleLength * cos(needleAngleRad)).toFloat(),
                            y = centerOffset.y + (needleLength * sin(needleAngleRad)).toFloat()
                        )

                        // Draw needle line
                        drawLine(
                            color = GoldLight,
                            start = centerOffset,
                            end = needleEnd,
                            strokeWidth = 4.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        // Center Pivot Knob
                        drawCircle(
                            color = GoldPrimary,
                            radius = 10.dp.toPx(),
                            center = centerOffset
                        )
                        drawCircle(
                            color = OnAccent,
                            radius = 5.dp.toPx(),
                            center = centerOffset
                        )
                    }

                    // Center Numerical Readout & Verdict Pill
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${analysis.agreementPercent.toInt()}%",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = signalColor
                        )

                        val verdictText = when (analysis.overallSignal) {
                            Signal.BUY -> when (currentLanguage) {
                                AppLanguage.ENGLISH -> "STRONG BUY"
                                AppLanguage.HINDI -> "मजबूत BUY (खरीदारी)"
                                AppLanguage.MARATHI -> "मजबूत BUY (खरेदी)"
                            }
                            Signal.SELL -> when (currentLanguage) {
                                AppLanguage.ENGLISH -> "STRONG SELL"
                                AppLanguage.HINDI -> "मजबूत SELL (बिकवाली)"
                                AppLanguage.MARATHI -> "मजबूत SELL (विक्री)"
                            }
                            Signal.WAIT -> when (currentLanguage) {
                                AppLanguage.ENGLISH -> "HOLD / STANDBY"
                                AppLanguage.HINDI -> "HOLD / इंतज़ार करें"
                                AppLanguage.MARATHI -> "HOLD / थांबा"
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = signalBg,
                            border = BorderStroke(1.dp, signalColor.copy(alpha = 0.7f))
                        ) {
                            Text(
                                text = verdictText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = signalColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Zone Labels (SELL - HOLD - BUY)
                Row(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "🔴 0% SELL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SignalSell)
                    Text(text = "🟡 50% HOLD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SignalWait)
                    Text(text = "🟢 100% BUY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SignalBuy)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Summary Explanation Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(0.8.dp, ObsidianBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = when (analysis.overallSignal) {
                                Signal.BUY -> when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "Bullish: the weighted pillar vote passed the 68% gate for BUY."
                                    AppLanguage.HINDI -> "तेजी: वेटेड पिलर वोट ने BUY के लिए 68% गेट पार किया।"
                                    AppLanguage.MARATHI -> "तेजी: वेटेड पिलर मताने BUY साठी 68% गेट पार केला."
                                }
                                Signal.SELL -> when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "Bearish: the weighted pillar vote passed the 68% gate for SELL."
                                    AppLanguage.HINDI -> "मंदी: वेटेड पिलर वोट ने SELL के लिए 68% गेट पार किया।"
                                    AppLanguage.MARATHI -> "उच्च अचूकता मंदीचा टप्पा: वरच्या स्तरावर मोठी विक्री सुरू आहे."
                                }
                                Signal.WAIT -> when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "Consolidation / Hold Protocol: Market is oscillating inside compression range. Capital preservation standby active to prevent false 50/50 chop losses."
                                    AppLanguage.HINDI -> "कंसोलिडेशन / होल्ड सुरक्षा नियम: मार्केट सीमित दायरे में फंसा है। गलत ट्रेड और नुकसान से बचने के लिए ऐप स्टैंडबाय पर है। स्पष्ट ब्रेकआउट का इंतज़ार करें।"
                                    AppLanguage.MARATHI -> "कन्सोलिडेशन / होल्ड सुरक्षा नियम: बाजार मर्यादित कक्षेत आहे. भांडवल सुरक्षित ठेवण्यासाठी थांबा."
                                }
                            },
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // --- 3. 🤖 KALANKAR QUANT AI BOT (AUTONOMOUS SIGNAL & EXECUTION) ---
        QuantBotCard(
            botSignal = analysis.quantBotSignal,
            onOpenCalculator = onOpenCalculator
        )

        // --- 3B. 🤖 MULTI-BOT ARSENAL (6 SPECIALIZED BOTS ENSEMBLE) ---
        analysis.multiBotEnsemble?.let { ensemble ->
            MultiBotArsenalCard(
                ensemble = ensemble,
                onOpenCalculator = onOpenCalculator
            )
        }

        // --- 4. 🧠 COMBINED 5-AI ENSEMBLE CONSENSUS MATRIX ---
        analysis.multiAiConsensus?.let { consensus ->
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = ObsidianSurfaceCard,
                border = BorderStroke(1.2.dp, GoldPrimary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "🧠", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (currentLanguage) {
                                    AppLanguage.ENGLISH -> "5-RULE COUNCIL ALIGNMENT"
                                    AppLanguage.HINDI -> "5 नियमों की वोटिंग व सहमति"
                                    AppLanguage.MARATHI -> "5 नियमांचे मतदान व सहमती"
                                },
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = GoldLight
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GoldPrimary.copy(alpha = 0.15f),
                            border = BorderStroke(0.6.dp, GoldPrimary)
                        ) {
                            Text(
                                text = "${consensus.agreeingModelsCount}/${consensus.totalModelsCount} MODELS AGREE",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = GoldLight
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 5 AI Cards Grid (Gemini, ChatGPT, Claude, DeepSeek, Perplexity)
                    consensus.modelInsights.forEach { model ->
                        val mColor = when (model.signal) {
                            Signal.BUY -> SignalBuy
                            Signal.SELL -> SignalSell
                            Signal.WAIT -> SignalWait
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ObsidianSurfaceElevated,
                            border = BorderStroke(0.8.dp, mColor.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(text = model.provider.iconEmoji, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = model.provider.displayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = model.provider.roleBadge,
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${model.confidencePercent}% pillars",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = mColor.copy(alpha = 0.15f),
                                        border = BorderStroke(0.8.dp, mColor.copy(alpha = 0.6f))
                                    ) {
                                        Text(
                                            text = model.signal.name,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = mColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 5. 7-PILLARS WEIGHTED INDICATOR MATRIX ---
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = ObsidianSurfaceCard,
            border = BorderStroke(1.2.dp, ObsidianBorderHighlight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "📊", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (currentLanguage) {
                                AppLanguage.ENGLISH -> "7-PILLARS INDICATOR WEIGHTS"
                                AppLanguage.HINDI -> "7-पिलर इंडिकेटर वेटेज विश्लेषण"
                                AppLanguage.MARATHI -> "7-पिलर इंडिकेटर वेटेज विश्लेषण"
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = GoldLight
                        )
                    }

                    Text(
                        text = "TAP FOR DEEP DIVE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val weights = mapOf(
                    "trend" to "3.0x",
                    "smc" to "3.0x",
                    "macro" to "2.5x",
                    "sr" to "2.0x",
                    "candlestick" to "2.0x",
                    "momentum" to "1.5x",
                    "volatility" to "1.5x"
                )

                analysis.groups.forEach { group ->
                    val pColor = when (group.verdict) {
                        Signal.BUY -> SignalBuy
                        Signal.SELL -> SignalSell
                        Signal.WAIT -> SignalWait
                    }
                    val weightTag = weights[group.key] ?: "1.5x"

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(0.8.dp, ObsidianBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPillarClick(group) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = GoldPrimary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = weightTag,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = GoldLight
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = group.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${group.indicators.size} indicators active",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = pColor.copy(alpha = 0.15f),
                                    border = BorderStroke(0.8.dp, pColor.copy(alpha = 0.6f))
                                ) {
                                    Text(
                                        text = group.verdict.name,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = pColor
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 6. SNIPER PULLBACK TRADE SETUP ---
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = ObsidianSurfaceCard,
            border = BorderStroke(1.2.dp, signalColor.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🎯", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (currentLanguage) {
                                AppLanguage.ENGLISH -> "SNIPER PULLBACK EXECUTION"
                                AppLanguage.HINDI -> "सटीक स्नाइपर ट्रेड सेटअप"
                                AppLanguage.MARATHI -> "अचूक स्नायपर ट्रेड सेटअप"
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = GoldLight
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = GoldPrimary.copy(alpha = 0.2f),
                        border = BorderStroke(0.6.dp, GoldPrimary)
                    ) {
                        Text(
                            text = "R:R ${analysis.tradeSetup.riskRewardRatio}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = GoldLight
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Entry & SL 2x2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(0.8.dp, ObsidianBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "ENTRY ZONE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", analysis.tradeSetup.entryPrice)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = TextPrimary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurfaceElevated,
                        border = BorderStroke(0.8.dp, ObsidianBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "STOP LOSS (-${String.format(Locale.US, "%.0f", analysis.tradeSetup.stopLossPips)} pips)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = SignalSell
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", analysis.tradeSetup.stopLoss)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = SignalSell
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SignalBuy.copy(alpha = 0.08f),
                        border = BorderStroke(0.8.dp, SignalBuy.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "TARGET 1 (+${String.format(Locale.US, "%.0f", analysis.tradeSetup.takeProfit1Pips)} pips)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = SignalBuy
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", analysis.tradeSetup.takeProfit1)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = SignalBuy
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SignalBuy.copy(alpha = 0.08f),
                        border = BorderStroke(0.8.dp, SignalBuy.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "TARGET 2 (+${String.format(Locale.US, "%.0f", analysis.tradeSetup.takeProfit2Pips)} pips)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = SignalBuy
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", analysis.tradeSetup.takeProfit2)}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = SignalBuy
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Lot Calculator action button
                Button(
                    onClick = { onOpenCalculator(analysis.tradeSetup.stopLossPips) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ObsidianSurfaceElevated,
                        contentColor = GoldLight
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.2.dp, GoldPrimary.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(imageVector = Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp), tint = GoldLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (currentLanguage) {
                            AppLanguage.ENGLISH -> "Calculate Safe Lot Size"
                            AppLanguage.HINDI -> "सुरक्षित लॉट साइज निकालें"
                            AppLanguage.MARATHI -> "सुरक्षित लॉट साइज काढा"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // --- 7. CLEAN PRO CANDLESTICK CHART ---
        ProCandleChart(
            candles = analysis.chartCandles.ifEmpty { analysis.recentCandles },
            groups = analysis.groups,
            buyerSellerRatio = analysis.buyerSellerRatio,
            tradeSetup = analysis.tradeSetup,
            pivotLevels = analysis.pivotLevels,
            currentPrice = analysis.currentPrice
        )

        // --- 8. ⚡ AUTONOMOUS ACCURACY VERIFICATION & PRODUCTION IMPROVEMENT ENGINE ---
        analysis.productionImprovement?.let { prodEngine ->
            ProductionImprovementAuditCard(
                engine = prodEngine,
                onRunAccuracyCheck = onRefreshClick
            )
        }
    }
}
