import com.example.livegoldai.data.HistoryExport
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

fun main() {
    val root = Files.createTempDirectory("history-export-test").toFile()
    var passed = 0
    var failed = 0
    fun check(ok: Boolean, name: String) {
        if (ok) passed++ else { failed++; println("FAIL: $name") }
    }
    try {
        val app = File(root, "app").apply { mkdirs() }
        File(app, "prediction_ledger.jsonl").writeText("rec\\nout\\n")
        File(app, "shadow_models.json").writeText("{}")
        File(app, "private_token.txt").writeText("SECRET")
        val archive = File(root, "export.zip")
        val result = HistoryExport.export(app, archive)
        check(result.files == 2, "only existing allowlisted files")
        check(result.bytes > 0, "nonempty payload")
        check(result.sha256.length == 64, "SHA-256 provided")
        ZipFile(archive).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            check(names.sorted() == listOf("prediction_ledger.jsonl", "shadow_models.json"), "no secrets archived")
            check(zip.getInputStream(zip.getEntry("prediction_ledger.jsonl")).bufferedReader().readText() == "rec\\nout\\n", "ledger bytes preserved")
        }
        check(File(app, "prediction_ledger.jsonl").exists(), "export never deletes source")
        check(runCatching { HistoryExport.export(app, archive) }.isFailure, "refuse overwrite")
        check(runCatching { HistoryExport.export(File(root, "missing"), File(root, "x.zip")) }.isFailure, "refuse missing source")
    } finally {
        root.deleteRecursively()
    }
    println("$passed passed, $failed failed")
    if (failed > 0) error("history export regression failed")
}
