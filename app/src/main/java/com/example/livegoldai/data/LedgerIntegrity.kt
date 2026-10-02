package com.example.livegoldai.data

/**
 * LEDGER INTEGRITY CHECK
 * Reads the raw ledger file and proves that the history can be trusted:
 * predictions are unique and were never rewritten, every result came after its prediction expired
 * (no future data leaked into a prediction), one prediction per candle, provenance is present.
 * CRITICAL = the accuracy numbers cannot be trusted until this is fixed.
 */
object LedgerIntegrity {

    data class Report(
        val status: String,                 // HEALTHY / WARNING / CRITICAL
        val checkedAt: Long,
        val lines: Int,
        val predictions: Int,
        val results: Int,
        val featureRows: Int,
        val aiVotes: Int,
        val findings: List<String>,         // problems, most serious first
        val passed: List<String>            // checks that passed
    )

    @Suppress("UNCHECKED_CAST")
    fun check(rawLines: List<String>, now: Long = System.currentTimeMillis()): Report {
        val critical = ArrayList<String>()
        val warn = ArrayList<String>()
        val passed = ArrayList<String>()

        val recLines = LinkedHashMap<String, String>()
        val recs = LinkedHashMap<String, Map<String, Any?>>()
        val outs = ArrayList<Map<String, Any?>>()
        val feats = ArrayList<Map<String, Any?>>()
        val aiv = ArrayList<Map<String, Any?>>()
        var unreadable = 0
        var rewritten = 0
        var duplicateSame = 0
        var n = 0
        for (line in rawLines) {
            if (line.isBlank()) continue
            n++
            val m = try { MiniJson.parse(line) as? Map<String, Any?> } catch (_: Exception) { null }
            if (m == null) { unreadable++; continue }
            when (m.str("t")) {
                "rec" -> {
                    val id = m.str("id")
                    val before = recLines[id]
                    if (before == null) { recLines[id] = line; recs[id] = m }
                    else if (before != line) rewritten++ else duplicateSame++
                }
                "out" -> outs.add(m)
                "feat" -> feats.add(m)
                "aiv" -> aiv.add(m)
            }
        }

        // 1. unique + immutable predictions
        if (rewritten > 0) critical.add("$rewritten prediction(s) appear twice with different content: an original prediction was changed")
        else passed.add("Original predictions never rewritten")
        if (duplicateSame > 0) warn.add("$duplicateSame prediction line(s) written twice (identical copies)")
        else passed.add("Prediction IDs unique")

        // 2. timestamps
        var badTime = 0
        for (r in recs.values) {
            val c = r.long("c"); val e = r.long("e")
            if (c <= 0 || e <= c || c > now + 5 * 60_000L) badTime++
        }
        if (badTime > 0) critical.add("$badTime prediction(s) with impossible timestamps") else passed.add("Timestamps valid")

        // 3. results only after expiry, and only for existing predictions (no future data, no orphan results)
        var early = 0; var orphan = 0; var multi = 0
        val seenOut = HashSet<String>()
        for (o in outs) {
            val id = o.str("id")
            val r = recs[id]
            if (r == null) { orphan++; continue }
            if (!seenOut.add(id)) multi++
            val outcome = o.str("o")
            if (outcome != "MARKET_CLOSED" && o.long("at") < r.long("e")) early++
        }
        if (early > 0) critical.add("$early result(s) were written before the prediction expired: possible look-ahead")
        else passed.add("Every result was checked after its prediction expired")
        if (orphan > 0) warn.add("$orphan result(s) refer to a prediction that is not in the file")
        if (multi > 0) warn.add("$multi prediction(s) have more than one result line (the last one is used)")
        if (orphan == 0 && multi == 0) passed.add("One result per prediction")

        // 4. one prediction per candle
        val perCandle = HashMap<String, Int>()
        for (r in recs.values) { val k = r.str("iv") + "|" + r.str("bar"); perCandle[k] = (perCandle[k] ?: 0) + 1 }
        val dupCandles = perCandle.values.count { it > 1 }
        if (dupCandles > 0) warn.add("$dupCandles candle(s) have more than one prediction") else passed.add("One prediction per candle")

        // 5. provenance (records written by V11 or later must carry source + snapshot + versions)
        val v11 = recs.values.filter { it.str("ev").isNotEmpty() }
        val noProv = v11.count { it.str("ds").isEmpty() || it.str("sid").isEmpty() || it.str("mv").isEmpty() }
        val legacy = recs.size - v11.size
        if (noProv > 0) warn.add("$noProv new prediction(s) without full provenance")
        else if (v11.isNotEmpty()) passed.add("Provenance present on all ${v11.size} V11 predictions")
        if (legacy > 0) passed.add("$legacy older prediction(s) kept without provenance (written before V11)")

        // 6. feature rows: belong to a prediction and were taken at prediction time (never later)
        var featOrphan = 0; var featLate = 0
        for (f in feats) {
            val r = recs[f.str("rid")]
            if (r == null) { featOrphan++; continue }
            if (f.long("at") > r.long("c") + 60_000L) featLate++
        }
        if (featLate > 0) critical.add("$featLate feature snapshot(s) are dated after their prediction: possible future data in features")
        else if (feats.isNotEmpty()) passed.add("Feature snapshots taken at prediction time")
        if (featOrphan > 0) warn.add("$featOrphan feature snapshot(s) without a prediction")

        // 7. AI votes belong to an existing prediction when linked
        val aiOrphan = aiv.count { val rid = it.str("rid"); rid.isNotEmpty() && !recs.containsKey(rid) }
        if (aiOrphan > 0) warn.add("$aiOrphan AI vote(s) linked to a missing prediction")

        if (unreadable > 0) warn.add("$unreadable unreadable line(s) skipped")
        else passed.add("All lines readable")

        val status = if (critical.isNotEmpty()) "CRITICAL" else if (warn.isNotEmpty()) "WARNING" else "HEALTHY"
        return Report(status, now, n, recs.size, seenOut.size, feats.size, aiv.size, critical + warn, passed)
    }
}
