package com.example.livegoldai.ui.v8

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.data.RealityEngine
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import java.util.Locale
import kotlin.math.roundToInt

/**
 * COCKPIT — the whole market state in a few seconds.
 * Every value here is real: live feed timing, the app's actual signal, real multi-timeframe
 * candles and the measured health of each component.
 */
@Composable
fun V8CockpitScreen(
    analysis: GoldAnalysisResult,
    lang: AppLanguage,
    selectedInterval: String,
    tick: Int,
    onIntervalChange: (String) -> Unit,
    onOpenHealth: () -> Unit,
    onOpenForecast: () -> Unit,
    onOpenPlaybook: () -> Unit,
    onOpenAi: () -> Unit = {}
) {
    @Suppress("UNUSED_VARIABLE") val recomposeEverySecond = tick
    val now = System.currentTimeMillis()
    val feed = analysis.feed
    val ageMs = feed?.let { now - it.fetchedAtMs } ?: -1L
    // truthful feed state: LIVE only when fresh. RESTORED for one minute after a gap ends.
    val rawState = when {
        feed == null -> "OFFLINE"
        !feed.isLive -> "OFFLINE"
        com.example.livegoldai.data.PredictionLedger.isMarketClosed(now) -> "MARKET CLOSED"
        ageMs > 180_000 -> "STALE"
        else -> "LIVE"
    }
    var lastGapAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(rawState) { if (rawState == "STALE" || rawState == "OFFLINE") lastGapAt = System.currentTimeMillis() }
    val liveState = if (rawState == "LIVE" && lastGapAt > 0 && now - lastGapAt < 60_000) "RESTORED" else rawState
    val liveColor = when (liveState) { "LIVE" -> V8.Green; "RESTORED" -> V8.Info; "STALE" -> V8.Amber; "MARKET CLOSED" -> V8.Text3; else -> V8.Red }
    val sig = analysis.overallSignal
    val rawConf = analysis.agreementPercent.roundToInt()
    // V29B: the shared decision description and its trace sheet
    val pres = analysis.presentation
    var showTrace by remember { mutableStateOf(false) }
    if (showTrace && pres != null) DecisionTraceSheet(pres, lang, com.example.livegoldai.data.PlanText.whyReasons(analysis, lang)) { showTrace = false }
    val calibrated = RealityEngine.calibratedConfidence(analysis.learning, rawConf)
    val ins = analysis.insights
    val health = analysis.health

    Column(
        modifier = Modifier
            .fillMaxSize()
            // transparent: the shared signature background shows through
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "PRIVATE MARKET INTELLIGENCE", color = V8.Text3, fontSize = 9.sp, fontWeight = FontWeight.Medium,
            letterSpacing = 3.sp, maxLines = 1, modifier = Modifier.fillMaxWidth().padding(start = 4.dp)
        )

        // ---------------- LEVEL 1: price + live feed
        V8Card(level = 1) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // V29A: the instrument on screen is always named; a proxy is never shown as XAU/USD
                val inst = feed?.instrumentId ?: ""
                Text(text = when (inst) { "PAXG_USDT" -> "PAXG / USDT • PROXY"; "GC_F" -> "GC=F • BACKUP"; else -> "XAU / USD" },
                    color = if (inst == "PAXG_USDT" || inst == "GC_F") V8.Amber else V8.Gold, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                V8Dot(liveColor)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = when (liveState) { "LIVE" -> tr(lang, "LIVE", "लाइव", "लाइव्ह"); "RESTORED" -> tr(lang, "RESTORED", "फिर से जुड़ा", "पुन्हा जोडले"); "STALE" -> tr(lang, "STALE", "पुराना", "जुना"); "MARKET CLOSED" -> tr(lang, "MARKET CLOSED", "मार्केट बंद", "मार्केट बंद"); else -> tr(lang, "OFFLINE", "ऑफ़लाइन", "ऑफलाइन") }, color = liveColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = String.format(Locale.US, "$%,.2f", analysis.currentPrice), color = V8.Text1, fontSize = 42.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.5).sp)
            Text(
                text = String.format(Locale.US, "%+.2f   %+.2f%%", analysis.changeAmount, analysis.changePercent),
                color = if (analysis.changeAmount >= 0) V8.Green else V8.Red, fontSize = 13.sp, fontWeight = FontWeight.Medium
            )
            V8Hairline()
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    tr(lang, "SOURCE", "स्रोत", "स्रोत") to (feed?.let { if (it.sourceType == "PRIMARY") "PRIMARY" else it.sourceType.ifBlank { "--" } } ?: "--"),
                    tr(lang, "AGE", "उम्र", "वय") to fmtAge(ageMs),
                    tr(lang, "FETCH", "लोड", "लोड") to (feed?.latencyMs?.let { "$it ms" } ?: "--")
                ).forEach { (k, v) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = k, color = V8.Text3, fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp)
                        Text(text = v, color = V8.Text2, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }

        // ---------------- context-aware priority: a RISK item (bad data, news window, unusual market, model alarm)
        // moves above the forecast while it is true, and disappears by itself when the condition ends.
        val matters = remember(analysis) { com.example.livegoldai.data.WhatMattersNow.build(analysis) }
        val topRisk = matters.firstOrNull { it.level == "RISK" }
        if (topRisk != null) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KSurface.Plane, RoundedCornerShape(KShape.medium))
                    .padding(horizontal = KSpace.md, vertical = KSpace.md)
            ) {
                Box(modifier = Modifier.padding(top = 5.dp)) { V8Dot(V8.Amber, 6.dp) }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = tr(lang, "RISK NOW", "अभी जोखिम", "आत्ता जोखीम"), color = V8.Amber, style = KType.SectionLabel)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = tr(lang, topRisk.en, topRisk.hi, topRisk.mr), color = V8.Text1, style = KType.BodyPrimary)
                }
            }
        }

        // ---------------- LEVEL 1: next move (accent turns amber while a risk is active)
        V8Card(level = 1, accent = (if (topRisk != null) V8.Amber else signalColor(sig)).copy(alpha = 0.28f), onClick = onOpenForecast) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // the selected timeframe is always visible and highlighted, also when it was chosen elsewhere (e.g. 1M in Tools)
                val base = listOf("5m", "15m", "30m", "1h", "4h", "1d")
                (if (base.any { it.equals(selectedInterval, ignoreCase = true) }) base else listOf(selectedInterval.lowercase()) + base).forEach { iv ->
                    V8Chip(text = iv.uppercase(), selected = iv.equals(selectedInterval, ignoreCase = true)) { onIntervalChange(iv) }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            val until = analysis.nextPrediction?.validUntilTimestamp ?: 0L
            Box {
            // brand watermark: one per screen, far below reading contrast, in the empty middle of the hero
            KalankarBrandEmblem(mode = EmblemMode.WATERMARK, modifier = Modifier.align(Alignment.Center), watermarkAlpha = 0.045f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    // V29E: the candle size and the evaluation horizon are different things; this is not a next-candle forecast
                    V8Label(com.example.livegoldai.data.ForecastState.intervalLine(selectedInterval, com.example.livegoldai.data.TechnicalEngine.calculateValidityMinutes(selectedInterval), lang))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = arrowOf(sig), color = signalColor(sig), fontSize = 30.sp, fontWeight = FontWeight.Light)
                    Text(text = when (sig) {
                        Signal.BUY -> tr(lang, "BULLISH", "ऊपर", "वर")
                        Signal.SELL -> tr(lang, "BEARISH", "नीचे", "खाली")
                        Signal.WAIT -> "WAIT"
                    }, color = signalColor(sig), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                    if (sig == Signal.WAIT) Text(
                        // V29A.7: the real reason for this WAIT
                        text = RealityEngine.waitReason(analysis).let { (code, en) -> when (code) {
                            "DATA" -> tr(lang, en, "डेटा जाँच फेल", "डेटा तपासणी नापास")
                            "NO_KEY" -> tr(lang, en, "डेटा key नहीं", "डेटा key नाही")
                            "SOURCE" -> if (pres != null && pres.restorationPending.isNotEmpty())
                                tr(lang, "RESTORATION PENDING ${pres.restorationPending} • BLOCKED", "बहाली बाकी ${pres.restorationPending} • रुका", "पुनर्स्थापना बाकी ${pres.restorationPending} • थांबवले")
                                else tr(lang, en, "स्रोत बदला", "स्रोत बदलला")
                            "RANGE" -> tr(lang, en, "रेंज फ़िल्टर • साइडवेज़ मार्केट", "रेंज फिल्टर • साइडवेज मार्केट")
                            "LEARNED" -> tr(lang, en, "सीखा हुआ फ़िल्टर", "शिकलेला फिल्टर")
                            "BELOW_GATE" -> tr(lang, en, en.replace("PILLARS", "पिलर"), en.replace("PILLARS", "पिलर"))
                            else -> tr(lang, en, "इंजन सुरक्षा नियम • क्यों? देखें", "इंजिन सुरक्षा नियम • का? पहा")
                        } },
                        color = V8.Text3, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp
                    )
                    // V29B: never hide that the engine and the published decision differ
                    if (pres != null && pres.rawDiffersFromFinal) Text(
                        text = tr(lang, "RAW QUANT: ", "रॉ क्वांट: ", "रॉ क्वांट: ") + (pres.rawSignal?.name ?: "--") +
                            tr(lang, "  •  FINAL: ", "  •  अंतिम: ", "  •  अंतिम: ") + pres.finalSignal.name,
                        color = V8.Amber, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp
                    )
                    // V29C: a WAIT has no validity countdown; show the real next review (next candle) instead
                    // V29D: an unproven BUY/SELL is labelled as an unverified research signal
                    if (sig != Signal.WAIT && analysis.tradeSetup.planStatus != com.example.livegoldai.data.PlanStamp.ACTIVE) Text(
                        text = when (analysis.tradeSetup.planStatus) {
                            com.example.livegoldai.data.PlanStamp.NO_DATA -> tr(lang, "DATA ", "डेटा ", "डेटा ") + analysis.tradeSetup.freshness + tr(lang, " • NOT ACTIONABLE", " • कार्रवाई योग्य नहीं", " • कृती योग्य नाही")
                            com.example.livegoldai.data.PlanStamp.STOPPED -> tr(lang, "STOP-LOSS HIT • CLOSED", "STOP-LOSS लगा • बंद", "STOP-LOSS लागला • बंद")
                            com.example.livegoldai.data.PlanStamp.EXPIRED -> tr(lang, "EXPIRED", "अवधि खत्म", "मुदत संपली")
                            else -> tr(lang, "UNVERIFIED RESEARCH SIGNAL", "असत्यापित रिसर्च सिग्नल", "असत्यापित रिसर्च सिग्नल")
                        },
                        color = V8.Amber, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp
                    )
                    // V29D: stable expiry (ledger record, else candle open + horizon); the horizon is shown, never reset by a refresh
                    if (sig != Signal.WAIT && until > now) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = tr(lang, "HORIZON ", "अवधि ", "कालावधी ") + "${analysis.tradeSetup.horizonMin}m • " + tr(lang, "ENDS IN ", "खत्म ", "संपेल ") + fmtAge(until - now), color = V8.Text3, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                    } else if (sig != Signal.WAIT && until in 1..now) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = tr(lang, "HORIZON ENDED • REVIEW DUE", "अवधि खत्म • जाँच बाकी", "कालावधी संपला • तपासणी बाकी"), color = V8.Amber, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                    } else if (sig == Signal.WAIT) {
                        // V29C.1: one fixed review time from the decision's snapshot; REVIEW DUE when passed
                        val review = pres?.nextReviewAtMs ?: 0L
                        if (review > 0L) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = if (now >= review) tr(lang, "REVIEW DUE", "जाँच का समय हो गया", "तपासणीची वेळ झाली") else tr(lang, "NEXT REVIEW ", "अगली जाँच ", "पुढील तपासणी ") + fmtAge(review - now),
                                color = if (now >= review) V8.Amber else V8.Text3, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                        }
                    }
                }
                // the ring shows exactly one real number: calibrated confidence if measured, else raw pillar agreement
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // V29A.5: a WAIT is not a call, so no hit-rate or confidence belongs to it
                    val isWait = sig == Signal.WAIT
                    V8Ring(
                        percent = if (isWait) -1 else calibrated ?: rawConf,
                        color = signalColor(sig),
                        label = if (isWait) tr(lang, "no call", "कोई कॉल नहीं", "कॉल नाही") else if (calibrated != null) tr(lang, "similar-setup hit-rate", "मिलते सेटअप हिट-रेट", "सारखे सेटअप हिट-रेट") else tr(lang, "agreement", "सहमति", "सहमती")
                    )
                    // V29A: agreement of indicators is not a probability of winning
                    val band = RealityEngine.bandRecord(analysis.learning, rawConf)
                    Text(
                        text = if (isWait) tr(lang, "WAIT has no\nwin chance to show", "WAIT की कोई\nजीत-संभावना नहीं", "WAIT ला\nजिंकण्याची शक्यता नाही")
                        else if (band != null) (pres?.metrics?.firstOrNull { it.id == com.example.livegoldai.data.Metrics.SIMILAR_SETUP }?.let { com.example.livegoldai.data.Metrics.short(it, lang) } ?: "N=${band.decided}") + tr(lang, "\nnot a forecast probability", "\nअनुमान की संभावना नहीं", "\nअंदाजाची शक्यता नाही")
                        else tr(lang, "evidence agreement\nnot a win probability", "संकेतों की सहमति\nजीत की संभावना नहीं", "संकेतांची सहमती\nजिंकण्याची शक्यता नाही"),
                        color = V8.Text3, fontSize = 8.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 10.sp
                    )
                }
            }
            }
            // Four different things, shown side by side so they are never read as one number.
            V8Hairline(V8.Line)
            // V29B: above the fold only four independent dimensions; every other number is one tap away
            // V29E: two clearly separate groups: market-data health (left) and predictive evidence (right)
            val PTx = com.example.livegoldai.data.PlanText
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = PTx.dataHealthCaption(lang), color = V8.Text3, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text(text = PTx.evidenceCaption(lang), color = V8.Text3, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                val p0 = analysis.pulse
                val unc = analysis.brain?.totalUncertainty ?: "--"
                // the verified number comes from the same metrics provider as the scorecard and the Decision Trace
                val closeDir = pres?.metrics?.firstOrNull { it.id == com.example.livegoldai.data.Metrics.ALL_CLOSE_DIRECTION }
                HomeTile(tr(lang, "DATA", "डेटा", "डेटा"), p0?.dataQuality ?: "--", tr(lang, "quality", "क्वालिटी", "गुणवत्ता"), Modifier.weight(1f))
                HomeTile(tr(lang, "REGIME", "स्थिति", "स्थिती"), analysis.brain?.regime?.replace('_', ' ') ?: "--", tr(lang, "measured now", "अभी मापा", "आत्ता मोजलेले"), Modifier.weight(1f))
                HomeTile(tr(lang, "VERIFIED", "जाँचा", "तपासलेले"),
                    if (closeDir == null || !closeDir.sufficient) tr(lang, "INSUFF.", "कम सबूत", "कमी पुरावा") else "${closeDir.pct}%",
                    if (closeDir == null) "--" else "N=${closeDir.n}" + tr(lang, " • close dir.", " • क्लोज़ दिशा", " • क्लोज दिशा"),
                    Modifier.weight(1f))
                HomeTile(tr(lang, "UNCERTAINTY", "अनिश्चितता", "अनिश्चितता"), unc, tr(lang, "model + market", "मॉडल + मार्केट", "मॉडेल + मार्केट"), Modifier.weight(1f),
                    when (unc) { "HIGH" -> V8.Red; "MODERATE" -> V8.Amber; "LOW" -> V8.Green; else -> V8.Text1 })
            }
            Text(text = PTx.healthVsEdgeNote(lang), color = V8.Text3, fontSize = 9.sp, lineHeight = 12.sp, modifier = Modifier.padding(top = 6.dp))
            var moreNumbers by remember { mutableStateOf(false) }
            Text(
                text = if (moreNumbers) tr(lang, "LESS  ▴", "कम  ▴", "कमी  ▴") else tr(lang, "MORE NUMBERS (heuristics, ranges)  ▾", "और संख्याएँ (अनुमान, दायरा)  ▾", "अधिक आकडे (अंदाज, पल्ला)  ▾"),
                color = V8.Text3, fontSize = 9.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth().v8Press { moreNumbers = !moreNumbers }.padding(vertical = 10.dp)
            )
            if (moreNumbers) {
            Row(modifier = Modifier.fillMaxWidth()) {
                    val p = analysis.pulse
                    val b = analysis.brain
                    listOf(
                        Triple(tr(lang, "AGREEMENT", "सहमति", "सहमती"), "$rawConf%", tr(lang, "pillars", "पिलर", "पिलर")),
                        Triple(tr(lang, "SETUP", "सेटअप", "सेटअप"), b?.qualityIndex?.toString() ?: "--", tr(lang, "setup (rules)", "सेटअप (नियम)", "सेटअप (नियम)")),
                        // V29A.1: this is the indicator split, not a measured edge (the measured edge lives in Learn → Shadow Lab)
                        Triple(tr(lang, "PILLAR SPLIT", "पिलर बँटवारा", "पिलर विभागणी"), b?.edge ?: "--", tr(lang, "not proven", "साबित नहीं", "सिद्ध नाही")),
                        Triple(
                            tr(lang, "ACCURACY", "सटीकता", "अचूकता"),
                            if (p == null || p.directionN < p.directionMinN) "--" else "${p.directionPct}%",
                            if (p == null) "" else "N=${p.directionN}" + if (p.directionN < p.directionMinN) "/${p.directionMinN}" else ""
                        )
                    ).forEach { (k, v, sub) ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = k, color = V8.Text3, fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, maxLines = 1)
                            Text(text = v, color = V8.Text1, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(text = sub, color = V8.Text3, fontSize = 8.sp, maxLines = 1)
                        }
                    }
                }
                // second row: measured from the ledger; "--" until there is enough real history
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    val p = analysis.pulse
                    listOf(
                        Triple(tr(lang, "STABILITY", "स्थिरता", "स्थिरता"), p?.stability ?: "--", tr(lang, "engine flips", "इंजन बदलाव", "इंजिन बदल")),
                        Triple(tr(lang, "EXP. MOVE", "अनुमानित चाल", "अपेक्षित चाल"), p?.expectedMove ?: "--", tr(lang, "median, pts", "मीडियन, पॉइंट", "मध्यक, पॉइंट")),
                        Triple(tr(lang, "COVERAGE", "कवरेज", "कव्हरेज"), p?.coverage ?: "--", "BUY/SELL"),
                        Triple(tr(lang, "DATA", "डेटा", "डेटा"), p?.dataQuality ?: "--", tr(lang, "quality", "क्वालिटी", "गुणवत्ता"))
                    ).forEach { (k, v, sub) ->
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = k, color = V8.Text3, fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, maxLines = 1)
                            Text(text = v, color = V8.Text1, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                            Text(text = sub, color = V8.Text3, fontSize = 8.sp, maxLines = 1)
                        }
                    }
                }
                // third row: market regime and the real 10-90% range of past calls (needs 30 checked calls and a direction)
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    val p = analysis.pulse
                    val q10 = p?.expectedMoveQ10
                    val q90 = p?.expectedMoveQ90
                    val dir = when (sig) { Signal.BUY -> 1.0; Signal.SELL -> -1.0; else -> 0.0 }
                    val range = if (q10 == null || q90 == null || dir == 0.0) "--" else {
                        val x = analysis.currentPrice + dir * q10
                        val y = analysis.currentPrice + dir * q90
                        String.format(Locale.US, "%.0f–%.0f", minOf(x, y), maxOf(x, y))
                    }
                    val rangeSub = when {
                        dir == 0.0 -> tr(lang, "no direction", "दिशा नहीं", "दिशा नाही")
                        q10 == null -> tr(lang, "needs 30 calls", "30 कॉल चाहिए", "30 कॉल लागतात")
                        else -> tr(lang, "80% of past calls", "पिछली 80% कॉल", "मागील 80% कॉल")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = tr(lang, "REGIME", "स्थिति", "स्थिती"), color = V8.Text3, fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, maxLines = 1)
                        Text(text = analysis.brain?.regime?.replace('_', ' ') ?: "--", color = V8.Text1, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(text = tr(lang, "measured now", "अभी मापा", "आत्ता मोजलेले"), color = V8.Text3, fontSize = 8.sp, maxLines = 1)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = tr(lang, "EXPECTED RANGE", "अनुमानित दायरा", "अपेक्षित पल्ला"), color = V8.Text3, fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, maxLines = 1)
                        Text(text = range, color = V8.Text1, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        Text(text = rangeSub, color = V8.Text3, fontSize = 8.sp, maxLines = 1)
                    }
                }
            }
            if (calibrated == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = tr(lang, "Pillar agreement, not a win probability (not calibrated yet)", "पिलर सहमति है, जीत की संभावना नहीं (अभी कैलिब्रेट नहीं)", "पिलर सहमती आहे, जिंकण्याची शक्यता नाही (अजून कॅलिब्रेट नाही)"), color = V8.Text3, fontSize = 9.sp)
            }
            // honest engine state from the real ledger audit
            analysis.pulse?.let { p ->
                if (p.modelHealth == "CRITICAL" || p.modelHealth == "POLARITY_SUSPECT" || p.modelHealth == "VALIDATING" || p.modelHealth == "WEAK") {
                    val c = if (p.modelHealth == "VALIDATING") V8.Amber else if (p.modelHealth == "WEAK") V8.Amber else V8.Red
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        V8Dot(c)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = p.modelHealthLine, color = c, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (analysis.quantBotSignal?.statusText?.contains("FILTER") == true) {
                Spacer(modifier = Modifier.height(4.dp))
                V8Badge(analysis.quantBotSignal?.statusText ?: "", V8.Learn)
            }
            Spacer(modifier = Modifier.height(6.dp))
            V8Hairline(V8.Line)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = tr(lang, "WHY?  FULL FORECAST  ›", "क्यों?  पूरा अनुमान  ›", "का?  पूर्ण अंदाज  ›"), color = V8.Gold, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp, modifier = Modifier.weight(1f))
                if (pres != null) Text(
                    text = tr(lang, "DECISION TRACE  ›", "फ़ैसले का ब्योरा  ›", "निर्णयाचा तपशील  ›"),
                    color = V8.Gold, fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.2.sp,
                    modifier = Modifier.v8Press { showTrace = true }.padding(vertical = 8.dp, horizontal = 4.dp)
                )
            }
        }

        // ---------------- health strip
        if (health != null) {
            V8Card(level = 2, onClick = onOpenHealth) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    V8Label(tr(lang, "System pulse • app health, not accuracy", "सिस्टम पल्स • ऐप की सेहत, सटीकता नहीं", "सिस्टम पल्स • ॲपचे आरोग्य, अचूकता नाही"))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "${health.overallScore}/100", color = statusColor(health.overallStatus), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(text = "›", color = V8.Text3, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("DATA" to "DATA", "INDICATORS" to "IND", "BOTS" to "BOTS", "AI" to "AI", "LEARNING" to "LEARN").forEach { (key, label) ->
                        val c = health.category(key)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                V8Dot(if (c == null || c.score < 0) V8.Text3 else statusColor(c.status), 5.dp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = label, color = V8.Text3, fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp)
                            }
                            Text(
                                text = if (c == null) "--" else if (c.score < 0) "OFF" else c.summary.substringBefore(" "),
                                color = if (c == null || c.score < 0) V8.Text3 else V8.Text1, fontSize = 12.sp, fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ---------------- what matters now (the risk shown above is not repeated)
        val rest = matters.filter { it !== topRisk }
        if (rest.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                V8Label(tr(lang, "What matters now", "अभी क्या मायने रखता है", "आत्ता काय महत्त्वाचे"))
                Spacer(modifier = Modifier.height(6.dp))
                rest.forEach { m ->
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 4.dp)) {
                        Box(modifier = Modifier.padding(top = 5.dp)) { V8Dot(when (m.level) { "RISK" -> V8.Red; "WATCH" -> V8.Amber; else -> V8.Text3 }, 5.dp) }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = tr(lang, m.en, m.hi, m.mr), color = V8.Text2, style = KType.BodyPrimary)
                    }
                }
            }
        }

        // ---------------- intelligence: quant vs bots vs AI (real answers only)
        val council = analysis.aiCouncil
        V8Card(level = 2, onClick = onOpenAi, accent = when (council?.conflictLevel) {
            "HIGH" -> V8.Red.copy(alpha = 0.5f); "ALIGNED" -> V8.Green.copy(alpha = 0.4f); else -> V8.Line
        }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                V8Label(tr(lang, "Intelligence", "इंटेलिजेंस", "इंटेलिजन्स"))
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = when (council?.conflictLevel) {
                        "HIGH" -> tr(lang, "HIGH CONFLICT", "भारी टकराव", "मोठा संघर्ष")
                        "MINOR" -> tr(lang, "PARTIAL", "आंशिक", "अंशतः")
                        "ALIGNED" -> tr(lang, "STRONG", "मज़बूत", "मजबूत")
                        else -> tr(lang, "AI not connected", "AI कनेक्ट नहीं", "AI कनेक्ट नाही")
                    },
                    color = when (council?.conflictLevel) { "HIGH" -> V8.Red; "MINOR" -> V8.Amber; "ALIGNED" -> V8.Green; else -> V8.Text3 },
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold
                )
                Text(text = "  ›", color = V8.Text3, fontSize = 14.sp)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val bots = analysis.multiBotEnsemble?.ensembleSignal
                val ai = when (council?.consensus) { "BUY" -> Signal.BUY; "SELL" -> Signal.SELL; "WAIT" -> Signal.WAIT; else -> null }
                listOf(
                    Triple("QUANT", sig as Signal?, ""),
                    Triple(tr(lang, "BOTS", "बॉट", "बॉट"), bots, analysis.multiBotEnsemble?.let { "${maxOf(it.buyVotes, it.sellVotes, it.waitVotes)}/${it.totalBots}" } ?: ""),
                    Triple("AI", ai, council?.let { if (it.consensus == "SPLIT") "split" else if (it.eligible == 0) "0/${it.configured}" else "${maxOf(it.buyVotes, it.sellVotes, it.waitVotes)}/${it.eligible}" } ?: "--")
                ).forEach { (t, s2, sub) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = t, color = V8.Text3, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(text = if (s2 == null) "—" else arrowOf(s2) + " " + s2.name, color = s2?.let { signalColor(it) } ?: V8.Text3, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        if (sub.isNotEmpty()) Text(text = sub, color = V8.Text3, fontSize = 9.sp)
                    }
                }
            }
        }

        // ---------------- V10 market brain summary
        analysis.brain?.let { V10BrainStrip(it, lang, onOpenForecast, pres) }

        // ---------------- early warnings
        if (ins != null && ins.warnings.isNotEmpty()) {
            V8Card(level = 2, accent = V8.Amber.copy(alpha = 0.5f)) {
                V8Label(tr(lang, "Early warning", "चेतावनी", "इशारा"), V8.Amber)
                ins.warnings.forEach { w ->
                    Text(text = "⚠ $w", color = V8.Text1, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        // ---------------- regime + story
        if (ins != null) {
            V8Card(level = 2) {
                V8Label(tr(lang, "Market regime", "मार्केट की स्थिति", "मार्केटची स्थिती"))
                Text(text = ins.regime.replace('_', ' '), color = V8.Text1, fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
                Text(
                    text = String.format(Locale.US, "Trend efficiency %d%%  •  ATR %.2f  •  last candle %.1f× ATR", ins.trendEfficiencyPercent, ins.atr, ins.lastRangeVsAtr),
                    color = V8.Text3, fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                V8Label(tr(lang, "What the market is doing", "मार्केट क्या कर रहा है", "मार्केट काय करत आहे"))
                V8KeyValue(tr(lang, "Short term", "छोटा समय", "अल्प काळ"), ins.storyShort)
                V8KeyValue(tr(lang, "Medium term", "मध्यम समय", "मध्यम काळ"), ins.storyMedium)
                V8KeyValue(tr(lang, "Higher timeframe", "बड़ा टाइमफ्रेम", "मोठा टाइमफ्रेम"), ins.storyHigher)
            }
        }

        // ---------------- multi-horizon matrix (real candles)
        val rows = analysis.mtfMatrix?.timeframes?.filter { it.label.startsWith("PAXG") } ?: emptyList()
        V8Card(level = 2) {
            V8Label(tr(lang, "PAXG/USDT proxy — context only", "PAXG/USDT प्रॉक्सी — सिर्फ़ संदर्भ", "PAXG/USDT प्रॉक्सी — फक्त संदर्भ"))
            if (rows.isEmpty()) {
                Text(text = tr(lang, "MULTI-TIMEFRAME CONTEXT UNAVAILABLE • optional context, the XAU/USD decision does not use it", "मल्टी-टाइमफ़्रेम संदर्भ उपलब्ध नहीं • वैकल्पिक, XAU/USD फ़ैसला इसे इस्तेमाल नहीं करता", "मल्टी-टाइमफ्रेम संदर्भ उपलब्ध नाही • पर्यायी, XAU/USD निर्णय तो वापरत नाही"), color = V8.Text3, fontSize = 11.sp)
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    listOf("TF" to 0.18f, "DIR" to 0.17f, "RSI" to 0.17f, tr(lang, "PAXG LEVEL", "PAXG लेवल", "PAXG लेव्हल") to 0.48f).forEach { (h, w) ->
                        Text(text = h, color = V8.Text3, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(w))
                    }
                }
                rows.forEach { r ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = r.timeframe, color = V8.Text1, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.18f))
                        Text(text = arrowOf(r.signal) + " " + r.signal.name, color = signalColor(r.signal), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(0.17f))
                        Text(text = "${r.momentumPercent}", color = V8.Text2, fontSize = 11.sp, modifier = Modifier.weight(0.17f))
                        // V29C.1: a PAXG/USDT price, never shown as an XAU/USD level
                        Text(text = com.example.livegoldai.data.PlanText.proxyLevel(r.keyLevel), color = V8.Text3, fontSize = 10.sp, modifier = Modifier.weight(0.48f))
                    }
                }
                Text(text = tr(lang, "Rule: EMA9 vs EMA21 + close vs EMA50 • PAXG/USDT, a different instrument: context only, not used in the XAU/USD decision", "नियम: EMA9 बनाम EMA21 + क्लोज़ बनाम EMA50 • PAXG/USDT, अलग इंस्ट्रूमेंट: सिर्फ संदर्भ, XAU/USD फैसले में शामिल नहीं", "नियम: EMA9 विरुद्ध EMA21 + क्लोज विरुद्ध EMA50 • PAXG/USDT, वेगळे इन्स्ट्रुमेंट: फक्त संदर्भ, XAU/USD निर्णयात नाही"), color = V8.Text3, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        V8Card(level = 3, onClick = onOpenPlaybook) {
            Text(text = com.example.livegoldai.data.PlanText.playbookLink(sig == Signal.WAIT, lang), color = if (sig == Signal.WAIT) V8.Text2 else V8.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** V29B: one small labelled number on the Home forecast card. */
@Composable
private fun HomeTile(k: String, v: String, sub: String, modifier: Modifier, vc: androidx.compose.ui.graphics.Color = V8.Text1) {
    Column(modifier = modifier) {
        Text(text = k, color = V8.Text3, fontSize = 8.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, maxLines = 1)
        Text(text = v, color = vc, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(text = sub, color = V8.Text3, fontSize = 8.sp, maxLines = 1)
    }
}
