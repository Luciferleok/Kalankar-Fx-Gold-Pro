package com.example.livegoldai.data

import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * V33.1R P0-B: ONE structured research scenario per snapshot.
 *
 * Root cause of the different levels on one screen (screenshots 70, 76): TechnicalEngine builds two level sets from
 * the same snapshot. The written plan (nextPrediction.whatToDo / invalidationRule) is anchored at the CURRENT price,
 * the cards (tradeSetup) at the PULLBACK ENTRY (current price ± 0.40 ATR), so stop and both targets differ by the same
 * amount. The engine is not changed. Every screen that shows numbers now takes them from tradeSetup — the set the
 * ledger, R:R, stop check and PlanStamp already use — through this one object, and no number is copied into a string
 * ahead of time: text is generated from [Levels] at display time.
 */
object ResearchScenario {

    data class Levels(
        val snapshotId: String, val interval: String, val side: Signal,
        val entry: Double, val stop: Double, val tp1: Double, val tp2: Double,
        val stopPips: Double, val tp1Pips: Double, val tp2Pips: Double,
        val expiresAtMs: Long, val horizonMin: Int,
        val planStatus: String, val state: String,
        /** true only when ResearchGate authorizes this exact snapshot now */
        val actionable: Boolean,
        /** V33.1R.2: when these levels were first seen in this window (0 = unknown) */
        val fixedAtMs: Long = 0L
    )

    /** null when there is no side (WAIT) or no usable levels. */
    fun of(a: GoldAnalysisResult?, now: Long): Levels? {
        if (a == null) return null
        val t = a.tradeSetup
        if (a.overallSignal == Signal.WAIT || t.signal == Signal.WAIT || t.signal != a.overallSignal) return null
        if (t.entryPrice <= 0.0 || t.stopLoss <= 0.0 || t.takeProfit1 <= 0.0) return null
        val state = ForecastState.resolve(t, a.overallSignal, a.presentation?.researchModelStatus)
        return Levels(t.snapshotId, a.interval, t.signal, t.entryPrice, t.stopLoss, t.takeProfit1, t.takeProfit2,
            t.stopLossPips, t.takeProfit1Pips, t.takeProfit2Pips, t.expiresAtMs,
            if (t.horizonMin > 0) t.horizonMin else PlanStamp.horizonMin(a.interval), t.planStatus, state,
            ResearchGate.executionAllowed(a, now), t.levelsFixedAtMs)
    }

    private fun t(l: AppLanguage, en: String, hi: String, mr: String) = when (l) { AppLanguage.ENGLISH -> en; AppLanguage.HINDI -> hi; AppLanguage.MARATHI -> mr }
    fun money(x: Double): String = "$" + String.format(Locale.US, "%,.2f", x)
    fun utc(ms: Long): String = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(java.util.Date(ms)) + " UTC"

