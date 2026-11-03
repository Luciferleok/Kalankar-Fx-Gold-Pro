package com.example.livegoldai.data

import com.example.livegoldai.model.GoldAnalysisResult

/**
 * V35.3-E: the ledger record that belongs to an analysis = same timeframe, same candle AND same data provider.
 * Before: the Market Brain took any record of the timeframe made in the last 2 minutes, and the AI council any record of
 * the timeframe whose window was still running, so a council on the 10:15 candle could be scored against the 10:00
 * record, and Twelve Data features could be stored under an OANDA record. No exact match = null (UNLINKED research):
 * nothing is attached to the wrong forecast. Old links already written are not changed.
 */
object RecordLink {
    const val UNLINKED = ""

    fun forAnalysis(records: List<LedgerRecord>, a: GoldAnalysisResult): LedgerRecord? {
        val prov = SourceRegistry.identify(a.feed?.source).provider
        if (prov == ProviderId.NONE || a.lastUpdated.isBlank()) return null
        return records.lastOrNull {
            it.interval.equals(a.interval, true) && it.barId == a.lastUpdated && SourceRegistry.identify(it.dataSource).provider == prov
        }
    }

    /** prefix of the AI snapshot id for this timeframe, candle and provider (see MarketSnapshotBuilder) */
    fun snapshotPrefix(a: GoldAnalysisResult): String =
        "S-${a.interval}-${a.lastUpdated.replace(" ", "T")}-${SourceRegistry.identify(a.feed?.source).provider.name}-"

    /**
     * One record per candle (LearningCoordinator): an existing record blocks a new one only for the SAME candle, or (data
     * source switched, candle ids not comparable) when it is younger than ~one candle and not from an OLDER candle.
     * Before: a record made in the last seconds of a candle blocked the next candle for ~0.9 candle.
     */
    fun sameCandle(r: LedgerRecord, interval: String, barId: String, now: Long, ivMin: Int): Boolean {
        if (!r.interval.equals(interval, true)) return false
        if (r.barId == barId) return true
        val older = ShadowLive.barMs(r.barId)?.let { rb -> ShadowLive.barMs(barId)?.let { cb -> rb < cb } } ?: false
        return !older && now - r.createdAt < ivMin * 60_000L * 9 / 10
    }
}
