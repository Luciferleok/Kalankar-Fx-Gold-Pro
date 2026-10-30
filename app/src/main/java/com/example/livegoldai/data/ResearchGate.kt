package com.example.livegoldai.data

import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * V33.1 RESEARCH GATE: the one place that decides whether a plan may be linked to a position size.
 *
 * Called by the ViewModel (before the calculator opens), by the screen (before it draws the calculator) and by the
 * calculator itself (it needs a [LotGrant] to exist at all). Hiding buttons is only a fourth layer.
 * A grant is tied to ONE immutable snapshot: a newer refresh, another timeframe or another provider voids it.
 * Nothing here changes how a plan is made; it only reads the stamp PlanStamp already wrote.
 */
object ResearchGate {

    // refusal codes (stable, tested; shown to the user through [refusalText])
    const val NO_SNAPSHOT = "NO_SNAPSHOT"
    const val PLAN_NOT_ACTIVE = "PLAN_NOT_ACTIVE"
    const val SNAPSHOT_MISMATCH = "SNAPSHOT_MISMATCH"
    const val TIMEFRAME_MISMATCH = "TIMEFRAME_MISMATCH"
    const val PROVIDER_MISMATCH = "PROVIDER_MISMATCH"
    const val INSTRUMENT_NOT_XAU = "INSTRUMENT_NOT_XAU"
    const val EXPIRED = "EXPIRED"
    const val STOP_TOUCHED = "STOP_TOUCHED"
    const val DATA_NOT_FRESH = "DATA_NOT_FRESH"
    const val NO_LIVE_DATA = "NO_LIVE_DATA"
    const val NOT_QUALIFIED = "NOT_QUALIFIED"
    const val NO_SIDE = "NO_SIDE"
    const val INVALID_LEVELS = "INVALID_LEVELS"

    /** Everything the calculator may use, copied from the authorized snapshot (it never reads the live state). */
    data class LotGrant(
        val snapshotId: String, val interval: String, val instrument: String, val dataSource: String,
        val signal: Signal, val entry: Double, val stop: Double, val tp1: Double, val tp2: Double,
        val stopPips: Double, val price: Double, val expiresAtMs: Long, val grantedAtMs: Long
    )

    data class Decision(val grant: LotGrant?, val refusals: List<String>) {
        val allowed: Boolean get() = grant != null && refusals.isEmpty()
    }

    /**
     * @param requestSnapshotId the snapshot the user was LOOKING AT when they asked (from the card they tapped)
     * @param requestInterval the timeframe selected on screen
     */
    fun authorize(a: GoldAnalysisResult?, requestSnapshotId: String?, requestInterval: String?, now: Long): Decision {
        if (a == null) return Decision(null, listOf(NO_SNAPSHOT))
        val t = a.tradeSetup
        val r = ArrayList<String>()
        if (t.planStatus != PlanStamp.ACTIVE) r += PLAN_NOT_ACTIVE
        if (requestSnapshotId.isNullOrBlank() || t.snapshotId != requestSnapshotId) r += SNAPSHOT_MISMATCH
        if (requestInterval.isNullOrBlank() || !a.interval.equals(requestInterval, true)) r += TIMEFRAME_MISMATCH
        val src = a.feed?.source
        if (t.dataSource != (src ?: "UNKNOWN") || t.instrument != SourceRegistry.badge(src)) r += PROVIDER_MISMATCH
        if (SourceRegistry.identify(src).instrument != InstrumentId.XAU_USD_SPOT) r += INSTRUMENT_NOT_XAU
        if (t.expiresAtMs <= 0L || now >= t.expiresAtMs) r += EXPIRED
        if (t.stopTouched.isNotEmpty()) r += STOP_TOUCHED
        if (a.isSimulatedFallback || a.feed?.isLive != true) r += NO_LIVE_DATA
        val fresh = Freshness.assess(a, now)
        if (fresh != Freshness.LIVE && fresh != Freshness.DELAYED) r += DATA_NOT_FRESH
        // qualification is read twice on purpose: the stamp (ACTIVE) and the presentation built from the same snapshot
        if (a.presentation?.provenEdge != true) r += NOT_QUALIFIED
        if (a.overallSignal == Signal.WAIT || t.signal != a.overallSignal) r += NO_SIDE
        if (t.entryPrice <= 0.0 || t.stopLoss <= 0.0 || abs(t.entryPrice - t.stopLoss) <= 1e-9 || t.stopLossPips <= 0.0 ||
            RiskMath.rewardPerRisk(t.entryPrice, t.stopLoss, t.takeProfit1, t.signal).isNaN()) r += INVALID_LEVELS
        if (r.isNotEmpty()) return Decision(null, r)
        return Decision(LotGrant(t.snapshotId, a.interval, t.instrument, t.dataSource, t.signal, t.entryPrice, t.stopLoss,
            t.takeProfit1, t.takeProfit2, t.stopLossPips, a.currentPrice, t.expiresAtMs, now), emptyList())
    }

