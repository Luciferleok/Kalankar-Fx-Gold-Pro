package com.example.livegoldai.data

import com.example.livegoldai.model.LabelStat
import com.example.livegoldai.model.Signal
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * RESEARCH LAB (V19)
 *
 * Read-only research statistics computed from the real ledger. Nothing here changes a signal.
 * Every number carries its sample size; below the minimum it says so instead of showing a value.
 *
 *  - accuracy with a 95% range (Wilson)            - coverage (BUY/SELL vs WAIT)
 *  - negative controls (simple momentum, simple mean-reversion, previous direction)
 *  - strong-signal precision                       - bot diversity (are the bots really independent?)
 *  - forecast stability (do recent calls keep flipping?)
 *  - expected move: empirical median and 10-90% range of what past calls actually did
 */
object ResearchLab {

    const val MIN_N = 30              // calls needed before a statistic is shown as a judgement
    const val STABILITY_WINDOW = 6
    const val STABILITY_MIN = 4

    data class Report(
        val lines: List<LabelStat>,
        val stability: String,        // HIGH / MEDIUM / LOW / "--"
        val stabilityDetail: String,
        val expectedMove: String,     // short text for the home screen, "--" when history is insufficient
        val expectedMoveDetail: String,
        val coverage: String          // "62%" share of checked calls that were BUY/SELL, "--" when nothing checked
    )

    private fun pct(c: Int, n: Int) = if (n == 0) "--" else String.format(Locale.US, "%.0f%% (N=%d)", 100.0 * c / n, n)

    /** 95% Wilson range in percent, null below 10 samples. */
    fun wilson(c: Int, n: Int): Pair<Double, Double>? {
        if (n < 10) return null
        val z = 1.96
        val p = c.toDouble() / n
        val den = 1 + z * z / n
        val centre = (p + z * z / (2 * n)) / den
        val half = z * sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / den
        return (centre - half) * 100 to (centre + half) * 100
    }

    /** Forecast stability over the most recent calls of one timeframe. A BUY<->SELL reversal counts double. */
    fun stability(signals: List<Signal>): Pair<String, String> {
        val s = signals.takeLast(STABILITY_WINDOW)
        if (s.size < STABILITY_MIN) return "--" to "needs $STABILITY_MIN recorded calls on this timeframe (has ${s.size})"
        var flips = 0; var reversals = 0
        for (i in 1 until s.size) {
            if (s[i] != s[i - 1]) { flips++; if (PredictionLedger.opposite(s[i], s[i - 1])) reversals++ }
        }
        val level = when {
            reversals >= 1 || flips >= 3 -> "LOW"
            flips <= 1 -> "HIGH"
            else -> "MEDIUM"
        }
        return level to "last ${s.size}: " + s.joinToString(" ") { it.name } + " • $flips change(s), $reversals reversal(s)"
    }

