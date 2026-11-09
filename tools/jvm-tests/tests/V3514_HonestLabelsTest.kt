import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.io.File

// V35.14 honest labels + 2 shadow challengers + HOME tile. SYNTHETIC FIXTURES.
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name :: $d") } }
val R = "app/src/main/java/com/example/livegoldai/"
fun main() {
    val E = AppLanguage.ENGLISH
    // ---- wording of stored text (owner screenshots 6 Nov)
    check("timeline: confidence -> rule agreement", ForensicText.timeline("Prediction SELL at 4100.24 (OANDA XAU_USD M1), confidence 84%") ==
        "Prediction SELL at 4100.24 (OANDA XAU_USD M1), rule agreement 84% (not a win chance)")
    check("timeline: other lines untouched", ForensicText.timeline("Moved 1.34 in predicted direction") == "Moved 1.34 in predicted direction")
    ForensicText.counterfactual("If the app had obeyed the Candlestick pillar (it pointed the other way) and shown WAIT, this loss would have been avoided.", E).let {
        check("hindsight, not a promise", it.startsWith("Hindsight: the Candlestick pillar pointed the other way.") && it.contains("not proven") && !it.contains("would have been avoided"), it) }
    check("timeframe sentence", ForensicText.counterfactual("If predictions against the higher timeframe were blocked, this loss would have been avoided.", E).startsWith("Hindsight: this call went against the higher timeframe"))
    check("no-warning sentence", ForensicText.counterfactual("No recorded signal warned against this prediction. No simple rule would have avoided it.", E) == "No pillar pointed the other way at the time.")
    check("Hindi", ForensicText.counterfactual("If the app had obeyed the Candlestick pillar (it pointed the other way) and shown WAIT, this loss would have been avoided.", AppLanguage.HINDI).startsWith("पीछे देखकर"))
    check("tags: plain names, UNKNOWN dropped", ForensicText.tags(listOf("OVERCONFIDENCE", "FAKE_MOVE_REVERSAL", "UNKNOWN"), E) == "HIGH AGREEMENT, WRONG · MOVED RIGHT FIRST, THEN REVERSED")
    check("tags title says not proven causes", ForensicText.tagsTitle(E).contains("not proven causes"))
    // ---- new results are written the same way
    val pl = File(R + "data/PredictionLedger.kt").readText()
    check("new timeline text", pl.contains("rule agreement \${record.confidence}% (not a win chance)") && !pl.contains("), confidence \${record.confidence}%"))
    check("new counterfactual text", !pl.contains("this loss would have been avoided"))
    // ---- screens
    val ob = File(R + "ui/components/OutcomeBrowserDialog.kt").readText(); val an = File(R + "ui/components/PredictionErrorAnalyzerDialog.kt").readText()
    check("Correct/Wrong list re-words stored text", ob.contains("ForensicText.counterfactual(r.counterfactual, lang)") && ob.contains("ForensicText.timeline(what)") && ob.contains("ForensicText.tags(r.tags, lang)"))
    check("Learn failures: no 40% / 20% cause percentages", !an.contains("\"\${it.key.replace('_', ' ')} \${it.value}%\"") && an.contains("ForensicText.tags(f.attribution.keys, lang)"))
    check("Learn failures: header says agreement", an.contains("\"\${f.signal.name} · \" + t(lang, \"agreement\""))
    check("forensic timeline readable (>= 11sp)", ob.contains("ForensicText.timeline(what), color = TextSecondary, fontSize = 12.sp") && an.contains("ForensicText.timeline(ev.text), color = TextPrimary, fontSize = 11.sp"))
    // ---- two new shadow challengers
    check("WAIT stays WAIT", PrecisionLab.decideExtra(Signal.WAIT, true, true).values.all { it == Signal.WAIT })
    PrecisionLab.decideExtra(Signal.SELL, true, false).let { check("candle against -> CANDLE waits, REPEAT keeps", it[PrecisionLab.CANDLE] == Signal.WAIT && it[PrecisionLab.REPEAT] == Signal.SELL) }
    PrecisionLab.decideExtra(Signal.BUY, false, true).let { check("same zone -> REPEAT waits, CANDLE keeps", it[PrecisionLab.CANDLE] == Signal.BUY && it[PrecisionLab.REPEAT] == Signal.WAIT) }
    val lc = File(R + "data/LearningCoordinator.kt").readText()
    check("decided at record time from known data only", lc.contains("sources.putAll(PrecisionLab.decideExtra(side, candleOpposes, repeatZone))") &&
        lc.contains("r.barId != raw.lastUpdated") && lc.contains("now - r.createdAt in 1..PrecisionLab.REPEAT_MS"))
    // evaluated like the others, only on records that carry the key
    fun rec(i: Int, side: Signal, cand: Signal) = LedgerRecord(id = "x$i", createdAt = i * 1_800_000L, expiresAt = i * 1_800_000L + 1_500_000L, interval = "5m", horizonMin = 25,
        barId = "b$i", price = 4000.0, rawSignal = side, finalSignal = side, confidence = 75, atr = 4.0, slDistance = 0.0, regime = "TREND_UP", session = "S",
        newsActive = false, modelVersion = "", appliedFilter = "", sources = mapOf(PrecisionLab.CANDLE to cand), dataSource = "OANDA XAU_USD", engineVersion = PredictionLedger.ENGINE_VERSION)
    val recs = (0 until 10).map { rec(it, Signal.BUY, if (it < 3) Signal.WAIT else Signal.BUY) } + (10 until 14).map { rec(it, Signal.BUY, Signal.BUY).copy(sources = emptyMap()) }
    val res = recs.associate { it.id to LedgerResult(it.id, 1L, if (it.id.removePrefix("x").toInt() % 2 == 0) LedgerOutcome.CORRECT else LedgerOutcome.INCORRECT, "x", 4000.0, 4001.0, 1.0, 0.5, 1.0, 0.0, 0L, 0.0, emptyList(), emptyMap(), emptyList(), "", emptyList()) }
    val st = PrecisionLab.evaluate(LedgerState(recs, res, emptyList()))
    check("5 challengers listed", st.map { it.key } == listOf(PrecisionLab.LATE, PrecisionLab.QUIET, PrecisionLab.SHORT, PrecisionLab.CANDLE, PrecisionLab.REPEAT), "${st.map { it.key }}")
    st.first { it.key == PrecisionLab.CANDLE }.let { check("CANDLE pool = only records that carry it; kept/skipped split", it.prodN == 10 && it.keptN == 7 && it.skippedN == 3 && it.verdict == "COLLECTING", "$it") }
    check("never promoted: needs >= 50 kept + 20 skipped and the owner", PrecisionLab.MIN_KEPT == 50 && PrecisionLab.MIN_SKIPPED == 20)
    // ---- HOME tile
    check("HOME tile value wraps (TREND DOWN not cut)", File(R + "ui/v8/V8CockpitScreen.kt").readText().contains("Text(text = v, color = vc, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2"))
    println("V35.14 honest labels: $pass passed, $fail failed")
}
