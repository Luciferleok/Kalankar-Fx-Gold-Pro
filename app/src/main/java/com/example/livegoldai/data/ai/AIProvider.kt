package com.example.livegoldai.data.ai

import com.example.livegoldai.data.MiniJson
import com.example.livegoldai.model.AiVote
import java.util.Locale

/** Context for the optional debate round: anonymised round-1 answers of the other analysts. */
data class DebateContext(val others: List<String>, val own: AiVote)

data class AiCallResult(
    val provider: AiProviderId,
    val model: String,
    val role: AiRole,
    val promptVersion: String,
    val status: String,
    val detail: String,
    val vote: AiVote?,
    val latencyMs: Long,
    val tokens: Int,
    val receivedAtMs: Long,
    val factConflicts: List<String> = emptyList(),
    val retryAfterSec: Int = -1,
    val httpCode: Int = -1
)

data class ModelListResult(val ok: Boolean, val status: String, val detail: String, val models: List<String>, val latencyMs: Long)

/** Provider-independent contract. Each provider's real API format lives in [HttpAIProvider]. */
interface AIProvider {
    val id: AiProviderId
    fun analyze(snapshot: MarketSnapshot, timeoutMs: Long, debate: DebateContext? = null): AiCallResult
    fun healthCheck(timeoutMs: Long): ModelListResult
    fun validateResponse(text: String, snapshot: MarketSnapshot, debate: Boolean = false): AiResponseValidator.Outcome
    fun getLatency(): Long
    fun getProviderStatus(): String
    fun getModelMetadata(): Map<String, String>
}

