package com.example.livegoldai.data.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/** Real network transport (OkHttp). Each call has a hard total timeout. */
class OkHttpAiTransport : AiTransport {

    private val base = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun send(method: String, url: String, headers: Map<String, String>, body: String?, timeoutMs: Long): HttpReply {
        val started = System.currentTimeMillis()
        return try {
            val client = base.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
            val rb = Request.Builder().url(url)
            headers.forEach { (k, v) -> rb.header(k, v) }
            if (method == "POST") rb.post((body ?: "").toRequestBody("application/json; charset=utf-8".toMediaType())) else rb.get()
            client.newCall(rb.build()).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                val retry = resp.header("Retry-After")?.trim()?.toIntOrNull() ?: -1
                HttpReply(resp.code, text, System.currentTimeMillis() - started, retryAfterSec = retry)
            }
        } catch (e: InterruptedIOException) {
            // OkHttp reports both read timeouts and the total call timeout this way
            HttpReply(-1, "", System.currentTimeMillis() - started, timedOut = true, networkError = e.message ?: "timeout")
        } catch (e: Exception) {
            HttpReply(-1, "", System.currentTimeMillis() - started, networkError = e.message ?: e.javaClass.simpleName)
        }
    }
}
