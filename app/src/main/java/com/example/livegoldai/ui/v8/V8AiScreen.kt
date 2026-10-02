package com.example.livegoldai.ui.v8

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.data.RealityEngine
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.AiCouncilReport
import com.example.livegoldai.model.AiProviderUi
import com.example.livegoldai.model.AiProviderView
import com.example.livegoldai.model.AiTestUi
import com.example.livegoldai.model.GoldAnalysisResult
import com.example.livegoldai.model.Signal
import kotlin.math.roundToInt

private val ROLES = listOf(
    "SCENARIO" to "Scenario", "MTF" to "MTF", "RISK" to "Risk", "QUANT" to "Quant",
    "NEWS" to "Events", "ALTERNATIVE" to "Alt. view", "FAST" to "Fast"
)

private fun aiColor(status: String): Color = when (status) {
    "ONLINE" -> V8.Green
    "STALE", "PENDING", "RATE_LIMITED", "CIRCUIT_OPEN", "SKIPPED", "FACT_CONFLICT" -> V8.Amber
    "NOT_CONFIGURED", "DISABLED" -> V8.Text3
    else -> V8.Red
}

private fun sigOf(s: String): Signal? = when (s) { "BUY" -> Signal.BUY; "SELL" -> Signal.SELL; "WAIT" -> Signal.WAIT; else -> null }

/**
 * AI COMMAND CENTER — the external AI council.
 * Only providers connected with the user's own key vote. Everything shown is a real API answer
 * or a real measurement from the on-device ledger.
 */
