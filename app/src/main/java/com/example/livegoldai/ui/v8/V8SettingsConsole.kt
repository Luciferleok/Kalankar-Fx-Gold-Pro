package com.example.livegoldai.ui.v8

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage

/** Everything the console shows, read from real app state by the caller. */
data class SettingsConsoleState(
    val lang: AppLanguage,
    val themeTitle: String,
    val amoled: Boolean,
    val depth: String,                 // FLAT / SIGNATURE / DEEP
    val interval: String,
    val feedLine: String,              // e.g. "Twelve Data • LIVE • 12s"
    val feedKeyLine: String,           // e.g. "Saved (…51db0)" / "Not set"
    val aiLine: String,                // e.g. "2 of 5 keys saved • mode AUTO"
    val learnLine: String,             // e.g. "34 checked • 0 active"
    val alertLine: String,             // e.g. "Off" / "4,150.00"
    val recorderOn: Boolean,
    val recorderLine: String,
    val healthLine: String,            // e.g. "86/100 • HEALTHY"
    val engine: String,
    val appVersion: String
)

class SettingsConsoleActions(
    val onLang: (AppLanguage) -> Unit,
    val onOpenTheme: () -> Unit,
    val onAmoled: (Boolean) -> Unit,
    val onDepth: (String) -> Unit,
    val onInterval: (String) -> Unit,
    val onSaveFeedKey: (String) -> Unit,
    val onOpenAi: () -> Unit,
    val onOpenLearning: () -> Unit,
    val onOpenAlert: () -> Unit,
    val onRecorder: (Boolean) -> Unit,
    val onOpenSystem: () -> Unit,
    val onResetAppearance: () -> Unit,
    val onResetChart: () -> Unit,
    val onClose: () -> Unit
)

/**
 * SETTINGS CONSOLE (V25.1)
 * A control room, not a stack of cards: sections, precise rows, a real status line on every row, search,
 * and only settings that actually work in this build.
 */
