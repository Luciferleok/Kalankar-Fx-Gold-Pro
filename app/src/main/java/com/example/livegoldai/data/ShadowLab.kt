package com.example.livegoldai.data

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * SHADOW LAB (V28.0 / V28.1)
 *
 * A walk-forward tournament on real XAU/USD candle history. It answers one question honestly:
 * "Does a simple factor model beat plain baselines on data it has never seen?"
 *
 * Rules (from the master prompt, sections 135-205):
 *  - closed candles only: the newest (still forming) candle is never used
 *  - one immutable snapshot: every model sees exactly the same bars, identified by [Report.snapshotId]
 *  - label v1 ([LABEL_VERSION]): close after [HORIZON] candles vs close now. Move >= 0.25 x ATR14 = UP or DOWN,
 *    smaller = FLAT (counted separately, never as right)
 *  - walk-forward: a model is trained only on samples whose label was already known before the test block
 *    starts (purge of the horizon), then tested on the next block, then the window moves on
 *  - baselines are scored on the very same test samples
 *  - several challengers are tried, so the verdict uses a stricter 95% range corrected for that number
 *    (Bonferroni); the horizon sweep is corrected the same way
 *  - a challenger is an EDGE CANDIDATE only if the low end of its corrected range beats the best baseline
 *    AND 50%, with at least [MIN_CALLS] decided calls, coverage >= 15% and a positive move after costs
 *  - SHADOW ONLY: nothing here changes the live BUY/SELL/WAIT
 */
object ShadowLab {

    const val LAB_VERSION = "SL1.1"
    const val LABEL_VERSION = "L1"
    const val HORIZON = 4               // closed candles ahead
    val SWEEP = listOf(2, 4, 8)         // horizons compared (exploratory, corrected for 3 tests)
    const val FLAT_ATR = 0.25           // |move| below this many ATRs = FLAT
    const val COST_USD = 0.40           // assumed spread + slippage per call, in USD per ounce
    const val MIN_CALLS = 100           // decided calls needed before a verdict
    const val MIN_COVERAGE = 0.15
    const val MIN_SAMPLES = 600
    const val WARMUP = 110              // bars needed before the first factor row (EMA50 + 100-candle medians)
    const val TRAIN_MAX = 1500          // rolling training window (samples)
    const val TEST_BLOCK = 250
    const val TARGET_COVERAGE = 0.30    // gate is set on TRAINING data so ~30% of samples get a call
    const val META_MIN = 200            // past out-of-sample calls needed before the meta filter is used
    const val N_CHALLENGERS = 4
    val Z_CHALLENGERS = 2.50            // two-sided 95% with Bonferroni for 4 challengers
    val Z_SWEEP = 2.39                  // two-sided 95% with Bonferroni for 3 horizons

    data class Bar(val time: String, val open: Double, val high: Double, val low: Double, val close: Double)

    enum class Regime { TREND, RANGE, HIGH_VOL, UNCLEAR }

    /** Continuous factors, all in ATR units or bounded, computed from bars up to and including i. */
    data class Factors(
        val trend: Double,      // (EMA20 - EMA50) / ATR
        val momentum: Double,   // 10-candle return / (ATR * sqrt(10))
        val stretch: Double,    // (close - EMA20) / ATR
        val rangePos: Double,   // position inside the 20-candle high-low range, -1..+1
        val volRatio: Double,   // ATR / median ATR of the last 100 candles
        val efficiency: Double, // Kaufman efficiency ratio over 20 candles, 0..1
        val regime: Regime,
        val sessionSin: Double = 0.0,   // hour of day (UTC) on a circle; 0 for daily and slower candles
        val sessionCos: Double = 0.0,
        val squeeze: Double = 0.0       // ln(Bollinger width / its 100-candle median): below 0 = compressed
    ) {
        fun directional() = doubleArrayOf(trend, momentum, stretch, rangePos, trend * efficiency)
        fun extended() = doubleArrayOf(trend, momentum, stretch, rangePos, trend * efficiency, sessionSin, sessionCos, squeeze, squeeze * momentum)
        fun context(conf: Double) = doubleArrayOf(conf, volRatio, efficiency,
            if (regime == Regime.TREND) 1.0 else 0.0, if (regime == Regime.RANGE) 1.0 else 0.0, squeeze, sessionSin, sessionCos)
    }