@Composable
fun V8AiScreen(
    analysis: GoldAnalysisResult,
    lang: AppLanguage,
    providers: List<AiProviderUi>,
    mode: String,
    debate: Boolean,
    freshnessSec: Int,
    tests: Map<String, AiTestUi>,
    running: Boolean,
    tick: Int,
    onSave: (String, String?, String, String, Boolean) -> Unit,
    onRemoveKey: (String) -> Unit,
    onTest: (String) -> Unit,
    onMode: (String) -> Unit,
    onDebate: (Boolean) -> Unit,
    onFreshness: (Int) -> Unit,
    onRunNow: () -> Unit
) {
    @Suppress("UNUSED_VARIABLE") val recompose = tick
    val r = analysis.aiCouncil
    val now = System.currentTimeMillis()
    val connected = providers.count { it.hasKey }
    var openDetail by remember { mutableStateOf<String?>(null) }
    var openSetup by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(V8.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ================= HERO
        V8Card(level = 1, accent = (if (r != null && r.eligible > 0) V8.Gold else V8.Line).copy(alpha = 0.6f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                V8Label(tr(lang, "AI council", "AI काउंसिल", "AI कौन्सिल"), V8.Gold)
                Spacer(modifier = Modifier.weight(1f))
                if (running) V8Badge(tr(lang, "ASKING…", "पूछ रहे…", "विचारत आहे…"), V8.Info)
            }
            if (r == null) {
                Text(
                    text = if (connected == 0) tr(lang, "No AI connected", "कोई AI कनेक्ट नहीं", "कोणतेही AI कनेक्ट नाही")
                    else tr(lang, "Waiting for the first answer", "पहले जवाब का इंतज़ार", "पहिल्या उत्तराची वाट"),
                    color = V8.Text1, fontSize = 22.sp, fontWeight = FontWeight.Black
                )
                Text(
                    text = if (connected == 0) tr(lang,
                        "Add your own API key below (Gemini has a free key). Only connected providers can vote — nothing is filled in.",
                        "नीचे अपनी API key डालें (Gemini की key फ्री है)। सिर्फ कनेक्टेड AI ही वोट करेंगे — कुछ भी नकली नहीं।",
                        "खाली तुमची API key टाका (Gemini ची key फ्री आहे). फक्त कनेक्ट केलेले AI मत देतील — काहीही बनावट नाही.")
                    else tr(lang, "$connected connected. The council runs after each data refresh.", "$connected कनेक्टेड। हर रिफ्रेश के बाद काउंसिल चलेगी।", "$connected कनेक्टेड. प्रत्येक रिफ्रेशनंतर कौन्सिल चालेल."),
                    color = V8.Text3, fontSize = 11.sp
                )
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "${r.eligible}", color = V8.Text1, fontSize = 40.sp, fontWeight = FontWeight.Black)
                    Text(text = " / ${r.supported} " + tr(lang, "voting", "वोट दे रहे", "मत देत"), color = V8.Text3, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                Text(
                    text = tr(lang, "Connected ", "कनेक्टेड ", "कनेक्टेड ") + "${r.configured} • " + tr(lang, "asked ", "पूछा ", "विचारले ") + "${r.called} • ${r.effectiveMode} (${r.modeReason})",
                    color = V8.Text3, fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    HeroStat(tr(lang, "Consensus", "सहमति", "सहमती"), r.consensus, sigOf(r.consensus)?.let { signalColor(it) } ?: V8.Text2, Modifier.weight(1f))
                    HeroStat(tr(lang, "Agreement", "एकमत", "एकमत"), if (r.agreementPct < 0) "--" else "${r.agreementLevel} ${r.agreementPct}%", V8.Text1, Modifier.weight(1.2f))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    HeroStat(tr(lang, "Dispersion", "मतभेद", "मतभेद"), if (r.dispersionPct < 0) "N/A" else "${r.dispersionPct}% ${r.dispersionLevel}",
                        when (r.dispersionLevel) { "LOW" -> V8.Green; "MODERATE" -> V8.Amber; "HIGH" -> V8.Red; else -> V8.Text2 }, Modifier.weight(1f))
                    HeroStat(tr(lang, "Avg answer age", "जवाब की उम्र", "उत्तराचे वय"), if (r.avgResponseAgeMs < 0) "--" else fmtAge(now - r.runAtMs + r.avgResponseAgeMs), V8.Text1, Modifier.weight(1.2f))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (connected > 0) V8Chip(text = tr(lang, "Ask now ↻", "अभी पूछें ↻", "आता विचारा ↻"), selected = false) { onRunNow() }
        }

        if (r != null && r.eligible > 0) {
            // ================= AGREEMENT BAR
            V8Card(level = 2) {
                V8Label(tr(lang, "AI agreement (real answers)", "AI सहमति (असली जवाब)", "AI सहमती (खरी उत्तरे)"))
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(Triple("SELL", r.sellVotes, V8.Red), Triple("WAIT", r.waitVotes, V8.Amber), Triple("BUY", r.buyVotes, V8.Green)).forEach { (lbl, n, c) ->
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = lbl, color = c, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)).background(V8.Line)
                            ) {
                                if (n > 0) Box(modifier = Modifier.fillMaxWidth(n.toFloat() / r.eligible).height(8.dp).clip(RoundedCornerShape(4.dp)).background(c)) {}
                            }
                            Text(text = "$n", color = V8.Text1, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
                Text(
                    text = tr(lang, "AI average view: ", "AI औसत राय: ", "AI सरासरी मत: ") + "↑${r.avgBullish}% ↔${r.avgSideways}% ↓${r.avgBearish}%" +
                        tr(lang, "  •  model opinion, NOT market probability", "  •  मॉडल की राय, बाज़ार की संभावना नहीं", "  •  मॉडेलचे मत, बाजाराची शक्यता नाही"),
                    color = V8.Text3, fontSize = 9.sp
                )
            }
        }

        // ================= QUANT vs BOTS vs AI
        val rawConf = analysis.agreementPercent.roundToInt()
        val cal = RealityEngine.calibratedConfidence(analysis.learning, rawConf)
        V8Card(level = 2, accent = when (r?.conflictLevel) { "HIGH" -> V8.Red.copy(alpha = 0.6f); "MINOR" -> V8.Amber.copy(alpha = 0.5f); "ALIGNED" -> V8.Green.copy(alpha = 0.5f); else -> V8.Line }) {
            V8Label(tr(lang, "Intelligence check", "इंटेलिजेंस जाँच", "इंटेलिजन्स तपासणी"))
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Node("QUANT", analysis.overallSignal, "${cal ?: rawConf}%")
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Node(tr(lang, "BOTS", "बॉट", "बॉट"), analysis.multiBotEnsemble?.ensembleSignal,
                    analysis.multiBotEnsemble?.let { "${maxOf(it.buyVotes, it.sellVotes, it.waitVotes)}/${it.totalBots}" } ?: "--")
                Text(
                    text = when (r?.conflictLevel) { "HIGH" -> "⚠ HIGH CONFLICT"; "MINOR" -> "≈ MINOR"; "ALIGNED" -> "✓ ALIGNED"; else -> "— NO AI" },
                    color = when (r?.conflictLevel) { "HIGH" -> V8.Red; "MINOR" -> V8.Amber; "ALIGNED" -> V8.Green; else -> V8.Text3 },
                    fontSize = 12.sp, fontWeight = FontWeight.Black
                )
                Node("AI", r?.let { sigOf(it.consensus) }, r?.let { if (it.eligible == 0) "0" else "${maxOf(it.buyVotes, it.sellVotes, it.waitVotes)}/${it.eligible}" } ?: "--")
            }
            Text(
                text = r?.conflictNote ?: tr(lang, "Connect at least one AI to compare it with the quant engine and bots.", "क्वांट और बॉट से तुलना के लिए कम से कम एक AI जोड़ें।", "क्वांट आणि बॉटशी तुलना करण्यासाठी किमान एक AI जोडा."),
                color = V8.Text2, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = tr(lang, "The forecast always comes from the quant engine. AI is advisory (weight 0) until the ledger proves it adds value.",
                    "अनुमान हमेशा क्वांट इंजन से आता है। AI सिर्फ सलाह है (वज़न 0) जब तक लेजर साबित न करे कि वह मदद करता है।",
                    "अंदाज नेहमी क्वांट इंजिनकडून येतो. लेजर सिद्ध करेपर्यंत AI फक्त सल्ला आहे (वजन 0)."),
                color = V8.Text3, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }

        // ================= DISSENT
        if (r != null && r.dissent.isNotEmpty()) {
            V8Card(level = 2, accent = V8.Learn.copy(alpha = 0.5f)) {
                V8Label(tr(lang, "Dissenting view", "असहमत राय", "असहमत मत"), V8.Learn)
                Text(text = tr(lang, "Council: ", "काउंसिल: ", "कौन्सिल: ") + r.consensus, color = V8.Text2, fontSize = 11.sp)
                r.dissent.forEach { d ->
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text(text = d.provider, color = V8.Text1, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.38f))
                        Text(text = arrowOf(d.direction) + " " + d.direction.name + " ${d.probability}%", color = signalColor(d.direction), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(0.3f))
                    }
                    Text(text = d.reason, color = V8.Text3, fontSize = 10.sp)
                }
            }
        }

        // ================= WHAT COULD MAKE IT WRONG
        val contrarian = r?.contrarian ?: com.example.livegoldai.data.ai.AiConsensus.contrarian(analysis)
        V8Card(level = 2) {
            V8Label(tr(lang, "What could make this forecast wrong?", "यह अनुमान गलत कैसे हो सकता है?", "हा अंदाज चुकीचा कसा ठरू शकतो?"), V8.Amber)
            contrarian.forEach { Text(text = "• $it", color = V8.Text1, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
            if (r != null && r.aiInvalidations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = tr(lang, "AI invalidation points", "AI के अमान्य होने के बिंदु", "AI चे अमान्य बिंदू"), color = V8.Text3, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                r.aiInvalidations.forEach { Text(text = "• $it", color = V8.Text2, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp)) }
            }
            Text(text = tr(lang, "Built from real levels, pillars and timeframes.", "असली लेवल, पिलर और टाइमफ्रेम से बना।", "खऱ्या लेव्हल, पिलर आणि टाइमफ्रेमवरून."), color = V8.Text3, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
        }

        // ================= PROVIDER CARDS
        V8Label(tr(lang, "AI providers", "AI प्रोवाइडर", "AI प्रोव्हायडर"))
        providers.forEach { p ->
            val v = r?.providers?.firstOrNull { it.id == p.id }
            ProviderCard(p, v, now, lang, open = openDetail == p.id) { openDetail = if (openDetail == p.id) null else p.id }
        }

        // ================= ROUTING
        V8Card(level = 2) {
            V8Label(tr(lang, "Routing mode", "रूटिंग मोड", "रूटिंग मोड"))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("AUTO", "FAST", "BALANCED", "FULL").forEach { m -> V8Chip(text = m, selected = mode == m) { onMode(m) } }
            }
            Text(
                text = when (mode) {
                    "FAST" -> tr(lang, "2 fastest connected AI", "2 सबसे तेज़ AI", "2 सर्वात वेगवान AI")
                    "BALANCED" -> tr(lang, "Up to 4 AI", "4 AI तक", "4 AI पर्यंत")
                    "FULL" -> tr(lang, "All connected AI (more API calls)", "सभी कनेक्टेड AI (ज़्यादा API कॉल)", "सर्व कनेक्टेड AI (जास्त API कॉल)")
                    else -> tr(lang, "Quiet market: 2 • conflict: 4 • news or quant/bot clash: all", "शांत मार्केट: 2 • टकराव: 4 • न्यूज़ या क्वांट/बॉट टकराव: सभी", "शांत मार्केट: 2 • संघर्ष: 4 • न्यूज किंवा क्वांट/बॉट संघर्ष: सर्व")
                },
                color = V8.Text3, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                V8Chip(text = tr(lang, "Debate round ", "बहस राउंड ", "चर्चा राउंड ") + if (debate) "ON" else "OFF", selected = debate) { onDebate(!debate) }
                Text(text = tr(lang, "Wait max:", "अधिकतम इंतज़ार:", "कमाल प्रतीक्षा:"), color = V8.Text3, fontSize = 10.sp)
                listOf(0 to "Auto", 10 to "10s", 20 to "20s", 30 to "30s").forEach { (sec, lbl) -> V8Chip(text = lbl, selected = freshnessSec == sec) { onFreshness(sec) } }
            }
            Text(
                text = tr(lang,
                    "Answers are reused while the market snapshot is the same (no repeat calls). Late answers are kept for diagnostics only. Debate = a 2nd call per AI when they disagree.",
                    "मार्केट स्नैपशॉट वही रहने तक जवाब दोबारा इस्तेमाल होते हैं (दोबारा कॉल नहीं)। देर से आए जवाब सिर्फ जाँच के लिए। बहस = असहमति पर हर AI को दूसरी कॉल।",
                    "मार्केट स्नॅपशॉट तोच असेपर्यंत उत्तरे पुन्हा वापरली जातात (पुन्हा कॉल नाही). उशिरा आलेली उत्तरे फक्त तपासणीसाठी. चर्चा = असहमती असल्यास प्रत्येक AI ला दुसरा कॉल."),
                color = V8.Text3, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp)
            )
        }

        // ================= EVIDENCE FROM THE LEDGER
        if (r != null) {
            V8Card(level = 3) {
                V8Label(tr(lang, "Does AI add value? (verified)", "क्या AI मदद करता है? (जाँचा हुआ)", "AI उपयोगी आहे का? (तपासलेले)"), V8.Learn)
                V8KeyValue(tr(lang, "Quant right when AI agreed", "AI सहमत तब क्वांट सही", "AI सहमत तेव्हा क्वांट बरोबर"), r.quantWhenAiAgreed)
                V8KeyValue(tr(lang, "Quant right when AI disagreed", "AI असहमत तब क्वांट सही", "AI असहमत तेव्हा क्वांट बरोबर"), r.quantWhenAiDisagreed)
                r.pairs.take(6).forEach { pr ->
                    val same = pr.sameDirectionPct
                    V8KeyValue(
                        "${short(pr.a)} ↔ ${short(pr.b)}",
                        if (pr.together < 10) "untested (N=${pr.together})" else "$same% same (N=${pr.together})" + if (same >= 85) " • not independent" else "",
                        if (pr.together >= 10 && same >= 85) V8.Amber else V8.Text1
                    )
                }
                Text(text = tr(lang, "Needs 20+ checked predictions before a number is shown.", "संख्या दिखाने से पहले 20+ जाँची गई भविष्यवाणियाँ चाहिए।", "आकडा दाखवण्यापूर्वी 20+ तपासलेले अंदाज हवेत."), color = V8.Text3, fontSize = 9.sp)
            }
        }

        // ================= CONNECT PROVIDERS
        V8Card(level = 2, accent = V8.Gold.copy(alpha = 0.35f)) {
            V8Label(tr(lang, "Connect your AI keys", "अपनी AI keys जोड़ें", "तुमच्या AI keys जोडा"), V8.Gold)
            Text(
                text = tr(lang,
                    "Keys are encrypted on this phone (Android Keystore) and sent only to that provider. Never share a key in chat.",
                    "Keys इसी फ़ोन पर एन्क्रिप्ट रहती हैं (Android Keystore) और सिर्फ उसी प्रोवाइडर को भेजी जाती हैं। Key किसी चैट में न भेजें।",
                    "Keys याच फोनवर एन्क्रिप्ट राहतात (Android Keystore) आणि फक्त त्या प्रोव्हायडरला पाठवल्या जातात. Key कोणत्याही चॅटमध्ये पाठवू नका."),
                color = V8.Text3, fontSize = 10.sp
            )
            providers.forEach { p ->
                SetupRow(p, tests[p.id], lang, open = openSetup == p.id,
                    onToggle = { openSetup = if (openSetup == p.id) null else p.id },
                    onSave = onSave, onRemoveKey = onRemoveKey, onTest = onTest)
            }
        }

        Text(
            text = tr(lang,
                "Local rule engines (5 rules + 6 bots) are algorithms, not AI. They are in HEALTH and MORE.",
                "लोकल नियम इंजन (5 नियम + 6 बॉट) एल्गोरिदम हैं, AI नहीं। ये HEALTH और MORE में हैं।",
                "लोकल नियम इंजिन (5 नियम + 6 बॉट) अल्गोरिदम आहेत, AI नाही. ते HEALTH आणि MORE मध्ये आहेत."),
            color = V8.Text3, fontSize = 9.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun short(id: String) = when (id) {
    "OPENAI" -> "OpenAI"; "GEMINI" -> "Gemini"; "CLAUDE" -> "Claude"; "DEEPSEEK" -> "DeepSeek"
    "PERPLEXITY" -> "Perplexity"; "XAI" -> "Grok"; "MISTRAL" -> "Mistral"; "GROQ" -> "Groq"; else -> id
}

@Composable
private fun HeroStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(text = label.uppercase(), color = V8.Text3, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun Node(title: String, sig: Signal?, sub: String) {
    val c = sig?.let { signalColor(it) } ?: V8.Text3
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, color = V8.Text3, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(text = if (sig == null) "—" else arrowOf(sig) + " " + sig.name, color = c, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Text(text = sub, color = V8.Text2, fontSize = 9.sp)
    }
}

@Composable
private fun ProviderCard(p: AiProviderUi, v: AiProviderView?, now: Long, lang: AppLanguage, open: Boolean, onToggle: () -> Unit) {
    val status = v?.status ?: if (!p.hasKey) "NOT_CONFIGURED" else if (!p.enabled) "DISABLED" else "PENDING"
    val c = aiColor(status)
    V8Card(level = if (p.hasKey) 2 else 3, accent = if (v?.eligible == true) c.copy(alpha = 0.45f) else V8.Line, onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(c)) {}
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = p.name, color = V8.Text1, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            V8Badge(status.replace('_', ' '), c)
        }
        val vote = v?.vote
        if (vote != null) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val pr = when (vote.direction) { Signal.BUY -> vote.bullish; Signal.SELL -> vote.bearish; Signal.WAIT -> vote.sideways }
                Text(text = arrowOf(vote.direction) + " " + vote.direction.name + " $pr%", color = if (v.eligible) signalColor(vote.direction) else V8.Text3,
                    fontSize = 16.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "${v.latencyMs} ms" + if (v.cached) " • cached" else "", color = V8.Text2, fontSize = 10.sp)
                    Text(text = tr(lang, "age ", "उम्र ", "वय ") + fmtAge(now - v.receivedAtMs), color = V8.Text3, fontSize = 9.sp)
                }
            }
            if (!v.eligible) Text(text = tr(lang, "Not counted: ", "गिना नहीं: ", "मोजले नाही: ") + status.replace('_', ' '), color = V8.Amber, fontSize = 10.sp)
        }
        Text(text = (v?.role ?: ROLES.firstOrNull { it.first == p.role }?.second ?: p.role) + " • " + p.model, color = V8.Text3, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        if (v != null && (open || vote == null)) Text(text = v.statusDetail, color = V8.Text2, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        if (open && v != null) {
            Spacer(modifier = Modifier.height(6.dp))
            if (vote != null) {
                V8KeyValue(tr(lang, "Probabilities", "संभावनाएँ", "शक्यता"), "↑${vote.bullish}% ↔${vote.sideways}% ↓${vote.bearish}% • conf ${vote.confidence}%")
                if (vote.regime.isNotBlank()) V8KeyValue(tr(lang, "Regime", "स्थिति", "स्थिती"), vote.regime)
                Bullets(tr(lang, "Strongest evidence", "सबसे मज़बूत सबूत", "सर्वात मजबूत पुरावा"), vote.evidence, V8.Text1)
                Bullets(tr(lang, "Contradictions", "विरोधाभास", "विरोधाभास"), vote.contradictions, V8.Amber)
                Bullets(tr(lang, "Invalidation", "अमान्य होगा अगर", "अमान्य होईल जर"), vote.invalidations, V8.Text2)
                Bullets(tr(lang, "Uncertainty", "अनिश्चितता", "अनिश्चितता"), vote.uncertainty, V8.Text2)
                Bullets(tr(lang, "Data concerns", "डेटा चिंता", "डेटा चिंता"), vote.dataConcerns, V8.Text3)
                Bullets(tr(lang, "Would prove it wrong (debate)", "गलत साबित करेगा (बहस)", "चूक सिद्ध करेल (चर्चा)"), vote.proveWrong, V8.Learn)
            }
            v.round1?.let { r1 -> V8KeyValue(tr(lang, "Round 1 answer", "राउंड 1 जवाब", "राउंड 1 उत्तर"), "${r1.direction.name} ↑${r1.bullish}% ↓${r1.bearish}%") }
            Bullets(tr(lang, "Fact conflicts (dropped)", "तथ्य टकराव (हटाए)", "तथ्य संघर्ष (काढले)"), v.factConflicts, V8.Red)
            Spacer(modifier = Modifier.height(4.dp))
            V8KeyValue(tr(lang, "Operational trust", "ऑपरेशनल भरोसा", "ऑपरेशनल विश्वास"), if (v.trustScore < 0) "untested (N=${v.trustSamples})" else "${v.trustScore}/100 (N=${v.trustSamples})")
            V8KeyValue(tr(lang, "Availability", "उपलब्धता", "उपलब्धता"), if (v.availabilityPct < 0) "--" else "${v.availabilityPct}%")
            V8KeyValue(tr(lang, "Schema OK", "फॉर्मेट सही", "फॉरमॅट बरोबर"), if (v.schemaOkPct < 0) "--" else "${v.schemaOkPct}%")
            V8KeyValue(tr(lang, "Median latency", "औसत समय", "सरासरी वेळ"), if (v.medianLatencyMs < 0) "--" else "${v.medianLatencyMs} ms")
            V8KeyValue(tr(lang, "Direction record", "दिशा रिकॉर्ड", "दिशा रेकॉर्ड"),
                com.example.livegoldai.data.ai.AiPerformanceTracker.label(v.directionHits, v.directionDecided))
            V8KeyValue(tr(lang, "Today", "आज", "आज"), "${v.requestsToday} req • ${v.okToday} ok • ${v.failedToday} failed" +
                (if (v.avgLatencyTodayMs >= 0) " • ${v.avgLatencyTodayMs} ms" else "") + (if (v.tokensToday > 0) " • ${v.tokensToday} tokens" else ""))
            V8KeyValue(tr(lang, "Prompt / model", "प्रॉम्प्ट / मॉडल", "प्रॉम्प्ट / मॉडेल"), "${v.promptVersion} • ${v.model}" + if (v.tokens > 0) " • ${v.tokens} tok" else "")
            Text(text = tr(lang, "Trust = API reliability, not trading accuracy.", "भरोसा = API की विश्वसनीयता, ट्रेडिंग सटीकता नहीं।", "विश्वास = API ची विश्वसनीयता, ट्रेडिंग अचूकता नाही."), color = V8.Text3, fontSize = 9.sp)
        }
    }
}