    /**
     * V33.1R P0-A: the ONE policy for every execution-linked feature (MT4/MT5 command, copyable order, lot size, news
     * straddle, ACTIVE badge). True only when the current snapshot itself is an authorized ACTIVE plan.
     */
    fun executionAllowed(a: GoldAnalysisResult?, now: Long): Boolean =
        a != null && authorize(a, a.tradeSetup.snapshotId, a.interval, now).allowed

    /** A grant is used only while the snapshot it came from is still the current, still-authorized one. */
    fun stillValid(g: LotGrant?, a: GoldAnalysisResult?, now: Long): Boolean =
        g != null && authorize(a, g.snapshotId, g.interval, now).allowed

    /** Hypothetical result at a target for a given cash risk: risk × (reward / risk) of THIS plan. null when not computable. */
    fun hypotheticalAtTarget(g: LotGrant, riskCash: Double, target: Double): Double? {
        val rr = RiskMath.rewardPerRisk(g.entry, g.stop, target, g.signal)
        return if (rr.isNaN() || riskCash <= 0.0) null else riskCash * rr
    }

    private fun t(l: AppLanguage, en: String, hi: String, mr: String) = when (l) { AppLanguage.ENGLISH -> en; AppLanguage.HINDI -> hi; AppLanguage.MARATHI -> mr }

    fun refusalTitle(l: AppLanguage) = t(l, "Position size not available", "पोज़िशन साइज़ उपलब्ध नहीं", "पोझिशन साइज उपलब्ध नाही")

    fun refusalText(codes: List<String>, l: AppLanguage): List<String> = codes.map { c ->
        when (c) {
            NO_SNAPSHOT -> t(l, "No market snapshot yet.", "अभी कोई मार्केट स्नैपशॉट नहीं।", "अजून मार्केट स्नॅपशॉट नाही.")
            PLAN_NOT_ACTIVE -> t(l, "This is a research signal, not an approved ACTIVE plan.", "यह रिसर्च सिग्नल है, मंज़ूर ACTIVE प्लान नहीं।", "हा रिसर्च सिग्नल आहे, मंजूर ACTIVE प्लॅन नाही.")
            SNAPSHOT_MISMATCH -> t(l, "The screen showed an older snapshot; data has refreshed.", "स्क्रीन पर पुराना स्नैपशॉट था; डेटा बदल गया।", "स्क्रीनवर जुना स्नॅपशॉट होता; डेटा बदलला.")
            TIMEFRAME_MISMATCH -> t(l, "The plan is for a different timeframe.", "प्लान दूसरे टाइमफ्रेम का है।", "प्लॅन दुसऱ्या टाइमफ्रेमचा आहे.")
            PROVIDER_MISMATCH -> t(l, "The plan's data source differs from the current feed.", "प्लान का डेटा स्रोत मौजूदा फ़ीड से अलग है।", "प्लॅनचा डेटा स्रोत सध्याच्या फीडपेक्षा वेगळा आहे.")
            INSTRUMENT_NOT_XAU -> t(l, "The data is not XAU/USD spot.", "डेटा XAU/USD स्पॉट नहीं है।", "डेटा XAU/USD स्पॉट नाही.")
            EXPIRED -> t(l, "The plan's horizon has ended.", "प्लान का समय खत्म हो गया।", "प्लॅनची वेळ संपली.")
            STOP_TOUCHED -> t(l, "The plan's stop was already touched.", "प्लान का स्टॉप पहले ही छू गया।", "प्लॅनचा स्टॉप आधीच लागला.")
            DATA_NOT_FRESH -> t(l, "Market data is not fresh (stale, offline or closed).", "मार्केट डेटा ताज़ा नहीं (पुराना, ऑफ़लाइन या बंद)।", "मार्केट डेटा ताजा नाही (जुना, ऑफलाइन किंवा बंद).")
            NO_LIVE_DATA -> t(l, "No live data.", "लाइव डेटा नहीं।", "लाइव्ह डेटा नाही.")
            NOT_QUALIFIED -> t(l, "No live-qualified model for this timeframe and data source.", "इस टाइमफ्रेम और डेटा स्रोत पर कोई लाइव-क्वालिफ़ाइड मॉडल नहीं।", "या टाइमफ्रेम व डेटा स्रोतावर लाइव्ह-क्वालिफाइड मॉडेल नाही.")
            NO_SIDE -> t(l, "The plan has no BUY/SELL side.", "प्लान में BUY/SELL पक्ष नहीं।", "प्लॅनमध्ये BUY/SELL बाजू नाही.")
            INVALID_LEVELS -> t(l, "Entry, stop or target levels are not valid.", "एंट्री, स्टॉप या टारगेट लेवल सही नहीं।", "एंट्री, स्टॉप किंवा टार्गेट लेव्हल योग्य नाहीत.")
            else -> c
        }
    }
}

