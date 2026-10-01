package com.example.livegoldai.data.ai

/**
 * Supported external AI providers. Listing a provider here does NOT mean it is available:
 * only a provider with the user's own key, a successful request and a valid answer may vote.
 * Model names change often, so the model is editable and "Test" lists what the key can really use.
 */
enum class AiStyle { OPENAI_COMPAT, GEMINI, ANTHROPIC }

enum class AiRole(val title: String, val instruction: String) {
    SCENARIO(
        "Contradiction + scenario analyst",
        "Find contradictions between the readings, then describe the most likely scenario and the main alternative."
    ),
    MTF(
        "Multi-timeframe pattern analyst",
        "Focus on how the timeframes agree or conflict and what pattern the recent candles form."
    ),
    RISK(
        "Risk + invalidation analyst",
        "Focus on what would invalidate a move, where the risk sits, and whether standing aside (WAIT) is the safer call."
    ),
    QUANT(
        "Quant / structure analyst",
        "Reason strictly from the numbers: momentum, volatility, ATR, distance to the listed levels."
    ),
    NEWS(
        "Event-context analyst",
        "Weigh the scheduled calendar events listed in the snapshot. Do not rely on news that is not in the snapshot; if you use outside information, write it in data_concerns as UNVERIFIED."
    ),
    ALTERNATIVE(
        "Alternative-hypothesis analyst",
        "First build the strongest case for the outcome the readings seem to argue against, then decide honestly which case is stronger."
    ),
    FAST(
        "Fast secondary analyst",
        "Give a short, careful read of the snapshot."
    );

    companion object {
        fun fromName(n: String?, def: AiRole): AiRole = values().firstOrNull { it.name == n } ?: def
    }
}

enum class AiProviderId(
    val display: String,
    val style: AiStyle,
    val baseUrl: String,
    val defaultModel: String,
    val defaultRole: AiRole,
    val keyUrl: String,
    val jsonMode: Boolean,       // provider documents response_format json_object
    val canListModels: Boolean,
    val promptPrefix: String
) {
    OPENAI("OpenAI", AiStyle.OPENAI_COMPAT, "https://api.openai.com/v1", "gpt-4.1-mini", AiRole.SCENARIO,
        "platform.openai.com/api-keys", true, true, "OAI"),
    GEMINI("Google Gemini", AiStyle.GEMINI, "https://generativelanguage.googleapis.com/v1beta", "gemini-flash-latest", AiRole.MTF,
        "aistudio.google.com/apikey", true, true, "GEM"),
    CLAUDE("Anthropic Claude", AiStyle.ANTHROPIC, "https://api.anthropic.com/v1", "claude-haiku-4-5", AiRole.RISK,
        "console.anthropic.com", false, true, "CLD"),
    DEEPSEEK("DeepSeek", AiStyle.OPENAI_COMPAT, "https://api.deepseek.com", "deepseek-chat", AiRole.QUANT,
        "platform.deepseek.com/api_keys", true, true, "DSK"),
    PERPLEXITY("Perplexity", AiStyle.OPENAI_COMPAT, "https://api.perplexity.ai", "sonar", AiRole.NEWS,
        "perplexity.ai/settings/api", false, false, "PPX"),
    XAI("xAI Grok", AiStyle.OPENAI_COMPAT, "https://api.x.ai/v1", "grok-4.6", AiRole.ALTERNATIVE,
        "console.x.ai", false, true, "GRK"),
    MISTRAL("Mistral", AiStyle.OPENAI_COMPAT, "https://api.mistral.ai/v1", "mistral-small-latest", AiRole.FAST,
        "console.mistral.ai/api-keys", true, true, "MIS");

    companion object {
        fun fromName(n: String): AiProviderId? = values().firstOrNull { it.name == n }
    }
}

/** User settings for one provider. The key never leaves the phone except in the request to that provider. */
data class AiProviderConfig(
    val id: AiProviderId,
    val apiKey: String = "",
    val model: String = id.defaultModel,
    val role: AiRole = id.defaultRole,
    val enabled: Boolean = true
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}

object AiStatus {
    const val ONLINE = "ONLINE"
    const val NOT_CONFIGURED = "NOT_CONFIGURED"
    const val DISABLED = "DISABLED"
    const val SKIPPED = "SKIPPED"                 // not needed in this routing mode
    const val CIRCUIT_OPEN = "CIRCUIT_OPEN"
    const val TIMEOUT = "TIMEOUT"
    const val STALE = "STALE"                     // answered after the freshness window
    const val RATE_LIMITED = "RATE_LIMITED"
    const val QUOTA_EXCEEDED = "QUOTA_EXCEEDED"
    const val AUTH_FAILED = "AUTH_FAILED"
    const val MODEL_NOT_FOUND = "MODEL_NOT_FOUND"
    const val BAD_REQUEST = "BAD_REQUEST"
    const val PROVIDER_ERROR = "PROVIDER_ERROR"
    const val OFFLINE = "OFFLINE"
    const val SCHEMA_INVALID = "SCHEMA_INVALID"
    const val FACT_CONFLICT = "FACT_CONFLICT"
    const val PENDING = "PENDING"

    /** Failures that count towards the circuit breaker. */
    val FAILURES = setOf(TIMEOUT, RATE_LIMITED, QUOTA_EXCEEDED, AUTH_FAILED, MODEL_NOT_FOUND, BAD_REQUEST, PROVIDER_ERROR, OFFLINE, SCHEMA_INVALID)

    /** Failures that will not fix themselves: wait until the user changes the settings. */
    val NEEDS_USER = setOf(AUTH_FAILED, MODEL_NOT_FOUND)
}

object AiPrompt {
    const val VERSION = "P1"
    const val SCHEMA_VERSION = "S1"
    fun versionFor(id: AiProviderId) = "${id.promptPrefix}-$VERSION"
}
