package com.example.livegoldai.data

import com.example.livegoldai.localization.AppLanguage
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * OANDA V20 PRACTICE (V32 stage 1): connection check only, read-only.
 *
 *  - The token is typed by the user in Settings and kept encrypted on this phone (Android Keystore). It is never in the
 *    source code, the APK, the repo, logs, the UI state or any error text.
 *  - Only GET requests to the PRACTICE host. This app contains no order, trade or position endpoint: order execution
 *    is impossible, not just switched off.
 *  - "CONNECTED" is only ever produced from real, parsed OANDA responses. Nothing is assumed (not even that XAU_USD
 *    exists on the account) and nothing is filled in.
 *  - Stage 1 does NOT feed the analysis. The active analysis provider stays Twelve Data until stage 2.
 */
object OandaEndpoints {
    const val ENV = "practice"
    const val REST = "https://api-fxpractice.oanda.com/v3"
    const val STREAM = "https://stream-fxpractice.oanda.com/v3"
    const val INSTRUMENT = "XAU_USD"

    fun accounts() = "$REST/accounts"
    fun instruments(account: String) = "$REST/accounts/$account/instruments?instruments=$INSTRUMENT"
    /** Latest few completed + developing 1-minute mid candles, used only to prove real data arrives. */
    fun probeCandles() = "$REST/instruments/$INSTRUMENT/candles?count=3&granularity=M1&price=M"

    /** Every URL this app may call on OANDA. A test asserts nothing here can place or change an order. */
    fun all(account: String = "ACC") = listOf(accounts(), instruments(account), probeCandles())
}

/** One HTTP answer. code 0 = no answer at all; then [error] is TIMEOUT or NETWORK. */
data class OandaHttp(val code: Int, val body: String, val error: String = "")

/** The only network seam: the real one uses OkHttp, tests use a fake. Must send "Authorization: Bearer <token>". */
fun interface OandaTransport { fun get(url: String, token: String): OandaHttp }

enum class OandaStatus {
    NOT_CONFIGURED, CONNECTED, XAU_UNAVAILABLE, NO_ACCOUNT,
    TOKEN_INVALID, FORBIDDEN, NOT_FOUND, BAD_REQUEST, RATE_LIMITED,
    TIMEOUT, NETWORK, SERVICE_ERROR, BAD_RESPONSE, COOLDOWN
}

data class OandaHealth(
    val status: OandaStatus,
    val step: String = "",               // ACCOUNTS / INSTRUMENTS / CANDLES: where it stopped
    val httpCode: Int = 0,
    val detail: String = "",             // OANDA's own errorMessage, cleaned, never containing the token
    val accounts: Int = 0,
    val account: String = "",            // masked: "…-001"
    val xauAvailable: Boolean? = null,   // null = not checked yet
    val xauName: String = "",            // OANDA displayName, e.g. "Gold"
    val lastCandleTime: String = "",     // UTC, last COMPLETED M1 candle
    val lastCandleMs: Long = 0L,
    val lastClose: Double = Double.NaN,  // mid close of that completed candle, exactly as OANDA sent it
    val developing: Boolean = false,     // a not-yet-complete candle was also returned (shown, never used)
    val latencyMs: Long = 0L,            // the candles request round trip
    val checkedAtMs: Long = 0L,
    val requests: Int = 0
) {
    val connected get() = status == OandaStatus.CONNECTED
}

object OandaDiagnostics {

    /** Conservative own limit for the manual test: one full check per 15 s (3 requests), never a burst. */
    const val MIN_GAP_MS = 15_000L
    private const val MAX_ACCOUNTS_CHECKED = 3

    fun maskAccount(id: String): String = if (id.length <= 4) "…" else "…" + id.takeLast(4)

    /** OANDA error text without anything that could be the token, max 140 chars. */
    fun clean(body: String, token: String): String {
        val msg = (try { (MiniJson.parse(body) as? Map<*, *>)?.get("errorMessage") as? String } catch (_: Exception) { null }) ?: ""
        var s = msg
        if (token.isNotBlank()) s = s.replace(token.trim(), "••••")
        s = s.replace(Regex("Bearer\\s+\\S+", RegexOption.IGNORE_CASE), "Bearer ••••")
        s = s.replace(Regex("[0-9a-fA-F]{24,}-[0-9a-fA-F]{24,}"), "••••")
        return s.take(140)
    }

