import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File

// V37-A.2 learned filters wait for the owner's approval. SYNTHETIC FIXTURES.
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name :: $d") } }
const val M = 60_000L
fun rec(i: Int, at: Long, regime: String) = LedgerRecord(id = "r$i", createdAt = at, expiresAt = at + 25 * M, interval = "5m", horizonMin = 25, barId = "b$i",
    price = 4000.0, rawSignal = Signal.BUY, finalSignal = Signal.BUY, confidence = 80, atr = 4.0, slDistance = 0.0, regime = regime, session = "S",
    newsActive = false, modelVersion = "", appliedFilter = "", sources = emptyMap(), dataSource = "OANDA XAU_USD", engineVersion = PredictionLedger.ENGINE_VERSION)
fun res(r: LedgerRecord, up: Boolean) = LedgerResult(r.id, r.expiresAt, if (up) LedgerOutcome.CORRECT else LedgerOutcome.INCORRECT, "x", 4000.0,
    if (up) 4002.0 else 3998.0, if (up) 2.0 else -2.0, 1.0, 0.0, 0.0, 0L, 0.0, emptyList(), emptyMap(), emptyList(), "", emptyList())
fun main() {
    val t0 = 1_000_000_000L
    // shadow test of C-RANGE started at t0; after it: RANGE calls wrong, the rest right -> the shadow test passes
    // + 60 older low-agreement calls, all wrong: makes another filter (C-AGR60) eligible for a NEW shadow test
    val old = (0 until 60).map { i -> rec(1000 + i, t0 - (i + 1) * 30 * M, "TREND_UP").copy(confidence = 55) }
    val rs = (0 until 100).map { i -> rec(i, t0 + (i + 1) * 30 * M, if (i < 40) "RANGE" else "TREND_UP") } + old
    val results = (rs.mapIndexed { i, r -> res(r, i in 40 until 100) }).associateBy { it.id }
    val shadow = LedgerEvent(t0, "cand", "C-RANGE", "SHADOW", "test")
    var st = LedgerState(rs, results, listOf(shadow))
    val now = t0 + 200 * 30 * M
    val c1 = LearningEngine.runCycle(st, now, 0)
    check("passed shadow test -> READY, not PROMOTED", c1.newEvents.map { it.candidateId to it.stage } == listOf("C-RANGE" to LearningEngine.READY), "${c1.newEvents}")
    st = LedgerState(rs, results, st.events + c1.newEvents)
    check("READY is not active: no live change", LearningEngine.activeFilterIds(st).isEmpty())
    check("fixture: another filter is eligible", LearningEngine.snapshot(LedgerState(rs, results, emptyList()), "5m", now, emptyList()).candidates.any { it.id == "C-AGR60" && it.stage == "CANDIDATE" })
    val c2 = LearningEngine.runCycle(st, now + M, 0)
    check("next cycle: still waiting, nothing promoted or started", c2.newEvents.isEmpty() && c2.report.any { it.contains("waits for the owner's approval") }, "${c2.newEvents} ${c2.report}")
    val snap = LearningEngine.snapshot(st, "5m", now, emptyList())
    check("Learn shows it as READY first, engine AWAITING_APPROVAL", snap.candidates.first().let { it.id == "C-RANGE" && it.stage == "READY" } && snap.engineState == "AWAITING_APPROVAL", "${snap.candidates.first().stage} ${snap.engineState}")
    // owner decisions
    LearningEngine.ownerDecision(st, "C-RANGE", true, now, promotionLocked = true, lockReason = "engine under review").let { (ev, msg) ->
        check("approve refused while promotion is locked", ev == null && msg.contains("engine under review"), msg) }
    val (ok, okMsg) = LearningEngine.ownerDecision(st, "C-RANGE", true, now)
    check("approve -> PROMOTED by the owner", ok?.stage == "PROMOTED" && ok.detail.contains("Approved by the owner"), okMsg)
    check("approved filter is active", LearningEngine.activeFilterIds(LedgerState(rs, results, st.events + ok!!)).contains("C-RANGE"))
    val (no, _) = LearningEngine.ownerDecision(st, "C-RANGE", false, now)
    check("decline -> REJECTED, stays off", no?.stage == "REJECTED" && LearningEngine.activeFilterIds(LedgerState(rs, results, st.events + no)).isEmpty())
    check("a filter that is not READY cannot be approved", LearningEngine.ownerDecision(LedgerState(rs, results, listOf(shadow)), "C-RANGE", true, now).first == null)
    check("unknown id refused", LearningEngine.ownerDecision(st, "C-NOPE", true, now).first == null)
    // wiring
    val R = "app/src/main/java/com/example/livegoldai/"
    val d = File(R + "ui/components/PredictionErrorAnalyzerDialog.kt").readText()
    check("Approve / Decline buttons, 48dp", d.contains("if (c.stage == \"READY\")") && d.contains("onDecision(c.id, true)") && d.contains("onDecision(c.id, false)") && d.contains("heightIn(min = 48.dp)"))
    // CI #226: the parameter must reach LearningCenterContent (used by the dialog AND the LEARN tab)
    check("both entry points pass the decision down", d.contains("onFilterDecision = onFilterDecision)") && d.contains("    onFilterDecision: (String, Boolean) -> Unit = { _, _ -> },   // V37-A.2") &&
        Regex("4 -> LearningCenterContent\\([\\s\\S]{0,300}onFilterDecision = \\{ id, approve -> viewModel.decideLearnedFilter").containsMatchIn(File(R + "ui/GoldHomeScreen.kt").readText()))
    check("screen -> view model -> coordinator", File(R + "ui/GoldHomeScreen.kt").readText().contains("viewModel.decideLearnedFilter(id, approve)") &&
        File(R + "ui/GoldViewModel.kt").readText().contains("learning.decideFilter(id, approve)") && File(R + "data/LearningCoordinator.kt").readText().contains("LearningEngine.ownerDecision("))
    check("engine never writes PROMOTED by itself any more", !File(R + "data/LearningEngine.kt").readText().contains("LedgerEvent(now, \"cand\", h.id, \"PROMOTED\""))
    println("V37-A.2 filter approval: $pass passed, $fail failed")
}
