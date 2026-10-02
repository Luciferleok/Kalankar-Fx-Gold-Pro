package com.example.livegoldai.data

import com.example.livegoldai.model.LabelStat
import com.example.livegoldai.model.Signal
import java.util.Locale

/**
 * PREDICTION AUDIT (V11.3)
 *
 * Read-only root-cause report computed from the real ledger. It never changes a stored result,
 * never flips BUY/SELL and never changes a weight. It only measures:
 *
 *  - DIRECTION result : price at the end of the window vs the start (stop-loss plays no part)
 *  - TRADE result     : target first / stop first (what a trade would have done)
 *  - PATH result      : which side price touched first at the same distance (1 x ATR up vs down)
 *  - baselines        : the same calls flipped, "always up", "always down", coin flip
 *  - BUY / SELL, timeframe, session and regime splits
 *  - how many separate market moves the calls really came from
 *
 * and decides the model health state used to lock learning promotions.
 */
object PredictionAudit {

    const val MIN_N = 30                 // decided direction calls needed before the engine is judged
    const val CRITICAL_BELOW = 35.0      // direction accuracy below this (N >= MIN_N) = CRITICAL
    const val POLARITY_FLIPPED_AT = 65.0 // flipped calls at least this good (and enough separate moves) = polarity suspect
    const val MIN_MOVES = 10             // separate market moves needed before polarity is suspected
    const val NO_EDGE_BELOW = 40.0       // a timeframe below this with N >= 20 is marked NO EDGE
    const val TF_MIN_N = 20

    data class Report(
        val health: String,              // COLLECTING / VALIDATING / HEALTHY / WEAK / CRITICAL / POLARITY_SUSPECT
        val headline: String,
        val promotionLocked: Boolean,
        val lockReason: String,
        val decided: Int,
        val directionPct: Double,        // -1 when nothing decided
        val flippedPct: Double,
        val separateMoves: Int,
        val noEdgeIntervals: List<String>,
        val lines: List<LabelStat>
    )

    private class Row(val r: LedgerRecord, val res: LedgerResult, val dir: Int)

    private fun pct(c: Int, n: Int): Double = if (n == 0) -1.0 else 100.0 * c / n
    private fun p(c: Int, n: Int): String = if (n == 0) "--" else String.format(Locale.US, "%.0f%% (%d/%d)", 100.0 * c / n, c, n)

