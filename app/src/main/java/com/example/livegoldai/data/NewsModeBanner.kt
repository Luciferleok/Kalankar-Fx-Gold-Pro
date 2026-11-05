package com.example.livegoldai.data

import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.EconomicEvent
import com.example.livegoldai.model.GoldAnalysisResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * V35.10 News Mode banner (owner choice, 5 Nov 2026, before NFP 6 Nov 19:00 IST). Display only: it reads the same
 * calendar, MacroWindow and typed Macro evidence the engine used for this analysis and changes nothing.
 *  UPCOMING : a high-impact USD event within [HEADS_UP_MS], window not open yet
 *  PRE      : window open, release still ahead (Macro measures the 60-minute move into it)
 *  POST     : release out, window still open (Macro needs a COMPLETED 5-minute bar after the release)
 */
object NewsModeBanner {
    const val HEADS_UP_MS = 3 * 60 * 60_000L

    enum class Stage { UPCOMING, PRE, POST }

    data class Banner(
        val stage: Stage,
        val event: String,            // "NFP (Non-Farm Employment Change)"
        val releaseAt: String,        // "Fri 06 Nov 19:00 IST"
        val countdown: String,        // "Macro window opens in 1h 12m" / "Release in 23m" / "Released 7m ago · window closes in 23m"
        val dollar: String,           // evidence status of this analysis, "--" before the window
        val tenY: String,
        val macro: String,            // "Macro votes SELL (used in decision)" / "Macro no vote ..." / "Macro OFF until the window opens"
        val note: String
    )

    private fun t(l: AppLanguage, en: String, hi: String, mr: String) = when (l) { AppLanguage.ENGLISH -> en; AppLanguage.HINDI -> hi; AppLanguage.MARATHI -> mr }

    fun span(ms: Long): String {
        val m = (ms.coerceAtLeast(0L) + 59_999L) / 60_000L
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }

    fun build(a: GoldAnalysisResult, now: Long, l: AppLanguage, tz: TimeZone = TimeZone.getDefault()): Banner? {
        val events = a.macroRadar?.upcomingEvents ?: return null
        val active = MacroWindow.activeEvent(events, now)
        val ev: EconomicEvent = active ?: events
            .filter { it.impact.equals("High", true) && it.country.equals("USD", true) }
            .mapNotNull { e -> EventTime.parseMs(e.isoTime)?.let { e to it } }
            .filter { (_, t) -> t - MacroWindow.PRE_MS > now && t - MacroWindow.PRE_MS - now <= HEADS_UP_MS }
            .minByOrNull { it.second }?.first ?: return null
        val t = EventTime.parseMs(ev.isoTime) ?: return null
        val open = t - MacroWindow.PRE_MS
        val close = t + MacroWindow.postMs(ev)
        val stage = when { active == null -> Stage.UPCOMING; now < t -> Stage.PRE; else -> Stage.POST }
        val day = SimpleDateFormat("EEE dd MMM HH:mm", Locale.US).apply { timeZone = tz }
        val hm = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = tz }
        val zone = OutcomeBrowser.zone(tz, t)
        val countdown = when (stage) {
            Stage.UPCOMING -> t(l, "Macro window opens ${hm.format(Date(open))} $zone (in ${span(open - now)})", "Macro विंडो ${hm.format(Date(open))} $zone पर खुलेगी (${span(open - now)} में)", "Macro विंडो ${hm.format(Date(open))} $zone ला उघडेल (${span(open - now)} मध्ये)")
            Stage.PRE -> t(l, "Release in ${span(t - now)} · window closes ${hm.format(Date(close))} $zone", "रिलीज़ ${span(t - now)} में · विंडो ${hm.format(Date(close))} $zone पर बंद", "रिलीज ${span(t - now)} मध्ये · विंडो ${hm.format(Date(close))} $zone ला बंद")
            Stage.POST -> t(l, "Released ${span(now - t)} ago · window closes in ${span(close - now)}", "रिलीज़ हुए ${span(now - t)} · विंडो ${span(close - now)} में बंद", "रिलीज होऊन ${span(now - t)} · विंडो ${span(close - now)} मध्ये बंद")
        }
        val d = MacroEvidenceView.dollar(a).evidence; val y = MacroEvidenceView.tenY(a).evidence
        fun state(e: com.example.livegoldai.model.MacroEvidence) = if (stage == Stage.UPCOMING) "--" else e.status.name.replace('_', ' ') + if (e.usableInWindow) " (${e.provider} ${e.sourceKind})" else ""
        val g = a.groups.firstOrNull { it.key == "macro" }
        val macro = when {
            stage == Stage.UPCOMING -> t(l, "Macro OFF until the window opens (no vote, no weight)", "विंडो खुलने तक Macro बंद (वोट/वज़न नहीं)", "विंडो उघडेपर्यंत Macro बंद (मत/वजन नाही)")
            g?.active == true -> t(l, "Macro votes ${g.verdict.name} (used in decision)", "Macro वोट ${g.verdict.name} (फ़ैसले में शामिल)", "Macro मत ${g.verdict.name} (निर्णयात वापर)")
            else -> t(l, "Macro no vote, no weight (no usable minute data yet)", "Macro का वोट/वज़न नहीं (अभी उपयोगी मिनट डेटा नहीं)", "Macro ला मत/वजन नाही (अजून उपयोगी मिनिट डेटा नाही)")
        }
        val note = when (stage) {
            Stage.UPCOMING -> t(l, "Before the window: run SYSTEM > News readiness to check the dollar / 10Y sources.", "विंडो से पहले: SYSTEM > News readiness चलाकर डॉलर / 10Y स्रोत जाँचें।", "विंडोपूर्वी: SYSTEM > News readiness चालवून डॉलर / 10Y स्रोत तपासा.")
            Stage.PRE -> t(l, "Into the release Macro uses the last 60 minutes of the dollar / 10Y. Spreads widen near news; research only.", "रिलीज़ तक Macro डॉलर / 10Y के पिछले 60 मिनट देखता है। न्यूज़ के पास स्प्रेड बढ़ता है; सिर्फ़ रिसर्च।", "रिलीजपर्यंत Macro डॉलर / 10Y ची मागील 60 मिनिटे पाहतो. न्यूजजवळ स्प्रेड वाढतो; फक्त रिसर्च.")
            Stage.POST -> t(l, "After the release Macro waits for the first COMPLETED 5-minute bar (about 5 minutes). Fast moves and wide spreads; research only.", "रिलीज़ के बाद Macro पहली पूरी 5-मिनट कैंडल (लगभग 5 मिनट) का इंतज़ार करता है। तेज़ चाल, चौड़ा स्प्रेड; सिर्फ़ रिसर्च।", "रिलीजनंतर Macro पहिल्या पूर्ण 5-मिनिट कँडलची (सुमारे 5 मिनिटे) वाट पाहतो. वेगवान हालचाल, रुंद स्प्रेड; फक्त रिसर्च.")
        }
        return Banner(stage, OfficialCalendar.kind(ev.title) + " (" + ev.title + ")", day.format(Date(t)) + " " + zone, countdown, state(d), state(y), macro, note)
    }
}
