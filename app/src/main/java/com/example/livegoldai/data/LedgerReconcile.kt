package com.example.livegoldai.data

import com.example.livegoldai.model.Signal
import kotlin.math.abs

/**
 * V35.13 READ-ONLY price-anchor reconciliation (owner approval, 6 Nov 2026). Nothing is rewritten: every stored result
 * stays exactly as recorded and the tiles keep counting it. This only answers "what if the check had started from the
 * PUBLISHED price instead of the first verification bar's open?" from what each result stored:
 *   UNCHANGED : the two prices agree within [PredictionLedger.anchorTol], or the close rule gives the same outcome
 *   CHANGED   : decided by the close rule, and from the published price the same stored end price gives another outcome
 *   UNCERTAIN : decided by a stop / target touch (the order of touches from another anchor cannot be rebuilt from the
 *               stored numbers), or no comparable start price (proxy instrument, old record)
 * The stored end price may still come from the bar that closed after expiry (pre-V35.13 checks), so this is a
 * hypothetical, not a corrected record.
 */
object LedgerReconcile {
    enum class Cls { UNCHANGED, CHANGED, UNCERTAIN }
    data class Row(val id: String, val createdAt: Long, val side: Signal, val original: String, val hypothetical: String,
                   val published: Double, val firstOpen: Double, val diff: Double, val cls: Cls, val note: String)
    data class Summary(val interval: String, val checked: Int, val unchanged: Int, val changed: Int, val uncertain: Int,
                       val before: Map<String, Int>, val after: Map<String, Int>, val rows: List<Row>)

    private fun bucket(o: LedgerOutcome) = when (o) {
        LedgerOutcome.CORRECT -> "CORRECT"; LedgerOutcome.INCORRECT, LedgerOutcome.INVALIDATED -> "WRONG"; else -> "NO EDGE"
    }

    fun build(state: LedgerState, interval: String): Summary {
        val rows = state.records.filter { it.interval == interval && it.finalSignal != Signal.WAIT }.mapNotNull { r ->
            val res = state.resultOf(r.id) ?: return@mapNotNull null
            if (res.outcome != LedgerOutcome.CORRECT && res.outcome != LedgerOutcome.INCORRECT &&
                res.outcome != LedgerOutcome.INVALIDATED && res.outcome != LedgerOutcome.SIDEWAYS) return@mapNotNull null
            val orig = bucket(res.outcome)
            val same = PredictionLedger.instrumentOf(r.dataSource).let { it.isNotEmpty() && it == PredictionLedger.instrumentOf(res.verifySource) }
            if (!same || r.price <= 0.0 || res.startPx <= 0.0)
                return@mapNotNull Row(r.id, r.createdAt, r.finalSignal, orig, "--", r.price, res.startPx, 0.0, Cls.UNCERTAIN, "no comparable start price")
            val diff = res.startPx - r.price
            if (abs(diff) <= PredictionLedger.anchorTol(r))
                return@mapNotNull Row(r.id, r.createdAt, r.finalSignal, orig, orig, r.price, res.startPx, diff, Cls.UNCHANGED, "prices agree")
            if (res.tpHitAt != 0L || res.slHitAt != 0L)
                return@mapNotNull Row(r.id, r.createdAt, r.finalSignal, orig, "--", r.price, res.startPx, diff, Cls.UNCERTAIN,
                    if (res.tpHitAt != 0L) "target touch from another price" else "stop touch from another price")
            val hyp = when (PredictionLedger.directionScore(r.finalSignal, res.endPx - r.price, res.threshold)) { 1 -> "CORRECT"; 0 -> "WRONG"; else -> "NO EDGE" }
            Row(r.id, r.createdAt, r.finalSignal, orig, hyp, r.price, res.startPx, diff, if (hyp == orig) Cls.UNCHANGED else Cls.CHANGED,
                "close ${PredictionLedger.fmt(res.endPx)}")
        }.sortedByDescending { it.createdAt }
        fun count(f: (Row) -> String) = rows.groupingBy(f).eachCount()
        return Summary(interval, rows.size, rows.count { it.cls == Cls.UNCHANGED }, rows.count { it.cls == Cls.CHANGED }, rows.count { it.cls == Cls.UNCERTAIN },
            count { it.original }, count { if (it.cls == Cls.UNCERTAIN) "UNCERTAIN" else it.hypothetical }, rows)
    }
}
