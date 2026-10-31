package com.example.livegoldai.data

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * V33.1R.4-a: position size that never exceeds the user's cash-risk budget.
 *
 * Defects fixed (LotCalculatorDialog at b5baa0f):
 *  - the lot was clamped to at least 0.01, so a $5 budget could carry $10 of risk;
 *  - blank or malformed input silently became $5,000 / 2% / 50 pips;
 *  - the stop was rounded to whole pips before sizing;
 *  - TP profits used the requested budget, not the rounded volume;
 *  - a $10 pip value and 1:100 leverage were assumed for every broker.
 *
 * The risk per lot is computed from the exact price distance (entry to stop, plus an optional slippage allowance) and the
 * broker's contract size. No pip definition is assumed. Every cash figure uses the SAME rounded-down volume.
 * Pure Kotlin, no Android types, no network: it computes, it never places anything.
 */
object PositionSizer {

    /** Broker contract specification. null = the user has not provided it. */
    data class BrokerSpec(
        val contractSize: Double?,      // units of the instrument per 1.00 lot (many XAU/USD brokers: 100 oz; not universal)
        val minVolume: Double?,
        val volumeStep: Double?,
        val maxVolume: Double?,
        val leverage: Double?,          // 100 for 1:100; only needed for margin
        val accountCurrency: String?,   // "USD", "INR", ...
        val usdToAccountRate: Double?   // 1 USD in account currency; not needed when the account is USD
    )

    data class Input(
        val balanceText: String, val riskPercentText: String, val slippageText: String,
        val entry: Double, val stop: Double, val tp1: Double, val tp2: Double, val price: Double
    )

    sealed class Result {
        /** Input the user typed is not usable. Nothing is computed. */
        data class Invalid(val errors: List<String>) : Result()
        /** Broker data is missing or invalid: no lot is recommended. */
        data class Unavailable(val missing: List<String>) : Result()
        /** Even the broker's minimum volume would risk more than the budget. */
        data class NoSafeSize(val budget: Double, val minVolume: Double, val riskAtMinimum: Double, val currency: String) : Result()
        data class Ok(
            val volume: Double, val budget: Double, val plannedRisk: Double,
            val tp1Profit: Double?, val tp2Profit: Double?, val margin: Double?,
            val stopDistance: Double, val slippage: Double, val currency: String, val cappedByMaxVolume: Boolean
        ) : Result()
    }

    const val E_BALANCE = "BALANCE"; const val E_RISK = "RISK_PERCENT"; const val E_SLIPPAGE = "SLIPPAGE"; const val E_LEVELS = "PLAN_LEVELS"
    const val M_CONTRACT = "CONTRACT_SIZE"; const val M_MIN = "MIN_VOLUME"; const val M_STEP = "VOLUME_STEP"; const val M_MAX = "MAX_VOLUME"
    const val M_CURRENCY = "ACCOUNT_CURRENCY"; const val M_RATE = "USD_TO_ACCOUNT_RATE"

    /** Strict number: finite, no fallback. Accepts "1,000.50". null when not a number. */
    fun parse(text: String?): Double? {
        val t = text?.trim()?.replace(",", "") ?: return null
        if (t.isEmpty()) return null
        val v = t.toDoubleOrNull() ?: return null
        return if (v.isFinite()) v else null
    }

    private fun pos(x: Double?) = x != null && x.isFinite() && x > 0.0

    fun specProblems(s: BrokerSpec): List<String> {
        val m = ArrayList<String>()
        if (!pos(s.contractSize)) m += M_CONTRACT
        if (!pos(s.minVolume)) m += M_MIN
        if (!pos(s.volumeStep)) m += M_STEP
        if (!pos(s.maxVolume) || (pos(s.minVolume) && s.maxVolume!! < s.minVolume!!)) m += M_MAX
        val cur = s.accountCurrency?.trim()?.uppercase()
        if (cur.isNullOrEmpty() || !Regex("[A-Z]{3}").matches(cur)) m += M_CURRENCY
        else if (cur != "USD" && !pos(s.usdToAccountRate)) m += M_RATE
        return m
    }

    /** Largest multiple of [step] that is <= [x]. Exact decimal arithmetic, so 0.03 / 0.01 is 3 steps, not 2.999. */
    fun floorToStep(x: Double, step: Double): Double {
        if (x <= 0.0) return 0.0
        val s = BigDecimal.valueOf(step)
        val n = BigDecimal.valueOf(x).divide(s, 0, RoundingMode.FLOOR)
        return n.multiply(s).toDouble()
    }

    fun size(inp: Input, spec: BrokerSpec): Result {
        val errors = ArrayList<String>()
        val balance = parse(inp.balanceText)
        val riskPct = parse(inp.riskPercentText)
        val slippage = if (inp.slippageText.isBlank()) 0.0 else parse(inp.slippageText)
        if (balance == null || balance <= 0.0) errors += E_BALANCE
        if (riskPct == null || riskPct <= 0.0 || riskPct > 100.0) errors += E_RISK
        if (slippage == null || slippage < 0.0) errors += E_SLIPPAGE
        val dist = kotlin.math.abs(inp.entry - inp.stop)
        if (!inp.entry.isFinite() || !inp.stop.isFinite() || inp.entry <= 0.0 || inp.stop <= 0.0 || dist <= 1e-9) errors += E_LEVELS
        if (errors.isNotEmpty()) return Result.Invalid(errors)

        val missing = specProblems(spec)
        if (missing.isNotEmpty()) return Result.Unavailable(missing)

        val cur = spec.accountCurrency!!.trim().uppercase()
        val rate = if (cur == "USD") 1.0 else spec.usdToAccountRate!!
        val budget = balance!! * riskPct!! / 100.0
        val lossPerLot = (dist + slippage!!) * spec.contractSize!! * rate          // account currency, at the stop + slippage
        val minRisk = spec.minVolume!! * lossPerLot
        val raw = budget / lossPerLot
        var vol = floorToStep(minOf(raw, spec.maxVolume!!), spec.volumeStep!!)
        // floating guard: never above the budget
        while (vol > 0.0 && vol * lossPerLot > budget * (1 + 1e-12)) vol = floorToStep(vol - spec.volumeStep, spec.volumeStep)
        if (vol + 1e-12 < spec.minVolume) return Result.NoSafeSize(budget, spec.minVolume, minRisk, cur)

        val isLong = inp.stop < inp.entry
        fun profit(tp: Double): Double? {
            if (!tp.isFinite() || tp <= 0.0) return null
            val reward = if (isLong) tp - inp.entry else inp.entry - tp
            return if (reward <= 0.0) null else vol * reward * spec.contractSize * rate
        }
        val margin = if (pos(spec.leverage) && inp.price.isFinite() && inp.price > 0.0)
            vol * spec.contractSize * inp.price * rate / spec.leverage!! else null
        return Result.Ok(vol, budget, vol * lossPerLot, profit(inp.tp1), profit(inp.tp2), margin, dist, slippage, cur,
            cappedByMaxVolume = raw > spec.maxVolume)
    }
}
