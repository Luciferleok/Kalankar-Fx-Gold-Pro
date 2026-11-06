package com.example.livegoldai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.livegoldai.data.OutcomeBrowser
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.Signal
import com.example.livegoldai.theme.*
import java.util.Locale

/**
 * V35.7 prediction results: the actual ledger records behind the SAHI / GALAT tiles of the shown timeframe.
 * Four separate lists (correct, wrong, pending, no edge), counted from one population; each record expands to its
 * checked prices and timeline. Display only: nothing here changes a record.
 */
@Composable
fun OutcomeBrowserDialog(page: OutcomeBrowser.Page, lang: AppLanguage, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    fun t(en: String, hi: String, mr: String) = when (lang) { AppLanguage.ENGLISH -> en; AppLanguage.HINDI -> hi; AppLanguage.MARATHI -> mr }
    val c = page.counts
    val correct = c[OutcomeBrowser.Kind.CORRECT] ?: 0
    val wrong = c[OutcomeBrowser.Kind.WRONG] ?: 0
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 20.dp).testTag("outcome_browser_${page.kind.name}"),
            shape = RoundedCornerShape(22.dp),
            color = ObsidianBackground,
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = t("PREDICTION RESULTS", "प्रेडिक्शन नतीजे", "अंदाजांचे निकाल"), color = GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                        Text(text = page.interval.uppercase(Locale.US) + t(" · this phone's ledger", " · इस फ़ोन का लेजर", " · या फोनचा लेजर"), color = TextSecondary, fontSize = 11.sp)
                    }
                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
                        Text(text = "✕", color = TextSecondary, fontSize = 18.sp)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                // four separate states, one population; the selected one is outlined
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KindTile(t("CORRECT", "सही", "बरोबर"), correct, SignalBuy, page.kind == OutcomeBrowser.Kind.CORRECT, Modifier.weight(1f)) { onSelect("CORRECT") }
                    KindTile(t("WRONG", "गलत", "चूक"), wrong, SignalSell, page.kind == OutcomeBrowser.Kind.WRONG, Modifier.weight(1f)) { onSelect("WRONG") }
                    KindTile(t("PENDING", "बाकी", "बाकी"), c[OutcomeBrowser.Kind.PENDING] ?: 0, GoldPrimary, page.kind == OutcomeBrowser.Kind.PENDING, Modifier.weight(1f)) { onSelect("PENDING") }
                    KindTile(t("NO EDGE", "कोई बढ़त नहीं", "धार नाही"), c[OutcomeBrowser.Kind.NO_EDGE] ?: 0, TextSecondary, page.kind == OutcomeBrowser.Kind.NO_EDGE, Modifier.weight(1f)) { onSelect("NO_EDGE") }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (correct + wrong == 0) t("No checked call yet.", "अभी कोई जाँची गई कॉल नहीं।", "अजून तपासलेली कॉल नाही.")
                    else t("Directional hit-rate = correct ÷ (correct + wrong) = ", "दिशा हिट-रेट = सही ÷ (सही + गलत) = ", "दिशा हिट-रेट = बरोबर ÷ (बरोबर + चूक) = ") +
                        "$correct ÷ ${correct + wrong} = ${Math.round(100.0 * correct / (correct + wrong))}%" +
                        t(" · past record, not a probability. Pending and no-edge are not counted.", " · पुराना रिकॉर्ड, संभावना नहीं। बाकी और बिना बढ़त वाली गिनी नहीं जातीं।", " · जुनी नोंद, शक्यता नाही. बाकी आणि धार नसलेल्या मोजल्या जात नाहीत."),
                    color = TextSecondary, fontSize = 10.sp, lineHeight = 14.sp
                )
                Text(text = page.population, color = TextMuted, fontSize = 9.sp, lineHeight = 12.sp, modifier = Modifier.padding(top = 4.dp))
                if (page.total > page.rows.size) Text(text = t("Showing the latest ${page.rows.size} of ${page.total}.", "${page.total} में से नई ${page.rows.size} दिख रही हैं।", "${page.total} पैकी नवीन ${page.rows.size} दिसत आहेत."),
                    color = TextMuted, fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(modifier = Modifier.height(8.dp))
                if (page.rows.isEmpty()) Text(text = t("No record in this list yet.", "इस लिस्ट में अभी कोई रिकॉर्ड नहीं।", "या यादीत अजून नोंद नाही."), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 20.dp))
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    page.reconcile?.takeIf { it.checked > 0 }?.let { rc -> item(key = "reconcile") { ReconcileCard(rc, lang) } }
                    items(page.rows, key = { it.id }) { RecordCard(it, lang) }
                }
            }
        }
    }
}

