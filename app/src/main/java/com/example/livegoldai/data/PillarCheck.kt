package com.example.livegoldai.data

import com.example.livegoldai.model.Signal

/**
 * V34.9 Pillar check: does each of the 7 rule pillars help or hurt? Read-only statistics on the ledger.
 *
 * Every ledger record keeps each pillar's own vote ("grp:<key>" in [LedgerRecord.sources]) and the real move over the
 * call's window. For each pillar this reports:
 *   - its OWN vote: how often the price really moved the way the pillar voted (close direction beyond the noise
 *     threshold; flat windows excluded), counted on INDEPENDENT windows only (see [EdgeFinder.independent]);
 *   - the calls that NEEDED it: BUY/SELL calls the pillar agreed with that would have been WAIT without its vote
 *     (same 68% weighted gate and 5-of-7 count rule as TechnicalEngine), and how often those calls were right.
 * And the range filter: the BUY/SELL calls it turned into WAIT, and how often they would have been right.
 *
 * Verdicts use the Edge Finder's 99.9% Wilson interval and N >= [EdgeFinder.MIN_N]. Nothing here changes a pillar,
 * a weight, the gate, a forecast or the ledger: it only shows evidence for a decision the owner makes.
 */
object PillarCheck {

    val TITLES = linkedMapOf("trend" to "Trend", "smc" to "Smart Money", "momentum" to "Momentum", "sr" to "Support/Resistance",
        "volatility" to "Volatility", "candlestick" to "Candlestick", "macro" to "Macro & News")
    private const val GATE = 68.0

    private data class Row(val r: LedgerRecord, val res: LedgerResult)

    private fun checked(state: LedgerState): List<Row> = state.records.mapNotNull { r ->
        val res = state.resultOf(r.id) ?: return@mapNotNull null
        if (!PredictionLedger.reliable(res.outcome) || res.threshold.isNaN() || res.move.isNaN()) null else Row(r, res)
    }

    private fun independentRows(rows: List<Row>): List<Row> {
        val byId = rows.associateBy { it.r.id }
        // V35.3: pillars pool all timeframes, so one window per moment across timeframes (as in the Edge Finder)
        return EdgeFinder.independentAcross(rows.map { it.r }).mapNotNull { byId[it.id] }
    }

    /**
     * V35.4: the gate weights each engine version actually used. Identical for every engine since V14 (git: b5c7596 to
     * now), verified against the live engine on 4000 random decisions. A version not listed here is NOT re-scored with
     * today's weights: its records are left out of "calls that needed it" (said in the line) instead of being silently
     * rebuilt with rules it never used.
     */
    val GATE_WEIGHTS_BY_ENGINE: Map<String, Map<String, Double>> = mapOf("" to TechnicalEngine.PILLAR_WEIGHTS,
        "E22.0" to TechnicalEngine.PILLAR_WEIGHTS, "E22.1" to TechnicalEngine.PILLAR_WEIGHTS)
    fun gateWeights(engineVersion: String): Map<String, Double>? = GATE_WEIGHTS_BY_ENGINE[engineVersion]

    /** Would the engine still have called [side] if [key]'s vote were WAIT? (weighted 68% gate or 5-of-7 count) */
    fun survivesWithout(votes: Map<String, Signal>, side: Signal, key: String, weights: Map<String, Double> = TechnicalEngine.PILLAR_WEIGHTS): Boolean {
        // V35.0: only pillars that voted in this record take part (Macro is absent outside its news window)
        val total = weights.filterKeys { it in votes }.values.sum()
        val pts = weights.entries.sumOf { (k, w) -> if (k != key && votes[k] == side) w else 0.0 }
        val count = weights.keys.count { k -> k != key && votes[k] == side }
        return 100.0 * pts / total >= GATE || count >= 5
    }

    private fun votes(r: LedgerRecord): Map<String, Signal> =
        TITLES.keys.mapNotNull { k -> r.sources["grp:$k"]?.let { k to it } }.toMap()

    private fun verdict(g: EdgeFinder.Group): String = when (g.verdict) {
        EdgeFinder.HIST -> "HELPS (past data)"
        EdgeFinder.WORSE -> "HURTS (wrong more often than chance)"
        EdgeFinder.FEW -> "too few to judge"
        else -> "not proven either way"
    }

    data class PillarLine(val key: String, val ownVote: EdgeFinder.Group, val needed: EdgeFinder.Group, val title: String = TITLES[key] ?: key, val neededByEngine: String = "")

    /** V35.0: records written after the Macro news-window rule carry "ctx:macro" (Macro's raw vote, active or not). */
    private fun newRule(r: LedgerRecord) = r.sources.containsKey("ctx:macro")

