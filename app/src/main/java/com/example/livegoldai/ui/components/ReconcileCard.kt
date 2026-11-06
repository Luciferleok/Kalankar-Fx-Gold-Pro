package com.example.livegoldai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.data.LedgerReconcile
import com.example.livegoldai.data.OutcomeBrowser
import com.example.livegoldai.data.PredictionLedger
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.theme.*

/** V35.13: read-only price-anchor check of this timeframe's results (nothing is rewritten; tiles keep the stored results). */
@Composable
internal fun ReconcileCard(s: LedgerReconcile.Summary, lang: AppLanguage) {
    fun t(en: String, hi: String, mr: String) = when (lang) { AppLanguage.ENGLISH -> en; AppLanguage.HINDI -> hi; AppLanguage.MARATHI -> mr }
    var open by remember { mutableStateOf(false) }
    val warn = s.changed + s.uncertain > 0
    Surface(shape = RoundedCornerShape(14.dp), color = ObsidianSurfaceCard, border = BorderStroke(1.dp, if (warn) GoldPrimary.copy(alpha = 0.7f) else TextMuted.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "Show the price check details") { open = !open }) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(t("PRICE CHECK (READ-ONLY)", "प्राइस जाँच (सिर्फ़ पढ़ने के लिए)", "प्राइस तपासणी (फक्त वाचन)"), color = GoldPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(t("If each of the ${s.checked} checked ${s.interval.uppercase()} calls had been measured from its PUBLISHED price: ${s.unchanged} same, ${s.changed} different, ${s.uncertain} cannot be re-checked.",
                "अगर ${s.checked} जाँची ${s.interval.uppercase()} कॉल अपने प्रकाशित भाव से नापी जातीं: ${s.unchanged} वैसी ही, ${s.changed} अलग, ${s.uncertain} दोबारा जाँच नहीं हो सकती।",
                "जर ${s.checked} तपासलेल्या ${s.interval.uppercase()} कॉल प्रकाशित भावापासून मोजल्या असत्या: ${s.unchanged} तशाच, ${s.changed} वेगळ्या, ${s.uncertain} पुन्हा तपासता येत नाहीत."),
                color = TextPrimary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
            fun line(m: Map<String, Int>) = listOf("CORRECT", "WRONG", "NO EDGE", "UNCERTAIN").filter { (m[it] ?: 0) > 0 }.joinToString(" · ") { "$it ${m[it]}" }
            Text(t("Recorded: ", "दर्ज: ", "नोंदवलेले: ") + line(s.before), color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Text(t("From the published price: ", "प्रकाशित भाव से: ", "प्रकाशित भावापासून: ") + line(s.after), color = TextSecondary, fontSize = 12.sp)
            Text(t("Hypothetical only. The tiles above keep the recorded results; nothing was changed. Checks from V35.13 on start from the published price.",
                "सिर्फ़ अनुमान। ऊपर के टाइल दर्ज नतीजे ही गिनते हैं; कुछ नहीं बदला। V35.13 से नई जाँच प्रकाशित भाव से शुरू होती है।",
                "फक्त अंदाज. वरचे टाइल नोंदवलेले निकालच मोजतात; काहीही बदलले नाही. V35.13 पासून नवीन तपासणी प्रकाशित भावापासून सुरू होते."),
                color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 4.dp))
            if (open) s.rows.filter { it.cls != LedgerReconcile.Cls.UNCHANGED }.take(30).forEach { r ->
                Text("${r.side.name} ${OutcomeBrowser.ts(r.createdAt)} · ${r.original} -> ${if (r.cls == LedgerReconcile.Cls.UNCERTAIN) "UNCERTAIN" else r.hypothetical}" +
                    " · published ${PredictionLedger.fmt(r.published)} vs first bar ${PredictionLedger.fmt(r.firstOpen)} (${"%+.2f".format(r.diff)}) · ${r.note}",
                    color = if (r.cls == LedgerReconcile.Cls.CHANGED) GoldPrimary else TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
            }
            else if (warn) Text(t("Tap for the list", "सूची के लिए टैप करें", "यादीसाठी टॅप करा"), color = GoldPrimary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
