package com.example.livegoldai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.model.GroupAnalysis
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*

/**
 * INDICATOR WORKSPACE (V20.2)
 *
 * Every indicator the engine really calculates, straight from the analysis result (nothing is hardcoded here):
 * name, live value, what it says now and its pillar. Search and pillar filter are instant.
 * Indicators that need volume are marked when the current price feed has no real volume.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartIndicatorSheet(
    groups: List<GroupAnalysis>,
    hasRealVolume: Boolean,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }

    val total = groups.sumOf { it.indicators.size }
    val q = query.trim()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianBackground,
        contentColor = TextPrimary,
        scrimColor = Color(0xFF000000).copy(alpha = 0.7f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier.testTag("chart_indicator_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "INDICATORS", color = GoldPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "$total live • ${groups.size} pillars", color = TextMuted, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(text = "Search: RSI, EMA, ATR…", color = TextMuted, fontSize = 13.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = ObsidianBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = GoldPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("indicator_search")
            )
            Spacer(modifier = Modifier.height(10.dp))

            // pillar filter
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                (listOf("ALL" to "ALL") + groups.map { it.key to it.title.substringBefore(" &").substringBefore(" (").uppercase() }).forEach { (key, label) ->
                    val on = filter == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) GoldPrimary.copy(alpha = 0.14f) else ObsidianSurfaceElevated)
                            .clickable { filter = key }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(text = label, color = if (on) GoldPrimary else TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                var shown = 0
                groups.filter { filter == "ALL" || it.key == filter }.forEach { g ->
                    val items = g.indicators.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
                    if (items.isNotEmpty()) {
                        Text(
                            text = g.title.uppercase() + "  •  " + g.verdict.name,
                            color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                        )
                    }
                    items.forEach { ind ->
                        shown++
                        val needsVolume = !hasRealVolume && listOf("VWAP", "MFI", "Volume", "Money Flow").any { ind.name.contains(it, ignoreCase = true) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = ind.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    text = if (needsVolume) "This feed has no real volume: treat as a price-only reading" else ind.detail,
                                    color = if (needsVolume) SignalWait else TextMuted, fontSize = 10.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = ind.valueDisplay, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(
                                    text = ind.signal.name,
                                    color = when (ind.signal) { Signal.BUY -> SignalBuy; Signal.SELL -> SignalSell; Signal.WAIT -> SignalWait },
                                    fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
                if (shown == 0) {
                    Text(text = "No indicator matches \"$q\".", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "These are the indicators the signal engine votes with. On the chart you can draw SuperTrend, EMA 9/21, Bollinger and pivot lines.",
                    color = TextMuted, fontSize = 10.sp
                )
            }
        }
    }
}