    fun pillars(state: LedgerState): List<PillarLine> {
        val rows = checked(state)
        return TITLES.keys.map { key ->
            // V35.0: Macro is judged separately before (always on) and after (news window only) the rule change
            val pool = if (key == "macro") rows.filter { !newRule(it.r) } else rows
            // own vote, on independent windows of the records where this pillar voted BUY or SELL
            val voted = independentRows(pool.filter { val v = it.r.sources["grp:$key"]; v == Signal.BUY || v == Signal.SELL })
            var ok = 0; var n = 0
            voted.forEach { x -> when (PredictionLedger.directionScore(x.r.sources["grp:$key"]!!, x.res.move, x.res.threshold)) { 1 -> { ok++; n++ }; 0 -> n++ } }
            // calls that needed this pillar's vote
            val needRows = independentRows(pool.filter { x ->
                val side = x.r.finalSignal
                val w = gateWeights(x.r.engineVersion)
                w != null && (side == Signal.BUY || side == Signal.SELL) && x.r.sources["grp:$key"] == side && !survivesWithout(votes(x.r), side, key, w)
            })
            var nOk = 0; var nN = 0
            val byEng = LinkedHashMap<String, IntArray>()
            needRows.forEach { x -> val c = byEng.getOrPut(EdgeFinder.engineOf(x.r.engineVersion)) { IntArray(2) }
                when (PredictionLedger.directionScore(x.r.finalSignal, x.res.move, x.res.threshold)) { 1 -> { nOk++; nN++; c[0]++; c[1]++ }; 0 -> { nN++; c[1]++ } } }
            val engLine = byEng.filter { it.value[1] > 0 }.entries.joinToString(" · ") { (k, v) -> "$k ${Math.round(100.0 * v[0] / v[1])}% of ${v[1]}" }
            val title = if (key == "macro") "Macro & News (old rule, always on)" else TITLES[key]!!
            PillarLine(key, EdgeFinder.Group("Pillar", title, ok, n), EdgeFinder.Group("Needed", title, nOk, nN), title, engLine)
        } + macroNewRule(rows)
    }

    /** V35.0: Macro under the news-window rule: its votes inside the window (used), and outside it (not used, research). */
    private fun macroNewRule(rows: List<Row>): List<PillarLine> {
        fun score(sel: List<Row>, sig: (LedgerRecord) -> Signal?): EdgeFinder.Group {
            var ok = 0; var n = 0
            independentRows(sel.filter { val v = sig(it.r); v == Signal.BUY || v == Signal.SELL }).forEach { x ->
                when (PredictionLedger.directionScore(sig(x.r)!!, x.res.move, x.res.threshold)) { 1 -> { ok++; n++ }; 0 -> n++ }
            }
            return EdgeFinder.Group("Pillar", "", ok, n)
        }
        val fresh = rows.filter { newRule(it.r) }
        if (fresh.isEmpty()) return emptyList()
        val inWin = fresh.filter { it.r.sources.containsKey("grp:macro") && !it.r.sources.containsKey("ctx:macroProxy") }
        val inWinProxy = fresh.filter { it.r.sources.containsKey("grp:macro") && it.r.sources.containsKey("ctx:macroProxy") }
        val outWin = fresh.filter { !it.r.sources.containsKey("grp:macro") }
        val empty = EdgeFinder.Group("Needed", "", 0, 0)
        return listOf(
            PillarLine("macro", score(inWin) { it.sources["grp:macro"] }.copy(value = "Macro & News (news window, used)"), empty, "Macro & News (news window, used)"),
            PillarLine("macro", score(inWinProxy) { it.sources["grp:macro"] }.copy(value = "Macro & News (news window, dollar via EUR/USD proxy)"), empty, "Macro & News (news window, dollar via EUR/USD proxy)"),
            PillarLine("macro", score(outWin) { it.sources["ctx:macro"] }.copy(value = "Macro & News (outside window, NOT used)"), empty, "Macro & News (outside window, NOT used)")
        )
    }

    /** BUY/SELL calls the range filter turned into WAIT: how often the raw call would have been right (independent windows). */
    fun rangeFilter(state: LedgerState): EdgeFinder.Group {
        val blocked = independentRows(checked(state).filter { it.r.appliedFilter == "C-RANGE" && (it.r.rawSignal == Signal.BUY || it.r.rawSignal == Signal.SELL) })
        var ok = 0; var n = 0
        blocked.forEach { x -> when (PredictionLedger.directionScore(x.r.rawSignal, x.res.move, x.res.threshold)) { 1 -> { ok++; n++ }; 0 -> n++ } }
        return EdgeFinder.Group("Range filter", "blocked calls", ok, n)
    }

    private fun pct(g: EdgeFinder.Group) = "${Math.round(g.pct)}% right of ${g.n} independent (99.9% range ${Math.round(g.low)}–${Math.round(g.high)}%)"

    /** SYSTEM lines: one per pillar, then the range filter. Empty when nothing has been checked yet. */
    fun summary(state: LedgerState): List<String> {
        val ps = pillars(state)
        if (ps.all { it.ownVote.n == 0 }) return emptyList()
        val lines = ps.map { p ->
            // V35.4: two different questions, named apart: the pillar's OWN vote accuracy, and its INCREMENTAL contribution
            // (final BUY/SELL calls that would have been WAIT without it), with the engine versions it comes from
            "${p.title}: own vote ${if (p.ownVote.n == 0) "no checked votes yet" else pct(p.ownVote) + " · " + verdict(p.ownVote)}" +
                if (p.needed.n > 0) " · incremental (calls that needed it): ${Math.round(p.needed.pct)}% right of ${p.needed.n}" +
                    (if (p.needed.n >= EdgeFinder.MIN_N) " (${verdict(p.needed)})" else "") + (p.neededByEngine.takeIf { it.isNotEmpty() }?.let { " [$it]" } ?: "") else ""
        }
        val rf = rangeFilter(state)
        val rfLine = if (rf.n == 0) emptyList() else listOf("Range filter: the calls it blocked would have been ${pct(rf)} · " + when (rf.verdict) {
            EdgeFinder.HIST -> "the filter is blocking calls that worked"
            EdgeFinder.WORSE -> "the filter is helping"
            EdgeFinder.FEW -> "too few to judge"
            else -> "no proof either way"
        })
        return lines + rfLine + "Close direction beyond the noise threshold; flat windows excluded. Evidence only: this check changes no pillar, weight or filter. Since E22.1 Macro votes only 1 h before to 30 min after high-impact USD news."
    }
}
