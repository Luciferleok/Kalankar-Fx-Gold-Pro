package com.example.livegoldai.data

import com.example.livegoldai.model.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * STOPPED-REPLAY CANDLE CHECK (V23.1, rewritten)
 *
 * Takes the most recent replay trade that hit its stop and measures the REAL candle that touched the stop:
 * its open/high/low/close, wick and body sizes, how far it went past the stop and (only when the feed has
 * real volume) its volume against the 20-candle average.
 *
 * Nothing is invented: no AI "readings", no story about market makers, no claimed recalibration.
 * Returns null when no replay trade was stopped or the candle cannot be found.
 */
object FailedPredictionAutopsyEngine {

    private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)

    fun generateAutopsy(
        audit: TimeframeAccuracyAudit?,
        candles: List<CandleBar>,
        currentPrice: Double,
        atrSafe: Double
    ): FailedPredictionCandleAutopsy? {
        val stopped = audit?.recentSignalAudits?.firstOrNull { it.outcomeStatus == PredictionOutcomeStatus.STOP_LOSS_HIT } ?: return null
        val evalIdx = candles.indexOfFirst { it.datetime == stopped.timestamp }
        if (evalIdx < 0) return null
        val isBuy = stopped.signal == Signal.BUY
        val sl = stopped.stopLossPrice
        val hitIdx = (evalIdx + 1 until min(candles.size, evalIdx + 8)).firstOrNull { i ->
            if (isBuy) candles[i].low <= sl else candles[i].high >= sl
        } ?: return null
        val c = candles[hitIdx]

        val body = abs(c.close - c.open)
        val upper = c.high - max(c.open, c.close)
        val lower = min(c.open, c.close) - c.low
        val range = (c.high - c.low).coerceAtLeast(1e-9)
        val beyond = if (isBuy) sl - c.low else c.high - sl
        val closedBack = if (isBuy) c.close > sl else c.close < sl

        val shape = when {
            isBuy && lower >= 2 * body && lower >= 0.5 * range -> "Long lower wick"
            !isBuy && upper >= 2 * body && upper >= 0.5 * range -> "Long upper wick"
            body >= 0.6 * range -> "Large body candle"
            else -> "Mixed candle"
        }
        val shapeHi = when (shape) {
            "Long lower wick" -> "लंबी निचली विक"
            "Long upper wick" -> "लंबी ऊपरी विक"
            "Large body candle" -> "बड़ी बॉडी वाली कैंडल"
            else -> "मिली-जुली कैंडल"
        }
        val shapeMr = when (shape) {
            "Long lower wick" -> "लांब खालची विक"
            "Long upper wick" -> "लांब वरची विक"
            "Large body candle" -> "मोठ्या बॉडीची कँडल"
            else -> "मिश्र कँडल"
        }

        // volume only when the feed carries real volume (several different values), never the flat filler
        val vols = candles.subList(max(0, hitIdx - 20), hitIdx).mapNotNull { it.volume }
        val realVolume = candles.mapNotNull { it.volume }.distinct().size >= 3
        val volRatio = if (realVolume && vols.isNotEmpty() && vols.average() > 0 && c.volume != null) c.volume / vols.average() else 0.0

        val closeEn = if (closedBack) "then closed back on the other side of the stop" else "and closed beyond the stop"
        val closeHi = if (closedBack) "फिर Stop के दूसरी तरफ वापस बंद हुई" else "और Stop के पार ही बंद हुई"
        val closeMr = if (closedBack) "नंतर Stop च्या दुसऱ्या बाजूला परत बंद झाली" else "आणि Stop च्या पलीकडेच बंद झाली"
        val volEn = if (volRatio > 0) " Volume was ${String.format(Locale.US, "%.1f", volRatio)}x the 20-candle average." else ""

        val diagEn = "The ${if (isBuy) "BUY" else "SELL"} replay stop ($${f2(sl)}) was touched by the candle of ${c.datetime}: " +
            "it went $${f2(beyond)} past the stop $closeEn.$volEn"
        val diagHi = "${if (isBuy) "BUY" else "SELL"} रीप्ले का Stop ($${f2(sl)}) ${c.datetime} की कैंडल ने छुआ: वह Stop से $${f2(beyond)} आगे गई $closeHi।"
        val diagMr = "${if (isBuy) "BUY" else "SELL"} रीप्लेचा Stop ($${f2(sl)}) ${c.datetime} च्या कँडलने गाठला: ती Stop पासून $${f2(beyond)} पुढे गेली $closeMr."

        val actEn = "No rule is changed from one stopped trade. Rules change only after the Learning Center's shadow test on 50+ real results."
        val actHi = "एक Stop से कोई नियम नहीं बदला जाता। नियम तभी बदलते हैं जब लर्निंग सेंटर 50+ असली नतीजों पर शैडो टेस्ट पास करे।"
        val actMr = "एका Stop मुळे कोणताही नियम बदलत नाही. लर्निंग सेंटरने 50+ खऱ्या निकालांवर शॅडो टेस्ट पास केल्यावरच नियम बदलतात."

        return FailedPredictionCandleAutopsy(
            previousTradeId = stopped.id,
            failedSignal = stopped.signal,
            entryPrice = stopped.entryPrice,
            stopLossPrice = sl,
            targetPrice = stopped.target1Price,
            pipsLoss = abs(stopped.entryPrice - sl) * 10.0,
            timeAgo = stopped.timeAgo,
            trapCandleType = "$shape • $shapeHi • $shapeMr",
            candleOpen = c.open,
            candleHigh = c.high,
            candleLow = c.low,
            candleClose = c.close,
            upperWickPips = upper * 10.0,
            lowerWickPips = lower * 10.0,
            bodyPips = body * 10.0,
            volumeSurgeMultiplier = volRatio,
            trapDiagnosisEnglish = diagEn,
            trapDiagnosisHindi = diagHi,
            trapDiagnosisMarathi = diagMr,
            geminiCandleReading = "",
            chatGptCandleReading = "",
            claudeCandleReading = "",
            deepSeekCandleReading = "",
            recalibrationActionTakenEnglish = actEn,
            recalibrationActionTakenHindi = actHi,
            recalibrationActionTakenMarathi = actMr
        )
    }
}
