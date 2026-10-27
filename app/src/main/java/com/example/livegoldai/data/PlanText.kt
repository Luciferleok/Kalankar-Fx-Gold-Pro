package com.example.livegoldai.data

import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import com.example.livegoldai.model.TradeSetup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** V31: every word the forecast popup uses about the plan, in one testable place (EN / HI / MR). */
object PlanText {
    private fun t(l: AppLanguage, en: String, hi: String, mr: String) = when (l) { AppLanguage.ENGLISH -> en; AppLanguage.HINDI -> hi; AppLanguage.MARATHI -> mr }

    /** One authoritative line at the top: the published decision and whether anything is actionable. */
    fun banner(a: GoldAnalysisResult, l: AppLanguage): Pair<String, String> {
        val ts = a.tradeSetup
        val reason = a.presentation?.let { DecisionTraceText.reason(it, l) } ?: ""
        val head = t(l, "FINAL DECISION: ", "अंतिम फ़ैसला: ", "अंतिम निर्णय: ") + a.overallSignal.name
        val sub = when (ts.planStatus) {
            PlanStamp.ACTIVE -> t(l, "Active plan from this snapshot. Not financial advice.", "इसी स्नैपशॉट का सक्रिय प्लान। वित्तीय सलाह नहीं।", "याच स्नॅपशॉटचा सक्रिय प्लॅन. आर्थिक सल्ला नाही.")
            PlanStamp.STOPPED -> t(l, "STOP-LOSS HIT inside candle ${ts.stopTouched}: this trade is over, do not enter.",
                "कैंडल ${ts.stopTouched} में STOP-LOSS लग चुका: यह ट्रेड खत्म, एंट्री मत लो।", "कँडल ${ts.stopTouched} मध्ये STOP-LOSS लागला: हा ट्रेड संपला, एंट्री घेऊ नका.")
            PlanStamp.INACTIVE_WAIT -> t(l, "NO ACTIVE TRADE. The engine wanted ${(a.rawSignal ?: Signal.WAIT).name} but it was held: ", "कोई सक्रिय ट्रेड नहीं। इंजन ${(a.rawSignal ?: Signal.WAIT).name} चाहता था पर रोका गया: ", "सक्रिय ट्रेड नाही. इंजिनला ${(a.rawSignal ?: Signal.WAIT).name} हवे होते पण थांबवले: ") + reason
            PlanStamp.NO_DATA -> t(l, "NO ACTIVE TRADE: no usable live XAU/USD data.", "कोई सक्रिय ट्रेड नहीं: उपयोगी लाइव XAU/USD डेटा नहीं।", "सक्रिय ट्रेड नाही: वापरण्यायोग्य लाइव्ह XAU/USD डेटा नाही.")
            else -> t(l, "NO ACTIVE TRADE. Levels below are a conditional scenario only.", "कोई सक्रिय ट्रेड नहीं। नीचे के लेवल सिर्फ़ एक शर्त वाला परिदृश्य हैं।", "सक्रिय ट्रेड नाही. खालील लेव्हल फक्त अटींवरील परिस्थिती आहेत.")
        }
        return head to sub
    }

    fun planTitle(ts: TradeSetup, l: AppLanguage): String = when (ts.planStatus) {
        PlanStamp.ACTIVE -> t(l, "ACTION PLAN (ACTIVE)", "एक्शन प्लान (सक्रिय)", "अ‍ॅक्शन प्लॅन (सक्रिय)")
        PlanStamp.STOPPED -> t(l, "PLAN CLOSED: STOP-LOSS HIT", "प्लान बंद: STOP-LOSS लगा", "प्लॅन बंद: STOP-LOSS लागला")
        else -> t(l, "CONDITIONAL SCENARIO (NOT A TRADE)", "शर्त वाला परिदृश्य (ट्रेड नहीं)", "अटींवरील परिस्थिती (ट्रेड नाही)")
    }

    fun targetLabel(ts: TradeSetup, l: AppLanguage): String =
        if (ts.planStatus == PlanStamp.ACTIVE) t(l, "Next target (TP1)", "अगला टारगेट (TP1)", "पुढील टार्गेट (TP1)")
        else t(l, "Scenario level (not a target)", "परिदृश्य लेवल (टारगेट नहीं)", "परिस्थिती लेव्हल (टार्गेट नाही)")

