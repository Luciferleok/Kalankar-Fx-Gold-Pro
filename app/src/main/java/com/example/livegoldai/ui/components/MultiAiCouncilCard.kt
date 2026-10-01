package com.example.livegoldai.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.model.AiModelProvider
import com.example.livegoldai.model.MultiAiConsensusReport
import com.example.livegoldai.model.Signal
import com.example.livegoldai.model.SingleAiPredictionInsight
import com.example.livegoldai.theme.*

@Composable
fun MultiAiCouncilCard(
    consensus: MultiAiConsensusReport?,
    onRefreshAiCouncil: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (consensus == null) return

    val currentLang = LocalAppLanguage.current
    val context = LocalContext.current
    var selectedModelProvider by remember { mutableStateOf(AiModelProvider.GEMINI) }
    var showAllJointCorrections by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "multi_ai_pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    val signalColor = when (consensus.consensusSignal) {
        Signal.BUY -> NeonGreen
        Signal.SELL -> NeonRed
        Signal.WAIT -> AmberWarning
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("multi_ai_council_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceCard),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    GoldPrimary.copy(alpha = 0.9f),
                    signalColor.copy(alpha = 0.6f),
                    ObsidianBorderHighlight,
                    GoldDark
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: Multi-AI Council Badge & Live Pulse
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GoldPrimary.copy(alpha = 0.18f))
                            .border(1.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🤖", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "ALGORITHMIC ENGINES (5 LOCAL RULES)"
                                    AppLanguage.HINDI -> "नियम सहमति परिषद (5 लोकल नियम)"
                                    AppLanguage.MARATHI -> "नियम सहमती समिती (5 लोकल नियम)"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = GoldLight,
                                letterSpacing = 0.6.sp,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = "Algorithms on this phone, not AI • real AI is in the AI tab",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = TextSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = signalColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, signalColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(signalColor.copy(alpha = pulseGlow))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "${consensus.agreeingModelsCount}/${consensus.totalModelsCount} AGREE",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.ExtraBold,
                            color = signalColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Massive Consensus Verdict Banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = signalColor.copy(alpha = 0.12f),
                border = BorderStroke(1.5.dp, signalColor.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "RULE COUNCIL VERDICT:"
                                    AppLanguage.HINDI -> "5 नियमों का संयुक्त फैसला:"
                                    AppLanguage.MARATHI -> "5 नियमांचा अंतिम निर्णय:"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = signalColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when (consensus.consensusSignal) {
                                    Signal.BUY -> when (currentLang) {
                                        AppLanguage.ENGLISH -> "STRONG CONSENSUS: BUY 🟢"
                                        AppLanguage.HINDI -> "पूर्ण सहमति: BUY करें 🟢"
                                        AppLanguage.MARATHI -> "एकमुखाने निर्णय: BUY करा 🟢"
                                    }
                                    Signal.SELL -> when (currentLang) {
                                        AppLanguage.ENGLISH -> "STRONG CONSENSUS: SELL 🔴"
                                        AppLanguage.HINDI -> "पूर्ण सहमति: SELL करें 🔴"
                                        AppLanguage.MARATHI -> "एकमुखाने निर्णय: SELL करा 🔴"
                                    }
                                    Signal.WAIT -> when (currentLang) {
                                        AppLanguage.ENGLISH -> "CONSENSUS: WAIT / CAPITAL PRESERVATION 🟡"
                                        AppLanguage.HINDI -> "सहमति: इंतज़ार करें (WAIT) 🟡"
                                        AppLanguage.MARATHI -> "सहमती: शांत रहा (WAIT) 🟡"
                                    }
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = signalColor,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = "${consensus.consensusConfidence}% RULE AGREEMENT",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Black,
                                color = Color.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = consensus.getSummary(currentLang),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Multi-AI Selector Chips (Toggle each model)
            Text(
                text = when (currentLang) {
                    AppLanguage.ENGLISH -> "SELECT AI MODEL TO INSPECT CORRECTION:"
                    AppLanguage.HINDI -> "AI मॉडल चुनें और देखें उसने प्रेडिक्शन कैसे सुधारा:"
                    AppLanguage.MARATHI -> "AI मॉडेल निवडून त्याने अंदाज कसा सुधारला ते पाहा:"
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Black,
                color = TextMuted,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                consensus.modelInsights.forEach { insight ->
                    val isSelected = insight.provider == selectedModelProvider
                    val modelColor = when (insight.provider) {
                        AiModelProvider.GEMINI -> Color(0xFF6E97FF)
                        AiModelProvider.CHAT_GPT -> Color(0xFF10A37F)
                        AiModelProvider.CLAUDE -> Color(0xFFE28743)
                        AiModelProvider.DEEP_SEEK -> Color(0xFF3B82F6)
                        AiModelProvider.PERPLEXITY -> Color(0xFF22C55E)
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) modelColor.copy(alpha = 0.22f) else ObsidianSurfaceElevated,
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 1.dp,
                            if (isSelected) modelColor else ObsidianBorder
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedModelProvider = insight.provider }
                            .testTag("ai_chip_${insight.provider.name}")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = insight.provider.iconEmoji, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = insight.provider.displayName.substringBefore(" "),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                color = if (isSelected) modelColor else TextSecondary
                            )
                            val voteIcon = when (insight.signal) {
                                Signal.BUY -> "🟢"
                                Signal.SELL -> "🔴"
                                Signal.WAIT -> "🟡"
                            }
                            Text(
                                text = voteIcon,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selected AI Model Detailed Thesis & How It Corrected The Prediction
            val selectedInsight = consensus.modelInsights.firstOrNull { it.provider == selectedModelProvider }
                ?: consensus.modelInsights.first()

            val selectedColor = when (selectedInsight.provider) {
                AiModelProvider.GEMINI -> Color(0xFF6E97FF)
                AiModelProvider.CHAT_GPT -> Color(0xFF10A37F)
                AiModelProvider.CLAUDE -> Color(0xFFE28743)
                AiModelProvider.DEEP_SEEK -> Color(0xFF3B82F6)
                AiModelProvider.PERPLEXITY -> Color(0xFF22C55E)
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ObsidianSurfaceElevated,
                border = BorderStroke(1.dp, selectedColor.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = selectedInsight.provider.iconEmoji, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = selectedInsight.provider.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Black,
                                    color = selectedColor
                                )
                                Text(
                                    text = "${selectedInsight.provider.company} • ${selectedInsight.provider.roleBadge}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = TextMuted
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = (if (selectedInsight.signal == Signal.BUY) SignalBuy else if (selectedInsight.signal == Signal.SELL) SignalSell else AmberWarning).copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "${selectedInsight.signal.name} (${selectedInsight.confidencePercent}% of pillars agree)",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Black,
                                color = if (selectedInsight.signal == Signal.BUY) SignalBuy else if (selectedInsight.signal == Signal.SELL) SignalSell else AmberWarning
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // AI's Core Thesis
                    Text(
                        text = selectedInsight.getCoreThesis(currentLang),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 🛡️ HOW THIS AI CORRECTED THE PREDICTION
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = GoldPrimary.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(16.dp).padding(top = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = when (currentLang) {
                                        AppLanguage.ENGLISH -> "${selectedInsight.provider.displayName.uppercase()} • TRACK RECORD:"
                                        AppLanguage.HINDI -> "${selectedInsight.provider.displayName} • असली रिकॉर्ड:"
                                        AppLanguage.MARATHI -> "${selectedInsight.provider.displayName} • खरा रेकॉर्ड:"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.Black,
                                    color = GoldLight
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = selectedInsight.getCorrectionApplied(currentLang),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    selectedInsight.keyTrapWarned?.let { trap ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️ Trap Blocked: $trap",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = AmberWarning
                            )
                            Text(
                                text = "SL: -${selectedInsight.suggestedStopLossPips.toInt()}p | TP: +${selectedInsight.suggestedTargetPips.toInt()}p",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Joint Multi-AI Corrections List (Galtiyan Na Hone Ke Niyam)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ObsidianSurfaceCard,
                border = BorderStroke(1.dp, ObsidianBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAllJointCorrections = !showAllJointCorrections }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Gavel,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "RULE COUNCIL NOTES"
                                    AppLanguage.HINDI -> "नियम काउंसिल नोट्स"
                                    AppLanguage.MARATHI -> "नियम कौन्सिल नोट्स"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Black,
                                color = GoldLight
                            )
                        }
                        Icon(
                            imageVector = if (showAllJointCorrections) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    AnimatedVisibility(visible = showAllJointCorrections) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            consensus.getJointCorrections(currentLang).forEach { rule ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(text = "🛡️", fontSize = 10.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = rule,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: [ Consult Council Live ] & [ Copy Multi-AI Report ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onRefreshAiCouncil()
                        Toast.makeText(
                            context,
                            "🔄 Rules re-calculated on the latest price",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("consult_multi_ai_council_btn")
                ) {
                    Icon(imageVector = Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (currentLang) {
                            AppLanguage.ENGLISH -> "Consult Council"
                            AppLanguage.HINDI -> "AI काउंसिल से पूछें"
                            AppLanguage.MARATHI -> "AI कौन्सिल सल्ला"
                        },
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val reportText = buildString {
                            appendLine("🏆 KALANKAR FX RULE COUNCIL REPORT:")
                            appendLine("Consensus Verdict: ${consensus.consensusSignal.name} (${consensus.consensusConfidence}% Win Confidence)")
                            appendLine("Agreeing Models: ${consensus.agreeingModelsCount}/${consensus.totalModelsCount}")
                            appendLine("• Rule 1: ${consensus.modelInsights.firstOrNull { it.provider == AiModelProvider.GEMINI }?.signal?.name}")
                            appendLine("• Rule 2: ${consensus.modelInsights.firstOrNull { it.provider == AiModelProvider.CHAT_GPT }?.signal?.name}")
                            appendLine("• Rule 3: ${consensus.modelInsights.firstOrNull { it.provider == AiModelProvider.CLAUDE }?.signal?.name}")
                            appendLine("• Rule 4: ${consensus.modelInsights.firstOrNull { it.provider == AiModelProvider.DEEP_SEEK }?.signal?.name}")
                            appendLine("Summary: ${consensus.getSummary(currentLang)}")
                        }
                        val clip = ClipData.newPlainText("Rule Council", reportText)
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, "✅ Rule Council Report Copied!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldLight),
                    modifier = Modifier.testTag("copy_multi_ai_report_btn")
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Copy", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
