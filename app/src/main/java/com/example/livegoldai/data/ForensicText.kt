package com.example.livegoldai.data

import com.example.livegoldai.localization.AppLanguage

/**
 * V35.14 honest forensic wording (owner approval, 7 Nov 2026). Results saved before this version keep their stored text;
 * it is re-worded here when SHOWN, and new results are written with the same wording.
 *  - "confidence N%" is pillar AGREEMENT, not a win chance
 *  - failure tags are rule-based labels written by the verifier, not measured causes; no percentage is shown for them
 *  - "this loss would have been avoided" was hindsight from one failed call, never a tested rule
 */
object ForensicText {
    private fun t(l: AppLanguage, en: String, hi: String, mr: String) = when (l) { AppLanguage.ENGLISH -> en; AppLanguage.HINDI -> hi; AppLanguage.MARATHI -> mr }

    private val CONF = Regex(""",\s*confidence (\d+)%""")
    fun timeline(s: String): String = s.replace(CONF) { ", rule agreement ${it.groupValues[1]}% (not a win chance)" }

    private val TAGS = mapOf(
        "OVERCONFIDENCE" to Triple("HIGH AGREEMENT, WRONG", "ज़्यादा सहमति, फिर भी गलत", "जास्त सहमती, तरीही चूक"),
        "MISSED_CONTRADICTION" to Triple("A PILLAR POINTED THE OTHER WAY", "एक पिलर उल्टी तरफ़ था", "एक पिलर उलट दिशेला होता"),
        "FAKE_MOVE_REVERSAL" to Triple("MOVED RIGHT FIRST, THEN REVERSED", "पहले सही चला, फिर पलटा", "आधी बरोबर, मग उलटले"),
        "STOP_HUNT" to Triple("STOP HIT, THEN CAME TRUE", "स्टॉप लगा, फिर सही हुआ", "स्टॉप लागला, मग बरोबर ठरले"),
        "RANGE_WHIPSAW" to Triple("RANGE WHIPSAW", "रेंज में उलझा", "रेंजमध्ये अडकले"),
        "TIMEFRAME_CONFLICT" to Triple("AGAINST THE HIGHER TIMEFRAME", "बड़े टाइमफ्रेम के उल्टा", "मोठ्या टाइमफ्रेमच्या उलट"),
        "NEWS_SHOCK" to Triple("NEWS SHOCK", "न्यूज़ झटका", "न्यूज धक्का"),
        "INSUFFICIENT_EDGE" to Triple("LOW AGREEMENT", "कम सहमति", "कमी सहमती"),
        "VOLATILITY_EXPANSION" to Triple("VOLATILITY JUMPED", "उतार-चढ़ाव बढ़ा", "चढउतार वाढला")
    )
    fun tag(code: String, l: AppLanguage): String = TAGS[code]?.let { t(l, it.first, it.second, it.third) } ?: code.replace('_', ' ')
    fun tags(codes: Collection<String>, l: AppLanguage) = codes.filter { it != "UNKNOWN" }.joinToString(" · ") { tag(it, l) }
    fun tagsTitle(l: AppLanguage) = t(l, "Possible factors (rule labels, not proven causes)", "संभावित कारण (नियम के लेबल, साबित कारण नहीं)", "संभाव्य कारणे (नियमांची लेबल, सिद्ध कारणे नाहीत)")

    private val OBEYED = Regex("""If the app had obeyed (.+?) \(it pointed the other way\) and shown WAIT, this loss would have been avoided\.""")
    /** a stored counterfactual sentence, re-worded as what it is: hindsight from this one call */
    fun counterfactual(s: String, l: AppLanguage): String {
        OBEYED.find(s)?.let { m -> val who = m.groupValues[1]
            return t(l, "Hindsight: $who pointed the other way. A WAIT on such a signal is not proven to help; it is being tested forward in the shadow lab.",
                "पीछे देखकर: $who उल्टी तरफ़ था। ऐसे सिग्नल पर WAIT से फ़ायदा साबित नहीं; शैडो लैब में आगे की कॉल पर जाँच चल रही है।",
                "मागे पाहून: $who उलट दिशेला होता. अशा सिग्नलवर WAIT फायदेशीर हे सिद्ध नाही; शॅडो लॅबमध्ये पुढील कॉलवर चाचणी सुरू आहे.") }
        if (s.startsWith("If predictions against the higher timeframe were blocked"))
            return t(l, "Hindsight: this call went against the higher timeframe. Blocking such calls is not proven to help.",
                "पीछे देखकर: यह कॉल बड़े टाइमफ्रेम के उल्टा थी। ऐसी कॉल रोकने से फ़ायदा साबित नहीं।",
                "मागे पाहून: ही कॉल मोठ्या टाइमफ्रेमच्या उलट होती. अशा कॉल थांबवणे फायदेशीर हे सिद्ध नाही.")
        if (s.startsWith("No recorded signal warned against this prediction"))
            return t(l, "No pillar pointed the other way at the time.", "उस समय कोई पिलर उल्टी तरफ़ नहीं था।", "त्या वेळी कोणताही पिलर उलट दिशेला नव्हता.")
        return s
    }
    fun counterfactualTitle(l: AppLanguage) = t(l, "Hindsight note", "पीछे देखकर नोट", "मागे पाहून नोंद")
    fun warnedTitle(l: AppLanguage) = t(l, "Pointed the other way (hindsight): ", "उल्टी तरफ़ थे (पीछे देखकर): ", "उलट दिशेला होते (मागे पाहून): ")
}
