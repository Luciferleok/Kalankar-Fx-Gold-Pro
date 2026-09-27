package com.example.livegoldai.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalAppLanguage
import com.example.livegoldai.model.BuyerSellerSentiment
import com.example.livegoldai.model.CandleBar
import com.example.livegoldai.model.PivotLevels
import com.example.livegoldai.model.TradeSetup
import com.example.livegoldai.theme.*
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun FullscreenCandleChartDialog(
    candles: List<CandleBar>,
    currentPrice: Double,
    buyerSellerRatio: BuyerSellerSentiment? = null,
    tradeSetup: TradeSetup? = null,
    pivotLevels: PivotLevels? = null,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val currentLang = LocalAppLanguage.current

    // Chart View Settings
    var selectedTimeframe by remember { mutableStateOf("15M") }
    var candleCount by remember { mutableIntStateOf(45) }
    var showSuperTrend by remember { mutableStateOf(true) }
    var showEma by remember { mutableStateOf(true) }
    var showBb by remember { mutableStateOf(false) }
    var showVwap by remember { mutableStateOf(true) }
    var showVolume by remember { mutableStateOf(true) }
    var showTradeLevels by remember { mutableStateOf(true) }
    var showSmcZones by remember { mutableStateOf(true) }

    var selectedCandleIndex by remember { mutableStateOf<Int?>(null) }
    var crosshairTouchY by remember { mutableFloatStateOf(-1f) }

    val displayCandles = remember(candles, candleCount) {
        candles.takeLast(candleCount)
    }

    val maxPrice = displayCandles.maxOfOrNull { it.high } ?: 1.0
    val minPrice = displayCandles.minOfOrNull { it.low } ?: 0.0
    val priceSpan = max(maxPrice - minPrice, 0.5)

    val maxVolume = displayCandles.maxOfOrNull { it.volume ?: 1000.0 } ?: 1000.0

    val activeCandle = selectedCandleIndex?.let { idx ->
        if (idx in displayCandles.indices) displayCandles[idx] else null
    } ?: displayCandles.lastOrNull()

    val spotPrice = if (currentPrice > 0) currentPrice else (displayCandles.lastOrNull()?.close ?: 0.0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("fullscreen_candle_chart_dialog"),
            color = ObsidianBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // 1. Top Bar: Asset Symbol, Live Price, Timeframe & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GoldPrimary.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = "XAU/USD",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = GoldLight,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(NeonGreen)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$${String.format(Locale.US, "%,.2f", spotPrice)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "Gold Spot • Fullscreen Pro Chart",
                                fontSize = 9.sp,
                                color = TextMuted
                            )
                        }
                    }

                    // Close / Exit Fullscreen Button
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF261820),
                        border = BorderStroke(1.dp, SignalSell.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onDismiss() }
                            .testTag("btn_close_fullscreen_chart")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Fullscreen",
                                tint = SignalSell,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (currentLang) {
                                    AppLanguage.ENGLISH -> "CLOSE"
                                    AppLanguage.HINDI -> "बंद करें"
                                    AppLanguage.MARATHI -> "बंद करा"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SignalSell
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Timeframe Selector & Bar Count Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Timeframe buttons
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("1M", "5M", "15M", "30M", "1H", "4H", "1D").forEach { tf ->
                            val isSelected = selectedTimeframe == tf
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) GoldPrimary else ObsidianSurfaceElevated,
                                border = BorderStroke(0.8.dp, if (isSelected) GoldPrimary else ObsidianBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { selectedTimeframe = tf }
                            ) {
                                Text(
                                    text = tf,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                    color = if (isSelected) ObsidianBackground else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Candle count zoom
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        listOf(20, 35, 50, 75).forEach { count ->
                            val isSelected = candleCount == count
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) GoldLight.copy(alpha = 0.25f) else ObsidianSurfaceElevated,
                                border = BorderStroke(0.6.dp, if (isSelected) GoldLight else ObsidianBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        candleCount = count
                                        selectedCandleIndex = null
                                    }
                            ) {
                                Text(
                                    text = "${count}B",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) GoldLight else TextMuted,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 3. Live HUD Inspector Bar
                if (activeCandle != null) {
                    val isBull = activeCandle.close >= activeCandle.open
                    val candleDiff = activeCandle.close - activeCandle.open
                    val pips = candleDiff * 10.0

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianSurfaceCard,
                        border = BorderStroke(0.8.dp, ObsidianBorderHighlight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${activeCandle.datetime} • $selectedTimeframe",
                                    fontSize = 9.5.sp,
                                    color = TextMuted,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${if (pips >= 0) "+" else ""}${String.format(Locale.US, "%.1f", pips)} Pips (${String.format(Locale.US, "%+.2f", (candleDiff / (activeCandle.open.coerceAtLeast(1.0))) * 100)}%)",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (isBull) SignalBuy else SignalSell
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FsHudItem("O", String.format(Locale.US, "%.2f", activeCandle.open))
                                FsHudItem("H", String.format(Locale.US, "%.2f", activeCandle.high))
                                FsHudItem("L", String.format(Locale.US, "%.2f", activeCandle.low))
                                FsHudItem("C", String.format(Locale.US, "%.2f", activeCandle.close), if (isBull) SignalBuy else SignalSell)
                                activeCandle.vwap?.let {
                                    FsHudItem("VWAP", String.format(Locale.US, "%.2f", it), Color(0xFFFF9100))
                                }
                                activeCandle.superTrend?.let {
                                    val stBull = activeCandle.close >= it
                                    FsHudItem("SuperTrend", String.format(Locale.US, "%.2f", it), if (stBull) NeonGreen else SignalSell)
                                }
                                activeCandle.ema9?.let {
                                    FsHudItem("EMA9", String.format(Locale.US, "%.2f", it), GoldLight)
                                }
                                activeCandle.ema21?.let {
                                    FsHudItem("EMA21", String.format(Locale.US, "%.2f", it), Color(0xFF80D8FF))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 4. Ultra High-Definition Candlestick Canvas (Expanded for Fullscreen)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurfaceElevated)
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                        .pointerInput(displayCandles) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val count = displayCandles.size
                                    if (count > 0) {
                                        val slotW = size.width / count
                                        val idx = (offset.x / slotW).toInt().coerceIn(0, count - 1)
                                        selectedCandleIndex = idx
                                        crosshairTouchY = offset.y
                                    }
                                },
                                onDrag = { change, _ ->
                                    val count = displayCandles.size
                                    if (count > 0) {
                                        val slotW = size.width / count
                                        val idx = (change.position.x / slotW).toInt().coerceIn(0, count - 1)
                                        selectedCandleIndex = idx
                                        crosshairTouchY = change.position.y
                                    }
                                },
                                onDragEnd = {
                                    // keep selection visible for inspection
                                }
                            )
                        }
                        .pointerInput(displayCandles) {
                            detectTapGestures { offset ->
                                val count = displayCandles.size
                                if (count > 0) {
                                    val slotW = size.width / count
                                    val idx = (offset.x / slotW).toInt().coerceIn(0, count - 1)
                                    selectedCandleIndex = if (selectedCandleIndex == idx) null else idx
                                    crosshairTouchY = if (selectedCandleIndex != null) offset.y else -1f
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val count = displayCandles.size
                        if (count < 2) return@Canvas

                        val slotW = w / count
                        val candleW = (slotW * 0.65f).coerceIn(2.5f, 18f)

                        val priceToY: (Double) -> Float = { p ->
                            val norm = ((maxPrice - p) / priceSpan).toFloat()
                            norm * (h * 0.78f) + 12f
                        }

                        // 1. Grid Lines (Horizontal Price Grid)
                        val gridSteps = 5
                        for (i in 0..gridSteps) {
                            val p = minPrice + (priceSpan * i / gridSteps)
                            val y = priceToY(p)
                            drawLine(
                                color = ObsidianBorder.copy(alpha = 0.5f),
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 0.8f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
                            )
                        }

                        // 2. SMC Liquidity Order Blocks (High & Low Wick Sweep Areas)
                        if (showSmcZones) {
                            val highestBar = displayCandles.maxByOrNull { it.high }
                            val lowestBar = displayCandles.minByOrNull { it.low }

                            highestBar?.let { hb ->
                                val yTop = priceToY(hb.high)
                                val yBottom = priceToY(hb.close.coerceAtLeast(hb.open))
                                drawRect(
                                    color = SignalSell.copy(alpha = 0.12f),
                                    topLeft = Offset(0f, yTop),
                                    size = Size(w, max(yBottom - yTop, 6f))
                                )
                            }

                            lowestBar?.let { lb ->
                                val yTop = priceToY(lb.close.coerceAtMost(lb.open))
                                val yBottom = priceToY(lb.low)
                                drawRect(
                                    color = SignalBuy.copy(alpha = 0.12f),
                                    topLeft = Offset(0f, yTop),
                                    size = Size(w, max(yBottom - yTop, 6f))
                                )
                            }
                        }

                        // 3. Trade Setup Overlay (TP1, TP2, SL Horizontal Lines)
                        if (showTradeLevels && tradeSetup != null) {
                            // TP1 Line (Green)
                            if (tradeSetup.takeProfit1 in minPrice..maxPrice) {
                                val tp1Y = priceToY(tradeSetup.takeProfit1)
                                drawLine(
                                    color = SignalBuy,
                                    start = Offset(0f, tp1Y),
                                    end = Offset(w, tp1Y),
                                    strokeWidth = 1.2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                                )
                            }

                            // TP2 Line (Neon Green)
                            if (tradeSetup.takeProfit2 in minPrice..maxPrice) {
                                val tp2Y = priceToY(tradeSetup.takeProfit2)
                                drawLine(
                                    color = NeonGreen,
                                    start = Offset(0f, tp2Y),
                                    end = Offset(w, tp2Y),
                                    strokeWidth = 1.2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                                )
                            }

                            // Stop Loss Line (Red)
                            if (tradeSetup.stopLoss in minPrice..maxPrice) {
                                val slY = priceToY(tradeSetup.stopLoss)
                                drawLine(
                                    color = SignalSell,
                                    start = Offset(0f, slY),
                                    end = Offset(w, slY),
                                    strokeWidth = 1.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                                )
                            }
                        }

                        // 4. Bollinger Bands (Cloud Fill & Bands)
                        if (showBb) {
                            val upperPath = Path()
                            val lowerPath = Path()
                            var firstPoint = true

                            displayCandles.forEachIndexed { i, c ->
                                val u = c.bbUpper ?: return@forEachIndexed
                                val l = c.bbLower ?: return@forEachIndexed
                                val cx = i * slotW + slotW / 2
                                val uy = priceToY(u)
                                val ly = priceToY(l)

                                if (firstPoint) {
                                    upperPath.moveTo(cx, uy)
                                    lowerPath.moveTo(cx, ly)
                                    firstPoint = false
                                } else {
                                    upperPath.lineTo(cx, uy)
                                    lowerPath.lineTo(cx, ly)
                                }
                            }

                            if (!firstPoint) {
                                drawPath(upperPath, color = Color(0xFFB388FF).copy(alpha = 0.7f), style = Stroke(width = 1.2f))
                                drawPath(lowerPath, color = Color(0xFFB388FF).copy(alpha = 0.7f), style = Stroke(width = 1.2f))
                            }
                        }

                        // 5. VWAP Line (Orange)
                        if (showVwap) {
                            val vwapPath = Path()
                            var started = false
                            displayCandles.forEachIndexed { i, c ->
                                c.vwap?.let { v ->
                                    val cx = i * slotW + slotW / 2
                                    val cy = priceToY(v)
                                    if (!started) {
                                        vwapPath.moveTo(cx, cy)
                                        started = true
                                    } else {
                                        vwapPath.lineTo(cx, cy)
                                    }
                                }
                            }
                            if (started) {
                                drawPath(vwapPath, color = Color(0xFFFF9100), style = Stroke(width = 1.8f))
                            }
                        }

                        // 6. EMA 9 (Gold) & EMA 21 (Cyan)
                        if (showEma) {
                            val ema9Path = Path()
                            val ema21Path = Path()
                            var st9 = false
                            var st21 = false

                            displayCandles.forEachIndexed { i, c ->
                                val cx = i * slotW + slotW / 2
                                c.ema9?.let { e9 ->
                                    val y9 = priceToY(e9)
                                    if (!st9) { ema9Path.moveTo(cx, y9); st9 = true } else ema9Path.lineTo(cx, y9)
                                }
                                c.ema21?.let { e21 ->
                                    val y21 = priceToY(e21)
                                    if (!st21) { ema21Path.moveTo(cx, y21); st21 = true } else ema21Path.lineTo(cx, y21)
                                }
                            }
                            if (st9) drawPath(ema9Path, color = GoldLight, style = Stroke(width = 1.8f))
                            if (st21) drawPath(ema21Path, color = Color(0xFF80D8FF), style = Stroke(width = 1.5f))
                        }

                        // 7. SuperTrend Overlay
                        if (showSuperTrend) {
                            for (i in 1 until count) {
                                val stVal = displayCandles[i].superTrend ?: continue
                                val isBull = displayCandles[i].close >= stVal
                                val cColor = if (isBull) NeonGreen else SignalSell

                                val x1 = (i - 1) * slotW + slotW / 2
                                val x2 = i * slotW + slotW / 2
                                val y1 = priceToY(displayCandles[i - 1].superTrend ?: stVal)
                                val y2 = priceToY(stVal)

                                drawLine(
                                    color = cColor,
                                    start = Offset(x1, y1),
                                    end = Offset(x2, y2),
                                    strokeWidth = 2.4f
                                )
                            }
                        }

                        // 8. Volume Sub-Bars (Bottom 20% of canvas)
                        if (showVolume) {
                            val volH = h * 0.18f
                            displayCandles.forEachIndexed { i, c ->
                                val v = (c.volume ?: 1000.0).toFloat()
                                val barH = (v / maxVolume.toFloat()) * volH
                                val cx = i * slotW + (slotW - candleW) / 2
                                val isBull = c.close >= c.open
                                val color = if (isBull) SignalBuy.copy(alpha = 0.35f) else SignalSell.copy(alpha = 0.35f)

                                drawRect(
                                    color = color,
                                    topLeft = Offset(cx, h - barH),
                                    size = Size(candleW, barH)
                                )
                            }
                        }

                        // 9. Candlesticks (Wick + Body)
                        displayCandles.forEachIndexed { index, candle ->
                            val isBull = candle.close >= candle.open
                            val candleColor = if (isBull) SignalBuy else SignalSell

                            val cx = index * slotW + slotW / 2
                            val highY = priceToY(candle.high)
                            val lowY = priceToY(candle.low)
                            val openY = priceToY(candle.open)
                            val closeY = priceToY(candle.close)

                            // Wick
                            drawLine(
                                color = candleColor,
                                start = Offset(cx, highY),
                                end = Offset(cx, lowY),
                                strokeWidth = 1.5f
                            )

                            // Body
                            val bodyTop = min(openY, closeY)
                            val bodyH = max(kotlin.math.abs(closeY - openY), 1.8f)

                            drawRect(
                                color = candleColor,
                                topLeft = Offset(cx - candleW / 2, bodyTop),
                                size = Size(candleW, bodyH)
                            )
                        }

                        // 10. Interactive Crosshair (Vertical & Horizontal Lines + Price Marker)
                        selectedCandleIndex?.let { selIdx ->
                            if (selIdx in displayCandles.indices) {
                                val c = displayCandles[selIdx]
                                val cx = selIdx * slotW + slotW / 2
                                val cy = if (crosshairTouchY in 0f..h) crosshairTouchY else priceToY(c.close)

                                // Vertical Crosshair
                                drawLine(
                                    color = GoldPrimary,
                                    start = Offset(cx, 0f),
                                    end = Offset(cx, h),
                                    strokeWidth = 1.2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                                )

                                // Horizontal Crosshair
                                drawLine(
                                    color = GoldPrimary,
                                    start = Offset(0f, cy),
                                    end = Offset(w, cy),
                                    strokeWidth = 1.2f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                                )

                                // Touch Point Glow Node
                                drawCircle(
                                    color = GoldPrimary,
                                    radius = 4.5f,
                                    center = Offset(cx, priceToY(c.close))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 5. Studio Overlays Toggles Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FsToggleChip("SUPERTREND", showSuperTrend, NeonGreen) { showSuperTrend = !showSuperTrend }
                    FsToggleChip("EMA 9/21", showEma, GoldLight) { showEma = !showEma }
                    FsToggleChip("VWAP", showVwap, Color(0xFFFF9100)) { showVwap = !showVwap }
                    FsToggleChip("BB 2.0", showBb, Color(0xFFB388FF)) { showBb = !showBb }
                    FsToggleChip("SMC ZONES", showSmcZones, Color(0xFFFFD54F)) { showSmcZones = !showSmcZones }
                    FsToggleChip("TRADE LEVELS", showTradeLevels, SignalBuy) { showTradeLevels = !showTradeLevels }
                    FsToggleChip("VOLUME", showVolume, TextSecondary) { showVolume = !showVolume }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 6. Order Flow Bar & Quick Navigation Footer
                buyerSellerRatio?.let { bs ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ObsidianSurfaceCard,
                        border = BorderStroke(0.8.dp, ObsidianBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🟢 BUY VOL: ${bs.buyersPercent}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SignalBuy
                            )
                            Text(
                                text = "⚡ Drag crosshair to inspect exact wicks",
                                fontSize = 9.sp,
                                color = TextMuted
                            )
                            Text(
                                text = "🔴 SELL VOL: ${bs.sellersPercent}%",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SignalSell
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FsHudItem(label: String, value: String, color: Color = TextPrimary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label:",
            fontSize = 9.sp,
            color = TextMuted
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = value,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun FsToggleChip(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isActive) activeColor.copy(alpha = 0.18f) else ObsidianSurfaceElevated,
        border = BorderStroke(1.dp, if (isActive) activeColor else ObsidianBorder),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = if (isActive) FontWeight.Black else FontWeight.Normal,
            color = if (isActive) activeColor else TextMuted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
