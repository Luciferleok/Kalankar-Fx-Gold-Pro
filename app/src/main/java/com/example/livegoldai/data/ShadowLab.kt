package com.example.livegoldai.data

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
 * SHADOW LAB (V28.0 → V28.4 research integrity)
 *
 * A walk-forward tournament on real XAU/USD candle history. The question it answers:
 * "Does a model show a reproducible, out-of-sample, dependence-aware, economically meaningful edge over
 *  appropriate baselines?"  Nothing here changes the live BUY/SELL/WAIT.
 *
 * Integrity rules implemented here (master prompt + V28.4):
 *  - closed candles only; one snapshot identity (full SHA-256) per run
 *  - label L1: close after H candles vs now, +-0.25 x ATR14 = UP / DOWN, else FLAT
 *  - prediction (UP / DOWN / WAIT + reason) and realised outcome (UP / DOWN / FLAT) are separate fields
 *  - FLAT is never hidden: non-flat direction accuracy R/(R+W), directional call success R/(R+W+F), flat call rate
 *  - outcomes of neighbouring candles overlap (H-candle windows), so evidence uses a moving-block bootstrap
 *    and a non-overlapping proof sample, never plain Wilson alone
 *  - baselines are compared globally AND on exactly the candles where the challenger called (matched, paired)
 *  - the multiple-testing family is the actual number of challengers in the run (Bonferroni)
 *  - negative controls (random feature, shuffled feature) must stay dead, and a causality check must pass,
 *    or the whole run is RUN FAILED
 *  - costs are research scenarios, not truth; prediction evidence and economics are reported separately
 */
object ShadowLab {

    const val LAB_VERSION = "SL1.4"
    const val LABEL_VERSION = "L1"
    const val FEATURE_SCHEMA = "F2"     // 5 directional factors + context (vol, efficiency, time of day, squeeze)
    const val RECIPE_VERSION = "R2"     // FACTOR GATED: logistic, L2=2, rolling 1500, gate at 30% train coverage, OOD wait
    const val HORIZON = 4               // closed candles ahead
    val SWEEP = listOf(2, 4, 8)         // horizons compared (exploratory, own correction family)
    const val FLAT_ATR = 0.25
    const val MIN_CALLS = 100           // decided calls (operational floor, not proof by itself)
    const val MIN_PROOF = 30            // non-overlapping decided calls
    const val MIN_COVERAGE = 0.15
    const val MIN_SAMPLES = 600
    const val WARMUP = 110
    const val TRAIN_MAX = 1500
    const val TEST_BLOCK = 250
    const val TARGET_COVERAGE = 0.30
    const val META_MIN = 200
    const val MAX_LAB_BARS = 15_000
    const val OOD_Z = 4.0               // a standardised factor beyond this = state never seen in training -> WAIT
    const val BOOT_ITERS = 2000

    /** Research cost scenarios in USD per ounce per call (assumptions, not broker quotes). */
    val COSTS = listOf("ZERO" to 0.0, "LOW" to 0.20, "REFERENCE" to 0.40, "STRESS" to 0.80)
    const val REFERENCE_COST = 0.40

    /** Pre-registered experiment: fixed in code BEFORE any run, so the winning rule cannot be chosen afterwards. */
    object Experiment {
        const val ID = "EXP-28.4-A"
        const val REGISTERED = "2026-10-20"
        const val HYPOTHESIS = "A gated 5-factor logistic model predicts the direction of the 4-candle move better than simple rules, on unseen XAU/USD candles."
        const val PRIMARY = "Non-flat direction accuracy: moving-block bootstrap lower bound (Bonferroni over the challengers) above 50%, AND paired bootstrap lower bound of (challenger - best matched baseline) above 0."
        const val SECONDARY = "Directional call success, flat call rate, coverage, balanced accuracy, net per call under 4 cost scenarios, calibration."
        const val TIMEFRAMES = "5m, 15m, 1h, 4h, 1d (any timeframe can be run)"
        const val GATE_RANGE = "Gate fixed at 30% training coverage; 10/20/30/50/100% shown as a curve only (not used to pick a winner)."
        const val CORRECTION = "Bonferroni over the number of challengers in this run; horizon sweep has its own family of 3."
        const val ATTEMPTS = "Max 3 pre-registered one-change attempts per timeframe; after 3 failures: architecture review."
    }

    data class Bar(val time: String, val open: Double, val high: Double, val low: Double, val close: Double)

    enum class Regime { TREND, RANGE, HIGH_VOL, UNCLEAR }

    data class Factors(
        val trend: Double,
        val momentum: Double,
        val stretch: Double,
        val rangePos: Double,
        val volRatio: Double,
        val efficiency: Double,
        val regime: Regime,
        val sessionSin: Double = 0.0,
        val sessionCos: Double = 0.0,
        val squeeze: Double = 0.0
    ) {
        fun directional() = doubleArrayOf(trend, momentum, stretch, rangePos, trend * efficiency)
        fun extended() = doubleArrayOf(trend, momentum, stretch, rangePos, trend * efficiency, sessionSin, sessionCos, squeeze, squeeze * momentum)
        fun context(conf: Double) = doubleArrayOf(conf, volRatio, efficiency,
            if (regime == Regime.TREND) 1.0 else 0.0, if (regime == Regime.RANGE) 1.0 else 0.0, squeeze, sessionSin, sessionCos)
    }

