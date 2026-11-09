import com.example.livegoldai.data.*
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.*
import java.io.File

// V35.8.1: squeezed badge headers, WAIT legend, honest WAIT scenario title. Source + JVM checks (Compose layout not run here).
var pass = 0; var fail = 0
fun check(name: String, ok: Boolean, d: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $d") } }
fun main() {
    val w = TradeSetup(signal = Signal.WAIT, entryPrice = 1.0, stopLoss = 0.5, stopLossPips = 1.0, takeProfit1 = 1.5, takeProfit1Pips = 1.0, takeProfit2 = 2.0, takeProfit2Pips = 1.0,
        riskRewardRatio = "", confidencePercent = 50, strategyNote = "", strategyNoteHindi = "", strategyNoteMarathi = "", atrPips = 1.0)
    check("WAIT scenario titled as an upside example, not a lean", PlanText.planTitle(w.copy(planStatus = PlanStamp.SCENARIO), AppLanguage.ENGLISH) == "UPSIDE BREAKOUT EXAMPLE (NOT A LEAN, NOT A TRADE)")
    check("Hindi / Marathi too", PlanText.planTitle(w.copy(planStatus = PlanStamp.INACTIVE_WAIT), AppLanguage.HINDI).startsWith("ऊपर ब्रेकआउट") && PlanText.planTitle(w, AppLanguage.MARATHI).startsWith("वर ब्रेकआउट"))
    check("BUY / SELL scenarios keep their title", PlanText.planTitle(w.copy(signal = Signal.SELL, planStatus = PlanStamp.NO_DATA), AppLanguage.ENGLISH) == "CONDITIONAL SCENARIO (NOT A TRADE)" &&
        PlanText.planTitle(w.copy(signal = Signal.BUY, planStatus = PlanStamp.RESEARCH), AppLanguage.ENGLISH) == "HYPOTHETICAL RESEARCH SCENARIO")
    val kh = File("app/src/main/java/com/example/livegoldai/ui/components/KyaHogaPredictionDialog.kt").readText()
    check("rule guards header: text side weighted, badge capped", kh.contains("Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {\n                                        Box(") && kh.contains("color = NeonGreen.copy(alpha = 0.15f),\n                                        modifier = Modifier.widthIn(max = 140.dp)"))
    check("guard tag on one line", kh.contains("text = corr.badgeTag,") && kh.contains("maxLines = 1,\n                                                    softWrap = false,"))
    check("council header weighted", kh.contains("Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {\n                                        Text(text = \"⚙\""))
    check("scenario header weighted", kh.contains("text = PT.planTitle(tradeSetup, currentLanguage, analysis.overallSignal),\n                                    style = MaterialTheme.typography.labelSmall,\n                                    fontWeight = FontWeight.Black,\n                                    color = GoldLight,\n                                    modifier = Modifier.weight(1f)"))
    check("middle share labelled Wait, not Range", !kh.contains("\"🟡 Range \$sidewaysProb%\"") && kh.contains("AppLanguage.ENGLISH -> \"Wait\""))
    println("V35.8.1 layout: $pass passed, $fail failed")
}
