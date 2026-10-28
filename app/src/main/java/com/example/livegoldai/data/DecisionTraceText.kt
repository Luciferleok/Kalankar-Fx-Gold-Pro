package com.example.livegoldai.data

import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.Signal
import java.util.Locale

/** Pure text of the trace (no Compose), so it can be tested in all three languages. */
object DecisionTraceText {
    data class Section(val title: String, val rows: List<Pair<String, String>>, val note: String? = null, val collapsed: Boolean = false)

    private fun t(lang: AppLanguage, en: String, hi: String, mr: String) = when (lang) {
        AppLanguage.ENGLISH -> en; AppLanguage.HINDI -> hi; AppLanguage.MARATHI -> mr
    }

    fun title(lang: AppLanguage) = t(lang, "DECISION TRACE", "फ़ैसले का ब्योरा", "निर्णयाचा तपशील")

    fun signal(s: Signal?, lang: AppLanguage) = when (s) {
        null -> t(lang, "UNKNOWN", "अज्ञात", "अज्ञात"); else -> s.name
    }

    fun reason(p: PredictionPresentationState, lang: AppLanguage): String = when (p.reasonCode) {
        "ELIGIBLE" -> t(lang, "Published: source, data and filter checks passed", "प्रकाशित: स्रोत, डेटा और फ़िल्टर जाँच पास", "प्रकाशित: स्रोत, डेटा आणि फिल्टर तपासणी पास")
        "SOURCE" -> t(lang, "Blocked: price is not Twelve Data XAU/USD (source switch)", "रोका गया: भाव Twelve Data XAU/USD का नहीं (स्रोत बदला)", "थांबवले: भाव Twelve Data XAU/USD चा नाही (स्रोत बदलला)")
        "DATA" -> t(lang, "Blocked: market data check failed", "रोका गया: मार्केट डेटा जाँच फेल", "थांबवले: मार्केट डेटा तपासणी नापास")
        "NO_KEY" -> t(lang, "Blocked: no Twelve Data key saved", "रोका गया: Twelve Data key नहीं", "थांबवले: Twelve Data key नाही")
        "RANGE" -> t(lang, "Blocked: range filter (sideways market)", "रोका गया: रेंज फ़िल्टर (साइडवेज़ मार्केट)", "थांबवले: रेंज फिल्टर (साइडवेज मार्केट)")
        "LEARNED" -> t(lang, "Blocked: learned filter", "रोका गया: सीखा हुआ फ़िल्टर", "थांबवले: शिकलेला फिल्टर") + " (" + p.reasonText + ")"
        "BELOW_GATE" -> t(lang, "No side: pillars below the gate", "कोई पक्ष नहीं: पिलर गेट से नीचे", "कोणतीही बाजू नाही: पिलर गेटच्या खाली") + " (" + p.reasonText + ")"
        else -> t(lang, "No side: engine safety rule", "कोई पक्ष नहीं: इंजन सुरक्षा नियम", "कोणतीही बाजू नाही: इंजिन सुरक्षा नियम")
    }

    private fun age(ms: Long, now: Long, lang: AppLanguage): String =
        if (ms <= 0L) t(lang, "UNKNOWN", "अज्ञात", "अज्ञात") else "${(now - ms) / 1000}s"

    private fun pts(x: Double) = String.format(Locale.US, "%+.1f", x)

