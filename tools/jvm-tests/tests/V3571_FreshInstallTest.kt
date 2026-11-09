import com.example.livegoldai.data.*
import com.example.livegoldai.data.brain.FeatureStore
import com.example.livegoldai.model.*
import kotlinx.coroutines.*
import java.io.File
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory

// V35.7.1: backup rules, restore guard (fresh install vs update), safe reset, outcome reasons, IST label. SYNTHETIC.
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
const val M = 60_000L
const val DAY = 86_400_000L
fun rec(id: String, at: Long) = LedgerRecord(id = id, createdAt = at, expiresAt = at + 25 * M, interval = "5m", horizonMin = 25, barId = id, price = 4000.0,
    rawSignal = Signal.BUY, finalSignal = Signal.BUY, confidence = 75, atr = 1.0, slDistance = 1.0, regime = "R", session = "S", newsActive = false, modelVersion = "",
    appliedFilter = "", sources = emptyMap(), dataSource = GoldApiService.OANDA_SOURCE, engineVersion = "E22.1")
fun res(id: String, o: LedgerOutcome, tp: Long = 0L, sl: Long = 0L) = LedgerResult(id, 1L, o, "OANDA XAU_USD M1", 4000.0, 4001.0, 1.0, 0.5, 2.0, 1.0, sl, 0.0,
    listOf("RANGE_WHIPSAW"), emptyMap(), emptyList(), "No simple rule.", emptyList(), tpHitAt = tp)

fun tmp(): Pair<File, File> { val root = File.createTempFile("kfx", "").apply { delete(); mkdirs() }; return File(root, "files").apply { mkdirs() } to File(root, "no_backup").apply { mkdirs() } }
fun ledgerWith(dir: File, vararg at: Long) { File(dir, "prediction_ledger.jsonl").writeText(at.mapIndexed { i, t -> PredictionLedger.encodeRecord(rec("r$i", t)) }.joinToString("\n", postfix = "\n")) }

