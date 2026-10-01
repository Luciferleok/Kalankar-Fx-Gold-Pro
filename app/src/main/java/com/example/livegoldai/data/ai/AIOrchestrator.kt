package com.example.livegoldai.data.ai

import com.example.livegoldai.data.LedgerState
import com.example.livegoldai.data.PredictionLedger
import com.example.livegoldai.model.AiCouncilReport
import com.example.livegoldai.model.AiProviderView
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * AI ORCHESTRATOR
 *  routing (FAST / BALANCED / FULL / AUTO) • parallel requests • freshness window • one retry on
 *  network/5xx • rate-limit + circuit breaker • response cache per snapshot • schema + fact check •
 *  optional debate round • consensus • provider ledger.
 *
 * AI never changes the app's signal. The forecast stays with the quant engine; the council is shown
 * next to it, and its real value is measured in the ledger before it may ever get a weight.
 * Blocking API: call from a background thread.
 */
class AIOrchestrator(
    private val transport: AiTransport,
    val tracker: AiPerformanceTracker,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val pool: ExecutorService = Executors.newFixedThreadPool(8) { r -> Thread(r, "ai-council").apply { isDaemon = true } }
) {
    // ---- user settings
    @Volatile var mode: String = "AUTO"           // FAST / BALANCED / FULL / AUTO
    @Volatile var debateEnabled: Boolean = false
    @Volatile var freshnessOverrideSec: Int = 0    // 0 = automatic per timeframe

    // ---- circuit breaker
    private class Circuit(var fingerprint: String = "", var failures: Int = 0, var openUntil: Long = 0L, var reason: String = "")
    private val circuits = ConcurrentHashMap<AiProviderId, Circuit>()

    // ---- response cache: snapshot + provider + model + prompt version + role (+ round)
    private data class Cached(val result: AiCallResult, val at: Long)
    private val cache = ConcurrentHashMap<String, Cached>()

    @Volatile private var lastReport: AiCouncilReport? = null
    fun latest(interval: String): AiCouncilReport? = lastReport?.takeIf { it.interval == interval }

    fun freshnessWindowMs(interval: String): Long {
        if (freshnessOverrideSec > 0) return freshnessOverrideSec * 1000L
        return when (interval.lowercase()) {
            "1m" -> 10_000L
            "5m" -> 15_000L
            "15m" -> 20_000L
            else -> 25_000L
        }
    }

    private fun cacheMaxAgeMs(interval: String): Long {
        val min = RealityIntervals.minutes(interval)
        return (min.coerceIn(2, 15)) * 60_000L
    }

    fun circuitOpenUntil(id: AiProviderId): Long = circuits[id]?.openUntil ?: 0L

    // ------------------------------------------------------------------ routing

    data class Route(val mode: String, val reason: String, val limit: Int)

    fun route(a: GoldAnalysisResult): Route {
        val m = mode
        if (m == "FAST") return Route("FAST", "Chosen by you", 2)
        if (m == "BALANCED") return Route("BALANCED", "Chosen by you", 4)
        if (m == "FULL") return Route("FULL", "Chosen by you", Int.MAX_VALUE)
        val newsSoon = a.newsMode != null || a.newsTradingPlan?.isNewsActive == true || a.isNewsModeTriggered
        val bots = a.multiBotEnsemble?.ensembleSignal
        val mtf = a.mtfMatrix?.timeframes?.filter { it.label.startsWith("PAXG") } ?: emptyList()
        val mtfConflict = mtf.any { it.signal == Signal.BUY } && mtf.any { it.signal == Signal.SELL }
        val regime = a.insights?.regime ?: ""
        return when {
            newsSoon -> Route("FULL", "AUTO: news / event risk", Int.MAX_VALUE)
            bots != null && PredictionLedger.opposite(bots, a.overallSignal) -> Route("FULL", "AUTO: quant and bots disagree", Int.MAX_VALUE)
            mtfConflict -> Route("BALANCED", "AUTO: timeframes conflict", 4)
            regime == "TRANSITION" || regime == "VOLATILITY_EXPANSION" -> Route("BALANCED", "AUTO: unsettled regime ($regime)", 4)
            else -> Route("FAST", "AUTO: clear / quiet market", 2)
        }
    }

    // ------------------------------------------------------------------ run

    /**
     * @param recordId the ledger prediction this council belongs to ("" if none), used to verify AI later
     */
    fun run(
        a: GoldAnalysisResult,
        configs: List<AiProviderConfig>,
        recordId: String,
        ledger: LedgerState?,
        providerFactory: (AiProviderConfig) -> AIProvider = { HttpAIProvider(it, transport, clock) }
    ): AiCouncilReport {
        val now = clock()
        val snapshot = MarketSnapshotBuilder.build(a, now)
        val window = freshnessWindowMs(a.interval)
        val hardTimeout = window + 10_000L
        val route = route(a)
        val byId = configs.associateBy { it.id }

        // ---- who may be called
        val views = LinkedHashMap<AiProviderId, AiProviderView>()
        val callable = ArrayList<AiProviderConfig>()
        for (id in AiProviderId.values()) {
            val c = byId[id] ?: AiProviderConfig(id)
            when {
                !c.isConfigured -> views[id] = baseView(c, AiStatus.NOT_CONFIGURED, "Add your API key to connect")
                !c.enabled -> views[id] = baseView(c, AiStatus.DISABLED, "Switched off by you")
                else -> {
                    val circ = circuitFor(c)
                    if (circ.openUntil > now) {
                        val until = if (circ.openUntil == Long.MAX_VALUE) "until you change the key or model" else "for ${((circ.openUntil - now) / 1000)}s"
                        views[id] = baseView(c, AiStatus.CIRCUIT_OPEN, "Paused $until • ${circ.reason}")
                    } else callable.add(c)
                }
            }
        }
        // fastest first (real median latency), unknown latency in the middle
        val ordered = callable.sortedWith(compareBy({ tracker.opStats(it.id.name, now).medianLatencyMs.let { l -> if (l < 0) 5000L else l } }, { it.id.ordinal }))
        val chosen = ordered.take(route.limit.coerceAtMost(ordered.size))
        ordered.drop(chosen.size).forEach { views[it.id] = baseView(it, AiStatus.SKIPPED, "Not needed in ${route.mode} mode") }

        // ---- round 1 (independent)
        val r1 = callAll(chosen, snapshot, a.interval, window, hardTimeout, recordId, now, providerFactory, null)
        chosen.forEach { c -> views[c.id] = toView(c, r1[c.id], now, window) }

        // ---- optional debate round
        var debateRan = false
        val eligible1 = chosen.filter { views[it.id]?.eligible == true }
        if (debateEnabled && eligible1.size >= 2) {
            val disp = AiConsensus.dispersion(eligible1.mapNotNull { views[it.id]?.vote })
            val dirs = eligible1.mapNotNull { views[it.id]?.vote?.direction }.distinct()
            if (disp >= 15 || dirs.size > 1) {
                debateRan = true
                val letters = "ABCDEFG"
                val r2 = callAll(eligible1, snapshot, a.interval, window, hardTimeout, recordId, now, providerFactory) { c ->
                    val own = views[c.id]!!.vote!!
                    val others = eligible1.filter { it.id != c.id }.mapIndexed { i, o ->
                        val v = views[o.id]!!.vote!!
                        val p = when (v.direction) { Signal.BUY -> v.bullish; Signal.SELL -> v.bearish; Signal.WAIT -> v.sideways }
                        "Analyst ${letters[i]}: ${v.direction.name} $p%"
                    }
                    DebateContext(others, own)
                }
                eligible1.forEach { c ->
                    val res = r2[c.id]
                    val before = views[c.id]!!
                    if (res != null && res.status == AiStatus.ONLINE && res.vote != null && res.latencyMs <= window) {
                        views[c.id] = before.copy(
                            vote = res.vote,
                            round1 = before.vote.takeIf { it?.direction != res.vote.direction || it.bullish != res.vote.bullish },
                            statusDetail = before.statusDetail + " • debate: " + if (before.vote?.direction != res.vote.direction) "changed to ${res.vote.direction.name}" else "kept ${res.vote.direction.name}"
                        )
                    } else {
                        views[c.id] = before.copy(statusDetail = before.statusDetail + " • debate answer unusable (${res?.status ?: "no answer"}), round-1 kept")
                    }
                }
            }
        }

        // ---- stats from the ledger
        val finalViews = AiProviderId.values().map { id ->
            val v = views[id]!!
            val op = tracker.opStats(id.name, now)
            val dr = if (ledger != null) tracker.directionRecord(id.name, ledger) else (0 to 0)
            v.copy(
                trustScore = op.trust, trustSamples = op.attempts, availabilityPct = op.availabilityPct,
                schemaOkPct = op.schemaOkPct, medianLatencyMs = op.medianLatencyMs,
                requestsToday = op.requestsToday, okToday = op.okToday, failedToday = op.failedToday,
                avgLatencyTodayMs = op.avgLatencyTodayMs, tokensToday = op.tokensToday,
                directionHits = dr.first, directionDecided = dr.second,
                circuitOpenUntilMs = circuits[id]?.openUntil ?: 0L
            )
        }

        val cons = AiConsensus.compute(finalViews)
        val bots = a.multiBotEnsemble?.ensembleSignal
        val (conflict, conflictNote) = AiConsensus.conflict(a.overallSignal, bots, cons.consensus)
        val qa = if (ledger != null) tracker.quantVsAi(ledger) else ((0 to 0) to (0 to 0))
        val eligibleViews = finalViews.filter { it.eligible }
        val report = AiCouncilReport(
            runAtMs = now,
            snapshotId = snapshot.id,
            interval = a.interval,
            requestedMode = mode,
            effectiveMode = route.mode,
            modeReason = route.reason,
            debateRan = debateRan,
            providers = finalViews,
            supported = AiProviderId.values().size,
            configured = configs.count { it.isConfigured },
            called = chosen.size,
            eligible = eligibleViews.size,
            buyVotes = cons.buy, sellVotes = cons.sell, waitVotes = cons.wait,
            consensus = cons.consensus,
            agreementLevel = cons.agreementLevel,
            agreementPct = cons.agreementPct,
            dispersionPct = cons.dispersionPct,
            dispersionLevel = cons.dispersionLevel,
            avgBullish = cons.avgBull, avgBearish = cons.avgBear, avgSideways = cons.avgSide,
            avgResponseAgeMs = if (eligibleViews.isEmpty()) -1 else eligibleViews.map { now - it.receivedAtMs }.average().toLong().coerceAtLeast(0),
            dissent = cons.dissent,
            quantSignal = a.overallSignal,
            botSignal = bots,
            conflictLevel = conflict,
            conflictNote = conflictNote,
            contrarian = AiConsensus.contrarian(a),
            aiInvalidations = eligibleViews.flatMap { v -> v.vote!!.invalidations.map { "${v.name}: $it" } }.take(6),
            quantWhenAiAgreed = AiPerformanceTracker.label(qa.first.first, qa.first.second),
            quantWhenAiDisagreed = AiPerformanceTracker.label(qa.second.first, qa.second.second),
            pairs = if (ledger != null) tracker.pairs(ledger) else emptyList(),
            note = "AI votes are advisory. The forecast comes from the quant engine; AI weight stays 0 until verified results show it adds value."
        )
        lastReport = report
        return report
    }

    /** Forget the cache + circuit for a provider (after the user changes its key/model). */
    fun reset(id: AiProviderId) {
        circuits.remove(id)
        cache.keys.removeIf { it.contains("|${id.name}|") }
    }

    /** Settings-screen "Test" button. */
    fun test(config: AiProviderConfig): ModelListResult {
        reset(config.id)
        return HttpAIProvider(config, transport, clock).healthCheck(20_000L)
    }

    // ------------------------------------------------------------------ internals

    private fun callAll(
        list: List<AiProviderConfig>,
        snapshot: MarketSnapshot,
        interval: String,
        window: Long,
        hardTimeout: Long,
        recordId: String,
        now: Long,
        providerFactory: (AiProviderConfig) -> AIProvider,
        debateFor: ((AiProviderConfig) -> DebateContext)?
    ): Map<AiProviderId, AiCallResult?> {
        val round = if (debateFor == null) "R1" else "R2"
        val maxAge = cacheMaxAgeMs(interval)
        val futures = LinkedHashMap<AiProviderId, Future<AiCallResult>>()
        val out = LinkedHashMap<AiProviderId, AiCallResult?>()
        for (c in list) {
            val key = "${snapshot.id}|${c.id.name}|${c.model}|${AiPrompt.versionFor(c.id)}|${c.role.name}|$round"
            val hit = cache[key]
            if (hit != null && now - hit.at <= maxAge) {
                out[c.id] = hit.result.copy(detail = hit.result.detail)
                continue
            }
            val debate = debateFor?.invoke(c)
            futures[c.id] = pool.submit<AiCallResult> {
                val provider = providerFactory(c)
                var res = provider.analyze(snapshot, hardTimeout, debate)
                val elapsed = clock() - now
                if ((res.status == AiStatus.OFFLINE || res.status == AiStatus.PROVIDER_ERROR) && elapsed < window / 2) {
                    val retry = provider.analyze(snapshot, (hardTimeout - elapsed).coerceAtLeast(3_000L), debate)
                    res = retry.copy(latencyMs = retry.latencyMs + res.latencyMs, detail = retry.detail + " (after 1 retry)")
                }
                val validation = if (res.status in ANSWERED) res.status else ""
                // late answer: keep it for diagnostics, never vote
                val totalLatency = clock() - now
                if (res.status == AiStatus.ONLINE && totalLatency > window) {
                    res = res.copy(status = AiStatus.STALE, detail = "Answered after ${totalLatency / 1000.0}s (limit ${window / 1000}s): diagnostics only • ${res.detail}")
                }
                record(c, res, snapshot, recordId, debate != null, validation)
                cache[key] = Cached(res, clock())
                res
            }
        }
        val deadline = now + window
        for ((id, f) in futures) {
            val left = deadline - clock()
            out[id] = try {
                f.get(left.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                null      // still running: shown as PENDING now, logged as STALE/TIMEOUT when it ends
            } catch (e: Exception) {
                AiCallResult(id, "", AiRole.FAST, AiPrompt.versionFor(id), AiStatus.PROVIDER_ERROR, "Internal error: ${e.javaClass.simpleName}", null, -1, -1, clock())
            }
        }
        return out
    }

    private fun record(c: AiProviderConfig, r: AiCallResult, s: MarketSnapshot, recordId: String, debate: Boolean, validation: String) {
        // circuit breaker
        val circ = circuitFor(c)
        synchronized(circ) {
            val t = clock()
            when {
                r.status == AiStatus.ONLINE || r.status == AiStatus.FACT_CONFLICT || r.status == AiStatus.STALE -> { circ.failures = 0; circ.openUntil = 0L; circ.reason = "" }
                r.status in AiStatus.NEEDS_USER -> { circ.openUntil = Long.MAX_VALUE; circ.reason = r.status.replace('_', ' ') }
                r.status == AiStatus.RATE_LIMITED -> { circ.openUntil = t + maxOf(r.retryAfterSec, 60) * 1000L; circ.reason = "rate limited" }
                r.status == AiStatus.QUOTA_EXCEEDED -> { circ.openUntil = t + 30 * 60_000L; circ.reason = "quota exceeded" }
                r.status in AiStatus.FAILURES -> {
                    circ.failures++
                    if (circ.failures >= 3) { circ.openUntil = t + 5 * 60_000L; circ.reason = "${circ.failures} failures in a row" }
                }
            }
        }
        val v = r.vote
        tracker.append(
            AiVoteLine(
                at = r.receivedAtMs, provider = c.id.name, model = r.model, promptVersion = r.promptVersion,
                snapshotId = s.id, recordId = recordId, status = r.status, validation = validation,
                eligible = r.status == AiStatus.ONLINE && v != null,
                direction = v?.direction, bullish = v?.bullish ?: 0, bearish = v?.bearish ?: 0, sideways = v?.sideways ?: 0,
                confidence = v?.confidence ?: 0, latencyMs = r.latencyMs, tokens = r.tokens, price = s.price,
                factConflicts = r.factConflicts.size, debate = debate
            )
        )
    }

    private fun circuitFor(c: AiProviderConfig): Circuit {
        val fp = (c.apiKey.trim().hashCode().toString() + "|" + c.model.trim())
        val circ = circuits.getOrPut(c.id) { Circuit(fp) }
        if (circ.fingerprint != fp) { circ.fingerprint = fp; circ.failures = 0; circ.openUntil = 0L; circ.reason = "" }
        return circ
    }

    private fun baseView(c: AiProviderConfig, status: String, detail: String) = AiProviderView(
        id = c.id.name, name = c.id.display, model = c.model.ifBlank { c.id.defaultModel }, role = c.role.title,
        status = status, statusDetail = detail, eligible = false, promptVersion = AiPrompt.versionFor(c.id)
    )

    private fun toView(c: AiProviderConfig, r: AiCallResult?, now: Long, window: Long): AiProviderView {
        if (r == null) return baseView(c, AiStatus.PENDING, "No answer within ${window / 1000}s: excluded from this forecast")
        val cachedHit = r.receivedAtMs < now
        return AiProviderView(
            id = c.id.name, name = c.id.display, model = r.model.ifBlank { c.model }, role = c.role.title,
            status = r.status, statusDetail = r.detail + if (cachedHit) " • reused (same market snapshot)" else "",
            eligible = r.status == AiStatus.ONLINE && r.vote != null,
            vote = r.vote, latencyMs = r.latencyMs, receivedAtMs = r.receivedAtMs, cached = cachedHit,
            promptVersion = r.promptVersion, factConflicts = r.factConflicts, tokens = r.tokens
        )
    }
}

private val ANSWERED = setOf(AiStatus.ONLINE, AiStatus.FACT_CONFLICT, AiStatus.SCHEMA_INVALID)

/** Interval string -> minutes (kept here so the AI layer does not depend on UI code). */
object RealityIntervals {
    fun minutes(interval: String): Int {
        val s = interval.trim().lowercase()
        val n = s.dropLast(1).toIntOrNull() ?: return 60
        return when (s.last()) { 'm' -> n; 'h' -> n * 60; 'd' -> n * 1440; 'w' -> n * 10080; else -> 60 }
    }
}