    data class ModelScore(
        val name: String,
        val kind: String,           // BASELINE / CHALLENGER
        val note: String,
        val samples: Int,           // test samples seen
        val calls: Int,             // UP or DOWN calls made
        val right: Int,
        val wrong: Int,
        val flat: Int,              // calls where the market did not move 0.25 x ATR either way
        val avgNetUsd: Double?,     // mean signed move per call after COST_USD, null when no calls
        val wilsonLow: Double?,     // percent, plain 95%, null below 10 decided
        val wilsonHigh: Double?,
        val strictLow: Double? = null   // percent, corrected for the number of challengers / horizons tried
    ) {
        val decided get() = right + wrong
        val accuracy: Double? get() = if (decided == 0) null else 100.0 * right / decided
        val coverage: Double get() = if (samples == 0) 0.0 else calls.toDouble() / samples
    }

    data class RegimeLine(val regime: String, val samples: Int, val calls: Int, val right: Int, val wrong: Int)
    data class HorizonLine(val horizon: Int, val score: ModelScore, val bestBaseline: String, val bestBaselineAcc: Double?)
    /** Calibration: when the model said this sure, how often was it really right (out-of-sample, decided outcomes). */
    data class CalibLine(val bin: String, val n: Int, val predicted: Double, val actual: Double)

    /** The fitted FACTOR GATED model, used by the live shadow recorder. */
    data class LiveModel(
        val interval: String,
        val weights: List<Double>,
        val mean: List<Double>,
        val sd: List<Double>,
        val gate: Double,
        val horizon: Int,
        val snapshotId: String,
        val verdict: String,
        val trainedAt: Long
    ) {
        val id: String get() = "$snapshotId/$horizon"
        fun prob(f: Factors): Double = Logit.Model(weights.toDoubleArray(), mean.toDoubleArray(), sd.toDoubleArray()).prob(f.directional())
        fun call(f: Factors): Pair<Int, Double> {
            val p = prob(f)
            return (if (abs(p - 0.5) >= gate) (if (p > 0.5) 1 else -1) else 0) to p
        }
    }

    data class CurrentRead(
        val barTime: String,
        val regime: String,
        val factorsText: String,
        val probUp: Double?,        // null = model could not be fitted
        val call: String,           // UP / DOWN / NO CALL
        val reason: String
    )

    data class Report(
        val interval: String,
        val snapshotId: String,
        val labelVersion: String,
        val labVersion: String,
        val bars: Int,
        val firstBar: String,
        val lastClosedBar: String,
        val samples: Int,
        val folds: Int,
        val upShare: Double,        // share of UP labels among UP/DOWN outcomes in the test period
        val flatShare: Double,
        val models: List<ModelScore>,
        val bestBaseline: String,
        val verdict: String,        // EDGE CANDIDATE / NO EDGE / NOT ENOUGH HISTORY
        val verdictDetail: String,
        val regimes: List<RegimeLine>,
        val current: CurrentRead?,
        val createdAtMs: Long,
        val horizons: List<HorizonLine> = emptyList(),
        val calibration: List<CalibLine> = emptyList(),
        val live: LiveModel? = null
    )

    // ------------------------------------------------------------------ public entry

