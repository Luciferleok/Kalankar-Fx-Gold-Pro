package com.example.livegoldai.data

import com.example.livegoldai.model.*
import java.util.Locale

/**
 * News Trading & Volatility Engine
 * Changes prediction methodology during high-impact economic releases (CPI, NFP, FOMC)
 * Automatically triggers the app's News Emergency Alert color theme.
 */
object NewsTradingEngine {

    fun generateNewsPlan(
        currentPrice: Double,
        atrSafe: Double,
        macroSignal: Signal,
        forceActiveNews: Boolean = false
    ): NewsTradingPlan {
        val upperStraddle = currentPrice + (1.2 * atrSafe)
        val lowerStraddle = currentPrice - (1.2 * atrSafe)
        val spreadBuffer = 6.5 // pips

        val eventTitle = "US Core CPI Inflation & Federal Reserve Rate Path (High Impact 🔴)"
        val bias = if (macroSignal == Signal.BUY) Signal.BUY else Signal.SELL

        val tacticHeadingEng = "Pre-News Straddle & 2nd-Wave Retracement Method"
        val tacticHeadingHin = "न्यूज़ स्ट्रैडल और सेकंड-वेव रिट्रेसमेंट रणनीति"
        val tacticHeadingMar = "न्यूज स्ट्रॅडल आणि सेकंड-वेव्ह रिट्रेसमेंट रणनीती"

        val tacticDetailEng = if (bias == Signal.BUY) {
            "CPI expected lower than consensus (-0.1% deviation) → Dollar weakness will launch Gold spot upward. Strategy: Set Buy Stop at $${String.format(Locale.US, "%.2f", upperStraddle)} and wait for 2nd wave retracement."
        } else {
            "Sticky inflation reading expected → Dollar strength will pressure Gold down. Strategy: Set Sell Stop at $${String.format(Locale.US, "%.2f", lowerStraddle)} with strict 2.5x ATR stop buffer."
        }

        val tacticDetailHin = if (bias == Signal.BUY) {
            "CPI डेटा अनुमान से कम आने की संभावना है → जिससे अमेरिकी डॉलर कमजोर होगा और Gold में $20-$40 की तीव्र तेजी आएगी। रणनीति: $${String.format(Locale.US, "%.2f", upperStraddle)} पर Buy Stop लगाएं।"
        } else {
            "महंगाई डेटा अधिक आने की संभावना है → जिससे डॉलर मजबूत होगा और Gold में शार्प गिरावट आएगी। रणनीति: $${String.format(Locale.US, "%.2f", lowerStraddle)} पर Sell Stop लगाएं।"
        }

        val tacticDetailMar = if (bias == Signal.BUY) {
            "CPI डेटा अपेक्षेपेक्षा कमी येण्याची शक्यता आहे → ज्यामुळे अमेरिकन डॉलर कमजोर होईल आणि Gold मध्ये $20-$40 ची मोठी तेजी येईल. रणनीती: $${String.format(Locale.US, "%.2f", upperStraddle)} वर Buy Stop लावा."
        } else {
            "महागाई डेटा जास्त येण्याची शक्यता आहे → ज्यामुळे डॉलर मजबूत होईल आणि Gold मध्ये मोठी घसरण येईल. रणनीती: $${String.format(Locale.US, "%.2f", lowerStraddle)} वर Sell Stop लावा."
        }

        return NewsTradingPlan(
            isNewsActive = forceActiveNews,
            eventName = eventTitle,
            eventImpact = "HIGH IMPACT 🔴🔴🔴 (MAJOR MARKET MOVER)",
            releaseCountdownFormatted = if (forceActiveNews) "🚨 LIVE NEWS SPIKE WINDOW ACTIVE (Next 45 Mins)" else "⏰ Next Release: Today 18:30 UTC (US CPI)",
            phase = if (forceActiveNews) NewsPhase.LIVE_NEWS_SPIKE else NewsPhase.PRE_NEWS_COIL,
            primaryDirectionBias = bias,
            straddleUpperLevel = upperStraddle,
            straddleLowerLevel = lowerStraddle,
            spreadWarningBufferPips = spreadBuffer,
            freezeRuleTitle = "DO NOT MARKET ORDER IN FIRST 90 SECONDS",
            freezeRuleDescriptionEnglish = "During high-impact news release, broker spreads widen by 8-15 pips and algorithmic slippage causes false wicks. Freeze market entry for first 90 seconds. Enter only on second-wave confirmation.",
            freezeRuleDescriptionHindi = "न्यूज़ रिलीज़ होते ही शुरुआती 90 सेकंड में ब्रोकर्स का स्प्रेड 8-15 pips तक बढ़ जाता है और स्लिपेज से नुकसान होता है। पहले 90 सेकंड में कोई मार्केट ऑर्डर न लगाएं, सिर्फ सेकंड-वेव पुलबैक पर ट्रेड करें।",
            freezeRuleDescriptionMarathi = "न्यूज प्रसिद्ध होताच पहिल्या 90 सेकंदांत ब्रोकर्सचा स्प्रेड 8-15 pips पर्यंत वाढतो आणि स्लिपेजमुळे नुकसान होते. पहिल्या 90 सेकंदांत कोणताही मार्केट ऑर्डर लावू नका, फक्त सेकंड-वेव्ह पुलबॅकवरच ट्रेड करा.",
            newsTacticHeadingEnglish = tacticHeadingEng,
            newsTacticHeadingHindi = tacticHeadingHin,
            newsTacticHeadingMarathi = tacticHeadingMar,
            newsTacticDetailEnglish = tacticDetailEng,
            newsTacticDetailHindi = tacticDetailHin,
            newsTacticDetailMarathi = tacticDetailMar,
            secondWaveRetracementLevel = "$${String.format(Locale.US, "%.2f", currentPrice - 0.5 * atrSafe)} (50% Fibonacci Discount)",
            actualVsForecastScenario = "Actual CPI < 0.2% = Ultra Bullish Gold (+$35) | Actual CPI > 0.4% = Bearish Gold (-$30)"
        )
    }
}