@Composable
fun V8SettingsConsole(s: SettingsConsoleState, a: SettingsConsoleActions) {
    val lang = s.lang
    var query by remember { mutableStateOf("") }
    var editKey by remember { mutableStateOf(false) }
    var keyDraft by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }      // which reset is waiting for a second tap
    val haptic = LocalHapticFeedback.current
    val q = query.trim().lowercase()
    fun show(vararg words: String) = q.isEmpty() || words.any { it.lowercase().contains(q) }

    Column(modifier = Modifier.fillMaxSize()) {
        KalankarBackdropHeaderSpacer()
        // ---- title bar
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(text = tr(lang, "Settings", "सेटिंग्स", "सेटिंग्ज"), color = V8.Text1, style = KType.ScreenTitle, modifier = Modifier.weight(1f))
            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).v8Press(onClick = a.onClose).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(text = tr(lang, "DONE", "हो गया", "झाले"), color = V8.Gold, style = KType.SectionLabel)
            }
        }
        // ---- search
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(12.dp))
                .background(V8.Card2).padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (query.isEmpty()) Text(text = tr(lang, "Search: theme, AI, chart, language…", "खोजें: थीम, AI, चार्ट, भाषा…", "शोधा: थीम, AI, चार्ट, भाषा…"), color = V8.Text3, style = KType.BodyPrimary)
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = V8.Text1, fontSize = 13.sp), cursorBrush = SolidColor(V8.Gold),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {

            // ================= GENERAL
            if (show("general", "language", "भाषा", "timeframe", "chart", "interval")) {
                Section(tr(lang, "GENERAL", "सामान्य", "सामान्य"))
                if (show("language", "भाषा", "hindi", "marathi", "english")) Row1(tr(lang, "Language", "भाषा", "भाषा"), when (lang) { AppLanguage.ENGLISH -> "English"; AppLanguage.HINDI -> "हिन्दी"; AppLanguage.MARATHI -> "मराठी" }) {
                    Segmented(listOf("EN", "हि", "म"), listOf(AppLanguage.ENGLISH, AppLanguage.HINDI, AppLanguage.MARATHI).indexOf(lang)) { i ->
                        a.onLang(listOf(AppLanguage.ENGLISH, AppLanguage.HINDI, AppLanguage.MARATHI)[i])
                    }
                }
                if (show("timeframe", "chart", "interval", "default")) Row1(tr(lang, "Default timeframe", "डिफ़ॉल्ट टाइमफ्रेम", "डिफॉल्ट टाइमफ्रेम"), s.interval.uppercase(), stacked = true) {
                    val ivs = listOf("5m", "15m", "30m", "1h", "4h", "1d")
                    Segmented(ivs.map { it.uppercase() }, ivs.indexOf(s.interval)) { i -> a.onInterval(ivs[i]) }
                }
            }

            // ================= APPEARANCE
            if (show("appearance", "theme", "थीम", "amoled", "black", "background", "depth", "haptic", "vibration")) {
                Section(tr(lang, "APPEARANCE", "रूप", "स्वरूप"))
                if (show("theme", "थीम", "color", "colour")) Row1(tr(lang, "Theme", "थीम", "थीम"), s.themeTitle, onClick = a.onOpenTheme) { Chevron() }
                if (show("amoled", "black", "dark")) Row1("AMOLED", if (s.amoled) tr(lang, "Pure black background", "पूरा काला बैकग्राउंड", "पूर्ण काळी पार्श्वभूमी") else tr(lang, "Theme background", "थीम बैकग्राउंड", "थीम पार्श्वभूमी")) {
                    Toggle(s.amoled) { a.onAmoled(it) }
                }
                if (show("background", "depth", "बैकग्राउंड")) Row1(tr(lang, "Background depth", "बैकग्राउंड गहराई", "पार्श्वभूमी खोली"), when (s.depth) { "FLAT" -> tr(lang, "Flat: one plain colour", "सपाट: एक रंग", "सपाट: एक रंग"); "DEEP" -> tr(lang, "Deep: richer tone, no motion", "गहरा: ज़्यादा टोन, कोई हलचल नहीं", "खोल: जास्त टोन, हालचाल नाही"); else -> tr(lang, "Signature (default)", "सिग्नेचर (डिफ़ॉल्ट)", "सिग्नेचर (डिफॉल्ट)") }, stacked = true) {
                    val ds = listOf("FLAT", "SIGNATURE", "DEEP")
                    Segmented(listOf("FLAT", "SIGN", "DEEP"), ds.indexOf(s.depth)) { i -> a.onDepth(ds[i]) }
                }
                if (show("haptic", "vibration", "touch")) Row1(tr(lang, "Haptics", "हैप्टिक्स", "हॅप्टिक्स"), tr(lang, "Follows the phone's touch-feedback setting", "फ़ोन की टच-फ़ीडबैक सेटिंग के अनुसार", "फोनच्या टच-फीडबॅक सेटिंगनुसार")) {
                    SmallAction(tr(lang, "TEST", "टेस्ट", "टेस्ट")) { haptic.performHapticFeedback(V8Haptics.Medium) }
                }
                if (show("reset", "appearance")) Row1(tr(lang, "Reset appearance", "रूप रीसेट", "स्वरूप रीसेट"), if (confirm == "app") tr(lang, "Tap again to confirm", "पक्का करने के लिए फिर दबाएँ", "खात्रीसाठी पुन्हा दाबा") else tr(lang, "Obsidian Gold, no AMOLED, Signature background", "ऑब्सीडियन गोल्ड, AMOLED बंद, सिग्नेचर", "ऑब्सिडियन गोल्ड, AMOLED बंद, सिग्नेचर")) {
                    SmallAction(if (confirm == "app") tr(lang, "CONFIRM", "पक्का", "खात्री") else tr(lang, "RESET", "रीसेट", "रीसेट"), danger = confirm == "app") {
                        if (confirm == "app") { a.onResetAppearance(); confirm = "" } else confirm = "app"
                    }
                }
            }

            // ================= MARKET & DATA
            if (show("market", "data", "feed", "source", "twelve", "key", "api")) {
                Section(tr(lang, "MARKET DATA", "मार्केट डेटा", "मार्केट डेटा"))
                if (show("feed", "source", "market", "live")) Row1(tr(lang, "Market feed", "मार्केट फ़ीड", "मार्केट फीड"), s.feedLine) {}
                if (show("key", "api", "twelve", "feed")) Row1(tr(lang, "Twelve Data key", "Twelve Data की", "Twelve Data की"), s.feedKeyLine, onClick = { editKey = !editKey; keyDraft = "" }) { Chevron() }
                if (editKey) {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(V8.Card2).border(0.6.dp, V8.Line, RoundedCornerShape(10.dp)).padding(12.dp)) {
                            if (keyDraft.isEmpty()) Text(text = tr(lang, "Paste a new key", "नई की पेस्ट करें", "नवीन की पेस्ट करा"), color = V8.Text3, style = KType.BodyPrimary)
                            BasicTextField(
                                value = keyDraft, onValueChange = { keyDraft = it.trim() }, singleLine = true,
                                textStyle = TextStyle(color = V8.Text1, fontSize = 13.sp), cursorBrush = SolidColor(V8.Gold),
                                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row {
                            Spacer(modifier = Modifier.weight(1f))
                            SmallAction(tr(lang, "SAVE", "सेव", "सेव्ह")) { if (keyDraft.length >= 16) { a.onSaveFeedKey(keyDraft); editKey = false; keyDraft = "" } }
                        }
                        Text(text = tr(lang, "Stored only on this phone. Backup feeds (PAXG, GC=F) need no key.", "सिर्फ़ इस फ़ोन में सेव। बैकअप फ़ीड (PAXG, GC=F) को की नहीं चाहिए।", "फक्त या फोनमध्ये सेव्ह. बॅकअप फीड (PAXG, GC=F) ला की लागत नाही."), color = V8.Text3, style = KType.Caption)
                    }
                }
                if (show("reset", "chart", "timeframe")) Row1(tr(lang, "Reset chart settings", "चार्ट सेटिंग रीसेट", "चार्ट सेटिंग रीसेट"), if (confirm == "chart") tr(lang, "Tap again to confirm", "पक्का करने के लिए फिर दबाएँ", "खात्रीसाठी पुन्हा दाबा") else tr(lang, "Default timeframe back to 4H", "डिफ़ॉल्ट टाइमफ्रेम 4H", "डिफॉल्ट टाइमफ्रेम 4H")) {
                    SmallAction(if (confirm == "chart") tr(lang, "CONFIRM", "पक्का", "खात्री") else tr(lang, "RESET", "रीसेट", "रीसेट"), danger = confirm == "chart") {
                        if (confirm == "chart") { a.onResetChart(); confirm = "" } else confirm = "chart"
                    }
                }
            }

            // ================= INTELLIGENCE
            if (show("intelligence", "ai", "learning", "accuracy", "provider", "model")) {
                Section(tr(lang, "INTELLIGENCE", "इंटेलिजेंस", "इंटेलिजन्स"))
                if (show("ai", "provider", "key", "council")) Row1(tr(lang, "AI providers", "AI प्रोवाइडर", "AI प्रोव्हायडर"), s.aiLine, onClick = a.onOpenAi) { Chevron() }
                if (show("learning", "accuracy", "ledger", "result")) Row1(tr(lang, "Learning Center", "लर्निंग सेंटर", "लर्निंग सेंटर"), s.learnLine, onClick = a.onOpenLearning) { Chevron() }
            }

            // ================= ALERTS
            if (show("alert", "alerts", "notification", "price")) {
                Section(tr(lang, "ALERTS", "अलर्ट", "अलर्ट"))
                Row1(tr(lang, "Price alert", "प्राइस अलर्ट", "प्राइस अलर्ट"), s.alertLine, onClick = a.onOpenAlert) { Chevron() }
            }

            // ================= BACKGROUND
            if (show("background", "recorder", "battery", "24/7")) {
                Section(tr(lang, "BACKGROUND", "बैकग्राउंड", "पार्श्वभूमी"))
                Row1(tr(lang, "Prediction recorder", "प्रेडिक्शन रिकॉर्डर", "प्रेडिक्शन रेकॉर्डर"), s.recorderLine) { Toggle(s.recorderOn) { a.onRecorder(it) } }
            }

            // ================= SYSTEM
            if (show("system", "health", "engine", "diagnostic")) {
                Section(tr(lang, "SYSTEM", "सिस्टम", "सिस्टम"))
                Row1(tr(lang, "System health", "सिस्टम हेल्थ", "सिस्टम हेल्थ"), s.healthLine, onClick = a.onOpenSystem) { Chevron() }
                Row1(tr(lang, "Engine", "इंजन", "इंजन"), s.engine) {}
            }

            // ================= ABOUT
            if (show("about", "version", "brand", "kalankar")) {
                Section(tr(lang, "ABOUT", "परिचय", "माहिती"))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
                    KalankarBrandEmblem(mode = EmblemMode.HERO)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "KALANKAR", color = V8.Text1, style = KType.BrandDisplay)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "FX GOLD PRO", color = V8.Gold, style = KType.BrandSecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "PRIVATE MARKET INTELLIGENCE", color = V8.Text3, style = KType.SectionLabel)
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(modifier = Modifier.width(120.dp).height(0.6.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, V8.Gold.copy(alpha = 0.6f), Color.Transparent))))
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = "Version ${s.appVersion}  •  Engine ${s.engine}", color = V8.Text3, style = KType.Caption)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "By Mr. Rudvay Ujjwal Kalankar", color = V8.Text2, style = KType.BodySecondary)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = tr(lang, "Signals are research output, not financial advice.", "सिग्नल रिसर्च हैं, निवेश सलाह नहीं।", "सिग्नल हे संशोधन आहे, गुंतवणूक सल्ला नाही."),
                        color = V8.Text3, style = KType.Caption
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable private fun KalankarBackdropHeaderSpacer() { Spacer(modifier = Modifier.height(4.dp)) }

