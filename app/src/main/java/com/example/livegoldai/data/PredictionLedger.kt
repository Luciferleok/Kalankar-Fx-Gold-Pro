package com.example.livegoldai.data

import com.example.livegoldai.model.CandleBar
import com.example.livegoldai.model.Signal
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * PREDICTION LEDGER
 *
 * Append-only file. Every prediction the app shows is written once ("rec" line).
 * When it expires, the real result is appended as a separate "out" line.
 * Learning-engine decisions are appended as "evt" lines.
 * Old lines are never edited.
 */

enum class LedgerOutcome {
    PENDING, CORRECT, INCORRECT, INVALIDATED, SIDEWAYS, WAIT_FLAT, WAIT_MISSED, DATA_FAILURE, MARKET_CLOSED,
    /** Checked only against a different instrument and the result was too close to call: never counted right or wrong. */
    VERIFICATION_UNCERTAIN
}

data class LedgerRecord(
    val id: String,
    val createdAt: Long,
    val expiresAt: Long,
    val interval: String,
    val horizonMin: Int,
    val barId: String,
    val price: Double,
    val rawSignal: Signal,        // what the indicator engine said
    val finalSignal: Signal,      // what the user was shown (after learned filters)
    val confidence: Int,          // agreement % shown to the user
    val atr: Double,
    val slDistance: Double,
    val regime: String,
    val session: String,
    val newsActive: Boolean,
    val modelVersion: String,
    val appliedFilter: String,    // candidate id that turned raw signal into WAIT, "" if none
    val sources: Map<String, Signal>,
    // ---- provenance (V11): how this prediction was made. Empty on records written by older versions.
    val dataSource: String = "",      // "Twelve Data XAU/USD", "Binance PAXG/USDT", "Yahoo GC=F"
    val snapshotId: String = "",      // same id the AI council and the feature row refer to
    val engineVersion: String = "",
    val featureVersion: String = "",
    /** Distance from the prediction price to target 1. 0 on older records (then only the end-of-window rule is used). */
    val tpDistance: Double = 0.0
)

data class PathBar(val t: Long, val o: Double, val h: Double, val l: Double, val c: Double)

/** waiting = the same-source price history is not available yet: try again later, this is not a failure. */
data class PricePath(val source: String, val bars: List<PathBar>, val waiting: Boolean = false)

data class LedgerResult(
    val id: String,
    val checkedAt: Long,
    val outcome: LedgerOutcome,
    val verifySource: String,
    val startPx: Double,
    val endPx: Double,
    val move: Double,
    val threshold: Double,
    val mfe: Double,              // max move in predicted direction (for WAIT: max up move)
    val mae: Double,              // max move against (for WAIT: max down move)
    val slHitAt: Long,            // 0 if never
    val realizedRange: Double,
    val tags: List<String>,
    val attribution: Map<String, Int>,
    val warnings: List<String>,
    val counterfactual: String,
    val timeline: List<Pair<Long, String>>,
    // ---- source-consistent verification (V11)
    val sourceMatch: String = "",        // SAME / PROXY / LEGACY ("" on results written by older versions)
    val basis: Double = 0.0,             // proxy start price − prediction price (0 when SAME)
    val basisNoise: Double = 0.0,        // band used to decide "too close to call"
    val verifyConfidence: String = "",   // HIGH (same instrument) / MEDIUM (proxy, decisive) / LOW (proxy, too close)
    val tpHitAt: Long = 0L,              // when target 1 was reached before the stop (0 = not reached first)
    /** Symmetric path result: which side was touched first at 1 x ATR. +1 with the call, -1 against, 0 neither / same bar / WAIT. */
    val pathHit: Int = 0
)

data class LedgerEvent(
    val at: Long,
    val kind: String,      // "cand"
    val candidateId: String,
    val stage: String,     // SHADOW / PROMOTED / REJECTED / ROLLED_BACK
    val detail: String
)