    fun run(interval: String, closedBars: List<Bar>, now: Long = System.currentTimeMillis()): Report {
        val bars = closedBars
        val sid = snapshotId(interval, bars)
        val first = bars.firstOrNull()?.time ?: "--"
        val last = bars.lastOrNull()?.time ?: "--"
        val f = factorRows(bars)
        val atr = atr14(bars)
        val idx = (WARMUP until bars.size - HORIZON).filter { f[it] != null && atr[it] > 0 }
        if (idx.size < MIN_SAMPLES) {
            return Report(interval, sid, LABEL_VERSION, LAB_VERSION, bars.size, first, last, idx.size, 0, 0.0, 0.0,
                emptyList(), "--", "NOT ENOUGH HISTORY",
                "Needs at least $MIN_SAMPLES labelled samples, has ${idx.size}. Try a shorter timeframe (more history fits in one download).",
                emptyList(), null, now)
        }
        val label = labels(bars, atr, idx, HORIZON)
        val moveUsd = DoubleArray(bars.size).also { m -> for (i in idx) m[i] = bars[i + HORIZON].close - bars[i].close }

        // ---- walk-forward
        val initialTrain = max(300, (idx.size * 0.35).toInt())
        val acc = LinkedHashMap<String, Acc>()
        fun accOf(name: String, kind: String, note: String) = acc.getOrPut(name) { Acc(name, kind, note) }
        val regimeAcc = LinkedHashMap<Regime, IntArray>()   // samples, calls, right, wrong (FACTOR GATED)
        Regime.values().forEach { regimeAcc[it] = IntArray(4) }
        val calib = ArrayList<Pair<Double, Boolean>>()       // (P(up), outcome was UP) for decided test samples
        val metaX = ArrayList<DoubleArray>(); val metaY = ArrayList<Boolean>()   // out-of-sample primary calls
        var folds = 0
        var testUp = 0; var testDown = 0; var testFlat = 0
        val rnd = java.util.Random(sid.hashCode().toLong())

        var start = initialTrain
        while (start < idx.size) {
            val end = min(idx.size, start + TEST_BLOCK)
            val testStartBar = idx[start]
            // purge: only samples whose label window closed before the first test bar
            val trainIdx = idx.subList(max(0, start - TRAIN_MAX), start).filter { it + HORIZON < testStartBar }
            val dec = trainIdx.filter { label[it] != 0 }
            val global = Logit.fit(dec.map { f[it]!!.directional() }, dec.map { label[it] == 1 })
            val gate = global?.let { g -> gateFor(trainIdx.map { g.prob(f[it]!!.directional()) }) }
            val plus = Logit.fit(dec.map { f[it]!!.extended() }, dec.map { label[it] == 1 })
            val plusGate = plus?.let { g -> gateFor(trainIdx.map { g.prob(f[it]!!.extended()) }) }
            val perRegime = HashMap<Regime, Pair<Logit.Model, Double>>()
            for (r in listOf(Regime.TREND, Regime.RANGE)) {
                val tr = trainIdx.filter { f[it]!!.regime == r }
                val d = tr.filter { label[it] != 0 }
                if (d.size < 150) continue
                val m = Logit.fit(d.map { f[it]!!.directional() }, d.map { label[it] == 1 }) ?: continue
                perRegime[r] = m to gateFor(tr.map { m.prob(f[it]!!.directional()) })
            }
            // meta filter: learned only from earlier folds' out-of-sample calls
            val meta = if (metaY.size >= META_MIN) Logit.fit(metaX, metaY) else null
            val foldMetaX = ArrayList<DoubleArray>(); val foldMetaY = ArrayList<Boolean>()
            folds++
            for (k in start until end) {
                val i = idx[k]
                val fi = f[i]!!
                val y = label[i]
                when (y) { 1 -> testUp++; -1 -> testDown++; else -> testFlat++ }
                val c = bars[i].close
                // baselines: fixed rules, nothing fitted
                accOf("MOMENTUM", "BASELINE", "same direction as the last $HORIZON candles").add(sign(c - bars[i - HORIZON].close), y, moveUsd[i])
                accOf("REVERSION", "BASELINE", "opposite of the last $HORIZON candles").add(-sign(c - bars[i - HORIZON].close), y, moveUsd[i])
                accOf("EMA TREND", "BASELINE", "EMA20 above EMA50 = UP").add(sign(fi.trend), y, moveUsd[i])
                accOf("ALWAYS UP", "BASELINE", "gold's long-run drift").add(1, y, moveUsd[i])
                accOf("COIN FLIP", "BASELINE", "random, negative control").add(if (rnd.nextBoolean()) 1 else -1, y, moveUsd[i])
                // challengers
                var pG = 0.5
                val dGlobal = if (global != null && gate != null) {
                    pG = global.prob(fi.directional()); if (abs(pG - 0.5) >= gate) (if (pG > 0.5) 1 else -1) else 0
                } else 0
                accOf("FACTOR GATED", "CHALLENGER", "5 factors, fitted on past data only, calls only its strongest ~30%").add(dGlobal, y, moveUsd[i])
                if (global != null && y != 0) calib.add(pG to (y == 1))
                val pr = perRegime[fi.regime]
                val dReg = if (pr != null) {
                    val p = pr.first.prob(fi.directional()); if (abs(p - 0.5) >= pr.second) (if (p > 0.5) 1 else -1) else 0
                } else 0
                accOf("REGIME FIRST", "CHALLENGER", "separate model for TREND and RANGE, no call in HIGH VOL / UNCLEAR").add(dReg, y, moveUsd[i])
                val dPlus = if (plus != null && plusGate != null) {
                    val p = plus.prob(fi.extended()); if (abs(p - 0.5) >= plusGate) (if (p > 0.5) 1 else -1) else 0
                } else 0
                accOf("FACTOR+", "CHALLENGER", "adds time of day and volatility squeeze; kept only if it beats FACTOR GATED").add(dPlus, y, moveUsd[i])
                // meta filter: take FACTOR GATED's call only when the context model trusts it
                val ctx = fi.context(abs(pG - 0.5))
                val dMeta = if (dGlobal != 0 && meta != null && meta.prob(ctx) >= 0.5) dGlobal else 0
                accOf("META FILTER", "CHALLENGER", "FACTOR GATED, but skips calls in conditions where its past calls failed").add(dMeta, y, moveUsd[i])
                if (dGlobal != 0 && y != 0) { foldMetaX.add(ctx); foldMetaY.add(y == dGlobal) }
                val ra = regimeAcc[fi.regime]!!
                ra[0]++
                if (dGlobal != 0) { ra[1]++; if (y == dGlobal) ra[2]++ else if (y == -dGlobal) ra[3]++ }
            }
            metaX.addAll(foldMetaX); metaY.addAll(foldMetaY)
            start = end
        }

        val models = acc.values.map { it.score(if (it.kind == "CHALLENGER") Z_CHALLENGERS else null) }
        val (bestName, bestAcc) = bestBaseline(models)
        val challengers = models.filter { it.kind == "CHALLENGER" }
        val qualified = challengers.filter { it.decided >= MIN_CALLS && it.coverage >= MIN_COVERAGE }
        val top = qualified.maxByOrNull { it.strictLow ?: 0.0 }
        val (verdict, detail) = when {
            top == null -> "NO EDGE" to "No challenger reached $MIN_CALLS decided calls with ${(MIN_COVERAGE * 100).toInt()}% coverage yet."
            (top.strictLow ?: 0.0) > max(50.0, bestAcc) && (top.avgNetUsd ?: 0.0) > 0 ->
                "EDGE CANDIDATE" to "${top.name}: low end of its strict range ${f1(top.strictLow)}% (corrected for $N_CHALLENGERS challengers) beats the best baseline $bestName (${f1(bestAcc)}%) and makes ${usd(top.avgNetUsd)} per call after an assumed ${usd(COST_USD)} cost. Candidate only: it must now hold up in live shadow before it may ever be used."
            else -> "NO EDGE" to "Best challenger ${top.name} ${f1(top.accuracy)}% (strict range from ${f1(top.strictLow)}%) does not clearly beat the best baseline $bestName ${f1(bestAcc)}%" +
                (if ((top.avgNetUsd ?: 0.0) <= 0) " and loses ${usd(abs(top.avgNetUsd ?: 0.0))} per call after costs." else ".")
        }
        val regimes = regimeAcc.map { (r, a) -> RegimeLine(r.name.replace('_', ' '), a[0], a[1], a[2], a[3]) }.filter { it.samples > 0 }
        val decidedTest = testUp + testDown
        val horizons = SWEEP.map { h ->
            if (h == HORIZON) HorizonLine(h, models.first { it.name == "FACTOR GATED" }.let { m ->
                m.copy(strictLow = ResearchLab.wilsonZ(m.right, m.decided, Z_SWEEP)?.first) }, bestName, bestAcc)
            else sweepOne(bars, f, atr, h)
        }
        return Report(
            interval, sid, LABEL_VERSION, LAB_VERSION, bars.size, first, last, idx.size, folds,
            if (decidedTest == 0) 0.0 else testUp.toDouble() / decidedTest,
            if (testUp + testDown + testFlat == 0) 0.0 else testFlat.toDouble() / (testUp + testDown + testFlat),
            models, bestName, verdict, detail, regimes,
            currentRead(bars, f, idx, label), now,
            horizons, calibration(calib), liveModel(interval, sid, verdict, f, idx, label, now)
        )
    }

