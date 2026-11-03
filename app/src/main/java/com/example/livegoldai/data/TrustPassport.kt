package com.example.livegoldai.data

import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import java.util.Locale

/**
 * V34.6: Forecast Trust Passport. One read-only audit sheet for the forecast on screen, built only from values the app
 * already holds (the stamped snapshot, its presentation and the ledger). Anything not known is shown as NOT VERIFIED;
 * nothing is estimated or filled in. It changes no decision, level, ledger entry or model.
 */
object TrustPassport {
    data class Row(val label: String, val value: String, val verified: Boolean = true)

    private fun t(l: AppLanguage, en: String, hi: String, mr: String) = when (l) { AppLanguage.ENGLISH -> en; AppLanguage.HINDI -> hi; AppLanguage.MARATHI -> mr }
    private fun m(x: Double) = String.format(Locale.US, "%,.2f", x)
    private fun nv(l: AppLanguage) = t(l, "NOT VERIFIED", "सत्यापित नहीं", "सत्यापित नाही")

    fun title(l: AppLanguage) = t(l, "TRUST PASSPORT", "ट्रस्ट पासपोर्ट", "ट्रस्ट पासपोर्ट")
    fun footer(l: AppLanguage) = t(l, "Research record, not financial advice. Past results do not guarantee future results.",
        "रिसर्च रिकॉर्ड, वित्तीय सलाह नहीं। पिछले नतीजे भविष्य की गारंटी नहीं।", "रिसर्च नोंद, आर्थिक सल्ला नाही. मागील निकाल भविष्याची हमी नाहीत.")

