package com.example.livegoldai.ui.v8

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
    NavigationBar(containerColor = V8.Bg, contentColor = V8.Text2) {
        items.forEachIndexed { i, (icon, label) ->
            val iconColor = if (i == 3 && healthStatus != null && healthStatus != "HEALTHY") statusColor(healthStatus) else null
            NavigationBarItem(
                selected = selected == i,
                onClick = { onSelect(i) },
                icon = {
                    // selected tab: a short champagne line above the icon, no filled pill
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (selected == i) V8.Gold else Color.Transparent)
                                .padding(vertical = 1.dp)
                        ) {}
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(text = icon, fontSize = 17.sp, color = iconColor ?: if (selected == i) V8.Gold else V8.Text3)
                    }
                },
                label = { Text(text = label.uppercase(), fontSize = 8.sp, maxLines = 1, letterSpacing = 0.8.sp, fontWeight = if (selected == i) FontWeight.SemiBold else FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = V8.Gold,
                    unselectedTextColor = V8.Text3,
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}