    private fun bestBaseline(models: List<ModelScore>): Pair<String, Double> {
        val b = models.filter { it.kind == "BASELINE" && it.name != "COIN FLIP" && it.decided >= MIN_CALLS }.maxByOrNull { it.accuracy ?: 0.0 }
        return (b?.name ?: "50%") to (b?.accuracy ?: 50.0)
    }

    fun labels(bars: List<Bar>, atr: DoubleArray, idx: List<Int>, h: Int): IntArray {
        val label = IntArray(bars.size)
        for (i in idx) {
            if (i + h >= bars.size) continue
            val m = bars[i + h].close - bars[i].close
            label[i] = when { m >= FLAT_ATR * atr[i] -> 1; m <= -FLAT_ATR * atr[i] -> -1; else -> 0 }
        }
        return label
    }

    /** FACTOR GATED and the baselines at another horizon, same walk-forward. */
    private fun sweepOne(bars: List<Bar>, f: List<Factors?>, atr: DoubleArray, h: Int): HorizonLine {
        val idx = (WARMUP until bars.size - h).filter { f[it] != null && atr[it] > 0 }
        val label = labels(bars, atr, idx, h)
        val model = Acc("FACTOR GATED", "CHALLENGER", "")
        val base = LinkedHashMap<String, Acc>()
        var start = max(300, (idx.size * 0.35).toInt())
        while (start < idx.size) {
            val end = min(idx.size, start + TEST_BLOCK)
            val trainIdx = idx.subList(max(0, start - TRAIN_MAX), start).filter { it + h < idx[start] }
            val dec = trainIdx.filter { label[it] != 0 }
            val g = Logit.fit(dec.map { f[it]!!.directional() }, dec.map { label[it] == 1 })
            val gate = g?.let { m -> gateFor(trainIdx.map { m.prob(f[it]!!.directional()) }) }
            for (k in start until end) {
                val i = idx[k]; val fi = f[i]!!; val y = label[i]
                val mv = bars[i + h].close - bars[i].close
                val d = if (g != null && gate != null) { val p = g.prob(fi.directional()); if (abs(p - 0.5) >= gate) (if (p > 0.5) 1 else -1) else 0 } else 0
                model.add(d, y, mv)
                base.getOrPut("MOMENTUM") { Acc("MOMENTUM", "BASELINE", "") }.add(sign(bars[i].close - bars[i - h].close), y, mv)
                base.getOrPut("REVERSION") { Acc("REVERSION", "BASELINE", "") }.add(-sign(bars[i].close - bars[i - h].close), y, mv)
                base.getOrPut("EMA TREND") { Acc("EMA TREND", "BASELINE", "") }.add(sign(fi.trend), y, mv)
                base.getOrPut("ALWAYS UP") { Acc("ALWAYS UP", "BASELINE", "") }.add(1, y, mv)
            }
            start = end
        }
        val (bn, ba) = bestBaseline(base.values.map { it.score(null) })
        return HorizonLine(h, model.score(Z_SWEEP), bn, ba)
    }