class LedgerState(
    val records: List<LedgerRecord>,
    val results: Map<String, LedgerResult>,
    val events: List<LedgerEvent>,
    val skippedLines: Int = 0
) {
    fun resultOf(id: String): LedgerResult? = results[id]
}

/** Storage abstraction so the logic can be tested off-device. */
interface LedgerStore {
    fun readAll(): List<String>
    fun append(lines: List<String>)
    fun sizeBytes(): Long = -1L
}

class FileLedgerStore(private val file: File) : LedgerStore {
    @Synchronized
    override fun readAll(): List<String> = if (file.exists()) file.readLines() else emptyList()

    @Synchronized
    override fun append(lines: List<String>) {
        if (lines.isEmpty()) return
        file.parentFile?.mkdirs()
        file.appendText(lines.joinToString(separator = "\n", postfix = "\n"))
    }

    override fun sizeBytes(): Long = if (file.exists()) file.length() else 0L
}

class InMemoryLedgerStore : LedgerStore {
    val lines = mutableListOf<String>()
    override fun readAll(): List<String> = lines.toList()
    override fun append(lines: List<String>) { this.lines.addAll(lines) }
}

object PredictionLedger {

    const val MAX_RECORDS_KEPT_IN_MEMORY = 20000

    // ------------------------------------------------------------------ load / save

    fun load(store: LedgerStore): LedgerState {
        val records = ArrayList<LedgerRecord>()
        val results = LinkedHashMap<String, LedgerResult>()
        val events = ArrayList<LedgerEvent>()
        var skipped = 0
        for (line in store.readAll()) {
            if (line.isBlank()) continue
            val m = try {
                @Suppress("UNCHECKED_CAST")
                MiniJson.parse(line) as? Map<String, Any?>
            } catch (_: Exception) {
                null
            }
            if (m == null) { skipped++; continue }
            when (m.str("t")) {
                "rec" -> decodeRecord(m)?.let { records.add(it) }
                "out" -> decodeResult(m)?.let { results[it.id] = it }
                "evt" -> events.add(
                    LedgerEvent(
                        at = m.long("at"), kind = m.str("kind"), candidateId = m.str("cid"),
                        stage = m.str("stage"), detail = m.str("detail")
                    )
                )
            }
        }
        val kept = if (records.size > MAX_RECORDS_KEPT_IN_MEMORY) records.takeLast(MAX_RECORDS_KEPT_IN_MEMORY) else records
        return LedgerState(kept, results, events, skipped)
    }

    fun encodeRecord(r: LedgerRecord): String = MiniJson.write(
        linkedMapOf(
            "t" to "rec", "id" to r.id, "c" to r.createdAt, "e" to r.expiresAt, "iv" to r.interval,
            "h" to r.horizonMin, "bar" to r.barId, "px" to r.price, "raw" to r.rawSignal.name,
            "fin" to r.finalSignal.name, "conf" to r.confidence, "atr" to r.atr, "sl" to r.slDistance,
            "reg" to r.regime, "ses" to r.session, "news" to r.newsActive, "mv" to r.modelVersion,
            "flt" to r.appliedFilter, "src" to r.sources.mapValues { it.value.name },
            "ds" to r.dataSource, "sid" to r.snapshotId, "ev" to r.engineVersion, "fv" to r.featureVersion,
            "tp" to r.tpDistance
        )
    )

    private fun decodeRecord(m: Map<String, Any?>): LedgerRecord? = try {
        LedgerRecord(
            id = m.str("id"), createdAt = m.long("c"), expiresAt = m.long("e"), interval = m.str("iv"),
            horizonMin = m.int("h"), barId = m.str("bar"), price = m.num("px"),
            rawSignal = Signal.valueOf(m.str("raw", "WAIT")), finalSignal = Signal.valueOf(m.str("fin", "WAIT")),
            confidence = m.int("conf"), atr = m.num("atr"), slDistance = m.num("sl"), regime = m.str("reg"),
            session = m.str("ses"), newsActive = m.bool("news"), modelVersion = m.str("mv"),
            appliedFilter = m.str("flt"),
            sources = m.obj("src").mapNotNull { (k, v) ->
                val sig = (v as? String)?.let { runCatching { Signal.valueOf(it) }.getOrNull() }
                if (sig == null) null else k to sig
            }.toMap(),
            dataSource = m.str("ds"), snapshotId = m.str("sid"), engineVersion = m.str("ev"), featureVersion = m.str("fv"),
            tpDistance = m.num("tp")
        )
    } catch (_: Exception) {
        null
    }

