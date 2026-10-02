package com.example.livegoldai.data.brain

import com.example.livegoldai.data.LedgerOutcome
import com.example.livegoldai.data.LedgerState
import com.example.livegoldai.data.LedgerStore
import com.example.livegoldai.data.MiniJson
import com.example.livegoldai.data.PredictionLedger
import com.example.livegoldai.data.ai.MarketSnapshotBuilder
import com.example.livegoldai.data.long
import com.example.livegoldai.data.obj
import com.example.livegoldai.data.str
import com.example.livegoldai.model.AnalogReport
import com.example.livegoldai.model.AnomalyReport
import com.example.livegoldai.model.CrossMarketReport
import com.example.livegoldai.model.FamiliarityReport
import com.example.livegoldai.model.FeatureDefUi
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * FEATURE STORE
 *
 * Every recorded prediction also stores the numeric market state it was made in ("feat" line in the
 * same append-only ledger file). Feature names carry a version: if a formula ever changes, the feature
 * gets a new name (…_v2) so old rows never silently change meaning.
 * This history is what analog search, familiarity, and later ML / ablation tests are built on.
 */
data class FeatureDef(val name: String, val formula: String, val inputs: String, val inDistance: Boolean = true)

data class FeatureRow(val recordId: String, val at: Long, val interval: String, val values: Map<String, Double>)

object FeatureCatalog {
    const val VERSION = "F1"

    val DEFS = listOf(
        FeatureDef("rsi14_v1", "RSI(14)", "close"),
        FeatureDef("adx_v1", "ADX(14)", "high, low, close"),
        FeatureDef("di_spread_v1", "(+DI) − (−DI)", "high, low, close"),
        FeatureDef("ema9_21_atr_v1", "(EMA9 − EMA21) / ATR14", "close, ATR"),
        FeatureDef("px_ema50_atr_v1", "(price − EMA50) / ATR14", "close, ATR"),
        FeatureDef("macd_hist_atr_v1", "(MACD − signal) / ATR14", "close, ATR"),
        FeatureDef("stoch_k_v1", "Stochastic %K", "high, low, close"),
        FeatureDef("cci20_v1", "CCI(20)", "high, low, close"),
        FeatureDef("mfi14_v1", "Money Flow Index(14)", "high, low, close, volume"),
        FeatureDef("roc10_v1", "Rate of change(10) %", "close"),
        FeatureDef("bb_pos_v1", "(price − lower band) / (upper − lower)", "close, Bollinger"),
        FeatureDef("px_pivot_atr_v1", "(price − pivot) / ATR14", "price, pivot, ATR"),
        FeatureDef("er20_v1", "Kaufman efficiency ratio, last 20 candles (%)", "close"),
        FeatureDef("range_atr_v1", "last candle range / ATR14", "high, low, ATR"),
        FeatureDef("atr_pct_v1", "ATR14 / price × 100", "ATR, price"),
        FeatureDef("pillar_bull_v1", "weighted share of pillars voting BUY (%)", "7 pillars"),
        FeatureDef("pillar_bear_v1", "weighted share of pillars voting SELL (%)", "7 pillars"),
        FeatureDef("mtf_net_v1", "timeframes up − timeframes down (EMA rule)", "PAXG candles 5M-1D"),
        FeatureDef("bots_net_v1", "bots BUY − bots SELL", "6 rule bots"),
        FeatureDef("anomaly_v1", "anomaly severity 0-3", "anomaly engine"),
        FeatureDef("xm_net_v1", "cross-market assets implying up − implying down", "cross-market"),
        FeatureDef("dxy_ret24_v1", "US Dollar Index 24h change %", "DX-Y.NYB"),
        FeatureDef("dxy_corr30_v1", "30D correlation gold vs dollar (hourly returns)", "GC=F, DX-Y.NYB"),
        FeatureDef("us10y_ret24_v1", "US 10Y yield 24h change %", "^TNX"),
        FeatureDef("vix_ret24_v1", "VIX 24h change %", "^VIX"),
        FeatureDef("news_v1", "1 if a high-impact news window is active", "calendar", inDistance = false),
        FeatureDef("hour_utc_v1", "hour of day (UTC)", "clock", inDistance = false),
        FeatureDef("dow_v1", "day of week (1=Mon … 7=Sun, UTC)", "clock", inDistance = false)
    )