@Composable
private fun Bullets(title: String, items: List<String>, color: Color) {
    if (items.isEmpty()) return
    Text(text = title, color = V8.Text3, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
    items.forEach { Text(text = "• $it", color = color, fontSize = 10.sp) }
}

@Composable
private fun SetupRow(
    p: AiProviderUi,
    test: AiTestUi?,
    lang: AppLanguage,
    open: Boolean,
    onToggle: () -> Unit,
    onSave: (String, String?, String, String, Boolean) -> Unit,
    onRemoveKey: (String) -> Unit,
    onTest: (String) -> Unit
) {
    var keyText by remember(p.id) { mutableStateOf("") }
    var modelText by remember(p.id, p.model) { mutableStateOf(p.model) }
    var role by remember(p.id, p.role) { mutableStateOf(p.role) }
    var enabled by remember(p.id, p.enabled) { mutableStateOf(p.enabled) }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(text = p.name, color = V8.Text1, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                text = if (p.hasKey) "● " + p.maskedKey + if (!p.enabled) " (off)" else "" else tr(lang, "not connected", "कनेक्ट नहीं", "कनेक्ट नाही"),
                color = if (p.hasKey && p.enabled) V8.Green else V8.Text3, fontSize = 10.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            V8Chip(text = if (open) "▴" else tr(lang, "Set up ▾", "सेट करें ▾", "सेट करा ▾"), selected = false) { onToggle() }
        }
        if (open) {
            Text(text = tr(lang, "Get a key: ", "Key यहाँ से: ", "Key येथून: ") + p.keyUrl, color = V8.Info, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            OutlinedTextField(
                value = keyText,
                onValueChange = { keyText = it.trim() },
                label = { Text(text = if (p.hasKey) tr(lang, "New API key (blank = keep)", "नई API key (खाली = वही रखें)", "नवी API key (रिकामे = तीच ठेवा)") else "API key", fontSize = 11.sp) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = V8.Gold, unfocusedBorderColor = V8.Line,
                    focusedTextColor = V8.Text1, unfocusedTextColor = V8.Text1, cursorColor = V8.Gold
                )
            )
            OutlinedTextField(
                value = modelText,
                onValueChange = { modelText = it.trim() },
                label = { Text(text = tr(lang, "Model (default ", "मॉडल (डिफ़ॉल्ट ", "मॉडेल (डिफॉल्ट ") + p.defaultModel + ")", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = V8.Gold, unfocusedBorderColor = V8.Line,
                    focusedTextColor = V8.Text1, unfocusedTextColor = V8.Text1, cursorColor = V8.Gold
                )
            )
            Text(text = tr(lang, "Analyst role", "विश्लेषक भूमिका", "विश्लेषक भूमिका"), color = V8.Text3, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ROLES.forEach { (k, lbl) -> V8Chip(text = lbl, selected = role == k) { role = k } }
            }
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                V8Chip(text = if (enabled) tr(lang, "Voting ON", "वोटिंग चालू", "मतदान चालू") else tr(lang, "Voting OFF", "वोटिंग बंद", "मतदान बंद"), selected = enabled) { enabled = !enabled }
                V8Chip(text = tr(lang, "Save", "सेव", "सेव्ह"), selected = true) {
                    onSave(p.id, keyText.ifBlank { null }, modelText, role, enabled)
                    keyText = ""
                }
                if (p.hasKey) {
                    V8Chip(text = if (test?.running == true) tr(lang, "Testing…", "टेस्ट…", "टेस्ट…") else "Test", selected = false) { if (test?.running != true) onTest(p.id) }
                    V8Chip(text = tr(lang, "Remove key", "Key हटाएँ", "Key काढा"), selected = false) { onRemoveKey(p.id) }
                }
            }
            if (test != null && !test.running) {
                Text(
                    text = (if (test.ok) "✓ " else "✕ ") + test.status.replace('_', ' ') + " • " + test.detail,
                    color = if (test.ok && test.status == "ONLINE") V8.Green else if (test.ok) V8.Amber else V8.Red,
                    fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp)
                )
                if (test.models.isNotEmpty()) {
                    Text(text = tr(lang, "Your models (tap to use):", "आपके मॉडल (टैप करें):", "तुमची मॉडेल (टॅप करा):"), color = V8.Text3, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        test.models.forEach { m ->
                            V8Chip(text = m, selected = m == p.model) {
                                modelText = m
                                onSave(p.id, null, m, role, enabled)
                            }
                        }
                    }
                }
            }
        }
    }
}
