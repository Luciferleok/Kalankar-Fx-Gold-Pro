package com.example.livegoldai.data

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * SHADOW LAB (V28.0)
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
 *    starts (purge of [HORIZON] candles), then tested on the next block, then the window moves on
 *  - baselines are scored on the very same test samples
 *  - a challenger is an EDGE CANDIDATE only if the low end of its 95% Wilson range beats the best baseline
 *    AND 50%, with at least [MIN_CALLS] decided calls, coverage >= 15% and a positive move after costs
 *  - SHADOW ONLY: nothing here changes the live BUY/SELL/WAIT
 */
object ShadowLab {

    const val LAB_VERSION = "SL1.0"
    const val LABEL_VERSION = "L1"
    const val HORIZON = 4               // closed candles ahead
    const val FLAT_ATR = 0.25           // |move| below this many ATRs = FLAT
    const val COST_USD = 0.40           // assumed spread + slippage per call, in USD per ounce
    const val MIN_CALLS = 100           // decided calls needed before a verdict
    const val MIN_COVERAGE = 0.15
    const val MIN_SAMPLES = 600
    const val WARMUP = 110              // bars needed before the first factor row (EMA50 + ATR median window)
    const val TRAIN_MAX = 1500          // rolling training window (samples)
    const val TEST_BLOCK = 250
    const val TARGET_COVERAGE = 0.30    // gate is set on TRAINING data so ~30% of samples get a call

    data class Bar(val time: String, val open: Double, val high: Double, val low: Double, val close: Double)

    enum class Regime { TREND, RANGE, HIGH_VOL, UNCLEAR }

    /** Five continuous factors, all in ATR units or bounded, computed from bars up to and including i. */
    data class Factors(
        val trend: Double,      // (EMA20 - EMA50) / ATR
        val momentum: Double,   // 10-candle return / (ATR * sqrt(10))
        val stretch: Double,    // (close - EMA20) / ATR
        val rangePos: Double,   // position inside the 20-candle high-low range, -1..+1
        val volRatio: Double,   // ATR / median ATR of the last 100 candles
        val efficiency: Double, // Kaufman efficiency ratio over 20 candles, 0..1
        val regime: Regime
    ) {
        fun directional() = doubleArrayOf(trend, momentum, stretch, rangePos, trend * efficiency)
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
        val wilsonLow: Double?,     // percent, null below 10 decided
        val wilsonHigh: Double?
    ) {
        val decided get() = right + wrong
        val accuracy: Double? get() = if (decided == 0) null else 100.0 * right / decided
        val coverage: Double get() = if (samples == 0) 0.0 else calls.toDouble() / samples
    }