    /** Numbered plan text, built from [lv] only. */
    fun steps(lv: Levels, l: AppLanguage): String {
        val s = lv.side.name
        val head = if (lv.actionable) t(l, "1. ACTIVE PLAN: $s (authorized for this snapshot).", "1. सक्रिय प्लान: $s (इस स्नैपशॉट के लिए अधिकृत)।", "1. सक्रिय प्लॅन: $s (या स्नॅपशॉटसाठी अधिकृत).")
            else t(l, "1. Rules lean $s. Research scenario, not a trade instruction.", "1. नियमों का झुकाव $s। रिसर्च परिदृश्य, ट्रेड निर्देश नहीं।", "1. नियमांचा कल $s. रिसर्च परिस्थिती, ट्रेड सूचना नाही.")
        return listOf(head,
            if (lv.fixedAtMs > 0) t(l, "2. Entry reference: ${money(lv.entry)} (pullback level, fixed at ${utc(lv.fixedAtMs)} for this window).", "2. एंट्री संदर्भ: ${money(lv.entry)} (पुलबैक लेवल, इस विंडो के लिए ${utc(lv.fixedAtMs)} पर तय)।", "2. एंट्री संदर्भ: ${money(lv.entry)} (पुलबॅक लेव्हल, या विंडोसाठी ${utc(lv.fixedAtMs)} ला निश्चित).")
            else t(l, "2. Entry reference: ${money(lv.entry)} (pullback level of this snapshot).", "2. एंट्री संदर्भ: ${money(lv.entry)} (इस स्नैपशॉट का पुलबैक लेवल)।", "2. एंट्री संदर्भ: ${money(lv.entry)} (या स्नॅपशॉटचा पुलबॅक लेव्हल)."),
            t(l, "3. Invalidation: ${money(lv.stop)}. Reaching it ends the scenario.", "3. अमान्य स्तर: ${money(lv.stop)}। यहाँ पहुँचते ही परिदृश्य खत्म।", "3. अवैध स्तर: ${money(lv.stop)}. येथे पोहोचल्यावर परिस्थिती संपते."),
            t(l, "4. Research targets: TP1 ${money(lv.tp1)} · TP2 ${money(lv.tp2)}.", "4. रिसर्च टारगेट: TP1 ${money(lv.tp1)} · TP2 ${money(lv.tp2)}।", "4. रिसर्च टार्गेट: TP1 ${money(lv.tp1)} · TP2 ${money(lv.tp2)}."),
            if (lv.expiresAtMs > 0) t(l, "5. Evaluation ends ${utc(lv.expiresAtMs)} (fixed).", "5. मूल्यांकन ${utc(lv.expiresAtMs)} पर खत्म (तय)।", "5. मूल्यमापन ${utc(lv.expiresAtMs)} ला संपते (निश्चित).")
            else t(l, "5. Evaluation end not stamped.", "5. मूल्यांकन का अंत दर्ज नहीं।", "5. मूल्यमापनाचा शेवट नोंदलेला नाही.")
        ).joinToString("\n")
    }

    /** "Ends 10:45 UTC (fixed) or at $4,052.64 (invalidation) or $4,038.69 (TP2)." */
    fun invalidationLine(lv: Levels, l: AppLanguage): String {
        val end = if (lv.expiresAtMs > 0) utc(lv.expiresAtMs) else "--"
        return t(l, "Ends $end (fixed) or at ${money(lv.stop)} (invalidation) or ${money(lv.tp2)} (TP2).",
            "$end पर (तय) या ${money(lv.stop)} (अमान्य स्तर) या ${money(lv.tp2)} (TP2) पर खत्म।",
            "$end ला (निश्चित) किंवा ${money(lv.stop)} (अवैध स्तर) किंवा ${money(lv.tp2)} (TP2) ला संपते.")
    }

    /** "25M evaluation horizon · ends 10:45 UTC": the duration and the end are shown separately from the countdown. */
    fun durationLine(lv: Levels?, interval: String, expiresAtMs: Long, l: AppLanguage): String {
        val h = lv?.horizonMin ?: PlanStamp.horizonMin(interval)
        val hs = if (h >= 60 && h % 60 == 0) "${h / 60}H" else "${h}M"
        val end = if (expiresAtMs > 0) utc(expiresAtMs) else "--"
        return t(l, "$hs evaluation horizon · ends $end", "$hs मूल्यांकन अवधि · $end पर खत्म", "$hs मूल्यमापन कालावधी · $end ला संपते")
    }

