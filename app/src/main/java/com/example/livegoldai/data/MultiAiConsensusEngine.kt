package com.example.livegoldai.data

import com.example.livegoldai.model.*
import java.util.Locale

/**
 * RULE-ENGINE COUNCIL
 *
 * Five small decision rules that run on this phone. No external AI model is called.
 * (The enum constants keep their old names only so existing screens still compile;
 * their display names say what they really are.)
 * Each engine's real accuracy is attached later by RealityEngine from the Prediction Ledger.
 */
object MultiAiConsensusEngine {

    private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)

    fun generateConsensusReport(
        currentPrice: Double,
        interval: String,
        overallSignal: Signal,
        buyCount: Int,
        sellCount: Int,
        waitCount: Int,
        rsi14: Double,
        superTrendValue: Double,
        vwapValue: Double,
        atrSafe: Double,
        dxySignal: Signal,
        hadRecentStopLoss: Boolean
    ): MultiAiConsensusReport {
        val total = (buyCount + sellCount + waitCount).coerceAtLeast(1)

        // R1: group vote + VWAP side
        val r1 = when {
            buyCount >= 4 && currentPrice >= vwapValue -> Signal.BUY
            sellCount >= 4 && currentPrice <= vwapValue -> Signal.SELL
            else -> Signal.WAIT
        }
        // R2: dollar filter — only agree with the main signal when DXY points the same way for gold
        val r2 = when {
            overallSignal == Signal.BUY && dxySignal == Signal.BUY -> Signal.BUY
            overallSignal == Signal.SELL && dxySignal == Signal.SELL -> Signal.SELL
            else -> Signal.WAIT
        }
        // R3: risk guard — after a real recent loss, only strong (5+/7) setups pass
        val r3 = if (hadRecentStopLoss && buyCount < 5 && sellCount < 5) Signal.WAIT else overallSignal
        // R4: RSI zone
        val r4 = when {
            rsi14 >= 55 -> Signal.BUY
            rsi14 <= 45 -> Signal.SELL
            else -> Signal.WAIT
        }
        // R5: the weighted 68% pillar gate (the app's main signal)
        val r5 = overallSignal

        fun score(s: Signal): Int = when (s) {
            Signal.BUY -> buyCount * 100 / total
            Signal.SELL -> sellCount * 100 / total
            Signal.WAIT -> waitCount * 100 / total
        }

        fun insight(p: AiModelProvider, s: Signal, en: String, hi: String, mr: String): SingleAiPredictionInsight =
            SingleAiPredictionInsight(
                provider = p,
                signal = s,
                confidencePercent = score(s),
                coreThesisEnglish = en,
                coreThesisHindi = hi,
                coreThesisMarathi = mr,
                correctionAppliedEnglish = "Accuracy: not measured yet",
                correctionAppliedHindi = "सटीकता: अभी मापी नहीं गई",
                correctionAppliedMarathi = "अचूकता: अजून मोजली नाही",
                suggestedStopLossPips = 15.0 * atrSafe, // 1.5x ATR in pips
                suggestedTargetPips = 16.0 * atrSafe, // 1.6x ATR in pips
                keyTrapWarned = null
            )

        val models = listOf(
            insight(AiModelProvider.GEMINI, r1,
                "BUY if 4+ of 7 pillars say BUY and price >= VWAP (${f2(vwapValue)}); SELL mirror. Pillars now: $buyCount BUY / $sellCount SELL.",
                "BUY अगर 7 में से 4+ पिलर BUY और भाव VWAP (${f2(vwapValue)}) से ऊपर; SELL उल्टा। अभी: $buyCount BUY / $sellCount SELL।",
                "BUY जर 7 पैकी 4+ पिलर BUY आणि भाव VWAP (${f2(vwapValue)}) वर; SELL उलट. सध्या: $buyCount BUY / $sellCount SELL."),
            insight(AiModelProvider.CHAT_GPT, r2,
                "Agrees with the main signal only when the US Dollar Index points the same way for gold. Dollar effect now: ${dxySignal.name}.",
                "मुख्य सिग्नल से तभी सहमत जब डॉलर इंडेक्स भी गोल्ड के लिए उसी दिशा में हो। अभी डॉलर असर: ${dxySignal.name}।",
                "मुख्य सिग्नलशी तेव्हाच सहमत जेव्हा डॉलर इंडेक्सही सोन्यासाठी त्याच दिशेने असेल. सध्या डॉलर परिणाम: ${dxySignal.name}."),
            insight(AiModelProvider.CLAUDE, r3,
                if (hadRecentStopLoss) "Last checked prediction on this timeframe was wrong, so only 5+/7 pillar setups pass."
                else "No recent loss on this timeframe, so it follows the main signal.",
                if (hadRecentStopLoss) "इस टाइमफ्रेम की पिछली जाँची गई प्रेडिक्शन गलत थी, इसलिए सिर्फ 5+/7 पिलर वाले सेटअप पास।"
                else "इस टाइमफ्रेम पर हाल में कोई गलत प्रेडिक्शन नहीं, इसलिए मुख्य सिग्नल फॉलो।",
                if (hadRecentStopLoss) "या टाइमफ्रेमचा मागील तपासलेला अंदाज चुकीचा होता, म्हणून फक्त 5+/7 पिलर सेटअप पास."
                else "या टाइमफ्रेमवर अलीकडे चूक नाही, म्हणून मुख्य सिग्नल फॉलो."),
            insight(AiModelProvider.DEEP_SEEK, r4,
                "BUY if RSI >= 55, SELL if RSI <= 45, else WAIT. RSI now ${String.format(Locale.US, "%.1f", rsi14)}.",
                "BUY अगर RSI >= 55, SELL अगर RSI <= 45, वरना WAIT। अभी RSI ${String.format(Locale.US, "%.1f", rsi14)}।",
                "BUY जर RSI >= 55, SELL जर RSI <= 45, नाहीतर WAIT. सध्या RSI ${String.format(Locale.US, "%.1f", rsi14)}."),
            insight(AiModelProvider.PERPLEXITY, r5,
                "The app's main signal: weighted pillar agreement must reach 68% for BUY/SELL.",
                "ऐप का मुख्य सिग्नल: BUY/SELL के लिए वेटेड पिलर सहमति 68% होनी चाहिए।",
                "ॲपचा मुख्य सिग्नल: BUY/SELL साठी वेटेड पिलर सहमती 68% हवी.")
        )

        val buyVotes = models.count { it.signal == Signal.BUY }
        val sellVotes = models.count { it.signal == Signal.SELL }
        val waitVotes = models.count { it.signal == Signal.WAIT }
        val (consensus, agreeing) = when {
            buyVotes >= 3 -> Signal.BUY to buyVotes
            sellVotes >= 3 -> Signal.SELL to sellVotes
            else -> Signal.WAIT to maxOf(waitVotes, buyVotes, sellVotes)
        }
        val agreementPercent = agreeing * 100 / models.size

        val tp = if (consensus == Signal.SELL) currentPrice - 1.6 * atrSafe else currentPrice + 1.6 * atrSafe
        val sl = if (consensus == Signal.SELL) currentPrice + 1.5 * atrSafe else currentPrice - 1.5 * atrSafe

        return MultiAiConsensusReport(
            unanimousAgreementPercent = agreementPercent,
            consensusSignal = consensus,
            consensusConfidence = agreementPercent,
            agreeingModelsCount = agreeing,
            totalModelsCount = models.size,
            consensusSummaryEnglish = "$agreeing of ${models.size} local rule engines say ${consensus.name} ($buyVotes BUY, $sellVotes SELL, $waitVotes WAIT). These are rules on your phone, not AI models.",
            consensusSummaryHindi = "${models.size} में से $agreeing लोकल नियम ${consensus.name} कहते हैं ($buyVotes BUY, $sellVotes SELL, $waitVotes WAIT)। ये फोन पर चलने वाले नियम हैं, AI मॉडल नहीं।",
            consensusSummaryMarathi = "${models.size} पैकी $agreeing लोकल नियम ${consensus.name} सांगतात ($buyVotes BUY, $sellVotes SELL, $waitVotes WAIT). हे फोनवरील नियम आहेत, AI मॉडेल नाहीत.",
            modelInsights = models,
            jointAiCorrections = listOf("No automatic correction is applied by these rules. Learned corrections appear in the Learning Center only after they pass the shadow test."),
            jointAiCorrectionsHindi = listOf("ये नियम खुद कोई सुधार लागू नहीं करते। सीखे गए सुधार शैडो टेस्ट पास करने के बाद ही लर्निंग सेंटर में दिखते हैं।"),
            jointAiCorrectionsMarathi = listOf("हे नियम स्वतः कोणतीही सुधारणा लागू करत नाहीत. शिकलेल्या सुधारणा शॅडो टेस्ट पास झाल्यावरच लर्निंग सेंटरमध्ये दिसतात."),
            calibratedEntryRecommendation = if (consensus == Signal.WAIT) "No entry" else "Near $${f2(currentPrice)}",
            calibratedSlRecommendation = if (consensus == Signal.WAIT) "--" else "$${f2(sl)} (1.5x ATR)",
            calibratedTpRecommendation = if (consensus == Signal.WAIT) "--" else "$${f2(tp)} (1.6x ATR)"
        )
    }
}