/**
 * V33.1 HONEST LABELS for the TOOLS cards. Each metric is called what it is:
 *  - rule agreement: share of rule votes (never a probability or confidence)
 *  - directional hit-rate: checked calls whose close-to-close direction matched (never a "win rate": no costs, no trades)
 *  - research levels: hypothetical, never an order
 */
object HonestText {
    private fun t(l: AppLanguage, en: String, hi: String, mr: String) = when (l) { AppLanguage.ENGLISH -> en; AppLanguage.HINDI -> hi; AppLanguage.MARATHI -> mr }

    /**
     * The app's one "shown score" (RealityEngine: calibrated similar-setup hit-rate when its confidence band has
     * enough checked records, otherwise the raw rule agreement). These name WHICH of the two it is, with N.
     */
    fun scoreIsHitRate(a: GoldAnalysisResult): Boolean =
        RealityEngine.calibratedConfidence(a.learning, a.agreementPercent.roundToInt()) != null
    fun scoreTag(a: GoldAnalysisResult, l: AppLanguage) =
        if (scoreIsHitRate(a)) t(l, "HIT-RATE · SIMILAR SETUPS", "हिट-रेट · समान सेटअप", "हिट-रेट · समान सेटअप") else ruleAgreementTag(l)
    fun scoreValue(a: GoldAnalysisResult): String {
        val raw = a.agreementPercent.roundToInt()
        val cal = RealityEngine.calibratedConfidence(a.learning, raw)
        return if (cal != null) "$cal% · N=${RealityEngine.bandRecord(a.learning, raw)?.decided ?: 0}" else "$raw%"
    }
    fun scoreShort(a: GoldAnalysisResult, l: AppLanguage): String =
        if (scoreIsHitRate(a)) scoreValue(a) + t(l, " similar-setup hit-rate · not a probability", " समान सेटअप हिट-रेट · संभावना नहीं", " समान सेटअप हिट-रेट · शक्यता नाही")
        else ruleAgreement(a.agreementPercent.roundToInt(), l)

    fun ruleAgreement(pct: Int, l: AppLanguage) = t(l, "$pct% rule agreement · not a probability", "$pct% नियम सहमति · संभावना नहीं", "$pct% नियम सहमती · शक्यता नाही")
    fun ruleAgreementTag(l: AppLanguage) = t(l, "RULE AGREEMENT", "नियम सहमति", "नियम सहमती")
    fun modelScore(pct: Int, l: AppLanguage) = t(l, "SCORE $pct · uncalibrated", "स्कोर $pct · अकैलिब्रेटेड", "स्कोअर $pct · अकॅलिब्रेटेड")

