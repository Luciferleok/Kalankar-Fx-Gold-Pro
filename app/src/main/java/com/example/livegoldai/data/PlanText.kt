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
        val p = a.presentation
        val head = t(l, "FINAL DECISION: ", "अंतिम फ़ैसला: ", "अंतिम निर्णय: ") + a.overallSignal.name +
            (if (a.overallSignal == Signal.WAIT && p != null) " — " + reasonShort(p.reasonCode, l) else "")
        val sub = when (ts.planStatus) {
            PlanStamp.ACTIVE -> t(l, "Active plan from this snapshot. Not financial advice.", "इसी स्नैपशॉट का सक्रिय प्लान। वित्तीय सलाह नहीं।", "याच स्नॅपशॉटचा सक्रिय प्लॅन. आर्थिक सल्ला नाही.")
            PlanStamp.STOPPED -> t(l, "STOP-LOSS HIT inside candle ${ts.stopTouched}: this trade is over, do not enter.",
                "कैंडल ${ts.stopTouched} में STOP-LOSS लग चुका: यह ट्रेड खत्म, एंट्री मत लो।", "कँडल ${ts.stopTouched} मध्ये STOP-LOSS लागला: हा ट्रेड संपला, एंट्री घेऊ नका.")
            PlanStamp.INACTIVE_WAIT -> t(l, "NO ACTIVE TRADE. The engine wanted ${(a.rawSignal ?: Signal.WAIT).name} but it was held: ", "कोई सक्रिय ट्रेड नहीं। इंजन ${(a.rawSignal ?: Signal.WAIT).name} चाहता था पर रोका गया: ", "सक्रिय ट्रेड नाही. इंजिनला ${(a.rawSignal ?: Signal.WAIT).name} हवे होते पण थांबवले: ") + reason
            PlanStamp.NO_DATA -> t(l, "NO ACTIVE TRADE: no usable live XAU/USD data.", "कोई सक्रिय ट्रेड नहीं: उपयोगी लाइव XAU/USD डेटा नहीं।", "सक्रिय ट्रेड नाही: वापरण्यायोग्य लाइव्ह XAU/USD डेटा नाही.")
            else -> t(l, "NO ACTIVE TRADE. Levels below are a conditional scenario only.", "कोई सक्रिय ट्रेड नहीं। नीचे के लेवल सिर्फ़ एक शर्त वाला परिदृश्य हैं।", "सक्रिय ट्रेड नाही. खालील लेव्हल फक्त अटींवरील परिस्थिती आहेत.")
        }
        val extra = listOfNotNull(
            p?.restorationPending?.takeIf { it.isNotEmpty() }?.let { restoration(it, l) },
            p?.let { secondaryLine(it.secondaryReasons, l).ifBlank { null } }
        )
        return head to (listOf(sub) + extra).joinToString("\n")
    }

    /** One short name per primary WAIT reason (same precedence as RealityEngine.waitReason). */
    fun reasonShort(code: String, l: AppLanguage): String = when (code) {
        "SOURCE" -> t(l, "SOURCE SWITCH", "स्रोत बदला", "स्रोत बदलला")
        "DATA" -> t(l, "DATA CHECK FAILED", "डेटा जाँच फेल", "डेटा तपासणी नापास")
        "NO_KEY" -> t(l, "NO DATA KEY", "डेटा key नहीं", "डेटा key नाही")
        "RANGE" -> t(l, "RANGE FILTER", "रेंज फ़िल्टर", "रेंज फिल्टर")
        "LEARNED" -> t(l, "LEARNED FILTER", "सीखा फ़िल्टर", "शिकलेला फिल्टर")
        "BELOW_GATE" -> t(l, "PILLARS BELOW GATE", "पिलर गेट से नीचे", "पिलर गेटखाली")
        "ENGINE_RULE" -> t(l, "ENGINE SAFETY RULE", "इंजन सुरक्षा नियम", "इंजिन सुरक्षा नियम")
        else -> t(l, "NO MEASURED EDGE", "कोई मापी बढ़त नहीं", "मोजलेली आघाडी नाही")
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
        // V29C: the headline names the SAME primary reason as the banner; it never invents a second main reason
        a.presentation?.let { "WAIT — " + reasonShort(it.reasonCode, l) } ?: if (a.rawSignal != null && a.rawSignal != Signal.WAIT && a.overallSignal == Signal.WAIT)
            t(l, "WAIT: the engine's ${a.rawSignal.name} was held back", "WAIT: इंजन का ${a.rawSignal.name} रोका गया", "WAIT: इंजिनचा ${a.rawSignal.name} थांबवला")
        else t(l, "WAIT: no side has enough evidence now", "WAIT: अभी किसी तरफ़ पर्याप्त सबूत नहीं", "WAIT: आत्ता कोणत्याही बाजूला पुरेसा पुरावा नाही")

    fun evidenceTitle(fromHistory: Boolean, l: AppLanguage): String =
        if (fromHistory) t(l, "PAST RECORD (NOT A FORECAST)", "पिछला रिकॉर्ड (भविष्यवाणी नहीं)", "मागील रेकॉर्ड (भविष्यवाणी नाही)")
        else t(l, "CURRENT EVIDENCE DISTRIBUTION", "अभी के सबूत का बँटवारा", "सध्याच्या पुराव्याचे वाटप")

    fun evidenceNote(fromHistory: Boolean, l: AppLanguage): String =
        if (fromHistory) t(l, "What really happened after this app's past BUY/SELL calls (all timeframes). A track record, not the chance of this call.",
            "पिछली BUY/SELL कॉल्स के बाद असल में क्या हुआ (सभी टाइमफ़्रेम)। ट्रैक रिकॉर्ड, इस कॉल की संभावना नहीं।",
            "मागील BUY/SELL कॉल्सनंतर प्रत्यक्षात काय झाले (सर्व टाइमफ्रेम). ट्रॅक रेकॉर्ड, या कॉलची शक्यता नाही.")
        else t(l, "Indicator agreement only — not historical performance or calibrated probability. Shares of rule-based indicator pillars (7 pillars, sums to 100%). Not probabilities of future market outcomes.",
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

    // ================= V29C =================

    /** Next candle boundary for this timeframe: the time the recorder actually re-evaluates (UTC-aligned, like the recorder). */
    fun nextReviewMs(interval: String, now: Long): Long {
        val iv = RealityEngine.intervalMinutes(interval) * 60_000L
        return if (iv <= 0) 0L else (now / iv + 1) * iv
    }

    fun utcTime(ms: Long): String = time(ms)

    fun noActiveSignal(l: AppLanguage) = t(l, "NO ACTIVE SIGNAL", "कोई सक्रिय सिग्नल नहीं", "सक्रिय सिग्नल नाही")
    fun nextReview(ms: Long, l: AppLanguage) = t(l, "NEXT REVIEW: ", "अगली जाँच: ", "पुढील तपासणी: ") + time(ms)

    fun secondaryLabel(code: String, l: AppLanguage): String = when (code) {
        "ENGINE_NO_SIDE" -> t(l, "engine saw no side either", "इंजन को भी कोई पक्ष नहीं दिखा", "इंजिनलाही बाजू दिसली नाही")
        "RANGE_REGIME" -> t(l, "range regime", "रेंज मार्केट", "रेंज मार्केट")
        "NO_PROVEN_EDGE" -> t(l, "no proven edge", "कोई साबित बढ़त नहीं", "सिद्ध आघाडी नाही")
        "HIGH_UNCERTAINTY" -> t(l, "high uncertainty", "ऊँची अनिश्चितता", "उच्च अनिश्चितता")
        "MTF_UNAVAILABLE" -> t(l, "optional MTF unavailable", "वैकल्पिक MTF उपलब्ध नहीं", "पर्यायी MTF उपलब्ध नाही")
        else -> code
    }

    fun secondaryLine(codes: List<String>, l: AppLanguage): String =
        if (codes.isEmpty()) "" else t(l, "Also: ", "साथ में: ", "तसेच: ") + codes.joinToString(" • ") { secondaryLabel(it, l) }

    /** "PRIMARY FEED AVAILABLE • RESTORATION PENDING — 1/2 CLEAN FETCHES • PREDICTION BLOCKED — SOURCE SWITCH" */
    fun restoration(pending: String, l: AppLanguage): String =
        t(l, "PRIMARY FEED AVAILABLE • RESTORATION PENDING — $pending CLEAN FETCHES • PREDICTION BLOCKED — SOURCE SWITCH",
            "मुख्य फ़ीड उपलब्ध • बहाली बाकी — $pending साफ़ फ़ेच • प्रेडिक्शन रुका — स्रोत बदला",
            "मुख्य फीड उपलब्ध • पुनर्स्थापना बाकी — $pending स्वच्छ फेच • अंदाज थांबवला — स्रोत बदलला")

    fun noProvenEdge(l: AppLanguage) = t(l, "NO PROVEN PREDICTIVE EDGE — historical hit-rate is not today's win chance.",
        "कोई साबित भविष्यवाणी बढ़त नहीं — पिछला हिट-रेट आज की जीत की संभावना नहीं।",
        "सिद्ध अंदाज आघाडी नाही — मागील हिट-रेट म्हणजे आजची जिंकण्याची शक्यता नाही.")

    fun researchToggle(open: Boolean, l: AppLanguage) =
        if (open) t(l, "RESEARCH DETAILS  ▴", "रिसर्च विवरण  ▴", "रिसर्च तपशील  ▴")
        else t(l, "RESEARCH DETAILS (rule council, vote shares, scenario, provenance)  ▾", "रिसर्च विवरण (नियम परिषद, वोट हिस्सा, परिदृश्य, स्रोत)  ▾", "रिसर्च तपशील (नियम परिषद, मत वाटा, परिस्थिती, स्रोत)  ▾")

    fun researchScenarioTitle(l: AppLanguage) = t(l, "RESEARCH SCENARIOS — NOT AN ACTIVE TRADE", "रिसर्च परिदृश्य — सक्रिय ट्रेड नहीं", "रिसर्च परिस्थिती — सक्रिय ट्रेड नाही")
    fun scenarioInvalidationTitle(l: AppLanguage) = t(l, "SCENARIO INVALIDATION CONDITIONS", "परिदृश्य रद्द होने की शर्तें", "परिस्थिती रद्द होण्याच्या अटी")
    fun levelsSuppressed(l: AppLanguage) = t(l, "Levels hidden: the price source is not usable for XAU/USD decisions.", "लेवल छिपाए: भाव स्रोत XAU/USD फ़ैसलों के लायक नहीं।", "लेव्हल लपवले: भाव स्रोत XAU/USD निर्णयांसाठी योग्य नाही.")

    // scorecard
    fun hitRateBadge(pct: Int, n: Int, l: AppLanguage) = t(l, "$pct% HISTORICAL HIT-RATE (N=$n)", "$pct% पिछला हिट-रेट (N=$n)", "$pct% मागील हिट-रेट (N=$n)")
    fun pointsLabel(l: AppLanguage) = t(l, "Close-to-close move, \$ • before costs, hypothetical", "क्लोज़-से-क्लोज़ चाल, \$ • खर्च से पहले, काल्पनिक", "क्लोज-ते-क्लोज चाल, \$ • खर्चापूर्वी, काल्पनिक")
    fun pointsValue(netPips: Double): String = String.format(Locale.US, "%+.1f", netPips / 10.0)

    // safety checks
    fun safetyChecksBadge(n: Int, l: AppLanguage) = t(l, "$n ACTIVE SAFETY CHECKS", "$n सक्रिय सुरक्षा जाँच", "$n सक्रिय सुरक्षा तपासण्या")
    fun safetyChecksNote(l: AppLanguage) = t(l, "These checks restrict signal eligibility. Their effectiveness is evaluated separately.",
        "ये जाँच सिग्नल की पात्रता सीमित करती हैं। इनका असर अलग से जाँचा जाता है।", "या तपासण्या सिग्नलची पात्रता मर्यादित करतात. त्यांचा परिणाम वेगळा तपासला जातो.")

    // rule council
    fun councilTitle(n: Int, l: AppLanguage) = t(l, "$n LOCAL RULE ENGINES", "$n लोकल नियम इंजन", "$n लोकल नियम इंजिन")
    fun councilBadge(agree: Int, n: Int, s: Signal, l: AppLanguage) = t(l, "$agree/$n CURRENTLY SAY ${s.name}", "$agree/$n अभी ${s.name} कहते हैं", "$agree/$n सध्या ${s.name} सांगतात")
    fun councilNote(l: AppLanguage) = t(l, "Rule agreement does not prove prediction accuracy.", "नियमों की सहमति भविष्यवाणी की सटीकता साबित नहीं करती।", "नियमांची सहमती अंदाजाची अचूकता सिद्ध करत नाही.")

    // vote shares (current pillar votes) vs past outcomes
    fun shareRow(kind: Signal, fromHistory: Boolean, l: AppLanguage): String = if (!fromHistory) when (kind) {
        Signal.BUY -> t(l, "BUY VOTE SHARE", "BUY वोट हिस्सा", "BUY मत वाटा")
        Signal.WAIT -> t(l, "WAIT VOTE SHARE", "WAIT वोट हिस्सा", "WAIT मत वाटा")
        Signal.SELL -> t(l, "SELL VOTE SHARE", "SELL वोट हिस्सा", "SELL मत वाटा")
    } else when (kind) {
        Signal.BUY -> t(l, "PAST CALLS: PRICE WENT UP", "पिछली कॉल: भाव ऊपर गया", "मागील कॉल: भाव वर गेला")
        Signal.WAIT -> t(l, "PAST CALLS: NO REAL MOVE", "पिछली कॉल: कोई असली चाल नहीं", "मागील कॉल: खरी हालचाल नाही")
        Signal.SELL -> t(l, "PAST CALLS: PRICE WENT DOWN", "पिछली कॉल: भाव नीचे गया", "मागील कॉल: भाव खाली गेला")
    }
    fun shareNote(l: AppLanguage) = t(l, "Shares of rule-based indicator pillars. Not probabilities of future market outcomes.",
        "नियम-आधारित इंडिकेटर पिलरों का हिस्सा। भविष्य की चाल की संभावना नहीं।", "नियम-आधारित इंडिकेटर पिलरचा वाटा. भविष्यातील हालचालीची शक्यता नाही.")
}
