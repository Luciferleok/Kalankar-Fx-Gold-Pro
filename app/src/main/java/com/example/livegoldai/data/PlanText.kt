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
        // V29D: a BUY/SELL without a proven edge is an unverified research signal, never a "decision" to trade
        val head = if (a.overallSignal != Signal.WAIT && ts.planStatus != PlanStamp.ACTIVE)
            t(l, "RESEARCH SIGNAL: ", "रिसर्च सिग्नल: ", "रिसर्च सिग्नल: ") + a.overallSignal.name + t(l, " (UNVERIFIED)", " (असत्यापित)", " (असत्यापित)")
        else t(l, "FINAL DECISION: ", "अंतिम फ़ैसला: ", "अंतिम निर्णय: ") + a.overallSignal.name +
            (if (a.overallSignal == Signal.WAIT && p != null) " — " + reasonShort(p.reasonCode, l) else "")
        val sub = when (ts.planStatus) {
            PlanStamp.ACTIVE -> t(l, "Active plan from this snapshot. Not financial advice.", "इसी स्नैपशॉट का सक्रिय प्लान। वित्तीय सलाह नहीं।", "याच स्नॅपशॉटचा सक्रिय प्लॅन. आर्थिक सल्ला नाही.")
            PlanStamp.STOPPED -> t(l, "STOP-LOSS HIT inside candle ${ts.stopTouched}: this trade is over, do not enter.",
                "कैंडल ${ts.stopTouched} में STOP-LOSS लग चुका: यह ट्रेड खत्म, एंट्री मत लो।", "कँडल ${ts.stopTouched} मध्ये STOP-LOSS लागला: हा ट्रेड संपला, एंट्री घेऊ नका.")
            PlanStamp.INACTIVE_WAIT -> t(l, "NO ACTIVE TRADE. The engine wanted ${(a.rawSignal ?: Signal.WAIT).name} but it was held: ", "कोई सक्रिय ट्रेड नहीं। इंजन ${(a.rawSignal ?: Signal.WAIT).name} चाहता था पर रोका गया: ", "सक्रिय ट्रेड नाही. इंजिनला ${(a.rawSignal ?: Signal.WAIT).name} हवे होते पण थांबवले: ") + reason
            PlanStamp.NO_DATA -> t(l, "NO ACTIVE TRADE: market data is ${ts.freshness.ifBlank { "unusable" }}. The last forecast is kept only for reference.",
                "कोई सक्रिय ट्रेड नहीं: मार्केट डेटा ${ts.freshness.ifBlank { "अनुपयोगी" }} है। पिछला अनुमान सिर्फ़ संदर्भ के लिए।", "सक्रिय ट्रेड नाही: मार्केट डेटा ${ts.freshness.ifBlank { "निरुपयोगी" }} आहे. मागील अंदाज फक्त संदर्भासाठी.")
            PlanStamp.RESEARCH -> t(l, "Unverified research signal: no proven predictive edge. Levels are conditional research, not a trade instruction.",
                "असत्यापित रिसर्च सिग्नल: कोई साबित बढ़त नहीं। लेवल शर्त वाला रिसर्च हैं, ट्रेड का निर्देश नहीं।", "असत्यापित रिसर्च सिग्नल: सिद्ध आघाडी नाही. लेव्हल अटींवरील रिसर्च आहेत, ट्रेडची सूचना नाही.")
            PlanStamp.EXPIRED -> t(l, "PREDICTION EXPIRED: its horizon has passed. Wait for the next review.", "प्रेडिक्शन की अवधि खत्म। अगली जाँच का इंतज़ार करें।", "अंदाजाची मुदत संपली. पुढील तपासणीची वाट पहा.")
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
        PlanStamp.RESEARCH -> t(l, "HYPOTHETICAL RESEARCH SCENARIO", "काल्पनिक रिसर्च परिदृश्य", "काल्पनिक रिसर्च परिस्थिती")
        PlanStamp.EXPIRED -> t(l, "PREDICTION EXPIRED", "प्रेडिक्शन की अवधि खत्म", "अंदाजाची मुदत संपली")
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
            t(l, "Horizon", "अवधि", "कालावधी") + " ${ts.horizonMin} min • " + t(l, "expires", "समाप्ति", "मुदत") + " ${time(ts.expiresAtMs)} • " + t(l, "data", "डेटा", "डेटा") + " ${ts.freshness.ifBlank { "UNKNOWN" }}\n" +
            t(l, "Prices are feed mid quotes. Your broker's bid/ask, spread, slippage and stop execution will differ.",
                "भाव फ़ीड के मिड कोट हैं। आपके ब्रोकर का bid/ask, स्प्रेड, स्लिपेज और स्टॉप एक्ज़ीक्यूशन अलग होगा।",
                "भाव फीडचे मिड कोट आहेत. तुमच्या ब्रोकरचा bid/ask, स्प्रेड, स्लिपेज आणि स्टॉप एक्झिक्युशन वेगळे असेल.")

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
        "QUALIFIED_OTHER_FEED" -> t(l, "qualified on Twelve Data only", "सिर्फ़ Twelve Data पर क्वालिफ़ाइड", "फक्त Twelve Data वर क्वालिफाइड")
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

    fun researchScenarioTitle(l: AppLanguage) = t(l, "HYPOTHETICAL RESEARCH SCENARIO — NOT AN ACTIVE TRADE", "काल्पनिक रिसर्च परिदृश्य — सक्रिय ट्रेड नहीं", "काल्पनिक रिसर्च परिस्थिती — सक्रिय ट्रेड नाही")
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
    fun shareRow(kind: Signal, fromHistory: Boolean, l: AppLanguage, current: Signal = Signal.WAIT): String = if (!fromHistory) when (kind) {
        Signal.BUY -> t(l, "BUY VOTE SHARE", "BUY वोट हिस्सा", "BUY मत वाटा")
        Signal.WAIT -> t(l, "WAIT VOTE SHARE", "WAIT वोट हिस्सा", "WAIT मत वाटा")
        Signal.SELL -> t(l, "SELL VOTE SHARE", "SELL वोट हिस्सा", "SELL मत वाटा")
    } else when {
        // V29E: history rows are outcomes RELATIVE to each past call's own direction (BUY and SELL calls mixed),
        // shown on the side of the current call; they are not price-direction frequencies
        kind == Signal.WAIT -> t(l, "PAST CALLS: NO REAL MOVE", "पिछली कॉल: कोई असली चाल नहीं", "मागील कॉल: खरी हालचाल नाही")
        kind == current -> t(l, "PAST CALLS: WENT THE CALLED WAY", "पिछली कॉल: बताई दिशा में गईं", "मागील कॉल: सांगितलेल्या दिशेने")
        else -> t(l, "PAST CALLS: WENT AGAINST THE CALL", "पिछली कॉल: उल्टी गईं", "मागील कॉल: उलट गेल्या")
    }
    fun shareNote(l: AppLanguage) = t(l, "Shares of rule-based indicator pillars. Not probabilities of future market outcomes.",
        "नियम-आधारित इंडिकेटर पिलरों का हिस्सा। भविष्य की चाल की संभावना नहीं।", "नियम-आधारित इंडिकेटर पिलरचा वाटा. भविष्यातील हालचालीची शक्यता नाही.")

    // ================= V29C.1 =================

    /** "NEXT REVIEW: 12:15:00 UTC (in 4m)" or "REVIEW DUE" once the fixed review time has passed. */
    fun reviewLine(reviewAtMs: Long, now: Long, l: AppLanguage): String = when {
        reviewAtMs <= 0L -> ""
        now >= reviewAtMs -> t(l, "REVIEW DUE", "जाँच का समय हो गया", "तपासणीची वेळ झाली")
        else -> nextReview(reviewAtMs, l) + " (" + t(l, "in ", "", "") + mins(reviewAtMs - now) + t(l, ")", " में)", " मध्ये)")
    }
    private fun mins(ms: Long): String { val m = ms / 60_000; val s = (ms / 1000) % 60; return if (m >= 60) "${m / 60}h ${m % 60}m" else if (m > 0) "${m}m ${s}s" else "${s}s" }

    /** Up to three reasons for THIS decision. A WAIT is explained by its gate first, never by a pillar count. */
    fun whyReasons(a: GoldAnalysisResult, l: AppLanguage): List<String> {
        val p = a.presentation
        val raw = (a.rawSignal ?: a.overallSignal).name
        if (a.overallSignal == Signal.WAIT && p != null) {
            val first = when (p.reasonCode) {
                "RANGE" -> t(l, "Range filter blocked the raw $raw signal: trend efficiency is below 20% (sideways market).",
                    "रेंज फ़िल्टर ने रॉ $raw सिग्नल रोका: ट्रेंड एफिशिएंसी 20% से कम (साइडवेज़ मार्केट)।", "रेंज फिल्टरने रॉ $raw सिग्नल थांबवला: ट्रेंड एफिशियन्सी 20% पेक्षा कमी (साइडवेज मार्केट).")
                "SOURCE" -> t(l, "Source switch: the price is not clean Twelve Data XAU/USD, so the raw $raw is not published.",
                    "स्रोत बदला: भाव साफ़ Twelve Data XAU/USD का नहीं, इसलिए रॉ $raw प्रकाशित नहीं।", "स्रोत बदलला: भाव स्वच्छ Twelve Data XAU/USD चा नाही, म्हणून रॉ $raw प्रकाशित नाही.")
                "DATA" -> t(l, "Market data check failed, so no BUY/SELL is published.", "मार्केट डेटा जाँच फेल, इसलिए BUY/SELL प्रकाशित नहीं।", "मार्केट डेटा तपासणी नापास, म्हणून BUY/SELL प्रकाशित नाही.")
                "NO_KEY" -> t(l, "No Twelve Data key is saved, so no XAU/USD decision can be made.", "Twelve Data key नहीं, इसलिए XAU/USD फ़ैसला नहीं।", "Twelve Data key नाही, म्हणून XAU/USD निर्णय नाही.")
                "LEARNED" -> t(l, "A learned filter blocked the raw $raw signal (${p.reasonText}).", "सीखे फ़िल्टर ने रॉ $raw रोका (${p.reasonText})।", "शिकलेल्या फिल्टरने रॉ $raw थांबवला (${p.reasonText}).")
                "BELOW_GATE" -> t(l, "No side reached the 68% pillar gate (${p.reasonText}).", "कोई पक्ष 68% पिलर गेट तक नहीं पहुँचा (${p.reasonText})।", "कोणतीही बाजू 68% पिलर गेटपर्यंत पोहोचली नाही (${p.reasonText}).")
                else -> t(l, "An engine safety rule held the signal.", "इंजन के सुरक्षा नियम ने सिग्नल रोका।", "इंजिनच्या सुरक्षा नियमाने सिग्नल थांबवला.")
            }
            val calErr = a.learning?.calibrationErrorPoints ?: -1.0
            val more = p.secondaryReasons.mapNotNull { c -> when (c) {
                "HIGH_UNCERTAINTY" -> t(l, "Uncertainty is HIGH" + (if (calErr >= 0) String.format(Locale.US, " (confidence off by %.0f pts)", calErr) else "") + ".",
                    "अनिश्चितता ऊँची है" + (if (calErr >= 0) String.format(Locale.US, " (भरोसा %.0f अंक गलत)", calErr) else "") + "।",
                    "अनिश्चितता उच्च आहे" + (if (calErr >= 0) String.format(Locale.US, " (विश्वास %.0f अंक चुकीचा)", calErr) else "") + ".")
                "NO_PROVEN_EDGE" -> t(l, "No proven predictive edge: the research model is not LIVE QUALIFIED.", "कोई साबित बढ़त नहीं: रिसर्च मॉडल LIVE QUALIFIED नहीं।", "सिद्ध आघाडी नाही: रिसर्च मॉडेल LIVE QUALIFIED नाही.")
                "QUALIFIED_OTHER_FEED" -> t(l, "The model is LIVE QUALIFIED on Twelve Data only; this data is from OANDA, so it stays a research signal.",
                    "मॉडल सिर्फ़ Twelve Data पर LIVE QUALIFIED है; यह डेटा OANDA का है, इसलिए रिसर्च सिग्नल ही रहेगा।",
                    "मॉडेल फक्त Twelve Data वर LIVE QUALIFIED आहे; हा डेटा OANDA चा आहे, म्हणून रिसर्च सिग्नलच राहील.")
                "ENGINE_NO_SIDE" -> t(l, "The engine itself also saw no side.", "इंजन को भी कोई पक्ष नहीं दिखा।", "इंजिनलाही बाजू दिसली नाही.")
                "RANGE_REGIME" -> t(l, "The market regime is RANGE: trend rules are weaker.", "मार्केट RANGE में: ट्रेंड नियम कमज़ोर।", "मार्केट RANGE मध्ये: ट्रेंड नियम कमकुवत.")
                else -> null   // optional MTF never explains a decision it is not part of
            } }
            return (listOf(first) + more).take(3)
        }
        val ins = a.insights
        val sig = a.overallSignal
        val votes = a.groups.count { it.verdict == sig }
        val pillar = if (ins != null) t(l, String.format(Locale.US, "%d of %d pillars voted %s (weighted %.0f%%, gate %.0f%%).", votes, a.totalGroups, sig.name, if (sig == Signal.BUY) ins.bullishPoints else kotlin.math.abs(ins.bearishPoints), ins.gatePercent),
            String.format(Locale.US, "%d में से %d पिलर %s (वेटेड %.0f%%, गेट %.0f%%)।", a.totalGroups, votes, sig.name, if (sig == Signal.BUY) ins.bullishPoints else kotlin.math.abs(ins.bearishPoints), ins.gatePercent),
            String.format(Locale.US, "%d पैकी %d पिलर %s (वेटेड %.0f%%, गेट %.0f%%).", a.totalGroups, votes, sig.name, if (sig == Signal.BUY) ins.bullishPoints else kotlin.math.abs(ins.bearishPoints), ins.gatePercent))
        else t(l, "$votes of ${a.totalGroups} pillars voted ${sig.name}.", "${a.totalGroups} में से $votes पिलर ${sig.name}।", "${a.totalGroups} पैकी $votes पिलर ${sig.name}.")
        val smc = a.groups.firstOrNull { it.key == "smc" }?.verdict?.name ?: "--"
        val reg = (a.brain?.regime ?: ins?.regime ?: "--").replace('_', ' ')
        return listOf(pillar,
            t(l, "SMC pillar (SuperTrend, VWAP/TWAP, structure): $smc.", "SMC पिलर (SuperTrend, VWAP/TWAP, संरचना): $smc।", "SMC पिलर (SuperTrend, VWAP/TWAP, रचना): $smc."),
            t(l, "Market regime: $reg.", "मार्केट स्थिति: $reg।", "मार्केट स्थिती: $reg."))
    }

    fun whyTitle(l: AppLanguage) = t(l, "WHY? (UP TO 3 REASONS)", "क्यों? (अधिकतम 3 कारण)", "का? (जास्तीत जास्त 3 कारणे)")

    /** Three separate statuses, never one blended "usable". */
    fun brainStatus(p: PredictionPresentationState, l: AppLanguage): List<Pair<String, String>> {
        val dq = p.dataQuality
        val data = when (dq) { "EXCELLENT", "GOOD" -> t(l, "DATA USABLE", "डेटा ठीक", "डेटा वापरण्यायोग्य") to "OK"; "UNKNOWN" -> t(l, "DATA UNKNOWN", "डेटा अज्ञात", "डेटा अज्ञात") to "NA"; else -> t(l, "DATA $dq", "डेटा $dq", "डेटा $dq") to "WARN" }
        val signal = when {
            p.isTradeSetupActive -> t(l, "SIGNAL ELIGIBLE", "सिग्नल योग्य", "सिग्नल पात्र") to "OK"
            p.finalSignal != Signal.WAIT && p.planStatus == PlanStamp.RESEARCH -> t(l, "RESEARCH SIGNAL — UNVERIFIED", "रिसर्च सिग्नल — असत्यापित", "रिसर्च सिग्नल — असत्यापित") to "WARN"
            p.planStatus == PlanStamp.NO_DATA -> t(l, "SIGNAL BLOCKED (DATA)", "सिग्नल रुका (डेटा)", "सिग्नल थांबला (डेटा)") to "BLOCK"
            p.finalSignal != Signal.WAIT -> t(l, "SIGNAL CLOSED", "सिग्नल बंद", "सिग्नल बंद") to "WARN"
            p.rawDiffersFromFinal || p.reasonCode in listOf("SOURCE", "DATA", "NO_KEY", "RANGE", "LEARNED") -> t(l, "SIGNAL BLOCKED", "सिग्नल रुका", "सिग्नल थांबला") to "BLOCK"
            else -> t(l, "NO SIGNAL", "कोई सिग्नल नहीं", "सिग्नल नाही") to "WARN"
        }
        val edge = if (p.provenEdge) t(l, "EDGE LIVE QUALIFIED", "बढ़त LIVE QUALIFIED", "आघाडी LIVE QUALIFIED") to "OK"
            else t(l, "PREDICTIVE EDGE NOT ESTABLISHED", "भविष्यवाणी बढ़त स्थापित नहीं", "अंदाज आघाडी स्थापित नाही") to "WARN"
        return listOf(data, signal, edge)
    }

    // historical scorecard definitions
    fun directionalTitle(l: AppLanguage) = t(l, "HISTORICAL DIRECTIONAL HIT-RATE", "पिछला दिशा हिट-रेट", "मागील दिशा हिट-रेट")
    fun outcomeDefinition(horizonMin: Int, l: AppLanguage) = t(l,
        "Window: each call's own horizon ($horizonMin min). Correct = target hit, or price closed the called way beyond the noise threshold. Incorrect = stop hit or wrong way. Small moves are excluded. Not profitability.",
        "विंडो: हर कॉल का अपना समय ($horizonMin मिनट)। सही = टारगेट लगा, या भाव बताई दिशा में नॉइज़ सीमा से आगे बंद हुआ। गलत = स्टॉप लगा या उल्टी दिशा। छोटी चालें शामिल नहीं। मुनाफ़ा नहीं।",
        "विंडो: प्रत्येक कॉलचा स्वतःचा कालावधी ($horizonMin मिनिटे). बरोबर = टार्गेट लागला, किंवा भाव सांगितलेल्या दिशेने नॉइज मर्यादेपलीकडे बंद झाला. चूक = स्टॉप लागला किंवा उलट दिशा. लहान हालचाली वगळल्या. नफा नाही.")

    fun playbookLink(isWait: Boolean, l: AppLanguage) = if (isWait) t(l, "View research scenarios →", "रिसर्च परिदृश्य देखें →", "रिसर्च परिस्थिती पहा →")
        else t(l, "Open full playbook (entry, SL, targets) →", "पूरा प्लेबुक खोलें (एंट्री, SL, टारगेट) →", "पूर्ण प्लेबुक उघडा (एंट्री, SL, टार्गेट) →")

    fun proxyLevelNote(l: AppLanguage) = t(l, "Levels are PAXG/USDT prices, not XAU/USD levels.", "लेवल PAXG/USDT के भाव हैं, XAU/USD लेवल नहीं।", "लेव्हल PAXG/USDT चे भाव आहेत, XAU/USD लेव्हल नाहीत.")

    /** V32: Home SOURCE tile names the provider: "OANDA", "TWELVE DATA", "TD BACKUP"; proxies keep their type. */
    fun sourceTile(source: String?, sourceType: String): String {
        val b = SourceRegistry.badge(source).split(" • ")
        return if (b.size >= 3 && b[0] == "XAU/USD") (if (b[2] == "BACKUP") "TD BACKUP" else b[1]) else sourceType.ifBlank { "--" }
    }

    /** "EMA21 $4039.56" -> "PAXG EMA21 $4039.56" (idempotent). */
    fun proxyLevel(level: String): String = if (level.isBlank() || level.startsWith("PAXG")) level else "PAXG $level"

    // ================= V29E =================
    fun nextCandleLabel(l: AppLanguage) = t(l, "Next-candle rule lean (separate, not evaluated, no track record): ",
        "अगली कैंडल पर नियम झुकाव (अलग, जाँचा नहीं गया, कोई रिकॉर्ड नहीं): ", "पुढील कँडलवर नियम कल (वेगळा, तपासलेला नाही, रेकॉर्ड नाही): ")

    /** "Time remaining: 22m 51s · Expires at 11:52:00 UTC" from a FIXED expiry. */
    fun remainingLine(expiresAtMs: Long, now: Long, l: AppLanguage): String {
        val ms = (expiresAtMs - now).coerceAtLeast(0L); val m = ms / 60_000; val s = (ms / 1000) % 60
        val rem = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m ${s}s"
        return t(l, "Time remaining: ", "बचा समय: ", "उरलेला वेळ: ") + rem + " · " + t(l, "Expires at ", "समाप्ति ", "मुदत ") + time(expiresAtMs)
    }

    fun dataHealthCaption(l: AppLanguage) = t(l, "MARKET DATA HEALTH", "मार्केट डेटा की सेहत", "मार्केट डेटा आरोग्य")
    fun evidenceCaption(l: AppLanguage) = t(l, "PREDICTIVE EVIDENCE", "भविष्यवाणी का सबूत", "अंदाजाचा पुरावा")
    fun healthVsEdgeNote(l: AppLanguage) = t(l, "Good data does not mean accurate predictions. Pillar agreement is not a win chance; 7 rules on the same chart are not 7 independent models.",
        "अच्छा डेटा मतलब सही भविष्यवाणी नहीं। पिलर सहमति जीत की संभावना नहीं; एक ही चार्ट पर 7 नियम 7 अलग मॉडल नहीं।",
        "चांगला डेटा म्हणजे अचूक अंदाज नाही. पिलर सहमती म्हणजे जिंकण्याची शक्यता नाही; एकाच चार्टवरील 7 नियम म्हणजे 7 स्वतंत्र मॉडेल नाहीत.")
}