    private fun calibration(c: List<Pair<Double, Boolean>>): List<CalibLine> {
        val bins = listOf(0.50 to 0.55, 0.55 to 0.60, 0.60 to 0.65, 0.65 to 1.01)
        return bins.mapNotNull { (lo, hi) ->
            val inBin = c.filter { val conf = max(it.first, 1 - it.first); conf >= lo && conf < hi }
            if (inBin.isEmpty()) null
            else CalibLine(
                String.format(Locale.US, "%.0f-%.0f%%", lo * 100, min(hi, 1.0) * 100),
                inBin.size,
                100.0 * inBin.map { max(it.first, 1 - it.first) }.average(),
                100.0 * inBin.count { (it.first > 0.5) == it.second } / inBin.size
            )
        }
    }

    private fun liveModel(interval: String, sid: String, verdict: String, f: List<Factors?>, idx: List<Int>, label: IntArray, now: Long): LiveModel? {
        val train = idx.takeLast(TRAIN_MAX)
        val dec = train.filter { label[it] != 0 }
        val m = Logit.fit(dec.map { f[it]!!.directional() }, dec.map { label[it] == 1 }) ?: return null
        val gate = gateFor(train.map { m.prob(f[it]!!.directional()) })
        return LiveModel(interval, m.w.toList(), m.mean.toList(), m.sd.toList(), gate, HORIZON, sid, verdict, now)
    }

