package com.example.livegoldai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.livegoldai.theme.*
import java.util.Locale
import kotlin.math.max

/**
 * V33.1: cannot be shown without a [grant] from ResearchGate.authorize (one ACTIVE plan of one snapshot).
 * The stop distance and price come from that plan and are not editable; targets use the plan's own reward/risk.
 */
@Composable
fun LotCalculatorDialog(
    grant: com.example.livegoldai.data.ResearchGate.LotGrant,
    lang: com.example.livegoldai.localization.AppLanguage = com.example.livegoldai.localization.AppLanguage.ENGLISH,
    onDismiss: () -> Unit
) {
    var balanceText by remember { mutableStateOf("5000") }
    var riskPercentText by remember { mutableStateOf("2.0") }
    val slPipsText = String.format(Locale.US, "%.0f", grant.stopPips)
    val currentGoldPrice = grant.price

    val balance = balanceText.toDoubleOrNull() ?: 5000.0
    val riskPercent = riskPercentText.toDoubleOrNull() ?: 2.0
    val slPips = max(slPipsText.toDoubleOrNull() ?: 50.0, 1.0)

    // XAU/USD standard contract: 1 lot = 100 oz. 1 pip ($0.10 price delta) = $10.00
    val riskDollar = balance * (riskPercent / 100.0)
    val pipValuePerStandardLot = 10.0
    val calculatedLot = (riskDollar / (slPips * pipValuePerStandardLot)).coerceIn(0.01, 100.0)

    // Estimated margin required assuming 1:100 leverage
    val marginRequired = (currentGoldPrice * 100.0 * calculatedLot) / 100.0
    // V33.1: from THIS plan's levels (before: always 1:1 and 1:2 whatever the plan said)
    val tp1Profit = com.example.livegoldai.data.ResearchGate.hypotheticalAtTarget(grant, riskDollar, grant.tp1)
    val tp2Profit = com.example.livegoldai.data.ResearchGate.hypotheticalAtTarget(grant, riskDollar, grant.tp2)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lot_calculator_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceCard),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(listOf(GoldPrimary, ObsidianBorder))
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "XAU/USD • " + com.example.livegoldai.data.HonestText.planHeadline(true, lang) + " • " + grant.interval.uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            color = GoldLight,
                            letterSpacing = 0.5.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Recommended Lot Highlight Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianSurfaceElevated,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(GoldPrimary, GoldDark))
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = com.example.livegoldai.data.HonestText.lotResultTitle(lang),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format(Locale.US, "%.2f Lots", calculatedLot),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = GoldLight
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Risking: $${String.format(Locale.US, "%.2f", riskDollar)} (${String.format(Locale.US, "%.1f", riskPercent)}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = SignalSell
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Account Balance Input + Preset chips
                Text(
                    text = "Account Balance (USD)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = GoldPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_balance")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Preset Balance Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("1000", "5000", "10000", "25000").forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (balanceText == preset) GoldPrimary else ObsidianSurfaceElevated,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { balanceText = preset }
                        ) {
                            Text(
                                text = "$$preset",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = if (balanceText == preset) ObsidianBackground else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Risk % and Stop Loss in Pips (2 columns)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Risk %",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = riskPercentText,
                            onValueChange = { riskPercentText = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = ObsidianBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = GoldPrimary
                            ),
                            modifier = Modifier.testTag("input_risk")
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SL (Pips)",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = slPipsText,
                            onValueChange = { },
                            readOnly = true,    // the plan's stop, not a free input
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = ObsidianBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = GoldPrimary
                            ),
                            modifier = Modifier.testTag("input_sl_pips")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Projected Profits Breakdown
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        CalcRow(label = "TP1 (hypothetical)", value = tp1Profit?.let { "+$" + String.format(Locale.US, "%.2f", it) } ?: "--", color = SignalBuy)
                        Spacer(modifier = Modifier.height(4.dp))
                        CalcRow(label = "TP2 (hypothetical)", value = tp2Profit?.let { "+$" + String.format(Locale.US, "%.2f", it) } ?: "--", color = GoldLight)
                        Spacer(modifier = Modifier.height(4.dp))
                        CalcRow(label = "Est. Margin (1:100)", value = "$${String.format(Locale.US, "%.2f", marginRequired)}", color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = com.example.livegoldai.data.HonestText.hypotheticalNote(lang) + " • snapshot " + grant.snapshotId,
                            style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("close_calc_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = ObsidianBackground
                    )
                ) {
                    Text(
                        text = "Apply & Done",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun CalcRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
    }
}
