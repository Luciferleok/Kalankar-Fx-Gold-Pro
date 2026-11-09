import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }

fun main() {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val now = sdf.parse("2026-10-28 10:22:10")!!.time; val iv = 300_000L
    val td = FeedStatus("Twelve Data XAU/USD", now - 5_000, 300, true, true, true, 0, 1)
    val empty = LedgerState(emptyList(), emptyMap(), emptyList())
    val kinds = TrustCard.Kind.values().toList()
    var calls = 0; var waits = 0; var research = 0; var qualified = 0
    for (seed in 1L..400L) {
        val rnd = Random(seed); var p = 4100.0; val drift = if (seed % 2 == 0L) 0.0 else (rnd.nextDouble() - 0.5) * 1.6
        val candles = (0 until 150).map { i -> val o = p; p += drift + (rnd.nextDouble() - 0.5) * (if (seed % 2 == 0L) 4.0 else 1.2); CandleBar(sdf.format(Date((now / iv) * iv - (149 - i) * iv)), o, maxOf(o, p) + 0.6, minOf(o, p) - 0.6, p) }
        val raw = TechnicalEngine.analyze(candles, "5m").copy(feed = td, sourceState = SourceStateMachine.PRIMARY_ACTIVE)
        val mode = if (seed % 3 == 0L) "LIVE QUALIFIED" else "COLLECTING"
        val a = PlanStamp.stamp(raw, empty, now, mode).let { it.copy(presentation = PresentationBuilder.build(it, empty, mode, now)) }
        for (lang in AppLanguage.values()) {
            val c = TrustCard.build(a, now, lang)
            check("canonical decision", c.decision == a.overallSignal)
            check("five metrics, one per kind, in order", c.metrics.map { it.kind } == kinds, "$seed")
            val cal = c.metrics.first { it.kind == TrustCard.Kind.CALIBRATED_PROBABILITY }
            check("calibrated probability never shown as a number", !cal.value.any { it.isDigit() } && cal.good == false, cal.value)
            val rule = c.metrics.first { it.kind == TrustCard.Kind.RULE_AGREEMENT }
            if (lang == AppLanguage.ENGLISH) {
                check("rule agreement labelled 'not a probability'", rule.note.contains("not a probability"))
                check("hit-rate labelled as past data", c.metrics[1].note.contains("not today's chance"))
                check("experimental: not used in this forecast", c.metrics[4].note.contains("not used in this forecast"))
            }
            check("max three factors each side", c.forSide.size <= 3 && c.againstSide.size <= 3)
            check("no metric value claims a probability", c.metrics.none { it.value.contains("probab", true) })
            if (a.overallSignal == Signal.WAIT) {
                check("WAIT: no levels, no expiry, no evidence", c.levels == null && c.expiresAtMs == 0L && c.evidence.isEmpty())
                check("WAIT: status WAIT", c.status == "WAIT")
                check("WAIT: no hit-rate number", !c.metrics[1].value.any { it.isDigit() }, c.metrics[1].value)
                check("WAIT: no agreement %", !rule.value.contains("%"))
                check("WAIT: factors point up / down only", c.forTitle.isNotBlank() && c.againstTitle.isNotBlank())
            } else {
                val sc = ResearchScenario.of(a, now)
                check("call: levels from the one frozen scenario", (sc == null && c.levels == null) || (sc != null && c.levels!!.entry == sc.entry && c.levels!!.stop == sc.stop && c.levels!!.tp1 == sc.tp1 && c.levels!!.tp2 == sc.tp2))
                check("call: expiry is the setup's", c.expiresAtMs == a.tradeSetup.expiresAtMs)
                check("call: status follows plan stamp", when (a.tradeSetup.planStatus) { PlanStamp.ACTIVE -> c.status == "QUALIFIED"; PlanStamp.RESEARCH -> c.status == "RESEARCH"; else -> c.status == "NOT ACTIONABLE" }, "${a.tradeSetup.planStatus} ${c.status}")
                check("call: agreement shows % and votes", rule.value.contains("%") && rule.value.contains("/${a.totalGroups}"))
                val pres = a.presentation
                // V35.5-E: the value is the exact-match record; the agreement-band number only as labelled general context
                if (pres != null) check("call: hit-rate shows exact N", c.metrics[1].value.contains("N=${pres.exactHitN}"), c.metrics[1].value)
                if (pres != null && pres.hitRateN > 0 && pres.hitRatePct >= 0 && lang == AppLanguage.ENGLISH) check("general band only as GENERAL HISTORICAL CONTEXT", c.metrics[1].note.contains("GENERAL HISTORICAL CONTEXT") && c.metrics[1].note.contains("N=${pres.hitRateN}"))
            }
            if (lang == AppLanguage.ENGLISH) { if (a.overallSignal == Signal.WAIT) waits++ else { calls++; if (c.status == "RESEARCH") research++; if (c.status == "QUALIFIED") qualified++ } }
        }
    }
    check("fixtures cover calls and WAITs", calls > 20 && waits >= 5, "calls=$calls waits=$waits")
    // stale price: data health is not good
    run {
        val rnd = Random(9); var p = 4100.0
        val candles = (0 until 150).map { i -> val o = p; p += 0.4 + (rnd.nextDouble() - 0.5); CandleBar(sdf.format(Date((now / iv) * iv - (149 - i) * iv)), o, maxOf(o, p) + 0.6, minOf(o, p) - 0.6, p) }
        val stale = TechnicalEngine.analyze(candles, "5m").copy(feed = td.copy(fetchedAtMs = now - 3_600_000L), sourceState = SourceStateMachine.PRIMARY_ACTIVE)
        val c = TrustCard.build(stale, now, AppLanguage.ENGLISH)
        check("stale feed: data health not good", c.metrics[3].good == false, c.metrics[3].value)
        check("data health names the Macro window", c.metrics[3].note.startsWith("Macro news window: "), c.metrics[3].note)
    }
    // forced overflow: six factors each side still shows three; a WAIT never shows levels even if a setup is left over
    run {
        val rnd = Random(4); var p = 4100.0
        val candles = (0 until 150).map { i -> val o = p; p += 0.5 + (rnd.nextDouble() - 0.5); CandleBar(sdf.format(Date((now / iv) * iv - (149 - i) * iv)), o, maxOf(o, p) + 0.6, minOf(o, p) - 0.6, p) }
        val a0 = PlanStamp.stamp(TechnicalEngine.analyze(candles, "5m").copy(feed = td, sourceState = SourceStateMachine.PRIMARY_ACTIVE), empty, now, "COLLECTING")
            .let { it.copy(presentation = PresentationBuilder.build(it, empty, "COLLECTING", now)) }
        val six = (1..6).map { PredictionPresentationState.Factor("f$it", it.toDouble()) }
        val a = a0.copy(presentation = a0.presentation!!.copy(supporting = six, conflicting = six.map { it.copy(points = -it.points) }))
        val c = TrustCard.build(a, now, AppLanguage.ENGLISH)
        check("overflow fixture is a call", a.overallSignal != Signal.WAIT)
        check("six supporting -> three shown", c.forSide.size == 3 && c.forSide[0] == "f1 +1", "${c.forSide}")
        check("six opposing -> three shown", c.againstSide.size == 3)
        val w = TrustCard.build(a.copy(overallSignal = Signal.WAIT), now, AppLanguage.ENGLISH)
        check("WAIT with leftover setup: no levels, no expiry", w.levels == null && w.expiresAtMs == 0L && w.status == "WAIT")
    }
    // the card is on HOME
    val ck = File("app/src/main/java/com/example/livegoldai/ui/v8/V8CockpitScreen.kt").readText()
    check("cockpit shows the trust card", ck.contains("V8TrustCard(analysis, lang, now)"))
    println("calls=$calls (research=$research qualified=$qualified) waits=$waits")
    println("V355 TrustCard: $pass passed, $fail failed")
}
