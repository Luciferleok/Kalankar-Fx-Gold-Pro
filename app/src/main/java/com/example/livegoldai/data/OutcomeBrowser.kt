package com.example.livegoldai.data

import com.example.livegoldai.model.Signal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * V35.7: the actual ledger records behind the SAHI / GALAT numbers (owner, 4 Nov 2026: "sahi galat same hi dikha raha he").
 * Before, the green tile did nothing and the red one opened the general analyzer on OVERVIEW (all timeframes), so both
 * led to the same screen. Each tile now opens THIS list with an explicit destination (kind + timeframe), built from the
 * same population as the tile: records of exactly this timeframe, outcome from the ledger result, nothing invented.
 *   CORRECT = CORRECT                     (same as LearningEngine "All Time" window: the SAHI tile)
 *   WRONG   = INCORRECT + INVALIDATED     (the GALAT tile)
 *   PENDING = no result yet (BUY/SELL calls), NO_EDGE = SIDEWAYS (moved less than the noise threshold)
 */
object OutcomeBrowser {
    enum class Kind { CORRECT, WRONG, PENDING, NO_EDGE }

    data class Row(
        val id: String,
        val createdAtMs: Long,
        val expiresAtMs: Long,
        val side: Signal,
        val agreement: Int,
        val entry: Double,
        val provider: String,
        val engine: String,
        val regime: String,
        val outcome: String,               // ledger outcome name, or PENDING
        val moveUsd: Double,               // close-to-close move in the called direction ($), NaN when pending
        val thresholdUsd: Double,
        val mfeUsd: Double, val maeUsd: Double,
        val stopHitAtMs: Long,
        val verifiedBy: String,
        val checkedAtMs: Long,
        val timeline: List<Pair<Long, String>>,
        // V35.7.1: why it is right / wrong (a CORRECT call can close against the call when the target came first)
        val reason: String = "",
        val tags: List<String> = emptyList(),
        val counterfactual: String = ""
    )

    data class Page(val kind: Kind, val interval: String, val rows: List<Row>, val total: Int, val counts: Map<Kind, Int>, val population: String)

    const val LIMIT = 200

    private fun kindOf(o: LedgerOutcome?): Kind? = when (o) {
        null, LedgerOutcome.PENDING -> Kind.PENDING
        LedgerOutcome.CORRECT -> Kind.CORRECT
        LedgerOutcome.INCORRECT, LedgerOutcome.INVALIDATED -> Kind.WRONG
        LedgerOutcome.SIDEWAYS -> Kind.NO_EDGE
        else -> null                       // WAIT outcomes, data failure, market closed, uncertain: in none of these lists
    }

    fun build(state: LedgerState, interval: String, kind: Kind, limit: Int = LIMIT): Page {
        // exactly the tile's population: this timeframe (same comparison as LearningEngine.snapshot), BUY/SELL calls
        val mine = state.records.filter { it.interval == interval && it.finalSignal != Signal.WAIT }
        val byKind = mine.groupBy { kindOf(state.resultOf(it.id)?.outcome) }
        val counts = Kind.values().associateWith { byKind[it]?.size ?: 0 }
        val list = (byKind[kind] ?: emptyList()).sortedByDescending { it.createdAt }
        val rows = list.take(limit).map { r ->
            val res = state.resultOf(r.id)
            val dir = if (r.finalSignal == Signal.SELL) -1.0 else 1.0
            Row(r.id, r.createdAt, r.expiresAt, r.finalSignal, r.confidence, r.price, EdgeFinder.providerOf(r.dataSource), EdgeFinder.engineOf(r.engineVersion),
                r.regime, res?.outcome?.name ?: "PENDING", res?.let { it.move * dir } ?: Double.NaN, res?.threshold ?: Double.NaN,
                res?.mfe ?: Double.NaN, res?.mae ?: Double.NaN, res?.slHitAt ?: 0L, res?.verifySource ?: "", res?.checkedAt ?: 0L, res?.timeline ?: emptyList(),
                reasonOf(res), res?.tags?.filter { it != "UNKNOWN" } ?: emptyList(), res?.counterfactual ?: "")
        }
        val pop = "${interval.uppercase(Locale.US)} BUY/SELL calls in this phone's ledger · " + when (kind) {
            Kind.CORRECT -> "outcome CORRECT (target hit, or closed the called way beyond the noise threshold)"
            Kind.WRONG -> "outcome INCORRECT or INVALIDATED (stop hit, or closed the wrong way)"
            Kind.PENDING -> "not checked yet"
            Kind.NO_EDGE -> "outcome SIDEWAYS (moved less than the noise threshold: not counted right or wrong)"
        }
        return Page(kind, interval, rows, list.size, counts, pop)
    }

    /** how the ledger decided it (same order as PredictionLedger.evaluate: target, stop, close direction) */
    fun reasonOf(res: LedgerResult?): String = when {
        res == null -> "Not checked yet"
        res.tpHitAt > 0L -> "Target reached first at ${hm(res.tpHitAt)}"
        res.slHitAt > 0L -> "Stop hit at ${hm(res.slHitAt)}"
        res.outcome == LedgerOutcome.CORRECT -> "Closed the called way beyond the noise"
        res.outcome == LedgerOutcome.INCORRECT -> "Closed the wrong way beyond the noise"
        res.outcome == LedgerOutcome.SIDEWAYS -> "Moved less than the noise"
        else -> res.outcome.name.replace('_', ' ').lowercase()
    }

    /** V35.7.1: "IST" for India (Android names it "GMT+05:30"), else the zone's short name */
    fun zone(tz: TimeZone, ms: Long): String = if (tz.id == "Asia/Kolkata" || tz.id == "Asia/Calcutta") "IST"
        else tz.getDisplayName(tz.inDaylightTime(Date(ms)), TimeZone.SHORT, Locale.US)

    private fun hm(ms: Long, tz: TimeZone = TimeZone.getDefault()) = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = tz }.format(Date(ms)) + " " + zone(tz, ms)

    /** "04 Nov 15:51 IST" in the phone's time zone */
    fun ts(ms: Long, tz: TimeZone = TimeZone.getDefault()): String = if (ms <= 0L) "--" else
        SimpleDateFormat("dd MMM HH:mm", Locale.US).apply { timeZone = tz }.format(Date(ms)) + " " + zone(tz, ms)
}