    fun build(state: LedgerState, interval: String): Report {
        val lines = ArrayList<LabelStat>()

        // ---- checked records
        class Row(val r: LedgerRecord, val res: LedgerResult)
        val checked = state.records.mapNotNull { r ->
            val res = state.resultOf(r.id) ?: return@mapNotNull null
            if (!PredictionLedger.reliable(res.outcome)) null else Row(r, res)
        }
        val directional = checked.filter { it.r.finalSignal != Signal.WAIT }
        fun score(x: Row) = PredictionLedger.directionScore(x.r.finalSignal, x.res.move, x.res.threshold)
        val decided = directional.filter { score(it) >= 0 }
        val ok = decided.count { score(it) == 1 }

        // ---- accuracy with its uncertainty
        if (decided.isNotEmpty()) {
            val w = wilson(ok, decided.size)
            lines.add(LabelStat("Accuracy with 95% range", pct(ok, decided.size) +
                if (w == null) " • too few for a range" else String.format(Locale.US, " • true value likely %.0f–%.0f%%", w.first, w.second)))
        }

        // ---- coverage
        val coverage = if (checked.isEmpty()) "--" else "${100 * directional.size / checked.size}%"
        if (checked.isNotEmpty()) lines.add(LabelStat("Coverage", "BUY/SELL on ${directional.size} of ${checked.size} checked calls ($coverage) • WAIT on ${checked.size - directional.size}"))

        // ---- strong-signal precision
        val strong = decided.filter { it.r.confidence >= 80 }
        if (strong.isNotEmpty()) lines.add(LabelStat("Strong calls (agreement ≥ 80%)", pct(strong.count { score(it) == 1 }, strong.size) +
            if (strong.size < MIN_N) " • too few to judge" else if (decided.size > strong.size) " vs all " + pct(ok, decided.size) else ""))

        // ---- negative controls: what a trivial rule would have scored on the same windows
        run {
            var momOk = 0; var momN = 0; var revOk = 0; var revN = 0; var prevOk = 0; var prevN = 0
            for ((_, list) in checked.groupBy { it.r.interval }) {
                val sorted = list.sortedBy { it.r.createdAt }
                for (i in 1 until sorted.size) {
                    val prev = sorted[i - 1]; val cur = sorted[i]
                    val prevDir = when {
                        prev.res.move >= prev.res.threshold -> Signal.BUY
                        prev.res.move <= -prev.res.threshold -> Signal.SELL
                        else -> null
                    }
                    if (prevDir != null) {
                        val s = PredictionLedger.directionScore(prevDir, cur.res.move, cur.res.threshold)
                        if (s >= 0) { momN++; revN++; if (s == 1) momOk++ else revOk++ }
                    }
                    // "previous direction": repeat the engine's previous call
                    if (prev.r.rawSignal != Signal.WAIT) {
                        val s = PredictionLedger.directionScore(prev.r.rawSignal, cur.res.move, cur.res.threshold)
                        if (s >= 0) { prevN++; if (s == 1) prevOk++ }
                    }
                }
            }
            if (momN > 0) {
                lines.add(LabelStat("Control: simple momentum", pct(momOk, momN)))
                lines.add(LabelStat("Control: simple mean-reversion", pct(revOk, revN)))
            }
            if (prevN > 0) lines.add(LabelStat("Control: repeat previous call", pct(prevOk, prevN)))
            if (decided.size >= MIN_N && momN >= MIN_N) {
                val engine = 100.0 * ok / decided.size
                val best = maxOf(100.0 * momOk / momN, 100.0 * revOk / revN, 50.0)
                lines.add(LabelStat("Engine vs simple controls", if (engine > best + 3) "engine is ahead by ${(engine - best).toInt()} points"
                    else if (engine < best - 3) "a trivial rule did BETTER by ${(best - engine).toInt()} points: no proven edge" else "no clear difference: no proven edge"))
            }
        }

        // ---- bot diversity: do the bots really give independent views?
        run {
            val agree = HashMap<String, IntArray>()
            var used = 0
            for (r in state.records) {
                val bots = r.sources.filterKeys { it.startsWith("bot:") }.toSortedMap().entries.toList()
                if (bots.size < 3) continue
                used++
                for (i in bots.indices) for (j in i + 1 until bots.size) {
                    val c = agree.getOrPut(bots[i].key + "|" + bots[j].key) { IntArray(2) }
                    c[0]++; if (bots[i].value == bots[j].value) c[1]++
                }
            }
            if (used > 0) {
                val avg = agree.values.map { 100.0 * it[1] / it[0] }.average()
                val level = when { used < MIN_N -> "untested"; avg >= 90 -> "LOW"; avg >= 70 -> "MODERATE"; else -> "HIGH" }
                lines.add(LabelStat("Bot diversity", "$level • bots give the same answer ${avg.toInt()}% of the time (N=$used)" +
                    if (level == "LOW") " • do not count them as separate confirmations" else ""))
            }
        }

        // ---- forecast stability (current timeframe)
        val mine = state.records.filter { it.interval.equals(interval, ignoreCase = true) }.sortedBy { it.createdAt }
        val (stab, stabDetail) = stability(mine.map { it.finalSignal })
        lines.add(LabelStat("Forecast stability (${interval.uppercase(Locale.US)})", if (stab == "--") stabDetail else "$stab • $stabDetail"))

        // ---- expected move: what past calls on this timeframe actually did, measured in the call's direction
        val moves = directional.filter { it.r.interval.equals(interval, ignoreCase = true) }
            .map { if (it.r.finalSignal == Signal.SELL) -it.res.move else it.res.move }.sorted()
        val expShort: String; val expDetail: String
        if (moves.size < MIN_N) {
            expShort = "--"
            expDetail = "INSUFFICIENT HISTORY (N=${moves.size}/$MIN_N)"
        } else {
            fun q(p: Double) = moves[((moves.size - 1) * p).toInt()]
            expShort = String.format(Locale.US, "%+.1f", q(0.5))
            expDetail = String.format(Locale.US, "median %+.1f • 80%% of past calls ended between %+.1f and %+.1f (N=%d, in the call's direction)", q(0.5), q(0.1), q(0.9), moves.size)
        }
        lines.add(LabelStat("Expected move (${interval.uppercase(Locale.US)})", expDetail))

        return Report(lines, stab, stabDetail, expShort, expDetail, coverage)
    }
}