    data class ModelScore(
        val name: String,
        val kind: String,               // BASELINE / CHALLENGER / CONTROL
        val note: String,
        val samples: Int,
        val calls: Int,
        val right: Int,
        val wrong: Int,
        val flat: Int,
        val bootLow: Double?,           // block bootstrap of non-flat direction accuracy (percent); corrected for challengers/controls
        val bootHigh: Double?,
        val proofN: Int,                // non-overlapping decided calls
        val proofAcc: Double?,
        val balancedAcc: Double?,
        val upCallShare: Double?,
        val netByCost: List<Double?>,   // per COSTS scenario, USD per call
        val matchedBaseline: String = "--",
        val matchedBaselineAcc: Double? = null,
        val pairedDiff: Double? = null, // challenger minus best matched baseline, percentage points
        val pairedDiffLow: Double? = null
    ) {
        val decided get() = right + wrong
        /** Non-flat direction accuracy R/(R+W): conditional on the market really moving. */
        val nonFlatAcc: Double? get() = if (decided == 0) null else 100.0 * right / decided
        val accuracy: Double? get() = nonFlatAcc
        /** Directional call success R/(R+W+F). */
        val callSuccess: Double? get() = if (calls == 0) null else 100.0 * right / calls
        val flatRate: Double? get() = if (calls == 0) null else 100.0 * flat / calls
        val coverage: Double get() = if (samples == 0) 0.0 else calls.toDouble() / samples
        val netRef: Double? get() = netByCost.getOrNull(2)
    }

    data class Check(val name: String, val pass: Boolean, val detail: String)
    data class RegimeLine(val regime: String, val samples: Int, val calls: Int, val right: Int, val wrong: Int, val flat: Int)
    data class HorizonLine(val horizon: Int, val score: ModelScore, val bestBaseline: String, val bestBaselineAcc: Double?)
    data class CalibLine(val bin: String, val lo: Double, val hi: Double, val n: Int, val predicted: Double, val actual: Double)
    data class CoveragePoint(val target: Double, val coverage: Double, val nonFlatAcc: Double?, val callSuccess: Double?, val flatRate: Double?, val errorRate: Double?)
    /** rows: predicted UP, DOWN, WAIT; cols: actual UP, DOWN, FLAT */
    data class Confusion(val m: List<List<Int>>) {
        val waitTotal get() = m[2].sum()
        val total get() = m.sumOf { it.sum() }
    }
    data class Depth(
        val bars: Int, val spanDays: Double, val first: String, val last: String,
        val sessions: Map<String, Int>, val regimes: Map<String, Int>, val highVolShare: Double, val gaps: Int
    )

    data class LiveModel(
        val interval: String,
        val weights: List<Double>,
        val mean: List<Double>,
        val sd: List<Double>,
        val gate: Double,
        val horizon: Int,
        val snapshotId: String,
        val verdict: String,
        val trainedAt: Long,
        val recipe: String = RECIPE_VERSION,
        val featureSchema: String = FEATURE_SCHEMA,
        val target: String = LABEL_VERSION,
        val trainingStart: String = "",
        val trainingEnd: String = "",
        val calib: List<CalibLine> = emptyList()   // LAB calibration, used to detect live calibration drift
    ) {
        val modelHash: String get() = HistoryStore.sha256(weights.joinToString(",") + "|" + mean.joinToString(",") + "|" + sd.joinToString(",") + "|" + gate)
        val instanceId: String get() = "$recipe-${modelHash.take(10)}"
        val id: String get() = instanceId
        private fun model() = Logit.Model(weights.toDoubleArray(), mean.toDoubleArray(), sd.toDoubleArray())
        fun prob(f: Factors): Double = model().prob(f.directional())
        /** (decision +1/-1/0, model score, wait reason or "") */
        fun decide(f: Factors): Triple<Int, Double, String> {
            val x = f.directional()
            val ood = x.indices.any { j -> abs((x[j] - mean[j]) / sd[j]) > OOD_Z }
            val p = prob(f)
            if (ood) return Triple(0, p, "OOD_STATE")
            if (abs(p - 0.5) < gate) return Triple(0, p, "EDGE_TOO_SMALL")
            return Triple(if (p > 0.5) 1 else -1, p, "")
        }
    }

    data class CurrentRead(
        val barTime: String,
        val regime: String,
        val factorsText: String,
        val score: Double?,
        val decision: String,       // UP / DOWN / WAIT
        val waitReason: String,
        val reason: String
    )

    data class Report(
        val interval: String,
        val snapshotId: String,         // full SHA-256 of the canonical snapshot content
        val dataHash: String,
        val labelVersion: String,
        val labVersion: String,
        val bars: Int,
        val firstBar: String,
        val lastClosedBar: String,
        val samples: Int,
        val folds: Int,
        val outcomeShares: Triple<Double, Double, Double>,  // UP, DOWN, FLAT share of test outcomes
        val models: List<ModelScore>,
        val bestBaseline: String,
        val bestBaselineAcc: Double,
        val verdict: String,            // EDGE CANDIDATE / NO EDGE / RUN FAILED / NOT ENOUGH HISTORY
        val verdictDetail: String,
        val topChallenger: String,
        val checks: List<Check>,
        val familySize: Int,
        val regimes: List<RegimeLine>,
        val confusion: Confusion?,
        val coverageCurve: List<CoveragePoint>,
        val horizons: List<HorizonLine>,
        val calibration: List<CalibLine>,
        val depth: Depth?,
        val economics: String,          // ROBUST / FRAGILE ECONOMIC EDGE / NEGATIVE AFTER COSTS
        val current: CurrentRead?,
        val live: LiveModel?,
        val createdAtMs: Long,
        val historyRevisions: Int = 0   // provider changes to already-stored candles, logged in the history manifest
    ) {
        val shortId: String get() = "S-" + snapshotId.take(8)
    }

    // ------------------------------------------------------------------ statistics