    val DISTANCE_KEYS: Set<String> = DEFS.filter { it.inDistance }.map { it.name }.toSet()

    fun ui(): List<FeatureDefUi> = DEFS.map { FeatureDefUi(it.name, it.formula, it.inputs) }

    /** Computes the feature vector. A feature whose inputs are missing is simply absent (never 0). */
    fun compute(a: GoldAnalysisResult, xm: CrossMarketReport?, anomaly: AnomalyReport?, now: Long): Map<String, Double> {
        val f = LinkedHashMap<String, Double>()
        val facts = LinkedHashMap<String, Double>()
        a.groups.forEach { g -> g.indicators.forEach { ind ->
            MarketSnapshotBuilder.extractFacts(ind.name, ind.valueDisplay).forEach { (k, v) -> facts.putIfAbsent(MarketSnapshotBuilder.norm(k), v) }
        } }
        fun put(name: String, v: Double?) { if (v != null && !v.isNaN() && !v.isInfinite()) f[name] = v }
        val atr = PredictionLedger.atrOf(a.recentCandles).takeIf { it > 0 }
        val px = a.currentPrice
        fun perAtr(x: Double?): Double? = if (x == null || atr == null) null else x / atr

        put("rsi14_v1", facts["rsi14"])
        put("adx_v1", facts["adx"])
        val pdi = facts["+di"]; val mdi = facts["-di"]
        put("di_spread_v1", if (pdi != null && mdi != null) pdi - mdi else null)
        val e9 = facts["ema9"]; val e21 = facts["ema21"]; val e50 = facts["ema50"]
        put("ema9_21_atr_v1", perAtr(if (e9 != null && e21 != null) e9 - e21 else null))
        put("px_ema50_atr_v1", perAtr(if (e50 != null) px - e50 else null))
        val macd = facts["macd"]; val sig = facts["sig"]
        put("macd_hist_atr_v1", perAtr(if (macd != null && sig != null) macd - sig else null))
        put("stoch_k_v1", facts["stochastick"])
        put("cci20_v1", facts["cci20"])
        put("mfi14_v1", facts["moneyflowindexmfi14"])
        put("roc10_v1", facts["roc10velocity"])
        val bu = facts["u"]; val bl = facts["l"]
        put("bb_pos_v1", if (bu != null && bl != null && bu > bl) (px - bl) / (bu - bl) else null)
        put("px_pivot_atr_v1", perAtr(px - a.pivotLevels.pivot))
        a.insights?.let { ins ->
            put("er20_v1", ins.trendEfficiencyPercent.toDouble())
            put("range_atr_v1", ins.lastRangeVsAtr)
            put("pillar_bull_v1", ins.bullishPoints)
            put("pillar_bear_v1", ins.bearishPoints)
        }
        put("atr_pct_v1", if (atr != null && px > 0) atr / px * 100.0 else null)
        val mtf = a.mtfMatrix?.timeframes?.filter { it.label.startsWith("PAXG") } ?: emptyList()
        if (mtf.isNotEmpty()) put("mtf_net_v1", (mtf.count { it.signal == Signal.BUY } - mtf.count { it.signal == Signal.SELL }).toDouble())
        a.multiBotEnsemble?.let { put("bots_net_v1", (it.buyVotes - it.sellVotes).toDouble()) }
        anomaly?.let { put("anomaly_v1", it.severity.toDouble()) }
        if (xm != null && xm.available) {
            put("xm_net_v1", (xm.supportBull - xm.supportBear).toDouble())
            fun asset(k: String) = xm.assets.firstOrNull { it.key == k && it.health != "UNAVAILABLE" }
            asset("DXY")?.let { put("dxy_ret24_v1", it.change24hPct); if (it.n30 >= CrossMarketEngine.MIN_PAIRS_30D) put("dxy_corr30_v1", it.corr30d) }
            asset("US10Y")?.let { put("us10y_ret24_v1", it.change24hPct) }
            asset("VIX")?.let { put("vix_ret24_v1", it.change24hPct) }
        }
        val news = a.newsMode != null || a.newsTradingPlan?.isNewsActive == true || a.isNewsModeTriggered
        put("news_v1", if (news) 1.0 else 0.0)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = now }
        put("hour_utc_v1", cal.get(Calendar.HOUR_OF_DAY).toDouble())
        put("dow_v1", ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1).toDouble())
        return f
    }
}