    /** Title of the 3-part bar: past outcomes of checked calls (fromHistory) or the current rule vote split. */
    fun splitTitle(fromHistory: Boolean, l: AppLanguage) =
        if (fromHistory) t(l, "PAST OUTCOMES OF CHECKED CALLS · not a probability", "जाँची गई कॉल के पिछले नतीजे · संभावना नहीं", "तपासलेल्या कॉलचे मागील निकाल · शक्यता नाही")
        else t(l, "RULE VOTE SPLIT · not a probability", "नियम वोट बँटवारा · संभावना नहीं", "नियम मत विभागणी · शक्यता नाही")
    fun splitUp(pct: Int, fromHistory: Boolean, l: AppLanguage) =
        if (fromHistory) t(l, "$pct% went up", "$pct% ऊपर गए", "$pct% वर गेले") else t(l, "$pct% of votes up", "$pct% वोट ऊपर", "$pct% मते वर")

    fun hitRateTitle(tf: String, l: AppLanguage) = t(l, "DIRECTIONAL HIT-RATE ($tf)", "दिशा हिट-रेट ($tf)", "दिशा हिट-रेट ($tf)")
    fun hitRateLine(pct: Int, n: Int, l: AppLanguage) = t(l, "$pct% directional hit-rate (N=$n) · before costs, all providers", "$pct% दिशा हिट-रेट (N=$n) · लागत से पहले, सभी स्रोत", "$pct% दिशा हिट-रेट (N=$n) · खर्चापूर्वी, सर्व स्रोत")
    fun botHitRateHeader(l: AppLanguage) = t(l, "Hit-rates below come from checked predictions (direction only, before costs)", "नीचे के हिट-रेट जाँची गई प्रेडिक्शन से हैं (सिर्फ़ दिशा, लागत से पहले)", "खालील हिट-रेट तपासलेल्या अंदाजांवरून (फक्त दिशा, खर्चापूर्वी)")

    /** Headline over a plan: an approved plan, or what the rules lean to (research). */
    fun planHeadline(actionable: Boolean, l: AppLanguage) =
        if (actionable) t(l, "ACTIVE PLAN", "सक्रिय प्लान", "सक्रिय प्लॅन") else t(l, "RULES LEAN · RESEARCH", "नियमों का झुकाव · रिसर्च", "नियमांचा कल · रिसर्च")
    fun lotButton(actionable: Boolean, l: AppLanguage) =
        if (actionable) t(l, "Position size for this ACTIVE plan", "इस ACTIVE प्लान का पोज़िशन साइज़", "या ACTIVE प्लॅनचा पोझिशन साइज")
        else t(l, "Position size: ACTIVE plan only", "पोज़िशन साइज़: सिर्फ़ ACTIVE प्लान", "पोझिशन साइज: फक्त ACTIVE प्लॅन")
    fun lotResultTitle(l: AppLanguage) = t(l, "POSITION SIZE AT YOUR RISK %", "आपके रिस्क % पर पोज़िशन साइज़", "तुमच्या रिस्क % वर पोझिशन साइज")
    fun hypotheticalNote(l: AppLanguage) = t(l, "Hypothetical, before costs (spread, slippage, swap). Not a promise.", "काल्पनिक, लागत से पहले (स्प्रेड, स्लिपेज, स्वैप)। वादा नहीं।", "काल्पनिक, खर्चापूर्वी (स्प्रेड, स्लिपेज, स्वॅप). वचन नाही.")

    /** Text copied to the clipboard: a research scenario is never formatted as an order. */
    fun shareHeader(actionable: Boolean) =
        if (actionable) "KALANKAR FX GOLD — ACTIVE PLAN (approved by the app's gate; not financial advice)"
        else "KALANKAR FX GOLD — RESEARCH SCENARIO (unverified, NOT a trade instruction)"

