package com.example.livegoldai.data.ai

import com.example.livegoldai.data.PredictionLedger
import com.example.livegoldai.model.AiDissent
import com.example.livegoldai.model.AiProviderView
import com.example.livegoldai.model.AiVote
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Council math. Kept separate from the market probability on purpose:
 * "4 of 5 AI say SELL" is agreement between models, not a 80% chance of a fall.
 */
object AiConsensus {

    data class Result(
        val buy: Int, val sell: Int, val wait: Int,
        val consensus: String,
        val agreementLevel: String,
        val agreementPct: Int,
        val dispersionPct: Int,
        val dispersionLevel: String,
        val avgBull: Int, val avgBear: Int, val avgSide: Int,
        val dissent: List<AiDissent>
    )

    fun compute(views: List<AiProviderView>): Result {
        val el = views.filter { it.eligible && it.vote != null }
        val votes = el.map { it.vote!! }
        val n = votes.size
        val buy = votes.count { it.direction == Signal.BUY }
        val sell = votes.count { it.direction == Signal.SELL }
        val wait = votes.count { it.direction == Signal.WAIT }
        if (n == 0) return Result(0, 0, 0, "NONE", "NONE", -1, -1, "N/A", -1, -1, -1, emptyList())

        val counts = listOf(Signal.BUY to buy, Signal.SELL to sell, Signal.WAIT to wait).sortedByDescending { it.second }
        val top = counts[0]
        val tie = counts[1].second == top.second
        val consensus = when {
            n == 1 -> top.first.name
            !tie && top.second * 2 > n -> top.first.name
            else -> "SPLIT"
        }
        val agreementPct = (100.0 * top.second / n).roundToInt()
        val level = when {
            n == 1 -> "SINGLE"
            consensus == "SPLIT" -> "WEAK"
            agreementPct >= 80 -> "STRONG"
            agreementPct >= 60 -> "MODERATE"
            else -> "WEAK"
        }
        val disp = dispersion(votes)
        val dispLevel = when { disp < 0 -> "N/A"; disp < 15 -> "LOW"; disp < 35 -> "MODERATE"; else -> "HIGH" }

        val plurality: Signal? = if (tie) null else top.first
        val dissent = el.filter { plurality == null || it.vote!!.direction != plurality }.map { v ->
            val vote = v.vote!!
            val p = when (vote.direction) { Signal.BUY -> vote.bullish; Signal.SELL -> vote.bearish; Signal.WAIT -> vote.sideways }
            AiDissent(v.name, vote.direction, p, vote.evidence.firstOrNull() ?: vote.contradictions.firstOrNull() ?: vote.regime)
        }
        return Result(
            buy, sell, wait, consensus, level, agreementPct, disp, dispLevel,
            votes.map { it.bullish }.average().roundToInt(),
            votes.map { it.bearish }.average().roundToInt(),
            votes.map { it.sideways }.average().roundToInt(),
            if (n >= 2) dissent else emptyList()
        )
    }

    /** Mean pairwise total-variation distance between probability vectors, 0-100. -1 with fewer than 2 votes. */
    fun dispersion(votes: List<AiVote>): Int {
        if (votes.size < 2) return -1
        var sum = 0.0; var pairs = 0
        for (i in votes.indices) for (j in i + 1 until votes.size) {
            val a = votes[i]; val b = votes[j]
            sum += 0.5 * (abs(a.bullish - b.bullish) + abs(a.bearish - b.bearish) + abs(a.sideways - b.sideways))
            pairs++
        }
        return (sum / pairs).roundToInt()
    }

    /** QUANT vs BOTS vs AI. Returns (level, note). No invented confidence penalty: conflict is shown as a fact. */
    fun conflict(quant: Signal, bots: Signal?, ai: String): Pair<String, String> {
        val aiSig = when (ai) { "BUY" -> Signal.BUY; "SELL" -> Signal.SELL; "WAIT" -> Signal.WAIT; else -> null }
        val all = listOfNotNull(quant, bots, aiSig)
        val opposite = all.any { a -> all.any { b -> PredictionLedger.opposite(a, b) } }
        if (aiSig == null) {
            val base = if (ai == "SPLIT") "AI council is split" else "No eligible AI vote"
            return "NO_AI" to if (bots != null && PredictionLedger.opposite(quant, bots)) "$base. Quant and bots point in opposite directions." else "$base. Comparing quant and bots only."
        }
        return when {
            opposite -> "HIGH" to "Quant, bots and AI point in opposite directions. Treat this forecast as low quality; standing aside is the cautious choice."
            all.distinct().size > 1 -> "MINOR" to "Not all three agree (one side says WAIT). Weaker setup."
            else -> "ALIGNED" to "Quant engine, rule bots and AI council point the same way."
        }
    }

    /** "What could make the current forecast wrong?" built only from real numbers in the analysis. */
    fun contrarian(a: GoldAnalysisResult): List<String> {
        val out = ArrayList<String>()
        val sig = a.overallSignal
        val pv = a.pivotLevels
        val px = a.currentPrice
        val rsi = a.groups.flatMap { it.indicators }.firstOrNull { it.name.startsWith("RSI") }
            ?.let { MarketSnapshotBuilder.extractFacts(it.name, it.valueDisplay).values.firstOrNull() }
        val against = a.groups.filter { PredictionLedger.opposite(it.verdict, sig) }.map { it.title }
        val mtfAgainst = a.mtfMatrix?.timeframes?.filter { it.label.startsWith("PAXG") && PredictionLedger.opposite(it.signal, sig) }?.map { it.timeframe } ?: emptyList()
        val ts = a.tradeSetup
        when (sig) {
            Signal.BUY -> {
                val lvl = listOf(pv.s1, pv.pivot).filter { it < px }.maxOrNull()
                if (lvl != null) out.add(String.format(Locale.US, "A candle close below %.2f (nearest pivot support)", lvl))
                if (ts.signal == Signal.BUY && ts.stopLoss > 0) out.add(String.format(Locale.US, "Price reaching the stop level %.2f", ts.stopLoss))
                if (rsi != null && rsi >= 70) out.add(String.format(Locale.US, "RSI %.1f is overbought: pullback risk", rsi))
            }
            Signal.SELL -> {
                val lvl = listOf(pv.r1, pv.pivot).filter { it > px }.minOrNull()
                if (lvl != null) out.add(String.format(Locale.US, "A candle close above %.2f (nearest pivot resistance)", lvl))
                if (ts.signal == Signal.SELL && ts.stopLoss > 0) out.add(String.format(Locale.US, "Price reaching the stop level %.2f", ts.stopLoss))
                if (rsi != null && rsi <= 30) out.add(String.format(Locale.US, "RSI %.1f is oversold: bounce risk", rsi))
            }
            Signal.WAIT -> out.add(String.format(Locale.US, "No direction now. A close above %.2f (R1) or below %.2f (S1) would start a move", pv.r1, pv.s1))
        }
        if (sig != Signal.WAIT && against.isNotEmpty()) out.add("Pillars voting against it now: " + against.joinToString(", "))
        if (sig != Signal.WAIT && mtfAgainst.isNotEmpty()) out.add("Timeframes pointing the other way (EMA rule): " + mtfAgainst.joinToString(", "))
        val ev = a.macroRadar?.upcomingEvents?.firstOrNull { it.impact.equals("High", true) }
        if (ev != null) out.add("Scheduled high-impact event: ${ev.title} (${ev.getIndiaTimeFormatted()})")
        return out.take(5)
    }
}
