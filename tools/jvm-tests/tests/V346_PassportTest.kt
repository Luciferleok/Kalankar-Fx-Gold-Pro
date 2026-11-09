import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean) { if (ok) pass++ else { fail++; println("FAIL: $name") } }

fun main() {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    val now = sdf.parse("2026-10-28 10:22:10")!!.time; val iv = 300_000L
    val rnd = Random(3); var p = 4100.0
    val candles = (0 until 150).map { i -> val o = p; p -= 0.35 + (rnd.nextDouble() - 0.5) * 1.2; CandleBar(sdf.format(Date((now / iv) * iv - (149 - i) * iv)), o, maxOf(o, p) + 0.6, minOf(o, p) - 0.6, p) }
    val oa = FeedStatus(GoldApiService.OANDA_SOURCE, now - 3_000, 300, true, true, true, 0, 1, quoteTimeMs = now - 2_000, bid = 4049.70, ask = 4050.00, tradeable = true)
    val empty = LedgerState(emptyList(), emptyMap(), emptyList())
    val raw = TechnicalEngine.analyze(candles, "5m").copy(feed = oa, sourceState = SourceStateMachine.PRIMARY_ACTIVE)
    val stamped = PlanStamp.stamp(raw, empty, now, "COLLECTING").let { it.copy(presentation = PresentationBuilder.build(it, empty, "COLLECTING", now)) }
    val shown = LevelFreeze.apply(stamped, mutableMapOf(), now)
    fun rows(a: GoldAnalysisResult?, st: LedgerState?, l: AppLanguage = AppLanguage.ENGLISH) = TrustPassport.build(a, st, now, l)
    fun v(rs: List<TrustPassport.Row>, label: String) = rs.firstOrNull { it.label.startsWith(label) }

    val r0 = rows(shown, empty)
    r0.forEach { println("  ${it.label}: ${it.value}${if (!it.verified) "  [!]" else ""}") }
    check("forecast id", v(r0, "Forecast ID")?.value == shown.tradeSetup.snapshotId)
    check("source badge", v(r0, "Source")?.value?.contains("OANDA") == true)
    check("freshness from provider time + spread", v(r0, "Price freshness")?.value?.let { it.startsWith("LIVE · 2s") && it.contains("provider time") && it.contains("spread 0.30") } == true)
    check("levels = scenario", ResearchScenario.of(shown, now)!!.let { sc -> v(r0, "Entry")?.value == String.format(Locale.US, "%,.2f · %,.2f", sc.entry, sc.stop) })
    check("research status said plainly", v(r0, "Rule direction")?.value?.contains("RESEARCH") == true)
    check("agreement marked not a probability", v(r0, "Rule agreement (not a probability)") != null)
    check("no record -> NOT VERIFIED", v(r0, "Ledger record")?.let { it.value == "NOT VERIFIED" && !it.verified } == true)
    check("why it may fail always present", v(r0, "Why it may fail")?.value?.isNotBlank() == true)

    // with a ledger record and results
    val lc = LearningCoordinator(object : LedgerStore { override fun readAll() = emptyList<String>(); override fun append(lines: List<String>) {} }, { _, _ -> null })
    val br = LearningCoordinator::class.java.declaredMethods.first { it.name == "buildRecord" }.apply { isAccessible = true }
    val rec = br.invoke(lc, raw, raw, "5m", now, false, null, "", emptyList<LedgerRecord>()) as LedgerRecord  // V35.14: + recent records (8th arg)
    val st = LedgerState(listOf(rec), emptyMap(), emptyList())
    val r1 = rows(shown, st)
    check("record shown with method", v(r1, "Ledger record")?.value == rec.id + " · plan check v2")
    check("pending result says when", v(r1, "Result")?.let { it.value.startsWith("pending, checked after") && !it.verified } == true)
    val res = LedgerResult(rec.id, now + 1, LedgerOutcome.CORRECT, "OANDA", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0L, 0.0, emptyList(), emptyMap(), emptyList(), "", emptyList(), sourceMatch = "SAME", planOutcome = "NO_ENTRY")
    val r2 = rows(shown, LedgerState(listOf(rec), mapOf(rec.id to res), emptyList()))
    check("v1 and v2 results both shown", v(r2, "Result v1")?.value == "CORRECT" && v(r2, "Result v2")?.value == "NO_ENTRY")
    // other provider's record is never shown as this forecast's record
    val tdRec = rec.copy(id = "TD1", dataSource = "Twelve Data XAU/USD")
    val r3 = rows(PlanStamp.stamp(raw, LedgerState(listOf(tdRec), emptyMap(), emptyList()), now, "COLLECTING"), LedgerState(listOf(tdRec), emptyMap(), emptyList()))
    check("other provider record -> not used, explained", v(r3, "Ledger record")?.let { !it.verified && it.value.contains("TWELVE DATA") } == true)
    // missing data
    check("null analysis -> NOT VERIFIED", rows(null, null).single().value == "NOT VERIFIED")
    val noFeed = rows(shown.copy(feed = null), null)
    check("no feed -> freshness NOT VERIFIED", v(noFeed, "Price freshness")?.value == "NOT VERIFIED")
    // languages
    for (l in AppLanguage.values()) check("all rows in $l", rows(shown, st, l).size == r1.size && TrustPassport.title(l).isNotBlank() && TrustPassport.footer(l).isNotBlank())
    // read-only: builder never writes
    val src = File("app/src/main/java/com/example/livegoldai/data/TrustPassport.kt").readText()
    check("passport is read-only", !src.contains("append(") && !src.contains("save(") && !src.contains(".copy("))
    val home = File("app/src/main/java/com/example/livegoldai/ui/GoldHomeScreen.kt").readText()
    check("dialog + button wired", home.contains("onOpenPassport = { viewModel.openPassport() }") && home.contains("uiState.passportRows?.let"))
    println("V34.6 passport: $pass passed, $fail failed")
    if (fail > 0) System.exit(1)
}
