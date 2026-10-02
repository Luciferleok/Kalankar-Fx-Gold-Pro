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

    fun build(a: GoldAnalysisResult, state: LedgerState, stats: LearningCoordinator.Stats, now: Long = System.currentTimeMillis()): LedgerPulse {
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
            else -> { status = "RECORDING"; detail = "One prediction per candle for each timeframe you open, while the app is open" }
        }
        val lastCheck = when {
            stats.lastVerifyAttemptAt == 0L && pending.isEmpty() -> "Nothing to check"
            stats.lastVerifyAttemptAt == 0L -> "Not yet: no prediction has expired since the app was opened"
            stats.consecutiveFetchFailures > 0 -> "${PredictionLedger.utcLabel(stats.lastVerifyAttemptAt)} • price history not available, ${stats.consecutiveFetchFailures} failed attempt(s), will retry"
            else -> "${PredictionLedger.utcLabel(stats.lastVerifyAttemptAt)} • ${stats.lastVerifiedCount} checked then, ${stats.totalVerifiedThisSession} since app start"
        }
        return LedgerPulse(
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
            fileSize = if (stats.fileBytes >= 0) String.format(Locale.US, "%.1f KB", stats.fileBytes / 1024.0) else "unknown"
        )
    }
}
