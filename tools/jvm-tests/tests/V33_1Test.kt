import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean) { if (ok) pass++ else { fail++; println("FAIL: $name") } }

/** V33.1: research gate (domain + ViewModel + dialog) and honest metric names. Deterministic clock. */
fun main() {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val now = sdf.parse("2026-10-28 08:02:10")!!.time          // Wednesday, market open by the app's calendar
    check("fixed clock is a market day", !PredictionLedger.isMarketClosed(now))
    val iv = 300_000L
    val r = Random(7); var p = 4000.0
    val candles = (0 until 150).map { i -> val o = p; p += 0.4 + (r.nextDouble() - 0.5) * 2; CandleBar(sdf.format(Date((now / iv) * iv - (149 - i) * iv)), o, maxOf(o, p) + 1, minOf(o, p) - 1, p) }
    val td = FeedStatus("Twelve Data XAU/USD", now - 5_000, 300, true, true, true, 0, 1)
    val px = candles.last().close
    val base = TechnicalEngine.analyze(candles, "5m").let { a ->
        a.copy(feed = td, overallSignal = Signal.BUY, rawSignal = Signal.BUY, sourceState = SourceStateMachine.PRIMARY_ACTIVE,
            tradeSetup = a.tradeSetup.copy(signal = Signal.BUY, entryPrice = px, stopLoss = px - 5.0, takeProfit1 = px + 6.0, takeProfit2 = px + 12.0, stopLossPips = 50.0))
    }
    val st = LedgerState(emptyList(), emptyMap(), emptyList())
    fun present(a: GoldAnalysisResult, research: String?, at: Long = now): GoldAnalysisResult {
        val s = PlanStamp.stamp(a, st, at, research)
        return s.copy(presentation = PresentationBuilder.build(s, st, research, at))
    }
    val active = present(base, "LIVE QUALIFIED")
    val snap = active.tradeSetup.snapshotId
    check("fixture is an ACTIVE plan", active.tradeSetup.planStatus == PlanStamp.ACTIVE && active.presentation?.provenEdge == true)

    // ---------- 1. the one allowed case
    val ok = ResearchGate.authorize(active, snap, "5m", now)
    check("ACTIVE + same snapshot/timeframe/provider -> granted", ok.allowed && ok.refusals.isEmpty())
    val g = ok.grant!!
    check("grant copies the plan (not caller input)", g.snapshotId == snap && g.stopPips == 50.0 && g.entry == px && g.stop == px - 5.0 && g.interval == "5m" && g.dataSource == "Twelve Data XAU/USD")
    check("interval match ignores case", ResearchGate.authorize(active, snap, "5M", now).allowed)

    // ---------- 2. every refusal, one at a time
    fun refused(name: String, a: GoldAnalysisResult?, s: String?, i: String?, at: Long, code: String) {
        val d = ResearchGate.authorize(a, s, i, at)
        check("$name -> refused with $code (got ${d.refusals})", !d.allowed && d.grant == null && code in d.refusals)
    }
    refused("no snapshot", null, snap, "5m", now, ResearchGate.NO_SNAPSHOT)
    refused("research (not qualified)", present(base, "COLLECTING"), present(base, "COLLECTING").tradeSetup.snapshotId, "5m", now, ResearchGate.PLAN_NOT_ACTIVE)
    refused("research also NOT_QUALIFIED", present(base, "COLLECTING"), present(base, "COLLECTING").tradeSetup.snapshotId, "5m", now, ResearchGate.NOT_QUALIFIED)
    refused("unknown research status", present(base, null), snap, "5m", now, ResearchGate.PLAN_NOT_ACTIVE)
    refused("other snapshot on screen", active, "S-5m-2026-10-28T07:55:00", "5m", now, ResearchGate.SNAPSHOT_MISMATCH)
    refused("caller gave no snapshot", active, null, "5m", now, ResearchGate.SNAPSHOT_MISMATCH)
    refused("blank snapshot", active, " ", "5m", now, ResearchGate.SNAPSHOT_MISMATCH)
    refused("other timeframe selected", active, snap, "15m", now, ResearchGate.TIMEFRAME_MISMATCH)
    refused("no timeframe", active, snap, null, now, ResearchGate.TIMEFRAME_MISMATCH)
    val swapped = active.copy(feed = td.copy(source = GoldApiService.OANDA_SOURCE, providerId = "OANDA"))
    refused("feed provider changed after stamping", swapped, snap, "5m", now, ResearchGate.PROVIDER_MISMATCH)
    val paxg = active.copy(feed = td.copy(source = "Binance PAXG/USDT"))
    refused("proxy instrument", paxg, snap, "5m", now, ResearchGate.INSTRUMENT_NOT_XAU)
    refused("after expiry", active, snap, "5m", active.tradeSetup.expiresAtMs, ResearchGate.EXPIRED)
    refused("no expiry stamped", active.copy(tradeSetup = active.tradeSetup.copy(expiresAtMs = 0L)), snap, "5m", now, ResearchGate.EXPIRED)
    refused("stop touched", active.copy(tradeSetup = active.tradeSetup.copy(stopTouched = "2026-10-28 08:00:00")), snap, "5m", now, ResearchGate.STOP_TOUCHED)
    refused("stale data", active.copy(feed = td.copy(fetchedAtMs = now - 600_000)), snap, "5m", now, ResearchGate.DATA_NOT_FRESH)
    refused("offline feed", active.copy(feed = td.copy(isLive = false)), snap, "5m", now, ResearchGate.NO_LIVE_DATA)
    refused("simulated", active.copy(isSimulatedFallback = true), snap, "5m", now, ResearchGate.NO_LIVE_DATA)
    refused("presentation says no proven edge", active.copy(presentation = active.presentation!!.copy(provenEdge = false)), snap, "5m", now, ResearchGate.NOT_QUALIFIED)
    refused("no presentation", active.copy(presentation = null), snap, "5m", now, ResearchGate.NOT_QUALIFIED)
    refused("WAIT", active.copy(overallSignal = Signal.WAIT), snap, "5m", now, ResearchGate.NO_SIDE)
    refused("setup side differs", active.copy(tradeSetup = active.tradeSetup.copy(signal = Signal.SELL)), snap, "5m", now, ResearchGate.NO_SIDE)
    refused("stop == entry", active.copy(tradeSetup = active.tradeSetup.copy(stopLoss = px)), snap, "5m", now, ResearchGate.INVALID_LEVELS)
    refused("target on wrong side", active.copy(tradeSetup = active.tradeSetup.copy(takeProfit1 = px - 3)), snap, "5m", now, ResearchGate.INVALID_LEVELS)
    refused("zero stop pips", active.copy(tradeSetup = active.tradeSetup.copy(stopLossPips = 0.0)), snap, "5m", now, ResearchGate.INVALID_LEVELS)
    // a status string forged to ACTIVE without the rest is still refused
    val forged = present(base, "COLLECTING").let { it.copy(tradeSetup = it.tradeSetup.copy(planStatus = PlanStamp.ACTIVE)) }
    refused("forged ACTIVE stamp", forged, forged.tradeSetup.snapshotId, "5m", now, ResearchGate.NOT_QUALIFIED)
    // OANDA data with a Twelve Data qualification is research, never a lot size
    val oanda = present(base.copy(feed = td.copy(source = GoldApiService.OANDA_SOURCE, providerId = "OANDA")), "LIVE QUALIFIED")
    refused("OANDA + TD qualification", oanda, oanda.tradeSetup.snapshotId, "5m", now, ResearchGate.PLAN_NOT_ACTIVE)
    val sat = sdf.parse("2026-10-31 10:00:00")!!.time
    refused("market closed", active.copy(feed = td.copy(fetchedAtMs = sat - 5_000)), snap, "5m", sat, ResearchGate.DATA_NOT_FRESH)

    // ---------- 3. a grant dies with its snapshot
    check("grant valid on its snapshot", ResearchGate.stillValid(g, active, now + 1_000))
    val next = present(base.copy(lastUpdated = sdf.format(Date((now / iv) * iv + iv))), "LIVE QUALIFIED", now + iv)
    check("newer snapshot has a new id", next.tradeSetup.snapshotId != snap)
    check("grant void on a newer snapshot", !ResearchGate.stillValid(g, next, now + iv))
    check("grant void after expiry", !ResearchGate.stillValid(g, active, active.tradeSetup.expiresAtMs + 1))
    check("null grant never valid", !ResearchGate.stillValid(null, active, now))

    // ---------- 4. hypothetical results use THIS plan's reward/risk
    check("TP1 = risk x 1.2 here", Math.abs(ResearchGate.hypotheticalAtTarget(g, 100.0, g.tp1)!! - 120.0) < 1e-6)
    check("TP2 = risk x 2.4 here", Math.abs(ResearchGate.hypotheticalAtTarget(g, 100.0, g.tp2)!! - 240.0) < 1e-6)
    check("wrong-side target -> not computed", ResearchGate.hypotheticalAtTarget(g, 100.0, g.entry - 1) == null)
    check("zero risk -> not computed", ResearchGate.hypotheticalAtTarget(g, 0.0, g.tp1) == null)

    // ---------- 5. honest names, three languages
    val codes = listOf(ResearchGate.NO_SNAPSHOT, ResearchGate.PLAN_NOT_ACTIVE, ResearchGate.SNAPSHOT_MISMATCH, ResearchGate.TIMEFRAME_MISMATCH,
        ResearchGate.PROVIDER_MISMATCH, ResearchGate.INSTRUMENT_NOT_XAU, ResearchGate.EXPIRED, ResearchGate.STOP_TOUCHED, ResearchGate.DATA_NOT_FRESH,
        ResearchGate.NO_LIVE_DATA, ResearchGate.NOT_QUALIFIED, ResearchGate.NO_SIDE, ResearchGate.INVALID_LEVELS)
    for (l in AppLanguage.values()) {
        val txt = ResearchGate.refusalText(codes, l)
        check("every refusal explained $l", txt.size == codes.size && txt.zip(codes).all { (t, c) -> t != c && t.isNotBlank() })
        check("research headline never 'recommended' $l", !HonestText.planHeadline(false, l).contains("RECOMMEND", true))
        check("lot button says ACTIVE only $l", HonestText.lotButton(false, l).contains("ACTIVE"))
        val outs = listOf(HonestText.ruleAgreement(70, l), HonestText.splitTitle(true, l), HonestText.splitTitle(false, l),
            HonestText.hitRateTitle("5m", l), HonestText.hitRateLine(55, 120, l), HonestText.scoreShort(active, l), HonestText.nextCandleTitle(l))
        check("no bare probability/confidence/win-rate $l", outs.none { o ->
            val x = o.lowercase(); (x.contains("probab") && !x.contains("not a probab")) || x.contains("confidence") || x.contains("win rate") || x.contains("win-rate") })
    }
    check("hit-rate line carries N and costs note", HonestText.hitRateLine(55, 120, AppLanguage.ENGLISH).let { it.contains("N=120") && it.contains("before costs") })
    check("no learning -> score is rule agreement", !HonestText.scoreIsHitRate(active) && HonestText.scoreTag(active, AppLanguage.ENGLISH) == "RULE AGREEMENT")
    check("score value equals the raw agreement then", HonestText.scoreValue(active) == "${Math.round(active.agreementPercent)}%")
    check("research share text is not an order", HonestText.shareHeader(false).contains("NOT a trade instruction"))

    // ---------- 6. static guards on the real UI source (the audited files)
    val ui = "app/src/main/java/com/example/livegoldai/ui"
    val all = File(ui).walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.associate { it.name to it.readText() }
    val banned = listOf("SAMBHAVNA (PROBABILITY", "Confidence: \$winProb", "REAL WIN RATE", "\"RECOMMENDED ACTION\"", "\"Safe Lot Size\"",
        "Calculate Safe Lot Size", "NEXT CANDLE PROJECTION", "% CONFIDENCE\"", "Win Confidence", "\"WIN RATE (", "RECOMMENDED LOT SIZE",
        "\${trick.winRate}", "TP1 Profit (1:1 R:R)", "Win rates below are real", "Green Expansion Projected")
    for (b in banned) check("removed: $b", all.values.none { it.contains(b) })
    val vm = all.getValue("GoldViewModel.kt")
    check("VM: calculator opens only through ResearchGate", vm.contains("ResearchGate.authorize(s.data, snapshotId, s.selectedInterval") &&
        Regex("showLotCalculator = true").findAll(vm).count() == 1)
    val home = all.getValue("GoldHomeScreen.kt")
    check("every call passes the shown snapshot", !home.contains("openLotCalculator(slPips)") && Regex("openLotCalculator\\(slPips, ").findAll(home).count() == 10)
    check("screen draws the calculator only under stillValid", home.contains("ResearchGate.stillValid(lotGrant, uiState.data"))
    check("refusals are shown", home.contains("ResearchGate.refusalText(uiState.lotRefusal"))
    val dlg = all.getValue("LotCalculatorDialog.kt")
    check("dialog needs a grant", dlg.contains("grant: com.example.livegoldai.data.ResearchGate.LotGrant") && !dlg.contains("initialStopLossPips") && !dlg.contains("currentGoldPrice: Double"))
    // V33.1R.4-a: the stop is shown as text (no input field at all) and sizing uses the grant's exact levels via PositionSizer
    check("plan stop is not an input", dlg.contains("Stop from the plan: \$stopLabel (not editable)") && !dlg.contains("slPipsText"))
    check("targets from the plan", dlg.contains("grant.entry, grant.stop, grant.tp1, grant.tp2, grant.price"))
    check("bot order copy defaults to research", all.getValue("MultiBotArsenalCard.kt").contains("actionable: Boolean = false"))
    check("unnamed scores are not shown", all.getValue("QuantBotCard.kt").contains("if (scoreText != null)") && all.getValue("CandleMtfOracleCard.kt").contains("if (scoreText != null)") &&
        all.getValue("TradeSetupCard.kt").contains("if (scoreTag != null && scoreValue != null)"))

    println("V33.1 research gate + honest labels: $pass passed, $fail failed")
    if (fail > 0) System.exit(1)
}