    fun build(a: GoldAnalysisResult?, state: LedgerState?, now: Long, l: AppLanguage): List<Row> {
        if (a == null) return listOf(Row(t(l, "Forecast", "फ़ोरकास्ट", "अंदाज"), nv(l), false))
        val ts = a.tradeSetup; val p = a.presentation; val f = a.feed
        val rows = ArrayList<Row>()
        fun add(en: String, hi: String, mr: String, v: String?, ok: Boolean = true) =
            rows.add(if (v.isNullOrBlank()) Row(t(l, en, hi, mr), nv(l), false) else Row(t(l, en, hi, mr), v, ok))

        add("Forecast ID", "फ़ोरकास्ट ID", "अंदाज ID", ts.snapshotId)
        add("Timeframe · candle (UTC)", "टाइमफ्रेम · कैंडल (UTC)", "टाइमफ्रेम · कँडल (UTC)", "${a.interval.uppercase()} · ${a.lastUpdated}".takeIf { a.lastUpdated.isNotBlank() })
        add("Source", "स्रोत", "स्रोत", (p?.sourceBadge ?: ts.instrument).ifBlank { ts.dataSource })
        // freshness of the price itself
        val fresh = Freshness.assess(a, now); val age = Freshness.priceAgeMs(a, now)
        val spread = f?.takeIf { it.bid > 0.0 && it.ask >= it.bid }?.let { " · spread ${m(it.ask - it.bid)}" } ?: ""
        add("Price freshness", "भाव कितना ताज़ा", "भाव किती ताजा", if (age < 0) null else "$fresh · ${age / 1000}s" + (if ((f?.quoteTimeMs ?: 0L) > 0L) " (provider time)" else " (download time)") + spread)
        // the decision
        val status = when (ts.planStatus) { PlanStamp.ACTIVE -> "ACTIVE"; PlanStamp.RESEARCH -> t(l, "RESEARCH (not approved)", "रिसर्च (मंज़ूर नहीं)", "रिसर्च (मंजूर नाही)"); else -> ts.planStatus }
        add("Rule direction · status", "नियम दिशा · स्थिति", "नियम दिशा · स्थिती", "${a.overallSignal.name} · $status")
        add("Rule agreement (not a probability)", "नियम सहमति (संभावना नहीं)", "नियम सहमती (शक्यता नाही)", "${a.agreementPercent.toInt()}%")
        // levels
        val sc = ResearchScenario.of(a, now)
        if (a.overallSignal == Signal.WAIT || sc == null) add("Research levels", "रिसर्च लेवल", "रिसर्च लेव्हल", t(l, "none (WAIT)", "कोई नहीं (WAIT)", "नाहीत (WAIT)"))
        else {
            add("Entry · stop", "एंट्री · स्टॉप", "एंट्री · स्टॉप", "${m(sc.entry)} · ${m(sc.stop)}")
            add("TP1 · TP2", "TP1 · TP2", "TP1 · TP2", "${m(sc.tp1)} · ${m(sc.tp2)}")
            add("Levels fixed", "लेवल तय", "लेव्हल निश्चित",
                if (ts.levelsFixedAtMs > 0L) ResearchScenario.utc(ts.levelsFixedAtMs) + (if (ts.levelsSaved) "" else t(l, " · not saved on phone", " · फ़ोन में सेव नहीं", " · फोनमध्ये सेव्ह नाही")) else null,
                ts.levelsFixedAtMs > 0L && ts.levelsSaved)
        }
        add("Window ends", "विंडो खत्म", "विंडो संपते", if (ts.expiresAtMs > 0L) ResearchScenario.utc(ts.expiresAtMs) else null)
        add("Engine · model", "इंजन · मॉडल", "इंजिन · मॉडेल", ts.modelVersion)
        // evidence
        add("Historical evidence", "पुराना सबूत", "जुना पुरावा",
            p?.let { if (it.hitRatePct >= 0 && it.hitRateN > 0) "${it.hitRatePct}% · N=${it.hitRateN} " + t(l, "(similar setups, not a probability)", "(मिलते-जुलते सेटअप, संभावना नहीं)", "(समान सेटअप, शक्यता नाही)") else null })
        add("Evidence for this setup", "इस सेटअप का सबूत", "या सेटअपचा पुरावा", a.pulse?.edgeForCurrent?.ifBlank { null }, a.pulse?.edgeForCurrent?.startsWith("HISTORICAL GROUP EVIDENCE") == true)
        add("Live qualification", "लाइव योग्यता", "लाइव्ह पात्रता", p?.researchModelStatus?.takeIf { it.isNotBlank() && it != "UNKNOWN" }, p?.provenEdge == true)
        p?.conflicting?.takeIf { it.isNotEmpty() }?.let { c -> add("Why it may fail", "क्यों गलत हो सकता है", "का चुकू शकते", c.take(3).joinToString(" · ") { it.title }) }
            ?: add("Why it may fail", "क्यों गलत हो सकता है", "का चुकू शकते", t(l, "no opposing rule recorded; rules can still be wrong", "कोई उल्टा नियम दर्ज नहीं; नियम फिर भी गलत हो सकते हैं", "उलटा नियम नोंदलेला नाही; नियम तरीही चुकू शकतात"))
        // ledger + outcome (same provider, same candle)
        val prov = SourceRegistry.identify(f?.source).provider
        val rec = state?.records?.lastOrNull {
            it.interval.equals(a.interval, true) && it.barId == a.lastUpdated && prov != ProviderId.NONE && SourceRegistry.identify(it.dataSource).provider == prov
        }
        if (rec == null) add("Ledger record", "लेजर रिकॉर्ड", "लेजर नोंद",
            if (ts.recordedByOtherProvider.isNotBlank()) t(l, "this candle is recorded from ${ts.recordedByOtherProvider}", "यह कैंडल ${ts.recordedByOtherProvider} से दर्ज है", "ही कँडल ${ts.recordedByOtherProvider} कडून नोंदली") else null, false)
        else {
            add("Ledger record", "लेजर रिकॉर्ड", "लेजर नोंद", rec.id + " · " + (if (rec.planMethod.isNotEmpty()) "plan check v2" else "v1 only"))
            val res = state.resultOf(rec.id)
            if (res == null) add("Result", "नतीजा", "निकाल", t(l, "pending, checked after ${ResearchScenario.utc(rec.expiresAt)}", "बाकी, ${ResearchScenario.utc(rec.expiresAt)} के बाद जाँच", "बाकी, ${ResearchScenario.utc(rec.expiresAt)} नंतर तपासणी"), false)
            else {
                add("Result v1 (direction)", "नतीजा v1 (दिशा)", "निकाल v1 (दिशा)", res.outcome.name + if (res.sourceMatch == "PROXY") " (checked on a proxy)" else "", res.sourceMatch != "PROXY")
                if (rec.planMethod.isNotEmpty()) add("Result v2 (plan)", "नतीजा v2 (प्लान)", "निकाल v2 (प्लॅन)", res.planOutcome.ifBlank { null })
            }
        }
        return rows
    }
}
