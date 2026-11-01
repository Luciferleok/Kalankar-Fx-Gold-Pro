package com.example.livegoldai.data

/**
 * V34.3: the provider's real latest XAU_USD quote (OANDA practice, read-only GET /pricing).
 *
 * Before, "LIVE" was judged from the time the phone fetched candles. A successful fetch of an old price still looked
 * fresh. Now the app also keeps OANDA's own quote time, bid, ask and its "tradeable" flag. Nothing here can place or
 * change an order, and no price is invented: on any error the quote is simply unavailable.
 */
object OandaPricing {
    data class Quote(val timeMs: Long, val bid: Double, val ask: Double, val tradeable: Boolean?) {
        val spread: Double get() = ask - bid
    }

    /** account ids from GET /accounts; empty on any problem */
    fun parseAccounts(body: String): List<String> = try {
        ((MiniJson.parse(body) as? Map<*, *>)?.get("accounts") as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.get("id") as? String } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    /** XAU_USD quote from GET /pricing; null unless time, bid and ask are all present and sane */
    fun parseQuote(body: String): Quote? = try {
        val prices = (MiniJson.parse(body) as? Map<*, *>)?.get("prices") as? List<*>
        val p = prices?.mapNotNull { it as? Map<*, *> }?.firstOrNull { it["instrument"] == null || it["instrument"] == OandaEndpoints.INSTRUMENT }
        if (p == null) null else {
            fun top(k: String) = ((p[k] as? List<*>)?.firstOrNull() as? Map<*, *>)?.get("price")?.toString()?.toDoubleOrNull()
            val bid = top("bids"); val ask = top("asks")
            val t = (p["time"] as? String)?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
            val tradeable = p["tradeable"] as? Boolean
            if (bid == null || ask == null || t == null || !bid.isFinite() || !ask.isFinite() || bid <= 0.0 || ask < bid) null
            else Quote(t, bid, ask, tradeable)
        }
    } catch (_: Exception) { null }

    /**
     * One quote. [account] is the id found earlier (null = find it now: the first account whose pricing answers).
     * Returns the account to remember and the quote (or null). Never throws.
     */
    fun fetch(t: OandaTransport, token: String, account: String?): Pair<String?, Quote?> {
        if (token.isBlank()) return null to null
        return try {
            val ids = if (account != null) listOf(account) else t.get(OandaEndpoints.accounts(), token).let { if (it.code == 200) parseAccounts(it.body) else emptyList() }
            for (id in ids.take(3)) {
                val r = t.get(OandaEndpoints.pricing(id), token)
                if (r.code == 200) { val q = parseQuote(r.body); if (q != null) return id to q }
            }
            account to null
        } catch (_: Exception) { account to null }
    }
}
