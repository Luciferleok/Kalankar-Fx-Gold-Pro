package com.example.livegoldai.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.livegoldai.R
import com.example.livegoldai.theme.*

data class LogoOption(
    val id: String,
    val title: String,
    val tag: String,
    val description: String,
    val drawableRes: Int
)

@Composable
fun LogoSelectorDialog(
    currentSelectedLogo: Int,
    onSelectLogo: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val logoOptions = remember {
        listOf(
            LogoOption(
                id = "kalankar_official",
                title = "Kalankar FX Gold Pro",
                tag = "OFFICIAL LOGO",
                description = "The official K + bull + trading ring emblem.",
                drawableRes = R.drawable.kalankar_logo_mark
            ),
            LogoOption(
                id = "kalankar_gold_crest",
                title = "Kalankar 24K Sovereign Bullion Crest",
                tag = "👑 ULTRA-HD 24K GOLD CREST • SIGNATURE",
                description = "Masterpiece 3D 24K gold sovereign crest with imperial royal eagle, sovereign crown, and micro-engraved candlestick chart on dark obsidian.",
                drawableRes = R.drawable.ic_kalankar_gold_crest
            ),
            LogoOption(
                id = "vip_gold_crest",
                title = "Dubai Imperial Bullion Crest",
                tag = "✨ 24K DUBAI BULLION CREST • CLASSIC",
                description = "Masterpiece 3D 24K pure gold imperial eagle with royal sovereign crown on obsidian velvet.",
                drawableRes = R.drawable.ic_vip_gold_crest
            ),
            LogoOption(
                id = "imperial_k",
                title = "Imperial Monogram 'K'",
                tag = "👑 24K ROYAL GOLD • PRESTIGE",
                description = "Bespoke 3D 'K' monogram crowned with an imperial diadem & rising candlestick lines on obsidian.",
                drawableRes = R.drawable.ic_luxury_gold_logo
            ),
            LogoOption(
                id = "apex_falcon",
                title = "Apex Golden Falcon",
                tag = "🦅 MODERN SWISS FINTECH",
                description = "Geometric origami-styled golden falcon ascending through institutional bullion chevrons.",
                drawableRes = R.drawable.ic_gold_eagle_logo
            ),
            LogoOption(
                id = "heraldic_crest",
                title = "Sovereign Heraldic Shield",
                tag = "🛡️ CLASSIC VIP HERITAGE",
                description = "High-relief 3D baroque gold shield with sovereign lions and laurel bullion wreath.",
                drawableRes = R.drawable.img_royal_gold_emblem
            )
        )
    }

    var tempSelected by remember { mutableIntStateOf(currentSelectedLogo) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .testTag("logo_selector_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = ObsidianSurfaceCard,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(
                    listOf(GoldPrimary, ObsidianBorderHighlight, GoldDark)
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.2f))
                                .border(1.dp, GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Diamond,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "VIP BRAND EMBLEMS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = GoldLight,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Choose your preferred luxury emblem",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Options List
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    logoOptions.forEach { option ->
                        val isChosen = tempSelected == option.drawableRes

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { tempSelected = option.drawableRes }
                                .testTag("logo_option_${option.id}"),
                            color = if (isChosen) ObsidianSurfaceElevated else ObsidianBackground,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = if (isChosen) {
                                    Brush.horizontalGradient(listOf(GoldPrimary, GoldLight))
                                } else {
                                    Brush.horizontalGradient(listOf(ObsidianBorder, ObsidianBorder))
                                }
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Logo Preview
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            width = if (isChosen) 2.dp else 1.dp,
                                            color = if (isChosen) GoldPrimary else ObsidianBorder,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                ) {
                                    Image(
                                        painter = painterResource(id = option.drawableRes),
                                        contentDescription = option.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isChosen) GoldLight else TextGold.copy(alpha = 0.8f)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = option.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isChosen) TextPrimary else TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        lineHeight = 14.sp
                                    )
                                }

                                if (isChosen) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Apply Button
                Button(
                    onClick = {
                        onSelectLogo(tempSelected)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("apply_logo_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = OnAccent
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "APPLY AS PRIMARY VIP EMBLEM",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
