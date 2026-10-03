package com.example.livegoldai.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import com.example.livegoldai.model.ProductionImprovementEngine
import com.example.livegoldai.theme.*

/**
 * ⚡ AUTONOMOUS ACCURACY VERIFICATION & PRODUCTION IMPROVEMENT ENGINE
 * Continuously audits algorithm accuracy on real market candles, auto-tunes indicator weights,
 * and displays active machine-learned production safeguards.
 */
@Composable
fun ProductionImprovementAuditCard(
    engine: ProductionImprovementEngine?,
    onRunAccuracyCheck: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (engine == null) return

    val currentLang = LocalAppLanguage.current
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "prod_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "prodPulse"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("production_improvement_audit_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceCard),
        border = BorderStroke(
            1.2.dp,
            Brush.linearGradient(
                listOf(
                    GoldPrimary.copy(alpha = pulseAlpha),
                    ObsidianBorderHighlight,
                    NeonGreen.copy(alpha = 0.5f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header: Title + Auto-Tuning Badge + Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NeonGreen.copy(alpha = 0.15f))
                            .border(1.dp, NeonGreen.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "⚡", fontSize = 18.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "MODEL PRODUCTION IMPROVER"
                                    AppLanguage.HINDI -> "सत्यापन व उत्पादन मॉडल सुधार"
                                    AppLanguage.MARATHI -> "सत्यापन व मॉडेल सुधारणा"
                                },
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Black,
                                color = GoldLight,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = when (currentLang) {
                                AppLanguage.ENGLISH -> "Self-Calibrating Feedback Loop Active"
                                AppLanguage.HINDI -> "ऑटो-कैलिब्रेटिंग फीडबैक लूप सक्रिय"
                                AppLanguage.MARATHI -> "ऑटो-कॅलिब्रेटिंग फीडबॅक लूप सक्रिय"
                            },
                            fontSize = 10.sp,
                            color = NeonGreen
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = NeonGreen.copy(alpha = 0.15f),
                    border = BorderStroke(0.6.dp, NeonGreen)
                ) {
                    Text(
                        text = if (engine.verifiedAccuracyPercent < 0) "COLLECTING DATA" else "${String.format(java.util.Locale.US, "%.1f", engine.verifiedAccuracyPercent)}% REAL",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonGreen,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3-Metric KPI Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metric 1: Verified Accuracy
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(0.8.dp, ObsidianBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "REAL ACCURACY", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = if (engine.verifiedAccuracyPercent < 0) "--" else "${String.format(java.util.Locale.US, "%.1f", engine.verifiedAccuracyPercent)}%", fontSize = 14.sp, fontWeight = FontWeight.Black, color = NeonGreen)
                        Text(text = "${engine.totalBacktestedTrades} checked predictions", fontSize = 8.sp, color = TextSecondary)
                    }
                }

                // Metric 2: Profit Factor
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(0.8.dp, ObsidianBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "PROFIT FACTOR", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = if (engine.profitFactor < 0) "--" else "${String.format(java.util.Locale.US, "%.2f", engine.profitFactor)}x", fontSize = 14.sp, fontWeight = FontWeight.Black, color = GoldLight)
                        Text(text = "${String.format(java.util.Locale.US, "%+.1f", engine.averagePipGainPerTrade)} pips avg move", fontSize = 8.sp, color = TextSecondary)
                    }
                }

                // Metric 3: Chop Filter Threshold
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(0.8.dp, ObsidianBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "SIGNAL GATE", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = "${engine.dynamicConfidenceThreshold}%", fontSize = 14.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                        Text(text = "Weighted agreement", fontSize = 8.sp, color = TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Summary description of how the production model improves
            Text(
                text = when (currentLang) {
                    AppLanguage.ENGLISH -> "🔄 Learning engine: every prediction is saved and checked against real prices when it expires. A rule changes the model only after 50+ samples and a shadow test; one loss never changes anything."
                    AppLanguage.HINDI -> "🔄 लर्निंग इंजन: हर प्रेडिक्शन सेव होती है और समय खत्म होने पर असली भाव से जाँची जाती है। कोई नियम 50+ सैंपल और शैडो टेस्ट के बाद ही मॉडल बदलता है; एक गलती से कुछ नहीं बदलता।"
                    AppLanguage.MARATHI -> "🔄 लर्निंग इंजिन: प्रत्येक अंदाज जतन होतो आणि वेळ संपल्यावर खऱ्या भावाशी तपासला जातो. नियम 50+ नमुने आणि शॅडो टेस्टनंतरच मॉडेल बदलतो; एका चुकीने काही बदलत नाही."
                },
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = TextPrimary
            )

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        text = when (currentLang) {
                            AppLanguage.ENGLISH -> "ACTIVE PRODUCTION SAFEGUARDS & FIXES"
                            AppLanguage.HINDI -> "सक्रिय मॉडल सुधार व सुरक्षा नियम"
                            AppLanguage.MARATHI -> "सक्रिय मॉडेल सुधारणा व नियम"
                        },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        color = GoldLight,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (engine.appliedProductionFixes.isEmpty()) {
                        Text(
                            text = when (currentLang) {
                                AppLanguage.ENGLISH -> "No learned fix is active yet. Status: ${engine.statusBadge}."
                                AppLanguage.HINDI -> "अभी कोई सीखा हुआ सुधार चालू नहीं। स्थिति: ${engine.statusBadge}।"
                                AppLanguage.MARATHI -> "अजून कोणतीही शिकलेली सुधारणा चालू नाही. स्थिती: ${engine.statusBadge}."
                            },
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }

                    engine.appliedProductionFixes.forEach { fix ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianSurfaceElevated,
                            border = BorderStroke(0.8.dp, ObsidianBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (currentLang == AppLanguage.HINDI) fix.ruleTitleHindi else fix.ruleTitle,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = NeonGreen.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "APPLIED 🛡️",
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Black,
                                            color = NeonGreen,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "❌ Prevented: ${if (currentLang == AppLanguage.HINDI) fix.errorPreventedHindi else fix.errorPrevented}",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "✅ Impact: ${if (currentLang == AppLanguage.HINDI) fix.improvementImpactHindi else fix.improvementImpact}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = GoldLight
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Pillar Weights Matrix
                    Text(
                        text = when (currentLang) {
                            AppLanguage.ENGLISH -> "CALIBRATED PILLAR WEIGHTS"
                            AppLanguage.HINDI -> "एडजस्टेड इंडिकेटर वेटेज मैट्रिक्स"
                            AppLanguage.MARATHI -> "इंडिकेटर वेटेज मॅट्रिक्स"
                        },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        color = GoldLight,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    engine.liveModelWeights.take(4).forEach { weightItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (currentLang == AppLanguage.HINDI) weightItem.pillarNameHindi else weightItem.pillarName,
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = GoldPrimary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${weightItem.weightMultiplier}x Weight",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = GoldLight,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action: Run Verification Audit Simulation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { isExpanded = !isExpanded },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isExpanded) "LESS DETAILS" else "VIEW SAFEGUARDS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                }

                Button(
                    onClick = {
                        onRunAccuracyCheck()
                        Toast.makeText(context, "Checking expired predictions against real prices…", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = OnAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (currentLang) {
                            AppLanguage.ENGLISH -> "VERIFY NOW"
                            AppLanguage.HINDI -> "एक्यूरेसी जांचें"
                            AppLanguage.MARATHI -> "अचूकता तपासा"
                        },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        color = OnAccent
                    )
                }
            }
        }
    }
}