class FeatureStore(private val store: LedgerStore?) {

    private val rows = LinkedHashMap<String, FeatureRow>()
    @Volatile var lastWriteOk: Boolean = true; private set

    init {
        if (store != null) {
            try {
                store.readAll().forEach { raw ->
                    if (!raw.contains("\"feat\"")) return@forEach
                    decode(raw)?.let { rows[it.recordId] = it }
                }
            } catch (_: Exception) {
            }
        }
    }

    @Synchronized fun size(): Int = rows.size
    @Synchronized fun all(): List<FeatureRow> = ArrayList(rows.values)
    @Synchronized fun has(recordId: String): Boolean = rows.containsKey(recordId)

    /** Stores the features of a prediction once. Returns true if a new row was written. */
    @Synchronized
    fun recordIfAbsent(recordId: String, at: Long, interval: String, values: Map<String, Double>): Boolean {
        if (recordId.isEmpty() || values.isEmpty() || rows.containsKey(recordId)) return false
        val row = FeatureRow(recordId, at, interval, values)
        rows[recordId] = row
        if (store != null) lastWriteOk = try { store.append(listOf(encode(row))); true } catch (_: Exception) { false }
        return true
    }

    fun encode(r: FeatureRow): String = MiniJson.write(
        linkedMapOf("t" to "feat", "rid" to r.recordId, "at" to r.at, "iv" to r.interval, "fv" to FeatureCatalog.VERSION, "f" to r.values)
    )

    @Suppress("UNCHECKED_CAST")
    fun decode(raw: String): FeatureRow? = try {
        val m = MiniJson.parse(raw) as? Map<String, Any?>
        if (m == null || m.str("t") != "feat") null else FeatureRow(
            m.str("rid"), m.long("at"), m.str("iv"),
            m.obj("f").mapNotNull { (k, v) -> (v as? Number)?.let { k to it.toDouble() } }.toMap()
        )
    } catch (_: Exception) {
        null
    }
}

/**
 * HISTORICAL ANALOGS + FAMILIARITY
 * Compares the current feature vector with past predictions of the same timeframe whose real outcome is
 * already known (no future information). With too little history it says so instead of inventing a number.
 */
object AnalogEngine {

    const val MIN_HISTORY = 40
    const val MIN_MATCHES = 10
    private const val MAX_DISTANCE = 1.5

    private class Past(val row: FeatureRow, val move: Double, val threshold: Double)

    private fun history(fs: FeatureStore, state: LedgerState, interval: String, now: Long): List<Past> {
        val recs = state.records.associateBy { it.id }
        return fs.all().mapNotNull { row ->
            if (row.interval != interval) return@mapNotNull null
            val rec = recs[row.recordId] ?: return@mapNotNull null
            if (rec.expiresAt >= now) return@mapNotNull null
            val res = state.resultOf(row.recordId) ?: return@mapNotNull null
            if (res.outcome == LedgerOutcome.DATA_FAILURE || res.outcome == LedgerOutcome.MARKET_CLOSED || res.outcome == LedgerOutcome.PENDING) return@mapNotNull null
            Past(row, res.move, res.threshold)
        }
    }