@Composable
private fun Section(title: String) {
    Text(text = title, color = V8.Text3, style = KType.SectionLabel, modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(V8.Line))
}

/** One precise row: title, real status line, trailing control. Hairline below, no card. */
@Composable
private fun Row1(title: String, status: String, onClick: (() -> Unit)? = null, stacked: Boolean = false, trailing: @Composable () -> Unit) {
    val base = Modifier.fillMaxWidth()
    if (stacked) {
        // wide controls (timeframes, depth) go under the title so nothing is squeezed on a small phone
        Column(modifier = base.padding(vertical = 12.dp)) {
            Text(text = title, color = V8.Text1, style = KType.BodyPrimary)
            if (status.isNotBlank()) Text(text = status, color = V8.Text3, style = KType.Caption)
            Spacer(modifier = Modifier.height(8.dp))
            trailing()
        }
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(V8.Line.copy(alpha = 0.6f)))
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = (if (onClick != null) base.v8Press(onClick = onClick) else base).padding(vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = V8.Text1, style = KType.BodyPrimary)
            if (status.isNotBlank()) Text(text = status, color = V8.Text3, style = KType.Caption)
        }
        Spacer(modifier = Modifier.width(12.dp))
        trailing()
    }
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(V8.Line.copy(alpha = 0.6f)))
}

@Composable private fun Chevron() { Text(text = "›", color = V8.Text3, fontSize = 18.sp) }

@Composable
private fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(40.dp, 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (on) V8.Gold.copy(alpha = 0.85f) else V8.Card2)
            .border(0.6.dp, if (on) V8.Gold else V8.Line, RoundedCornerShape(11.dp))
            .v8Press(onClick = { haptic.performHapticFeedback(V8Haptics.Light); onChange(!on) })
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(modifier = Modifier.size(16.dp).clip(RoundedCornerShape(8.dp)).background(if (on) V8.Bg else V8.Text3))
    }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(V8.Card2).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (sel) V8.Card else Color.Transparent)
                    .border(0.6.dp, if (sel) V8.Gold.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(8.dp))
                    .v8Press(onClick = { if (!sel) { haptic.performHapticFeedback(V8Haptics.Light); onSelect(i) } })
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(text = o, color = if (sel) V8.Gold else V8.Text2, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun SmallAction(text: String, danger: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(0.6.dp, if (danger) V8.Red else V8.Line, RoundedCornerShape(8.dp))
            .v8Press(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text = text, color = if (danger) V8.Red else V8.Gold, style = KType.SectionLabel)
    }
}
