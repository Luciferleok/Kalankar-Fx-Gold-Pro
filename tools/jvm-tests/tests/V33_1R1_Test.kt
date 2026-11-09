import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean) { if (ok) pass++ else { fail++; println("FAIL: $name") } }

/** V33.1R.1: click-time authorization, ensemble side rule, one level source everywhere, news without direction triggers. */
fun main() {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val now = sdf.parse("2026-10-28 10:22:10")!!.time
    val iv = 300_000L
    val r = Random(3); var p = 4100.0
    val candles = (0 until 150).map { i -> val o = p; p -= 0.35 + (r.nextDouble() - 0.5) * 1.2; CandleBar(sdf.format(Date((now / iv) * iv - (149 - i) * iv)), o, maxOf(o, p) + 0.6, minOf(o, p) - 0.6, p) }
    val td = FeedStatus("Twelve Data XAU/USD", now - 5_000, 300, true, true, true, 0, 1)
    val raw = TechnicalEngine.analyze(candles, "5m").copy(feed = td, sourceState = SourceStateMachine.PRIMARY_ACTIVE)
    val st = LedgerState(emptyList(), emptyMap(), emptyList())
    fun present(a: GoldAnalysisResult, research: String?, at: Long = now): GoldAnalysisResult {
        val s = PlanStamp.stamp(a, st, at, research)
        return s.copy(presentation = PresentationBuilder.build(s, st, research, at))
    }
    val active = present(raw, "LIVE QUALIFIED")
    val research = present(raw, "COLLECTING")
    check("fixtures", active.tradeSetup.planStatus == PlanStamp.ACTIVE && research.tradeSetup.planStatus == PlanStamp.RESEARCH && active.overallSignal == Signal.SELL)

    // ---------- 1. order text = the scenario, asked at click time
    val cmd = ResearchScenario.orderCommand(active, now)!!
    val sc = ResearchScenario.of(active, now)!!
    check("command uses the scenario levels", listOf(sc.entry, sc.stop, sc.tp1, sc.tp2).all { cmd.contains(String.format(Locale.US, "%.2f", it)) } && cmd.startsWith("SELL LIMIT XAUUSD @ "))
    check("research: no command", ResearchScenario.orderCommand(research, now) == null)
    check("WAIT: no command", ResearchScenario.orderCommand(active.copy(overallSignal = Signal.WAIT), now) == null)
    check("null snapshot: no command", ResearchScenario.orderCommand(null, now) == null)
    // drawn while allowed, clicked after the plan expired (no recomposition in between): refused at click
    check("allowed when drawn", ResearchScenario.orderCommand(active, now + 60_000) != null)
    check("refused at click after expiry", ResearchScenario.orderCommand(active, active.tradeSetup.expiresAtMs + 1) == null)
    check("refused at click when data went stale", ResearchScenario.orderCommand(active.copy(feed = td.copy(fetchedAtMs = now - 900_000)), now) == null)
    check("refused at click when provider switched", ResearchScenario.orderCommand(active.copy(feed = td.copy(source = GoldApiService.OANDA_SOURCE)), now) == null)
    check("refused at click when qualification revoked", ResearchScenario.orderCommand(active.copy(presentation = active.presentation!!.copy(provenEdge = false)), now) == null)
    // switched snapshot: a different command, so the card's "drawn vs now" comparison refuses
    val next = present(raw.copy(lastUpdated = sdf.format(Date((now / iv) * iv + iv)), feed = td.copy(fetchedAtMs = now + iv - 5_000),
        tradeSetup = raw.tradeSetup.copy(entryPrice = raw.tradeSetup.entryPrice - 1.0, stopLoss = raw.tradeSetup.stopLoss - 1.0,
            takeProfit1 = raw.tradeSetup.takeProfit1 - 1.0, takeProfit2 = raw.tradeSetup.takeProfit2 - 1.0)), "LIVE QUALIFIED", now + iv)
    val cmdNext = ResearchScenario.orderCommand(next, now + iv)
    check("new snapshot -> different text (card refuses: plan changed)", cmdNext != null && cmdNext != cmd)

    // ---------- 2. ensemble: authorized plan AND same side
    val ens = active.multiBotEnsemble
    check("fixture has an ensemble", ens != null)
    if (ens != null) {
        val same = active.copy(multiBotEnsemble = ens.copy(ensembleSignal = Signal.SELL))
        val opposite = active.copy(multiBotEnsemble = ens.copy(ensembleSignal = Signal.BUY))
        val none = active.copy(multiBotEnsemble = ens.copy(ensembleSignal = Signal.WAIT))
        check("same side -> the plan's own command", ResearchScenario.ensembleCommand(same, now) == cmd)
        check("opposite side -> refused", ResearchScenario.ensembleCommand(opposite, now) == null)
        check("ensemble WAIT -> refused", ResearchScenario.ensembleCommand(none, now) == null)
        check("research + same side -> refused", ResearchScenario.ensembleCommand(research.copy(multiBotEnsemble = ens.copy(ensembleSignal = Signal.SELL)), now) == null)
        check("expired at click -> refused", ResearchScenario.ensembleCommand(same, active.tradeSetup.expiresAtMs + 1) == null)
    }
    check("no ensemble -> refused", ResearchScenario.ensembleCommand(active.copy(multiBotEnsemble = null), now) == null)
    for (l in AppLanguage.values()) check("messages $l", ResearchScenario.ensembleRefused(l).isNotBlank() && ResearchScenario.planChanged(l).isNotBlank())

    // ---------- 3. static guards on the real source
    val root = File("app/src/main/java/com/example/livegoldai")
    val src = root.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.associate { it.relativeTo(root).path to it.readText() }
    val ui = src.filterKeys { it.startsWith("ui/") }
    check("no screen shows the engine's price-anchored levels", ui.values.none { Regex("prediction\\??\\.(stopLossLevel|takeProfit1|takeProfit2|recommendedEntryZone)").containsMatchIn(it) })
    val ex = src.getValue("ui/components/ExecutiveSummaryBar.kt")
    check("executive snapshot reads the scenario", ex.contains("ResearchScenario.of(analysis") && ex.contains("money(it.entry)") && ex.contains("money(it.stop)") && ex.contains("money(sc.tp1)"))
    check("no 'SEAL'", ui.values.none { it.contains("SEAL (SL)") })
    val qb = src.getValue("ui/components/QuantBotCard.kt")
    check("bot: no engine command text", !qb.contains("executionCommandEng"))
    check("bot: COPY re-asks and compares", qb.contains("val nowCmd = commandProvider()") && qb.contains("nowCmd != shownCommand") && qb.contains("commandProvider: () -> String? = { null }"))
    val mb = src.getValue("ui/components/MultiBotArsenalCard.kt")
    check("multi-bot: no ensemble-built order", !mb.contains("LIMIT XAUUSD @ \${ensemble."))
    check("multi-bot: click-time provider", mb.contains("val command = if (actionable) commandProvider() else null") && mb.contains("commandProvider: () -> String? = { null }"))
    check("multi-bot: pips formatted", !mb.contains("\"SL: \${bot.stopLossPips} pips\""))
    check("multi-bot: no 'near zero' claim", !mb.contains("near zero"))
    val home = src.getValue("ui/GoldHomeScreen.kt")
    check("home: 2 bot providers + 1 ensemble provider", Regex("ResearchScenario\\.orderCommand\\(uiState\\.data").findAll(home).count() == 2 && home.contains("ResearchScenario.ensembleCommand(uiState.data"))
    check("home: views get the current snapshot", Regex("currentAnalysis = \\{ uiState\\.data \\}").findAll(home).count() == 2)
    for (v in listOf("ui/components/SimpleEasyTradingView.kt", "ui/components/UnifiedSignalDashboardView.kt"))
        check("$v passes the current snapshot", src.getValue(v).contains("ResearchScenario.orderCommand(currentAnalysis()"))
    check("unified multi-bot provider", src.getValue("ui/components/UnifiedSignalDashboardView.kt").contains("ResearchScenario.ensembleCommand(currentAnalysis()"))
    val news = src.getValue("ui/components/NewsTradingModeCard.kt")
    check("news: no direction triggers", !news.contains("Trigger on") && !news.contains("High-frequency algorithms"))
    val notif = src.getValue("notification/PreNewsAlertNotificationManager.kt")
    check("notification: no BUY/SELL 'expected' texts", !notif.contains("Surge Expected") && !notif.contains("Drop Expected"))
    // untouched: engine / bot engine / ledger / shadow / OANDA
    val eng = src.getValue("data/TechnicalEngine.kt")
    check("engine unchanged (both level sets still built)", eng.contains("val pullbackEntry = min(currentPrice + (0.40 * atrSafe), fib0500)") && eng.contains("val slNum = currentPrice + (adaptiveSlMultiplier * atrSafe + 0.35)"))

    println("V33.1R.1: $pass passed, $fail failed")
    if (fail > 0) System.exit(1)
}