    /** Title of the countdown card. ACTIVE only for an authorized plan; after the end, AWAITING OUTCOME CHECK. */
    fun windowTitle(lv: Levels?, expired: Boolean, l: AppLanguage): String = when {
        lv != null && lv.actionable && !expired -> ForecastState.validityTitle(ForecastState.APPROVED_ACTIVE, l)
        expired -> t(l, "RESEARCH HORIZON ENDED — AWAITING OUTCOME CHECK", "रिसर्च अवधि खत्म — नतीजे की जाँच बाकी", "रिसर्च कालावधी संपला — निकाल तपासणी बाकी")
        lv != null && lv.state != ForecastState.APPROVED_ACTIVE -> ForecastState.validityTitle(lv.state, l)
        else -> ForecastState.validityTitle(ForecastState.UNVERIFIED_RESEARCH, l)
    }

    /** Small line under the countdown: never "auto closed" for research (nothing is opened or closed). */
    fun outcomeNote(lv: Levels?, l: AppLanguage): String =
        if (lv != null && lv.actionable) t(l, "Ends at TP2, invalidation or time", "TP2, अमान्य स्तर या समय पर खत्म", "TP2, अवैध स्तर किंवा वेळेवर संपते")
        else t(l, "Outcome checked later with real prices", "नतीजा बाद में असली भाव से जाँचा जाएगा", "निकाल नंतर खऱ्या भावाने तपासला जाईल")

    /** Clipboard text, ONLY for an authorized plan; null otherwise (nothing is copied). */
    fun clipboard(lv: Levels?): String? {
        if (lv == null || !lv.actionable) return null
        return HonestText.shareHeader(true) + "\n" +
            "Direction: ${lv.side.name} · ${lv.interval} · snapshot ${lv.snapshotId}\n" +
            "Entry: ${money(lv.entry)}\nInvalidation: ${money(lv.stop)}\nTP1: ${money(lv.tp1)}\nTP2: ${money(lv.tp2)}\n" +
            "Ends: ${if (lv.expiresAtMs > 0) utc(lv.expiresAtMs) else "--"}"
    }

    private fun p2(x: Double) = String.format(Locale.US, "%.2f", x)

    /**
     * V33.1R.1: order text for the CURRENT snapshot, built from the scenario at the moment it is asked for (a click).
     * null unless that snapshot is an authorized ACTIVE plan right now.
     */
    fun orderCommand(a: GoldAnalysisResult?, now: Long): String? {
        val lv = of(a, now) ?: return null
        if (!lv.actionable) return null
        return "${lv.side.name} LIMIT XAUUSD @ ${p2(lv.entry)} SL ${p2(lv.stop)} TP1 ${p2(lv.tp1)} TP2 ${p2(lv.tp2)}"
    }

    /** Ensemble copy: the authorized plan's order, and only when the six-bot ensemble is on the SAME side. */
    fun ensembleCommand(a: GoldAnalysisResult?, now: Long): String? {
        val e = a?.multiBotEnsemble ?: return null
        val lv = of(a, now) ?: return null
        if (e.ensembleSignal != lv.side) return null
        return orderCommand(a, now)
    }

    fun ensembleRefused(l: AppLanguage) = t(l, "No order to copy: needs an authorized ACTIVE plan and bots on the same side.",
        "कॉपी के लिए ऑर्डर नहीं: अधिकृत ACTIVE प्लान और उसी दिशा में बॉट चाहिए।", "कॉपीसाठी ऑर्डर नाही: अधिकृत ACTIVE प्लॅन व त्याच दिशेतील बॉट हवे.")
    fun planChanged(l: AppLanguage) = t(l, "The plan changed since this screen was drawn. Review it again; nothing was copied.",
        "स्क्रीन बनने के बाद प्लान बदल गया। फिर से देखें; कुछ कॉपी नहीं हुआ।", "स्क्रीन बनल्यानंतर प्लॅन बदलला. पुन्हा तपासा; काहीही कॉपी झाले नाही.")

    fun copyRefused(l: AppLanguage) = t(l, "Copy is available only for an authorized ACTIVE plan.", "कॉपी सिर्फ़ अधिकृत ACTIVE प्लान के लिए है।", "कॉपी फक्त अधिकृत ACTIVE प्लॅनसाठी आहे.")
}