    fun waitHeadline(a: GoldAnalysisResult, l: AppLanguage): String =
        if (a.rawSignal != null && a.rawSignal != Signal.WAIT && a.overallSignal == Signal.WAIT)
            t(l, "WAIT: the engine's ${a.rawSignal.name} was held back", "WAIT: इंजन का ${a.rawSignal.name} रोका गया", "WAIT: इंजिनचा ${a.rawSignal.name} थांबवला")
        else t(l, "WAIT: no side has enough evidence now", "WAIT: अभी किसी तरफ़ पर्याप्त सबूत नहीं", "WAIT: आत्ता कोणत्याही बाजूला पुरेसा पुरावा नाही")

    fun evidenceTitle(fromHistory: Boolean, l: AppLanguage): String =
        if (fromHistory) t(l, "PAST RECORD (NOT A FORECAST)", "पिछला रिकॉर्ड (भविष्यवाणी नहीं)", "मागील रेकॉर्ड (भविष्यवाणी नाही)")
        else t(l, "CURRENT EVIDENCE DISTRIBUTION", "अभी के सबूत का बँटवारा", "सध्याच्या पुराव्याचे वाटप")

    fun evidenceNote(fromHistory: Boolean, l: AppLanguage): String =
        if (fromHistory) t(l, "What really happened after this app's past BUY/SELL calls (all timeframes). A track record, not the chance of this call.",
            "पिछली BUY/SELL कॉल्स के बाद असल में क्या हुआ (सभी टाइमफ़्रेम)। ट्रैक रिकॉर्ड, इस कॉल की संभावना नहीं।",
            "मागील BUY/SELL कॉल्सनंतर प्रत्यक्षात काय झाले (सर्व टाइमफ्रेम). ट्रॅक रेकॉर्ड, या कॉलची शक्यता नाही.")
        else t(l, "Indicator agreement only — not historical performance or calibrated probability. Share of the 7 pillars voting BUY / WAIT / SELL (sums to 100%).",
            "सिर्फ़ इंडिकेटर सहमति — पिछला प्रदर्शन या कैलिब्रेटेड संभावना नहीं। 7 पिलरों में BUY / WAIT / SELL वोट का हिस्सा (कुल 100%)।",
            "फक्त इंडिकेटर सहमती — मागील कामगिरी किंवा कॅलिब्रेटेड शक्यता नाही. 7 पिलरपैकी BUY / WAIT / SELL मतांचा वाटा (एकूण 100%).")

    private val hm = SimpleDateFormat("dd MMM HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    private fun time(ms: Long) = if (ms <= 0L) "UNKNOWN" else synchronized(hm) { hm.format(Date(ms)) } + " UTC"

    /** Where every price on the popup came from. */
    fun provenance(ts: TradeSetup, l: AppLanguage): String =
        t(l, "Snapshot", "स्नैपशॉट", "स्नॅपशॉट") + " ${ts.snapshotId.ifBlank { "UNKNOWN" }} • ${ts.instrument.ifBlank { "UNKNOWN" }}\n" +
            t(l, "Source", "स्रोत", "स्रोत") + " ${ts.dataSource.ifBlank { "UNKNOWN" }} @ ${time(ts.sourceTimeMs)} • " +
            t(l, "built", "बना", "तयार") + " ${time(ts.generatedAtMs)}\n" +
            t(l, "Model", "मॉडल", "मॉडेल") + " ${ts.modelVersion.ifBlank { "UNKNOWN" }} • " + t(l, "plan", "प्लान", "प्लॅन") + " ${ts.planStatus}\n" +
            t(l, "Prices are feed mid quotes. Your broker's bid/ask, spread and stop execution will differ.",
                "भाव फ़ीड के मिड कोट हैं। आपके ब्रोकर का bid/ask, स्प्रेड और स्टॉप एक्ज़ीक्यूशन अलग होगा।",
                "भाव फीडचे मिड कोट आहेत. तुमच्या ब्रोकरचा bid/ask, स्प्रेड आणि स्टॉप एक्झिक्युशन वेगळे असेल.")
}