    // ------------------------------------------------------------------ current shadow read

    private fun currentRead(bars: List<Bar>, f: List<Factors?>, idx: List<Int>, label: IntArray): CurrentRead? {
        val lastI = bars.lastIndex
        val fl = f.getOrNull(lastI) ?: return null
        val train = idx.takeLast(TRAIN_MAX)
        val dec = train.filter { label[it] != 0 }
        val m = Logit.fit(dec.map { f[it]!!.directional() }, dec.map { label[it] == 1 })
        val txt = factorsText(fl)
        if (m == null) return CurrentRead(bars[lastI].time, fl.regime.name.replace('_', ' '), txt, null, "NO CALL", "model could not be fitted")
        val gate = gateFor(train.map { m.prob(f[it]!!.directional()) })
        val p = m.prob(fl.directional())
        val call = if (abs(p - 0.5) >= gate) (if (p > 0.5) "UP" else "DOWN") else "NO CALL"
        val reason = if (call == "NO CALL") String.format(Locale.US, "P(up) %.0f%% is inside the no-call zone (needs %.0f%% or %.0f%%)", p * 100, (0.5 + gate) * 100, (0.5 - gate) * 100)
        else String.format(Locale.US, "P(up) %.0f%% is outside the no-call zone", p * 100)
        return CurrentRead(bars[lastI].time, fl.regime.name.replace('_', ' '), txt, p, call, reason)
    }

    fun factorsText(fl: Factors) = String.format(Locale.US,
        "trend %+.2f • momentum %+.2f • stretch %+.2f • range pos %+.2f • vol x%.2f • efficiency %.2f • squeeze %+.2f",
        fl.trend, fl.momentum, fl.stretch, fl.rangePos, fl.volRatio, fl.efficiency, fl.squeeze)

    // ------------------------------------------------------------------ factors

    fun atr14(bars: List<Bar>): DoubleArray {
        val out = DoubleArray(bars.size)
        var a = 0.0
        for (i in bars.indices) {
            val tr = if (i == 0) bars[i].high - bars[i].low
            else max(bars[i].high - bars[i].low, max(abs(bars[i].high - bars[i - 1].close), abs(bars[i].low - bars[i - 1].close)))
            a = if (i < 14) (a * i + tr) / (i + 1) else (a * 13 + tr) / 14
            out[i] = a
        }
        return out
    }

    private fun ema(values: DoubleArray, n: Int): DoubleArray {
        val k = 2.0 / (n + 1)
        val out = DoubleArray(values.size)
        for (i in values.indices) out[i] = if (i == 0) values[0] else values[i] * k + out[i - 1] * (1 - k)
        return out
    }

    /** Hour of day from "yyyy-MM-dd HH:mm:ss" (UTC). null for date-only candles. */
    private fun hourOf(t: String): Double? {
        if (t.length < 16) return null
        val h = t.substring(11, 13).toIntOrNull() ?: return null
        val m = t.substring(14, 16).toIntOrNull() ?: 0
        return h + m / 60.0
    }

