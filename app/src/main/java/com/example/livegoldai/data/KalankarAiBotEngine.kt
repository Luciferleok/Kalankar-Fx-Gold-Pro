package com.example.livegoldai.data

import com.example.livegoldai.model.*
import java.util.Locale
import kotlin.math.abs

/**
 * Rule bots. These are simple, transparent rules computed on the device — not AI and not
 * back-tested strategies. Their win rates are filled in later by RealityEngine from the
 * real Prediction Ledger (or shown as "untested" until enough predictions are checked).
 */
object KalankarAiBotEngine {

    /** Inputs needed for the six independent rule bots. */
    data class BotInputs(
        val close: Double,
        val ema9: Double,
        val ema21: Double,
        val ema50: Double,
        val rsi14: Double,
        val macd: Double,
        val macdSignal: Double,
        val superTrendSignal: Signal,
        val bbUpper: Double,
        val bbLower: Double,
        val isLowSweep: Boolean,
        val isHighSweep: Boolean
    )

    private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)

    fun generateBotSignal(
        currentPrice: Double,
        tradeSetup: TradeSetup,
        overallSignal: Signal,
        agreementPercent: Double,
        smartMoney: SmartMoneyAnalysis?,
        multiAiConsensus: MultiAiConsensusReport?,
        atrSafe: Double,
        isNewsActive: Boolean = false
    ): QuantBotTradeSignal {
        val entry = tradeSetup.entryPrice
        val sl = tradeSetup.stopLoss
        val tp1 = tradeSetup.takeProfit1
        val tp2 = tradeSetup.takeProfit2
        val pipsRisk = abs(entry - sl) * 10.0
        val pipsTp1 = abs(tp1 - entry) * 10.0
        val pipsTp2 = abs(tp2 - entry) * 10.0
        val agr = agreementPercent.toInt()

        val active = !isNewsActive && overallSignal != Signal.WAIT && agreementPercent >= 70.0
        val sig = if (active) overallSignal else Signal.WAIT
        val side = sig.name

        val reasonEn = when {
            isNewsActive -> "High-impact news window: the bot rule is to place no order until the window ends."
            active -> "Main signal is $side with $agr% weighted pillar agreement. The bot rule fires at 70% or more."
            else -> "Main signal is ${overallSignal.name} with $agr% agreement. The bot rule needs a BUY/SELL with 70% or more, so it waits."
        }
        val reasonHi = when {
            isNewsActive -> "हाई-इम्पैक्ट न्यूज़ विंडो: नियम के अनुसार विंडो खत्म होने तक कोई ऑर्डर नहीं।"
            active -> "मुख्य सिग्नल $side है, पिलर सहमति $agr%। बॉट का नियम 70% या उससे ज़्यादा पर चलता है।"
            else -> "मुख्य सिग्नल ${overallSignal.name}, सहमति $agr%। बॉट को 70%+ वाला BUY/SELL चाहिए, इसलिए इंतज़ार।"
        }
        val reasonMr = when {
            isNewsActive -> "हाय-इम्पॅक्ट न्यूज विंडो: नियमानुसार विंडो संपेपर्यंत कोणताही ऑर्डर नाही."
            active -> "मुख्य सिग्नल $side आहे, पिलर सहमती $agr%. बॉटचा नियम 70% किंवा जास्त असल्यावर चालतो."
            else -> "मुख्य सिग्नल ${overallSignal.name}, सहमती $agr%. बॉटला 70%+ BUY/SELL हवा, म्हणून थांबा."
        }

        return QuantBotTradeSignal(
            botName = "KALANKAR RULE BOT",
            statusText = when {
                isNewsActive -> "🚨 NEWS WINDOW: NO ORDER"
                active -> "⚡ RULE CONDITIONS MET ($side)"
                else -> "🛡️ STANDBY: RULE CONDITIONS NOT MET"
            },
            isTradeActive = active,
            signal = sig,
            orderType = if (active) "$side near ${f2(entry)}" else "STANDBY",
            entryPrice = if (active) entry else currentPrice,
            stopLoss = sl,
            takeProfit1 = tp1,
            takeProfit2 = tp2,
            pipsRisk = pipsRisk,
            pipsRewardTp1 = pipsTp1,
            pipsRewardTp2 = pipsTp2,
            recommendedLot = if (active) 0.01 else 0.0,
            winProbabilityPercent = agr, // replaced by calibrated ledger value when available
            strategyName = "Follows main signal when weighted agreement >= 70% (rule, not AI)",
            executionCommandEng = if (active) "$side XAUUSD @ ${f2(entry)} SL ${f2(sl)} TP1 ${f2(tp1)} TP2 ${f2(tp2)}" else "NO ORDER",
            botReasoningEnglish = reasonEn,
            botReasoningHindi = reasonHi,
            botReasoningMarathi = reasonMr,
            autoBreakevenRuleEng = "Suggestion only: after TP1, consider moving SL to entry. The app does not place trades.",
            autoBreakevenRuleHindi = "सिर्फ सुझाव: TP1 के बाद SL को एंट्री पर ले जाने पर विचार करें। ऐप खुद ट्रेड नहीं करता।",
            autoBreakevenRuleMarathi = "फक्त सूचना: TP1 नंतर SL एंट्रीवर नेण्याचा विचार करा. ॲप स्वतः ट्रेड करत नाही.",
            newsLockActive = isNewsActive
        )
    }

    /**
     * Six independent single-rule bots. Each uses a different, real calculation, so their
     * votes are not copies of the main signal.
     */
    fun generateMultiBotEnsemble(
        currentPrice: Double,
        tradeSetup: TradeSetup,
        inputs: BotInputs,
        isNewsActive: Boolean = false
    ): MultiBotEnsemble {
        val entry = tradeSetup.entryPrice
        val sl = tradeSetup.stopLoss
        val tp1 = tradeSetup.takeProfit1
        val tp2 = tradeSetup.takeProfit2
        val slPips = abs(entry - sl) * 10.0
        val tpPips = abs(tp1 - entry) * 10.0
        val i = inputs

        fun bot(id: String, name: String, hindi: String, emoji: String, type: String, sig: Signal,
                en: String, hi: String, mr: String): IndividualBot = IndividualBot(
            id = id, name = name, hindiName = hindi, iconEmoji = emoji, botType = type,
            backtestedWinRate = -1.0, profitFactor = -1.0, // filled from the ledger by RealityEngine
            signal = if (isNewsActive) Signal.WAIT else sig,
            confidence = 0,
            keyTriggerSummary = en, keyTriggerSummaryHindi = hi, keyTriggerSummaryMarathi = mr,
            suggestedOrder = when (if (isNewsActive) Signal.WAIT else sig) {
                Signal.BUY -> "BUY idea (SL ${f2(sl)})"
                Signal.SELL -> "SELL idea (SL ${f2(sl)})"
                Signal.WAIT -> "NO SIGNAL"
            },
            stopLossPips = slPips,
            targetPips = tpPips,
            status = if (isNewsActive) "NEWS PAUSE 🛡️" else if (sig == Signal.WAIT) "NO SIGNAL 🟡" else "SIGNAL 🟢"
        )

        val trendSig = when {
            i.ema9 > i.ema21 && i.close > i.ema50 -> Signal.BUY
            i.ema9 < i.ema21 && i.close < i.ema50 -> Signal.SELL
            else -> Signal.WAIT
        }
        val macdSig = when {
            i.macd > i.macdSignal && i.macd > 0 -> Signal.BUY
            i.macd < i.macdSignal && i.macd < 0 -> Signal.SELL
            else -> Signal.WAIT
        }
        val rsiSig = when {
            i.rsi14 <= 30 -> Signal.BUY
            i.rsi14 >= 70 -> Signal.SELL
            else -> Signal.WAIT
        }
        val bbSig = when {
            i.close > i.bbUpper -> Signal.BUY
            i.close < i.bbLower -> Signal.SELL
            else -> Signal.WAIT
        }
        val sweepSig = when {
            i.isLowSweep -> Signal.BUY
            i.isHighSweep -> Signal.SELL
            else -> Signal.WAIT
        }

        val bots = listOf(
            bot("bot_trend_ema", "Trend Rule: EMA9/21 + EMA50", "ट्रेंड नियम: EMA9/21 + EMA50", "🌊", "Trend following", trendSig,
                "BUY if EMA9 > EMA21 and close > EMA50; SELL if the opposite. EMA9 ${f2(i.ema9)}, EMA21 ${f2(i.ema21)}, EMA50 ${f2(i.ema50)}.",
                "BUY अगर EMA9 > EMA21 और क्लोज़ > EMA50; उल्टा हो तो SELL। EMA9 ${f2(i.ema9)}, EMA21 ${f2(i.ema21)}, EMA50 ${f2(i.ema50)}।",
                "BUY जर EMA9 > EMA21 आणि क्लोज > EMA50; उलट असल्यास SELL. EMA9 ${f2(i.ema9)}, EMA21 ${f2(i.ema21)}, EMA50 ${f2(i.ema50)}."),
            bot("bot_supertrend", "SuperTrend (10, 3) Rule", "सुपरट्रेंड (10, 3) नियम", "📈", "Trend following", i.superTrendSignal,
                "Follows SuperTrend direction: ${i.superTrendSignal.name}.",
                "सुपरट्रेंड की दिशा: ${i.superTrendSignal.name}।",
                "सुपरट्रेंडची दिशा: ${i.superTrendSignal.name}."),
            bot("bot_macd", "MACD Rule", "MACD नियम", "📊", "Momentum", macdSig,
                "BUY if MACD > signal and > 0; SELL if MACD < signal and < 0. MACD ${f2(i.macd)}, signal ${f2(i.macdSignal)}.",
                "BUY अगर MACD > सिग्नल और > 0; SELL अगर MACD < सिग्नल और < 0। MACD ${f2(i.macd)}, सिग्नल ${f2(i.macdSignal)}।",
                "BUY जर MACD > सिग्नल आणि > 0; SELL जर MACD < सिग्नल आणि < 0. MACD ${f2(i.macd)}, सिग्नल ${f2(i.macdSignal)}."),
            bot("bot_rsi_reversion", "RSI Mean-Reversion Rule", "RSI मीन-रिवर्ज़न नियम", "⚖️", "Mean reversion", rsiSig,
                "BUY if RSI <= 30, SELL if RSI >= 70. RSI now ${String.format(Locale.US, "%.1f", i.rsi14)}.",
                "BUY अगर RSI <= 30, SELL अगर RSI >= 70। अभी RSI ${String.format(Locale.US, "%.1f", i.rsi14)}।",
                "BUY जर RSI <= 30, SELL जर RSI >= 70. सध्या RSI ${String.format(Locale.US, "%.1f", i.rsi14)}."),
            bot("bot_bb_breakout", "Bollinger Breakout Rule", "बोलिंजर ब्रेकआउट नियम", "⚡", "Volatility breakout", bbSig,
                "BUY if close > upper band (${f2(i.bbUpper)}), SELL if close < lower band (${f2(i.bbLower)}).",
                "BUY अगर क्लोज़ अपर बैंड (${f2(i.bbUpper)}) से ऊपर, SELL अगर लोअर बैंड (${f2(i.bbLower)}) से नीचे।",
                "BUY जर क्लोज अपर बँड (${f2(i.bbUpper)}) वर, SELL जर लोअर बँड (${f2(i.bbLower)}) खाली."),
            bot("bot_liquidity_sweep", "Liquidity Sweep Rule", "लिक्विडिटी स्वीप नियम", "🎯", "Price action", sweepSig,
                "BUY if the last candle broke the 5-bar low and closed back above it; SELL if the mirror happened at the high.",
                "BUY अगर आखिरी कैंडल 5-बार लो तोड़कर वापस ऊपर बंद हुई; हाई पर उल्टा हो तो SELL।",
                "BUY जर शेवटची कँडल 5-बार लो तोडून परत वर बंद झाली; हायवर उलट झाल्यास SELL.")
        )

        val buy = bots.count { it.signal == Signal.BUY }
        val sell = bots.count { it.signal == Signal.SELL }
        val wait = bots.count { it.signal == Signal.WAIT }
        val ens = when {
            buy >= 4 && sell == 0 -> Signal.BUY
            sell >= 4 && buy == 0 -> Signal.SELL
            else -> Signal.WAIT
        }
        val pct = when (ens) {
            Signal.BUY -> buy * 100 / bots.size
            Signal.SELL -> sell * 100 / bots.size
            Signal.WAIT -> wait * 100 / bots.size
        }
        val strong = ens != Signal.WAIT && (buy >= 5 || sell >= 5)

        return MultiBotEnsemble(
            ensembleSignal = ens,
            consensusPercent = pct,
            buyVotes = buy,
            sellVotes = sell,
            waitVotes = wait,
            totalBots = bots.size,
            isEnsembleConsensusStrong = strong,
            consensusLevel = "$buy BUY • $sell SELL • $wait NO SIGNAL (of ${bots.size})",
            allBots = bots,
            ensembleRationaleEnglish = "Six separate rules vote. Ensemble says BUY/SELL only if at least 4 agree and none oppose. Now: $buy BUY, $sell SELL, $wait no signal.",
            ensembleRationaleHindi = "छह अलग नियम वोट करते हैं। कम से कम 4 सहमत हों और कोई विरोध न हो तभी BUY/SELL। अभी: $buy BUY, $sell SELL, $wait कोई सिग्नल नहीं।",
            ensembleRationaleMarathi = "सहा वेगळे नियम मत देतात. किमान 4 सहमत आणि कोणीही विरोधात नसेल तरच BUY/SELL. सध्या: $buy BUY, $sell SELL, $wait सिग्नल नाही.",
            executionTacticHindi = if (ens == Signal.WAIT) "नियम सहमत नहीं हैं, इसलिए कोई ट्रेड आइडिया नहीं।" else "${ens.name} आइडिया, SL ${f2(sl)}। यह सिर्फ सुझाव है।",
            executionTacticEnglish = if (ens == Signal.WAIT) "The rules disagree, so there is no trade idea." else "${ens.name} idea with SL ${f2(sl)}. Suggestion only.",
            executionTacticMarathi = if (ens == Signal.WAIT) "नियम सहमत नाहीत, म्हणून ट्रेड आयडिया नाही." else "${ens.name} आयडिया, SL ${f2(sl)}. फक्त सूचना.",
            recommendedEntry = entry,
            recommendedSl = sl,
            recommendedTp1 = tp1,
            recommendedTp2 = tp2
        )
    }

    /** Placeholder; RealityEngine replaces it with numbers from the Prediction Ledger. */
    fun generateProductionImprovementEngine(
        currentPrice: Double,
        agreementPercent: Double,
        isBullish: Boolean
    ): ProductionImprovementEngine = ProductionImprovementEngine(
        engineVersion = "Base rules v1",
        statusBadge = "OBSERVING",
        verifiedAccuracyPercent = -1.0,
        totalBacktestedTrades = 0,
        profitFactor = -1.0,
        averagePipGainPerTrade = 0.0,
        dynamicConfidenceThreshold = 68,
        appliedProductionFixes = emptyList(),
        liveModelWeights = emptyList(),
        isAutoTuningActive = true
    )
}