    fun encodeResult(r: LedgerResult): String = MiniJson.write(
        linkedMapOf(
            "t" to "out", "id" to r.id, "at" to r.checkedAt, "o" to r.outcome.name, "vs" to r.verifySource,
            "s" to r.startPx, "x" to r.endPx, "mv" to r.move, "thr" to r.threshold, "mfe" to r.mfe,
            "mae" to r.mae, "slh" to r.slHitAt, "rng" to r.realizedRange, "tags" to r.tags,
            "att" to r.attribution, "warn" to r.warnings, "cf" to r.counterfactual,
            "tl" to r.timeline.map { listOf(it.first, it.second) },
            "sm" to r.sourceMatch, "bs" to r.basis, "bn" to r.basisNoise, "vc" to r.verifyConfidence, "tph" to r.tpHitAt, "ph" to r.pathHit
        )
    )

    private fun decodeResult(m: Map<String, Any?>): LedgerResult? = try {
        LedgerResult(
            id = m.str("id"), checkedAt = m.long("at"), outcome = LedgerOutcome.valueOf(m.str("o")),
            verifySource = m.str("vs"), startPx = m.num("s"), endPx = m.num("x"), move = m.num("mv"),
            threshold = m.num("thr"), mfe = m.num("mfe"), mae = m.num("mae"), slHitAt = m.long("slh"),
            realizedRange = m.num("rng"),
            tags = m.list("tags").mapNotNull { it as? String },
            attribution = m.obj("att").mapNotNull { (k, v) -> (v as? Number)?.let { k to it.toInt() } }.toMap(),
            warnings = m.list("warn").mapNotNull { it as? String },
            counterfactual = m.str("cf"),
            timeline = m.list("tl").mapNotNull { item ->
                val l = item as? List<*> ?: return@mapNotNull null
                val t = (l.getOrNull(0) as? Number)?.toLong() ?: return@mapNotNull null
                val txt = l.getOrNull(1) as? String ?: return@mapNotNull null
                t to txt
            },
            sourceMatch = m.str("sm"), basis = m.num("bs"), basisNoise = m.num("bn"), verifyConfidence = m.str("vc"),
            tpHitAt = m.long("tph"), pathHit = m.int("ph")
        )
    } catch (_: Exception) {
        null
    }

    fun encodeEvent(e: LedgerEvent): String = MiniJson.write(
        linkedMapOf("t" to "evt", "at" to e.at, "kind" to e.kind, "cid" to e.candidateId, "stage" to e.stage, "detail" to e.detail)
    )

    // ------------------------------------------------------------------ source-consistent verification

    const val ENGINE_VERSION = "E11.3"

    /** True when the result is based on a trustworthy real price path. */
    fun reliable(o: LedgerOutcome): Boolean =
        o != LedgerOutcome.PENDING && o != LedgerOutcome.DATA_FAILURE && o != LedgerOutcome.MARKET_CLOSED && o != LedgerOutcome.VERIFICATION_UNCERTAIN

    /** Which instrument a source name refers to: XAU (spot), PAXG (token), GC (futures), "" unknown. */
    fun instrumentOf(source: String): String = when {
        source.contains("XAU", true) -> "XAU"
        source.contains("PAXG", true) -> "PAXG"
        source.contains("GC=F", true) -> "GC"
        else -> ""
    }

