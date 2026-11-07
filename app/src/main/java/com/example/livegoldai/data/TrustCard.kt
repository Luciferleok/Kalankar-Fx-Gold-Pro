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
        val footer: String,
        // V35.5-E: status NOW (re-checked every second), apart from the status at publication (never rewritten)
        val nowStatus: String = "",
        val nowReasons: List<String> = emptyList(),
        val diagnostics: List<Diag> = emptyList(),
        val chips: List<Pair<String, String>> = emptyList(),   // V35.6 collapsed view: (label, value) x 3
        val snapshotAt: String = "",      // when this snapshot (and its status stamp) was made, phone time zone
        val ledgerAt: String = ""         // when the ledger recorded this candle's call ("" = not recorded)
    )
    /** V35.5-E: one factual check; state OK / WARN / FAIL / NA. Never combined into a score. */
    data class Diag(val name: String, val state: String, val detail: String)

    private fun t(l: AppLanguage, en: String, hi: String, mr: String) = when (l) { AppLanguage.ENGLISH -> en; AppLanguage.HINDI -> hi; AppLanguage.MARATHI -> mr }
    private fun pts(x: Double) = String.format(Locale.US, "%+.0f", x)
    /** "15:52 IST" in the phone's time zone, with the zone named */
    fun localTs(ms: Long, tz: java.util.TimeZone = java.util.TimeZone.getDefault()): String =
        java.text.SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = tz }.format(java.util.Date(ms)) + " " +
            OutcomeBrowser.zone(tz, ms)

    /** "OFF", or "NFP@1793971800000 · dollar OBSERVED_COMPLETE (OANDA COMPUTED) · 10Y INCOMPLETE · Macro votes BUY" */
    fun newsLine(a: GoldAnalysisResult, l: AppLanguage, now: Long = System.currentTimeMillis()): String {
        val g = a.groups.firstOrNull { it.key == "macro" }
        val d = MacroEvidenceView.dollar(a).evidence; val y = MacroEvidenceView.tenY(a).evidence
        val id = d.eventId.ifBlank { y.eventId }
        if (id.isBlank() || g == null) return t(l, "OFF (no high-impact USD news within 1 h)", "बंद (1 घंटे में कोई बड़ी USD न्यूज़ नहीं)", "बंद (1 तासात मोठी USD न्यूज नाही)")
        fun one(name: String, e: com.example.livegoldai.model.MacroEvidence) = "$name ${e.status.name}" + if (e.usableInWindow) " (${e.provider} ${e.sourceKind})" else ""
        // V35.5-E: whether Macro took part in THIS decision, and how old its newest completed bar is
        val vote = if (g.active) t(l, "Macro votes ", "Macro वोट ", "Macro मत ") + g.verdict.name + t(l, " (used in decision)", " (फ़ैसले में शामिल)", " (निर्णयात वापर)")
            else t(l, "Macro no vote, no weight (not used in decision)", "Macro का वोट/वज़न नहीं (फ़ैसले में नहीं)", "Macro ला मत/वजन नाही (निर्णयात नाही)")
        val newest = listOf(d, y).filter { it.usableInWindow }.maxOfOrNull { it.barEndMs } ?: 0L
        val age = if (newest > 0L && now >= newest) t(l, " · data ", " · डेटा ", " · डेटा ") + "${(now - newest) / 60_000L}m" + t(l, " old", " पुराना", " जुना") else ""
        val ms = id.substringAfter("@").toLongOrNull()
        val name = id.substringBefore("@") + (ms?.let { " " + java.text.SimpleDateFormat("dd MMM", Locale.US).format(java.util.Date(it)) + " " + localTs(it) } ?: "")
        return "$name · ${one("dollar", d)} · ${one("10Y", y)}$age · $vote"
    }

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

        // V35.5-E: the publication status above is the record; this is whether it still holds NOW (same checks as the
        // one execution policy, ResearchGate), so an old snapshot is never shown as currently actionable
        val fresh = Freshness.assess(a, now)
        val refusals = if (wait) emptyList() else ResearchGate.authorize(a, ts.snapshotId, a.interval, now).refusals
        val nowReasons = refusals.mapNotNull { r -> when (r) {
            ResearchGate.EXPIRED -> if (ts.expiresAtMs > 0L) t(l, "expired ", "समाप्त ", "मुदत संपली ") + "${(now - ts.expiresAtMs).coerceAtLeast(0L) / 60_000L}m" + t(l, " ago", " पहले", " आधी")
                else t(l, "no expiry", "समाप्ति नहीं", "मुदत नाही")
            ResearchGate.DATA_NOT_FRESH -> t(l, "price ", "प्राइस ", "प्राइस ") + fresh
            ResearchGate.PROVIDER_MISMATCH -> t(l, "provider changed since publication", "प्रकाशन के बाद प्रोवाइडर बदला", "प्रकाशनानंतर प्रोव्हायडर बदलला")
            ResearchGate.STOP_TOUCHED -> t(l, "stop touched", "स्टॉप छू गया", "स्टॉप लागला")
            ResearchGate.NO_LIVE_DATA -> t(l, "no live data", "लाइव डेटा नहीं", "लाइव्ह डेटा नाही")
            ResearchGate.INSTRUMENT_NOT_XAU -> t(l, "not XAU/USD spot", "XAU/USD स्पॉट नहीं", "XAU/USD स्पॉट नाही")
            else -> null } }
        val delayedNote = if (!wait && status == "QUALIFIED" && refusals.isEmpty() && fresh == Freshness.DELAYED)
            listOf(t(l, "price DELAYED: check the live price before acting", "प्राइस DELAYED: कुछ करने से पहले लाइव प्राइस देखें", "प्राइस DELAYED: काही करण्यापूर्वी लाइव्ह प्राइस तपासा")) else emptyList()
        val nowStatus = when {
            wait -> "WAIT"
            // V35.6: the execution policy accepts DELAYED data; the card says so instead of a plain ACTIONABLE
            status == "QUALIFIED" && refusals.isEmpty() && fresh == Freshness.DELAYED -> "DELAYED DATA"
            status == "QUALIFIED" && refusals.isEmpty() -> "ACTIONABLE"
            status == "QUALIFIED" -> "NOT ACTIONABLE"
            nowReasons.isNotEmpty() -> "OUTDATED"
            status == "RESEARCH" -> "RESEARCH ONLY"
            else -> "NOT ACTIONABLE"
        }

        val metrics = ArrayList<Metric>()
        // 1. rule agreement (only for a call: a WAIT has no agreement number to trade on)
        val votes = a.groups.count { it.active && it.verdict == sig }
        // V35.13.1: "below the gate" only when the pillars really were below it; a WAIT held for another reason
        // (not proven, news safety, filters) keeps its real agreement, marked as held
        val heldPct = if (wait && RealityEngine.waitReason(a).first != "BELOW_GATE" && a.rawSignal != null && a.rawSignal != Signal.WAIT)
            "${a.agreementPercent.roundToInt()}% " + t(l, "(held)", "(रोका)", "(थांबवले)") else null
        metrics += Metric(Kind.RULE_AGREEMENT,
            if (wait) heldPct ?: t(l, "below the gate", "गेट से नीचे", "गेटखाली") else "${a.agreementPercent.roundToInt()}% · $votes/${a.totalGroups} " + t(l, "pillars", "पिलर", "पिलर"),
            t(l, "weighted vote share, gate 68%: not a probability", "वेटेड वोट हिस्सा, गेट 68%: संभावना नहीं", "वेटेड मत वाटा, गेट 68%: शक्यता नाही"))
        // 2. V35.6 evidence tiers: A = same ledger regime too, B = same timeframe/side/provider/engine (regime mixed),
        //    C = general context (agreement band, everything mixed). The value is the narrowest tier with enough calls.
        fun cov(n: Int, w: Int) = if (w > 0) " · " + t(l, "coverage ", "कवरेज ", "कव्हरेज ") + "${100 * n / w}%" else ""
        val tier = when { wait || p == null -> ""; p.tierAPct >= 0 -> "A"; p.exactHitPct >= 0 -> "B"; else -> "" }
        val tierC = if (p != null && p.hitRatePct >= 0 && p.hitRateN > 0) "${p.hitRatePct}% · N=${p.hitRateN}" else ""
        metrics += Metric(Kind.HISTORICAL_HIT_RATE,
            when {
                wait -> t(l, "no call: none", "कॉल नहीं: कुछ नहीं", "कॉल नाही: काही नाही")
                tier == "A" -> "TIER A · ${p!!.tierAPct}% · N=${p.tierAN}"
                tier == "B" -> "TIER B · ${p!!.exactHitPct}% · N=${p.exactHitN}"
                else -> t(l, "INSUFFICIENT EVIDENCE", "पर्याप्त प्रमाण नहीं", "पुरेसा पुरावा नाही") + " · N=${p?.exactHitN ?: 0}/${EdgeFinder.EXACT_MIN_N}"
            },
            if (wait || p == null) t(l, "Past calls, sideways excluded: not today's chance.", "पुरानी कॉल, साइडवेज़ छोड़कर: आज का मौका नहीं।", "जुन्या कॉल, साइडवेज वगळून: आजची शक्यता नाही.")
            else listOf(
                "A (" + t(l, "same regime", "same रेजीम", "same रेजीम") + "): " + (if (p.tierAScope.isBlank()) t(l, "this candle not in the ledger yet", "यह कैंडल अभी लेजर में नहीं", "ही कँडल अजून लेजरमध्ये नाही")
                    else (if (p.tierAPct >= 0) "${p.tierAPct}%" else t(l, "too few", "बहुत कम", "खूप कमी")) + " · N=${p.tierAN}" + cov(p.tierAN, p.tierAWindows) + " · ${p.tierAScope}"),
                "B (" + t(l, "regime mixed", "रेजीम मिश्रित", "रेजीम मिश्र") + "): " + (if (p.exactHitPct >= 0) "${p.exactHitPct}%" else t(l, "too few", "बहुत कम", "खूप कमी")) +
                    " · N=${p.exactHitN}" + cov(p.exactHitN, p.exactWindows) + " · ${p.exactScope}",
                "C (" + t(l, "GENERAL HISTORICAL CONTEXT: agreement band, all sides/providers/engines, overlapping windows", "सामान्य पुराना संदर्भ: सहमति बैंड, सभी दिशा/प्रोवाइडर/इंजन, ओवरलैपिंग विंडो", "सामान्य जुना संदर्भ: सहमती बँड, सर्व दिशा/प्रोव्हायडर/इंजिन, ओव्हरलॅपिंग विंडो") + "): " + tierC.ifBlank { "--" },
                t(l, "One call per window; outcome: ", "हर विंडो में एक कॉल; नतीजा: ", "प्रत्येक विंडोत एक कॉल; निकाल: ") + EdgeFinder.OUTCOME_RULE + ".",
                t(l, "In-sample past record, not a validated probability: not today's chance.", "पुराना रिकॉर्ड, सत्यापित संभावना नहीं: आज का मौका नहीं।", "जुनी नोंद, सत्यापित शक्यता नाही: आजची शक्यता नाही.")
            ).joinToString(" | "))
        // 3. calibrated probability: honestly absent
        metrics += Metric(Kind.CALIBRATED_PROBABILITY, t(l, "NOT AVAILABLE", "उपलब्ध नहीं", "उपलब्ध नाही"),
            t(l, "no model here is calibrated on forward data", "यहाँ कोई मॉडल आगे के डेटा पर कैलिब्रेटेड नहीं", "इथे कोणतेही मॉडेल पुढील डेटावर कॅलिब्रेटेड नाही"), false)
        // 4. data health: freshness, source, spread, Macro window
        val age = Freshness.priceAgeMs(a, now)
        val f = a.feed
        val spread = f?.takeIf { it.bid > 0 && it.ask >= it.bid }?.let { " · spread " + String.format(Locale.US, "%.2f", it.ask - it.bid) } ?: ""
        val src = SourceRegistry.identify(f?.source).provider.name.replace('_', ' ')
        // V35.5-A: news event identity and Macro evidence from the same typed data SYSTEM shows
        val macro = newsLine(a, l, now)
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
                "रिसर्च रिकॉर्ड, वित्तीय सलाह नहीं। पिछले नतीजे भविष्य की गारंटी नहीं।", "रिसर्च नोंद, आर्थिक सल्ला नाही. मागील निकाल भविष्याची हमी नाहीत."),
            nowStatus, nowReasons + delayedNote, diagnostics(a, now, l, fresh, age, refusals, status),
            listOf(t(l, "AGREEMENT", "सहमति", "सहमती") to (if (wait) heldPct ?: t(l, "below gate", "गेट से नीचे", "गेटखाली") else "${a.agreementPercent.roundToInt()}%"),
                t(l, "EVIDENCE", "प्रमाण", "पुरावा") to when { wait -> "--"; tier == "A" -> "A · ${p!!.tierAPct}%"; tier == "B" -> "B · ${p!!.exactHitPct}%"; else -> "N<${EdgeFinder.EXACT_MIN_N}" },
                t(l, "DATA", "डेटा", "डेटा") to fresh),
            // V35.6: "status" is this snapshot's stamp (re-made on every refresh), not a saved publication: say when it was
            // made, and when the ledger recorded this candle (the ledger keeps the call, not the plan status)
            a.feed?.fetchedAtMs?.takeIf { it > 0L }?.let { localTs(it) } ?: "--",
            p?.ledgerRecordedAtMs?.takeIf { it > 0L }?.let { localTs(it) } ?: "")
    }

    /** V35.5-E: eight factual checks, each on its own; no total, no score, no probability */
    private fun diagnostics(a: GoldAnalysisResult, now: Long, l: AppLanguage, fresh: String, age: Long, refusals: List<String>, published: String): List<Diag> {
        val ts = a.tradeSetup; val p = a.presentation; val wait = a.overallSignal == Signal.WAIT
        val out = ArrayList<Diag>()
        out += Diag(t(l, "Price freshness", "प्राइस ताज़गी", "प्राइस ताजेपणा"), when (fresh) { Freshness.LIVE -> "OK"; Freshness.DELAYED -> "WARN"; Freshness.CLOSED -> "NA"; else -> "FAIL" },
            fresh + if (age >= 0) " · ${age / 1000}s" else "")
        val bar = ShadowLive.barMs(a.lastUpdated); val iv = RealityEngine.intervalMinutes(a.interval) * 60_000L
        out += Diag(t(l, "Completed candle", "पूरी कैंडल", "पूर्ण कँडल"), when {
            a.isSimulatedFallback -> "FAIL"; bar == null -> "FAIL"; fresh == Freshness.CLOSED -> "NA"; now - bar <= 2 * iv -> "OK"; else -> "WARN" },
            if (a.isSimulatedFallback) t(l, "simulated data", "सिम्युलेटेड डेटा", "सिम्युलेटेड डेटा") else (a.lastUpdated.ifBlank { "--" } + " UTC"))
        val inst = SourceRegistry.identify(a.feed?.source).instrument
        out += Diag(t(l, "Provider consistency", "प्रोवाइडर एकरूपता", "प्रोव्हायडर सुसंगती"), when {
            ResearchGate.PROVIDER_MISMATCH in refusals -> "FAIL"; inst != InstrumentId.XAU_USD_SPOT -> "WARN"; else -> "OK" },
            (a.feed?.source ?: "--") + if (inst != InstrumentId.XAU_USD_SPOT) t(l, " (proxy, not XAU/USD spot)", " (प्रॉक्सी, XAU/USD स्पॉट नहीं)", " (प्रॉक्सी, XAU/USD स्पॉट नाही)") else "")
        val d = MacroEvidenceView.dollar(a).evidence; val y = MacroEvidenceView.tenY(a).evidence
        val inWindow = d.eventId.isNotBlank() || y.eventId.isNotBlank()
        out += Diag(t(l, "News evidence", "न्यूज़ प्रमाण", "न्यूज पुरावा"), when {
            !inWindow -> "NA"; d.usableInWindow && y.usableInWindow -> "OK"; else -> "WARN" }, newsLine(a, l, now))
        out += Diag(t(l, "Forecast expiry", "फ़ोरकास्ट समाप्ति", "अंदाज मुदत"), when { wait || ts.expiresAtMs <= 0L -> "NA"; now < ts.expiresAtMs -> "OK"; else -> "FAIL" },
            when { wait -> t(l, "no call", "कॉल नहीं", "कॉल नाही"); ts.expiresAtMs <= 0L -> "--"
                now < ts.expiresAtMs -> t(l, "ends in ", "बाकी ", "उरले ") + "${(ts.expiresAtMs - now) / 60_000L}m"
                else -> t(l, "expired ", "समाप्त ", "मुदत संपली ") + "${(now - ts.expiresAtMs) / 60_000L}m" + t(l, " ago", " पहले", " आधी") })
        out += Diag(t(l, "Frozen levels saved", "फ़्रोज़न लेवल सेव", "गोठवलेले लेव्हल सेव्ह"), when {
            wait -> "NA"; ts.levelsFixedAtMs > 0L && ts.levelsSaved -> "OK"; else -> "WARN" },
            when { wait -> t(l, "no levels on WAIT", "WAIT पर लेवल नहीं", "WAIT वर लेव्हल नाहीत"); ts.levelsFixedAtMs <= 0L -> t(l, "not fixed yet", "अभी तय नहीं", "अजून निश्चित नाही")
                ts.levelsSaved -> t(l, "fixed ", "तय ", "निश्चित ") + ResearchScenario.utc(ts.levelsFixedAtMs); else -> t(l, "fixed but not saved", "तय पर सेव नहीं", "निश्चित पण सेव्ह नाही") })
        out += Diag(t(l, "Historical evidence", "पुराना प्रमाण", "जुना पुरावा"), when {
            wait -> "NA"; p != null && (p.tierAPct >= 0 || p.exactHitPct >= 0) -> "OK"; else -> "WARN" },
            if (wait) "--" else (if (p != null && p.tierAPct >= 0) "TIER A · N=${p.tierAN}" else if (p != null && p.exactHitPct >= 0) "TIER B · N=${p.exactHitN}"
                else "N=${p?.exactHitN ?: 0}/${EdgeFinder.EXACT_MIN_N}") + t(l, " decided, one per window", " तय कॉल, हर विंडो में एक", " ठरलेल्या कॉल, प्रत्येक विंडोत एक"))
        out += Diag(t(l, "Research authorization", "रिसर्च अनुमति", "रिसर्च परवानगी"), when {
            wait -> "NA"; published == "QUALIFIED" && refusals.isEmpty() -> "OK"; published == "QUALIFIED" -> "FAIL"; else -> "WARN" },
            when { wait -> "--"; published == "QUALIFIED" && refusals.isEmpty() -> t(l, "authorized now", "अभी अनुमति", "आता परवानगी")
                published == "QUALIFIED" -> t(l, "not now: ", "अभी नहीं: ", "आता नाही: ") + refusals.joinToString(", ")
                else -> t(l, "research only: model not live-qualified", "सिर्फ़ रिसर्च: मॉडल लाइव-योग्य नहीं", "फक्त रिसर्च: मॉडेल लाइव्ह-पात्र नाही") })
        return out
    }
}