@Composable
private fun KindTile(label: String, n: Int, color: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) color.copy(alpha = 0.14f) else ObsidianSurfaceCard)
            .border(if (selected) 1.5.dp else 0.75.dp, if (selected) color else ObsidianBorderHighlight, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "$n", color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = TextSecondary, fontSize = 8.sp, letterSpacing = 0.6.sp, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun RecordCard(r: OutcomeBrowser.Row, lang: AppLanguage) {
    fun t(en: String, hi: String, mr: String) = when (lang) { AppLanguage.ENGLISH -> en; AppLanguage.HINDI -> hi; AppLanguage.MARATHI -> mr }
    var open by remember(r.id) { mutableStateOf(false) }
    val sideColor = if (r.side == Signal.BUY) SignalBuy else SignalSell
    val outColor = when (r.outcome) { "CORRECT" -> SignalBuy; "INCORRECT", "INVALIDATED" -> SignalSell; "PENDING" -> GoldPrimary; else -> TextSecondary }
    fun usd(x: Double) = if (x.isNaN()) "--" else String.format(Locale.US, "%+.2f", x)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurfaceCard)
            .border(0.75.dp, outColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable { open = !open }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = r.side.name, color = sideColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = OutcomeBrowser.ts(r.createdAtMs), color = TextPrimary, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(text = r.outcome, color = outColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            text = t("Entry ", "एंट्री ", "एंट्री ") + String.format(Locale.US, "%.2f", r.entry) + " · " + t("agreement ", "सहमति ", "सहमती ") + "${r.agreement}%" +
                (if (!r.moveUsd.isNaN()) " · " + t("close move ", "क्लोज़ चाल ", "क्लोज हालचाल ") + "${usd(r.moveUsd)} \$ (" + t("noise ±", "शोर ±", "आवाज ±") + String.format(Locale.US, "%.2f", r.thresholdUsd) + ")" else ""),
            color = TextSecondary, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp)
        )
        // V35.7.1: how it was decided (a call can be CORRECT with a negative close move when the target came first)
        if (r.reason.isNotBlank()) Text(text = "→ " + r.reason, color = outColor, fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
        AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(modifier = Modifier.padding(top = 6.dp)) {
                Detail(t("Window ends", "विंडो समाप्त", "विंडो संपते"), OutcomeBrowser.ts(r.expiresAtMs))
                Detail(t("Provider / engine", "प्रोवाइडर / इंजन", "प्रोव्हायडर / इंजिन"), "${r.provider} · ${r.engine}")
                Detail(t("Market type then", "तब मार्केट", "तेव्हा मार्केट"), r.regime.ifBlank { "--" })
                Detail(t("Best / worst move in window", "विंडो में सबसे अच्छी / बुरी चाल", "विंडोत सर्वोत्तम / वाईट हालचाल"), "${usd(r.mfeUsd)} / ${usd(-r.maeUsd)} \$")
                if (r.stopHitAtMs > 0L) Detail(t("Stop touched", "स्टॉप छुआ", "स्टॉप लागला"), OutcomeBrowser.ts(r.stopHitAtMs))
                Detail(t("Checked with", "जाँचा गया", "तपासले"), r.verifiedBy.ifBlank { "--" } + if (r.checkedAtMs > 0L) " · " + OutcomeBrowser.ts(r.checkedAtMs) else "")
                if (r.tags.isNotEmpty()) Detail(t("Failure tags", "गलती के टैग", "चुकीचे टॅग"), r.tags.joinToString(" · ") { it.replace('_', ' ') })
                if (r.counterfactual.isNotBlank()) Detail(t("Could it be avoided?", "क्या बच सकते थे?", "टाळता आले असते?"), r.counterfactual)
                if (r.timeline.isNotEmpty()) {
                    Text(text = t("TIMELINE", "टाइमलाइन", "टाइमलाइन"), color = GoldLight, fontSize = 9.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 6.dp))
                    r.timeline.forEach { (ms, what) -> Text(text = OutcomeBrowser.ts(ms) + " · " + what, color = TextMuted, fontSize = 9.sp) }
                }
            }
        }
    }
}

@Composable
private fun Detail(k: String, v: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = k, color = TextMuted, fontSize = 10.sp, modifier = Modifier.weight(0.45f))
        Text(text = v, color = TextPrimary, fontSize = 10.sp, modifier = Modifier.weight(0.55f))
    }
}