    fun mapCode(code: Int, error: String): OandaStatus = when {
        code == 0 && error == "TIMEOUT" -> OandaStatus.TIMEOUT
        code == 0 -> OandaStatus.NETWORK
        code == 400 -> OandaStatus.BAD_REQUEST
        code == 401 -> OandaStatus.TOKEN_INVALID
        code == 403 -> OandaStatus.FORBIDDEN
        code == 404 -> OandaStatus.NOT_FOUND
        code == 429 -> OandaStatus.RATE_LIMITED
        code >= 500 -> OandaStatus.SERVICE_ERROR
        else -> OandaStatus.BAD_RESPONSE
    }

    /** RFC3339 "2026-10-27T07:31:00.000000000Z" -> epoch ms (UTC). null if it is not that shape. */
    fun parseTime(s: String): Long? {
        if (s.length < 20 || s[10] != 'T' || !s.endsWith("Z")) return null
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }
                .parse(s.substring(0, 19))?.time
        } catch (_: Exception) { null }
    }

    fun utc(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(java.util.Date(ms)) + " UTC"

    /**
     * Full read-only check: accounts -> XAU_USD on an account -> latest M1 candles.
     * [clock] is only used for timestamps and latency.
     */
    fun run(t: OandaTransport, token: String, clock: () -> Long = { System.currentTimeMillis() }): OandaHealth {
        val start = clock()
        if (token.isBlank()) return OandaHealth(OandaStatus.NOT_CONFIGURED, checkedAtMs = start)
        var n = 0
        fun fail(step: String, r: OandaHttp, extra: OandaHealth.() -> OandaHealth = { this }) =
            OandaHealth(mapCode(r.code, r.error), step, r.code, if (r.code == 0) r.error else clean(r.body, token), checkedAtMs = clock(), requests = n).extra()

        // 1. authentication + account discovery
        val acc = t.get(OandaEndpoints.accounts(), token); n++
        if (acc.code != 200) return fail("ACCOUNTS", acc)
        val ids = try {
            ((MiniJson.parse(acc.body) as? Map<*, *>)?.get("accounts") as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.get("id") as? String }
        } catch (_: Exception) { null } ?: return OandaHealth(OandaStatus.BAD_RESPONSE, "ACCOUNTS", 200, checkedAtMs = clock(), requests = n)
        if (ids.isEmpty()) return OandaHealth(OandaStatus.NO_ACCOUNT, "ACCOUNTS", 200, checkedAtMs = clock(), requests = n)

        // 2. account-specific instrument discovery: never assume XAU_USD is there
        var found: String? = null
        var name = ""
        var lastFail: OandaHttp? = null
        for (id in ids.take(MAX_ACCOUNTS_CHECKED)) {
            val r = t.get(OandaEndpoints.instruments(id), token); n++
            if (r.code == 400) continue                          // OANDA rejects an instrument this account cannot use
            if (r.code != 200) { lastFail = r; break }
            val list = try { (MiniJson.parse(r.body) as? Map<*, *>)?.get("instruments") as? List<*> } catch (_: Exception) { null }
                ?: return OandaHealth(OandaStatus.BAD_RESPONSE, "INSTRUMENTS", 200, accounts = ids.size, checkedAtMs = clock(), requests = n)
            val xau = list.mapNotNull { it as? Map<*, *> }.firstOrNull { it["name"] == OandaEndpoints.INSTRUMENT }
            if (xau != null) { found = id; name = xau["displayName"] as? String ?: ""; break }
        }
        lastFail?.let { r -> return fail("INSTRUMENTS", r) { copy(accounts = ids.size) } }
        if (found == null) return OandaHealth(OandaStatus.XAU_UNAVAILABLE, "INSTRUMENTS", 200, accounts = ids.size, xauAvailable = false, checkedAtMs = clock(), requests = n)

        // 3. real candles: proves price data arrives. Only a COMPLETED candle is reported as the last price.
        val c0 = clock()
        val cr = t.get(OandaEndpoints.probeCandles(), token); n++
        val lat = clock() - c0
        val base = OandaHealth(OandaStatus.CONNECTED, "CANDLES", cr.code, accounts = ids.size, account = maskAccount(found), xauAvailable = true, xauName = name, latencyMs = lat, requests = n)
        if (cr.code != 200) return fail("CANDLES", cr) { copy(accounts = ids.size, account = maskAccount(found), xauAvailable = true, xauName = name) }
        val candles = try { (MiniJson.parse(cr.body) as? Map<*, *>)?.get("candles") as? List<*> } catch (_: Exception) { null }
            ?: return base.copy(status = OandaStatus.BAD_RESPONSE, checkedAtMs = clock())
        val rows = candles.mapNotNull { it as? Map<*, *> }
        val developing = rows.any { it["complete"] == false }
        val done = rows.filter { it["complete"] == true }.mapNotNull { m ->
            val ts = (m["time"] as? String)?.let { parseTime(it) } ?: return@mapNotNull null
            val c = ((m["mid"] as? Map<*, *>)?.get("c") as? String)?.toDoubleOrNull()?.takeIf { it > 0 && !it.isNaN() } ?: return@mapNotNull null
            ts to c
        }.maxByOrNull { it.first }
        // Connected even with no completed candle (e.g. market closed): the account and instrument answers were real.
        return base.copy(
            lastCandleMs = done?.first ?: 0L, lastCandleTime = done?.let { utc(it.first) } ?: "",
            lastClose = done?.second ?: Double.NaN, developing = developing, checkedAtMs = clock()
        )
    }
}