class HttpAIProvider(
    private val config: AiProviderConfig,
    private val transport: AiTransport,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : AIProvider {

    override val id: AiProviderId = config.id
    @Volatile private var lastLatency: Long = -1
    @Volatile private var lastStatus: String = if (config.isConfigured) AiStatus.PENDING else AiStatus.NOT_CONFIGURED

    private val model: String = config.model.trim().removePrefix("models/").ifBlank { config.id.defaultModel }

    override fun getLatency(): Long = lastLatency
    override fun getProviderStatus(): String = lastStatus
    override fun getModelMetadata(): Map<String, String> = linkedMapOf(
        "provider" to id.display, "model" to model, "role" to config.role.title,
        "prompt_version" to AiPrompt.versionFor(id), "schema_version" to AiPrompt.SCHEMA_VERSION
    )

    override fun validateResponse(text: String, snapshot: MarketSnapshot, debate: Boolean) =
        AiResponseValidator.validate(text, snapshot, debate)

    // ------------------------------------------------------------------ analyze

    override fun analyze(snapshot: MarketSnapshot, timeoutMs: Long, debate: DebateContext?): AiCallResult {
        val pv = AiPrompt.versionFor(id)
        if (!config.isConfigured) return result(AiStatus.NOT_CONFIGURED, "No API key", null, -1, -1, pv)
        val sys = systemPrompt(snapshot, debate != null)
        val user = userPrompt(snapshot, debate)
        val (url, headers, body) = buildRequest(sys, user)
        val reply = transport.send("POST", url, headers, body, timeoutMs)
        lastLatency = reply.latencyMs
        if (reply.code != 200) {
            val (st, why) = classifyError(reply)
            return result(st, why, null, reply.latencyMs, -1, pv, retryAfter = reply.retryAfterSec, code = reply.code)
        }
        val (text, tokens, blocked) = extractText(reply.body)
        if (text.isNullOrBlank()) {
            return result(AiStatus.SCHEMA_INVALID, blocked ?: "Empty answer", null, reply.latencyMs, tokens, pv, code = 200)
        }
        val v = validateResponse(text, snapshot, debate != null)
        return result(v.status, v.detail, v.vote, reply.latencyMs, tokens, pv, v.factConflicts, code = 200)
    }

    private fun result(
        status: String, detail: String, vote: AiVote?, latency: Long, tokens: Int, pv: String,
        conflicts: List<String> = emptyList(), retryAfter: Int = -1, code: Int = -1
    ): AiCallResult {
        lastStatus = status
        return AiCallResult(id, model, config.role, pv, status, detail, vote, latency, tokens, clock(), conflicts, retryAfter, code)
    }

    // ------------------------------------------------------------------ health check / model list

    override fun healthCheck(timeoutMs: Long): ModelListResult {
        if (!config.isConfigured) return ModelListResult(false, AiStatus.NOT_CONFIGURED, "No API key", emptyList(), -1)
        if (!id.canListModels) {
            // no model list endpoint: send a tiny real request instead
            val (url, headers, body) = buildRequest("Reply with the single word OK.", "OK?")
            val r = transport.send("POST", url, headers, body, timeoutMs)
            lastLatency = r.latencyMs
            return if (r.code == 200) ModelListResult(true, AiStatus.ONLINE, "Key works (${r.latencyMs} ms)", listOf(model), r.latencyMs)
            else classifyError(r).let { (st, why) -> lastStatus = st; ModelListResult(false, st, why, emptyList(), r.latencyMs) }
        }
        val (url, headers) = when (id.style) {
            AiStyle.OPENAI_COMPAT -> "${id.baseUrl}/models" to mapOf("Authorization" to "Bearer ${config.apiKey.trim()}")
            AiStyle.GEMINI -> "${id.baseUrl}/models?pageSize=200" to mapOf("x-goog-api-key" to config.apiKey.trim())
            AiStyle.ANTHROPIC -> "${id.baseUrl}/models?limit=100" to mapOf("x-api-key" to config.apiKey.trim(), "anthropic-version" to ANTHROPIC_VERSION)
        }
        val r = transport.send("GET", url, headers, null, timeoutMs)
        lastLatency = r.latencyMs
        if (r.code != 200) {
            val (st, why) = classifyError(r)
            lastStatus = st
            return ModelListResult(false, st, why, emptyList(), r.latencyMs)
        }
        val models = parseModelList(r.body)
        val has = models.any { it.equals(model, true) }
        val detail = if (models.isEmpty()) "Key works (${r.latencyMs} ms)"
        else if (has) "Key works, model \"$model\" available (${r.latencyMs} ms)"
        else "Key works, but \"$model\" is not in your model list. Pick one below."
        lastStatus = if (models.isEmpty() || has) AiStatus.ONLINE else AiStatus.MODEL_NOT_FOUND
        return ModelListResult(true, lastStatus, detail, models, r.latencyMs)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseModelList(body: String): List<String> = try {
        val m = MiniJson.parse(body) as? Map<String, Any?> ?: emptyMap()
        when (id.style) {
            AiStyle.GEMINI -> (m["models"] as? List<*>)?.mapNotNull { x ->
                val mm = x as? Map<String, Any?> ?: return@mapNotNull null
                val methods = (mm["supportedGenerationMethods"] as? List<*>)?.map { it.toString() } ?: emptyList()
                if (methods.isNotEmpty() && "generateContent" !in methods) null else (mm["name"] as? String)?.removePrefix("models/")
            }
            else -> (m["data"] as? List<*>)?.mapNotNull { x -> (x as? Map<String, Any?>)?.get("id") as? String }
        }?.sorted() ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    // ------------------------------------------------------------------ request formats (per provider docs)

    private fun buildRequest(sys: String, user: String): Triple<String, Map<String, String>, String> {
        val key = config.apiKey.trim()
        return when (id.style) {
            AiStyle.OPENAI_COMPAT -> {
                val body = linkedMapOf<String, Any?>(
                    "model" to model,
                    "messages" to listOf(
                        linkedMapOf("role" to "system", "content" to sys),
                        linkedMapOf("role" to "user", "content" to user)
                    )
                )
                if (id.jsonMode) body["response_format"] = linkedMapOf("type" to "json_object")
                Triple("${id.baseUrl}/chat/completions", mapOf("Authorization" to "Bearer $key"), MiniJson.write(body))
            }
            AiStyle.GEMINI -> {
                val body = linkedMapOf<String, Any?>(
                    "systemInstruction" to linkedMapOf("parts" to listOf(linkedMapOf("text" to sys))),
                    "contents" to listOf(linkedMapOf("role" to "user", "parts" to listOf(linkedMapOf("text" to user)))),
                    "generationConfig" to linkedMapOf("responseMimeType" to "application/json")
                )
                Triple("${id.baseUrl}/models/$model:generateContent", mapOf("x-goog-api-key" to key), MiniJson.write(body))
            }
            AiStyle.ANTHROPIC -> {
                val body = linkedMapOf<String, Any?>(
                    "model" to model,
                    "max_tokens" to 1200,
                    "system" to sys,
                    "messages" to listOf(linkedMapOf("role" to "user", "content" to user))
                )
                Triple("${id.baseUrl}/messages", mapOf("x-api-key" to key, "anthropic-version" to ANTHROPIC_VERSION), MiniJson.write(body))
            }
        }
    }

    /** Returns (text, tokens or -1, reason when the answer was blocked). */
    @Suppress("UNCHECKED_CAST")
    fun extractText(body: String): Triple<String?, Int, String?> {
        val m = try { MiniJson.parse(body) as? Map<String, Any?> } catch (_: Exception) { null }
            ?: return Triple(null, -1, "Reply was not JSON")
        return when (id.style) {
            AiStyle.OPENAI_COMPAT -> {
                val choice = (m["choices"] as? List<*>)?.firstOrNull() as? Map<String, Any?>
                val msg = choice?.get("message") as? Map<String, Any?>
                val text = when (val c = msg?.get("content")) {
                    is String -> c
                    is List<*> -> c.mapNotNull { (it as? Map<String, Any?>)?.get("text") as? String }.joinToString("")
                    else -> null
                }
                val tokens = ((m["usage"] as? Map<String, Any?>)?.get("total_tokens") as? Number)?.toInt() ?: -1
                val refusal = msg?.get("refusal") as? String
                Triple(text, tokens, refusal?.let { "Refused: ${it.take(100)}" })
            }
            AiStyle.GEMINI -> {
                val cand = (m["candidates"] as? List<*>)?.firstOrNull() as? Map<String, Any?>
                val parts = (cand?.get("content") as? Map<String, Any?>)?.get("parts") as? List<*>
                val text = parts?.mapNotNull { p ->
                    val pm = p as? Map<String, Any?> ?: return@mapNotNull null
                    if (pm["thought"] == true) null else pm["text"] as? String
                }?.joinToString("")
                val tokens = ((m["usageMetadata"] as? Map<String, Any?>)?.get("totalTokenCount") as? Number)?.toInt() ?: -1
                val block = (m["promptFeedback"] as? Map<String, Any?>)?.get("blockReason") as? String
                    ?: (cand?.get("finishReason") as? String)?.takeIf { text.isNullOrBlank() }
                Triple(text, tokens, block?.let { "Blocked / stopped: $it" })
            }
            AiStyle.ANTHROPIC -> {
                val text = (m["content"] as? List<*>)?.mapNotNull { b ->
                    val bm = b as? Map<String, Any?> ?: return@mapNotNull null
                    if (bm["type"] == "text") bm["text"] as? String else null
                }?.joinToString("")
                val u = m["usage"] as? Map<String, Any?>
                val tokens = if (u == null) -1 else ((u["input_tokens"] as? Number)?.toInt() ?: 0) + ((u["output_tokens"] as? Number)?.toInt() ?: 0)
                Triple(text, tokens, (m["stop_reason"] as? String)?.takeIf { text.isNullOrBlank() }?.let { "Stopped: $it" })
            }
        }
    }

    /** Maps HTTP results to an honest status. The API key is never included in the detail. */
    fun classifyError(r: HttpReply): Pair<String, String> {
        if (r.code == -1) {
            return if (r.timedOut) AiStatus.TIMEOUT to "No answer within ${r.latencyMs / 1000.0}s"
            else AiStatus.OFFLINE to ("Network: " + (r.networkError ?: "unreachable")).take(120)
        }
        val msg = errorMessage(r.body)
        val low = (r.body + " " + msg).lowercase(Locale.US)
        val st = when {
            r.code == 401 || r.code == 403 -> AiStatus.AUTH_FAILED
            r.code == 402 -> AiStatus.QUOTA_EXCEEDED
            r.code == 404 -> AiStatus.MODEL_NOT_FOUND
            r.code == 429 -> if ("insufficient_quota" in low || "billing" in low || "credit balance" in low) AiStatus.QUOTA_EXCEEDED else AiStatus.RATE_LIMITED
            r.code == 400 && ("api key not valid" in low || "api_key_invalid" in low || "invalid x-api-key" in low || "incorrect api key" in low) -> AiStatus.AUTH_FAILED
            r.code == 400 && "model" in low && ("not found" in low || "does not exist" in low || "invalid model" in low || "not supported" in low) -> AiStatus.MODEL_NOT_FOUND
            r.code in 400..499 -> AiStatus.BAD_REQUEST
            else -> AiStatus.PROVIDER_ERROR
        }
        val base = "HTTP ${r.code}" + if (msg.isNotBlank()) ": $msg" else ""
        val hint = when (st) {
            AiStatus.AUTH_FAILED -> " • check the key"
            AiStatus.MODEL_NOT_FOUND -> " • change the model (tap Test to list yours)"
            AiStatus.QUOTA_EXCEEDED -> " • no credit / quota left"
            AiStatus.RATE_LIMITED -> if (r.retryAfterSec > 0) " • retry after ${r.retryAfterSec}s" else ""
            else -> ""
        }
        return st to (base.take(140) + hint)
    }

    @Suppress("UNCHECKED_CAST")
    private fun errorMessage(body: String): String = try {
        val m = MiniJson.parse(body) as? Map<String, Any?>
        val e = m?.get("error")
        when (e) {
            is Map<*, *> -> (e["message"] as? String) ?: ""
            is String -> e
            else -> (m?.get("message") as? String) ?: (m?.get("detail") as? String) ?: ""
        }.replace(config.apiKey.trim().ifBlank { "\u0000" }, "***").trim().take(110)
    } catch (_: Exception) {
        ""
    }

    // ------------------------------------------------------------------ prompts

    private fun systemPrompt(s: MarketSnapshot, debate: Boolean): String = buildString {
        append("You are one independent analyst (").append(config.role.title).append(") on a panel reviewing XAU/USD (spot gold).\n")
        append(config.role.instruction).append("\n")
        append("Rules:\n")
        append("- Use ONLY the values in the snapshot. Never invent prices, candles, indicator values, levels, DXY, volume or news.\n")
        append("- If something you need is missing or marked DATA UNAVAILABLE, say so in data_concerns.\n")
        append("- Forecast the direction of price over the next ").append(s.horizonMin).append(" minutes from current_price. WAIT means no clear edge.\n")
        append("- Reply with ONE JSON object and nothing else.\n")
        append("JSON keys:\n")
        append("\"direction\": \"BUY\" | \"SELL\" | \"WAIT\",\n")
        append("\"bullish_probability\", \"bearish_probability\", \"sideways_probability\": integers 0-100 that sum to 100,\n")
        append("\"confidence\": integer 0-100,\n")
        append("\"regime_assessment\": short string,\n")
        append("\"strongest_evidence\", \"contradictions\", \"invalidation_conditions\", \"uncertainty_sources\": up to 3 short strings each,\n")
        append("\"data_concerns\": list of strings (may be empty),\n")
        append("\"cited_values\": list of {\"name\": <name exactly as in the snapshot>, \"value\": <number>} for every number you relied on")
        if (debate) append(",\n\"would_prove_me_wrong\": up to 3 short strings")
        append("\n")
    }

    private fun userPrompt(s: MarketSnapshot, debate: DebateContext?): String = buildString {
        append("MARKET SNAPSHOT (JSON):\n").append(s.json)
        if (debate != null) {
            append("\n\nROUND 2. The other analysts answered independently (anonymised):\n")
            debate.others.forEach { append("- ").append(it).append("\n") }
            append("Your round-1 answer: ").append(debate.own.direction.name)
                .append(" (bullish ").append(debate.own.bullish).append("%, bearish ").append(debate.own.bearish)
                .append("%, sideways ").append(debate.own.sideways).append("%).\n")
            append("Do NOT move toward the others just to agree. State what evidence would make YOUR conclusion wrong, ")
            append("then give your final answer in the same JSON format including \"would_prove_me_wrong\".")
        }
    }

    companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
