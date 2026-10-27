package com.example.livegoldai.data

import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import com.example.livegoldai.model.TradeSetup
import java.util.Locale
import kotlinx.serialization.Serializable

/**
 * V29E: one statistic with its own definition. Different metrics measure different populations and are NEVER
 * merged or forced to agree. A metric below its minimum sample shows INSUFFICIENT EVIDENCE, never a number.
 */
@Serializable
data class MetricStat(
    val id: String,              // SIMILAR_SETUP / TF_DIRECTIONAL / ALL_CLOSE_DIRECTION
    val pct: Int,                // -1 = not available
    val n: Int,
    val minN: Int,
    val timeframe: String,       // "5M" or "ALL"
    val horizonMin: Int,         // evaluation horizon of the calls in the population (0 = mixed timeframes)
    val fromMs: Long,
    val toMs: Long,
    val version: String          // computation version (engine)
) {
    val sufficient: Boolean get() = pct >= 0 && n >= minN
}

object Metrics {
    const val SIMILAR_SETUP = "SIMILAR_SETUP"
    const val TF_DIRECTIONAL = "TF_DIRECTIONAL"
    const val ALL_CLOSE_DIRECTION = "ALL_CLOSE_DIRECTION"

    /** Built once per snapshot from the existing ledger statistics (no new calculation of accuracy). */
    fun build(a: GoldAnalysisResult, state: LedgerState?): List<MetricStat> {
        val tf = a.interval.uppercase(Locale.US)
        val mine = state?.records?.filter { it.interval.equals(a.interval, true) } ?: emptyList()
        val all = state?.records ?: emptyList()
        val band = RealityEngine.bandRecord(a.learning, kotlin.math.round(a.agreementPercent).toInt())
        val audit = a.timeframeAudit
        val h = TechnicalEngine.calculateValidityMinutes(a.interval)
        return listOf(
            // calls of this timeframe whose pillar agreement fell in the same band (sideways excluded)
            MetricStat(SIMILAR_SETUP, band?.accuracyPercent?.toInt() ?: -1, band?.decided ?: 0, 20, tf, h,
                mine.firstOrNull()?.createdAt ?: 0L, mine.lastOrNull()?.createdAt ?: 0L, PredictionLedger.ENGINE_VERSION),
            // every decided BUY/SELL call of this timeframe: target hit or closed the called way = correct
            MetricStat(TF_DIRECTIONAL, if (audit == null || audit.totalSignalsTested == 0) -1 else audit.winRatePercent, audit?.totalSignalsTested ?: 0, 30, tf, h,
                mine.firstOrNull()?.createdAt ?: 0L, mine.lastOrNull()?.createdAt ?: 0L, PredictionLedger.ENGINE_VERSION),
            // all timeframes: did the close at the horizon move the called way beyond the noise threshold
            MetricStat(ALL_CLOSE_DIRECTION, a.pulse?.directionPct ?: -1, a.pulse?.directionN ?: 0, a.pulse?.directionMinN ?: 30, "ALL", 0,
                all.firstOrNull()?.createdAt ?: 0L, all.lastOrNull()?.createdAt ?: 0L, PredictionLedger.ENGINE_VERSION)
        )
    }

    private fun t(l: AppLanguage, en: String, hi: String, mr: String) = when (l) { AppLanguage.ENGLISH -> en; AppLanguage.HINDI -> hi; AppLanguage.MARATHI -> mr }

    fun label(m: MetricStat, l: AppLanguage): String = when (m.id) {
        SIMILAR_SETUP -> t(l, "Similar-Setup Historical Hit-Rate", "मिलते सेटअप का पिछला हिट-रेट", "सारख्या सेटअपचा मागील हिट-रेट")
        TF_DIRECTIONAL -> t(l, "${m.timeframe} Historical Directional Accuracy", "${m.timeframe} पिछली दिशा सटीकता", "${m.timeframe} मागील दिशा अचूकता")
        else -> t(l, "All-Time Close-Direction Accuracy", "सभी समय की क्लोज़-दिशा सटीकता", "सर्वकालीन क्लोज-दिशा अचूकता")
    }

    private fun population(m: MetricStat, l: AppLanguage): String = when (m.id) {
        SIMILAR_SETUP -> t(l, "Similar setups", "मिलते सेटअप", "सारखे सेटअप")
        TF_DIRECTIONAL -> t(l, "${m.timeframe} decided calls", "${m.timeframe} तय कॉल", "${m.timeframe} ठरलेल्या कॉल")
        else -> t(l, "Close-direction calls", "क्लोज़-दिशा कॉल", "क्लोज-दिशा कॉल")
    }

    /** "59% · N=186 · Similar setups" or "INSUFFICIENT EVIDENCE · N=12/20 · Similar setups". */
    fun short(m: MetricStat, l: AppLanguage): String =
        (if (m.sufficient) "${m.pct}% · N=${m.n}" else t(l, "INSUFFICIENT EVIDENCE", "पर्याप्त सबूत नहीं", "पुरेसा पुरावा नाही") + " · N=${m.n}/${m.minN}") +
            " · " + population(m, l)

