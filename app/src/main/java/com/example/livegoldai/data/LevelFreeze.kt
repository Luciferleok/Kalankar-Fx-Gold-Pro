package com.example.livegoldai.data

import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import com.example.livegoldai.model.TradeSetup
import java.util.Locale

/**
 * V33.1R.2: one plan window = one set of levels.
 *
 * Root cause (phone screenshots 30 Oct 19:25): PlanStamp fixes the snapshot id and the end time per candle, but
 * TechnicalEngine rebuilds entry / stop / targets from the LIVE price on every refresh of the still-forming candle. Two
 * cards of the same snapshot showed levels exactly 2.17 apart, the same amount the price had moved, while the screen said
 * "fixed".
 *
 * The engine is not changed. After stamping, the levels first seen for (snapshot id + side) are kept and shown for the
 * rest of that window. This is the same moment the ledger records the call (one record per candle, first seen). If the
 * side changes, or a new candle starts, the new levels are taken. Live indicator values are not frozen: only plan levels,
 * their pips, the risk/reward and the texts that quote those levels.
 *
 * V33.1R.4-d: the kept levels are also written to LevelFreezeStore (app storage, not the ledger), so an app restart
 * inside a window shows the same levels.
 */
object LevelFreeze {

    /** V34.0-fix1: windows kept at most (9+ timeframes x 2 providers x 2 sides fit with room to spare) */
    const val CAP = 64
    private const val NO_END_KEEP_MS = 48 * 3_600_000L
    /** end of a kept window: its fixed expiry, or 48 h after it was fixed when no expiry was stamped */
    fun endOf(f: Frozen): Long = if (f.setup.expiresAtMs > 0L) f.setup.expiresAtMs else f.fixedAtMs + NO_END_KEEP_MS

    data class Frozen(val key: String, val setup: TradeSetup, val fixedAtMs: Long, val botSlPips: Double?, val botTpPips: Double?,
                      val interval: String = "", val barId: String = "")

    /**
     * null when there is nothing to keep (WAIT, no levels, no snapshot id).
     * V33.1R.4-c1: the provider and instrument are part of the key, so levels of one feed never carry over to another
     * feed on the same candle (before: "snapshotId|side" only).
     */
    fun key(a: GoldAnalysisResult): String? {
        val t = a.tradeSetup
        if (t.signal == Signal.WAIT || t.snapshotId.isBlank()) return null
        if (t.entryPrice <= 0.0 || t.stopLoss <= 0.0 || t.takeProfit1 <= 0.0) return null
        return "${t.snapshotId}|${t.signal.name}|${t.dataSource}|${t.instrument}"
    }

    private fun f2(x: Double) = String.format(Locale.US, "%.2f", x)

    /** Replaces, in one pass, each live level as the engine printed it ("%.2f") by the kept level. Other numbers stay. */
    fun requote(text: String, map: Map<String, String>): String {
        if (text.isEmpty() || map.isEmpty()) return text
        return Regex("\\d+\\.\\d{2}(?!\\d)").replace(text) { m -> map[m.value] ?: m.value }
    }

    /**
     * Pure: returns the analysis to show and the store entry to keep. [store] maps key -> Frozen; the caller owns it.
     * Call after PlanStamp.stamp and before PresentationBuilder.build.
     */
    /** V34.2: the levels recorded in the ledger for this call (same provider, candle and side). */
    data class Seed(val entry: Double, val stop: Double, val tp1: Double, val tp2: Double)

    /** V34.2: the ledger's plan levels for [a], or null (no v2 record of this provider, candle and side). */
    fun seedFrom(state: LedgerState?, a: GoldAnalysisResult): Seed? {
        val t = a.tradeSetup
        if (state == null || t.signal == Signal.WAIT) return null
        val prov = SourceRegistry.identify(a.feed?.source).provider
        if (prov == ProviderId.NONE) return null
        val r = state.records.lastOrNull {
            it.interval.equals(a.interval, true) && it.barId == a.lastUpdated && it.planMethod.isNotEmpty() &&
                it.finalSignal == t.signal && SourceRegistry.identify(it.dataSource).provider == prov
        } ?: return null
        val ok = listOf(r.planEntry, r.planStop, r.planTp1, r.planTp2).all { it.isFinite() && it > 0.0 }
        return if (ok) Seed(r.planEntry, r.planStop, r.planTp1, r.planTp2) else null
    }