    /** Factor row per bar, null during warm-up. Uses only bars 0..i for row i. */
    fun factorRows(bars: List<Bar>): List<Factors?> {
        val n = bars.size
        if (n == 0) return emptyList()
        val close = DoubleArray(n) { bars[it].close }
        val atr = atr14(bars)
        val e20 = ema(close, 20)
        val e50 = ema(close, 50)
        // Bollinger width (4 x std of 20 closes) / close
        val bw = DoubleArray(n)
        for (i in 19 until n) {
            var s = 0.0; var s2 = 0.0
            for (j in i - 19..i) { s += close[j]; s2 += close[j] * close[j] }
            val mu = s / 20; val v = max(0.0, s2 / 20 - mu * mu)
            bw[i] = 4 * sqrt(v) / close[i]
        }
        val out = ArrayList<Factors?>(n)
        for (i in 0 until n) {
            if (i < WARMUP - 1 || atr[i] <= 0) { out.add(null); continue }
            val a = atr[i]
            var hi = Double.NEGATIVE_INFINITY; var lo = Double.POSITIVE_INFINITY
            for (j in i - 19..i) { hi = max(hi, bars[j].high); lo = min(lo, bars[j].low) }
            var path = 0.0
            for (j in i - 19..i) path += abs(close[j] - close[j - 1])
            val er = if (path <= 0) 0.0 else abs(close[i] - close[i - 20]) / path
            val window = (i - 99..i).map { atr[it] }.sorted()
            val med = window[window.size / 2]
            val bwWin = (i - 99..i).map { bw[it] }.filter { it > 0 }.sorted()
            val bwMed = if (bwWin.isEmpty()) 0.0 else bwWin[bwWin.size / 2]
            val trend = clip((e20[i] - e50[i]) / a)
            val volRatio = if (med <= 0) 1.0 else a / med
            val regime = when {
                volRatio > 1.6 -> Regime.HIGH_VOL
                er > 0.35 && abs(trend) > 0.8 -> Regime.TREND
                er < 0.20 && abs(trend) < 1.0 -> Regime.RANGE
                else -> Regime.UNCLEAR
            }
            val hr = hourOf(bars[i].time)
            out.add(Factors(
                trend = trend,
                momentum = clip((close[i] - close[i - 10]) / (a * sqrt(10.0))),
                stretch = clip((close[i] - e20[i]) / a),
                rangePos = if (hi - lo <= 0) 0.0 else 2 * (close[i] - lo) / (hi - lo) - 1,
                volRatio = volRatio,
                efficiency = er,
                regime = regime,
                sessionSin = if (hr == null) 0.0 else sin(2 * PI * hr / 24),
                sessionCos = if (hr == null) 0.0 else cos(2 * PI * hr / 24),
                squeeze = if (bw[i] <= 0 || bwMed <= 0) 0.0 else clip(ln(bw[i] / bwMed))
            ))
        }
        return out
    }

    private fun clip(x: Double) = x.coerceIn(-4.0, 4.0)
    private fun sign(x: Double) = when { x > 0 -> 1; x < 0 -> -1; else -> 0 }

    /** Smallest |p - 0.5| that still lets ~TARGET_COVERAGE of the training samples through. */
    private fun gateFor(trainProbs: List<Double>): Double {
        if (trainProbs.isEmpty()) return 0.5
        val d = trainProbs.map { abs(it - 0.5) }.sortedDescending()
        val k = (d.size * TARGET_COVERAGE).toInt().coerceIn(1, d.size)
        return max(0.02, d[k - 1])
    }

    // ------------------------------------------------------------------ helpers

    fun snapshotId(interval: String, bars: List<Bar>): String {
        val key = "$interval|${bars.size}|${bars.firstOrNull()?.time}|${bars.lastOrNull()?.time}|${bars.lastOrNull()?.close}"
        val h = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return "S-" + h.take(4).joinToString("") { String.format("%02x", it) }
    }