    fun build(state: LedgerState, integrityStatus: String = ""): Report {
        val rows = ArrayList<Row>()
        var up = 0; var down = 0; var flatMarket = 0      // what the market did in every checked window
        for (r in state.records) {
            val res = state.resultOf(r.id) ?: continue
            if (!PredictionLedger.reliable(res.outcome)) continue
            when {
                res.move >= res.threshold -> up++
                res.move <= -res.threshold -> down++
                else -> flatMarket++
            }
            if (r.finalSignal == Signal.WAIT) continue
            rows.add(Row(r, res, PredictionLedger.directionScore(r.finalSignal, res.move, res.threshold)))
        }
        val dec = rows.filter { it.dir >= 0 }
        val n = dec.size
        val ok = dec.count { it.dir == 1 }
        val dirPct = pct(ok, n)
        val flipPct = pct(n - ok, n)

        // separate market moves: calls whose windows overlap are one move, not independent evidence
        var moves = 0; var movesOk = 0
        run {
            var end = Long.MIN_VALUE; var cOk = 0; var cN = 0
            for (x in dec.sortedBy { it.r.createdAt }) {
                if (x.r.createdAt >= end) {
                    if (cN > 0) { moves++; if (cOk * 2 > cN) movesOk++ }
                    cOk = 0; cN = 0; end = x.r.expiresAt
                } else if (x.r.expiresAt > end) end = x.r.expiresAt
                cN++; if (x.dir == 1) cOk++
            }
            if (cN > 0) { moves++; if (cOk * 2 > cN) movesOk++ }
        }

        // trade + path results
        val tp = rows.count { it.res.tpHitAt != 0L }
        val sl = rows.count { it.res.tpHitAt == 0L && it.res.slHitAt != 0L }
        val slButRight = rows.count { it.res.tpHitAt == 0L && it.res.slHitAt != 0L && it.dir == 1 }
        val pathFav = rows.count { it.res.pathHit == 1 }
        val pathAdv = rows.count { it.res.pathHit == -1 }

        val buy = dec.filter { it.r.finalSignal == Signal.BUY }
        val sell = dec.filter { it.r.finalSignal == Signal.SELL }

        val noEdge = dec.groupBy { it.r.interval }
            .filter { it.value.size >= TF_MIN_N && pct(it.value.count { x -> x.dir == 1 }, it.value.size) < NO_EDGE_BELOW }
            .keys.toList()

        val health = when {
            n == 0 -> "COLLECTING"
            n < MIN_N -> "VALIDATING"
            flipPct >= POLARITY_FLIPPED_AT && moves >= MIN_MOVES -> "POLARITY_SUSPECT"
            dirPct < CRITICAL_BELOW -> "CRITICAL"
            dirPct < 52.0 -> "WEAK"
            else -> "HEALTHY"
        }
        val integrityBad = integrityStatus == "CRITICAL"
        val locked = health == "CRITICAL" || health == "POLARITY_SUSPECT" || integrityBad
        val lockReason = when {
            integrityBad -> "ledger integrity is CRITICAL"
            health == "POLARITY_SUSPECT" -> "the opposite of the calls was right too often: direction logic must be reviewed by a human first"
            health == "CRITICAL" -> "direction accuracy is below ${CRITICAL_BELOW.toInt()}% on $n calls"
            else -> ""
        }
        val headline = when (health) {
            "COLLECTING" -> "No checked BUY/SELL call yet"
            "VALIDATING" -> "PREDICTION ENGINE UNDER VALIDATION: only $n decided calls (needs $MIN_N)"
            "POLARITY_SUSPECT" -> "MODEL HEALTH CRITICAL: the opposite call would have been right ${flipPct.toInt()}% of the time. Signals are NOT flipped automatically."
            "CRITICAL" -> "MODEL HEALTH CRITICAL: direction right only ${dirPct.toInt()}% on $n calls. Do not trade on these signals."
            "WEAK" -> "NO PROVEN EDGE: direction right ${dirPct.toInt()}% on $n calls (a coin flip is 50%)"
            else -> "Direction right ${dirPct.toInt()}% on $n calls"
        }

        val lines = ArrayList<LabelStat>()
        if (rows.isNotEmpty()) {
            lines.add(LabelStat("Direction (end of window)", p(ok, n) + " • no clear move ${rows.size - n}"))
            lines.add(LabelStat("Same calls flipped", p(n - ok, n)))
            val mkt = up + down
            lines.add(LabelStat("Always UP / always DOWN", p(up, mkt) + " / " + p(down, mkt)))
            lines.add(LabelStat("Coin flip", "50%"))
            lines.add(LabelStat("BUY / SELL direction", p(buy.count { it.dir == 1 }, buy.size) + " / " + p(sell.count { it.dir == 1 }, sell.size)))
            if (tp + sl > 0) lines.add(LabelStat("Trade (target vs stop)", "target first $tp • stop first $sl" + if (slButRight > 0) " ($slButRight stopped but direction was right)" else ""))
            if (pathFav + pathAdv > 0) lines.add(LabelStat("First touch at 1×ATR", "with the call $pathFav • against $pathAdv"))
            lines.add(LabelStat("Separate market moves", "$moves (right in $movesOk)" + if (n > 0 && moves * 3 < n) " • many calls repeat the same move" else ""))
            lines.add(LabelStat("Market in checked windows", "up $up • down $down • flat $flatMarket"))
            // matrix: timeframe x side
            for ((iv, list) in dec.groupBy { it.r.interval }.toSortedMap(compareBy { RealityEngine.intervalMinutes(it) })) {
                val b = list.filter { it.r.finalSignal == Signal.BUY }; val s = list.filter { it.r.finalSignal == Signal.SELL }
                lines.add(LabelStat(
                    iv.uppercase(Locale.US) + if (iv in noEdge) " • NO EDGE" else "",
                    "BUY ✓${b.count { it.dir == 1 }} ✗${b.count { it.dir == 0 }} • SELL ✓${s.count { it.dir == 1 }} ✗${s.count { it.dir == 0 }}"
                ))
            }
            fun split(title: String, key: (LedgerRecord) -> String) {
                val g = dec.groupBy { key(it.r).ifEmpty { "?" } }
                if (g.size >= 2 || g.keys.firstOrNull() != "?") lines.add(LabelStat(title, g.entries.joinToString(" • ") { "${it.key} ✓${it.value.count { x -> x.dir == 1 }} ✗${it.value.count { x -> x.dir == 0 }}" }))
            }
            split("By session") { it.session }
            split("By regime") { it.regime }
            split("News window") { if (it.newsActive) "news" else "quiet" }
            lines.add(LabelStat("Learning promotions", if (locked) "LOCKED: $lockReason" else "allowed (rules still need 50+ samples and a shadow test)"))
        }
        return Report(health, headline, locked, lockReason, n, dirPct, flipPct, moves, noEdge, lines)
    }
}
