package com.example.livegoldai.ui.v8

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.livegoldai.localization.AppLanguage

/** COCKPIT • FORECAST • AI • HEALTH • LEARNING • MORE */
@Composable
fun V8BottomNav(selected: Int, lang: AppLanguage, healthStatus: String?, onSelect: (Int) -> Unit) {
    val items = listOf(
        "◎" to tr(lang, "Cockpit", "कॉकपिट", "कॉकपिट"),
        "↗" to tr(lang, "Forecast", "अनुमान", "अंदाज"),
        "✦" to "AI",
        "♥" to tr(lang, "Health", "हेल्थ", "हेल्थ"),
        "◆" to tr(lang, "Learn", "लर्निंग", "लर्निंग"),
        "☰" to tr(lang, "More", "और", "अधिक")
    )
    NavigationBar(containerColor = V8.Card, contentColor = V8.Text2) {
        items.forEachIndexed { i, (icon, label) ->
            val iconColor = if (i == 3 && healthStatus != null && healthStatus != "HEALTHY") statusColor(healthStatus) else null
            NavigationBarItem(
                selected = selected == i,
                onClick = { onSelect(i) },
                icon = { Text(text = icon, fontSize = 18.sp, color = iconColor ?: if (selected == i) V8.Gold else V8.Text3) },
                label = { Text(text = label, fontSize = 9.sp, maxLines = 1, fontWeight = if (selected == i) FontWeight.Black else FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = V8.Gold,
                    unselectedTextColor = V8.Text3,
                    indicatorColor = V8.Card2
                )
            )
        }
    }
}