    /**
     * A prediction made on one instrument must not be blindly judged on another.
     *  SAME   -> the result stands (confidence HIGH).
     *  PROXY  -> the result stands only if the move is clearly past the deciding line by more than the basis noise
     *            band; otherwise the outcome becomes VERIFICATION_UNCERTAIN and is not counted right or wrong.
     *  LEGACY -> record from an older version without a stored source: result kept, marked as such.
     * @param noise measured short-term basis noise for this instrument pair, or null when fewer than 10 samples exist
     *              (then a default band of 0.05% of price is used and recorded).
     */
    fun sourceCheck(record: LedgerRecord, res: LedgerResult, noise: Double?): LedgerResult {
        if (!reliable(res.outcome) || res.startPx <= 0.0) return res
        val a = instrumentOf(record.dataSource)
        val b = instrumentOf(res.verifySource)
        if (a.isEmpty() || b.isEmpty()) return res.copy(sourceMatch = "LEGACY", verifyConfidence = "LEGACY")
        if (a == b) return res.copy(sourceMatch = "SAME", verifyConfidence = "HIGH")
        val band = noise ?: (0.0005 * record.price)
        val margin = when (res.outcome) {
            LedgerOutcome.INVALIDATED -> res.mae - record.slDistance
            LedgerOutcome.CORRECT -> if (res.tpHitAt != 0L) res.mfe - record.tpDistance else abs(abs(res.move) - res.threshold)
            LedgerOutcome.WAIT_FLAT, LedgerOutcome.WAIT_MISSED -> abs(abs(res.move) - 2 * res.threshold)
            else -> abs(abs(res.move) - res.threshold)
        }
        val basis = res.startPx - record.price
        return if (margin < band) {
            res.copy(
                outcome = LedgerOutcome.VERIFICATION_UNCERTAIN, sourceMatch = "PROXY", basis = basis, basisNoise = band,
                verifyConfidence = "LOW", tags = emptyList(), attribution = emptyMap(), warnings = emptyList(), counterfactual = "",
                timeline = res.timeline + (res.checkedAt to "Checked on ${res.verifySource}, not on ${record.dataSource}: move ${signed(res.move)} is within the ${fmt(band)} noise band of the deciding line -> not counted")
            )
        } else res.copy(sourceMatch = "PROXY", basis = basis, basisNoise = band, verifyConfidence = "MEDIUM")
    }

    /** Short-term basis noise between two instruments: spread of the change in (proxy − source) between consecutive checks. */
    fun basisNoise(state: LedgerState, recordInstrument: String, proxyInstrument: String, now: Long): Double? {
        val recs = state.records.associateBy { it.id }
        val pts = state.results.values.mapNotNull { r ->
            val rec = recs[r.id] ?: return@mapNotNull null
            if (r.sourceMatch != "PROXY" || now - rec.createdAt > 72 * 3_600_000L) return@mapNotNull null
            if (instrumentOf(rec.dataSource) != recordInstrument || instrumentOf(r.verifySource) != proxyInstrument) return@mapNotNull null
            rec.createdAt to r.basis
        }.sortedBy { it.first }
        if (pts.size < 11) return null
        val d = pts.zipWithNext { x, y -> y.second - x.second }
        val m = d.average()
        val sd = kotlin.math.sqrt(d.sumOf { (it - m) * (it - m) } / d.size)
        return max(sd, 0.10)
    }

    // ------------------------------------------------------------------ helpers

    fun isMarketClosed(ms: Long): Boolean {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = ms
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return when (dow) {
            Calendar.SATURDAY -> true
            Calendar.FRIDAY -> hour >= 21
            Calendar.SUNDAY -> hour < 22
            else -> false
        }
    }

    /** True if any part of [start, end] falls in the weekend closure (checked every 30 minutes). */
    fun windowTouchesClosure(start: Long, end: Long): Boolean {
        var t = start
        while (t <= end) {
            if (isMarketClosed(t)) return true
            t += 30 * 60_000L
        }
        return isMarketClosed(end)
    }

