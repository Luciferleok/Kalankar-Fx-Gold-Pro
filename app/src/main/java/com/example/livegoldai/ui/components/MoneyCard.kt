package com.example.livegoldai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.data.TradeEconomics
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.theme.*

/** V36-B: hypothetical money check of this timeframe's calls (read-only; never changes a call). */
@Composable
internal fun MoneyCard(s: TradeEconomics.Summary, lang: AppLanguage) {
    fun t(en: String, hi: String, mr: String) = when (lang) { AppLanguage.ENGLISH -> en; AppLanguage.HINDI -> hi; AppLanguage.MARATHI -> mr }
    val loss = s.all.trades > 0 && s.all.netUsd < 0
    Surface(shape = RoundedCornerShape(14.dp), color = ObsidianSurfaceCard, border = BorderStroke(1.dp, if (loss) Color(0xFFE5484D).copy(alpha = 0.6f) else GoldPrimary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(t("MONEY CHECK (HYPOTHETICAL)", "पैसे की जाँच (काल्पनिक)", "पैशांची तपासणी (काल्पनिक)"), color = GoldPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(t("If every ${s.interval.uppercase()} call had been traded, one trade at a time, after an assumed \$${"%.2f".format(TradeEconomics.COST_USD)} spread + slippage per trade:",
                "अगर हर ${s.interval.uppercase()} कॉल पर, एक बार में एक ट्रेड, हर ट्रेड पर अनुमानित \$${"%.2f".format(TradeEconomics.COST_USD)} स्प्रेड + स्लिपेज के बाद ट्रेड होता:",
                "जर प्रत्येक ${s.interval.uppercase()} कॉलवर, एका वेळी एक ट्रेड, प्रत्येक ट्रेडवर अंदाजे \$${"%.2f".format(TradeEconomics.COST_USD)} स्प्रेड + स्लिपेज नंतर ट्रेड झाला असता:"),
                color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
            @Composable fun line(title: String, b: TradeEconomics.Book) {
                if (b.trades == 0) { Text("$title: " + t("no trades yet", "अभी कोई ट्रेड नहीं", "अजून ट्रेड नाही"), color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)); return }
                Text(title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                Text(t("${b.trades} trades (from ${b.overlapping} overlapping calls) · right ${b.hitPct}% · net ${TradeEconomics.usd(b.netUsd)} · avg ${TradeEconomics.usd(b.avgUsd)} · worst drop -\$${"%.2f".format(b.maxDrawdownUsd)}",
                    "${b.trades} ट्रेड (${b.overlapping} ओवरलैपिंग कॉल से) · सही ${b.hitPct}% · कुल ${TradeEconomics.usd(b.netUsd)} · औसत ${TradeEconomics.usd(b.avgUsd)} · सबसे बड़ी गिरावट -\$${"%.2f".format(b.maxDrawdownUsd)}",
                    "${b.trades} ट्रेड (${b.overlapping} ओव्हरलॅपिंग कॉलमधून) · बरोबर ${b.hitPct}% · एकूण ${TradeEconomics.usd(b.netUsd)} · सरासरी ${TradeEconomics.usd(b.avgUsd)} · सर्वात मोठी घसरण -\$${"%.2f".format(b.maxDrawdownUsd)}"),
                    color = if (b.netUsd < 0) Color(0xFFE5484D) else Color(0xFF30A46C), fontSize = 12.sp, lineHeight = 17.sp)
            }
            line(t("Engine calls (all check methods)", "इंजन कॉल (सभी जाँच तरीके)", "इंजिन कॉल (सर्व तपासणी पद्धती)"), s.all)
            line(t("Engine calls, current check method (EV2)", "इंजन कॉल, मौजूदा जाँच तरीका (EV2)", "इंजिन कॉल, सध्याची तपासणी पद्धत (EV2)"), s.current)
            line(t("Shown on HOME as BUY / SELL", "HOME पर BUY / SELL दिखी", "HOME वर BUY / SELL दिसली"), s.shown)
            line(t("Held as WAIT by the gate (+ = profit it skipped, - = loss it avoided)", "गेट ने WAIT रखी (+ = छूटा फ़ायदा, - = बचा नुकसान)", "गेटने WAIT ठेवली (+ = सुटलेला नफा, - = टळलेला तोटा)"), s.held)
            Text(t("\$ per 1 unit of gold price. Hypothetical: your broker's spread, slippage, lot size and timing will differ. Not a promise.",
                "सोने के भाव की 1 यूनिट पर \$। काल्पनिक: आपके ब्रोकर का स्प्रेड, स्लिपेज, लॉट साइज़ और समय अलग होगा। वादा नहीं।",
                "सोन्याच्या भावाच्या 1 युनिटवर \$. काल्पनिक: तुमच्या ब्रोकरचा स्प्रेड, स्लिपेज, लॉट साइज आणि वेळ वेगळे असतील. खात्री नाही."),
                color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
