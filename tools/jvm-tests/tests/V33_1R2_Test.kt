import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean) { if (ok) pass++ else { fail++; println("FAIL: $name") } }

/** V33.1R.2: plan levels fixed per window, honest learning label, DST-aware sessions. */
fun main() {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val now = sdf.parse("2026-10-28 10:22:10")!!.time
    val iv = 300_000L
    val r = Random(3); var p = 4100.0
    val candles = (0 until 150).map { i -> val o = p; p -= 0.35 + (r.nextDouble() - 0.5) * 1.2; CandleBar(sdf.format(Date((now / iv) * iv - (149 - i) * iv)), o, maxOf(o, p) + 0.6, minOf(o, p) - 0.6, p) }
    val td = FeedStatus("Twelve Data XAU/USD", now - 5_000, 300, true, true, true, 0, 1)
    val st = LedgerState(emptyList(), emptyMap(), emptyList())
    fun snap(cs: List<CandleBar>, at: Long, research: String = "COLLECTING"): GoldAnalysisResult {
        val raw = TechnicalEngine.analyze(cs, "5m").copy(feed = td.copy(fetchedAtMs = at - 5_000), sourceState = SourceStateMachine.PRIMARY_ACTIVE)
        return PlanStamp.stamp(raw, st, at, research)
    }
    // same forming candle, the live price moved 2.17 down (exactly the phone case)
    val first = snap(candles, now)
    val last = candles.last()
    val movedCandles = candles.dropLast(1) + last.copy(close = last.close - 2.17, low = minOf(last.low, last.close - 2.17))
    val moved = snap(movedCandles, now + 20_000)
    check("fixture: same snapshot, same side, engine levels moved", first.tradeSetup.snapshotId == moved.tradeSetup.snapshotId &&
        first.tradeSetup.signal == moved.tradeSetup.signal && first.tradeSetup.signal != Signal.WAIT &&
        kotlin.math.abs(first.tradeSetup.entryPrice - moved.tradeSetup.entryPrice) > 0.5)

    val store = mutableMapOf<String, LevelFreeze.Frozen>()
    val a1 = LevelFreeze.apply(first, store, now)
    val a2 = LevelFreeze.apply(moved, store, now + 20_000)
    val t1 = a1.tradeSetup; val t2 = a2.tradeSetup
    check("first refresh keeps its own levels", t1.entryPrice == first.tradeSetup.entryPrice && t1.levelsFixedAtMs == now)
    check("next refresh, same window: identical entry/SL/TP1/TP2", t2.entryPrice == t1.entryPrice && t2.stopLoss == t1.stopLoss && t2.takeProfit1 == t1.takeProfit1 && t2.takeProfit2 == t1.takeProfit2)
    check("pips kept", t2.stopLossPips == t1.stopLossPips && t2.takeProfit1Pips == t1.takeProfit1Pips && t2.takeProfit2Pips == t1.takeProfit2Pips)
    check("fixed-at time kept", t2.levelsFixedAtMs == now)
    check("R:R from the kept levels", t2.riskRewardRatio == RiskMath.label(t1.entryPrice, t1.stopLoss, t1.takeProfit1, t1.takeProfit2, t1.signal))
    check("strategy note quotes the kept entry", t2.strategyNote == first.tradeSetup.strategyNote)
    check("live price is NOT frozen", a2.currentPrice == moved.currentPrice && a2.currentPrice != a1.currentPrice)
    check("plan status/expiry untouched", t2.planStatus == moved.tradeSetup.planStatus && t2.expiresAtMs == moved.tradeSetup.expiresAtMs)
    val q1 = a1.quantBotSignal!!; val q2 = a2.quantBotSignal!!
    check("rule bot levels kept", q2.stopLoss == t1.stopLoss && q2.takeProfit1 == t1.takeProfit1 && q2.takeProfit2 == t1.takeProfit2)
    check("rule bot pips from kept levels", kotlin.math.abs(q2.pipsRisk - kotlin.math.abs(t1.entryPrice - t1.stopLoss) * 10.0) < 1e-9)
    check("rule bot entry kept when active", !q2.isTradeActive || q2.entryPrice == t1.entryPrice)
    val e2 = a2.multiBotEnsemble!!
    check("ensemble levels kept", e2.recommendedEntry == t1.entryPrice && e2.recommendedSl == t1.stopLoss)
    val sl1 = String.format(Locale.US, "%.2f", t1.stopLoss); val slLive = String.format(Locale.US, "%.2f", moved.tradeSetup.stopLoss)
    check("bot texts quote the kept stop, not the live one", e2.allBots.filter { it.suggestedOrder.contains("SL") }.all { it.suggestedOrder.contains(sl1) && !it.suggestedOrder.contains(slLive) })
    check("bot pips kept", e2.allBots.map { it.stopLossPips } == a1.multiBotEnsemble!!.allBots.map { it.stopLossPips })
    // scenario / copy / executive all read the kept levels
    val sc = ResearchScenario.of(a2, now + 20_000)!!
    check("scenario = kept levels", sc.entry == t1.entryPrice && sc.stop == t1.stopLoss && sc.fixedAtMs == now)
    check("plan text says when the levels were fixed", ResearchScenario.steps(sc, AppLanguage.ENGLISH).contains("fixed at 10:22 UTC"))
    for (l in AppLanguage.values()) check("steps $l mention fixed time", ResearchScenario.steps(sc, l).contains("10:22 UTC"))
    // requote: one pass, no chained replacement
    check("requote swaps in one pass", LevelFreeze.requote("A 1.00 B 2.00 C 3.5", mapOf("1.00" to "2.00", "2.00" to "1.00")) == "A 2.00 B 1.00 C 3.5")
    check("requote leaves other numbers", LevelFreeze.requote("EMA 4100.123 SL 4100.12", mapOf("4100.12" to "4099.00")) == "EMA 4100.123 SL 4099.00")

    // a new candle, or a side change, takes new levels
    val nextCandles = candles.drop(1) + CandleBar(sdf.format(Date((now / iv) * iv + iv)), last.close, last.close + 0.5, last.close - 3.0, last.close - 2.5)
    val nextBar = snap(nextCandles, now + iv + 10_000)
    val a3 = LevelFreeze.apply(nextBar, store, now + iv + 10_000)
    check("new candle -> new window", a3.tradeSetup.snapshotId != t1.snapshotId && (a3.tradeSetup.signal == Signal.WAIT || a3.tradeSetup.entryPrice == nextBar.tradeSetup.entryPrice))
    check("old window dropped from store", store.keys.none { it.startsWith(t1.snapshotId) } || a3.tradeSetup.signal == Signal.WAIT)
    val flipped = moved.copy(tradeSetup = moved.tradeSetup.copy(signal = if (moved.tradeSetup.signal == Signal.SELL) Signal.BUY else Signal.SELL))
    val s2 = mutableMapOf<String, LevelFreeze.Frozen>(); LevelFreeze.apply(first, s2, now)
    val a4 = LevelFreeze.apply(flipped, s2, now + 30_000)
    check("side change -> its own levels", a4.tradeSetup.entryPrice == flipped.tradeSetup.entryPrice && a4.tradeSetup.levelsFixedAtMs == now + 30_000)
    check("WAIT untouched", LevelFreeze.apply(first.copy(tradeSetup = first.tradeSetup.copy(signal = Signal.WAIT)), mutableMapOf(), now).tradeSetup.levelsFixedAtMs == 0L)
    check("store stays small", run { val m = mutableMapOf<String, LevelFreeze.Frozen>(); for (k in 0 until 100) LevelFreeze.apply(first.copy(tradeSetup = first.tradeSetup.copy(snapshotId = "S-x$k-2026")), m, now); m.size <= LevelFreeze.CAP })

    // ---------- sessions follow each city's clock
    fun at(s: String) = sdf.parse(s)!!.time
    fun sess(ms: Long) = SessionClock.sessions(ms).associateBy { it.name }
    val oct30 = sess(at("2026-10-30 13:55:00"))     // UK winter, US still summer time
    check("30 Oct: London 08:00-17:00 UTC", oct30.getValue("London").timeWindowUtc == "08:00 - 17:00 UTC")
    check("30 Oct: New York 12:00-21:00 UTC (US summer time)", oct30.getValue("New York").timeWindowUtc == "12:00 - 21:00 UTC")
    check("30 Oct 13:55: overlap", oct30.getValue("London").isGoldenOverlap && oct30.getValue("New York").isGoldenOverlap)
    val nov2 = sess(at("2026-11-02 12:30:00"))       // after US change (1 Nov)
    check("2 Nov: New York 13:00-22:00 UTC", nov2.getValue("New York").timeWindowUtc == "13:00 - 22:00 UTC")
    check("2 Nov 12:30: New York not open yet", !nov2.getValue("New York").isOpen)
    val jul = sess(at("2026-07-15 07:30:00"))
    check("July: London 07:00-16:00 UTC and open at 07:30", jul.getValue("London").timeWindowUtc == "07:00 - 16:00 UTC" && jul.getValue("London").isOpen)
    check("July: Sydney 23:00-08:00 UTC", jul.getValue("Sydney").timeWindowUtc == "23:00 - 08:00 UTC")
    check("Jan: Sydney 22:00-07:00 UTC", sess(at("2026-01-14 03:00:00")).getValue("Sydney").timeWindowUtc == "22:00 - 07:00 UTC")
    check("Tokyo 00:00-09:00 UTC all year", oct30.getValue("Tokyo").timeWindowUtc == "00:00 - 09:00 UTC" && jul.getValue("Tokyo").timeWindowUtc == "00:00 - 09:00 UTC")
    check("Sydney open across UTC midnight (Jan 03:00)", sess(at("2026-01-14 03:00:00")).getValue("Sydney").isOpen)
    check("Saturday: all closed", sess(at("2026-10-31 10:00:00")).values.none { it.isOpen || it.isGoldenOverlap })
    check("engine uses the clock", TechnicalEngine.calculateMarketSessions().size == 4)

    // ---------- static guards
    val root = File("app/src/main/java/com/example/livegoldai")
    val src = root.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.associate { it.relativeTo(root).path to it.readText() }
    val dual = src.getValue("ui/components/DualPredictionAndIndicatorsSection.kt")
    check("no 'AUTO-CORRECTION ACTIVE' / 'Calibrated for next win' / GUARDS", src.values.none { it.contains("AUTO-CORRECTION ACTIVE") || it.contains("Calibrated for next win") || it.contains("GUARDS 🛡") })
    check("learning label counts learned items only", dual.contains("it.badgeTag.startsWith(\"LEARNED\")") && dual.contains("no learned correction in use yet"))
    val vm = src.getValue("ui/GoldViewModel.kt")
    val iStamp = vm.indexOf("PlanStamp.stamp(withBrain"); val iFreeze = vm.indexOf("LevelFreeze.apply(stamped0"); val iPres = vm.indexOf("PresentationBuilder.build(stamped,")
    check("VM: stamp -> freeze -> presentation", iStamp in 1 until iFreeze && iFreeze < iPres)
    val eng = src.getValue("data/TechnicalEngine.kt")
    check("engine levels formula unchanged", eng.contains("val pullbackEntry = min(currentPrice + (0.40 * atrSafe), fib0500)") && eng.contains("val pullbackEntry = max(currentPrice - (0.40 * atrSafe), fib0618)"))
    check("no fixed UTC session hours left in engine", !eng.contains("\"13:00 - 22:00 UTC\"") && !eng.contains("utcHour in 13..16"))
    val led = src.getValue("data/PredictionLedger.kt")
    check("ledger session rule untouched", led.contains("fun isMarketClosed(ms: Long): Boolean") && led.contains("Calendar.FRIDAY -> hour >= 21"))

    println("V33.1R.2: $pass passed, $fail failed")
    if (fail > 0) System.exit(1)
}
