package com.example.livegoldai.data

import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.LedgerPulse
import java.util.Locale

/** Turns the real ledger state into a plain "is it running?" summary. */
object LedgerPulseBuilder {

    private fun ago(ms: Long): String = when {
        ms < 0 -> "--"
        ms < 60_000 -> "${ms / 1000}s ago"
        ms < 3_600_000 -> "${ms / 60_000}m ago"
        ms < 86_400_000 -> "${ms / 3_600_000}h ${(ms % 3_600_000) / 60_000}m ago"
        else -> "${ms / 86_400_000}d ago"
    }

    private fun inTime(ms: Long): String = when {
        ms <= 0 -> "now"
        ms < 3_600_000 -> "in ${ms / 60_000 + 1}m"
        else -> "in ${ms / 3_600_000}h ${(ms % 3_600_000) / 60_000}m"
    }

    fun build(
        a: GoldAnalysisResult, state: LedgerState, stats: LearningCoordinator.Stats, now: Long = System.currentTimeMillis(),
        bgEnabled: Boolean = false, bg: BackgroundRecorder.TickReport? = null, integrity: LedgerIntegrity.Report? = null
    ): LedgerPulse {
        val last = state.records.lastOrNull()
        val first = state.records.firstOrNull()
        val pending = state.records.filter { state.resultOf(it.id) == null }
        val next = pending.minByOrNull { it.expiresAt }
        val closed = PredictionLedger.isMarketClosed(now)
        val status: String
        val detail: String
        when {
            !stats.lastWriteOk -> { status = "WRITE_ERROR"; detail = "Could not write the ledger file: ${stats.lastWriteError}" }
            a.isSimulatedFallback -> { status = "NOT_RECORDING"; detail = "No live price: offline demo data is never recorded" }
            closed -> { status = "MARKET_CLOSED"; detail = "Gold market is closed (weekend): nothing is recorded until it opens" }
            else -> { status = "RECORDING"; detail = if (bgEnabled) "One prediction per candle on every timeframe (5M to 1D), also in the background" else "One prediction per candle for each timeframe you open, while the app is open" }
        }
        val lastCheck = when {
            stats.lastVerifyAttemptAt == 0L && pending.isEmpty() -> "Nothing to check"
            stats.lastVerifyAttemptAt == 0L -> "Not yet: no prediction has expired since the app was opened"
            stats.consecutiveFetchFailures > 0 -> "${PredictionLedger.utcLabel(stats.lastVerifyAttemptAt)} • price history not available, ${stats.consecutiveFetchFailures} failed attempt(s), will retry"
            else -> "${PredictionLedger.utcLabel(stats.lastVerifyAttemptAt)} • ${stats.lastVerifiedCount} checked then, ${stats.totalVerifiedThisSession} since app start"
        }
        // ---- per timeframe
        val order = listOf("5m", "15m", "30m", "1h", "4h", "1d")
        val ivs = (order + state.records.map { it.interval }).distinct().filter { iv -> state.records.any { it.interval == iv } }
        val byInterval = ivs.map { iv ->
            var ok = 0; var bad = 0; var flat = 0; var active = 0; var unsure = 0
            // candle coverage: how many of the last 24h of open-market candles were actually recorded
            val ivMs = RealityEngine.intervalMinutes(iv) * 60_000L
            val firstAt = state.records.firstOrNull { it.interval == iv }?.createdAt ?: now
            val winStart = maxOf(now - 86_400_000L, firstAt)
            var expected = 0
            var t = (winStart / ivMs) * ivMs
            while (t <= now - ivMs) { if (t >= winStart && !PredictionLedger.isMarketClosed(t)) expected++; t += ivMs }
            val got = state.records.count { it.interval == iv && it.createdAt >= winStart }
            val coverage = if (expected < 3) "" else " • 24h coverage ${(100.0 * minOf(got, expected) / expected).toInt()}%"
            for (r in state.records) {
                if (r.interval != iv) continue
                when (state.resultOf(r.id)?.outcome) {
                    null -> active++
                    LedgerOutcome.VERIFICATION_UNCERTAIN -> unsure++
                    LedgerOutcome.CORRECT -> ok++
                    LedgerOutcome.INCORRECT, LedgerOutcome.INVALIDATED -> bad++
                    LedgerOutcome.SIDEWAYS, LedgerOutcome.WAIT_FLAT, LedgerOutcome.WAIT_MISSED -> flat++
                    else -> {}
                }
            }
            val acc = if (ok + bad >= 20) " • ${(100.0 * ok / (ok + bad)).toInt()}%" else ""
            com.example.livegoldai.model.LabelStat(iv.uppercase(Locale.US), "✓$ok  ✗$bad  ↔$flat" + (if (unsure > 0) "  ?$unsure" else "") + "  • $active active$acc$coverage")
        }
        val recById = state.records.associateBy { it.id }
        val recent = state.results.values.sortedByDescending { it.checkedAt }.take(12).mapNotNull { res ->
            val r = recById[res.id] ?: return@mapNotNull null
            com.example.livegoldai.model.LabelStat(
                "${PredictionLedger.utcLabel(r.createdAt, true)} • ${r.interval}",
                "${r.finalSignal.name} ${BackgroundRecorder.mark(res.outcome)}  " + String.format(Locale.US, "%+.2f", res.move) + when {
                    res.tpHitAt != 0L -> " • target"
                    res.outcome == LedgerOutcome.INVALIDATED -> " • stop"
                    else -> ""
                }
            )
        }
        val background = when {
            !bgEnabled -> "OFF: predictions are recorded only while the app is open"
            bg == null -> "ON: waiting for the first background run"
            else -> "ON • last run ${PredictionLedger.utcLabel(bg.at)} (${ago(now - bg.at)}) • took ${bg.durationMs} ms (avg ${bg.avgDurationMs}) • ${bg.runsToday} runs today" +
                " • waiting to be checked ${bg.waitingToCheck} • price failures today ${bg.providerFailuresToday}" +
                (if (bg.recordedNow.isNotEmpty()) " • recorded ${bg.recordedNow.joinToString(", ")}" else "") +
                (if (bg.problems.isNotEmpty()) " • ⚠ ${bg.problems.joinToString(", ") { it.title }}" else "")
        }
        // ---- diagnosis: what kind of right / wrong (all counted from real results)
        val diagnosis = ArrayList<com.example.livegoldai.model.LabelStat>()
        run {
            var dirWrong = 0; var stopped = 0; var stoppedButRight = 0; var target = 0; var endRight = 0
            var buyOk = 0; var buyBad = 0; var sellOk = 0; var sellBad = 0
            var slSum = 0.0; var maeSum = 0.0; var nSl = 0
            val perIv = LinkedHashMap<String, IntArray>()
            for (r in state.records) {
                val res = state.resultOf(r.id) ?: continue
                val ok = res.outcome == LedgerOutcome.CORRECT
                val bad = res.outcome == LedgerOutcome.INCORRECT || res.outcome == LedgerOutcome.INVALIDATED
                if (!ok && !bad) continue
                if (r.finalSignal == com.example.livegoldai.model.Signal.BUY) { if (ok) buyOk++ else buyBad++ } else { if (ok) sellOk++ else sellBad++ }
                when {
                    ok && res.tpHitAt != 0L -> target++
                    ok -> endRight++
                    res.outcome == LedgerOutcome.INCORRECT -> dirWrong++
                    else -> { stopped++; if (PredictionLedger.directionScore(r.finalSignal, res.move, res.threshold) == 1) stoppedButRight++ }
                }
                if (r.slDistance > 0) { slSum += r.slDistance; maeSum += res.mae; nSl++ }
            }
            val decided = target + endRight + dirWrong + stopped
            if (decided > 0) {
                diagnosis.add(com.example.livegoldai.model.LabelStat("Right", "$target reached target first • $endRight right at the end"))
                diagnosis.add(com.example.livegoldai.model.LabelStat("Wrong", "$dirWrong wrong direction at the end • $stopped stop-loss touched" + if (stoppedButRight > 0) " ($stoppedButRight of them ended the right way)" else ""))
                diagnosis.add(com.example.livegoldai.model.LabelStat("BUY calls", "✓$buyOk  ✗$buyBad"))
                diagnosis.add(com.example.livegoldai.model.LabelStat("SELL calls", "✓$sellOk  ✗$sellBad"))
                if (nSl > 0) diagnosis.add(com.example.livegoldai.model.LabelStat("Stop distance vs move against", String.format(Locale.US, "stop %.2f avg • price went %.2f against on avg", slSum / nSl, maeSum / nSl)))
                // what the market did while these predictions were running
                val timed = state.results.values.filter { it.startPx > 0 && recById[it.id] != null }.sortedBy { recById[it.id]!!.createdAt }
                if (timed.size >= 2) {
                    val first = timed.first(); val last = timed.last()
                    val hrs = (recById[last.id]!!.expiresAt - recById[first.id]!!.createdAt) / 3_600_000.0
                    diagnosis.add(com.example.livegoldai.model.LabelStat("Market in this period", String.format(Locale.US, "%+.2f in %.1f h (%.2f → %.2f)", last.endPx - first.startPx, hrs, first.startPx, last.endPx)))
                }
                if (decided < 50) diagnosis.add(com.example.livegoldai.model.LabelStat("Sample", "$decided decided calls: too few to judge the engine (many come from the same market move)"))
            }
        }

        // ---- how results were verified
        var same = 0; var proxy = 0; var unsureAll = 0; var legacyV = 0
        for (res in state.results.values) {
            when {
                res.outcome == LedgerOutcome.VERIFICATION_UNCERTAIN -> unsureAll++
                res.sourceMatch == "SAME" -> same++
                res.sourceMatch == "PROXY" -> proxy++
                PredictionLedger.reliable(res.outcome) -> legacyV++
            }
        }
        val verification = "same instrument $same • proxy (decisive) $proxy • too close to call $unsureAll" + if (legacyV > 0) " • older, source not stored $legacyV" else ""
        val audit = PredictionAudit.build(state, integrity?.status ?: "")
        val lab = ResearchLab.build(state, a.interval)
        return LedgerPulse(
            modelHealth = audit.health,
            modelHealthLine = audit.headline,
            audit = audit.lines + lab.lines,
            stability = lab.stability,
            expectedMove = lab.expectedMove,
            coverage = lab.coverage,
            directionPct = if (audit.decided == 0) -1 else audit.directionPct.toInt(),
            directionN = audit.decided,
            directionMinN = PredictionAudit.MIN_N,
            status = status,
            statusDetail = detail,
            recorded = stats.records,
            checked = stats.results,
            active = stats.pending,
            overdue = stats.overdue,
            lastRecorded = if (last == null) "Nothing recorded yet" else
                "${PredictionLedger.utcLabel(last.createdAt, true)} • ${last.interval} • ${last.finalSignal.name} (${ago(now - last.createdAt)})",
            nextDue = if (next == null) "No active prediction" else
                "${PredictionLedger.utcLabel(next.expiresAt, true)} (${inTime(next.expiresAt + LearningCoordinator.SETTLE_MS - now)}) • ${next.interval}",
            lastCheck = lastCheck,
            ledgerStarted = if (first == null) "--" else "${PredictionLedger.utcLabel(first.createdAt, true)} (${ago(now - first.createdAt)})",
            fileSize = if (stats.fileBytes >= 0) String.format(Locale.US, "%.1f KB", stats.fileBytes / 1024.0) else "unknown",
            byInterval = byInterval,
            recent = recent,
            background = background,
            verification = verification,
            integrity = integrity?.status ?: "",
            integrityFindings = integrity?.findings ?: emptyList(),
            integrityPassed = integrity?.passed ?: emptyList(),
            diagnosis = diagnosis
        )
    }
}