/** Shared protection for the manual "TEST" button: one check at a time, at most one per [OandaDiagnostics.MIN_GAP_MS]. */
class OandaProbeGate {
    private var lastStart = 0L
    private var running = false
    @Synchronized fun tryStart(now: Long): Boolean {
        if (running || (lastStart > 0 && now - lastStart < OandaDiagnostics.MIN_GAP_MS)) return false
        running = true; lastStart = now; return true
    }
    @Synchronized fun done() { running = false }
    @Synchronized fun waitMs(now: Long): Long = if (lastStart == 0L) 0 else maxOf(0L, OandaDiagnostics.MIN_GAP_MS - (now - lastStart))
}

/** Market Data Health panel text (EN / HI / MR). Every line comes from real state; unbuilt parts say so. */
object OandaText {
    private fun t(l: AppLanguage, en: String, hi: String, mr: String) = when (l) { AppLanguage.ENGLISH -> en; AppLanguage.HINDI -> hi; AppLanguage.MARATHI -> mr }

    /** No part of the token is ever shown, not even a few characters. */
    fun tokenLine(saved: Boolean, l: AppLanguage) =
        if (saved) t(l, "Saved, encrypted on this phone • practice", "सेव, इस फ़ोन में एन्क्रिप्टेड • practice", "सेव्ह, या फोनमध्ये एन्क्रिप्टेड • practice")
        else t(l, "Not set • paste your OANDA practice token", "सेट नहीं • OANDA practice टोकन पेस्ट करें", "सेट नाही • OANDA practice टोकन पेस्ट करा")