fun main() {
    val install = 1_800_000_000_000L
    // ---- restore guard
    run {
        val (f, nb) = tmp(); ledgerWith(f, install - 30 * DAY, install - DAY); File(f, "shadow_ledger.jsonl").writeText("x\n"); File(f, "level_freeze_v1.tsv").writeText("y\n")
        val before = File(f, "prediction_ledger.jsonl").readText()
        val o = RestoreGuard.check(f, nb, install, install, install + M)
        check("fresh install + old ledger: moved aside", o.action == "QUARANTINED_RESTORED_HISTORY" && o.moved.containsAll(listOf("prediction_ledger.jsonl", "shadow_ledger.jsonl", "level_freeze_v1.tsv")), "$o")
        check("app starts at zero", !File(f, "prediction_ledger.jsonl").exists() && !File(f, "shadow_ledger.jsonl").exists())
        val kept = nb.listFiles()!!.first { it.name.startsWith("restored_") }
        check("moved, never deleted (same content in no_backup)", File(kept, "prediction_ledger.jsonl").readText() == before)
        check("checked once only", RestoreGuard.check(f, nb, install, install, install + 2 * M).action == "ALREADY_CHECKED")
    }
    run {
        val (f, nb) = tmp(); ledgerWith(f, install - 30 * DAY)
        val o = RestoreGuard.check(f, nb, install, install + 5 * DAY, install + 5 * DAY + M)
        check("app UPDATE: history kept untouched", o.action == "UPDATE_KEPT" && File(f, "prediction_ledger.jsonl").exists() && o.moved.isEmpty(), "$o")
    }
    run {
        val (f, nb) = tmp(); ledgerWith(f, install + M, install + 2 * M)
        check("fresh install, own calls: kept", RestoreGuard.check(f, nb, install, install, install + 3 * M).action == "FRESH_INSTALL_CLEAN" && File(f, "prediction_ledger.jsonl").exists())
    }
    run {
        val (f, nb) = tmp(); ledgerWith(f, install - DAY)
        val o = RestoreGuard.check(f, nb, 0L, 0L, install)
        check("install time unknown: nothing moved, asks again next start", o.action == "UNKNOWN_INSTALL_TIME" && File(f, "prediction_ledger.jsonl").exists() && !File(nb, RestoreGuard.MARKER).exists())
    }
    run { val (f, nb) = tmp(); check("fresh install, no ledger: clean", RestoreGuard.check(f, nb, install, install, install + M).action == "FRESH_INSTALL_CLEAN") }

    // ---- backup rules exclude every history file, keep settings
    val base = "app/src/main"
    fun excludes(file: String, section: String?): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("$base/res/xml/$file"))
        val root = if (section == null) doc.documentElement else doc.getElementsByTagName(section).item(0) as org.w3c.dom.Element
        val nl = root.getElementsByTagName("exclude")
        return (0 until nl.length).map { nl.item(it).attributes.let { a -> a.getNamedItem("domain").nodeValue + ":" + a.getNamedItem("path").nodeValue } }.toSet()
    }
    val need = RestoreGuard.HISTORY_FILES.map { "file:$it" } + ShadowLab.tdPlan("5m").first.let { "file:history_$it.csv" }
    for ((file, sec) in listOf("data_extraction_rules.xml" to "cloud-backup", "data_extraction_rules.xml" to "device-transfer", "backup_rules.xml" to null)) {
        val ex = excludes(file, sec)
        check("$file ${sec ?: ""}: history excluded", ex.containsAll(need), "${need - ex}")
        check("$file ${sec ?: ""}: settings not excluded", ex.none { it.startsWith("sharedpref") })
    }
    val man = File("$base/AndroidManifest.xml").readText()
    check("manifest uses both rule files", man.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\"") && man.contains("android:fullBackupContent=\"@xml/backup_rules\""))

    // ---- reset: waits for the lock, clears everything, keeps nothing half-written
    runBlocking {
        val store = InMemoryLedgerStore()
        store.append((0 until 20).map { PredictionLedger.encodeRecord(rec("x$it", it * 30 * M)) } + (0 until 10).map { PredictionLedger.encodeResult(res("x$it", LedgerOutcome.CORRECT)) })
        val lc = LearningCoordinator(store) { _, _ -> null }
        lc.fetchPathFor = { r, _ -> delay(400); PricePath("OANDA XAU_USD M1", (0..25).map { k -> PathBar(r.createdAt + k * M, 4000.0, 4001.0, 3999.5, 4000.5) }) }
        check("fixture has history", lc.currentState().records.size == 20 && OutcomeBrowser.build(lc.currentState(), "5m", OutcomeBrowser.Kind.CORRECT).total == 10)
        val verifying = launch(Dispatchers.Default) { lc.verifyOnly(40 * 30 * M) }
        delay(100)
        val ok = withContext(Dispatchers.Default) { lc.resetAll() }
        verifying.join()
        check("reset waited for the running check, then cleared", ok && lc.currentState().records.isEmpty() && lc.currentState().results.isEmpty() && store.lines.isEmpty(), "${lc.currentState().records.size} ${store.lines.size}")
        val snap = LearningEngine.snapshot(lc.currentState(), "5m", 50 * 30 * M, emptyList())
        check("learning counters zero", snap.correct == 0 && snap.wrong == 0 && snap.active == 0)
        check("outcome lists zero", OutcomeBrowser.build(lc.currentState(), "5m", OutcomeBrowser.Kind.WRONG).counts.values.all { it == 0 })
        // a lock held past the timeout: nothing changes
        store.append(listOf(PredictionLedger.encodeRecord(rec("y", 0))))
        store.append(listOf(PredictionLedger.encodeRecord(rec("z", 1))))
        val lc3 = LearningCoordinator(store) { _, _ -> null }.apply { fetchPathFor = { _, _ -> delay(1500); null } }
        val hold = launch(Dispatchers.Default) { lc3.verifyOnly(10 * DAY) }
        delay(100)
        val refused = withContext(Dispatchers.Default) { lc3.resetAll(timeoutMs = 300) }
        hold.join()
        // (the check itself may write its own "no data" results: those are real, the reset must not remove anything)
        check("lock not free in time: refused, history untouched", !refused && lc3.currentState().records.size == 2 && store.lines.size >= 2, "${lc3.currentState().records.size} ${store.lines.size}")
    }
    // shadow lab + feature memory
    run {
        val (f, _) = tmp()
        File(f, "shadow_ledger.jsonl").writeText("{\"t\":\"sr\",\"id\":\"a\"}\n"); File(f, "shadow_models.json").writeText("{}")
        val sh = ShadowLive({ f }) { _, _ -> Result.failure(IllegalStateException()) }
        sh.allStats(); sh.resetAll()
        check("shadow reset: ledger emptied, models removed, market history untouched", File(f, "shadow_ledger.jsonl").readText().isEmpty() && !File(f, "shadow_models.json").exists())
        val fs = FeatureStore(null); fs.recordIfAbsent("a", 1L, "5m", mapOf("x" to 1.0)); fs.clearMemory()
        check("feature memory cleared", fs.size() == 0)
    }

    // ---- why a call is right / wrong, IST label
    check("target first", OutcomeBrowser.reasonOf(res("a", LedgerOutcome.CORRECT, tp = 1L)).startsWith("Target reached first"))
    check("stop", OutcomeBrowser.reasonOf(res("a", LedgerOutcome.INVALIDATED, sl = 1L)).startsWith("Stop hit"))
    check("close direction", OutcomeBrowser.reasonOf(res("a", LedgerOutcome.CORRECT)) == "Closed the called way beyond the noise" && OutcomeBrowser.reasonOf(res("a", LedgerOutcome.INCORRECT)) == "Closed the wrong way beyond the noise")
    check("pending", OutcomeBrowser.reasonOf(null) == "Not checked yet")
    val row = OutcomeBrowser.build(LedgerState(listOf(rec("w", 0)), mapOf("w" to res("w", LedgerOutcome.INVALIDATED, sl = 5 * M)), emptyList()), "5m", OutcomeBrowser.Kind.WRONG).rows.single()
    check("wrong call carries tags and counterfactual", row.tags == listOf("RANGE_WHIPSAW") && row.counterfactual.isNotBlank() && row.reason.startsWith("Stop hit"))
    val ist = TimeZone.getTimeZone("Asia/Kolkata")
    check("IST, not GMT+05:30", OutcomeBrowser.ts(1_793_971_800_000L, ist) == "06 Nov 19:00 IST" && TrustCard.localTs(1_793_971_800_000L, ist) == "19:00 IST", OutcomeBrowser.ts(1_793_971_800_000L, ist))
    check("other zones keep their own name", OutcomeBrowser.zone(TimeZone.getTimeZone("UTC"), 0L) == "UTC")

    // ---- wiring (source): both entry points run the guard, settings reset is two-step and says what stays
    val src = "app/src/main/java/com/example/livegoldai"
    check("screen and recorder pass no_backup + install times", File("$src/ui/GoldViewModel.kt").readText().contains("AppEngine.init(application.filesDir, application.noBackupFilesDir, pkg?.firstInstallTime ?: 0L, pkg?.lastUpdateTime ?: 0L)") &&
        File("$src/service/RecorderService.kt").readText().contains("AppEngine.init(filesDir, noBackupFilesDir, pkg?.firstInstallTime ?: 0L, pkg?.lastUpdateTime ?: 0L)"))
    val st = File("$src/ui/v8/V8SettingsConsole.kt").readText()
    check("settings: two taps, explains removed vs kept", st.contains("if (armed) { a.onResetHistory(); confirm = \"\" } else confirm = \"history\"") && st.contains("KEEPS: API keys, OANDA token, settings"))
    println("V35.7.1 fresh install: $pass passed, $fail failed")
}
