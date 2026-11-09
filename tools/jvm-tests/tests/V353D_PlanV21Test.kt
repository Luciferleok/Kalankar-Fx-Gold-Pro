import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
fun t(s: String) = sdf.parse(s)!!.time
const val MIN = 60_000L
fun rec(created: Long, method: String, side: Signal = Signal.BUY) = LedgerRecord(id = "p", createdAt = created, expiresAt = created + 25 * MIN, interval = "5m", horizonMin = 25,
    barId = "b", price = 4000.0, rawSignal = side, finalSignal = side, confidence = 70, atr = 2.0, slDistance = 5.0, regime = "TREND", session = "LONDON",
    newsActive = false, modelVersion = "", appliedFilter = "", sources = emptyMap(),
    planEntry = 4000.0, planStop = if (side == Signal.BUY) 3995.0 else 4005.0, planTp1 = if (side == Signal.BUY) 4006.0 else 3994.0, planTp2 = if (side == Signal.BUY) 4008.0 else 3992.0, planMethod = method)
fun bar(t: Long, o: Double, h: Double, l: Double, c: Double) = PathBar(t, o, h, l, c)
fun flat(from: Long, n: Int, px: Double) = (0 until n).map { k -> bar(from + k * MIN, px, px + 0.3, px - 0.3, px) }

fun main() {
    val c = t("2026-11-02 10:00:40")
    val m0 = t("2026-11-02 10:00:00")
    // A: stop touched inside the publication minute (time unknown), TP1 reached later in full minutes
    val pathA = PricePath("OANDA", listOf(bar(m0, 3998.0, 4001.0, 3994.0, 4000.5)) + (1..20).map { k -> bar(m0 + k * MIN, 4000.5 + k * 0.4, 4001.0 + k * 0.4, 4000.0 + k * 0.4, 4000.9 + k * 0.4) })
    check("V2 (old records) unchanged: STOP", PredictionLedger.planOutcome(rec(c, "PLAN-V2"), pathA) == PredictionLedger.PLAN_STOP)
    check("V2.1: publication-minute stop touch -> BOUNDARY_UNCERTAIN", PredictionLedger.planOutcome(rec(c, "PLAN-V2.1"), pathA) == PredictionLedger.PLAN_BOUNDARY)
    // B: TP1 touch only in the minute that runs past expiry
    val r = rec(c, "PLAN-V2.1"); val exp = r.expiresAt
    val pathB = PricePath("OANDA", flat(m0, 25, 3999.9) + bar(t("2026-11-02 10:25:00"), 4000.1, 4006.5, 4000.0, 4006.2))
    check("V2 (old) counts the after-expiry minute: TP1", PredictionLedger.planOutcome(rec(c, "PLAN-V2"), pathB) == PredictionLedger.PLAN_TP1)
    check("V2.1: TP1 only in the minute past expiry -> BOUNDARY_UNCERTAIN", PredictionLedger.planOutcome(r, pathB) == PredictionLedger.PLAN_BOUNDARY, PredictionLedger.planOutcome(r, pathB))
    // C: clean case: entry and TP1 in full minutes, nothing at the edges -> TP1
    val pathC = PricePath("OANDA", listOf(bar(m0, 4002.0, 4002.4, 4001.8, 4002.1)) + (1..24).map { k -> val p = if (k < 3) 4001.0 - k * 0.6 else 3999.8 + (k - 3) * 0.5; bar(m0 + k * MIN, p, p + 0.3, p - 0.3, p) })
    check("V2.1 clean path -> TP1", PredictionLedger.planOutcome(r, pathC) == PredictionLedger.PLAN_TP1, PredictionLedger.planOutcome(r, pathC))
    // D: stop in a full minute after entry -> STOP (verified)
    val pathD = PricePath("OANDA", listOf(bar(m0, 4002.0, 4002.4, 4001.8, 4002.1)) + (1..24).map { k -> val p = 4001.0 - k * 0.4; bar(m0 + k * MIN, p, p + 0.3, p - 0.3, p) })
    check("V2.1 verified stop -> STOP", PredictionLedger.planOutcome(r, pathD) == PredictionLedger.PLAN_STOP, PredictionLedger.planOutcome(r, pathD))
    // E: entry only in the publication minute, never again -> uncertain (before: entered)
    val pathE = PricePath("OANDA", listOf(bar(m0, 4000.5, 4001.0, 3999.8, 4000.9)) + (1..25).map { k -> bar(m0 + k * MIN, 4001.5, 4002.0, 4001.2, 4001.6) })
    check("V2.1 entry touched only before publication -> BOUNDARY_UNCERTAIN", PredictionLedger.planOutcome(r, pathE) == PredictionLedger.PLAN_BOUNDARY)
    // F: published exactly on the minute: no partial first minute
    val r0 = rec(t("2026-11-02 10:01:00"), "PLAN-V2.1")
    check("published on a minute boundary: the minute before is not read", PredictionLedger.planOutcome(r0, PricePath("OANDA", listOf(bar(m0, 3998.0, 4001.0, 3990.0, 4000.5)) + flat(m0 + MIN, 25, 4000.0))) == PredictionLedger.PLAN_OPEN)
    // G: SELL mirror
    val rs = rec(c, "PLAN-V2.1", Signal.SELL)
    val pathG = PricePath("OANDA", listOf(bar(m0, 4002.0, 4006.0, 4001.0, 4001.5)) + flat(m0 + MIN, 25, 4000.0))
    check("SELL: stop touched in the publication minute -> BOUNDARY_UNCERTAIN", PredictionLedger.planOutcome(rs, pathG) == PredictionLedger.PLAN_BOUNDARY)
    // new records get V2.1
    val src = java.io.File("app/src/main/java/com/example/livegoldai/data/LearningCoordinator.kt").readText()
    check("new records are written as PLAN-V2.1", src.contains("planMethod = PredictionLedger.PLAN_METHOD_V21"))
    // encode/decode keeps the method
    val enc = PredictionLedger.encodeRecord(r)
    check("ledger line keeps PLAN-V2.1", enc.contains("\"pm\":\"PLAN-V2.1\""))
    println("V35.3-D PLAN-V2.1: $pass passed, $fail failed")
    if (fail > 0) kotlin.system.exitProcess(1)
}
