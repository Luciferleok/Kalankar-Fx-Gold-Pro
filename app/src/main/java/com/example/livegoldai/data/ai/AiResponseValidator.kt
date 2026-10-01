package com.example.livegoldai.data.ai

import com.example.livegoldai.data.MiniJson
import com.example.livegoldai.model.AiVote
import com.example.livegoldai.model.Signal
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Schema validation + hallucination defence.
 *  - the answer must be one JSON object with the agreed keys and sane numbers
 *  - every number the model says it relied on ("cited_values") is checked against the snapshot;
 *    a value that does not match is a FACT CONFLICT and that claim is dropped;
 *    if half or more of the checked claims conflict, the whole vote is excluded.
 */
object AiResponseValidator {

    data class Outcome(
        val vote: AiVote?,
        val status: String,          // ONLINE / SCHEMA_INVALID / FACT_CONFLICT
        val detail: String,
        val factConflicts: List<String>,
        val checkedFacts: Int
    )

    fun validate(text: String, snapshot: MarketSnapshot, debate: Boolean = false): Outcome {
        val obj = extractObject(text) ?: return bad("Answer was not a JSON object")
        val dir = parseDirection(obj["direction"]) ?: return bad("Missing or unknown direction")

        var bull = prob(obj["bullish_probability"]) ?: return bad("Missing bullish_probability")
        var bear = prob(obj["bearish_probability"]) ?: return bad("Missing bearish_probability")
        var side = prob(obj["sideways_probability"]) ?: return bad("Missing sideways_probability")
        // accept 0-1 fractions
        if (bull <= 1.0 && bear <= 1.0 && side <= 1.0 && bull + bear + side in 0.9..1.1) { bull *= 100; bear *= 100; side *= 100 }
        if (listOf(bull, bear, side).any { it < 0 || it > 100 }) return bad("Probability outside 0-100")
        val sum = bull + bear + side
        if (sum < 90 || sum > 110) return bad(String.format(Locale.US, "Probabilities sum to %.0f, not 100", sum))
        val p = normalise(bull, bear, side)

        var conf = prob(obj["confidence"]) ?: return bad("Missing confidence")
        if (conf <= 1.0) conf *= 100
        if (conf < 0 || conf > 100) return bad("Confidence outside 0-100")

        if (dir == Signal.BUY && p[0] < p[1]) return bad("Says BUY but bearish probability is higher")
        if (dir == Signal.SELL && p[1] < p[0]) return bad("Says SELL but bullish probability is higher")

        // ---- fact check
        val conflicts = ArrayList<String>()
        var checked = 0
        (obj["cited_values"] as? List<*>)?.forEach { item ->
            val m = item as? Map<*, *> ?: return@forEach
            val name = (m["name"] as? String)?.trim() ?: return@forEach
            val said = when (val v = m["value"]) {
                is Number -> v.toDouble()
                is String -> v.replace("$", "").replace(",", "").replace("%", "").trim().toDoubleOrNull()
                else -> null
            } ?: return@forEach
            val truth = lookup(snapshot.facts, name) ?: return@forEach
            checked++
            if (abs(said - truth) > tolerance(truth)) {
                conflicts.add("$name: said ${fmt(said)}, data ${fmt(truth)}")
            }
        }
        val vote = AiVote(
            direction = dir,
            bullish = p[0], bearish = p[1], sideways = p[2],
            confidence = conf.roundToInt(),
            regime = (obj["regime_assessment"] as? String)?.take(120) ?: "",
            evidence = strings(obj["strongest_evidence"]),
            contradictions = strings(obj["contradictions"]),
            invalidations = strings(obj["invalidation_conditions"]),
            uncertainty = strings(obj["uncertainty_sources"]),
            dataConcerns = strings(obj["data_concerns"], 4),
            proveWrong = if (debate) strings(obj["would_prove_me_wrong"]) else emptyList()
        )
        if (conflicts.isNotEmpty() && conflicts.size * 2 >= checked) {
            return Outcome(vote, AiStatus.FACT_CONFLICT, "${conflicts.size} of $checked cited values do not match the market data", conflicts, checked)
        }
        val detail = when {
            conflicts.isNotEmpty() -> "${conflicts.size} wrong value(s) dropped, $checked checked"
            checked == 0 -> "Valid answer (no values cited to check)"
            else -> "Valid answer, $checked cited value(s) match the data"
        }
        return Outcome(vote, AiStatus.ONLINE, detail, conflicts, checked)
    }

    // ------------------------------------------------------------------ helpers

    private fun bad(why: String) = Outcome(null, AiStatus.SCHEMA_INVALID, why, emptyList(), 0)

    /** Finds the JSON object inside a reply that may be wrapped in ``` fences or text. */
    @Suppress("UNCHECKED_CAST")
    fun extractObject(text: String): Map<String, Any?>? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try { MiniJson.parse(text.substring(start, end + 1)) as? Map<String, Any?> } catch (_: Exception) { null }
    }

    fun parseDirection(v: Any?): Signal? = when ((v as? String)?.trim()?.uppercase(Locale.US)) {
        "BUY", "LONG", "BULLISH", "UP" -> Signal.BUY
        "SELL", "SHORT", "BEARISH", "DOWN" -> Signal.SELL
        "WAIT", "HOLD", "NEUTRAL", "SIDEWAYS", "FLAT", "NO TRADE" -> Signal.WAIT
        else -> null
    }

    private fun prob(v: Any?): Double? = when (v) {
        is Number -> v.toDouble()
        is String -> v.replace("%", "").trim().toDoubleOrNull()
        else -> null
    }

    /** Scales three probabilities to integers that sum to exactly 100. */
    fun normalise(a: Double, b: Double, c: Double): IntArray {
        val s = a + b + c
        if (s <= 0) return intArrayOf(0, 0, 100)
        val raw = doubleArrayOf(a / s * 100, b / s * 100, c / s * 100)
        val out = IntArray(3) { raw[it].roundToInt() }
        val diff = 100 - out.sum()
        if (diff != 0) {
            val i = (0..2).maxByOrNull { raw[it] } ?: 0
            out[i] += diff
        }
        return out
    }

    private fun strings(v: Any?, max: Int = 3): List<String> =
        (v as? List<*>)?.mapNotNull { (it as? String)?.trim()?.take(160)?.takeIf { s -> s.isNotEmpty() } }?.take(max) ?: emptyList()

    /** Snapshot value for a cited name: exact normalised match, else a single unambiguous prefix match. */
    fun lookup(facts: Map<String, Double>, name: String): Double? {
        val n = MarketSnapshotBuilder.norm(name)
        if (n.length < 2) return null
        facts[n]?.let { return it }
        val hits = facts.entries.filter { (k, _) -> (k.length >= 3 && n.startsWith(k)) || (n.length >= 3 && k.startsWith(n)) }
        return if (hits.size == 1) hits[0].value else null
    }

    /** Price-like values must be within ~0.07%; small values within 1% (min 0.15). */
    fun tolerance(truth: Double): Double {
        val a = abs(truth)
        return if (a >= 500) a * 0.0007 else maxOf(a * 0.01, 0.15)
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)
}