    fun definition(m: MetricStat, l: AppLanguage): String = when (m.id) {
        SIMILAR_SETUP -> t(l, "${m.timeframe} calls whose pillar agreement was in the same band; sideways results excluded; horizon ${m.horizonMin} min.",
            "${m.timeframe} कॉल जिनकी पिलर सहमति उसी बैंड में थी; साइडवेज़ नतीजे शामिल नहीं; अवधि ${m.horizonMin} मिनट।",
            "${m.timeframe} कॉल ज्यांची पिलर सहमती त्याच बँडमध्ये होती; साइडवेज निकाल वगळले; कालावधी ${m.horizonMin} मिनिटे.")
        TF_DIRECTIONAL -> t(l, "All decided ${m.timeframe} BUY/SELL calls: target hit or close the called way = correct; stop hit or wrong way = incorrect; horizon ${m.horizonMin} min.",
            "सभी तय ${m.timeframe} BUY/SELL कॉल: टारगेट या सही दिशा में क्लोज़ = सही; स्टॉप या उल्टी दिशा = गलत; अवधि ${m.horizonMin} मिनट।",
            "सर्व ठरलेल्या ${m.timeframe} BUY/SELL कॉल: टार्गेट किंवा योग्य दिशेने क्लोज = बरोबर; स्टॉप किंवा उलट दिशा = चूक; कालावधी ${m.horizonMin} मिनिटे.")
        else -> t(l, "All timeframes: the close at each call's horizon moved the called way beyond the noise threshold; small moves excluded.",
            "सभी टाइमफ़्रेम: हर कॉल की अवधि के अंत में क्लोज़ नॉइज़ सीमा से आगे बताई दिशा में गया; छोटी चालें शामिल नहीं।",
            "सर्व टाइमफ्रेम: प्रत्येक कॉलच्या कालावधीअखेरीस क्लोज नॉइज मर्यादेपलीकडे सांगितलेल्या दिशेने गेला; लहान हालचाली वगळल्या.")
    }

    fun notComparable(l: AppLanguage) = t(l, "These numbers measure different populations and cannot be compared directly. None is the chance of today's forecast.",
        "ये संख्याएँ अलग-अलग समूह मापती हैं, इनकी सीधी तुलना नहीं हो सकती। कोई भी आज के अनुमान की संभावना नहीं।",
        "हे आकडे वेगवेगळे गट मोजतात, त्यांची थेट तुलना करता येत नाही. कोणताही आजच्या अंदाजाची शक्यता नाही.")
}

/**
 * V29E: the single presentation state of a forecast, resolved from the existing gates (plan status from PlanStamp,
 * freshness, Shadow Lab status). No new promotion path: APPROVED_ACTIVE is exactly PlanStamp.ACTIVE.
 * The app has no separate user-approval gate; that is reported, not invented.
 */
object ForecastState {
    const val UNVERIFIED_RESEARCH = "UNVERIFIED_RESEARCH"
    const val QUALIFIED_RESEARCH = "QUALIFIED_RESEARCH"   // research model LIVE QUALIFIED, but another gate (instrument/source) blocks it
    const val APPROVED_ACTIVE = "APPROVED_ACTIVE"
    const val EXPIRED = "EXPIRED"
    const val STALE = "STALE"
    const val MARKET_CLOSED = "MARKET_CLOSED"
    const val OFFLINE = "OFFLINE"
    const val UNDER_REVIEW = "UNDER_REVIEW"
    const val STOPPED = "STOPPED_OUT"                    // recorded stop touched inside the candle
    const val NO_SIGNAL = "NO_SIGNAL"                    // final decision WAIT

    fun resolve(ts: TradeSetup, finalSignal: Signal, researchStatus: String?): String = when (ts.planStatus) {
        PlanStamp.ACTIVE -> APPROVED_ACTIVE
        PlanStamp.EXPIRED -> EXPIRED
        PlanStamp.STOPPED -> STOPPED
        PlanStamp.NO_DATA -> when (ts.freshness) { Freshness.STALE -> STALE; Freshness.CLOSED -> MARKET_CLOSED; else -> OFFLINE }
        PlanStamp.RESEARCH -> when (researchStatus) {
            "LIVE QUALIFIED" -> QUALIFIED_RESEARCH
            "UNDER REVIEW", "DEMOTED" -> UNDER_REVIEW
            else -> UNVERIFIED_RESEARCH
        }
        else -> if (finalSignal == Signal.WAIT) NO_SIGNAL else UNVERIFIED_RESEARCH   // unstamped never reads as active
    }

    fun isActionable(state: String) = state == APPROVED_ACTIVE

    private fun t(l: AppLanguage, en: String, hi: String, mr: String) = when (l) { AppLanguage.ENGLISH -> en; AppLanguage.HINDI -> hi; AppLanguage.MARATHI -> mr }

