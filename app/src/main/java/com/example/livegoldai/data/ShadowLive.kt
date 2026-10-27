package com.example.livegoldai.data

import com.example.livegoldai.model.GoldAnalysisResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * LIVE SHADOW RECORDER (V28.1 → V28.4 live edge proof governance)
 *
 * The Shadow Lab model, run on every new CLOSED candle in real time next to the live engine. Never changes
 * the live BUY/SELL/WAIT. Every prediction is kept (append-only), including overlapping ones; qualification
 * statistics use a NON-OVERLAPPING proof sample and a moving-block bootstrap, and compare against live
 * baselines recorded on the very same candles.
 *
 * Proof approach: RETRAINING-POLICY PROOF. The deterministic daily retraining recipe ([ShadowLab.RECIPE_VERSION])
 * is the system under test. Every record keeps its model instance id and hash; a recipe change starts a new
 * qualification (old records never validate a new recipe).
 *
 * Status ladder: NO MODEL → COLLECTING (no lab edge) / LIVE VALIDATION (lab edge, evidence building)
 *                → LIVE QUALIFIED → UNDER REVIEW → DEMOTED.
 * Qualify and demote use different thresholds (hysteresis), so the status does not flip on noise.
 */
class ShadowLive(
    private val dir: () -> File,
    private val fetchBase: suspend (String, String?) -> Result<List<ShadowLab.Bar>>
) {
    data class Rec(
        val id: String, val interval: String, val barTime: String, val createdAt: Long,
        val decision: Int,              // +1 UP, -1 DOWN, 0 WAIT
        val waitReason: String,         // EDGE_TOO_SMALL / OOD_STATE / "" for UP or DOWN
        val score: Double,              // raw model score (not a probability until calibrated)
        val close: Double, val atr: Double, val horizon: Int,
        val modelId: String, val modelHash: String, val recipe: String, val modelVerdict: String,
        val regime: String,
        val bMom: Int, val bEma: Int,   // live baselines on the same candle (ALWAYS UP/DOWN are implicit)
        val snapshot: String,
        val bChamp: Int = 0             // current champion (live engine BUY/SELL/WAIT) from the same refresh
    )

    /** realized: UP / DOWN / FLAT / WINDOW_LOST — separate from the prediction, never inferred from it */
    data class Out(val id: String, val realized: String, val move: Double, val checkedAt: Long)

    data class BaselineLine(val name: String, val acc: Double?, val n: Int)

    data class Stats(
        val interval: String,
        val recipe: String,
        val status: String,
        val statusWhy: String,
        val labVerdict: String,
        val since: Long,
        val eligible: Int,              // checked candles (any decision)
        val calls: Int,
        val right: Int,
        val wrong: Int,
        val flat: Int,
        val pending: Int,
        val lost: Int,
        val proofN: Int,
        val proofAcc: Double?,
        val bootLow: Double?,
        val bootHigh: Double?,
        val outcomeShares: Triple<Double, Double, Double>,
        val balancedAcc: Double?,
        val upCallShare: Double?,
        val matched: List<BaselineLine>,
        val pairedDiffLow: Double?,
        val netByCost: List<Double?>,
        val regimesWithCalls: Int,
        val waitRate: Double?,
        val waitFlatShare: Double?,
        val waitReasons: Map<String, Int>,
        val calibrationState: String,
        val calibrationNote: String,
        val modelId: String,
        val modelTrainedAt: Long,
        val trainingRange: String,
        val lastCall: String
    ) {
        val decided get() = right + wrong
        val nonFlatAcc: Double? get() = if (decided == 0) null else 100.0 * right / decided
        val callSuccess: Double? get() = if (calls == 0) null else 100.0 * right / calls
        val flatRate: Double? get() = if (calls == 0) null else 100.0 * flat / calls
        val coverage: Double? get() = if (eligible == 0) null else calls.toDouble() / eligible
    }

    companion object {
        val LIVE_INTERVALS = setOf("1m", "5m", "15m", "30m", "45m", "1h", "2h", "4h", "1d", "1w")
        const val RETRAIN_MS = 24 * 3600_000L
        const val RETRY_MS = 3600_000L
        const val MIN_DECIDED = 100
        const val MIN_PROOF = 30
        const val TWELVE = "Twelve"
        private val utc = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        fun barMs(t: String): Long? = try { synchronized(utc) { (if (t.length > 10) utc.parse(t) else utc.parse("$t 00:00:00"))?.time } } catch (_: Exception) { null }
    }

    private val store by lazy { FileLedgerStore(File(dir(), "shadow_ledger.jsonl")) }
    val history by lazy { HistoryStore(dir) }
    private val modelFile get() = File(dir(), "shadow_models.json")
    private var loaded = false
    private val recs = LinkedHashMap<String, Rec>()
    private val outs = HashMap<String, Out>()
    private val seen = HashSet<String>()
    private val models = HashMap<String, ShadowLab.LiveModel>()
    private val qualified = HashMap<String, Long>()          // interval -> time it became LIVE QUALIFIED (hysteresis memory)
    private val demoted = HashMap<String, Long>()
    private val reports = HashMap<String, ShadowLab.Report>()
    private val lastAttempt = HashMap<String, Long>()
    private val training = HashSet<String>()

    // ------------------------------------------------------------------ load / save

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            for (line in store.readAll()) {
                if (line.isBlank()) continue
                @Suppress("UNCHECKED_CAST")
                val m = (try { MiniJson.parse(line) } catch (_: Exception) { null }) as? Map<String, Any?> ?: continue
                when (m.str("t")) {
                    "sr" -> {
                        val r = Rec(m.str("id"), m.str("iv"), m.str("bar"), m.long("c"), m.int("d"), m.str("why"), m.num("s"),
                            m.num("px"), m.num("atr"), m.int("h", ShadowLab.HORIZON), m.str("mid"), m.str("mh"), m.str("rcp"), m.str("mv"),
                            m.str("reg"), m.int("bm"), m.int("be"), m.str("sid"), m.int("bc"))
                        recs[r.id] = r; seen.add(r.interval + "|" + r.barTime)
                    }
                    "so" -> outs[m.str("id")] = Out(m.str("id"), m.str("real"), m.num("mv"), m.long("at"))
                }
            }
        } catch (_: Exception) { }
        try {
            if (modelFile.exists()) {
                @Suppress("UNCHECKED_CAST")
                val root = MiniJson.parse(modelFile.readText()) as? Map<String, Any?>
                @Suppress("UNCHECKED_CAST")
                (root?.get("models") as? Map<String, Any?>)?.forEach { (iv, v) ->
                    @Suppress("UNCHECKED_CAST")
                    val m = v as? Map<String, Any?> ?: return@forEach
                    fun dl(k: String) = m.list(k).mapNotNull { (it as? Number)?.toDouble() }
                    val calib = m.list("cal").mapNotNull { c ->
                        @Suppress("UNCHECKED_CAST")
                        val x = c as? Map<String, Any?> ?: return@mapNotNull null
                        ShadowLab.CalibLine(x.str("b"), x.num("lo"), x.num("hi"), x.int("n"), x.num("p"), x.num("a"))
                    }
                    models[iv] = ShadowLab.LiveModel(iv, dl("w"), dl("mean"), dl("sd"), m.num("gate"), m.int("h", ShadowLab.HORIZON),
                        m.str("sid"), m.str("verdict"), m.long("at"), m.str("rcp", ShadowLab.RECIPE_VERSION), m.str("fs"), m.str("tg"),
                        m.str("ts"), m.str("te"), calib)
                }
                root?.obj("qualified")?.forEach { (k, v) -> (v as? Number)?.let { qualified[k] = it.toLong() } }
                root?.obj("demoted")?.forEach { (k, v) -> (v as? Number)?.let { demoted[k] = it.toLong() } }
            }
        } catch (_: Exception) { }
    }

    @Synchronized
    private fun saveModels() {
        try {
            val ms = LinkedHashMap<String, Any?>()
            models.forEach { (iv, m) ->
                ms[iv] = linkedMapOf("w" to m.weights, "mean" to m.mean, "sd" to m.sd, "gate" to m.gate, "h" to m.horizon,
                    "sid" to m.snapshotId, "verdict" to m.verdict, "at" to m.trainedAt, "rcp" to m.recipe, "fs" to m.featureSchema,
                    "tg" to m.target, "ts" to m.trainingStart, "te" to m.trainingEnd,
                    "cal" to m.calib.map { linkedMapOf("b" to it.bin, "lo" to it.lo, "hi" to it.hi, "n" to it.n, "p" to it.predicted, "a" to it.actual) })
            }
            modelFile.parentFile?.mkdirs()
            modelFile.writeText(MiniJson.write(linkedMapOf("models" to ms, "qualified" to HashMap(qualified), "demoted" to HashMap(demoted))))
        } catch (_: Exception) { }
    }

    // ------------------------------------------------------------------ history + training

    @Synchronized fun report(interval: String): ShadowLab.Report? { ensureLoaded(); return reports[interval] }
    @Synchronized fun allReports(): Map<String, ShadowLab.Report> { ensureLoaded(); return HashMap(reports) }
    fun manifest(interval: String): HistoryStore.Manifest? = history.manifest(ShadowLab.tdPlan(interval).first)

    /** Downloads only what is new (1 credit), merges into the permanent store, runs the lab on the stored history. */
    suspend fun train(interval: String, now: Long = System.currentTimeMillis(), deepen: Boolean = false): Result<ShadowLab.Report> {
        synchronized(this) {
            ensureLoaded()
            if (!training.add(interval)) return Result.failure(IllegalStateException("Already running"))
            lastAttempt[interval] = now
        }
        try {
            val (td, group) = ShadowLab.tdPlan(interval)
            val stored = history.load(td)
            val endDate = if (deepen) stored.firstOrNull()?.time else null
            if (deepen && endDate == null) return Result.failure(IllegalStateException("Nothing stored yet: run the test first"))
            val fresh = fetchBase(td, endDate).getOrElse { return Result.failure(it) }
            val (all, man) = history.add(td, fresh, ShadowLab.tdCandleMs(td), now)
            val bars = ShadowLab.group(all, group, ShadowLab.tdCandleMs(td), now)
            val rep = ShadowLab.run(interval, bars, now, man.dataHash, man.gapCount).copy(historyRevisions = man.revisions.sumOf { it.changedBars })
            synchronized(this) {
                reports[interval] = rep
                val lm = rep.live
                if (lm != null && interval in LIVE_INTERVALS && group == 1) {
                    val old = models[interval]
                    if (old != null && old.recipe != lm.recipe) { qualified.remove(interval); demoted.remove(interval) }   // material change resets
                    models[interval] = lm
                    saveModels()
                }
            }
            return Result.success(rep)
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            synchronized(this) { training.remove(interval) }
        }
    }

    // ------------------------------------------------------------------ live observe

    suspend fun observe(a: GoldAnalysisResult, interval: String, now: Long = System.currentTimeMillis()): List<String> {
        if (interval !in LIVE_INTERVALS || a.isSimulatedFallback) return emptyList()
        if (a.feed?.source?.contains(TWELVE) != true) return emptyList()      // DATA source must match the lab
        // V29A: shadow calls only on clean primary data (same gate as production; degraded / restoring = no record)
        if (a.sourceState.isNotEmpty() && a.sourceState != SourceStateMachine.PRIMARY_ACTIVE) return emptyList()
        val src = a.chartCandles.ifEmpty { a.recentCandles }
        if (src.size < ShadowLab.WARMUP + 6) return emptyList()
        val bars = src.dropLast(1).map { ShadowLab.Bar(it.datetime, it.open, it.high, it.low, it.close) }
        resolve(interval, bars, now)
        val needTrain = synchronized(this) {
            ensureLoaded()
            val m = models[interval]
            (m == null || now - m.trainedAt > RETRAIN_MS) && now - (lastAttempt[interval] ?: 0L) > RETRY_MS
        }
        if (needTrain) train(interval, now)
        val champ = when (a.overallSignal) { com.example.livegoldai.model.Signal.BUY -> 1; com.example.livegoldai.model.Signal.SELL -> -1; else -> 0 }
        return record(interval, bars, now, champ)
    }

    @Synchronized
    private fun record(interval: String, bars: List<ShadowLab.Bar>, now: Long, champ: Int = 0): List<String> {
        val model = models[interval] ?: return emptyList()
        val last = bars.lastOrNull() ?: return emptyList()
        val key = "$interval|${last.time}"
        if (key in seen) return emptyList()
        val rows = ShadowLab.factorRows(bars)
        val f = rows.lastOrNull() ?: return emptyList()
        val atr = ShadowLab.atr14(bars).last()
        if (atr <= 0) return emptyList()
        val (d, p, why) = model.decide(f)
        val h = model.horizon
        val bMom = if (bars.size > h) Integer.signum(java.lang.Double.compare(last.close, bars[bars.size - 1 - h].close)) else 0
        val bEma = when { f.trend > 0 -> 1; f.trend < 0 -> -1; else -> 0 }
        val snap = HistoryStore.sha256(listOf(HistoryStore.PROVIDER, HistoryStore.INSTRUMENT, interval, last.time, HistoryStore.line(last),
            ShadowLab.FEATURE_SCHEMA, model.instanceId).joinToString("|"))
        val r = Rec("sh-$interval-${last.time.replace(' ', 'T')}", interval, last.time, now, d, why, p, last.close, atr, h,
            model.instanceId, model.modelHash, model.recipe, model.verdict, f.regime.name, bMom, bEma, snap, champ)
        val ok = try {
            store.append(listOf(MiniJson.write(linkedMapOf(
                "t" to "sr", "id" to r.id, "iv" to r.interval, "bar" to r.barTime, "c" to r.createdAt, "d" to r.decision, "why" to r.waitReason,
                "s" to r.score, "px" to r.close, "atr" to r.atr, "h" to r.horizon, "mid" to r.modelId, "mh" to r.modelHash, "rcp" to r.recipe,
                "mv" to r.modelVerdict, "reg" to r.regime, "bm" to r.bMom, "be" to r.bEma, "sid" to r.snapshot, "bc" to r.bChamp
            ))))
            true
        } catch (_: Exception) { false }
        if (!ok) return emptyList()
        recs[r.id] = r; seen.add(key)
        if (d == 0) return emptyList()
        return if (statsLocked(interval).status == "LIVE QUALIFIED")
            listOf(String.format(Locale.US, "%s %s • model score %.2f • next %d candles", interval, if (d > 0) "UP" else "DOWN", p, h))
        else emptyList()
    }

    @Synchronized
    private fun resolve(interval: String, bars: List<ShadowLab.Bar>, now: Long) {
        ensureLoaded()
        if (bars.isEmpty()) return
        val pos = HashMap<String, Int>(bars.size * 2)
        bars.forEachIndexed { i, b -> pos[b.time] = i }
        val ivMs = RealityEngine.intervalMinutes(interval) * 60_000L
        val lines = ArrayList<String>()
        for (r in recs.values) {
            if (r.interval != interval || outs.containsKey(r.id)) continue
            val i = pos[r.barTime]
            val out = when {
                i != null && i + r.horizon <= bars.lastIndex -> {
                    val mv = bars[i + r.horizon].close - r.close
                    val real = when { mv >= ShadowLab.FLAT_ATR * r.atr -> "UP"; mv <= -ShadowLab.FLAT_ATR * r.atr -> "DOWN"; else -> "FLAT" }
                    Out(r.id, real, mv, now)
                }
                i == null && bars.first().time > r.barTime && now - r.createdAt > ivMs * (r.horizon + 2) -> Out(r.id, "WINDOW_LOST", 0.0, now)
                else -> null
            } ?: continue
            outs[r.id] = out
            lines.add(MiniJson.write(linkedMapOf("t" to "so", "id" to out.id, "real" to out.realized, "mv" to out.move, "at" to out.checkedAt)))
        }
        if (lines.isNotEmpty()) try { store.append(lines) } catch (_: Exception) { }
    }

    // ------------------------------------------------------------------ statistics + status

    @Synchronized fun stats(interval: String): Stats { ensureLoaded(); return statsLocked(interval) }

    @Synchronized fun allStats(): List<Stats> {
        ensureLoaded()
        val ivs = (recs.values.map { it.interval } + models.keys).toSet()
        return LIVE_INTERVALS.filter { it in ivs }.map { statsLocked(it) }
    }

    private fun y(real: String) = when (real) { "UP" -> 1; "DOWN" -> -1; else -> 0 }

    private fun statsLocked(interval: String): Stats {
        val model = models[interval]
        val recipe = model?.recipe ?: ShadowLab.RECIPE_VERSION
        val mine = recs.values.filter { it.interval == interval && it.recipe == recipe }.sortedBy { it.barTime }
        var eligible = 0; var calls = 0; var right = 0; var wrong = 0; var flat = 0; var pending = 0; var lost = 0
        var ups = 0; var downs = 0; var flats = 0; var upCalls = 0
        var upN = 0; var upR = 0; var dnN = 0; var dnR = 0
        var waits = 0; var waitFlat = 0
        val waitReasons = LinkedHashMap<String, Int>()
        val net = DoubleArray(ShadowLab.COSTS.size)
        val series = ArrayList<Double>(); val decidedBars = ArrayList<Long>(); val decidedRight = ArrayList<Int>()
        val diffs = HashMap<String, ArrayList<Double>>()
        val bAcc = LinkedHashMap<String, IntArray>()       // right, n on the model's decided calls
        listOf("MOMENTUM", "EMA TREND", "ALWAYS UP", "ALWAYS DOWN", "CHAMPION").forEach { bAcc[it] = IntArray(2); diffs[it] = ArrayList() }
        val regimesCalled = HashMap<String, Int>()
        val calib = ArrayList<Pair<Double, Boolean>>()
        for (r in mine) {
            val o = outs[r.id]
            if (o == null) { pending++; continue }
            if (o.realized == "WINDOW_LOST") { lost++; continue }
            eligible++
            val yy = y(o.realized)
            when (yy) { 1 -> ups++; -1 -> downs++; else -> flats++ }
            if (r.decision == 0) {
                waits++; if (yy == 0) waitFlat++
                waitReasons[r.waitReason.ifBlank { "UNKNOWN" }] = (waitReasons[r.waitReason.ifBlank { "UNKNOWN" }] ?: 0) + 1
                continue
            }
            calls++
            if (r.decision > 0) upCalls++
            ShadowLab.COSTS.forEachIndexed { ci, (_, c) -> net[ci] += r.decision * o.move - c }
            when (yy) {
                r.decision -> right++
                -r.decision -> wrong++
                else -> flat++
            }
            if (yy != 0) {
                val ok = if (yy == r.decision) 1.0 else 0.0
                series.add(ok); decidedBars.add(barMs(r.barTime) ?: r.createdAt); decidedRight.add(ok.toInt())
                regimesCalled[r.regime] = (regimesCalled[r.regime] ?: 0) + 1
                calib.add(r.score to (yy == 1))
                if (yy == 1) { upN++; if (r.decision == 1) upR++ } else { dnN++; if (r.decision == -1) dnR++ }
                // CHAMPION (live engine) is compared only where it also made a BUY/SELL call
                val bs = mapOf("MOMENTUM" to r.bMom, "EMA TREND" to r.bEma, "ALWAYS UP" to 1, "ALWAYS DOWN" to -1) +
                    (if (r.bChamp != 0) mapOf("CHAMPION" to r.bChamp) else emptyMap())
                bs.forEach { (n, bd) ->
                    val br = if (bd == yy) 1 else 0
                    bAcc[n]!![0] += br; bAcc[n]!![1]++
                    diffs[n]!!.add(ok - br)
                }
            }
        }
        val h = model?.horizon ?: ShadowLab.HORIZON
        val ivMs = RealityEngine.intervalMinutes(interval) * 60_000L
        // non-overlapping proof sample (by candle time, so weekends only make it safer)
        var proofN = 0; var proofR = 0; var nextFree = Long.MIN_VALUE
        for (k in decidedBars.indices) {
            if (decidedBars[k] >= nextFree) { proofN++; proofR += decidedRight[k]; nextFree = decidedBars[k] + h * ivMs }
        }
        val boot = ShadowLab.Evidence.blockBootstrap(series.toDoubleArray(), h * 2, 1, interval.hashCode().toLong())
        val matched = bAcc.map { (n, a) -> BaselineLine(n, if (a[1] == 0) null else 100.0 * a[0] / a[1], a[1]) }
        // the qualification rule pairs against the best of the always-present baselines (champion shown separately)
        val bestMatched = matched.filter { it.acc != null && it.name != "CHAMPION" }.maxByOrNull { it.acc!! }
        val paired = bestMatched?.let { b -> ShadowLab.Evidence.blockBootstrap(diffs[b.name]!!.toDoubleArray(), h * 2, 1, interval.hashCode() + 11L) }
        val netByCost = net.map { if (calls == 0) null else it / calls }
        val decided = right + wrong
        val nonFlat = if (decided == 0) null else 100.0 * right / decided
        val coverage = if (eligible == 0) 0.0 else calls.toDouble() / eligible
        val regimesWith = regimesCalled.count { it.value >= 10 }

        // ---- calibration provenance + drift
        val (calState, calNote) = calibrationState(model, calib)

        // ---- status with hysteresis
        val labEdge = model?.verdict == "EDGE CANDIDATE"
        val qualifies = labEdge && decided >= MIN_DECIDED && proofN >= MIN_PROOF && (boot?.first ?: 0.0) * 100 > 50.0 &&
            (paired?.first ?: -1.0) > 0.0 && coverage >= ShadowLab.MIN_COVERAGE && regimesWith >= 2 && (netByCost.getOrNull(2) ?: -1.0) > 0
        val clearlyBad = decided >= MIN_DECIDED && ((boot?.second ?: 1.0) * 100 < 50.0 || (paired?.second ?: 1.0) < 0.0)
        val wasQualified = qualified.containsKey(interval)
        val status: String
        val why: String
        when {
            model == null -> { status = "NO MODEL"; why = "no model trained yet for this timeframe" }
            wasQualified && clearlyBad -> {
                qualified.remove(interval); demoted[interval] = System.currentTimeMillis(); saveModels()
                status = "DEMOTED"; why = "live evidence now clearly below 50% or below its baseline"
            }
            wasQualified && !qualifies -> { status = "UNDER REVIEW"; why = "no longer meets every qualification rule, not yet clearly bad (no alerts)" }
            qualifies -> {
                if (!wasQualified) { qualified[interval] = System.currentTimeMillis(); demoted.remove(interval); saveModels() }
                status = "LIVE QUALIFIED"; why = "passed every live rule; can be withdrawn automatically"
            }
            demoted.containsKey(interval) -> { status = "DEMOTED"; why = "was qualified, then failed live; must re-qualify from scratch" }
            !labEdge -> { status = "COLLECTING"; why = "lab shows no edge for this model, so it cannot qualify; calls are still recorded" }
            else -> { status = "LIVE VALIDATION"; why = "lab edge candidate; needs $MIN_DECIDED decided + $MIN_PROOF non-overlapping calls, bootstrap > 50%, beats live baselines, 2+ regimes, positive at reference cost" }
        }
        val lastCall = mine.lastOrNull()?.let { r ->
            r.barTime + " • " + when (r.decision) { 1 -> "UP"; -1 -> "DOWN"; else -> "WAIT (" + r.waitReason + ")" } + String.format(Locale.US, " • score %.2f", r.score)
        } ?: "--"
        val tot = (ups + downs + flats).coerceAtLeast(1).toDouble()
        return Stats(interval, recipe, status, why, model?.verdict ?: "--", mine.minOfOrNull { it.createdAt } ?: 0L,
            eligible, calls, right, wrong, flat, pending, lost, proofN, if (proofN == 0) null else 100.0 * proofR / proofN,
            boot?.first?.times(100), boot?.second?.times(100), Triple(ups / tot, downs / tot, flats / tot),
            if (upN > 0 && dnN > 0) 50.0 * (upR.toDouble() / upN + dnR.toDouble() / dnN) else null,
            if (calls == 0) null else 100.0 * upCalls / calls,
            matched, paired?.first?.times(100), netByCost, regimesWith,
            if (eligible == 0) null else 100.0 * waits / eligible, if (waits == 0) null else 100.0 * waitFlat / waits, waitReasons,
            calState, calNote, model?.instanceId ?: "--", model?.trainedAt ?: 0L,
            if (model == null) "--" else "${model.trainingStart} → ${model.trainingEnd}", lastCall)
    }

    /** UNCALIBRATED SCORE / LAB CALIBRATED / LIVE VALIDATED CALIBRATION / CALIBRATION DRIFT */
    private fun calibrationState(model: ShadowLab.LiveModel?, live: List<Pair<Double, Boolean>>): Pair<String, String> {
        if (model == null || model.calib.isEmpty()) return "UNCALIBRATED SCORE" to "no out-of-fold calibration yet"
        var checked = 0; var drift = 0
        val parts = ArrayList<String>()
        for (b in model.calib) {
            val inBin = live.filter { val c = maxOf(it.first, 1 - it.first); c >= b.lo && c < b.hi }
            if (inBin.size < 30 || b.n < 30) continue
            checked++
            val act = 100.0 * inBin.count { (it.first > 0.5) == it.second } / inBin.size
            if (kotlin.math.abs(act - b.actual) > 10.0) drift++
            parts.add(String.format(Locale.US, "%s: lab %.0f%% / live %.0f%% (N=%d)", b.bin, b.actual, act, inBin.size))
        }
        return when {
            checked == 0 -> "LAB CALIBRATED" to "lab (out-of-fold) only; live needs 30+ results per score band"
            drift > 0 -> "CALIBRATION DRIFT" to parts.joinToString(" • ")
            else -> "LIVE VALIDATED CALIBRATION" to parts.joinToString(" • ")
        }
    }

    @Synchronized
    fun csv(): String {
        ensureLoaded()
        val sb = StringBuilder("id,interval,candle_utc,recorded_at_ms,decision,wait_reason,model_score,price,atr,horizon,regime,baseline_momentum,baseline_ema,champion,model_instance,model_hash,recipe,lab_verdict,snapshot_sha256,realized_outcome,move_usd\n")
        for (r in recs.values) {
            val o = outs[r.id]
            sb.append(r.id).append(',').append(r.interval).append(',').append(r.barTime).append(',').append(r.createdAt).append(',')
                .append(when (r.decision) { 1 -> "UP"; -1 -> "DOWN"; else -> "WAIT" }).append(',').append(r.waitReason).append(',')
                .append(String.format(Locale.US, "%.4f", r.score)).append(',').append(r.close).append(',')
                .append(String.format(Locale.US, "%.4f", r.atr)).append(',').append(r.horizon).append(',').append(r.regime).append(',')
                .append(r.bMom).append(',').append(r.bEma).append(',').append(r.bChamp).append(',').append(r.modelId).append(',').append(r.modelHash).append(',')
                .append(r.recipe).append(',').append(r.modelVerdict).append(',').append(r.snapshot).append(',')
                .append(o?.realized ?: "PENDING").append(',')
                .append(o?.let { String.format(Locale.US, "%.2f", it.move) } ?: "").append('\n')
        }
        return sb.toString()
    }
}

