import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.text.SimpleDateFormat
import java.util.*

// V35.13.1: Trust card agreement on a held WAIT (owner screenshot 6 Nov 09:49: "below gate" at 85% agreement). SYNTHETIC.
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name :: $d") } }
val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
fun main() {
    val now = sdf.parse("2026-11-04 10:00:00")!!.time
    val cs = (0 until 150).map { i -> val o = 4100.0 - i * 0.8; CandleBar(sdf.format(Date(now - (149 - i) * 300_000L)), o, o + 0.3, o - 1.0, o - 0.7) }
    val base = TechnicalEngine.analyze(cs, "5m").copy(feed = FeedStatus("OANDA XAU_USD (practice)", now - 5_000, 300, true, true, true, 0, 1), lastUpdated = cs.last().datetime)
    val sell = base.copy(overallSignal = Signal.SELL, rawSignal = null, agreementPercent = 85.0)
    val held = ProvenGate.apply(sell, LedgerState(emptyList(), emptyMap(), emptyList()))
    check("fixture held as UNPROVEN", held.overallSignal == Signal.WAIT && RealityEngine.waitReason(held).first == "UNPROVEN")
    val c = TrustCard.build(held, now, AppLanguage.ENGLISH)
    val chip = c.chips.first { it.first == "AGREEMENT" }.second
    check("held WAIT shows its real agreement, marked held", chip == "85% (held)", chip)
    check("metric too", c.metrics.first { it.kind == TrustCard.Kind.RULE_AGREEMENT }.value == "85% (held)", c.metrics.first { it.kind == TrustCard.Kind.RULE_AGREEMENT }.value)
    val below = base.copy(overallSignal = Signal.WAIT, rawSignal = Signal.WAIT, insights = CockpitInsightsBuilder.build(base).copy(bullishPoints = 40.0, bearishPoints = -20.0, gatePercent = 68.0))
    check("real below-gate WAIT still says below gate", TrustCard.build(below, now, AppLanguage.ENGLISH).chips.first { it.first == "AGREEMENT" }.second == "below gate")
    println("V35.13.1 held agreement: $pass passed, $fail failed")
}
