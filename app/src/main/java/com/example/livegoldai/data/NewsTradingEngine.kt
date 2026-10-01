package com.example.livegoldai.data

import com.example.livegoldai.model.*
import java.util.Locale
import kotlin.math.abs

/**
 * News plan built from the REAL economic calendar (ForexFactory feed).
 * No event name, time or forecast is invented. If the calendar could not be loaded,
 * the plan says so.
 */
object NewsTradingEngine {

    private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)

    fun generateNewsPlan(
        currentPrice: Double,
        atrSafe: Double,
        macroSignal: Signal,
        forceActiveNews: Boolean = false,
        events: List<EconomicEvent> = emptyList(),
        nowMs: Long = System.currentTimeMillis()
    ): NewsTradingPlan {
        val upper = currentPrice + 1.2 * atrSafe
        val lower = currentPrice - 1.2 * atrSafe

        val next = events
            .filter { it.impact.equals("High", ignoreCase = true) }
            .mapNotNull { ev -> EventTime.parseMs(ev.isoTime)?.let { ev to it } }
            .filter { it.second >= nowMs - 30 * 60_000L }
            .minByOrNull { it.second }

        val ev = next?.first
        val evMs = next?.second
        val minutesTo = if (evMs != null) (evMs - nowMs) / 60_000L else null
        val liveWindow = minutesTo != null && minutesTo in -30..30
        val active = forceActiveNews || liveWindow

        val countdown = when {
            ev == null && events.isEmpty() -> "Economic calendar not available right now"
            ev == null -> "No high-impact USD event left this week"
            minutesTo != null && minutesTo > 0 -> "⏰ ${ev.title}: in ${formatMinutes(minutesTo)} (${PredictionLedger.utcLabel(evMs ?: nowMs, withDay = true)})"
            minutesTo != null -> "🚨 ${ev.title}: released ${formatMinutes(abs(minutesTo))} ago"
            else -> ""
        }
        val phase = when {
            minutesTo == null -> NewsPhase.STANDBY
            minutesTo in 1..30 -> NewsPhase.PRE_NEWS_COIL
            minutesTo in -5..0 -> NewsPhase.LIVE_NEWS_SPIKE
            minutesTo in -30..-6 -> NewsPhase.POST_NEWS_RETRACEMENT
            else -> if (forceActiveNews) NewsPhase.LIVE_NEWS_SPIKE else NewsPhase.STANDBY
        }

        val fcst = if (ev != null && (ev.forecast.isNotBlank() || ev.previous.isNotBlank()))
            "Forecast: ${ev.forecast.ifBlank { "n/a" }} | Previous: ${ev.previous.ifBlank { "n/a" }} (actual value is not in the feed; watch the price reaction)"
        else "No forecast data in the calendar feed"

        return NewsTradingPlan(
            isNewsActive = active,
            eventName = ev?.let { "${it.title} (${it.country}, High impact)" } ?: "No high-impact event found",
            eventImpact = if (ev != null) "HIGH IMPACT 🔴" else "—",
            releaseCountdownFormatted = countdown,
            phase = phase,
            primaryDirectionBias = Signal.WAIT, // the app does not predict news outcomes
            straddleUpperLevel = upper,
            straddleLowerLevel = lower,
            spreadWarningBufferPips = 0.0,
            freezeRuleTitle = "NO NEW ENTRY 30 MIN BEFORE / 5 MIN AFTER RELEASE",
            freezeRuleDescriptionEnglish = "Spreads usually widen and price can spike both ways around high-impact releases. The app shows WAIT before the release and only follows the real price reaction afterwards.",
            freezeRuleDescriptionHindi = "हाई-इम्पैक्ट न्यूज़ के आसपास स्प्रेड बढ़ता है और भाव दोनों तरफ उछल सकता है। ऐप रिलीज़ से पहले WAIT दिखाता है और बाद में सिर्फ असली रिएक्शन देखता है।",
            freezeRuleDescriptionMarathi = "हाय-इम्पॅक्ट बातम्यांच्या वेळी स्प्रेड वाढतो आणि भाव दोन्ही बाजूंनी उसळू शकतो. ॲप रिलीजपूर्वी WAIT दाखवते आणि नंतर फक्त खरी प्रतिक्रिया पाहते.",
            newsTacticHeadingEnglish = "Wait for the release, then follow the reaction",
            newsTacticHeadingHindi = "रिलीज़ का इंतज़ार, फिर रिएक्शन के साथ",
            newsTacticHeadingMarathi = "रिलीजची वाट पहा, मग प्रतिक्रियेसोबत",
            newsTacticDetailEnglish = "Reference levels: ±1.2x ATR from now = ${f2(lower)} / ${f2(upper)}. A move beyond one of them after the first 5 minutes shows the market's direction. These are reference levels, not a forecast.",
            newsTacticDetailHindi = "रेफरेंस लेवल: अभी से ±1.2x ATR = ${f2(lower)} / ${f2(upper)}। पहले 5 मिनट के बाद इनमें से किसी के पार जाना मार्केट की दिशा दिखाता है। ये अनुमान नहीं, सिर्फ रेफरेंस हैं।",
            newsTacticDetailMarathi = "रेफरन्स लेव्हल: आतापासून ±1.2x ATR = ${f2(lower)} / ${f2(upper)}. पहिल्या 5 मिनिटांनंतर यापैकी एकाच्या पलीकडे जाणे बाजाराची दिशा दाखवते. हा अंदाज नाही, फक्त संदर्भ आहे.",
            secondWaveRetracementLevel = "${f2(currentPrice)} (price now)",
            actualVsForecastScenario = fcst
        )
    }

    private fun formatMinutes(m: Long): String = when {
        m < 60 -> "$m min"
        m < 1440 -> "${m / 60}h ${m % 60}m"
        else -> "${m / 1440}d ${(m % 1440) / 60}h"
    }
}
