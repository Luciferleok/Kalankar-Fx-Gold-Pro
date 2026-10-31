package com.example.livegoldai.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.livegoldai.data.PositionSizer
import com.example.livegoldai.theme.*
import java.math.BigDecimal
import java.util.Locale

/**
 * V33.1: cannot be shown without a [grant] from ResearchGate.authorize (one ACTIVE plan of one snapshot).
 * V33.1R.4-a: all numbers come from PositionSizer: no forced minimum lot, no silent defaults, no assumed pip value or
 * leverage, exact stop distance, and risk / targets / margin from the same rounded-down volume. Broker data the user
 * enters is kept on the phone (not secret; not sent anywhere).
 */
@Composable
fun LotCalculatorDialog(
    grant: com.example.livegoldai.data.ResearchGate.LotGrant,
    lang: com.example.livegoldai.localization.AppLanguage = com.example.livegoldai.localization.AppLanguage.ENGLISH,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("broker_spec_v1", Context.MODE_PRIVATE) }
    fun saved(k: String) = prefs.getString(k, "") ?: ""

    var balanceText by remember { mutableStateOf("") }
    var riskPercentText by remember { mutableStateOf("") }
    var slippageText by remember { mutableStateOf("") }
    var contractText by remember { mutableStateOf(saved("contract")) }
    var minText by remember { mutableStateOf(saved("min")) }
    var stepText by remember { mutableStateOf(saved("step")) }
    var maxText by remember { mutableStateOf(saved("max")) }
    var leverageText by remember { mutableStateOf(saved("leverage")) }
    var currencyText by remember { mutableStateOf(saved("currency")) }
    var rateText by remember { mutableStateOf(saved("rate")) }
    var showBroker by remember { mutableStateOf(contractText.isBlank() || minText.isBlank() || stepText.isBlank() || maxText.isBlank() || currencyText.isBlank()) }

    val spec = PositionSizer.BrokerSpec(
        contractSize = PositionSizer.parse(contractText), minVolume = PositionSizer.parse(minText),
        volumeStep = PositionSizer.parse(stepText), maxVolume = PositionSizer.parse(maxText),
        leverage = PositionSizer.parse(leverageText), accountCurrency = currencyText.trim().uppercase().ifBlank { null },
        usdToAccountRate = PositionSizer.parse(rateText)
    )
    val result = PositionSizer.size(
        PositionSizer.Input(balanceText, riskPercentText, slippageText, grant.entry, grant.stop, grant.tp1, grant.tp2, grant.price),
        spec
    )
    fun money(x: Double, cur: String) = String.format(Locale.US, "%,.2f", x) + " " + cur
    fun vol(x: Double) = BigDecimal.valueOf(x).stripTrailingZeros().toPlainString()
    val stopLabel = String.format(Locale.US, "$%.2f price distance (%.1f pips)", kotlin.math.abs(grant.entry - grant.stop), grant.stopPips)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().testTag("lot_calculator_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceCard),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(GoldPrimary, ObsidianBorder)))
        ) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Calculate, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "XAU/USD • " + com.example.livegoldai.data.HonestText.planHeadline(true, lang) + " • " + grant.interval.uppercase(),
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = GoldLight, letterSpacing = 0.5.sp
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Result
                Surface(
                    shape = RoundedCornerShape(16.dp), color = ObsidianSurfaceElevated,
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(GoldPrimary, GoldDark))),
                    modifier = Modifier.fillMaxWidth().testTag("lot_result")
                ) {
                    Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = com.example.livegoldai.data.HonestText.lotResultTitle(lang), style = MaterialTheme.typography.labelSmall, color = TextMuted, letterSpacing = 0.8.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        when (val r = result) {
                            is PositionSizer.Result.Ok -> {
                                Text(text = vol(r.volume) + " lots", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = GoldLight)
                                Text(text = "Planned risk at stop: " + money(r.plannedRisk, r.currency) + " (budget " + money(r.budget, r.currency) + ")",
                                    style = MaterialTheme.typography.bodySmall, color = SignalSell)
                                if (r.cappedByMaxVolume) Text(text = "Limited by the broker's maximum volume.", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                            is PositionSizer.Result.NoSafeSize -> {
                                Text(text = "NO SAFE POSITION SIZE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = SignalSell)
                                Text(text = "The broker minimum of " + vol(r.minVolume) + " lots would risk " + money(r.riskAtMinimum, r.currency) +
                                    ", more than your budget of " + money(r.budget, r.currency) + ".", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            is PositionSizer.Result.Unavailable -> {
                                Text(text = "UNAVAILABLE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = SignalWait)
                                Text(text = "Enter your broker's values: " + r.missing.joinToString(", ") { specName(it) } + ".",
                                    style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            }
                            is PositionSizer.Result.Invalid -> {
                                Text(text = "CHECK YOUR INPUT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = SignalWait)
                                r.errors.forEach { Text(text = "• " + inputError(it), style = MaterialTheme.typography.bodySmall, color = TextSecondary) }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LotField("Account balance (account currency)", balanceText, KeyboardType.Decimal, "input_balance") { balanceText = it }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("1000", "5000", "10000", "25000").forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (balanceText == preset) GoldPrimary else ObsidianSurfaceElevated,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { balanceText = preset }
                        ) {
                            Text(text = preset, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), fontWeight = FontWeight.Bold,
                                color = if (balanceText == preset) ObsidianBackground else TextSecondary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.weight(1f)) { LotField("Risk %", riskPercentText, KeyboardType.Decimal, "input_risk") { riskPercentText = it } }
                    Box(modifier = Modifier.weight(1f)) { LotField("Slippage allowance (USD price)", slippageText, KeyboardType.Decimal, "input_slippage") { slippageText = it } }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Stop from the plan: $stopLabel (not editable)", style = MaterialTheme.typography.labelSmall, color = TextMuted,
                    modifier = Modifier.testTag("input_sl_pips"))

                Spacer(modifier = Modifier.height(10.dp))

                // Broker specification (kept on this phone)
                Text(
                    text = (if (showBroker) "▼ " else "▶ ") + "Broker contract specification",
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = GoldLight,
                    modifier = Modifier.clickable { showBroker = !showBroker }.padding(vertical = 4.dp)
                )
                if (showBroker) {
                    Text(text = "Copy these from your broker's XAU/USD contract page. They differ between brokers.",
                        style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) { LotField("Contract size (oz per lot)", contractText, KeyboardType.Decimal, "input_contract") { contractText = it; prefs.edit().putString("contract", it).apply() } }
                        Box(modifier = Modifier.weight(1f)) { LotField("Leverage (100 = 1:100)", leverageText, KeyboardType.Decimal, "input_leverage") { leverageText = it; prefs.edit().putString("leverage", it).apply() } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) { LotField("Min lot", minText, KeyboardType.Decimal, "input_min") { minText = it; prefs.edit().putString("min", it).apply() } }
                        Box(modifier = Modifier.weight(1f)) { LotField("Lot step", stepText, KeyboardType.Decimal, "input_step") { stepText = it; prefs.edit().putString("step", it).apply() } }
                        Box(modifier = Modifier.weight(1f)) { LotField("Max lot", maxText, KeyboardType.Decimal, "input_max") { maxText = it; prefs.edit().putString("max", it).apply() } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) { LotField("Account currency (USD, INR…)", currencyText, KeyboardType.Text, "input_currency") { currencyText = it; prefs.edit().putString("currency", it).apply() } }
                        Box(modifier = Modifier.weight(1f)) { LotField("1 USD = ? account currency", rateText, KeyboardType.Decimal, "input_rate") { rateText = it; prefs.edit().putString("rate", it).apply() } }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val ok = result as? PositionSizer.Result.Ok
                Surface(shape = RoundedCornerShape(12.dp), color = ObsidianSurfaceElevated, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        CalcRow(label = "TP1 (hypothetical, this volume)", value = ok?.let { o -> o.tp1Profit?.let { "+" + money(it, o.currency) } } ?: "--", color = SignalBuy)
                        Spacer(modifier = Modifier.height(4.dp))
                        CalcRow(label = "TP2 (hypothetical, this volume)", value = ok?.let { o -> o.tp2Profit?.let { "+" + money(it, o.currency) } } ?: "--", color = GoldLight)
                        Spacer(modifier = Modifier.height(4.dp))
                        CalcRow(label = "Est. margin", value = ok?.let { o -> o.margin?.let { money(it, o.currency) } ?: "UNAVAILABLE (leverage not set)" } ?: "--",
                            color = TextSecondary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Planned risk only. A stop can fill worse than its level (gaps, fast markets, slippage beyond your allowance). " +
                                "Spread, commission, swap and currency-rate changes are not included. " +
                                com.example.livegoldai.data.HonestText.hypotheticalNote(lang) + " • snapshot " + grant.snapshotId,
                            style = MaterialTheme.typography.labelSmall, color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("close_calc_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBackground)
                ) {
                    Text(text = "Close", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

private fun specName(code: String) = when (code) {
    PositionSizer.M_CONTRACT -> "contract size"
    PositionSizer.M_MIN -> "min lot"
    PositionSizer.M_STEP -> "lot step"
    PositionSizer.M_MAX -> "max lot (not below min lot)"
    PositionSizer.M_CURRENCY -> "account currency (3 letters)"
    PositionSizer.M_RATE -> "USD to account-currency rate"
    else -> code
}

private fun inputError(code: String) = when (code) {
    PositionSizer.E_BALANCE -> "Account balance must be a number above 0."
    PositionSizer.E_RISK -> "Risk % must be above 0 and at most 100."
    PositionSizer.E_SLIPPAGE -> "Slippage allowance must be 0 or more (leave empty for 0)."
    PositionSizer.E_LEVELS -> "The plan's entry and stop are not usable."
    else -> code
}

@Composable
private fun LotField(label: String, value: String, keyboard: KeyboardType, tag: String, onChange: (String) -> Unit) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary, unfocusedBorderColor = ObsidianBorder,
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = GoldPrimary
            ),
            modifier = Modifier.fillMaxWidth().testTag(tag)
        )
    }
}

@Composable
private fun CalcRow(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = color)
    }
}