/** CSV export of the main prediction ledger (every recorded prediction and its checked result). */
object LedgerCsv {
    private fun q(s: String) = if (s.contains(',') || s.contains('"') || s.contains('\n')) "\"" + s.replace("\"", "\"\"") + "\"" else s

    fun build(state: LedgerState): String {
        val sb = StringBuilder("id,created_utc,expires_utc,interval,signal_shown,engine_signal,confidence,price,atr,regime,session,news,model,data_source,snapshot_id,result,move_usd,threshold_usd,checked_utc,verify_source\n")
        for (r in state.records) {
            val res = state.resultOf(r.id)
            sb.append(q(r.id)).append(',')
                .append(PredictionLedger.utcLabel(r.createdAt)).append(',')
                .append(PredictionLedger.utcLabel(r.expiresAt)).append(',')
                .append(r.interval).append(',').append(r.finalSignal.name).append(',').append(r.rawSignal.name).append(',')
                .append(r.confidence).append(',').append(r.price).append(',').append(String.format(Locale.US, "%.4f", r.atr)).append(',')
                .append(q(r.regime)).append(',').append(q(r.session)).append(',').append(r.newsActive).append(',')
                .append(q(r.modelVersion)).append(',').append(q(r.dataSource)).append(',').append(q(r.snapshotId)).append(',')
                .append(res?.outcome?.name ?: "PENDING").append(',')
                .append(res?.let { String.format(Locale.US, "%.2f", it.move) } ?: "").append(',')
                .append(res?.let { String.format(Locale.US, "%.2f", it.threshold) } ?: "").append(',')
                .append(res?.let { PredictionLedger.utcLabel(it.checkedAt) } ?: "").append(',')
                .append(q(res?.verifySource ?: "")).append('\n')
        }
        return sb.toString()
    }
}