    fun status(h: OandaHealth?, l: AppLanguage): String {
        if (h == null) return t(l, "Not tested yet", "अभी टेस्ट नहीं हुआ", "अजून टेस्ट नाही")
        val c = if (h.httpCode > 0) " (HTTP ${h.httpCode})" else ""
        return when (h.status) {
            OandaStatus.NOT_CONFIGURED -> t(l, "No token saved", "टोकन सेव नहीं", "टोकन सेव्ह नाही")
            OandaStatus.CONNECTED -> t(l, "CONNECTED • real OANDA practice response", "CONNECTED • OANDA practice से असली जवाब", "CONNECTED • OANDA practice कडून खरा प्रतिसाद")
            OandaStatus.XAU_UNAVAILABLE -> t(l, "Connected, but XAU_USD is NOT available on this account", "कनेक्ट, पर इस अकाउंट में XAU_USD उपलब्ध नहीं", "कनेक्ट, पण या खात्यात XAU_USD उपलब्ध नाही")
            OandaStatus.NO_ACCOUNT -> t(l, "Token works but no account found", "टोकन चला पर कोई अकाउंट नहीं मिला", "टोकन चालला पण खाते सापडले नाही")
            OandaStatus.TOKEN_INVALID -> t(l, "TOKEN REJECTED$c: wrong, expired, revoked, or a LIVE token on practice", "टोकन अस्वीकार$c: गलत, एक्सपायर, रद्द, या practice पर LIVE टोकन", "टोकन नाकारला$c: चुकीचा, कालबाह्य, रद्द, किंवा practice वर LIVE टोकन")
            OandaStatus.FORBIDDEN -> t(l, "ACCESS DENIED$c: token has no access to this account", "एक्सेस नहीं$c: टोकन को इस अकाउंट की अनुमति नहीं", "प्रवेश नाही$c: टोकनला या खात्याची परवानगी नाही")
            OandaStatus.NOT_FOUND -> t(l, "NOT FOUND$c: account or endpoint not on practice", "नहीं मिला$c: अकाउंट/endpoint practice पर नहीं", "सापडले नाही$c: खाते/endpoint practice वर नाही")
            OandaStatus.BAD_REQUEST -> t(l, "REQUEST REJECTED$c", "रिक्वेस्ट अस्वीकार$c", "रिक्वेस्ट नाकारली$c")
            OandaStatus.RATE_LIMITED -> t(l, "RATE LIMITED$c: wait and test again", "रेट लिमिट$c: रुककर फिर टेस्ट करें", "रेट लिमिट$c: थांबून पुन्हा टेस्ट करा")
            OandaStatus.TIMEOUT -> t(l, "TIMEOUT: OANDA did not answer in time", "टाइमआउट: OANDA ने समय पर जवाब नहीं दिया", "टाइमआउट: OANDA ने वेळेत उत्तर दिले नाही")
            OandaStatus.NETWORK -> t(l, "NO NETWORK: could not reach OANDA", "नेटवर्क नहीं: OANDA तक नहीं पहुँचे", "नेटवर्क नाही: OANDA पर्यंत पोहोचलो नाही")
            OandaStatus.SERVICE_ERROR -> t(l, "OANDA SERVICE ERROR$c: try later", "OANDA सर्विस एरर$c: बाद में", "OANDA सर्व्हिस एरर$c: नंतर")
            OandaStatus.BAD_RESPONSE -> t(l, "UNREADABLE RESPONSE$c: not used", "जवाब पढ़ा नहीं गया$c: इस्तेमाल नहीं", "प्रतिसाद वाचता आला नाही$c: वापरला नाही")
            OandaStatus.COOLDOWN -> t(l, "Wait a few seconds between tests", "टेस्ट के बीच कुछ सेकंड रुकें", "टेस्टमध्ये काही सेकंद थांबा")
        } + if (h.detail.isNotBlank() && h.status != OandaStatus.CONNECTED) " • ${h.detail}" else ""
    }

    private fun ago(ms: Long, now: Long, l: AppLanguage): String {
        val s = (now - ms) / 1000
        return when {
            s < 90 -> t(l, "${s}s ago", "${s} सेकंड पहले", "${s} सेकंदांपूर्वी")
            s < 5400 -> t(l, "${s / 60} min ago", "${s / 60} मिनट पहले", "${s / 60} मिनिटांपूर्वी")
            else -> t(l, "${s / 3600} h ago", "${s / 3600} घंटे पहले", "${s / 3600} तासांपूर्वी")
        }
    }

