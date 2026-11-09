package com.example.livegoldai.data

import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import kotlin.math.roundToInt

/**
 * V35.11 one-line verdict (owner, 5 Nov 2026: "clear and right prediction dikhe"). The first thing on HOME, in plain
 * words: the canonical decision, the ONE main reason, and what to look at next. Built from the same analysis as every
 * other card (no new numbers): overallSignal, RealityEngine.waitReason, the active pillar votes, Freshness, PlanStamp,
 * the timeframe's own Correct / Wrong record and the next candle time. Display only.
 */
object OneLineVerdict {
    data class Verdict(val word: String, val signal: Signal, val headline: String, val detail: String, val warn: Boolean)

    private fun t(l: AppLanguage, en: String, hi: String, mr: String) = when (l) { AppLanguage.ENGLISH -> en; AppLanguage.HINDI -> hi; AppLanguage.MARATHI -> mr }
    const val MIN_RECORD_N = 20
    const val REPLAY_ONLY = "REPLAY ONLY"

    fun build(a: GoldAnalysisResult, now: Long, l: AppLanguage): Verdict {
        val sig = a.overallSignal
        val fresh = Freshness.assess(a, now)
        val tf = a.interval.uppercase()
        val next = PlanText.nextReviewMs(a.interval, now)
        val nextAt = if (next > 0L) TrustCard.localTs(next) else "--"
        val audit = a.timeframeAudit
        val record = when {
            // the engine's own candle replay ("REPLAY ONLY") is not the Correct / Wrong record: never shown as one
            audit == null || audit.totalSignalsTested == 0 || audit.aiEngineLearningStatus == REPLAY_ONLY -> t(l, "$tf record: no checked call yet", "$tf रिकॉर्ड: अभी कोई जाँची कॉल नहीं", "$tf नोंद: अजून तपासलेली कॉल नाही")
            audit.totalSignalsTested < MIN_RECORD_N -> t(l, "$tf record ${audit.winCount}/${audit.totalSignalsTested} correct (too few to judge)", "$tf रिकॉर्ड ${audit.winCount}/${audit.totalSignalsTested} सही (अभी बहुत कम)", "$tf नोंद ${audit.winCount}/${audit.totalSignalsTested} बरोबर (अजून खूप कमी)")
            else -> t(l, "$tf record ${audit.winRatePercent}% correct (N=${audit.totalSignalsTested}, past, not a promise)", "$tf रिकॉर्ड ${audit.winRatePercent}% सही (N=${audit.totalSignalsTested}, पुराना, वादा नहीं)", "$tf नोंद ${audit.winRatePercent}% बरोबर (N=${audit.totalSignalsTested}, जुनी, खात्री नाही)")
        }
        // data first: a stale or closed price never gets a confident line
        val dataLine = when (fresh) {
            Freshness.LIVE -> null
            Freshness.DELAYED -> t(l, "price DELAYED", "प्राइस DELAYED", "प्राइस DELAYED")
            Freshness.CLOSED -> t(l, "market CLOSED", "मार्केट बंद", "मार्केट बंद")
            else -> t(l, "price $fresh", "प्राइस $fresh", "प्राइस $fresh")
        }
        if (sig == Signal.WAIT) {
            val (code, _) = RealityEngine.waitReason(a)
            val ins = a.insights
            val reason = when (code) {
                "BELOW_GATE" -> ins?.let { val top = maxOf(it.bullishPoints, kotlin.math.abs(it.bearishPoints)).roundToInt(); val g = it.gatePercent.roundToInt()
                    t(l, "no side is strong enough ($top% of $g% needed)", "कोई तरफ़ मज़बूत नहीं ($top%, चाहिए $g%)", "कोणतीही बाजू पुरेशी मजबूत नाही ($top%, हवे $g%)") }
                    ?: t(l, "no side is strong enough", "कोई तरफ़ मज़बूत नहीं", "कोणतीही बाजू पुरेशी मजबूत नाही")
                "RANGE" -> t(l, "sideways market, signals unreliable", "साइडवेज़ मार्केट, सिग्नल भरोसेमंद नहीं", "साइडवेज मार्केट, सिग्नल विश्वासार्ह नाहीत")
                "DATA" -> t(l, "price data check failed", "प्राइस डेटा जाँच फ़ेल", "प्राइस डेटा तपासणी अयशस्वी")
                "NO_KEY" -> t(l, "no data key saved (Settings)", "डेटा key सेव नहीं (Settings)", "डेटा key सेव्ह नाही (Settings)")
                "SOURCE" -> t(l, "data source just switched", "डेटा स्रोत अभी बदला", "डेटा स्रोत आत्ता बदलला")
                "UNPROVEN" -> a.proven?.let { pv -> val s = pv.side.name
                    t(l, "$s not validated yet (this setup ${pv.correct}/${pv.n} correct, minimum ${pv.minN} at ${pv.minPct}%)",
                        "$s अभी जाँचा-परखा नहीं (इस सेटअप के ${pv.n} में ${pv.correct} सही, कम से कम ${pv.minN} पर ${pv.minPct}%)",
                        "$s अजून पडताळलेला नाही (या सेटअपच्या ${pv.n} पैकी ${pv.correct} बरोबर, किमान ${pv.minN} वर ${pv.minPct}%)") }
                    ?: t(l, "setup not validated yet", "सेटअप अभी जाँचा-परखा नहीं", "सेटअप अजून पडताळलेला नाही")
                "NEWS" -> when (a.newsMode?.hold) {
                    "SPIKE_FREEZE" -> t(l, "news in under 15 min, pillar ${a.rawSignal?.name ?: "call"} held", "15 मिनट से कम में न्यूज़, पिलर का ${a.rawSignal?.name ?: "कॉल"} रोका", "15 मिनिटांच्या आत न्यूज, पिलरचा ${a.rawSignal?.name ?: "कॉल"} थांबवला")
                    "FIRST_BAR" -> t(l, "news just out, waiting for the first closed candle", "न्यूज़ अभी आई, पहली बंद कैंडल का इंतज़ार", "न्यूज आत्ताच आली, पहिल्या बंद कँडलची वाट")
                    "NO_BASELINE" -> t(l, "news reaction cannot be measured (no candle before the release)", "न्यूज़ रिएक्शन नापा नहीं जा सकता (रिलीज़ से पहले की कैंडल नहीं)", "न्यूज रिअॅक्शन मोजता येत नाही (रिलीजपूर्वीची कँडल नाही)")
                    "AGAINST" -> t(l, "news reaction goes against the pillar ${a.rawSignal?.name ?: "call"}", "न्यूज़ रिएक्शन पिलर के ${a.rawSignal?.name ?: "कॉल"} के उलटा", "न्यूज रिअॅक्शन पिलरच्या ${a.rawSignal?.name ?: "कॉल"} च्या उलट")
                    else -> t(l, "news reaction too small to confirm the pillar ${a.rawSignal?.name ?: "call"}", "न्यूज़ रिएक्शन पिलर के ${a.rawSignal?.name ?: "कॉल"} की पुष्टि के लिए बहुत छोटा", "न्यूज रिअॅक्शन पिलरच्या ${a.rawSignal?.name ?: "कॉल"} च्या पुष्टीसाठी खूप लहान")
                }
                "LEARNED" -> t(l, "a learned filter blocked it", "सीखे फ़िल्टर ने रोका", "शिकलेल्या फिल्टरने थांबवले")
                else -> t(l, "engine safety rule", "इंजन सुरक्षा नियम", "इंजिन सुरक्षा नियम")
            }
            return Verdict("WAIT", sig, t(l, "WAIT: ", "WAIT: ", "WAIT: ") + reason,
                listOfNotNull(dataLine, t(l, "next check $nextAt", "अगली जाँच $nextAt", "पुढील तपासणी $nextAt"), record).joinToString(" · "), dataLine != null || code in setOf("DATA", "NO_KEY", "SOURCE", "NEWS"))
        }
        val votes = a.groups.count { it.active && it.verdict == sig }
        val voting = a.groups.count { it.active }
        val ts = a.tradeSetup
        val status = when {
            ResearchGate.executionAllowed(a, now) -> t(l, "qualified", "क्वालिफ़ाइड", "पात्र")
            ts.planStatus == PlanStamp.EXPIRED || (ts.expiresAtMs in 1..now) -> t(l, "expired", "समाप्त", "मुदत संपली")
            else -> t(l, "research only, not a trade", "सिर्फ़ रिसर्च, ट्रेड नहीं", "फक्त रिसर्च, ट्रेड नाही")
        }
        val dir = if (sig == Signal.BUY) t(l, "up", "ऊपर", "वर") else t(l, "down", "नीचे", "खाली")
        val until = if (ts.expiresAtMs > now) t(l, "valid until ", "मान्य ", "वैध ") + TrustCard.localTs(ts.expiresAtMs) else null
        return Verdict(sig.name, sig, "${sig.name} ($status): " + t(l, "$votes of $voting pillars point $dir", "$voting में से $votes पिलर $dir", "$voting पैकी $votes पिलर $dir"),
            listOfNotNull(dataLine, until, a.proven?.takeIf { it.proven }?.let { pv ->
                t(l, "minimum sample met: ${pv.correct}/${pv.n} correct (95% range ${pv.lo}-${pv.hi}%, not proof of an edge)", "न्यूनतम सैंपल पूरा: ${pv.n} में ${pv.correct} सही (95% रेंज ${pv.lo}-${pv.hi}%, बढ़त का सबूत नहीं)", "किमान नमुना पूर्ण: ${pv.n} पैकी ${pv.correct} बरोबर (95% रेंज ${pv.lo}-${pv.hi}%, आघाडीचा पुरावा नाही)") } ?: record).joinToString(" · "), dataLine != null || status != t(l, "qualified", "क्वालिफ़ाइड", "पात्र"))
    }
}
