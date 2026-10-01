package com.example.livegoldai.ui.v8

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.HealthCategory
import com.example.livegoldai.model.SystemHealth

/**
 * HEALTH — what is working right now. Scores are the share of real checks that passed
 * on the last refresh. Problems are never hidden.
 */
@Composable
fun V8HealthScreen(health: SystemHealth?, lang: AppLanguage) {
    var openKey by remember { mutableStateOf<String?>(null) }
    var showAllIndicators by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(V8.Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (health == null) {
            V8Card(level = 1) {
                Text(text = tr(lang, "Health check runs after the first data refresh.", "पहले डेटा रिफ्रेश के बाद हेल्थ जाँच चलेगी।", "पहिल्या डेटा रिफ्रेशनंतर हेल्थ तपासणी चालेल."), color = V8.Text2, fontSize = 12.sp)
            }
        } else {

        // ---------------- hero
        V8Card(level = 1, accent = statusColor(health.overallStatus).copy(alpha = 0.5f)) {
            V8Label(tr(lang, "System health", "सिस्टम हेल्थ", "सिस्टम हेल्थ"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "${health.overallScore}", color = statusColor(health.overallStatus), fontSize = 40.sp, fontWeight = FontWeight.Black)
                Text(text = " / 100", color = V8.Text3, fontSize = 14.sp)
                Spacer(modifier = Modifier.weight(1f))
                V8Badge(health.overallStatus.replace('_', ' '), statusColor(health.overallStatus))
            }
            Text(
                text = tr(lang, "Checked ", "जाँचा ", "तपासले ") + health.checkedAtLabel + tr(lang, " • share of checks passed", " • पास हुई जाँचों का हिस्सा", " • पास झालेल्या तपासण्यांचा वाटा") +
                    if ((health.category("AI")?.score ?: -1) < 0) tr(lang, " (AI not counted: not connected)", " (AI गिना नहीं: कनेक्ट नहीं)", " (AI मोजले नाही: कनेक्ट नाही)") else "",
                color = V8.Text3, fontSize = 9.sp
            )
        }

        // ---------------- categories
        health.categories.forEach { c -> CategoryCard(c, open = openKey == c.key, lang = lang) { openKey = if (openKey == c.key) null else c.key } }

        // ---------------- indicator grid
        V8Card(level = 2) {
            val ok = health.indicators.count { it.status == "OK" }
            Row(verticalAlignment = Alignment.CenterVertically) {
                V8Label(tr(lang, "Indicator self-test", "इंडिकेटर सेल्फ-टेस्ट", "इंडिकेटर सेल्फ-टेस्ट"))
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "$ok / ${health.indicators.size} " + tr(lang, "healthy", "ठीक", "ठीक"), color = if (ok == health.indicators.size) V8.Green else V8.Amber, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            val list = if (showAllIndicators) health.indicators else health.indicators.sortedBy { if (it.status == "OK") 1 else 0 }.take(8)
            list.chunked(2).forEach { pair ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    pair.forEach { chk ->
                        Text(
                            text = "${statusMark(chk.status)} ${chk.name}",
                            color = statusColor(chk.status), fontSize = 10.sp, maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
            if (health.indicators.size > 8) {
                Spacer(modifier = Modifier.height(6.dp))
                V8Chip(
                    text = if (showAllIndicators) tr(lang, "Show less ▴", "कम दिखाएँ ▴", "कमी दाखवा ▴") else tr(lang, "Show all ${health.indicators.size} ▾", "सभी ${health.indicators.size} दिखाएँ ▾", "सर्व ${health.indicators.size} दाखवा ▾"),
                    selected = false
                ) { showAllIndicators = !showAllIndicators }
            }
        }

        // ---------------- bots
        if (health.bots.isNotEmpty()) {
            V8Card(level = 2) {
                V8Label(tr(lang, "Rule bots • activity", "नियम बॉट • गतिविधि", "नियम बॉट • क्रिया"))
                health.bots.forEach { b ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = if (b.healthy) "●" else "○", color = if (b.healthy) V8.Green else V8.Red, fontSize = 10.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = b.name, color = V8.Text1, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(text = tr(lang, "Ran ", "चला ", "चालला ") + b.lastEvaluationLabel + " • " + b.verifiedLabel, color = V8.Text3, fontSize = 9.sp)
                        }
                        Text(text = arrowOf(b.signal) + " " + b.signal.name, color = signalColor(b.signal), fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        // ---------------- event log
        V8Card(level = 3) {
            V8Label(tr(lang, "Event log (this session)", "इवेंट लॉग (इस सेशन)", "इव्हेंट लॉग (हे सेशन)"))
            if (health.events.isEmpty()) {
                Text(text = tr(lang, "No events yet.", "अभी कोई इवेंट नहीं।", "अजून इव्हेंट नाही."), color = V8.Text3, fontSize = 10.sp)
            }
            health.events.take(30).forEach { e ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(text = e.level, color = statusColor(e.level), fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(64.dp))
                    Text(text = e.timeLabel, color = V8.Text3, fontSize = 9.sp, modifier = Modifier.width(66.dp))
                    Text(text = e.text, color = V8.Text1, fontSize = 10.sp, modifier = Modifier.weight(1f))
                }
            }
        }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CategoryCard(c: HealthCategory, open: Boolean, lang: AppLanguage, onToggle: () -> Unit) {
    val color = if (c.score < 0) V8.Text3 else statusColor(c.status)
    V8Card(level = 2, onClick = onToggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = c.title, color = V8.Text1, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(text = if (c.score < 0) "OFF" else "${c.score}", color = color, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
        if (c.score >= 0) {
            LinearProgressIndicator(
                progress = { (c.score / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(4.dp),
                color = color,
                trackColor = V8.Line
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            V8Badge(c.status.replace('_', ' '), color)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = c.summary, color = V8.Text3, fontSize = 10.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = if (open) "▴" else "▾", color = V8.Text3, fontSize = 12.sp)
        }
        if (open) {
            c.checks.forEach { chk ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(text = statusMark(chk.status), color = statusColor(chk.status), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(18.dp))
                    Text(text = chk.name, color = V8.Text1, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.4f))
                    Text(text = chk.detail, color = V8.Text3, fontSize = 10.sp, modifier = Modifier.weight(0.6f))
                }
            }
        }
    }
}
