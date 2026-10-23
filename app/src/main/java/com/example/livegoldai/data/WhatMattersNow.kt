package com.example.livegoldai.data

import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * WHAT MATTERS NOW (V23.1)
 *
 * At most three short lines, each one read straight from a value the engine already measured:
 * data quality, news window / upcoming high-impact USD release, anomaly detector, sideways filter,
 * timeframe alignment, broken cross-market relations, unfamiliar market, model health.
 * Ordered by risk first. No line is written when its evidence is missing, and the wording never claims
 * a cause ("after" / "while", never "because").
 */
object WhatMattersNow {

    data class Item(val level: String, val en: String, val hi: String, val mr: String)   // level: RISK / WATCH / INFO

    private fun ffTimeMs(iso: String): Long? = try {
        if (!iso.contains("T") || iso.length < 19) null else {
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val local = f.parse(iso.substring(0, 19))?.time
            val tz = iso.substring(19)
            val off = if (tz.length >= 6 && (tz[0] == '+' || tz[0] == '-')) (if (tz[0] == '-') -1 else 1) * (tz.substring(1, 3).toInt() * 60 + tz.substring(4, 6).toInt()) else 0
            local?.minus(off * 60_000L)
        }
    } catch (_: Exception) { null }

    fun build(a: GoldAnalysisResult, now: Long = System.currentTimeMillis()): List<Item> {
        val out = ArrayList<Item>()
        val p = a.pulse
        val b = a.brain

        // 0. V29A source gate: a paused decision is the most important thing on the screen
        if (a.gateReason.isNotBlank()) {
            val f = a.feed
            when {
                a.gateReason == "WAIT_NO_DATA_KEY" || f?.keyMissing == true -> out.add(Item("RISK",
                    "No Twelve Data key saved: XAU/USD cannot load, so no BUY/SELL is shown. Add your key in Settings → Market data.",
                    "Twelve Data key सेव नहीं: XAU/USD लोड नहीं हो सकता, इसलिए कोई BUY/SELL नहीं। Settings → Market data में अपनी key डालें।",
                    "Twelve Data key सेव नाही: XAU/USD लोड होत नाही, म्हणून BUY/SELL नाही. Settings → Market data मध्ये तुमची key टाका."))
                a.gateReason == "WAIT_DATA_DEGRADED" -> out.add(Item("RISK",
                    "XAU/USD data failed a check (${a.gateDetail}): decision paused.",
                    "XAU/USD डेटा एक जाँच में फेल (${a.gateDetail}): फैसला रुका है।",
                    "XAU/USD डेटा एका तपासणीत नापास (${a.gateDetail}): निर्णय थांबवला आहे."))
                else -> out.add(Item("RISK",
                    "Source switch: ${a.gateDetail}${if (!f?.primaryIssue.isNullOrBlank()) " (primary: ${f?.primaryIssue})" else ""}. WAIT until Twelve Data XAU/USD is back and clean.",
                    "स्रोत बदला: ${a.gateDetail}। Twelve Data XAU/USD लौटने और साफ़ होने तक WAIT।",
                    "स्रोत बदलला: ${a.gateDetail}. Twelve Data XAU/USD परत येऊन स्वच्छ होईपर्यंत WAIT."))
            }
        }

        // 1. data quality
        when (p?.dataQuality) {
            "UNSAFE" -> out.add(Item("RISK",
                "Price data failed the quality check: no BUY/SELL is shown. ${p.dataQualityDetail.substringAfter(": ", "")}".trim(),
                "भाव का डेटा जाँच में फेल: अभी कोई BUY/SELL नहीं दिखाया जा रहा।",
                "भावाचा डेटा तपासणीत नापास: सध्या कोणताही BUY/SELL दाखवला जात नाही."))
            "DEGRADED" -> out.add(Item("WATCH",
                "Price data is usable but not clean: ${p.dataQualityDetail.substringAfter(": ", p.dataQualityDetail)}",
                "भाव का डेटा चल सकता है पर पूरी तरह साफ़ नहीं: ${p.dataQualityDetail.substringAfter(": ", p.dataQualityDetail)}",
                "भावाचा डेटा चालू शकतो पण पूर्ण स्वच्छ नाही: ${p.dataQualityDetail.substringAfter(": ", p.dataQualityDetail)}"))
        }

        // 2. news: active window first, else the next high-impact USD release within 3 hours
        val nm = a.newsMode
        if (nm != null) {
            if (nm.phase == "PRE") out.add(Item("RISK",
                "High-impact USD news in ${nm.minutes} min: ${nm.eventTitle}. Fresh entries are on WAIT.",
                "${nm.minutes} मिनट में हाई-इम्पैक्ट USD न्यूज़: ${nm.eventTitle}। नई एंट्री WAIT पर।",
                "${nm.minutes} मिनिटांत हाय-इम्पॅक्ट USD बातमी: ${nm.eventTitle}. नवीन एंट्री WAIT वर."))
            else out.add(Item("RISK",
                "${nm.eventTitle} was released ${nm.minutes} min ago: news window still active.",
                "${nm.eventTitle} ${nm.minutes} मिनट पहले आई: न्यूज़ विंडो अभी चालू है।",
                "${nm.eventTitle} ${nm.minutes} मिनिटांपूर्वी आली: न्यूज विंडो अजून चालू आहे."))
        } else {
            val next = a.macroRadar?.upcomingEvents.orEmpty()
                .filter { it.impact.equals("High", true) }
                .mapNotNull { e -> ffTimeMs(e.isoTime)?.let { e to it } }
                .filter { it.second > now && it.second - now <= 3 * 3600_000L }
                .minByOrNull { it.second }
            if (next != null) {
                val mins = (next.second - now) / 60_000L
                out.add(Item("WATCH",
                    "High-impact USD release in $mins min: ${next.first.title}.",
                    "$mins मिनट में हाई-इम्पैक्ट USD रिलीज़: ${next.first.title}।",
                    "$mins मिनिटांत हाय-इम्पॅक्ट USD रिलीज: ${next.first.title}."))
            }
        }

        // 3. anomaly detector
        if (b != null && (b.anomaly.level == "HIGH" || b.anomaly.level == "EXTREME")) {
            val why = b.anomaly.reasons.firstOrNull().orEmpty()
            out.add(Item("RISK",
                "Unusual market (${b.anomaly.level}): $why".trimEnd(':', ' '),
                "असामान्य मार्केट (${b.anomaly.level}): $why".trimEnd(':', ' '),
                "असामान्य मार्केट (${b.anomaly.level}): $why".trimEnd(':', ' ')))
        }

        // 4. model health from the real ledger
        if (p != null && (p.modelHealth == "CRITICAL" || p.modelHealth == "POLARITY_SUSPECT")) {
            out.add(Item("RISK", p.modelHealthLine, p.modelHealthLine, p.modelHealthLine))
        }

        // 5. sideways market (the range filter turns BUY/SELL into WAIT)
        // V29A.4: say "held as WAIT" only when the shown call really is WAIT
        if (b != null && b.regime.equals("RANGE", true)) {
            if (a.overallSignal == Signal.WAIT) out.add(Item("WATCH",
                "Sideways market: no BUY/SELL now; trend rules are unreliable in a range.",
                "साइडवेज़ मार्केट: अभी BUY/SELL नहीं; रेंज में ट्रेंड के नियम भरोसेमंद नहीं।",
                "साइडवेज मार्केट: आत्ता BUY/SELL नाही; रेंजमध्ये ट्रेंडचे नियम विश्वासार्ह नाहीत."))
            else out.add(Item("RISK",
                "Sideways market now, yet a ${a.overallSignal.name} is shown: the range filter did not block this candle's call (the filter is checked once, when the candle's call is recorded). Trend rules are less reliable here.",
                "अभी साइडवेज़ मार्केट, फिर भी ${a.overallSignal.name} दिख रहा है: इस कैंडल की कॉल पर रेंज फ़िल्टर नहीं लगा (फ़िल्टर कैंडल की कॉल दर्ज होते समय एक बार जाँचा जाता है)। यहाँ ट्रेंड के नियम कम भरोसेमंद हैं।",
                "आत्ता साइडवेज मार्केट, तरीही ${a.overallSignal.name} दिसत आहे: या कँडलच्या कॉलवर रेंज फिल्टर लागला नाही (फिल्टर कँडलची कॉल नोंदवताना एकदाच तपासला जातो). इथे ट्रेंडचे नियम कमी विश्वासार्ह आहेत."))
        }

        // 6. broken cross-market relation
        b?.crossMarket?.divergences?.firstOrNull()?.let { d ->
            out.add(Item("WATCH", "Relation break: $d", "संबंध टूटा: $d", "संबंध तुटला: $d"))
        }

        // 7. unfamiliar market
        if (b != null && b.familiarity.status == "OUT_OF_DISTRIBUTION") out.add(Item("WATCH",
            "Today's market is outside the range of stored history: past results apply less.",
            "आज का मार्केट रिकॉर्ड इतिहास की सीमा से बाहर है: पुराने नतीजे कम लागू होते हैं।",
            "आजचे मार्केट साठवलेल्या इतिहासाच्या मर्यादेबाहेर आहे: जुने निकाल कमी लागू होतात."))

        // 8. timeframe alignment (always available when the brain ran)
        if (b != null && b.mtfSummary.isNotBlank()) out.add(Item("INFO",
            "Timeframes: ${b.mtfSummary}", "टाइमफ्रेम: ${b.mtfSummary}", "टाइमफ्रेम: ${b.mtfSummary}"))

        return out.take(3)
    }
}