    /** Rows of the Market Data Health panel: label to value. [activeProvider] = the badge of the feed the analysis uses now. */
    fun panel(h: OandaHealth?, tokenSaved: Boolean, activeProvider: String, now: Long, l: AppLanguage): List<Pair<String, String>> {
        val notBuilt = { stage: Int -> t(l, "Not built yet (stage $stage)", "अभी नहीं बना (स्टेज $stage)", "अजून बनले नाही (स्टेज $stage)") }
        val rows = mutableListOf<Pair<String, String>>()
        rows += t(l, "Active provider (analysis)", "सक्रिय प्रोवाइडर (विश्लेषण)", "सक्रिय प्रोव्हायडर (विश्लेषण)") to
            (activeProvider.ifBlank { "--" } + t(l, " • OANDA is not used for analysis yet", " • OANDA अभी विश्लेषण में नहीं", " • OANDA अजून विश्लेषणात नाही"))
        rows += t(l, "OANDA practice API", "OANDA practice API", "OANDA practice API") to
            (if (!tokenSaved) status(OandaHealth(OandaStatus.NOT_CONFIGURED), l) else status(h, l))
        rows += "XAU/USD" to when (h?.xauAvailable) {
            true -> t(l, "Available on account ${h.account}", "अकाउंट ${h.account} पर उपलब्ध", "खाते ${h.account} वर उपलब्ध") + if (h.xauName.isNotBlank()) " (${h.xauName})" else ""
            false -> t(l, "NOT available on your account(s)", "आपके अकाउंट पर उपलब्ध नहीं", "तुमच्या खात्यावर उपलब्ध नाही")
            null -> t(l, "Not checked", "जाँच नहीं हुई", "तपासले नाही")
        }
        rows += t(l, "Last check", "आख़िरी जाँच", "शेवटची तपासणी") to
            (if (h == null || h.checkedAtMs == 0L) "--" else utcHm(h.checkedAtMs) + " • " + ago(h.checkedAtMs, now, l))
        rows += t(l, "Latency (candles request)", "लेटेंसी (कैंडल रिक्वेस्ट)", "लेटन्सी (कँडल रिक्वेस्ट)") to
            (if (h != null && h.connected) "${h.latencyMs} ms" else "--")
        rows += t(l, "Last completed M1 candle", "आख़िरी पूरी M1 कैंडल", "शेवटची पूर्ण M1 कँडल") to when {
            h == null || !h.connected -> "--"
            h.lastCandleMs == 0L -> t(l, "None returned (market may be closed)", "कोई नहीं मिली (मार्केट बंद हो सकता है)", "मिळाली नाही (मार्केट बंद असू शकते)")
            else -> h.lastCandleTime + " • mid " + String.format(Locale.US, "%.3f", h.lastClose) + " • " + ago(h.lastCandleMs, now, l)
        }
        rows += t(l, "Data freshness", "डेटा ताज़गी", "डेटा ताजेपणा") to when {
            h == null || !h.connected || h.lastCandleMs == 0L -> "--"
            PredictionLedger.isMarketClosed(now) -> Freshness.CLOSED
            now - h.lastCandleMs <= 3 * 60_000L -> "LIVE"
            else -> "STALE"
        }
        rows += t(l, "Streaming", "स्ट्रीमिंग", "स्ट्रीमिंग") to notBuilt(3)
        rows += t(l, "Historical candle sync", "हिस्टोरिकल कैंडल सिंक", "हिस्टोरिकल कँडल सिंक") to notBuilt(2)
        rows += t(l, "Reconnection", "री-कनेक्शन", "री-कनेक्शन") to notBuilt(3)
        rows += t(l, "Rate limit", "रेट लिमिट", "रेट लिमिट") to
            (if (h?.status == OandaStatus.RATE_LIMITED) t(l, "WARNING: OANDA answered 429", "चेतावनी: OANDA ने 429 दिया", "इशारा: OANDA ने 429 दिला")
             else t(l, "OK • test limited to 1 per 15 s (3 requests)", "OK • 15 सेकंड में 1 टेस्ट (3 रिक्वेस्ट)", "OK • 15 सेकंदात 1 टेस्ट (3 रिक्वेस्ट)"))
        rows += t(l, "Order execution", "ऑर्डर एक्सीक्यूशन", "ऑर्डर एक्झिक्युशन") to
            t(l, "DISABLED • the app has no order code", "बंद • ऐप में ऑर्डर का कोड ही नहीं", "बंद • ॲपमध्ये ऑर्डरचा कोडच नाही")
        return rows
    }

    private fun utcHm(ms: Long) = SimpleDateFormat("HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(java.util.Date(ms)) + " UTC"
}
