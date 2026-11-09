import com.example.livegoldai.data.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

var pass = 0; var fail = 0
fun check(name: String, ok: Boolean) { if (ok) pass++ else { fail++; println("FAIL: $name") } }

/** V33.1R.3: "Next result due" only names a future result; the gauge number says what it is. */
fun main() {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    fun at(s: String) = sdf.parse(s)!!.time
    val now = at("2026-10-30 14:00:00")
    val settle = LearningCoordinator.SETTLE_MS

    // 2 records waiting since the morning, 1 due later, 1 due sooner
    val recs = listOf(at("2026-10-30 09:15:00") to "15m", at("2026-10-30 10:30:00") to "1h", at("2026-10-30 14:47:00") to "1h", at("2026-10-30 14:10:00") to "5m")
    val d = DueLine.of(recs, now)
    check("next = earliest FUTURE expiry", d.nextAt == at("2026-10-30 14:10:00") && d.nextInterval == "5m")
    check("waiting counted separately", d.waiting == 2 && d.oldestWaitingAt == at("2026-10-30 09:15:00"))
    val lbl = DueLine.label(d, now)
    check("label: future time, never '(now)'", lbl.startsWith("30 Oct 14:10 UTC (in ") && !lbl.contains("(now)"))
    check("label: waiting part", lbl.contains("2 waiting for check (oldest 30 Oct 09:15 UTC, 4h 45m ago)"))
    // only overdue records (weekend / provider gap): no fake "next"
    val onlyOld = DueLine.of(recs.take(2), now)
    check("only waiting: no next", onlyOld.nextAt == null && onlyOld.waiting == 2)
    check("only waiting: label says so", DueLine.label(onlyOld, now).startsWith("No upcoming result · 2 waiting for check"))
    // nothing waiting
    val clean = DueLine.of(recs.drop(2), now)
    check("nothing waiting: no waiting text", clean.waiting == 0 && !DueLine.label(clean, now).contains("waiting"))
    // settle boundary: inside the settle period it is still "next", after it it is waiting
    val edge = at("2026-10-30 13:59:00")
    check("inside settle -> next", DueLine.of(listOf(edge to "1m"), edge + settle - 1).nextAt == edge)
    check("after settle -> waiting", DueLine.of(listOf(edge to "1m"), edge + settle).waiting == 1)
    check("days format", DueLine.label(DueLine.of(listOf(at("2026-10-27 09:00:00") to "1h"), now), now).contains("3d 5h ago"))
    check("no-day format for the notification", DueLine.label(DueLine.of(recs, now), now, withDay = false).startsWith("14:10 UTC (in "))

    // ---------- static guards
    val root = File("app/src/main/java/com/example/livegoldai")
    val src = root.walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.associate { it.relativeTo(root).path to it.readText() }
    check("pulse uses DueLine", src.getValue("data/LedgerPulseBuilder.kt").contains("DueLine.label(DueLine.of(pending.map { it.expiresAt to it.interval }, now), now)"))
    check("background report uses DueLine", src.getValue("data/BackgroundRecorder.kt").contains("DueLine.label(DueLine.of(") )
    check("learning note uses DueLine", src.getValue("data/LearningEngine.kt").contains("Next result due: \${DueLine.label("))
    check("no raw min-expiry 'next due' left", !src.getValue("data/LedgerPulseBuilder.kt").contains("utcLabel(next.expiresAt, true)} (\${inTime(") &&
        !src.getValue("data/LearningEngine.kt").contains("Next result due \${PredictionLedger.utcLabel(nextDue"))
    val g = src.getValue("ui/components/UnifiedSignalDashboardView.kt")
    check("gauge axis has no % positions", !g.contains("0% SELL") && !g.contains("50% HOLD") && !g.contains("100% BUY"))
    check("gauge number is named", g.contains("pillar agreement · not a probability") && g.contains("पिलर सहमति · संभावना नहीं") && g.contains("पिलर सहमती · शक्यता नाही"))
    // untouched: ledger verification, learning promotion
    val lc = src.getValue("data/LearningCoordinator.kt")
    check("ledger overdue rule untouched", lc.contains("overdue = pending.count { now > it.expiresAt + SETTLE_MS + 10 * 60_000L }") && lc.contains("const val SETTLE_MS = 90_000L"))

    println("V33.1R.3: $pass passed, $fail failed")
    if (fail > 0) System.exit(1)
}
