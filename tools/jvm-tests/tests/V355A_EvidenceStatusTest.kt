import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// V35.5-A: typed Macro evidence, honest health, phase-safe cache, NFP 6 Nov 2026 timeline. SYNTHETIC FIXTURES ONLY.
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
fun u(s: String) = sdf.parse(s)!!.time
const val M = 60_000L

// the V35.3-F move() exactly as it was before V35.5-A, to prove the measured move is unchanged
fun oldMove(bars: List<Pair<Long, Double>>, eventMs: Long, now: Long): NewsMacro.Move? {
    if (bars.groupBy { it.first }.values.any { g -> g.map { it.second }.distinct().size > 1 }) return null
    val b = bars.distinctBy { it.first }.filter { it.first + NewsMacro.BAR_MS <= now && it.second.isFinite() && it.second > 0 }.sortedBy { it.first }
    val cur = b.lastOrNull() ?: return null
    if (now - (cur.first + NewsMacro.BAR_MS) > NewsMacro.MAX_AGE_MS) return null
    val after = now >= eventMs
    val (baseAt, label) = if (!after) (now - 60 * 60_000L) to "last 60 min" else eventMs to "since the release"
    if (after && cur.first < eventMs) return null
    val base = b.lastOrNull { it.first + NewsMacro.BAR_MS <= baseAt } ?: return null
    if (base.first >= cur.first) return null
    return NewsMacro.Move(cur.second - base.second, base.first, cur.first, label)
}

