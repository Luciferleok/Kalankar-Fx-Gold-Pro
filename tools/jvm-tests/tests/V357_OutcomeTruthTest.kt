import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// V35.7: SAHI / GALAT lists = the tile's population, fair verification queue, six-pair readiness. SYNTHETIC FIXTURES ONLY.
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
const val M = 60_000L
fun rec(id: String, at: Long, iv: String, side: Signal, src: String = GoldApiService.OANDA_SOURCE, h: Int = 25) =
    LedgerRecord(id = id, createdAt = at, expiresAt = at + h * M, interval = iv, horizonMin = h, barId = id, price = 4000.0, rawSignal = side, finalSignal = side,
        confidence = 75, atr = 1.0, slDistance = 1.0, regime = "R", session = "S", newsActive = false, modelVersion = "", appliedFilter = "", sources = emptyMap(),
        dataSource = src, engineVersion = "E22.1")
fun res(id: String, o: LedgerOutcome, move: Double = 1.0) = LedgerResult(id, 1L, o, "OANDA XAU_USD M1", 4000.0, 4000.0 + move, move, 0.5, 2.0, 1.0, 0L, 0.0,
    emptyList(), emptyMap(), emptyList(), "", listOf(1L to "start"))

fun main() {
    // ---- a mixed ledger: two timeframes, two providers, WAIT calls, every outcome
    val r = Random(11); val recs = ArrayList<LedgerRecord>(); val out = HashMap<String, LedgerResult>()
    val outcomes = LedgerOutcome.values().toList()
    for (i in 0 until 3000) {
        val iv = if (i % 3 == 0) "15m" else "5m"
        val side = when (i % 7) { 0 -> Signal.WAIT; in 1..3 -> Signal.BUY; else -> Signal.SELL }
        val x = rec("r$i", i * 10 * M, iv, side, if (i % 2 == 0) GoldApiService.OANDA_SOURCE else "Twelve Data XAU/USD")
        recs += x
        if (i % 11 != 0) { val o = if (side == Signal.WAIT) listOf(LedgerOutcome.WAIT_FLAT, LedgerOutcome.WAIT_MISSED)[i % 2] else outcomes[r.nextInt(outcomes.size)]
            if (o != LedgerOutcome.PENDING) out[x.id] = res(x.id, o, if (i % 2 == 0) 2.5 else -2.5) }
    }
    val st = LedgerState(recs, out, emptyList())
    val now = 3000 * 10 * M
    val snap = LearningEngine.snapshot(st, "5m", now, emptyList())
    val allTime = snap.windows.first { it.label == "All Time" }
    val c5 = OutcomeBrowser.build(st, "5m", OutcomeBrowser.Kind.CORRECT)
    val w5 = OutcomeBrowser.build(st, "5m", OutcomeBrowser.Kind.WRONG)
    check("SAHI list = the 5M tile (All Time window of 5M)", c5.total == allTime.correct, "${c5.total} vs ${allTime.correct}")
    check("GALAT list = the 5M tile", w5.total == allTime.decided - allTime.correct, "${w5.total} vs ${allTime.decided - allTime.correct}")
    // the tile itself (RealityEngine.buildAudit through apply) shows the same two numbers
    val candles = (0 until 150).map { k -> val o = 4000.0 + k * 0.4; CandleBar(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(now - (150 - k) * 5 * M)), o, o + 0.6, o - 0.5, o + 0.35) }
    val audit = RealityEngine.apply(TechnicalEngine.analyze(candles, "5m"), snap, st, null, "5m", "").timeframeAudit!!
    check("tile numbers = list totals", audit.winCount == c5.total && audit.lossCount == w5.total && audit.timeframe == "5M", "${audit.winCount}/${audit.lossCount}")
    check("SAHI and GALAT are different lists", c5.rows.map { it.id }.intersect(w5.rows.map { it.id }.toSet()).isEmpty() && c5.kind != w5.kind)
    check("SAHI rows are CORRECT only", c5.rows.all { it.outcome == "CORRECT" })
    check("GALAT rows are INCORRECT / INVALIDATED only", w5.rows.all { it.outcome == "INCORRECT" || it.outcome == "INVALIDATED" })
    check("5M list has no 15M record", (c5.rows + w5.rows).all { id -> recs.first { it.id == id.id }.interval == "5m" })
    val c15 = OutcomeBrowser.build(st, "15m", OutcomeBrowser.Kind.CORRECT)
    check("15M counted apart", c15.total == LearningEngine.snapshot(st, "15m", now, emptyList()).windows.first { it.label == "All Time" }.correct && c15.total != c5.total)
    check("both providers kept (tile mixes them, list shows each row's provider)", c5.rows.map { it.provider }.toSet().size == 2)
    val p5 = OutcomeBrowser.build(st, "5m", OutcomeBrowser.Kind.PENDING); val n5 = OutcomeBrowser.build(st, "5m", OutcomeBrowser.Kind.NO_EDGE)
    check("pending = BUY/SELL without a result", p5.total == recs.count { it.interval == "5m" && it.finalSignal != Signal.WAIT && (out[it.id] == null || out[it.id]!!.outcome == LedgerOutcome.PENDING) })
    check("no edge = SIDEWAYS", n5.total == recs.count { it.interval == "5m" && it.finalSignal != Signal.WAIT && out[it.id]?.outcome == LedgerOutcome.SIDEWAYS })
    check("WAIT calls, data failures, closures, uncertain: in no list", (c5.rows + w5.rows + p5.rows + n5.rows).none { it.side == Signal.WAIT || it.outcome in setOf("DATA_FAILURE", "MARKET_CLOSED", "VERIFICATION_UNCERTAIN") })
    check("counts on every page are the same population", c5.counts == w5.counts && c5.counts[OutcomeBrowser.Kind.CORRECT] == c5.total && c5.counts[OutcomeBrowser.Kind.WRONG] == w5.total)
    check("newest first, at most 200", c5.rows.size == minOf(200, c5.total) && c5.rows.zipWithNext().all { (a, b) -> a.createdAtMs >= b.createdAtMs })
    val sell = OutcomeBrowser.build(LedgerState(listOf(rec("s", 0, "5m", Signal.SELL)), mapOf("s" to res("s", LedgerOutcome.CORRECT, -3.0)), emptyList()), "5m", OutcomeBrowser.Kind.CORRECT).rows.single()
    check("move shown in the called direction (SELL, price -3 -> +3)", sell.moveUsd == 3.0 && sell.timeline.isNotEmpty())
    OutcomeBrowser.build(LedgerState(emptyList(), emptyMap(), emptyList()), "5m", OutcomeBrowser.Kind.WRONG).let { check("empty ledger: empty list, zero counts", it.rows.isEmpty() && it.counts.values.all { c -> c == 0 }) }
    check("population text names timeframe and outcome rule", c5.population.startsWith("5M BUY/SELL calls") && w5.population.contains("INCORRECT or INVALIDATED"))

    // ---- fair verification queue: 6 stuck records no longer block newer ones
    val stuckIds = (0 until 6).map { "stuck$it" }.toSet()
    val lines = (stuckIds.mapIndexed { i, id -> rec(id, 1_000 * M + i * M, "5m", Signal.BUY) } + (0 until 6).map { rec("new$it", 2_000 * M + it * M, "5m", Signal.BUY) })
        .map { PredictionLedger.encodeRecord(it) }
    val written = ArrayList<String>()
    val store = object : LedgerStore { override fun readAll() = lines; override fun append(lines: List<String>) { written += lines } }
    val lc = LearningCoordinator(store) { _, _ -> null }
    lc.fetchPathFor = { x, _ -> if (x.id in stuckIds) PricePath("WAIT", emptyList(), waiting = true)
        else PricePath("OANDA XAU_USD M1", (0..25).map { k -> PathBar(x.createdAt + k * M, 4000.0, 4000.5 + k * 0.2, 3999.8, 4000.0 + k * 0.2) }) }
    val t = 2_100 * M
    runBlocking { lc.verifyOnly(t); lc.verifyOnly(t + M) }
    val checkedNew = lc.currentState().results.keys.count { it.startsWith("new") }
    check("two refreshes: the 6 newer records are checked (before: 0, stuck ones kept every slot)", checkedNew == 6, "$checkedNew")
    check("stuck records still waiting (not invented)", lc.currentState().results.keys.none { it in stuckIds })

    // ---- six-pair readiness needs common bars
    val tnow = 5_000 * 5 * M
    fun series(offset: Long = 0L, n: Int = 30) = (1..n).map { (tnow - it * 5 * M + offset) to 1.1 }
    val pairs = OandaMacro.DXY_PAIRS.keys.associateWith { series() }
    check("synced six pairs: READY with baseline", MacroReadiness.index(pairs, OandaStatus.CONNECTED, tnow).let { it.state == MacroReadiness.READY && it.detail.contains("baseline 60 min available") })
    val shifted = pairs + ("USD_SEK" to series(offset = 60_000L))
    check("one pair on other timestamps: no common bar -> UNAVAILABLE", MacroReadiness.index(shifted, OandaStatus.CONNECTED, tnow).let { it.state == MacroReadiness.UNAVAILABLE && it.detail.contains("same time") })
    check("missing pair -> UNAVAILABLE", MacroReadiness.index(pairs - "USD_CHF", OandaStatus.CONNECTED, tnow).state == MacroReadiness.UNAVAILABLE)
    check("conflicting duplicate -> UNAVAILABLE", MacroReadiness.index(pairs + ("GBP_USD" to series() + (series()[0].first to 1.3)), OandaStatus.CONNECTED, tnow).detail.startsWith("conflicting"))
    check("only 30 min of bars: baseline missing -> STALE", MacroReadiness.index(OandaMacro.DXY_PAIRS.keys.associateWith { series(n = 6) }, OandaStatus.CONNECTED, tnow).let { it.state == MacroReadiness.STALE && it.detail.contains("baseline missing") })
    check("old bars -> STALE", MacroReadiness.index(OandaMacro.DXY_PAIRS.keys.associateWith { series(offset = -40 * M) }, OandaStatus.CONNECTED, tnow).state == MacroReadiness.STALE)
    // all six READY one by one, index not computable: dollar falls back to the EUR/USD proxy
    val sources = OandaMacro.DXY_PAIRS.keys.map { MacroReadiness.Source("OANDA", it, MacroReadiness.READY, "") } +
        MacroReadiness.Source("OANDA", MacroReadiness.DXY_6, MacroReadiness.UNAVAILABLE, "") + MacroReadiness.Source("OANDA", OandaMacro.NOTE, MacroReadiness.READY, "")
    check("six pairs READY but no common bar: not 'computed index'", MacroReadiness.evaluate(tnow, emptyList(), sources).dollarPath == "OANDA EUR/USD proxy (inverted)")

    // ---- routing in the UI source (Compose UI tests need the Android build: not run here)
    val ui = "app/src/main/java/com/example/livegoldai/ui"
    val kh = File("$ui/components/KyaHogaPredictionDialog.kt").readText(); val du = File("$ui/components/DualPredictionAndIndicatorsSection.kt").readText()
    val vm = File("$ui/GoldViewModel.kt").readText(); val hs = File("$ui/GoldHomeScreen.kt").readText()
    check("dialog: SAHI -> CORRECT, GALAT -> WRONG", kh.contains("{ onOpenOutcomes(\"CORRECT\") }") && kh.contains("{ onOpenOutcomes(\"WRONG\") }") && !kh.contains("onDismiss()\n                                                onOpenPredictionErrorAnalyzer()"))
    check("scorecard: SAHI -> CORRECT, GALAT -> WRONG", du.contains("{ onOpenOutcomes(\"CORRECT\") }") && du.contains("{ onOpenOutcomes(\"WRONG\") }") && !du.contains(".clickable { onOpenPredictionErrorAnalyzer() }"))
    check("VM: explicit destination from the analysis on screen", vm.contains("val interval = _uiState.value.data?.interval ?: _uiState.value.selectedInterval") && vm.contains("OutcomeBrowser.build(learning.currentState(), interval, k)"))
    check("home: list dialog wired, both callers pass onOpenOutcomes", hs.contains("OutcomeBrowserDialog(") && hs.split("onOpenOutcomes = { kind -> viewModel.openOutcomes(kind) }").size == 3)
    println("V35.7 outcome truth: $pass passed, $fail failed")
}
