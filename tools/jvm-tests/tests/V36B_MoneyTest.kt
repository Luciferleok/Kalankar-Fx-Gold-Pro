import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File

// V36-B money check. SYNTHETIC FIXTURES.
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name :: $d") } }
const val M = 60_000L
fun close(a: Double, b: Double) = kotlin.math.abs(a - b) < 1e-6
fun rec(i: Int, side: Signal, at: Long = i * 30 * M, pub: Signal? = side, tp: Double = 3.0, sl: Double = 3.0, iv: String = "5m") = LedgerRecord(id = "r$i", createdAt = at, expiresAt = at + 25 * M,
    interval = iv, horizonMin = 25, barId = "b$i", price = 4000.0, rawSignal = side, finalSignal = side, confidence = 80, atr = 4.0, slDistance = sl, regime = "TREND_UP",
    session = "S", newsActive = false, modelVersion = "", appliedFilter = "", sources = emptyMap(), dataSource = "OANDA XAU_USD", engineVersion = PredictionLedger.ENGINE_VERSION,
    tpDistance = tp, publishedSignal = pub)
fun res(r: LedgerRecord, o: LedgerOutcome, move: Double, tpAt: Long = 0L, slAt: Long = 0L, ev: String = PredictionLedger.EVAL_VERSION) =
    LedgerResult(r.id, 1L, o, "OANDA XAU_USD M1", 4000.0, 4000.0 + move, move, 1.0, 0.0, 0.0, slAt, 0.0, emptyList(), emptyMap(), emptyList(), "", emptyList(), tpHitAt = tpAt, evalVersion = ev)
fun main() {
    val C = TradeEconomics.COST_USD
    // first touch rules
    check("target first = +target", TradeEconomics.gross(rec(1, Signal.BUY), res(rec(1, Signal.BUY), LedgerOutcome.CORRECT, -0.5, tpAt = 5L)) == 3.0)
    check("stop first = -stop", TradeEconomics.gross(rec(1, Signal.BUY), res(rec(1, Signal.BUY), LedgerOutcome.INVALIDATED, 9.0, slAt = 5L)) == -3.0)
    check("SELL close move is signed for the side", TradeEconomics.gross(rec(1, Signal.SELL), res(rec(1, Signal.SELL), LedgerOutcome.CORRECT, -2.0)) == 2.0)
    check("sideways still a trade (small move)", TradeEconomics.gross(rec(1, Signal.BUY), res(rec(1, Signal.BUY), LedgerOutcome.SIDEWAYS, 0.4)) == 0.4)
    check("uncertain / ambiguous never guessed", TradeEconomics.gross(rec(1, Signal.BUY), res(rec(1, Signal.BUY), LedgerOutcome.VERIFICATION_UNCERTAIN, 9.0)) == null)
    // book: + 3, -3, +2, -3, -3 (time order) -> gross -4, net -4 - 5C; drawdown from peak
    val rs = listOf(rec(0, Signal.BUY), rec(1, Signal.BUY), rec(2, Signal.SELL), rec(3, Signal.BUY), rec(4, Signal.BUY))
    val out = listOf(res(rs[0], LedgerOutcome.CORRECT, 0.0, tpAt = 1L), res(rs[1], LedgerOutcome.INVALIDATED, 0.0, slAt = 1L), res(rs[2], LedgerOutcome.CORRECT, -2.0),
        res(rs[3], LedgerOutcome.INVALIDATED, 0.0, slAt = 1L), res(rs[4], LedgerOutcome.INVALIDATED, 0.0, slAt = 1L))
    val st = LedgerState(rs, out.associateBy { it.id }, emptyList())
    val b = TradeEconomics.book(st, rs)
    check("trades and hit-rate", b.trades == 5 && b.correct == 2 && b.wrong == 3 && b.hitPct == 40, "$b")
    check("net after costs", close(b.grossUsd, -4.0) && close(b.netUsd, -4.0 - 5 * C), "$b")
    // cum: 3-C, -2C, 2-3C, -1-4C, -4-5C ; peak 3-C ; dd = (3-C) - (-4-5C) = 7 + 4C
    check("worst drop from the peak", close(b.maxDrawdownUsd, 7.0 + 4 * C), "${b.maxDrawdownUsd}")
    // overlap: 5 calls 5 minutes apart with a 25 min horizon -> one trade at a time
    val dense = (0 until 5).map { rec(10 + it, Signal.BUY, at = it * 5 * M) }
    val db = TradeEconomics.book(LedgerState(dense, dense.associate { it.id to res(it, LedgerOutcome.CORRECT, 0.0, tpAt = 1L) }, emptyList()), dense)
    check("overlapping calls are one trade at a time", db.overlapping == 5 && db.trades in 1..2, "$db")
    // summary splits
    val mixed = listOf(rec(20, Signal.BUY, pub = Signal.BUY), rec(21, Signal.BUY, pub = Signal.WAIT), rec(22, Signal.SELL, pub = null), rec(23, Signal.BUY, iv = "15m"))
    val mres = listOf(res(mixed[0], LedgerOutcome.CORRECT, 2.0), res(mixed[1], LedgerOutcome.INCORRECT, -2.0), res(mixed[2], LedgerOutcome.CORRECT, -2.0, ev = ""), res(mixed[3], LedgerOutcome.CORRECT, 2.0))
    val s = TradeEconomics.build(LedgerState(mixed, mres.associateBy { it.id }, emptyList()), "5m")
    check("all methods: 3 (5m only)", s.all.trades == 3, "${s.all}")
    check("current method: EV2 only", s.current.trades == 2, "${s.current}")
    check("shown on HOME vs held by the gate", s.shown.trades == 1 && s.held.trades == 1 && close(s.held.netUsd, -2.0 - C), "${s.shown} / ${s.held}")
    check("read-only: nothing written", st.results.size == 5 && st.records.size == 5)
    // wiring
    val R = "app/src/main/java/com/example/livegoldai/"
    check("Correct/Wrong list shows the money card", File(R + "ui/components/OutcomeBrowserDialog.kt").readText().contains("MoneyCard(mc, lang)") &&
        OutcomeBrowser.build(st, "5m", OutcomeBrowser.Kind.WRONG).money?.all?.trades == 5)
    check("cost assumption stated", File(R + "ui/components/MoneyCard.kt").readText().contains("assumed") && C > 0)
    println("V36-B money: $pass passed, $fail failed")
}
