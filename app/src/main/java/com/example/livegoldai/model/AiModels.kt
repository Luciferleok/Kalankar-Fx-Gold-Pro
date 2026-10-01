package com.example.livegoldai.model

import kotlinx.serialization.Serializable

/**
 * EXTERNAL AI COUNCIL — data shown in the AI Command Center.
 *
 * Every vote here is a real API response from a provider the user connected with their own key.
 * Providers that are not connected, failed, answered late, broke the schema or contradicted the
 * snapshot data are listed with their status but never vote. Nothing is filled in.
 */

/** One analyst answer after schema validation. Probabilities are normalised to sum to 100. */
@Serializable
data class AiVote(
    val direction: Signal,
    val bullish: Int,
    val bearish: Int,
    val sideways: Int,
    val confidence: Int,
    val regime: String,
    val evidence: List<String>,
    val contradictions: List<String>,
    val invalidations: List<String>,
    val uncertainty: List<String>,
    val dataConcerns: List<String>,
    val proveWrong: List<String> = emptyList()   // debate round only
)

@Serializable
data class AiProviderView(
    val id: String,
    val name: String,
    val model: String,
    val role: String,
    val status: String,              // AiStatus.*
    val statusDetail: String,
    val eligible: Boolean,           // true only for a fresh, schema-valid, fact-consistent answer
    val vote: AiVote? = null,
    val round1: AiVote? = null,      // first answer when the debate round changed it
    val latencyMs: Long = -1,
    val receivedAtMs: Long = 0,
    val cached: Boolean = false,
    val promptVersion: String = "",
    val factConflicts: List<String> = emptyList(),
    val tokens: Int = -1,            // only when the API reports usage
    // ---- operational stats from the on-device ledger (all real, -1 = not enough data)
    val trustScore: Int = -1,
    val trustSamples: Int = 0,
    val availabilityPct: Int = -1,
    val schemaOkPct: Int = -1,
    val medianLatencyMs: Long = -1,
    val requestsToday: Int = 0,
    val okToday: Int = 0,
    val failedToday: Int = 0,
    val avgLatencyTodayMs: Long = -1,
    val tokensToday: Long = 0,
    // ---- verified direction record (vote vs real price at the end of the forecast window)
    val directionHits: Int = 0,
    val directionDecided: Int = 0,
    val circuitOpenUntilMs: Long = 0
)

@Serializable
data class AiDissent(
    val provider: String,
    val direction: Signal,
    val probability: Int,
    val reason: String
)

@Serializable
data class AiPairStat(
    val a: String,
    val b: String,
    val together: Int,       // snapshots where both voted
    val sameDirectionPct: Int
)

@Serializable
data class AiCouncilReport(
    val runAtMs: Long,
    val snapshotId: String,
    val interval: String,
    val requestedMode: String,       // FAST / BALANCED / FULL / AUTO
    val effectiveMode: String,
    val modeReason: String,
    val debateRan: Boolean,
    val providers: List<AiProviderView>,
    val supported: Int,
    val configured: Int,
    val called: Int,
    val eligible: Int,
    val buyVotes: Int,
    val sellVotes: Int,
    val waitVotes: Int,
    val consensus: String,           // BUY / SELL / WAIT / SPLIT / NONE
    val agreementLevel: String,      // STRONG / MODERATE / WEAK / SINGLE / NONE
    val agreementPct: Int,           // majority share of eligible votes, -1 if none
    val dispersionPct: Int,          // mean pairwise distance of probability vectors, -1 if < 2 votes
    val dispersionLevel: String,     // LOW / MODERATE / HIGH / N/A
    val avgBullish: Int,             // average of eligible AI probabilities (AI view, NOT market probability), -1 if none
    val avgBearish: Int,
    val avgSideways: Int,
    val avgResponseAgeMs: Long,
    val dissent: List<AiDissent>,
    val quantSignal: Signal,
    val botSignal: Signal?,
    val conflictLevel: String,       // ALIGNED / MINOR / HIGH / NO_AI
    val conflictNote: String,
    val contrarian: List<String>,    // deterministic, from real snapshot data
    val aiInvalidations: List<String>,
    val quantWhenAiAgreed: String,   // real ledger accuracy label
    val quantWhenAiDisagreed: String,
    val pairs: List<AiPairStat>,
    val note: String
)

/** Settings row for one provider in the AI tab (the full key is never put in UI state). */
data class AiProviderUi(
    val id: String,
    val name: String,
    val hasKey: Boolean,
    val maskedKey: String,
    val model: String,
    val defaultModel: String,
    val role: String,            // AiRole name
    val enabled: Boolean,
    val keyUrl: String
)

/** Result of the "Test" button. */
data class AiTestUi(
    val running: Boolean,
    val ok: Boolean = false,
    val status: String = "",
    val detail: String = "",
    val models: List<String> = emptyList()
)
