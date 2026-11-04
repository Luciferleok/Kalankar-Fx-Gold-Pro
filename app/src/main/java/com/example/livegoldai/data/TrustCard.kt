package com.example.livegoldai.data

import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * V35.5-D Forecast Trust card (owner choice, 4 Nov 2026): one premium summary of the forecast on HOME.
 *
 * Built only from values the analysis already holds (the canonical decision, its frozen scenario, the presentation, the
 * ledger pulse and the feed). Every number carries its KIND, so a vote share is never read as a probability:
 *   RULE AGREEMENT          weighted share of the voting pillars (not a probability)
 *   HISTORICAL HIT-RATE     how often past calls of the same agreement band were right (past data, not today's chance)
 *   CALIBRATED PROBABILITY  shown as NOT AVAILABLE: no model in this app is calibrated on forward data
 *   DATA HEALTH             price freshness, source, spread and the Macro news-window state
 *   EXPERIMENTAL RESEARCH   the Shadow Lab status: never used in this forecast
 * Nothing here changes a decision, a level, the ledger or a model.
 */
object TrustCard {

    enum class Kind(val en: String, val hi: String, val mr: String) {
        RULE_AGREEMENT("RULE AGREEMENT", "नियम सहमति", "नियम सहमती"),
        HISTORICAL_HIT_RATE("HISTORICAL HIT-RATE", "पुराना हिट-रेट", "जुना हिट-रेट"),
        CALIBRATED_PROBABILITY("CALIBRATED PROBABILITY", "कैलिब्रेटेड संभावना", "कॅलिब्रेटेड शक्यता"),
        DATA_HEALTH("DATA HEALTH", "डेटा सेहत", "डेटा आरोग्य"),
        EXPERIMENTAL("EXPERIMENTAL RESEARCH", "प्रायोगिक रिसर्च", "प्रायोगिक रिसर्च")
    }

    data class Metric(val kind: Kind, val value: String, val note: String, val good: Boolean? = null)
    data class Levels(val entry: Double, val stop: Double, val tp1: Double, val tp2: Double, val fixedAt: String, val saved: Boolean)

    data class Card(
        val decision: Signal,
        val status: String,          // QUALIFIED / RESEARCH / NOT ACTIONABLE / WAIT
        val statusNote: String,
        val metrics: List<Metric>,
        val forTitle: String, val forSide: List<String>,
        val againstTitle: String, val againstSide: List<String>,
        val levels: Levels?,
        val expiresAtMs: Long,
        val regime: String,
        val alignment: String,
        val evidence: String,
        val footer: String
    )

    private fun t(l: AppLanguage, en: String, hi: String, mr: String) = when (l) { AppLanguage.ENGLISH -> en; AppLanguage.HINDI -> hi; AppLanguage.MARATHI -> mr }
    private fun pts(x: Double) = String.format(Locale.US, "%+.0f", x)

    fun build(a: GoldAnalysisResult, now: Long, l: AppLanguage): Card {
        val sig = a.overallSignal
        val ts = a.tradeSetup
        val p = a.presentation
        val wait = sig == Signal.WAIT

        val (status, statusNote) = when {
            wait -> "WAIT" to RealityEngine.waitReason(a).second.ifBlank { t(l, "no call this candle", "इस कैंडल पर कोई कॉल नहीं", "या कँडलवर कॉल नाही") }
            ts.planStatus == PlanStamp.ACTIVE -> "QUALIFIED" to t(l, "research model live-qualified and authorised", "रिसर्च मॉडल लाइव-योग्य और मंज़ूर", "रिसर्च मॉडेल लाइव्ह-पात्र आणि मंजूर")
            ts.planStatus == PlanStamp.RESEARCH -> "RESEARCH" to t(l, "unverified research signal, not trade advice", "असत्यापित रिसर्च सिग्नल, ट्रेड सलाह नहीं", "असत्यापित रिसर्च सिग्नल, ट्रेड सल्ला नाही")
            else -> "NOT ACTIONABLE" to ts.planStatus
        }

        val metrics = ArrayList<Metric>()
        // 1. rule agreement (only for a call: a WAIT has no agreement number to trade on)
        val votes = a.groups.count { it.active && it.verdict == sig }
        metrics += Metric(Kind.RULE_AGREEMENT,
            if (wait) t(l, "below the gate", "गेट से नीचे", "गेटखाली") else "${a.agreementPercent.roundToInt()}% · $votes/${a.totalGroups} " + t(l, "pillars", "पिलर", "पिलर"),
            t(l, "weighted vote share, gate 68%: not a probability", "वेटेड वोट हिस्सा, गेट 68%: संभावना नहीं", "वेटेड मत वाटा, गेट 68%: शक्यता नाही"))
        // 2. historical hit-rate of the same agreement band
        metrics += Metric(Kind.HISTORICAL_HIT_RATE,
            when { wait -> t(l, "no call: none", "कॉल नहीं: कुछ नहीं", "कॉल नाही: काही नाही"); p != null && p.hitRatePct >= 0 && p.hitRateN > 0 -> "${p.hitRatePct}% · N=${p.hitRateN}"
                else -> t(l, "not enough history", "पर्याप्त इतिहास नहीं", "पुरेसा इतिहास नाही") },
            t(l, "past calls of this agreement band, sideways excluded: not today's chance", "इसी सहमति बैंड की पुरानी कॉल, साइडवेज़ छोड़कर: आज का मौका नहीं", "याच सहमती बँडच्या जुन्या कॉल, साइडवेज वगळून: आजची शक्यता नाही"))
        // 3. calibrated probability: honestly absent
        metrics += Metric(Kind.CALIBRATED_PROBABILITY, t(l, "NOT AVAILABLE", "उपलब्ध नहीं", "उपलब्ध नाही"),
            t(l, "no model here is calibrated on forward data", "यहाँ कोई मॉडल आगे के डेटा पर कैलिब्रेटेड नहीं", "इथे कोणतेही मॉडेल पुढील डेटावर कॅलिब्रेटेड नाही"), false)
        // 4. data health: freshness, source, spread, Macro window
        val fresh = Freshness.assess(a, now); val age = Freshness.priceAgeMs(a, now)
        val f = a.feed
        val spread = f?.takeIf { it.bid > 0 && it.ask >= it.bid }?.let { " · spread " + String.format(Locale.US, "%.2f", it.ask - it.bid) } ?: ""
        val src = SourceRegistry.identify(f?.source).provider.name.replace('_', ' ')
        val macro = CockpitInsightsBuilder.macroWindowCheck(a).detail.substringBefore(" (").substringBefore(":").trim()
        metrics += Metric(Kind.DATA_HEALTH, "$fresh" + (if (age >= 0) " · ${age / 1000}s" else "") + " · $src$spread",
            t(l, "Macro news window: ", "Macro न्यूज़ विंडो: ", "Macro न्यूज विंडो: ") + macro, fresh == Freshness.LIVE)
        // 5. experimental research (Shadow Lab), never used in this forecast
        metrics += Metric(Kind.EXPERIMENTAL, (p?.researchModelStatus ?: "UNKNOWN").replace('_', ' '),
            t(l, "Shadow Lab model: shown for research, not used in this forecast", "शैडो लैब मॉडल: सिर्फ़ रिसर्च, इस फ़ोरकास्ट में इस्तेमाल नहीं", "शॅडो लॅब मॉडेल: फक्त रिसर्च, या अंदाजात वापर नाही"), p?.provenEdge == true)

        // three strongest factors each way (for a WAIT: up-pointing vs down-pointing pillars)
        val contrib = a.insights?.contributions?.filter { it.verdict != Signal.WAIT } ?: emptyList()
        fun line(title: String, points: Double) = "$title ${pts(points)}"
        val forT: String; val agT: String; val forL: List<String>; val agL: List<String>
        if (!wait && p != null) {
            forT = t(l, "WHY ${sig.name}", "${sig.name} क्यों", "${sig.name} का")
            agT = t(l, "WHY IT MAY FAIL", "क्यों गलत हो सकता है", "का चुकू शकते")
            forL = p.supporting.take(3).map { line(it.title, it.points) }
            agL = (p.conflicting.take(3).map { line(it.title, it.points) } + (a.insights?.warnings ?: emptyList())).take(3)
        } else {
            forT = t(l, "POINTING UP", "ऊपर की ओर", "वरच्या दिशेने")
            agT = t(l, "POINTING DOWN", "नीचे की ओर", "खालच्या दिशेने")
            forL = contrib.filter { it.points > 0 }.sortedByDescending { abs(it.points) }.take(3).map { line(it.title, it.points) }
            agL = contrib.filter { it.points < 0 }.sortedByDescending { abs(it.points) }.take(3).map { line(it.title, it.points) }
        }

        // levels: one frozen scenario, only for a call
        val sc = if (wait) null else ResearchScenario.of(a, now)
        val levels = sc?.let { Levels(it.entry, it.stop, it.tp1, it.tp2,
            if (ts.levelsFixedAtMs > 0L) ResearchScenario.utc(ts.levelsFixedAtMs) else "--", ts.levelsSaved) }

        val ins = a.insights
        val regime = ins?.let { "${it.regime.replace('_', ' ')} · " + t(l, "trend efficiency ", "ट्रेंड एफिशिएंसी ", "ट्रेंड एफिशियन्सी ") + "${it.trendEfficiencyPercent}%" } ?: "--"
        val alignment = a.mtfMatrix?.alignmentSummary?.ifBlank { null } ?: "--"
        val evidence = if (wait) "" else a.pulse?.edgeForCurrent ?: ""
        return Card(sig, status, statusNote, metrics, forT, forL, agT, agL, levels, if (wait) 0L else ts.expiresAtMs, regime, alignment, evidence,
            t(l, "Research record, not financial advice. Past results do not guarantee future results.",
                "रिसर्च रिकॉर्ड, वित्तीय सलाह नहीं। पिछले नतीजे भविष्य की गारंटी नहीं।", "रिसर्च नोंद, आर्थिक सल्ला नाही. मागील निकाल भविष्याची हमी नाहीत."))
    }
}