    data class RegimeLine(val regime: String, val samples: Int, val calls: Int, val right: Int, val wrong: Int)

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
        val createdAtMs: Long
    )

    // ------------------------------------------------------------------ public entry

    fun run(interval: String, closedBars: List<Bar>, now: Long = System.currentTimeMillis()): Report {
        val bars = closedBars
        val sid = snapshotId(interval, bars)
        val first = bars.firstOrNull()?.time ?: "--"
        val last = bars.lastOrNull()?.time ?: "--"
        val f = factorRows(bars)
        val atr = atr14(bars)
        // label for sample i needs bar i + HORIZON
        val idx = (WARMUP until bars.size - HORIZON).filter { f[it] != null && atr[it] > 0 }
        if (idx.size < MIN_SAMPLES) {
            return Report(interval, sid, LABEL_VERSION, LAB_VERSION, bars.size, first, last, idx.size, 0, 0.0, 0.0,
                emptyList(), "--", "NOT ENOUGH HISTORY",
                "Needs at least $MIN_SAMPLES labelled samples, has ${idx.size}. Try a shorter timeframe (more history fits in one download).",
                emptyList(), null, now)
        }
        val label = IntArray(bars.size)
        val moveUsd = DoubleArray(bars.size)
        for (i in idx) {
            val m = bars[i + HORIZON].close - bars[i].close
            moveUsd[i] = m
            label[i] = when { m >= FLAT_ATR * atr[i] -> 1; m <= -FLAT_ATR * atr[i] -> -1; else -> 0 }
        }

        // ---- walk-forward
        val initialTrain = max(300, (idx.size * 0.35).toInt())
        val acc = LinkedHashMap<String, Acc>()
        fun accOf(name: String, kind: String, note: String) = acc.getOrPut(name) { Acc(name, kind, note) }
        val regimeAcc = LinkedHashMap<Regime, IntArray>()   // samples, calls, right, wrong (best challenger = FACTOR GATED)
        Regime.values().forEach { regimeAcc[it] = IntArray(4) }
        var folds = 0
        var testUp = 0; var testDown = 0; var testFlat = 0
        val rnd = java.util.Random(sid.hashCode().toLong())

        var start = initialTrain
        while (start < idx.size) {
            val end = min(idx.size, start + TEST_BLOCK)
            val testStartBar = idx[start]
            // purge: only samples whose label window closed before the first test bar
            val trainIdx = idx.subList(max(0, start - TRAIN_MAX), start).filter { it + HORIZON < testStartBar }
            val global = Logit.fit(trainIdx.filter { label[it] != 0 }.map { f[it]!!.directional() }, trainIdx.filter { label[it] != 0 }.map { label[it] == 1 })
            val gate = global?.let { g -> gateFor(trainIdx.map { g.prob(f[it]!!.directional()) }) }
            val perRegime = HashMap<Regime, Pair<Logit.Model, Double>>()
            for (r in listOf(Regime.TREND, Regime.RANGE)) {
                val tr = trainIdx.filter { f[it]!!.regime == r }
                val dec = tr.filter { label[it] != 0 }
                if (dec.size < 150) continue
                val m = Logit.fit(dec.map { f[it]!!.directional() }, dec.map { label[it] == 1 }) ?: continue
                perRegime[r] = m to gateFor(tr.map { m.prob(f[it]!!.directional()) })
            }
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
                val dGlobal = if (global != null && gate != null) {
                    val p = global.prob(fi.directional()); if (abs(p - 0.5) >= gate) (if (p > 0.5) 1 else -1) else 0
                } else 0
                accOf("FACTOR GATED", "CHALLENGER", "5 factors, logistic fit on past data only, calls only its strongest ~30%").add(dGlobal, y, moveUsd[i])
                val pr = perRegime[fi.regime]
                val dReg = if (pr != null) {
                    val p = pr.first.prob(fi.directional()); if (abs(p - 0.5) >= pr.second) (if (p > 0.5) 1 else -1) else 0
                } else 0
                accOf("REGIME FIRST", "CHALLENGER", "separate model for TREND and RANGE, no call in HIGH VOL / UNCLEAR").add(dReg, y, moveUsd[i])
                val ra = regimeAcc[fi.regime]!!
                ra[0]++
                if (dGlobal != 0) { ra[1]++; if (y == dGlobal) ra[2]++ else if (y == -dGlobal) ra[3]++ }
            }
            start = end
        }

        val models = acc.values.map { it.score() }
        val baselines = models.filter { it.kind == "BASELINE" && it.name != "COIN FLIP" && it.decided >= MIN_CALLS }
        val best = baselines.maxByOrNull { it.accuracy ?: 0.0 }
        val bestAcc = best?.accuracy ?: 50.0
        val challengers = models.filter { it.kind == "CHALLENGER" }
        val qualified = challengers.filter { it.decided >= MIN_CALLS && it.coverage >= MIN_COVERAGE }
        val top = qualified.maxByOrNull { it.wilsonLow ?: 0.0 }
        val (verdict, detail) = when {
            top == null -> "NO EDGE" to "No challenger reached $MIN_CALLS decided calls with ${(MIN_COVERAGE * 100).toInt()}% coverage yet."
            (top.wilsonLow ?: 0.0) > max(50.0, bestAcc) && (top.avgNetUsd ?: 0.0) > 0 ->
                "EDGE CANDIDATE" to "${top.name}: low end of its 95% range ${f1(top.wilsonLow)}% beats the best baseline ${best?.name ?: "50%"} (${f1(bestAcc)}%) and makes ${usd(top.avgNetUsd)} per call after an assumed ${usd(COST_USD)} cost. Candidate only: it must now hold up in live shadow before it may ever be used."
            else -> "NO EDGE" to "Best challenger ${top.name} ${f1(top.accuracy)}% (95% range ${f1(top.wilsonLow)}-${f1(top.wilsonHigh)}%) does not clearly beat the best baseline ${best?.name ?: "50%"} ${f1(bestAcc)}%" +
                (if ((top.avgNetUsd ?: 0.0) <= 0) " and loses ${usd(abs(top.avgNetUsd ?: 0.0))} per call after costs." else ".")
        }
        val regimes = regimeAcc.map { (r, a) -> RegimeLine(r.name.replace('_', ' '), a[0], a[1], a[2], a[3]) }.filter { it.samples > 0 }
        val decidedTest = testUp + testDown
        return Report(
            interval, sid, LABEL_VERSION, LAB_VERSION, bars.size, first, last, idx.size, folds,
            if (decidedTest == 0) 0.0 else testUp.toDouble() / decidedTest,
            if (testUp + testDown + testFlat == 0) 0.0 else testFlat.toDouble() / (testUp + testDown + testFlat),
            models, best?.name ?: "--", verdict, detail, regimes,
            currentRead(bars, f, idx, label), now
        )
    }

    // ------------------------------------------------------------------ current shadow read

    private fun currentRead(bars: List<Bar>, f: List<Factors?>, idx: List<Int>, label: IntArray): CurrentRead? {
        val lastI = bars.lastIndex
        val fl = f.getOrNull(lastI) ?: return null
        val train = idx.takeLast(TRAIN_MAX)
        val dec = train.filter { label[it] != 0 }
        val m = Logit.fit(dec.map { f[it]!!.directional() }, dec.map { label[it] == 1 })
        val txt = String.format(Locale.US, "trend %+.2f • momentum %+.2f • stretch %+.2f • range pos %+.2f • vol x%.2f • efficiency %.2f",
            fl.trend, fl.momentum, fl.stretch, fl.rangePos, fl.volRatio, fl.efficiency)
        if (m == null) return CurrentRead(bars[lastI].time, fl.regime.name.replace('_', ' '), txt, null, "NO CALL", "model could not be fitted")
        val gate = gateFor(train.map { m.prob(f[it]!!.directional()) })
        val p = m.prob(fl.directional())
        val call = if (abs(p - 0.5) >= gate) (if (p > 0.5) "UP" else "DOWN") else "NO CALL"
        val reason = if (call == "NO CALL") String.format(Locale.US, "P(up) %.0f%% is inside the no-call zone (needs %.0f%% or %.0f%%)", p * 100, (0.5 + gate) * 100, (0.5 - gate) * 100)
        else String.format(Locale.US, "P(up) %.0f%% is outside the no-call zone", p * 100)
        return CurrentRead(bars[lastI].time, fl.regime.name.replace('_', ' '), txt, p, call, reason)
    }

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

    /** Factor row per bar, null during warm-up. Uses only bars 0..i for row i. */
    fun factorRows(bars: List<Bar>): List<Factors?> {
        val n = bars.size
        if (n == 0) return emptyList()
        val close = DoubleArray(n) { bars[it].close }
        val atr = atr14(bars)
        val e20 = ema(close, 20)
        val e50 = ema(close, 50)
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
            val trend = clip((e20[i] - e50[i]) / a)
            val volRatio = if (med <= 0) 1.0 else a / med
            val regime = when {
                volRatio > 1.6 -> Regime.HIGH_VOL
                er > 0.35 && abs(trend) > 0.8 -> Regime.TREND
                er < 0.20 && abs(trend) < 1.0 -> Regime.RANGE
                else -> Regime.UNCLEAR
            }
            out.add(Factors(
                trend = trend,
                momentum = clip((close[i] - close[i - 10]) / (a * sqrt(10.0))),
                stretch = clip((close[i] - e20[i]) / a),
                rangePos = if (hi - lo <= 0) 0.0 else 2 * (close[i] - lo) / (hi - lo) - 1,
                volRatio = volRatio,
                efficiency = er,
                regime = regime
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

    /** 95% Wilson range in percent. */
    fun wilson(c: Int, n: Int): Pair<Double, Double>? = ResearchLab.wilson(c, n)

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
        fun score(): ModelScore {
            val w = ResearchLab.wilson(right, right + wrong)
            return ModelScore(name, kind, note, samples, calls, right, wrong, flat,
                if (calls == 0) null else net / calls, w?.first, w?.second)
        }
    }

    /** Small L2-regularised logistic regression, fitted with Newton steps. Features are standardised on the training set. */
    object Logit {
        class Model(private val w: DoubleArray, private val mean: DoubleArray, private val sd: DoubleArray) {
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
                    val f = m[r][c] / m[c][c]
                    for (k in c..n) m[r][k] -= f * m[c][k]
                }
            }
            return DoubleArray(n) { m[it][n] / m[it][it] }
        }
    }
}