    /**
     * V29C.1 order: 1 decision + primary reason, 2 source / timeframe / next review, 3 up to three reasons,
     * 4 historical evidence, 5 research details (collapsed), 6 audit trail (collapsed).
     * [why] = PlanText.whyReasons for the same analysis.
     */
    fun sections(p: PredictionPresentationState, lang: AppLanguage, now: Long = System.currentTimeMillis(), why: List<String> = emptyList()): List<Section> {
        val unknown = t(lang, "UNKNOWN", "अज्ञात", "अज्ञात")
        fun u(v: String) = if (v == "UNKNOWN" || v == "UNAVAILABLE") unknown else v
        val decision = Section(
            t(lang, "Decision", "फ़ैसला", "निर्णय"),
            listOf(
                t(lang, "Final decision", "अंतिम फ़ैसला", "अंतिम निर्णय") to p.finalSignal.name,
                t(lang, "Primary reason", "मुख्य कारण", "मुख्य कारण") to reason(p, lang),
                t(lang, "Raw quant", "रॉ क्वांट", "रॉ क्वांट") to signal(p.rawSignal, lang),
                t(lang, "Forecast state", "अनुमान स्थिति", "अंदाज स्थिती") to (if (p.forecastState.isBlank()) unknown else ForecastState.validityTitle(p.forecastState, lang)),
                t(lang, "Timeframe", "टाइमफ़्रेम", "टाइमफ्रेम") to (if (p.horizonMin > 0) ForecastState.intervalLine(p.timeframe, p.horizonMin, lang) else p.timeframe),
                t(lang, "Expires at", "समाप्ति", "मुदत") to (if (p.expiresAtMs > 0) PlanText.utcTime(p.expiresAtMs) else unknown),
                t(lang, "Newest candle", "नई कैंडल", "नवीन कँडल") to u(p.candleTime)
            ),
            listOfNotNull(
                if (p.restorationPending.isNotEmpty()) PlanText.restoration(p.restorationPending, lang) else null,
                PlanText.secondaryLine(p.secondaryReasons, lang).ifBlank { null },
                if (p.rawDiffersFromFinal) t(lang, "The engine and the published decision differ: the primary reason is why.",
                    "इंजन और प्रकाशित फ़ैसला अलग हैं: मुख्य कारण इसकी वजह है।", "इंजिन आणि प्रकाशित निर्णय वेगळे आहेत: मुख्य कारण त्याचे स्पष्टीकरण आहे.") else null
            ).joinToString("\n").ifBlank { null }
        )
        val source = Section(
            t(lang, "Market source", "मार्केट स्रोत", "मार्केट स्रोत"),
            listOf(
                t(lang, "Instrument • provider", "इंस्ट्रूमेंट • प्रदाता", "इन्स्ट्रुमेंट • प्रदाता") to p.sourceBadge,
                t(lang, "Usable by the XAU model", "XAU मॉडल के लायक", "XAU मॉडेलसाठी योग्य") to
                    (if (p.compatibleWithModel) t(lang, "YES", "हाँ", "होय") else "⚠ " + t(lang, "NO (proxy / unknown)", "नहीं (प्रॉक्सी / अज्ञात)", "नाही (प्रॉक्सी / अज्ञात)")),
                t(lang, "Source state", "स्रोत स्थिति", "स्रोत स्थिती") to (if (p.restorationPending.isNotEmpty()) "⚠ RESTORING ${p.restorationPending}" else u(p.sourceState)),
                t(lang, "Data age", "डेटा की उम्र", "डेटाचे वय") to age(p.analysisAtMs, now, lang),
                t(lang, "Data quality", "डेटा क्वालिटी", "डेटा गुणवत्ता") to u(p.dataQuality),
                t(lang, "Next review", "अगली जाँच", "पुढील तपासणी") to PlanText.reviewLine(p.nextReviewAtMs, now, lang).ifBlank { unknown }
            )
        )
        val whySection = Section(PlanText.whyTitle(lang),
            if (why.isEmpty()) listOf(t(lang, "Reasons", "कारण", "कारणे") to unknown) else why.mapIndexed { i, w -> "${i + 1}" to w })
        val evidenceRows = ArrayList<Pair<String, String>>()
        if (!p.attributionAvailable) evidenceRows.add(t(lang, "Factors", "कारक", "घटक") to t(lang, "Detailed factor attribution unavailable", "विस्तृत कारक जानकारी उपलब्ध नहीं", "तपशीलवार घटक माहिती उपलब्ध नाही"))
        else {
            p.supporting.forEach { evidenceRows.add("✓ " + it.title to pts(it.points)) }
            p.conflicting.forEach { evidenceRows.add("✗ " + it.title to "⚠ " + pts(it.points)) }
            if (p.neutral.isNotEmpty()) evidenceRows.add(t(lang, "No vote", "कोई वोट नहीं", "मत नाही") to p.neutral.joinToString(", "))
        }
        evidenceRows.add(t(lang, "Pillar agreement", "पिलर सहमति", "पिलर सहमती") to "${p.evidenceAgreementPct}%")
        evidenceRows.add(t(lang, "Rule setup strength", "नियम सेटअप ताकत", "नियम सेटअप ताकद") to (if (p.ruleSetupStrength < 0) unknown else "${p.ruleSetupStrength}/100 " + t(lang, "(heuristic)", "(अनुमानित)", "(अंदाजी)")))
        evidenceRows.add(t(lang, "Regime", "रेजीम", "रेजीम") to u(p.regime))
        evidenceRows.add(t(lang, "Rule bots", "नियम बॉट", "नियम बॉट") to p.botsSummary)
        evidenceRows.add("AI" to when (p.aiStatus) {
            "AVAILABLE" -> t(lang, "AVAILABLE (advisory)", "उपलब्ध (सलाह)", "उपलब्ध (सल्ला)")
            "ERROR" -> "⚠ " + t(lang, "ERROR / no eligible vote", "त्रुटि / कोई योग्य वोट नहीं", "त्रुटी / योग्य मत नाही")
            else -> t(lang, "NOT CONNECTED", "जुड़ा नहीं", "जोडलेले नाही")
        })
        evidenceRows.add(t(lang, "Multi-timeframe", "मल्टी-टाइमफ़्रेम", "मल्टी-टाइमफ्रेम") to
            (if (p.mtfAvailable && p.mtfXau) t(lang, "XAU/USD (OANDA), context only", "XAU/USD (OANDA), सिर्फ़ संदर्भ", "XAU/USD (OANDA), फक्त संदर्भ")
             else if (p.mtfAvailable) t(lang, "PAXG/USDT proxy, context only", "PAXG/USDT प्रॉक्सी, सिर्फ़ संदर्भ", "PAXG/USDT प्रॉक्सी, फक्त संदर्भ")
             else t(lang, "UNAVAILABLE (optional, not in the decision)", "उपलब्ध नहीं (वैकल्पिक, फ़ैसले में नहीं)", "उपलब्ध नाही (पर्यायी, निर्णयात नाही)")))
        evidenceRows.add(t(lang, "Cross-market", "क्रॉस-मार्केट", "क्रॉस-मार्केट") to
            (if (p.crossMarketAvailable) t(lang, "available (context)", "उपलब्ध (संदर्भ)", "उपलब्ध (संदर्भ)") else t(lang, "UNAVAILABLE", "उपलब्ध नहीं", "उपलब्ध नाही")))
        val evidence = Section(t(lang, "Research details", "रिसर्च विवरण", "रिसर्च तपशील"), evidenceRows,
            t(lang, "Factors are the 7 pillars' weighted votes. Agreement is not a probability.",
                "कारक 7 पिलरों के भारित वोट हैं। सहमति संभावना नहीं है।", "घटक म्हणजे 7 पिलरची भारित मते. सहमती म्हणजे शक्यता नाही."), collapsed = true)

        val trust = Section(
            t(lang, "Historical evidence", "पिछला सबूत", "मागील पुरावा"),
            p.metrics.map { m -> Metrics.label(m, lang) to (Metrics.short(m, lang) + " — " + Metrics.definition(m, lang) +
                (if (m.fromMs > 0) " (" + PlanText.utcTime(m.fromMs) + " → " + PlanText.utcTime(m.toMs) + ")" else "")) } + listOf(
                t(lang, "Production engine", "प्रोडक्शन इंजन", "प्रोडक्शन इंजिन") to t(lang, "Rules (not a trained model)", "नियम (प्रशिक्षित मॉडल नहीं)", "नियम (प्रशिक्षित मॉडेल नाही)"),
                t(lang, "Verified record audit", "जाँचा रिकॉर्ड ऑडिट", "तपासलेला रेकॉर्ड ऑडिट") to u(p.ledgerAudit),
                t(lang, "Research model (Shadow Lab)", "रिसर्च मॉडल (Shadow Lab)", "रिसर्च मॉडेल (Shadow Lab)") to u(p.researchModelStatus),
                // V29E: one metrics provider; each statistic keeps its own id, definition, sample size and date range
                t(lang, "Coverage (took a side)", "कवरेज (पक्ष लिया)", "कव्हरेज (बाजू घेतली)") to p.coverage.ifBlank { unknown },
                t(lang, "Records from", "रिकॉर्ड अवधि", "रेकॉर्ड कालावधी") to
                    (if (p.ledgerFirstAtMs > 0) PlanText.utcTime(p.ledgerFirstAtMs) + " → " + PlanText.utcTime(p.ledgerLastAtMs) else unknown),
                t(lang, "Hypothetical economics", "काल्पनिक आर्थिक नतीजा", "काल्पनिक आर्थिक निकाल") to t(lang, "scorecard: close-to-close $, before costs", "स्कोरकार्ड: क्लोज़-से-क्लोज़ $, खर्च से पहले", "स्कोअरकार्ड: क्लोज-ते-क्लोज $, खर्चापूर्वी"),
                t(lang, "Metrics are not comparable", "मीट्रिक की सीधी तुलना नहीं", "मेट्रिकची थेट तुलना नाही") to Metrics.notComparable(lang),
                t(lang, "Realized trading results", "असली ट्रेडिंग नतीजे", "प्रत्यक्ष ट्रेडिंग निकाल") to t(lang, "NOT TRACKED (the app never sees your broker fills)", "ट्रैक नहीं (ऐप आपके ब्रोकर के सौदे नहीं देखता)", "ट्रॅक नाही (ॲप तुमच्या ब्रोकरचे व्यवहार पाहत नाही)"),
                t(lang, "Big % on Home is", "होम पर बड़ा % है", "होमवरचा मोठा % आहे") to when (p.headlineKind) {
                    "HISTORICAL" -> t(lang, "HISTORICAL hit-rate (not a forecast)", "पिछला हिट-रेट (अनुमान नहीं)", "मागील हिट-रेट (अंदाज नाही)")
                    "HEURISTIC" -> t(lang, "HEURISTIC agreement (not a probability)", "अनुमानित सहमति (संभावना नहीं)", "अंदाजी सहमती (शक्यता नाही)")
                    else -> t(lang, "not shown (no call)", "नहीं दिखाया (कोई कॉल नहीं)", "दाखवला नाही (कॉल नाही)")
                },
                t(lang, "Calibrated probability", "कैलिब्रेटेड संभावना", "कॅलिब्रेटेड शक्यता") to when (p.calibration) {
                    "WITHIN_10_PTS" -> t(lang, "not established (bands within 10 pts)", "स्थापित नहीं (बैंड 10 अंक के भीतर)", "स्थापित नाही (बँड 10 अंकांच्या आत)")
                    "UNRELIABLE" -> "⚠ " + t(lang, "UNRELIABLE", "भरोसेमंद नहीं", "विश्वासार्ह नाही") + String.format(Locale.US, " (%.1f pts)", p.calibrationErrorPts)
                    else -> t(lang, "UNAVAILABLE", "उपलब्ध नहीं", "उपलब्ध नाही")
                },
                t(lang, "Proven edge", "साबित बढ़त", "सिद्ध आघाडी") to (if (p.provenEdge) t(lang, "LIVE QUALIFIED (research model)", "LIVE QUALIFIED (रिसर्च मॉडल)", "LIVE QUALIFIED (रिसर्च मॉडेल)") else "⚠ " + t(lang, "NO PROVEN PREDICTIVE EDGE", "कोई साबित बढ़त नहीं", "सिद्ध आघाडी नाही")),
                t(lang, "Uncertainty", "अनिश्चितता", "अनिश्चितता") to u(p.uncertainty)
            ),
            if (p.confidenceInverted) "⚠ " + t(lang, "Calls with higher agreement were right LESS often. Do not trust a high % more.",
                "ज़्यादा सहमति वाली कॉल्स कम बार सही निकलीं। ऊँचे % पर ज़्यादा भरोसा मत करो।", "जास्त सहमतीच्या कॉल्स कमी वेळा बरोबर ठरल्या. जास्त % वर जास्त विश्वास ठेवू नका.") else null
        )
        val audit = Section(
            t(lang, "Audit trail", "ऑडिट ट्रेल", "ऑडिट ट्रेल"),
            listOf(
                t(lang, "Decision ID", "फ़ैसला ID", "निर्णय ID") to (if (p.decisionId == "NOT RECORDED") t(lang, "NOT RECORDED", "दर्ज नहीं", "नोंदवलेले नाही") else p.decisionId),
                t(lang, "Snapshot", "स्नैपशॉट", "स्नॅपशॉट") to u(p.snapshotId),
                t(lang, "Engine • features • model", "इंजन • फ़ीचर • मॉडल", "इंजिन • फीचर • मॉडेल") to "${p.engineVersion} • ${p.featureVersion} • ${u(p.modelVersion)}",
                t(lang, "Ledger", "लेजर", "लेजर") to "${p.ledgerSize} / ${p.ledgerChecked} " + t(lang, "checked", "जाँचे", "तपासले"),
                t(lang, "Latest quote", "आख़िरी भाव", "शेवटचा भाव") to PlanText.utcTime(p.quoteAtMs),
                t(lang, "Newest candle (open)", "नई कैंडल (शुरुआत)", "नवीन कँडल (सुरुवात)") to u(p.candleTime),
                t(lang, "Analysis built", "विश्लेषण बना", "विश्लेषण तयार") to PlanText.utcTime(p.builtAtMs),
                t(lang, "Decision published", "फ़ैसला दर्ज", "निर्णय नोंदवला") to (if (p.publishedAtMs > 0) PlanText.utcTime(p.publishedAtMs) else t(lang, "NOT RECORDED", "दर्ज नहीं", "नोंदवलेले नाही"))
            ),
            collapsed = true
        )
        return listOf(decision, source, whySection, trust, evidence, audit)
    }
}