    object Evidence {
        /** Inverse standard normal (Acklam). */
        fun invNorm(p: Double): Double {
            val a = doubleArrayOf(-39.69683028665376, 220.9460984245205, -275.9285104469687, 138.3577518672690, -30.66479806614716, 2.506628277459239)
            val b = doubleArrayOf(-54.47609879822406, 161.5858368580409, -155.6989798598866, 66.80131188771972, -13.28068155288572)
            val c = doubleArrayOf(-0.007784894002430293, -0.3223964580411365, -2.400758277161838, -2.549732539343734, 4.374664141464968, 2.938163982698783)
            val d = doubleArrayOf(0.007784695709041462, 0.3224671290700398, 2.445134137142996, 3.754408661907416)
            val pl = 0.02425
            return when {
                p < pl -> { val q = sqrt(-2 * ln(p)); (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1) }
                p > 1 - pl -> { val q = sqrt(-2 * ln(1 - p)); -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1) }
                else -> { val q = p - 0.5; val r = q * q; (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1) }
            }
        }

        /** Two-sided 95% z, Bonferroni-corrected for a family of [family] tests. */
        fun zFor(family: Int): Double = invNorm(1 - 0.025 / max(1, family))

        /**
         * Moving-block bootstrap of the mean of a time-ordered series (values 0/1 or differences).
         * Returns (lower, upper) at the given two-sided level, corrected for [family]. null below 10 values.
         */
        fun blockBootstrap(values: DoubleArray, blockLen: Int, family: Int, seed: Long, iters: Int = BOOT_ITERS): Pair<Double, Double>? {
            val n = values.size
            if (n < 10) return null
            val l = blockLen.coerceIn(1, max(1, n / 2))
            val starts = n - l + 1
            val rnd = java.util.Random(seed)
            val means = DoubleArray(iters)
            for (it in 0 until iters) {
                var sum = 0.0; var cnt = 0
                while (cnt < n) {
                    val s = rnd.nextInt(starts)
                    var j = 0
                    while (j < l && cnt < n) { sum += values[s + j]; cnt++; j++ }
                }
                means[it] = sum / n
            }
            means.sort()
            val a = 0.025 / max(1, family)
            val lo = means[((iters * a).toInt()).coerceIn(0, iters - 1)]
            val hi = means[((iters * (1 - a)).toInt()).coerceIn(0, iters - 1)]
            return lo to hi
        }