    fun apply(a: GoldAnalysisResult, store: MutableMap<String, Frozen>, now: Long, seed: Seed? = null): GoldAnalysisResult {
        val k = key(a) ?: return a
        var f = store[k]
        if (f == null) {
            // V33.1R.4-c1: an older candle arriving late (a slow load) must not evict the current window or be kept itself
            val iv = a.interval.lowercase(); val bar = a.lastUpdated
            if (store.values.any { it.interval == iv && it.barId > bar }) return a
            // drop only OLDER windows of the same timeframe (before: matched by a wrong prefix and could drop a newer one)
            store.values.removeAll { (it.interval == iv && it.barId < bar) }
            // V34.0-fix1: retention by expiry, not by count. Expired windows go first; an unexpired window is dropped only
            // above CAP (before: a cap of 16 removed the oldest window even while it was still running).
            store.values.removeAll { endOf(it) <= now }
            while (store.size >= CAP) store.values.minByOrNull { endOf(it) }?.let { old -> store.remove(old.key) } ?: break
            val bot = a.multiBotEnsemble?.allBots?.firstOrNull()
            val live = a.tradeSetup
            if (seed == null || (seed.entry == live.entryPrice && seed.stop == live.stopLoss && seed.tp1 == live.takeProfit1 && seed.tp2 == live.takeProfit2)) {
                store[k] = Frozen(k, live, now, bot?.stopLossPips, bot?.targetPips, iv, bar)
                return withFixedAt(a, now)
            }
            // V34.2: the ledger already holds this call's levels (e.g. recorded by the background recorder earlier in the
            // candle): show exactly those, so the screen and the plan check use the same numbers
            val sm = linkedMapOf<String, String>()
            listOf(live.entryPrice to seed.entry, live.stopLoss to seed.stop, live.takeProfit1 to seed.tp1, live.takeProfit2 to seed.tp2)
                .forEach { (l, v) -> if (f2(l) != f2(v)) sm[f2(l)] = f2(v) }
            val seeded = live.copy(
                entryPrice = seed.entry, stopLoss = seed.stop, takeProfit1 = seed.tp1, takeProfit2 = seed.tp2,
                stopLossPips = kotlin.math.abs(seed.entry - seed.stop) * 10.0, takeProfit1Pips = kotlin.math.abs(seed.tp1 - seed.entry) * 10.0,
                takeProfit2Pips = kotlin.math.abs(seed.tp2 - seed.entry) * 10.0,
                strategyNote = requote(live.strategyNote, sm), strategyNoteHindi = requote(live.strategyNoteHindi, sm), strategyNoteMarathi = requote(live.strategyNoteMarathi, sm)
            )
            f = Frozen(k, seeded, now, kotlin.math.abs(seed.entry - seed.stop) * 10.0, kotlin.math.abs(seed.tp1 - seed.entry) * 10.0, iv, bar)
            store[k] = f
        }
        val s = f.setup
        val t = a.tradeSetup
        val map = linkedMapOf<String, String>()
        listOf(t.entryPrice to s.entryPrice, t.stopLoss to s.stopLoss, t.takeProfit1 to s.takeProfit1, t.takeProfit2 to s.takeProfit2)
            .forEach { (live, kept) -> if (f2(live) != f2(kept)) map[f2(live)] = f2(kept) }

        val ts = t.copy(
            entryPrice = s.entryPrice, stopLoss = s.stopLoss, takeProfit1 = s.takeProfit1, takeProfit2 = s.takeProfit2,
            stopLossPips = s.stopLossPips, takeProfit1Pips = s.takeProfit1Pips, takeProfit2Pips = s.takeProfit2Pips,
            strategyNote = s.strategyNote, strategyNoteHindi = s.strategyNoteHindi, strategyNoteMarathi = s.strategyNoteMarathi,
            riskRewardRatio = RiskMath.label(s.entryPrice, s.stopLoss, s.takeProfit1, s.takeProfit2, t.signal),
            levelsFixedAtMs = f.fixedAtMs
        )
        val q = a.quantBotSignal?.let { b ->
            b.copy(
                entryPrice = if (b.isTradeActive) s.entryPrice else b.entryPrice,
                stopLoss = s.stopLoss, takeProfit1 = s.takeProfit1, takeProfit2 = s.takeProfit2,
                pipsRisk = kotlin.math.abs(s.entryPrice - s.stopLoss) * 10.0,
                pipsRewardTp1 = kotlin.math.abs(s.takeProfit1 - s.entryPrice) * 10.0,
                pipsRewardTp2 = kotlin.math.abs(s.takeProfit2 - s.entryPrice) * 10.0,
                orderType = requote(b.orderType, map),
                executionCommandEng = requote(b.executionCommandEng, map)
            )
        }
        val e = a.multiBotEnsemble?.let { m ->
            m.copy(
                recommendedEntry = s.entryPrice, recommendedSl = s.stopLoss, recommendedTp1 = s.takeProfit1, recommendedTp2 = s.takeProfit2,
                executionTacticEnglish = requote(m.executionTacticEnglish, map),
                executionTacticHindi = requote(m.executionTacticHindi, map),
                executionTacticMarathi = requote(m.executionTacticMarathi, map),
                allBots = m.allBots.map { bot ->
                    bot.copy(suggestedOrder = requote(bot.suggestedOrder, map),
                        stopLossPips = f.botSlPips ?: bot.stopLossPips, targetPips = f.botTpPips ?: bot.targetPips)
                }
            )
        }
        return a.copy(tradeSetup = ts, quantBotSignal = q, multiBotEnsemble = e)
    }

    private fun withFixedAt(a: GoldAnalysisResult, now: Long) = a.copy(tradeSetup = a.tradeSetup.copy(levelsFixedAtMs = now))
}