    fun sessionOf(ms: Long): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = ms
        return when (cal.get(Calendar.HOUR_OF_DAY)) {
            in 7..11 -> "LONDON"
            in 12..15 -> "LONDON_NY_OVERLAP"
            in 16..20 -> "NEW_YORK"
            21 -> "OFF_HOURS"
            else -> "ASIAN"
        }
    }

    fun atrOf(candles: List<CandleBar>, period: Int = 14): Double {
        if (candles.size < 2) return 0.0
        val trs = ArrayList<Double>()
        for (i in 1 until candles.size) {
            val c = candles[i]
            val pc = candles[i - 1].close
            trs.add(maxOf(c.high - c.low, abs(c.high - pc), abs(c.low - pc)))
        }
        val tail = trs.takeLast(period)
        return if (tail.isEmpty()) 0.0 else tail.average()
    }

    /** Measurable regime from the candles the prediction was made on. */
    fun regimeOf(candles: List<CandleBar>, newsActive: Boolean): String {
        if (newsActive) return "NEWS_EVENT"
        if (candles.size < 22) return "UNKNOWN"
        val closes = candles.map { it.close }
        val n = closes.size
        val window = closes.subList(n - 21, n)
        val net = abs(window.last() - window.first())
        var path = 0.0
        for (i in 1 until window.size) path += abs(window[i] - window[i - 1])
        val er = if (path > 0) net / path else 0.0
        val atr = atrOf(candles)
        val last = candles.last()
        if (atr > 0 && (last.high - last.low) > 2.2 * atr) return "VOLATILITY_EXPANSION"
        val ema9 = ema(closes, 9)
        val ema21 = ema(closes, 21)
        return when {
            er >= 0.35 && ema9 > ema21 && last.close > ema21 -> "TREND_UP"
            er >= 0.35 && ema9 < ema21 && last.close < ema21 -> "TREND_DOWN"
            er < 0.20 -> "RANGE"
            else -> "TRANSITION"
        }
    }

    private fun ema(values: List<Double>, period: Int): Double {
        if (values.isEmpty()) return 0.0
        val k = 2.0 / (period + 1)
        var e = values.take(min(period, values.size)).average()
        for (i in min(period, values.size) until values.size) e = values[i] * k + e * (1 - k)
        return e
    }

    fun utcLabel(ms: Long, withDay: Boolean = false): String {
        val f = SimpleDateFormat(if (withDay) "dd MMM HH:mm" else "HH:mm", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(ms)) + " UTC"
    }

    /** +1 correct, 0 wrong, -1 not decided (sideways / WAIT). Direction only, no stop-loss. */
    fun directionScore(signal: Signal, move: Double, threshold: Double): Int = when (signal) {
        Signal.BUY -> if (move >= threshold) 1 else if (move <= -threshold) 0 else -1
        Signal.SELL -> if (move <= -threshold) 1 else if (move >= threshold) 0 else -1
        Signal.WAIT -> -1
    }

    fun opposite(a: Signal, b: Signal): Boolean =
        (a == Signal.BUY && b == Signal.SELL) || (a == Signal.SELL && b == Signal.BUY)

    // ------------------------------------------------------------------ verification

    /**
     * Turns a real price path into a result + forensic evidence.
     * Pure function: same input -> same output.
     */
    fun evaluate(record: LedgerRecord, path: PricePath, now: Long): LedgerResult {
        val bars = path.bars.filter { it.t >= record.createdAt - 60_000L && it.t < record.expiresAt }.sortedBy { it.t }
        if (bars.isEmpty()) {
            return emptyResult(record, now, LedgerOutcome.DATA_FAILURE, path.source)
        }
        val start = bars.first().o
        val end = bars.last().c
        val move = end - start
        val threshold = max(0.25 * record.atr, 0.30)
        val sig = record.finalSignal
        val dirSign = if (sig == Signal.SELL) -1.0 else 1.0

        var mfe = 0.0
        var mae = 0.0
        var mfeAt = bars.first().t
        var slHitAt = 0L
        var firstFavorAt = 0L
        var tpHitAt = 0L
        var sameBar = false
        var decided = false          // first touch of target or stop already happened
        val useTarget = sig != Signal.WAIT && record.tpDistance > 0
        var pathHit = 0
        var pathDone = sig == Signal.WAIT || record.atr <= 0
        var hi = Double.NEGATIVE_INFINITY
        var lo = Double.POSITIVE_INFINITY
        for (b in bars) {
            hi = max(hi, b.h)
            lo = min(lo, b.l)
            val fav = if (dirSign > 0) b.h - start else start - b.l
            val adv = if (dirSign > 0) start - b.l else b.h - start
            if (fav > mfe) { mfe = fav; mfeAt = b.t }
            if (adv > mae) mae = adv
            if (firstFavorAt == 0L && fav >= threshold) firstFavorAt = b.t
            if (!pathDone) {
                val f1 = fav >= record.atr
                val a1 = adv >= record.atr
                if (f1 || a1) { pathDone = true; pathHit = if (f1 && a1) 0 else if (f1) 1 else -1 }
            }
            if (useTarget) {
                // FIRST TOUCH decides, like a real trade: target first = win (you are already out with profit),
                // stop first = loss. Both inside the same bar: order unknown -> counted as a loss (cautious).
                if (!decided) {
                    val hitTp = fav >= record.tpDistance
                    val hitSl = record.slDistance > 0 && adv >= record.slDistance
                    if (hitTp && hitSl) { slHitAt = b.t; sameBar = true; decided = true }
                    else if (hitTp) { tpHitAt = b.t; decided = true }
                    else if (hitSl) { slHitAt = b.t; decided = true }
                }
            } else if (sig != Signal.WAIT && slHitAt == 0L && record.slDistance > 0 && adv >= record.slDistance) slHitAt = b.t
        }
        val realizedRange = hi - lo
        val dirScore = directionScore(sig, move, threshold)

        val outcome = when {
            sig == Signal.WAIT -> if (abs(move) < 2 * threshold) LedgerOutcome.WAIT_FLAT else LedgerOutcome.WAIT_MISSED
            tpHitAt != 0L -> LedgerOutcome.CORRECT
            slHitAt != 0L -> LedgerOutcome.INVALIDATED
            dirScore == 1 -> LedgerOutcome.CORRECT
            dirScore == 0 -> LedgerOutcome.INCORRECT
            else -> LedgerOutcome.SIDEWAYS
        }

        // ---------- forensics (only for failures, only measurable evidence)
        val tags = ArrayList<String>()
        val weights = LinkedHashMap<String, Int>()
        val warnings = ArrayList<String>()
        var counterfactual = ""
        if (outcome == LedgerOutcome.INCORRECT || outcome == LedgerOutcome.INVALIDATED) {
            for ((key, s) in record.sources) {
                if (key.startsWith("grp:") || key.startsWith("eng:") || key.startsWith("bot:") || key == "mtf:higher") {
                    if (opposite(s, sig) && directionScore(s, move, threshold) == 1) warnings.add(key)
                }
            }
            val groupWarnings = warnings.count { it.startsWith("grp:") }
            if (groupWarnings > 0) { tags.add("MISSED_CONTRADICTION"); weights["MISSED_CONTRADICTION"] = 1 + groupWarnings }
            val htf = record.sources["mtf:higher"]
            if (htf != null && opposite(htf, sig)) { tags.add("TIMEFRAME_CONFLICT"); weights["TIMEFRAME_CONFLICT"] = 2 }
            if (record.newsActive) { tags.add("NEWS_SHOCK"); weights["NEWS_SHOCK"] = 2 }
            if (record.regime == "RANGE") { tags.add("RANGE_WHIPSAW"); weights["RANGE_WHIPSAW"] = 1 }
            if (record.confidence < 70) { tags.add("INSUFFICIENT_EDGE"); weights["INSUFFICIENT_EDGE"] = 1 }
            if (record.confidence >= 80) { tags.add("OVERCONFIDENCE"); weights["OVERCONFIDENCE"] = 1 }
            if (firstFavorAt != 0L) { tags.add("FAKE_MOVE_REVERSAL"); weights["FAKE_MOVE_REVERSAL"] = 2 }
            if (record.atr > 0 && realizedRange > 3 * record.atr) { tags.add("VOLATILITY_EXPANSION"); weights["VOLATILITY_EXPANSION"] = 1 }
            if (outcome == LedgerOutcome.INVALIDATED && directionScore(sig, move, threshold) != 0) {
                tags.add("STOP_HUNT"); weights["STOP_HUNT"] = 2
            }
            if (tags.isEmpty()) { tags.add("UNKNOWN"); weights["UNKNOWN"] = 1 }
            counterfactual = when {
                warnings.isNotEmpty() -> "If the app had obeyed ${prettySource(warnings.first())} (it pointed the other way) and shown WAIT, this loss would have been avoided."
                tags.contains("TIMEFRAME_CONFLICT") -> "If predictions against the higher timeframe were blocked, this loss would have been avoided."
                else -> "No recorded signal warned against this prediction. No simple rule would have avoided it."
            }
        }
        val attribution = normalizeTo100(weights)

        // ---------- timeline replay
        val tl = ArrayList<Pair<Long, String>>()
        tl.add(record.createdAt to "Prediction ${sig.name} at ${fmt(start)} (${path.source}), confidence ${record.confidence}%")
        if (sig != Signal.WAIT) {
            if (firstFavorAt != 0L) tl.add(firstFavorAt to "Moved ${fmt(threshold)} in predicted direction")
            if (mfe > 0) tl.add(mfeAt to "Best point: +${fmt(mfe)} in favour")
            if (tpHitAt != 0L) tl.add(tpHitAt to "Target 1 (${fmt(record.tpDistance)}) reached before the stop -> win")
            if (slHitAt != 0L) tl.add(slHitAt to "Stop-loss distance (${fmt(record.slDistance)}) hit" + if (sameBar) " (target and stop inside the same bar: counted as stop)" else "")
        }
        tl.add(record.expiresAt to "Expiry: ${fmt(end)} (move ${signed(move)}) -> ${outcome.name}")
        tl.sortBy { it.first }

        return LedgerResult(
            id = record.id, checkedAt = now, outcome = outcome, verifySource = path.source,
            startPx = start, endPx = end, move = move, threshold = threshold, mfe = mfe, mae = mae,
            slHitAt = slHitAt, realizedRange = realizedRange, tags = tags, attribution = attribution,
            warnings = warnings, counterfactual = counterfactual, timeline = tl, tpHitAt = tpHitAt, pathHit = pathHit
        )
    }

    fun emptyResult(record: LedgerRecord, now: Long, outcome: LedgerOutcome, source: String): LedgerResult = LedgerResult(
        id = record.id, checkedAt = now, outcome = outcome, verifySource = source, startPx = 0.0, endPx = 0.0,
        move = 0.0, threshold = max(0.25 * record.atr, 0.30), mfe = 0.0, mae = 0.0, slHitAt = 0L, realizedRange = 0.0,
        tags = emptyList(), attribution = emptyMap(), warnings = emptyList(), counterfactual = "", timeline = emptyList()
    )

    fun normalizeTo100(weights: Map<String, Int>): Map<String, Int> {
        val total = weights.values.sum()
        if (total <= 0) return emptyMap()
        val out = LinkedHashMap<String, Int>()
        var used = 0
        val entries = weights.entries.sortedByDescending { it.value }
        entries.forEachIndexed { i, e ->
            val pct = if (i == entries.lastIndex) 100 - used else Math.round(e.value * 100.0 / total).toInt()
            out[e.key] = pct
            used += pct
        }
        return out
    }

    fun prettySource(key: String): String = when {
        key.startsWith("grp:") -> "the " + key.removePrefix("grp:").replaceFirstChar { it.uppercase() } + " pillar"
        key.startsWith("eng:") -> "rule engine " + key.removePrefix("eng:")
        key.startsWith("bot:") -> "bot " + key.removePrefix("bot:")
        key == "mtf:higher" -> "the higher timeframe"
        else -> key
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun signed(v: Double) = String.format(Locale.US, "%+.2f", v)
}