    /** Title of the validity card. Never contains ACTIVE unless the state is APPROVED_ACTIVE. */
    fun validityTitle(state: String, l: AppLanguage): String = when (state) {
        APPROVED_ACTIVE -> t(l, "ACTIVE FORECAST VALIDITY", "सक्रिय अनुमान की वैधता", "सक्रिय अंदाजाची वैधता")
        EXPIRED -> t(l, "RESEARCH HORIZON ENDED — REVIEW DUE", "रिसर्च अवधि खत्म — जाँच बाकी", "रिसर्च कालावधी संपला — तपासणी बाकी")
        STALE -> t(l, "DATA STALE — FORECAST NOT ACTIONABLE", "डेटा पुराना — अनुमान पर कार्रवाई नहीं", "डेटा जुना — अंदाजावर कृती नाही")
        OFFLINE -> t(l, "DATA OFFLINE — FORECAST NOT ACTIONABLE", "डेटा ऑफ़लाइन — अनुमान पर कार्रवाई नहीं", "डेटा ऑफलाइन — अंदाजावर कृती नाही")
        MARKET_CLOSED -> t(l, "MARKET CLOSED — FORECAST NOT ACTIONABLE", "मार्केट बंद — अनुमान पर कार्रवाई नहीं", "मार्केट बंद — अंदाजावर कृती नाही")
        STOPPED -> t(l, "STOP LEVEL TOUCHED — PLAN CLOSED", "स्टॉप लेवल छुआ — प्लान बंद", "स्टॉप लेव्हल लागला — प्लॅन बंद")
        UNDER_REVIEW -> t(l, "RESEARCH EVALUATION WINDOW — MODEL UNDER REVIEW", "रिसर्च मूल्यांकन — मॉडल जाँच में", "रिसर्च मूल्यमापन — मॉडेल तपासणीत")
        else -> t(l, "RESEARCH EVALUATION WINDOW", "रिसर्च मूल्यांकन अवधि", "रिसर्च मूल्यमापन कालावधी")
    }

    fun validitySubtitle(state: String, l: AppLanguage): String = when (state) {
        APPROVED_ACTIVE -> t(l, "Live-qualified research model, fresh XAU/USD data. Still not financial advice.", "LIVE QUALIFIED रिसर्च मॉडल, ताज़ा XAU/USD डेटा। फिर भी वित्तीय सलाह नहीं।", "LIVE QUALIFIED रिसर्च मॉडेल, ताजा XAU/USD डेटा. तरीही आर्थिक सल्ला नाही.")
        EXPIRED -> t(l, "The evaluation horizon is over. The outcome is checked only with real market data.", "मूल्यांकन अवधि खत्म। नतीजा सिर्फ़ असली मार्केट डेटा से जाँचा जाता है।", "मूल्यमापन कालावधी संपला. निकाल फक्त खऱ्या मार्केट डेटाने तपासला जातो.")
        STALE, OFFLINE, MARKET_CLOSED -> t(l, "The last forecast is kept for reference only.", "पिछला अनुमान सिर्फ़ संदर्भ के लिए रखा है।", "मागील अंदाज फक्त संदर्भासाठी ठेवला आहे.")
        STOPPED -> t(l, "The recorded stop was touched inside the candle.", "दर्ज स्टॉप कैंडल के अंदर छू गया।", "नोंदलेला स्टॉप कँडलमध्ये लागला.")
        else -> t(l, "Historical rule-based forecast being evaluated. Not an active trading instruction.", "नियम-आधारित अनुमान जिसका मूल्यांकन हो रहा है। सक्रिय ट्रेडिंग निर्देश नहीं।", "नियम-आधारित अंदाज ज्याचे मूल्यमापन सुरू आहे. सक्रिय ट्रेडिंग सूचना नाही.")
    }

    /** "5M INTERVAL · 25M EVALUATION HORIZON": the candle size and the horizon are never the same thing. */
    fun intervalLine(interval: String, horizonMin: Int, l: AppLanguage): String {
        val h = if (horizonMin >= 60 && horizonMin % 60 == 0) "${horizonMin / 60}H" else "${horizonMin}M"
        return interval.uppercase(Locale.US) + t(l, " INTERVAL · $h EVALUATION HORIZON", " इंटरवल · $h मूल्यांकन अवधि", " इंटरव्हल · $h मूल्यमापन कालावधी")
    }

    /** After the horizon: AWAITING OUTCOME CHECK until a real verified outcome exists. */
    fun outcomeLine(outcome: String, l: AppLanguage): String = when (outcome) {
        "" , "PENDING" -> t(l, "HORIZON ENDED · AWAITING OUTCOME CHECK", "अवधि खत्म · नतीजे की जाँच बाकी", "कालावधी संपला · निकाल तपासणी बाकी")
        "DATA_FAILURE", "MARKET_CLOSED", "VERIFICATION_UNCERTAIN" -> t(l, "HORIZON ENDED · NOT SCORED ($outcome)", "अवधि खत्म · गिना नहीं गया ($outcome)", "कालावधी संपला · मोजले नाही ($outcome)")
        else -> t(l, "HORIZON ENDED · VERIFIED: $outcome", "अवधि खत्म · जाँचा गया: $outcome", "कालावधी संपला · तपासले: $outcome")
    }
}