    /** Rule bot status when its own conditions are met but the plan is not authorized. */
    fun botResearchStatus(side: String, l: AppLanguage) = t(l, "RULE CONDITIONS MET ($side) · RESEARCH, not approved", "नियम शर्तें पूरी ($side) · रिसर्च, मंज़ूर नहीं", "नियम अटी पूर्ण ($side) · रिसर्च, मंजूर नाही")
    fun executionLocked(l: AppLanguage) = t(l, "No order command: available only for an authorized ACTIVE plan. Levels below are research.",
        "कोई ऑर्डर कमांड नहीं: सिर्फ़ अधिकृत ACTIVE प्लान के लिए। नीचे के लेवल रिसर्च हैं।", "ऑर्डर कमांड नाही: फक्त अधिकृत ACTIVE प्लॅनसाठी. खालील लेव्हल रिसर्च आहेत.")
    fun researchEntryLabel(l: AppLanguage) = t(l, "🎯 RESEARCH ENTRY", "🎯 रिसर्च एंट्री", "🎯 रिसर्च एंट्री")

    // ---- news: no armed orders, no invented direction or size
    fun newsGuard(l: AppLanguage) = t(l, "🛡️ High-impact event: spreads widen and slippage is likely. The app arms no orders; any level shown is research.",
        "🛡️ हाई-इम्पैक्ट इवेंट: स्प्रेड बढ़ता है और स्लिपेज संभव है। ऐप कोई ऑर्डर तैयार नहीं करता; दिखाए लेवल रिसर्च हैं।",
        "🛡️ हाय-इम्पॅक्ट इव्हेंट: स्प्रेड वाढतो व स्लिपेज शक्य. ॲप कोणताही ऑर्डर तयार करत नाही; दाखवलेले लेव्हल रिसर्च आहेत.")
    fun newsPlanButton(l: AppLanguage) = t(l, "NEWS RISK LEVELS", "न्यूज़ रिस्क लेवल", "न्यूज रिस्क लेव्हल")
    fun newsRangeHeader(bufferPips: Double, l: AppLanguage) = t(l, "PRE-NEWS RANGE EDGES · research, not orders (spread buffer +${bufferPips}p)",
        "न्यूज़ से पहले रेंज की सीमाएँ · रिसर्च, ऑर्डर नहीं (स्प्रेड बफर +${bufferPips}p)", "न्यूजपूर्वी रेंजच्या कडा · रिसर्च, ऑर्डर नाही (स्प्रेड बफर +${bufferPips}p)")
    fun upperEdge(l: AppLanguage) = t(l, "UPPER RANGE EDGE", "ऊपरी रेंज सीमा", "वरची रेंज कडा")
    fun lowerEdge(l: AppLanguage) = t(l, "LOWER RANGE EDGE", "निचली रेंज सीमा", "खालची रेंज कडा")
    fun newsDirectionTitle(l: AppLanguage) = t(l, "BEFORE THE RELEASE: DIRECTION NOT KNOWN", "रिलीज़ से पहले: दिशा पता नहीं", "प्रसिद्धीपूर्वी: दिशा माहीत नाही")
    fun newsDirectionChip() = "PRE-BIAS: NOT MEASURED"
    fun newsDirectionBody(l: AppLanguage) = t(l, "The direction of a news move is not known before the number is out, and this app has no measured record of news moves. The levels below are the current range edges, for research only.",
        "नंबर आने से पहले न्यूज़ मूव की दिशा पता नहीं होती, और इस ऐप के पास न्यूज़ मूव का कोई मापा रिकॉर्ड नहीं। नीचे के लेवल अभी की रेंज की सीमाएँ हैं, सिर्फ़ रिसर्च के लिए।",
        "आकडा येण्यापूर्वी न्यूज मूव्हची दिशा माहीत नसते, आणि या ॲपकडे न्यूज मूव्हचा मोजलेला रेकॉर्ड नाही. खालील लेव्हल सध्याच्या रेंजच्या कडा आहेत, फक्त रिसर्चसाठी.")

    fun nextCandleTitle(l: AppLanguage) = t(l, "NEXT-CANDLE RULE LEAN · unvalidated, no track record", "अगली कैंडल पर नियम झुकाव · अप्रमाणित, कोई रिकॉर्ड नहीं", "पुढील कँडलवर नियम कल · अप्रमाणित, रेकॉर्ड नाही")
    fun noTrackRecord(l: AppLanguage) = t(l, "No measured track record in this app", "इस ऐप में कोई मापा हुआ रिकॉर्ड नहीं", "या ॲपमध्ये मोजलेला रेकॉर्ड नाही")
}
