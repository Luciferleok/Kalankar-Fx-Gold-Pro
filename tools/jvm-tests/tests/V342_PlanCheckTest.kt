import com.example.livegoldai.data.*
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean) { if (ok) pass++ else { fail++; println("FAIL: $name") } }

fun decodeLines(ls: List<String>) = PredictionLedger.load(InMemoryLedgerStore().apply { lines.addAll(ls) })

fun main() {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val now = sdf.parse("2026-10-28 10:22:10")!!.time; val iv = 300_000L
    val rnd = Random(3); var p = 4100.0
    val candles = (0 until 150).map { i -> val o = p; p -= 0.35 + (rnd.nextDouble() - 0.5) * 1.2; CandleBar(sdf.format(Date((now / iv) * iv - (149 - i) * iv)), o, maxOf(o, p) + 0.6, minOf(o, p) - 0.6, p) }
    val td = FeedStatus("Twelve Data XAU/USD", now - 5_000, 300, true, true, true, 0, 1)
    val empty = LedgerState(emptyList(), emptyMap(), emptyList())
    fun an(cs: List<CandleBar> = candles, f: FeedStatus = td) = TechnicalEngine.analyze(cs, "5m").copy(feed = f, sourceState = SourceStateMachine.PRIMARY_ACTIVE)
    val lc = LearningCoordinator(object : LedgerStore { override fun readAll() = emptyList<String>(); override fun append(lines: List<String>) {} }, { _, _ -> null })
    val br = LearningCoordinator::class.java.declaredMethods.first { it.name == "buildRecord" }.apply { isAccessible = true }
    fun record(a: GoldAnalysisResult, at: Long) = br.invoke(lc, a, a, a.interval, at, false, null, "", emptyList<LedgerRecord>()) as LedgerRecord  // V35.14: + recent records (8th arg)

    // ---- 1. the record keeps the exact plan levels of the call
    val a0 = an(); val rec = record(a0, now)
    check("fixture SELL", rec.finalSignal == Signal.SELL)
    check("plan levels recorded", rec.planMethod == "PLAN-V2.1" && rec.planEntry == a0.tradeSetup.entryPrice && rec.planStop == a0.tradeSetup.stopLoss &&
        rec.planTp1 == a0.tradeSetup.takeProfit1 && rec.planTp2 == a0.tradeSetup.takeProfit2)
    check("v1 fields unchanged", rec.slDistance > 0 && rec.price == a0.currentPrice)
    val waitRec = record(a0.copy(overallSignal = Signal.WAIT, rawSignal = Signal.WAIT, tradeSetup = a0.tradeSetup.copy(signal = Signal.WAIT)), now)
    check("WAIT: no plan levels", waitRec.planMethod.isEmpty())

    // ---- 2. ledger lines: round trip, and old lines still read
    val line = PredictionLedger.encodeRecord(rec)
    val back = decodeLines(listOf(line)).records.single()
    check("round trip keeps plan levels exactly", back.planEntry == rec.planEntry && back.planStop == rec.planStop && back.planTp1 == rec.planTp1 && back.planTp2 == rec.planTp2 && back.planMethod == "PLAN-V2.1")
    val oldLine = PredictionLedger.encodeRecord(rec.copy(planEntry = 0.0, planStop = 0.0, planTp1 = 0.0, planTp2 = 0.0, planMethod = ""))
    check("v1 record line has no plan keys (old format)", !oldLine.contains("\"pe\"") && !oldLine.contains("\"pm\""))
    val oldBack = decodeLines(listOf(oldLine)).records.single()
    check("old line reads with empty plan", oldBack.planMethod.isEmpty() && oldBack.planEntry == 0.0 && oldBack.id == rec.id && oldBack.slDistance == rec.slDistance)

    // ---- 3. plan outcome on real candle order
    val e = rec.planEntry; val s = rec.planStop; val t1 = rec.planTp1
    fun bar(k: Int, h: Double, l: Double) = PathBar(now + k * 60_000L, (h + l) / 2, h, l, (h + l) / 2)
    fun path(vararg b: PathBar) = PricePath("Twelve Data XAU/USD", b.toList())
    val below = e - 1.0
    check("never reaches entry -> NO_ENTRY (TP1 hit from current price does NOT count)", PredictionLedger.planOutcome(rec, path(bar(1, below, t1 - 1.0), bar(2, below, t1 - 2.0))) == PredictionLedger.PLAN_NO_ENTRY)
    check("entry then TP1 -> TP1", PredictionLedger.planOutcome(rec, path(bar(1, e + 0.1, e - 0.5), bar(2, e, t1 - 0.1))) == PredictionLedger.PLAN_TP1)
    check("entry then stop -> STOP", PredictionLedger.planOutcome(rec, path(bar(1, e + 0.1, e - 0.5), bar(2, s + 0.1, e))) == PredictionLedger.PLAN_STOP)
    check("stop and TP1 in one candle -> STOP (cautious)", PredictionLedger.planOutcome(rec, path(bar(1, e + 0.1, e - 0.2), bar(2, s + 0.1, t1 - 0.1))) == PredictionLedger.PLAN_STOP)
    check("entry and TP1 in one candle -> AMBIGUOUS", PredictionLedger.planOutcome(rec, path(bar(1, e + 0.1, t1 - 0.1))) == PredictionLedger.PLAN_AMBIGUOUS)
    check("entry and stop in one candle -> STOP", PredictionLedger.planOutcome(rec, path(bar(1, s + 0.1, e - 0.1))) == PredictionLedger.PLAN_STOP)
    check("entered, neither -> OPEN_AT_EXPIRY", PredictionLedger.planOutcome(rec, path(bar(1, e + 0.1, e - 0.2), bar(2, e + 0.2, e - 0.3))) == PredictionLedger.PLAN_OPEN)
    check("bars after expiry ignored", PredictionLedger.planOutcome(rec, path(bar(1, e + 0.1, e - 0.2), PathBar(rec.expiresAt + 60_000, 0.0, e, t1 - 5, e))) == PredictionLedger.PLAN_OPEN)
    check("v1 record -> not checked", PredictionLedger.planOutcome(oldBack, path(bar(1, e + 0.1, t1 - 1))) == "")
    // the v1 outcome of the same path is unchanged by v2 (learning keeps using it)
    // V35.13: v1 needs the whole window covered and the publication minute to confirm the published price (a 2-bar path
    // is now VERIFICATION_UNCERTAIN), so this fixture covers the full window
    val full = path(*(listOf(PathBar(now - 60_000L, rec.price, rec.price + 0.1, rec.price - 0.1, rec.price)) + (1 until rec.horizonMin).map { bar(it, below, t1 - 2.0) }).toTypedArray())
    val v1 = PredictionLedger.evaluate(rec, full, now + 30 * 60_000L)
    check("v1 outcome independent of v2 (here: CORRECT while v2 says NO_ENTRY)", v1.outcome == LedgerOutcome.CORRECT && v1.planOutcome == "" &&
        PredictionLedger.planOutcome(rec, full) == PredictionLedger.PLAN_NO_ENTRY)
    check("V35.13: a 2-bar path no longer decides v1", PredictionLedger.evaluate(rec, path(bar(1, below, t1 - 1.0), bar(2, below, t1 - 2.0)), now + 30 * 60_000L).outcome == LedgerOutcome.VERIFICATION_UNCERTAIN)
    // result line round trip
    val resLine = PredictionLedger.encodeResult(v1.copy(planOutcome = PredictionLedger.PLAN_NO_ENTRY))
    check("result keeps plan outcome", decodeLines(listOf(line, resLine)).results.values.single().planOutcome == PredictionLedger.PLAN_NO_ENTRY)
    check("old result line reads with empty plan outcome", !PredictionLedger.encodeResult(v1).contains("\"po\"") && decodeLines(listOf(line, PredictionLedger.encodeResult(v1))).results.values.single().planOutcome == "")

    // ---- 4. display = ledger: recorder-first record seeds the screen's levels
    val later = candles.dropLast(1) + candles.last().let { it.copy(close = it.close - 1.8, low = minOf(it.low, it.close - 1.8)) }
    val st = LedgerState(listOf(rec), emptyMap(), emptyList())
    val laterStamped = PlanStamp.stamp(an(later), st, now + 40_000, "COLLECTING")
    check("fixture: live levels moved", laterStamped.tradeSetup.entryPrice != rec.planEntry)
    val seed = LevelFreeze.seedFrom(st, laterStamped)
    check("seed found (same provider, candle, side)", seed != null && seed.entry == rec.planEntry)
    val shown = LevelFreeze.apply(laterStamped, mutableMapOf(), now + 40_000, seed)
    val ts = shown.tradeSetup
    check("screen shows the ledger's levels", ts.entryPrice == rec.planEntry && ts.stopLoss == rec.planStop && ts.takeProfit1 == rec.planTp1 && ts.takeProfit2 == rec.planTp2)
    check("pips from the ledger's levels", Math.abs(ts.stopLossPips - Math.abs(rec.planEntry - rec.planStop) * 10) < 1e-9)
    check("rule bot uses the ledger's levels", shown.quantBotSignal?.stopLoss == rec.planStop)
    check("note quotes the ledger entry", !ts.strategyNote.contains(String.format(Locale.US, "%.2f", laterStamped.tradeSetup.entryPrice)) || laterStamped.tradeSetup.entryPrice == rec.planEntry)
    check("other provider's record is not a seed", LevelFreeze.seedFrom(st, PlanStamp.stamp(an(later, td.copy(source = GoldApiService.OANDA_SOURCE)), st, now, "COLLECTING")) == null)
    check("opposite side is not a seed", LevelFreeze.seedFrom(LedgerState(listOf(rec.copy(finalSignal = Signal.BUY)), emptyMap(), emptyList()), laterStamped) == null)
    check("v1 record is not a seed", LevelFreeze.seedFrom(LedgerState(listOf(oldBack), emptyMap(), emptyList()), laterStamped) == null)
    // UI-first: same moment -> identical, no change
    val same = LevelFreeze.apply(PlanStamp.stamp(a0, st, now, "COLLECTING"), mutableMapOf(), now, LevelFreeze.seedFrom(st, PlanStamp.stamp(a0, st, now, "COLLECTING")))
    check("UI-first: same levels as the record", same.tradeSetup.entryPrice == rec.planEntry && same.tradeSetup.stopLoss == rec.planStop)
    // restart: the persisted window keeps the seeded levels
    val dir = File(System.getProperty("java.io.tmpdir"), "kfx_v342_" + System.nanoTime()).apply { mkdirs() }
    val m = mutableMapOf<String, LevelFreeze.Frozen>(); LevelFreeze.apply(laterStamped, m, now + 40_000, seed); LevelFreezeStore(File(dir, "f.tsv")).save(m)
    val again = LevelFreeze.apply(PlanStamp.stamp(an(), st, now + 90_000, "COLLECTING"), LevelFreezeStore(File(dir, "f.tsv")).load(now + 90_000), now + 90_000, null)
    check("restart keeps the ledger's levels", again.tradeSetup.entryPrice == rec.planEntry && again.tradeSetup.stopLoss == rec.planStop)
    dir.deleteRecursively()

    // ---- 5. summary line, learning untouched
    val r2 = rec.copy(id = "B"); val r3 = rec.copy(id = "C"); val r4 = rec.copy(id = "D")
    val results = mapOf(rec.id to v1.copy(planOutcome = "TP1"), "B" to v1.copy(id = "B", planOutcome = "STOP"), "C" to v1.copy(id = "C", planOutcome = "NO_ENTRY"), "D" to v1.copy(id = "D", planOutcome = "TP1"))
    val line5 = LedgerPulseBuilder.planCheckLine(LedgerState(listOf(rec, r2, r3, r4), results, emptyList()))
    check("summary counts", line5.startsWith("TP1 2 · stop 1 · no entry 1 · open 0") && line5.contains("TP1 first 67% of entered (N=3)"))
    check("no v2 data -> no line", LedgerPulseBuilder.planCheckLine(LedgerState(listOf(oldBack), emptyMap(), emptyList())) == "")
    val src = File("app/src/main/java/com/example/livegoldai/data")
    check("learning / Shadow Lab do not read the plan check", listOf("LearningEngine.kt", "ShadowLab.kt", "ShadowLive.kt", "RealityEngine.kt").none { File(src, it).readText().contains("planOutcome") })
    val coord = File(src, "LearningCoordinator.kt").readText()
    check("plan check only on the same instrument", coord.contains("if (checked.sourceMatch == \"SAME\") PredictionLedger.planOutcome(r, path) else PredictionLedger.PLAN_NOT_COMPARABLE"))
    check("one record per candle unchanged (V35.3-E: exact-candle rule, still one record per candle across providers)", coord.contains("RecordLink.sameCandle(it, interval, analysis.lastUpdated, now, ivMin)"))
    val vm = File("app/src/main/java/com/example/livegoldai/ui/GoldViewModel.kt").readText()
    check("ViewModel seeds from the ledger", vm.contains("com.example.livegoldai.data.LevelFreeze.seedFrom(st, stamped0)"))

    println("V34.2 plan check: $pass passed, $fail failed")
    if (fail > 0) System.exit(1)
}
