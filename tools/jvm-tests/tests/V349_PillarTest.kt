import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
const val H = 3_600_000L

fun rec(i: Int, at: Long, win: Long, final: Signal, raw: Signal, votes: Map<String, Signal>, flt: String = "") = LedgerRecord(id = "r$i", createdAt = at, expiresAt = at + win,
    interval = "1h", horizonMin = (win / 60_000L).toInt(), barId = "b$i", price = 4000.0, rawSignal = raw, finalSignal = final, confidence = 70, atr = 1.0,
    slDistance = 3.0, regime = "RANGE", session = "LONDON", newsActive = false, modelVersion = "", appliedFilter = flt,
    sources = votes.mapKeys { "grp:" + it.key } + mapOf("quant" to raw), dataSource = GoldApiService.OANDA_SOURCE)
fun res(id: String, move: Double, o: LedgerOutcome = LedgerOutcome.CORRECT) = LedgerResult(id, 1L, o, "x", 4000.0, 4000.0 + move, move, 1.0, 0.0, 0.0, 0L, 0.0, emptyList(), emptyMap(), emptyList(), "", emptyList())
fun opp(s: Signal) = if (s == Signal.BUY) Signal.SELL else Signal.BUY

fun main() {
    // 1. gate logic (weights: trend 3, smc 3, macro 2.5, sr 2, ...; total 15.5)
    val all = PillarCheck.TITLES.keys.associateWith { Signal.BUY }
    check("7/7 BUY survives without macro", PillarCheck.survivesWithout(all, Signal.BUY, "macro"))
    val v = mapOf("trend" to Signal.BUY, "smc" to Signal.BUY, "macro" to Signal.BUY, "sr" to Signal.BUY, "momentum" to Signal.WAIT, "volatility" to Signal.WAIT, "candlestick" to Signal.SELL)
    // with macro: 10.5/15.5 = 67.7% (<68) and 4 votes -> engine would not even call; check a call that needs macro:
    val w = v + ("volatility" to Signal.BUY)   // trend3+smc3+macro2.5+sr2+vol(w) ; count 5
    check("5-of-7 call loses its 5th vote without macro -> needs macro", !PillarCheck.survivesWithout(w, Signal.BUY, "macro"))
    check("...but survives without the candlestick pillar (it voted against)", PillarCheck.survivesWithout(w, Signal.BUY, "candlestick"))

    // 2. planted ledger: macro anti-signal (35% right), trend good (65%), others coin; 1h calls with 1h windows (independent)
    val rnd = Random(11); val recs = ArrayList<LedgerRecord>(); val out = HashMap<String, LedgerResult>()
    repeat(2000) { i ->
        val up = rnd.nextBoolean(); val truth = if (up) Signal.BUY else Signal.SELL
        val votes = PillarCheck.TITLES.keys.associateWith { k -> when (k) {
            "macro" -> if (rnd.nextDouble() < 0.35) truth else opp(truth)
            "trend" -> if (rnd.nextDouble() < 0.65) truth else opp(truth)
            else -> if (rnd.nextBoolean()) Signal.BUY else Signal.SELL } }
        val buy = votes.values.count { it == Signal.BUY }
        val side = if (buy >= 5) Signal.BUY else if (buy <= 2) Signal.SELL else Signal.WAIT
        val r = rec(i, i * H, H, side, side, votes); recs += r
        out[r.id] = res(r.id, if (up) 3.0 else -3.0, if (side == Signal.WAIT) LedgerOutcome.WAIT_MISSED else LedgerOutcome.CORRECT)
    }
    val st = LedgerState(recs, out, emptyList())
    val ps = PillarCheck.pillars(st).associateBy { it.key }
    check("macro anti-signal: WORSE THAN CHANCE", ps["macro"]!!.ownVote.verdict == "WORSE THAN CHANCE", "${ps["macro"]!!.ownVote.line()}")
    check("trend real signal: PROVEN EDGE", ps["trend"]!!.ownVote.verdict == "HISTORICAL EDGE", "${ps["trend"]!!.ownVote.line()}")
    check("coin pillars not flagged", listOf("smc", "momentum", "sr", "volatility", "candlestick").none { ps[it]!!.ownVote.verdict == "HISTORICAL EDGE" || ps[it]!!.ownVote.verdict == "WORSE THAN CHANCE" })
    check("own vote counts every voting record (2000 independent)", ps["macro"]!!.ownVote.n == 2000)
    check("calls that needed macro exist and are counted", ps["macro"]!!.needed.n in 1..2000)
    val sum = PillarCheck.summary(st)
    println(sum.joinToString("\n"))
    check("summary: one line per pillar + filter-less footer", sum.size == 8 && sum.first().startsWith("Trend:") && sum.any { it.startsWith("Macro & News (old rule, always on):") && it.contains("HURTS") })

    // 3. overlap: 15m calls with 1h windows -> counted once per hour
    val o2 = ArrayList<LedgerRecord>(); val r2 = HashMap<String, LedgerResult>()
    repeat(400) { i -> val r = rec(i, i * H / 4, H, Signal.BUY, Signal.BUY, mapOf("macro" to Signal.BUY)); o2 += r; r2[r.id] = res(r.id, 3.0) }
    check("overlapping windows counted once (400 -> 100)", PillarCheck.pillars(LedgerState(o2, r2, emptyList())).first { it.key == "macro" }.ownVote.n == 100)

    // 4. range filter: blocked calls whose raw side was right 75% -> "blocking calls that worked"
    val o3 = ArrayList<LedgerRecord>(); val r3 = HashMap<String, LedgerResult>(); val rr = Random(3)
    repeat(300) { i -> val r = rec(i, i * H, H, Signal.WAIT, Signal.BUY, mapOf("trend" to Signal.BUY), "C-RANGE"); o3 += r; r3[r.id] = res(r.id, if (rr.nextDouble() < 0.75) 3.0 else -3.0, LedgerOutcome.WAIT_MISSED) }
    val rf = PillarCheck.rangeFilter(LedgerState(o3, r3, emptyList()))
    check("range filter: 300 blocked counted", rf.n == 300)
    check("range filter blocking good calls is shown", PillarCheck.summary(LedgerState(o3, r3, emptyList())).any { it.startsWith("Range filter") && it.contains("blocking calls that worked") })
    // flat and unreliable results excluded
    val o4 = listOf(rec(1, 0, H, Signal.BUY, Signal.BUY, mapOf("macro" to Signal.BUY)), rec(2, H, H, Signal.BUY, Signal.BUY, mapOf("macro" to Signal.BUY)))
    val r4 = mapOf("r1" to res("r1", 0.5), "r2" to res("r2", 3.0, LedgerOutcome.DATA_FAILURE))
    check("flat window and data failure not counted", PillarCheck.pillars(LedgerState(o4, r4, emptyList())).first { it.key == "macro" }.ownVote.n == 0)
    check("empty ledger -> no lines", PillarCheck.summary(LedgerState(emptyList(), emptyMap(), emptyList())).isEmpty())
    // read-only
    val before = st.records.toList()
    PillarCheck.summary(st)
    check("ledger not changed", st.records == before)
    val src = java.io.File("app/src/main/java/com/example/livegoldai")
    check("shown in SYSTEM", java.io.File(src, "ui/v8/V8HealthScreen.kt").readText().contains("p.pillars.forEach") &&
        java.io.File(src, "data/LedgerPulseBuilder.kt").readText().contains("PillarCheck.summary(state)"))
    check("engine weights untouched (15.5 total, macro 2.5)", TechnicalEngine.PILLAR_WEIGHTS.values.sum() == 15.5 && TechnicalEngine.PILLAR_WEIGHTS["macro"] == 2.5)
    println("V34.9 pillar check: $pass passed, $fail failed")
    if (fail > 0) kotlin.system.exitProcess(1)
}