        /** Greedy non-overlapping sample: keep a call only if its outcome window starts after the previous kept one ended. */
        fun nonOverlapping(barPositions: IntArray, horizon: Int): IntArray {
            val keep = ArrayList<Int>()
            var nextFree = Int.MIN_VALUE
            for (k in barPositions.indices) {
                if (barPositions[k] >= nextFree) { keep.add(k); nextFree = barPositions[k] + horizon }
            }
            return keep.toIntArray()
        }
    }

    // ------------------------------------------------------------------ run

    fun run(interval: String, allBars: List<Bar>, now: Long = System.currentTimeMillis(), dataHash: String? = null, gaps: Int = 0): Report {
        val bars = if (allBars.size > MAX_LAB_BARS) allBars.takeLast(MAX_LAB_BARS) else allBars
        val dh = dataHash ?: HistoryStore.dataHash(bars)
        val sid = HistoryStore.sha256(listOf(HistoryStore.PROVIDER, HistoryStore.INSTRUMENT, interval, bars.size,
            bars.firstOrNull()?.time, bars.lastOrNull()?.time, dh, FEATURE_SCHEMA, LABEL_VERSION).joinToString("|"))
        val first = bars.firstOrNull()?.time ?: "--"
        val last = bars.lastOrNull()?.time ?: "--"
        val f = factorRows(bars)
        val atr = atr14(bars)
        val depth = depthAudit(bars, f, gaps)
        val idx = (WARMUP until bars.size - HORIZON).filter { f[it] != null && atr[it] > 0 }
        fun empty(verdict: String, detail: String, checks: List<Check> = emptyList()) = Report(interval, sid, dh, LABEL_VERSION, LAB_VERSION,
            bars.size, first, last, idx.size, 0, Triple(0.0, 0.0, 0.0), emptyList(), "--", 50.0, verdict, detail, "--", checks, 0,
            emptyList(), null, emptyList(), emptyList(), emptyList(), depth, "--", null, null, now)
        if (idx.size < MIN_SAMPLES) {
            return empty("NOT ENOUGH HISTORY", "Needs at least $MIN_SAMPLES labelled samples, has ${idx.size}. Use DEEPEN HISTORY or a shorter timeframe.")
        }
        val causal = causalityCheck(bars, f)
        val label = labels(bars, atr, idx, HORIZON)
        val move = DoubleArray(bars.size).also { m -> for (i in idx) m[i] = bars[i + HORIZON].close - bars[i].close }

        // ---- negative-control features (deterministic)
        val noise = java.util.Random(sid.hashCode().toLong() xor 0x5eedL)
        val randomFeat = Array(bars.size) { DoubleArray(5) { noise.nextGaussian() } }
        val perm = idx.shuffled(java.util.Random(sid.hashCode().toLong() xor 0x51ffL))
        val shuffledOf = HashMap<Int, Int>(idx.size * 2).also { mp -> idx.forEachIndexed { k, i -> mp[i] = perm[k] } }

        // ---- walk-forward: one decision vector per model over the test samples
        val initialTrain = max(300, (idx.size * 0.35).toInt())
        val testPos = idx.subList(initialTrain, idx.size)
        val nT = testPos.size
        val y = IntArray(nT) { label[testPos[it]] }
        val mv = DoubleArray(nT) { move[testPos[it]] }
        val dec = LinkedHashMap<String, IntArray>()
        val kind = LinkedHashMap<String, String>()
        val note = LinkedHashMap<String, String>()
        fun track(name: String, k: String, n: String): IntArray = dec.getOrPut(name) { kind[name] = k; note[name] = n; IntArray(nT) }
        val bMom = track("MOMENTUM", "BASELINE", "same direction as the last $HORIZON candles")
        val bRev = track("REVERSION", "BASELINE", "opposite of the last $HORIZON candles")
        val bEma = track("EMA TREND", "BASELINE", "EMA20 above EMA50 = UP")
        val bUp = track("ALWAYS UP", "BASELINE", "always UP")
        val bDown = track("ALWAYS DOWN", "BASELINE", "always DOWN")
        val bCoin = track("COIN FLIP", "CONTROL", "random direction")
        val cG = track("FACTOR GATED", "CHALLENGER", "5 factors, fitted on past data only, calls its strongest ~30%, WAIT on unseen states")
        val cR = track("REGIME FIRST", "CHALLENGER", "separate model for TREND and RANGE, WAIT in HIGH VOL / UNCLEAR")
        val cP = track("FACTOR+", "CHALLENGER", "adds time of day and volatility squeeze")
        val cM = track("META FILTER", "CHALLENGER", "FACTOR GATED, but skips conditions where its earlier calls failed")
        val nRand = track("RANDOM FEATURE", "CONTROL", "same model on pure noise: must show no edge")
        val nShuf = track("SHUFFLED FEATURE", "CONTROL", "real factors from the wrong candles: must show no edge")
        val waitReason = Array(nT) { "" }
        val pOut = DoubleArray(nT) { Double.NaN }
        val curveTargets = listOf(0.10, 0.20, 0.30, 0.50, 1.00)
        val curve = Array(curveTargets.size) { IntArray(nT) }
        val metaX = ArrayList<DoubleArray>(); val metaY = ArrayList<Boolean>()
        val rnd = java.util.Random(sid.hashCode().toLong())
        var folds = 0

        var start = initialTrain
        while (start < idx.size) {
            val end = min(idx.size, start + TEST_BLOCK)
            val testStartBar = idx[start]
            val trainIdx = idx.subList(max(0, start - TRAIN_MAX), start).filter { it + HORIZON < testStartBar }
            val dIdx = trainIdx.filter { label[it] != 0 }
            val ys = dIdx.map { label[it] == 1 }
            val global = Logit.fit(dIdx.map { f[it]!!.directional() }, ys)
            val trainP = global?.let { g -> trainIdx.map { g.prob(f[it]!!.directional()) } }
            val gate = trainP?.let { gateFor(it, TARGET_COVERAGE) }
            val curveGates = curveTargets.map { c -> trainP?.let { gateFor(it, c) } }
            val ood = global?.let { g -> g.mean to g.sd }
            val plus = Logit.fit(dIdx.map { f[it]!!.extended() }, ys)
            val plusGate = plus?.let { g -> gateFor(trainIdx.map { g.prob(f[it]!!.extended()) }, TARGET_COVERAGE) }
            val rM = Logit.fit(dIdx.map { randomFeat[it] }, ys)
            val rGate = rM?.let { g -> gateFor(trainIdx.map { g.prob(randomFeat[it]) }, TARGET_COVERAGE) }
            val sM = Logit.fit(dIdx.map { f[shuffledOf[it]!!]!!.directional() }, ys)
            val sGate = sM?.let { g -> gateFor(trainIdx.map { g.prob(f[shuffledOf[it]!!]!!.directional()) }, TARGET_COVERAGE) }
            val perRegime = HashMap<Regime, Pair<Logit.Model, Double>>()
            for (r in listOf(Regime.TREND, Regime.RANGE)) {
                val tr = trainIdx.filter { f[it]!!.regime == r }
                val d = tr.filter { label[it] != 0 }
                if (d.size < 150) continue
                val m = Logit.fit(d.map { f[it]!!.directional() }, d.map { label[it] == 1 }) ?: continue
                perRegime[r] = m to gateFor(tr.map { m.prob(f[it]!!.directional()) }, TARGET_COVERAGE)
            }
            val meta = if (metaY.size >= META_MIN) Logit.fit(metaX, metaY) else null
            val foldX = ArrayList<DoubleArray>(); val foldY = ArrayList<Boolean>()
            folds++
            for (k in start until end) {
                val t = k - initialTrain
                val i = idx[k]
                val fi = f[i]!!
                val c = bars[i].close
                bMom[t] = sign(c - bars[i - HORIZON].close)
                bRev[t] = -bMom[t]
                bEma[t] = sign(fi.trend)
                bUp[t] = 1; bDown[t] = -1
                bCoin[t] = if (rnd.nextBoolean()) 1 else -1
                // FACTOR GATED with explicit WAIT reasons
                if (global == null || gate == null || ood == null) { waitReason[t] = "MODEL_UNAVAILABLE" } else {
                    val x = fi.directional()
                    val p = global.prob(x)
                    pOut[t] = p
                    val isOod = x.indices.any { j -> abs((x[j] - ood.first[j]) / ood.second[j]) > OOD_Z }
                    when {
                        isOod -> waitReason[t] = "OOD_STATE"
                        abs(p - 0.5) < gate -> waitReason[t] = "EDGE_TOO_SMALL"
                        else -> cG[t] = if (p > 0.5) 1 else -1
                    }
                    curveGates.forEachIndexed { ci, g -> if (g != null && !isOod && abs(p - 0.5) >= g) curve[ci][t] = if (p > 0.5) 1 else -1 }
                    val ctx = fi.context(abs(p - 0.5))
                    if (cG[t] != 0 && meta != null && meta.prob(ctx) >= 0.5) cM[t] = cG[t]
                    if (cG[t] != 0 && label[i] != 0) { foldX.add(ctx); foldY.add(label[i] == cG[t]) }
                }
                perRegime[fi.regime]?.let { (m, g) -> val p = m.prob(fi.directional()); if (abs(p - 0.5) >= g) cR[t] = if (p > 0.5) 1 else -1 }
                if (plus != null && plusGate != null) { val p = plus.prob(fi.extended()); if (abs(p - 0.5) >= plusGate) cP[t] = if (p > 0.5) 1 else -1 }
                if (rM != null && rGate != null) { val p = rM.prob(randomFeat[i]); if (abs(p - 0.5) >= rGate) nRand[t] = if (p > 0.5) 1 else -1 }
                if (sM != null && sGate != null) { val p = sM.prob(f[shuffledOf[i]!!]!!.directional()); if (abs(p - 0.5) >= sGate) nShuf[t] = if (p > 0.5) 1 else -1 }
            }
            metaX.addAll(foldX); metaY.addAll(foldY)
            start = end
        }

        // ---- scoring
        val testBar = IntArray(nT) { testPos[it] }
        val challengers = dec.keys.filter { kind[it] == "CHALLENGER" }
        val family = challengers.size
        val baselineNames = dec.keys.filter { kind[it] == "BASELINE" }
        val seed = sid.hashCode().toLong()
        fun score(name: String): ModelScore {
            val k = kind[name]!!
            val fam = if (k == "BASELINE") 1 else family
            val base = scoreOf(name, k, note[name]!!, dec[name]!!, y, mv, testBar, HORIZON, fam, seed)
            if (k == "BASELINE") return base
            // matched + paired: baselines on exactly the candles where this model called and the market moved
            val sel = (0 until nT).filter { dec[name]!![it] != 0 && y[it] != 0 }
            if (sel.size < 10) return base
            val (bn, bAcc) = baselineNames.map { b -> b to 100.0 * sel.count { dec[b]!![it] == y[it] } / sel.size }.maxByOrNull { it.second }!!
            val diff = DoubleArray(sel.size) { j -> val t = sel[j]; (if (dec[name]!![t] == y[t]) 1.0 else 0.0) - (if (dec[bn]!![t] == y[t]) 1.0 else 0.0) }
            val ci = Evidence.blockBootstrap(diff, HORIZON * 2, fam, seed + 7)
            return base.copy(matchedBaseline = bn, matchedBaselineAcc = bAcc, pairedDiff = diff.average() * 100, pairedDiffLow = ci?.first?.times(100))
        }
        val models = dec.keys.map { score(it) }
        val globalBest = models.filter { it.kind == "BASELINE" && it.decided >= MIN_CALLS }.maxByOrNull { it.nonFlatAcc ?: 0.0 }
        val bestName = globalBest?.name ?: "50%"
        val bestAcc = globalBest?.nonFlatAcc ?: 50.0

        fun checksFor(m: ModelScore): List<Check> = listOf(
            Check("Enough evidence", m.decided >= MIN_CALLS && m.proofN >= MIN_PROOF, "${m.decided} decided calls (need $MIN_CALLS), ${m.proofN} non-overlapping (need $MIN_PROOF)"),
            Check("Coverage", m.coverage >= MIN_COVERAGE, String.format(Locale.US, "%.0f%% of candles (need %.0f%%)", m.coverage * 100, MIN_COVERAGE * 100)),
            Check("Direction above 50% (block bootstrap, corrected)", (m.bootLow ?: 0.0) > 50.0, "lower bound ${f1(m.bootLow)}%"),
            Check("Beats matched baselines (paired)", (m.pairedDiffLow ?: -1.0) > 0.0, "vs ${m.matchedBaseline} on the same candles: ${f1s(m.pairedDiff)} pts, lower bound ${f1s(m.pairedDiffLow)} pts"),
            Check("Beats best global baseline", (m.nonFlatAcc ?: 0.0) > bestAcc, "${f1(m.nonFlatAcc)}% vs $bestName ${f1(bestAcc)}%"),
            Check("Positive at reference cost", (m.netRef ?: -1.0) > 0, "net ${usd(m.netRef)} per call at ${usd(REFERENCE_COST)}")
        )
        val controls = models.filter { it.kind == "CONTROL" && it.name != "COIN FLIP" }
        val controlPassed = controls.filter { c -> (c.bootLow ?: 0.0) > 50.0 && (c.pairedDiffLow ?: -1.0) > 0.0 && c.decided >= MIN_CALLS }
        val challengerScores = models.filter { it.kind == "CHALLENGER" }
        val eligible = challengerScores.filter { it.decided >= MIN_CALLS && it.coverage >= MIN_COVERAGE }
        val top = (eligible.ifEmpty { challengerScores }).maxByOrNull { it.pairedDiffLow ?: it.bootLow ?: -99.0 }
        val topChecks = top?.let { checksFor(it) } ?: emptyList()
        val integrity = listOf(
            Check("Negative controls stay dead", controlPassed.isEmpty(), if (controlPassed.isEmpty()) "random and shuffled features show no edge" else "PASSED by: " + controlPassed.joinToString { it.name }),
            Check("No look-ahead (causality check)", causal.first, causal.second)
        )
        val checks = topChecks + integrity
        val econ = top?.let { m ->
            val n0 = m.netByCost.getOrNull(0) ?: 0.0; val nr = m.netByCost.getOrNull(2) ?: 0.0; val ns = m.netByCost.getOrNull(3) ?: 0.0
            when { nr > 0 && ns > 0 -> "ROBUST TO STRESS COST"; nr > 0 -> "FRAGILE ECONOMIC EDGE"; n0 > 0 -> "FRAGILE ECONOMIC EDGE"; else -> "NEGATIVE AFTER COSTS" }
        } ?: "--"
        val (verdict, detail) = when {
            integrity.any { !it.pass } -> "RUN FAILED" to "An integrity check failed (${integrity.filter { !it.pass }.joinToString { it.name }}). Results of this run must not be used."
            top != null && topChecks.all { it.pass } -> "EDGE CANDIDATE" to "${top.name} passed every pre-registered check. Candidate only: it must now hold up in live shadow before it may ever be used."
            top == null -> "NO EDGE" to "No challenger produced calls."
            else -> "NO EDGE" to "Best challenger ${top.name} failed: " + topChecks.filter { !it.pass }.joinToString("; ") { it.name.lowercase(Locale.US) } + "."
        }

        // ---- regimes, confusion, WAIT quality, curve, calibration
        val regimeAcc = LinkedHashMap<Regime, IntArray>()
        Regime.values().forEach { regimeAcc[it] = IntArray(5) }
        for (t in 0 until nT) {
            val a = regimeAcc[f[testBar[t]]!!.regime]!!
            a[0]++
            if (cG[t] != 0) { a[1]++; when (y[t]) { cG[t] -> a[2]++; -cG[t] -> a[3]++; else -> a[4]++ } }
        }
        val regimes = regimeAcc.map { (r, a) -> RegimeLine(r.name.replace('_', ' '), a[0], a[1], a[2], a[3], a[4]) }.filter { it.samples > 0 }
        val conf = List(3) { MutableList(3) { 0 } }
        for (t in 0 until nT) {
            val row = when (cG[t]) { 1 -> 0; -1 -> 1; else -> 2 }
            val col = when (y[t]) { 1 -> 0; -1 -> 1; else -> 2 }
            conf[row][col]++
        }
        val coverageCurve = curveTargets.mapIndexed { ci, tg ->
            var r = 0; var w = 0; var fl = 0; var calls = 0
            for (t in 0 until nT) { val d = curve[ci][t]; if (d == 0) continue; calls++; when (y[t]) { d -> r++; -d -> w++; else -> fl++ } }
            CoveragePoint(tg, if (nT == 0) 0.0 else calls.toDouble() / nT,
                if (r + w == 0) null else 100.0 * r / (r + w), if (calls == 0) null else 100.0 * r / calls,
                if (calls == 0) null else 100.0 * fl / calls, if (calls == 0) null else 100.0 * w / calls)
        }
        val calibPairs = (0 until nT).filter { !pOut[it].isNaN() && y[it] != 0 }.map { pOut[it] to (y[it] == 1) }
        val calibration = calibration(calibPairs)
        val horizons = SWEEP.map { h ->
            if (h == HORIZON) HorizonLine(h, models.first { it.name == "FACTOR GATED" }.let { m ->
                val ci = Evidence.blockBootstrap(series(cG, y), HORIZON * 2, SWEEP.size, seed + 3)
                m.copy(bootLow = ci?.first?.times(100), bootHigh = ci?.second?.times(100)) }, bestName, bestAcc)
            else sweepOne(bars, f, atr, h, seed)
        }
        val ups = y.count { it == 1 }; val downs = y.count { it == -1 }; val flats = nT - ups - downs
        return Report(
            interval, sid, dh, LABEL_VERSION, LAB_VERSION, bars.size, first, last, idx.size, folds,
            Triple(ups.toDouble() / max(1, nT), downs.toDouble() / max(1, nT), flats.toDouble() / max(1, nT)),
            models, bestName, bestAcc, verdict, detail, top?.name ?: "--", checks, family, regimes,
            Confusion(conf.map { it.toList() }), coverageCurve, horizons, calibration, depth, econ,
            currentRead(bars, f, idx, label), liveModel(interval, sid, verdict, bars, f, idx, label, now, calibration), now
        )
    }

    /** 0/1 series of right (1) vs wrong (0) over decided calls, in time order. */
    private fun series(d: IntArray, y: IntArray): DoubleArray {
        val out = ArrayList<Double>()
        for (t in d.indices) if (d[t] != 0 && y[t] != 0) out.add(if (d[t] == y[t]) 1.0 else 0.0)
        return out.toDoubleArray()
    }

    private fun scoreOf(name: String, kind: String, note: String, d: IntArray, y: IntArray, mv: DoubleArray, bar: IntArray, h: Int, family: Int, seed: Long): ModelScore {
        var calls = 0; var r = 0; var w = 0; var fl = 0
        val net = DoubleArray(COSTS.size)
        var upCalls = 0; var upR = 0; var upN = 0; var dnR = 0; var dnN = 0
        val callBars = ArrayList<Int>(); val callRight = ArrayList<Int>()   // decided calls, for the proof sample
        for (t in d.indices) {
            if (d[t] == 0) continue
            calls++
            if (d[t] > 0) upCalls++
            COSTS.forEachIndexed { ci, (_, c) -> net[ci] += d[t] * mv[t] - c }
            when (y[t]) {
                d[t] -> { r++; callBars.add(bar[t]); callRight.add(1) }
                -d[t] -> { w++; callBars.add(bar[t]); callRight.add(0) }
                else -> fl++
            }
            if (y[t] == 1) { upN++; if (d[t] == 1) upR++ }
            if (y[t] == -1) { dnN++; if (d[t] == -1) dnR++ }
        }
        val ci = Evidence.blockBootstrap(series(d, y), h * 2, family, seed + name.hashCode())
        val proof = Evidence.nonOverlapping(callBars.toIntArray(), h)
        val proofRight = proof.count { callRight[it] == 1 }
        val bal = if (upN > 0 && dnN > 0) 50.0 * (upR.toDouble() / upN + dnR.toDouble() / dnN) else null
        return ModelScore(name, kind, note, d.size, calls, r, w, fl, ci?.first?.times(100), ci?.second?.times(100),
            proof.size, if (proof.isEmpty()) null else 100.0 * proofRight / proof.size, bal,
            if (calls == 0) null else 100.0 * upCalls / calls,
            net.map { if (calls == 0) null else it / calls })
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

    private fun sweepOne(bars: List<Bar>, f: List<Factors?>, atr: DoubleArray, h: Int, seed: Long): HorizonLine {
        val idx = (WARMUP until bars.size - h).filter { f[it] != null && atr[it] > 0 }
        val label = labels(bars, atr, idx, h)
        val init = max(300, (idx.size * 0.35).toInt())
        if (idx.size <= init) return HorizonLine(h, scoreOf("FACTOR GATED", "CHALLENGER", "", IntArray(0), IntArray(0), DoubleArray(0), IntArray(0), h, SWEEP.size, seed), "--", null)
        val testPos = idx.subList(init, idx.size)
        val nT = testPos.size
        val y = IntArray(nT) { label[testPos[it]] }
        val mv = DoubleArray(nT) { bars[testPos[it] + h].close - bars[testPos[it]].close }
        val d = IntArray(nT)
        val base = mapOf("MOMENTUM" to IntArray(nT), "REVERSION" to IntArray(nT), "EMA TREND" to IntArray(nT), "ALWAYS UP" to IntArray(nT), "ALWAYS DOWN" to IntArray(nT))
        var start = init
        while (start < idx.size) {
            val end = min(idx.size, start + TEST_BLOCK)
            val trainIdx = idx.subList(max(0, start - TRAIN_MAX), start).filter { it + h < idx[start] }
            val dIdx = trainIdx.filter { label[it] != 0 }
            val g = Logit.fit(dIdx.map { f[it]!!.directional() }, dIdx.map { label[it] == 1 })
            val gate = g?.let { m -> gateFor(trainIdx.map { m.prob(f[it]!!.directional()) }, TARGET_COVERAGE) }
            for (k in start until end) {
                val t = k - init; val i = idx[k]; val fi = f[i]!!
                if (g != null && gate != null) { val p = g.prob(fi.directional()); if (abs(p - 0.5) >= gate) d[t] = if (p > 0.5) 1 else -1 }
                val s = sign(bars[i].close - bars[i - h].close)
                base["MOMENTUM"]!![t] = s; base["REVERSION"]!![t] = -s; base["EMA TREND"]!![t] = sign(fi.trend)
                base["ALWAYS UP"]!![t] = 1; base["ALWAYS DOWN"]!![t] = -1
            }
            start = end
        }
        val bar = IntArray(nT) { testPos[it] }
        val best = base.map { (n, v) -> scoreOf(n, "BASELINE", "", v, y, mv, bar, h, 1, seed) }.filter { it.decided >= MIN_CALLS }.maxByOrNull { it.nonFlatAcc ?: 0.0 }
        return HorizonLine(h, scoreOf("FACTOR GATED", "CHALLENGER", "", d, y, mv, bar, h, SWEEP.size, seed), best?.name ?: "--", best?.nonFlatAcc)
    }

    private fun calibration(c: List<Pair<Double, Boolean>>): List<CalibLine> {
        val bins = listOf(0.50 to 0.55, 0.55 to 0.60, 0.60 to 0.65, 0.65 to 1.01)
        return bins.mapNotNull { (lo, hi) ->
            val inBin = c.filter { val conf = max(it.first, 1 - it.first); conf >= lo && conf < hi }
            if (inBin.isEmpty()) null
            else CalibLine(
                String.format(Locale.US, "%.0f-%.0f", lo * 100, min(hi, 1.0) * 100), lo, hi,
                inBin.size,
                100.0 * inBin.map { max(it.first, 1 - it.first) }.average(),
                100.0 * inBin.count { (it.first > 0.5) == it.second } / inBin.size
            )
        }
    }

    private fun liveModel(interval: String, sid: String, verdict: String, bars: List<Bar>, f: List<Factors?>, idx: List<Int>, label: IntArray, now: Long, calib: List<CalibLine>): LiveModel? {
        val train = idx.takeLast(TRAIN_MAX)
        val dec = train.filter { label[it] != 0 }
        val m = Logit.fit(dec.map { f[it]!!.directional() }, dec.map { label[it] == 1 }) ?: return null
        val gate = gateFor(train.map { m.prob(f[it]!!.directional()) }, TARGET_COVERAGE)
        return LiveModel(interval, m.w.toList(), m.mean.toList(), m.sd.toList(), gate, HORIZON, sid, verdict, now,
            trainingStart = bars[train.first()].time, trainingEnd = bars[train.last()].time, calib = calib)
    }

    /** Recomputes factor rows on truncated copies of the history: a row must not change when later candles are removed. */
    private fun causalityCheck(bars: List<Bar>, full: List<Factors?>): Pair<Boolean, String> {
        val n = bars.size
        if (n < WARMUP + 50) return true to "too little data to check"
        val cuts = listOf(n / 3, n / 2, (n * 3) / 4, n - 20).filter { it in WARMUP until n }
        for (k in cuts) {
            val part = factorRows(bars.subList(0, k + 1))
            val a = part[k]; val b = full[k]
            if ((a == null) != (b == null)) return false to "factor row $k changed when later candles were removed"
            if (a != null && b != null) {
                val x = a.extended(); val z = b.extended()
                if (x.indices.any { abs(x[it] - z[it]) > 1e-9 } || a.regime != b.regime) return false to "factor row $k changed when later candles were removed"
            }
        }
        return true to "${cuts.size} cut points: rows identical with and without later candles"
    }

    private fun depthAudit(bars: List<Bar>, f: List<Factors?>, gaps: Int): Depth? {
        if (bars.isEmpty()) return null
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        fun ms(t: String) = try { (if (t.length > 10) fmt.parse(t) else fmt.parse("$t 00:00:00"))?.time } catch (_: Exception) { null }
        val a = ms(bars.first().time); val b = ms(bars.last().time)
        val sessions = LinkedHashMap<String, Int>()
        listOf("ASIA", "LONDON", "NEW YORK", "LATE").forEach { sessions[it] = 0 }
        var daily = false
        for (bar in bars) {
            val h = hourOf(bar.time)
            if (h == null) { daily = true; continue }
            val s = when { h < 7 -> "ASIA"; h < 13 -> "LONDON"; h < 21 -> "NEW YORK"; else -> "LATE" }
            sessions[s] = sessions[s]!! + 1
        }
        val regimes = LinkedHashMap<String, Int>()
        Regime.values().forEach { regimes[it.name.replace('_', ' ')] = 0 }
        var rows = 0
        f.forEach { r -> if (r != null) { rows++; val k = r.regime.name.replace('_', ' '); regimes[k] = regimes[k]!! + 1 } }
        return Depth(bars.size, if (a == null || b == null) 0.0 else (b - a) / 86_400_000.0, bars.first().time, bars.last().time,
            if (daily) emptyMap() else sessions, regimes, if (rows == 0) 0.0 else (regimes["HIGH VOL"] ?: 0).toDouble() / rows, gaps)
    }

    private fun currentRead(bars: List<Bar>, f: List<Factors?>, idx: List<Int>, label: IntArray): CurrentRead? {
        val lastI = bars.lastIndex
        val fl = f.getOrNull(lastI) ?: return null
        val train = idx.takeLast(TRAIN_MAX)
        val dec = train.filter { label[it] != 0 }
        val m = Logit.fit(dec.map { f[it]!!.directional() }, dec.map { label[it] == 1 })
        val txt = factorsText(fl)
        if (m == null) return CurrentRead(bars[lastI].time, fl.regime.name.replace('_', ' '), txt, null, "WAIT", "MODEL_UNAVAILABLE", "model could not be fitted")
        val lm = LiveModel("", m.w.toList(), m.mean.toList(), m.sd.toList(), gateFor(train.map { m.prob(f[it]!!.directional()) }, TARGET_COVERAGE), HORIZON, "", "", 0)
        val (d, p, why) = lm.decide(fl)
        val reason = when (why) {
            "OOD_STATE" -> "a factor is outside anything seen in training"
            "EDGE_TOO_SMALL" -> String.format(Locale.US, "model score %.2f is inside the WAIT zone (%.2f-%.2f)", p, 0.5 - lm.gate, 0.5 + lm.gate)
            else -> String.format(Locale.US, "model score %.2f is outside the WAIT zone", p)
        }
        return CurrentRead(bars[lastI].time, fl.regime.name.replace('_', ' '), txt, p, when (d) { 1 -> "UP"; -1 -> "DOWN"; else -> "WAIT" }, why, reason)
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

    fun hourOf(t: String): Double? {
        if (t.length < 16) return null
        val h = t.substring(11, 13).toIntOrNull() ?: return null
        val m = t.substring(14, 16).toIntOrNull() ?: 0
        return h + m / 60.0
    }

    /** Factor row per bar, null during warm-up. Row i uses only bars 0..i (verified by the causality check). */
    fun factorRows(bars: List<Bar>): List<Factors?> {
        val n = bars.size
        if (n == 0) return emptyList()
        val close = DoubleArray(n) { bars[it].close }
        val atr = atr14(bars)
        val e20 = ema(close, 20)
        val e50 = ema(close, 50)
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
            val window = DoubleArray(100) { atr[i - 99 + it] }.also { it.sort() }
            val med = window[50]
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

    /** Smallest |score - 0.5| that still lets ~[coverage] of the training samples through. */
    fun gateFor(trainProbs: List<Double>, coverage: Double): Double {
        if (trainProbs.isEmpty()) return 0.5
        if (coverage >= 1.0) return 0.0
        val d = trainProbs.map { abs(it - 0.5) }.sortedDescending()
        val k = (d.size * coverage).toInt().coerceIn(1, d.size)
        return max(0.02, d[k - 1])
    }

    // ------------------------------------------------------------------ helpers

    /** Provider candle size and how many of them make one app candle. */
    fun tdPlan(interval: String): Pair<String, Int> = when (interval.lowercase().trim()) {
        "1m", "1min" -> "1min" to 1; "2m" -> "1min" to 2; "3m" -> "1min" to 3; "4m" -> "1min" to 4
        "5m" -> "5min" to 1; "10m" -> "5min" to 2; "15m", "15min" -> "15min" to 1; "30m" -> "30min" to 1
        "45m" -> "45min" to 1; "1h" -> "1h" to 1; "2h" -> "2h" to 1; "3h" -> "1h" to 3; "4h" -> "4h" to 1
        "5h" -> "1h" to 5; "6h" -> "2h" to 3; "1d", "1day" -> "1day" to 1; "1w", "1week" -> "1week" to 1
        "2w" -> "1week" to 2; "3w" -> "1week" to 3; "1mo", "1month" -> "1month" to 1
        else -> "4h" to 1
    }

    fun tdCandleMs(td: String): Long = when (td) {
        "1min" -> 60_000L; "5min" -> 300_000L; "15min" -> 900_000L; "30min" -> 1_800_000L; "45min" -> 2_700_000L
        "1h" -> 3_600_000L; "2h" -> 7_200_000L; "4h" -> 14_400_000L; "1day" -> 86_400_000L; "1week" -> 604_800_000L
        else -> 0L
    }

    fun group(bars: List<Bar>, size: Int): List<Bar> {
        if (size <= 1) return bars
        return bars.chunked(size).filter { it.size == size }.map { ch ->
            Bar(ch.first().time, ch.first().open, ch.maxOf { it.high }, ch.minOf { it.low }, ch.last().close)
        }
    }

    fun f1(x: Double?) = if (x == null) "--" else String.format(Locale.US, "%.1f", x)
    fun f1s(x: Double?) = if (x == null) "--" else String.format(Locale.US, "%+.1f", x)
    fun usd(x: Double?) = if (x == null) "--" else String.format(Locale.US, "$%.2f", x)

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
