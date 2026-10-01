package com.example.livegoldai.data.ai

/** Result of one HTTP exchange. code = -1 when no HTTP answer arrived. */
data class HttpReply(
    val code: Int,
    val body: String,
    val latencyMs: Long,
    val timedOut: Boolean = false,
    val networkError: String? = null,
    val retryAfterSec: Int = -1
)

/** Blocking HTTP used by the AI adapters. Swappable so the logic can be tested without network. */
interface AiTransport {
    fun send(method: String, url: String, headers: Map<String, String>, body: String?, timeoutMs: Long): HttpReply
}