    /** Groups base bars into bigger candles aligned to the start of the list. The last group may be partial and is dropped. */
    fun group(bars: List<Bar>, size: Int): List<Bar> {
        if (size <= 1) return bars
        return bars.chunked(size).filter { it.size == size }.map { ch ->
            Bar(ch.first().time, ch.first().open, ch.maxOf { it.high }, ch.minOf { it.low }, ch.last().close)
        }
    }

    private fun f1(x: Double?) = if (x == null) "--" else String.format(Locale.US, "%.1f", x)
    private fun usd(x: Double?) = if (x == null) "--" else String.format(Locale.US, "$%.2f", x)

    private class Acc(val name: String, val kind: String, val note: String) {
        var samples = 0; var calls = 0; var right = 0; var wrong = 0; var flat = 0; var net = 0.0
        fun add(d: Int, y: Int, move: Double) {
            samples++
            if (d == 0) return
            calls++
            net += d * move - COST_USD
            when (y) { d -> right++; -d -> wrong++; else -> flat++ }
        }
        fun score(strictZ: Double?): ModelScore {
            val w = ResearchLab.wilson(right, right + wrong)
            val s = strictZ?.let { ResearchLab.wilsonZ(right, right + wrong, it) }
            return ModelScore(name, kind, note, samples, calls, right, wrong, flat,
                if (calls == 0) null else net / calls, w?.first, w?.second, s?.first)
        }
    }

    /** Small L2-regularised logistic regression, fitted with Newton steps. Features are standardised on the training set. */
    object Logit {
        class Model(val w: DoubleArray, val mean: DoubleArray, val sd: DoubleArray) {
            fun prob(x: DoubleArray): Double {
                var z = w[0]
                for (j in x.indices) z += w[j + 1] * (x[j] - mean[j]) / sd[j]
                return 1.0 / (1.0 + exp(-z.coerceIn(-30.0, 30.0)))
            }
        }

        fun fit(xs: List<DoubleArray>, ys: List<Boolean>, lambda: Double = 2.0, iters: Int = 12): Model? {
            if (xs.size < 50 || xs.size != ys.size) return null
            val p = xs[0].size
            val mean = DoubleArray(p) { j -> xs.sumOf { it[j] } / xs.size }
            val sd = DoubleArray(p) { j -> sqrt(xs.sumOf { (it[j] - mean[j]) * (it[j] - mean[j]) } / xs.size).let { if (it < 1e-9) 1.0 else it } }
            val z = xs.map { x -> DoubleArray(p + 1) { j -> if (j == 0) 1.0 else (x[j - 1] - mean[j - 1]) / sd[j - 1] } }
            val w = DoubleArray(p + 1)
            repeat(iters) {
                val g = DoubleArray(p + 1)
                val h = Array(p + 1) { DoubleArray(p + 1) }
                for (r in z.indices) {
                    var s = 0.0
                    for (j in 0..p) s += w[j] * z[r][j]
                    val pr = 1.0 / (1.0 + exp(-s.coerceIn(-30.0, 30.0)))
                    val e = pr - (if (ys[r]) 1.0 else 0.0)
                    val wt = pr * (1 - pr)
                    for (j in 0..p) {
                        g[j] += e * z[r][j]
                        for (k in 0..p) h[j][k] += wt * z[r][j] * z[r][k]
                    }
                }
                for (j in 1..p) { g[j] += lambda * w[j]; h[j][j] += lambda }
                h[0][0] += 1e-6
                val step = solve(h, g) ?: return null
                for (j in 0..p) w[j] -= step[j]
            }
            if (w.any { it.isNaN() || it.isInfinite() }) return null
            return Model(w, mean, sd)
        }

        private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
            val n = b.size
            val m = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) a[i][j] else b[i] } }
            for (c in 0 until n) {
                val piv = (c until n).maxByOrNull { abs(m[it][c]) } ?: return null
                if (abs(m[piv][c]) < 1e-12) return null
                val t = m[c]; m[c] = m[piv]; m[piv] = t
                for (r in 0 until n) if (r != c) {
                    val fr = m[r][c] / m[c][c]
                    for (k in c..n) m[r][k] -= fr * m[c][k]
                }
            }
            return DoubleArray(n) { m[it][n] / m[it][it] }
        }
    }
}