    fun analogs(current: Map<String, Double>, fs: FeatureStore, state: LedgerState, interval: String, now: Long): AnalogReport {
        val hist = history(fs, state, interval, now)
        if (hist.size < MIN_HISTORY) {
            return AnalogReport("INSUFFICIENT", hist.size, 0, 0, 0, 0, 0.0, 0,
                "INSUFFICIENT ANALOG HISTORY: ${hist.size} checked predictions with features on $interval, need $MIN_HISTORY")
        }
        val keys = FeatureCatalog.DISTANCE_KEYS.filter { current.containsKey(it) }
        // standardise with the history's own mean / spread
        val stats = HashMap<String, Pair<Double, Double>>()
        for (k in keys) {
            val vals = hist.mapNotNull { it.row.values[k] }
            if (vals.size < MIN_HISTORY / 2) continue
            val m = vals.average()
            val sd = sqrt(vals.sumOf { (it - m) * (it - m) } / vals.size)
            if (sd > 1e-9) stats[k] = m to sd
        }
        if (stats.size < 8) {
            return AnalogReport("INSUFFICIENT", hist.size, 0, 0, 0, 0, 0.0, 0, "INSUFFICIENT ANALOG HISTORY: only ${stats.size} comparable features")
        }
        val scored = hist.mapNotNull { p ->
            var sum = 0.0; var n = 0
            for ((k, ms) in stats) {
                val a = current[k] ?: continue
                val b = p.row.values[k] ?: continue
                val d = (a - b) / ms.second
                sum += d * d; n++
            }
            if (n < 8) null else p to sqrt(sum / n)
        }.sortedBy { it.second }
        val k = (hist.size / 5).coerceIn(MIN_MATCHES, 50)
        val near = scored.take(k).filter { it.second <= MAX_DISTANCE }
        if (near.size < MIN_MATCHES) {
            return AnalogReport("INSUFFICIENT", hist.size, near.size, 0, 0, 0, 0.0, 0,
                "INSUFFICIENT ANALOG HISTORY: only ${near.size} close matches in ${hist.size} past predictions")
        }
        val up = near.count { it.first.move >= it.first.threshold }
        val down = near.count { it.first.move <= -it.first.threshold }
        val n = near.size
        val upPct = (100.0 * up / n).roundToInt()
        val downPct = (100.0 * down / n).roundToInt()
        val avgDist = near.map { it.second }.average()
        return AnalogReport(
            "OK", hist.size, n, upPct, downPct, 100 - upPct - downPct,
            near.map { it.first.move }.average(),
            (100.0 / (1.0 + avgDist)).roundToInt(),
            "$n most similar past predictions out of ${hist.size} (${stats.size} features compared)"
        )
    }

    fun familiarity(current: Map<String, Double>, fs: FeatureStore, state: LedgerState, interval: String, now: Long): FamiliarityReport {
        val hist = history(fs, state, interval, now)
        if (hist.size < MIN_HISTORY) return FamiliarityReport("UNKNOWN", -1, emptyList(), hist.size)
        var inside = 0; var total = 0
        val outside = ArrayList<String>()
        for (k in FeatureCatalog.DISTANCE_KEYS) {
            val v = current[k] ?: continue
            val vals = hist.mapNotNull { it.row.values[k] }.sorted()
            if (vals.size < MIN_HISTORY / 2) continue
            val lo = vals[(vals.size * 0.05).toInt()]
            val hi = vals[((vals.size * 0.95).toInt()).coerceAtMost(vals.size - 1)]
            total++
            if (v in lo..hi) inside++ else outside.add(k.removeSuffix("_v1"))
        }
        if (total < 8) return FamiliarityReport("UNKNOWN", -1, emptyList(), hist.size)
        val pct = (100.0 * inside / total).roundToInt()
        val status = if (pct >= 75) "NORMAL" else if (pct >= 50) "UNUSUAL" else "OUT_OF_DISTRIBUTION"
        return FamiliarityReport(status, pct, outside.take(6), hist.size)
    }
}