fun main() {
    val t = u("2026-11-06 13:30:00")                   // NFP: 08:30 EST = 19:00 IST
    val cal = OfficialCalendar.events()
    val id = "NFP@$t"

    // ---- NFP timeline (IST = UTC + 5:30)
    check("17:59:59 IST: OFF", MacroWindow.phase(cal, u("2026-11-06 12:29:59")) == "OFF")
    check("18:00 IST: window opens (PRE)", MacroWindow.phase(cal, u("2026-11-06 12:30:00")) == "$id:PRE", MacroWindow.phase(cal, u("2026-11-06 12:30:00")))
    check("19:00 IST: release (POST)", MacroWindow.phase(cal, t) == "$id:POST")
    check("19:30 IST: last moment of the window", MacroWindow.phase(cal, u("2026-11-06 14:00:00")) == "$id:POST")
    check("19:30:01 IST: OFF", MacroWindow.phase(cal, u("2026-11-06 14:00:01")) == "OFF")

    // ---- the measured move is the same as before (fuzz against the old code)
    var same = 0; val N = 3000
    for (seed in 1..N) {
        val r = Random(seed.toLong()); val now = t + (r.nextInt(130) - 70) * M + r.nextInt(60) * 1000L
        val bars = (0 until 40).map { k -> (t - 100 * M + k * 5 * M) to 100 + r.nextGaussian() }.filter { r.nextDouble() > 0.15 }
            .let { if (seed % 50 == 0) it + (it.first().first to 7.0) else it }
        if (oldMove(bars, t, now) == NewsMacro.move(bars, t, now)) same++
    }
    check("move() identical to V35.3-F on $N random series", same == N, "$same")

    // ---- typed reasons
    val s = (0 until 40).map { k -> (t - 100 * M + k * 5 * M) to 100.0 + if (t - 100 * M + k * 5 * M >= t) 0.2 else 0.0 }
    check("no completed bar after release at 19:04 IST -> INCOMPLETE", NewsMacro.assess(s, t, t + 4 * M).second == EvidenceStatus.INCOMPLETE)
    val at = NewsMacro.assess(s, t, t + 5 * M)
    check("first completed post-release bar at 19:05 IST -> OBSERVED_COMPLETE", at.second == EvidenceStatus.OBSERVED_COMPLETE && at.first!!.toMs == t)
    check("newest bar older than 20 min -> DELAYED", NewsMacro.assess(s.filter { it.first < t - 30 * M }, t, t - 2 * M).second == EvidenceStatus.DELAYED)
    check("two prices for one time -> UNTRUSTED", NewsMacro.assess(s + (s[3].first to 99.0), t, t + 6 * M).second == EvidenceStatus.UNTRUSTED)
    check("no bars -> UNAVAILABLE", NewsMacro.assess(emptyList(), t, t).second == EvidenceStatus.UNAVAILABLE)

    // ---- each source says what it is
    val bar = { inst: String, c: Double -> (0 until 40).map { k -> val st = t - 100 * M + k * 5 * M; CandleAggregator.Bar(st, c, c, c, if (st >= t) c * (if (inst == "EUR_USD") 0.997 else 1.0) else c) } }
    val px = mapOf("EUR_USD" to 1.08, "USD_JPY" to 150.0, "GBP_USD" to 1.27, "USD_CAD" to 1.36, "USD_SEK" to 10.5, "USD_CHF" to 0.88)
    val dx = OandaMacro.dollarIndex(px.mapValues { bar(it.key, it.value) }, t, t + 7 * M, "NFP")!!.let { it.copy(evidence = it.evidence.copy(eventId = id)) }  // stamped by the service
    check("6-pair DXY: OANDA COMPUTED, bar 13:30-13:35 UTC", dx.evidence.provider == "OANDA" && dx.evidence.sourceKind == "COMPUTED" && dx.evidence.barStartMs == t && dx.evidence.barEndMs == t + 5 * M && dx.evidence.usableInWindow, "${dx.evidence}")
    val eur = bar("EUR_USD", 1.08).map { it.startMs to it.close }
    val pr = NewsMacro.usdProxy(eur, t, t + 7 * M, "NFP")!!
    check("EUR/USD proxy: PROXY, inverted unit", pr.evidence.sourceKind == "PROXY" && pr.evidence.unit.contains("inverted") && pr.symbol == NewsMacro.PROXY_SYMBOL)
    val note = OandaMacro.note(bar("USB10Y_USD", 110.0), t, t + 7 * M, "NFP")!!.let { it.copy(evidence = it.evidence.copy(eventId = id)) }
    check("T-Note: note price, not yield", note.evidence.unit == "note price" && note.evidence.instrument == OandaMacro.NOTE)
    val tnx = NewsMacro.yield10(eur.map { it.first to 4.5 }, t, t + 7 * M, "NFP")!!
    check("Yahoo ^TNX: yield %", tnx.evidence.unit == "yield %" && tnx.evidence.provider == "YAHOO")

    // ---- D1: no data in the window is never 'Live'
    val none = NewsMacro.windowFallback(null, true, listOf(EvidenceStatus.INCOMPLETE, EvidenceStatus.UNAVAILABLE), t, t + 4 * M, "NFP", id)
    check("fallback without daily: not data, no vote", !MacroEvidenceView.hasData(none) && !none.evidence.voteEligible && none.evidence.status == EvidenceStatus.INCOMPLETE && none.evidence.eventId == id)
    check("health: WARN, never Live", MacroEvidenceView.health("US Dollar (Macro data)", none).let { it.second == "WARN" && !it.third.contains("Live") })
    val daily = MacroMarketIndex("DXY", "US Dollar Index", 99.5, -0.3, Signal.BUY, "x", MacroEvidence(EvidenceStatus.CONTEXT_ONLY, "YAHOO", "DX-Y.NYB", "DAILY", "index points"))
    val withDaily = NewsMacro.windowFallback(daily, true, listOf(EvidenceStatus.DELAYED), t, t + 15 * M, "NFP", id)
    check("fallback with daily: DELAYED, context shown, WARN, no vote", withDaily.evidence.status == EvidenceStatus.DELAYED && withDaily.impactOnGold == Signal.WAIT &&
        MacroEvidenceView.health("x", withDaily).let { it.second == "WARN" && it.third.contains("DELAYED") && it.third.contains("daily context") })
    check("outside window: daily context OK", MacroEvidenceView.health("x", daily).let { it.second == "OK" && it.third.startsWith("Live (daily context, no vote)") })
    check("observed minute data: OK with source and bar", MacroEvidenceView.health("x", dx).let { it.second == "OK" && it.third.contains("OANDA COMPUTED") && it.third.contains("13:30-13:35 UTC") })

    // ---- D3: the engine reads the status, not the words
    val candles = (0 until 150).map { k -> val o = 4000.0 + k * 0.4; CandleBar(sdf.format(Date(t + 7 * M - (150 - k) * 5 * M)), o, o + 0.6, o - 0.5, o + 0.35) }
    val now = t + 7 * M
    val reworded = none.copy(explanation = "News window (NFP): no completed 5-minute price yet, Macro not voting")
    val a1 = TechnicalEngine.analyze(candles, "5m", reworded, null, cal, nowMs = now)
    check("reworded no-data text: Macro still inactive", !a1.groups.first { it.key == "macro" }.active)
    val a2 = TechnicalEngine.analyze(candles, "5m", dx.copy(explanation = "unavailable waiting for the first"), null, cal, nowMs = now)
    check("observed data with odd text: Macro active", a2.groups.first { it.key == "macro" }.active)
    val a3 = TechnicalEngine.analyze(candles, "5m", daily, null, cal, nowMs = now)
    check("daily context in a window: no vote, no weight", !a3.groups.first { it.key == "macro" }.active)

    // ---- SYSTEM, health rows and Trust card read the same evidence
    val feed = FeedStatus("OANDA XAU/USD", now, 300, true, MacroEvidenceView.hasData(none), MacroEvidenceView.hasData(none), 80, 1)
    val a0 = TechnicalEngine.analyze(candles, "5m", none, none.copy(symbol = "^TNX"), cal, nowMs = now).copy(feed = feed)
    val rows = HealthMonitor().build(a0, null, 0, now).categories.flatMap { it.checks }
    val dRow = rows.first { it.name == "US Dollar (Macro data)" }
    check("SYSTEM dollar row: WARN INCOMPLETE (repro case fixed)", dRow.status == "WARN" && dRow.detail.contains("INCOMPLETE"), "${dRow.status} ${dRow.detail}")
    check("feed flag false for a placeholder", !feed.dxyAvailable)
    val w = CockpitInsightsBuilder.macroWindowCheck(a0)
    check("Macro window row names both states", w.status == "WARN" && w.detail.contains("dollar INCOMPLETE") && w.detail.contains("10Y INCOMPLETE"), w.detail)
    val wOn = CockpitInsightsBuilder.macroWindowCheck(TechnicalEngine.analyze(candles, "5m", dx, note, cal, nowMs = now))
    check("Macro window row active with both observed", wOn.status == "OK" && wOn.detail.contains("minute data: dollar + 10Y") && wOn.detail.contains("OBSERVED_COMPLETE"), wOn.detail)
    val nl = TrustCard.newsLine(a0, AppLanguage.ENGLISH)
    check("Trust card: event + statuses + no vote", nl.startsWith("NFP 06 Nov") && nl.contains("dollar INCOMPLETE") && nl.contains("no vote"), nl)
    check("Trust card OFF outside window", TrustCard.newsLine(TechnicalEngine.analyze(candles, "5m", daily, null, emptyList(), nowMs = now), AppLanguage.ENGLISH).startsWith("OFF"))

    // ---- D2: a cached analysis never crosses a phase
    val ttl = 30 * M
    check("OFF cache at 17:55 not reused at 18:01 (window opened)", !MacroWindow.cacheReusable(u("2026-11-06 12:25:00"), "OFF", u("2026-11-06 12:31:00"), MacroWindow.phase(cal, u("2026-11-06 12:31:00")), ttl))
    check("pre-release cache at 18:58 not reused at 19:01", !MacroWindow.cacheReusable(t - 2 * M, "$id:PRE", t + M, MacroWindow.phase(cal, t + M), ttl))
    check("post-release cache not reused after the window closes", !MacroWindow.cacheReusable(u("2026-11-06 13:58:00"), "$id:POST", u("2026-11-06 14:01:00"), "OFF", ttl))
    check("inside a phase: at most 5 min", MacroWindow.cacheReusable(t + M, "$id:POST", t + 5 * M, "$id:POST", ttl) && !MacroWindow.cacheReusable(t + M, "$id:POST", t + 6 * M + 1, "$id:POST", ttl))
    check("outside windows: normal pacing kept (quota safe)", MacroWindow.cacheReusable(u("2026-11-06 10:00:00"), "OFF", u("2026-11-06 10:25:00"), "OFF", ttl))
    check("unknown phase of a cached result: refetch", !MacroWindow.cacheReusable(t, null, t + 1000, "OFF", ttl))
    check("clock went back: refetch", !MacroWindow.cacheReusable(t + 10 * M, "OFF", t, "OFF", ttl))

    // ---- source: no availability or eligibility from null checks / text
    val svc = File("app/src/main/java/com/example/livegoldai/data/GoldApiService.kt").readText()
    val eng = File("app/src/main/java/com/example/livegoldai/data/TechnicalEngine.kt").readText()
    check("service flags from typed evidence", !svc.contains("dxyDeferred.await() != null") && svc.split("MacroEvidenceView.hasData(dxyDeferred.await())").size == 5)
    check("cache checks the phase", svc.contains("MacroWindow.cacheReusable(cached.first, cachePhase[normInterval], now, phase, ttl)") && svc.split("cachePhase[normInterval] = phase").size == 5)
    check("engine eligibility not from text", eng.contains("fun usable(m: MacroMarketIndex?) = m?.evidence?.usableInWindow == true") && !eng.contains("explanation.contains(\"unavailable\""))
    check("thresholds unchanged", NewsMacro.DXY_MIN_PCT == 0.08 && NewsMacro.YIELD_MIN_BP == 2.0 && OandaMacro.NOTE_MIN_PCT == 0.15 && NewsMacro.MAX_AGE_MS == 20 * M && TechnicalEngine.PILLAR_WEIGHTS["macro"] == 2.5)
    println("V35.5-A evidence status: $pass passed, $fail failed")
}
