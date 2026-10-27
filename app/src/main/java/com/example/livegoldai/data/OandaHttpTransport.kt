package com.example.livegoldai.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Real OANDA transport (V32 stage 1). GET only, practice host only.
 * No logging interceptor and no Log calls: the Authorization header is never written anywhere.
 * Exceptions are reduced to TIMEOUT / NETWORK so no exception text (which could include request details) reaches the UI.
 */
class OkHttpOandaTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
) : OandaTransport {
    override fun get(url: String, token: String): OandaHttp {
        // Hard guard: this transport can only ever talk to the OANDA practice REST host.
        if (!url.startsWith(OandaEndpoints.REST + "/")) return OandaHttp(0, "", "NETWORK")
        return try {
            val req = Request.Builder().url(url).get()
                .header("Authorization", "Bearer " + token.trim())
                .header("Accept-Datetime-Format", "RFC3339")
                .build()
            client.newCall(req).execute().use { r -> OandaHttp(r.code, r.body?.string() ?: "") }
        } catch (_: java.net.SocketTimeoutException) {
            OandaHttp(0, "", "TIMEOUT")
        } catch (_: java.io.InterruptedIOException) {
            OandaHttp(0, "", "TIMEOUT")
        } catch (_: Exception) {
            OandaHttp(0, "", "NETWORK")
        }
    }
}
