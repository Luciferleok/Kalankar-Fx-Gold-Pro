import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }

const val M15 = 15 * 60_000L
const val H1 = 60 * 60_000L

fun rec(i: Int, at: Long, iv: String, win: Long, src: String = GoldApiService.OANDA_SOURCE) = LedgerRecord(id = "r$i", createdAt = at, expiresAt = at + win,
    interval = iv, horizonMin = (win / 60_000L).toInt(), barId = "b$i", price = 4000.0, rawSignal = Signal.BUY, finalSignal = Signal.BUY, confidence = 70, atr = 1.0,
    slDistance = 3.0, regime = "RANGE", session = "LONDON", newsActive = false, modelVersion = "", appliedFilter = "", sources = emptyMap(), dataSource = src)
fun res(id: String, ok: Boolean) = LedgerResult(id, 1L, if (ok) LedgerOutcome.CORRECT else LedgerOutcome.INCORRECT, "x", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0L, 0.0, emptyList(), emptyMap(), emptyList(), "", emptyList())

/** 15m calls every 15 min judged over 1 h: 4 neighbouring calls share one move (same right/wrong), no real edge */
fun overlapped(seed: Long, blocks: Int): LedgerState {
    val r = Random(seed); val rs = ArrayList<LedgerRecord>(); val os = HashMap<String, LedgerResult>(); var i = 0
    repeat(blocks) { b ->
        val ok = r.nextBoolean()
        repeat(4) { k -> val x = rec(i++, b * H1 + k * M15, "15m", H1); rs += x; os[x.id] = res(x.id, ok) }
    }
    return LedgerState(rs, os, emptyList())
}

fun main() {
    // 1. the thinning rule itself
    val a = listOf(rec(1, 0, "15m", H1), rec(2, M15, "15m", H1), rec(3, 2 * M15, "15m", H1), rec(4, H1, "15m", H1), rec(5, H1 + M15, "15m", H1),
        rec(6, 0, "1h", 4 * H1), rec(7, 4 * H1, "1h", 4 * H1))
    val kept = EdgeFinder.independent(a).map { it.id }.toSet()
    check("15m: one call per 1h window kept", kept.containsAll(listOf("r1", "r4")) && "r2" !in kept && "r3" !in kept && "r5" !in kept, "$kept")
    check("other timeframe thinned on its own", "r6" in kept && "r7" in kept)
    check("same moment, two providers -> counted once",
        EdgeFinder.independent(listOf(rec(1, 0, "15m", H1), rec(2, 0, "15m", H1, "Twelve Data XAU/USD"))).size == 1)
    check("order of input does not matter", EdgeFinder.independent(a.reversed()).map { it.id }.toSet() == kept)

    // 2. overlap no longer creates false edges
    var newHits = 0; var oldStyle = 0
    repeat(200) { s ->
        val st = overlapped(5000L + s, 120)          // 480 overlapping calls = 120 real pieces of evidence
        val g = EdgeFinder.groups(st).single { it.dimension == "Timeframe" }
        if (g.verdict == "HISTORICAL EDGE" || g.verdict == "WORSE THAN CHANCE") newHits++
        val right = st.records.count { st.resultOf(it.id)!!.outcome == LedgerOutcome.CORRECT }
        val (lo, hi) = EdgeFinder.wilson(right, st.records.size)
        if (lo > 0.5 || hi < 0.5) oldStyle++
        if (s == 0) check("N counts independent calls only (480 -> 120)", g.n == 120, "n=${g.n}")
    }
    println("overlapping no-edge ledgers flagged: counting every call $oldStyle/200, independent calls $newHits/200")
    check("independent counting: false verdicts rare (<= 1%)", newHits <= 2, "$newHits")
    check("every-call counting really was inflated (more false verdicts)", oldStyle > newHits)

    // 3. a real edge still shows: 70% right on independent 15m windows
    val r = Random(9); val rs = ArrayList<LedgerRecord>(); val os = HashMap<String, LedgerResult>(); var i = 0
    repeat(400) { b -> val ok = r.nextDouble() < 0.70; repeat(4) { k -> val x = rec(i++, b * H1 + k * M15, "15m", H1); rs += x; os[x.id] = res(x.id, ok) } }
    val g = EdgeFinder.groups(LedgerState(rs, os, emptyList())).single { it.dimension == "Timeframe" }
    check("real edge (70%, 400 windows) still PROVEN EDGE", g.verdict == "HISTORICAL EDGE" && g.n == 400, "${g.verdict} ${g.n}")
    check("line says independent calls", g.line().contains("independent calls"))

    println("V34.8.1 independent calls: $pass passed, $fail failed")
    if (fail > 0) kotlin.system.exitProcess(1)
}
