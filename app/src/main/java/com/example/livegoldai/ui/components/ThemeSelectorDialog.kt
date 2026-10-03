package com.example.livegoldai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.livegoldai.theme.*

@Composable
fun ThemeSelectorDialog(
    currentTheme: ThemeMode,
    isCompactEasyView: Boolean,
    onToggleEasyView: () -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    var tempTheme by remember { mutableStateOf(currentTheme) }
    val appColors = LocalAppColors.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .testTag("theme_selector_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = appColors.surfaceCard,
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(
                    listOf(appColors.lightGold, appColors.primaryGold, appColors.borderHighlight)
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.2f))
                                .border(1.dp, GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = GoldLight,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "TERMINAL THEME & VIEW",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = GoldLight,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Choose luxury styling & layout mode",
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

                Spacer(modifier = Modifier.height(16.dp))

                // Easy 1-Glance View Switch Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isCompactEasyView) GoldContainer else ObsidianSurfaceElevated,
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = if (isCompactEasyView) {
                            Brush.horizontalGradient(listOf(GoldPrimary, GoldLight))
                        } else {
                            Brush.horizontalGradient(listOf(ObsidianBorder, ObsidianBorder))
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isCompactEasyView) GoldPrimary else ObsidianBorder),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = if (isCompactEasyView) OnAccent else GoldLight,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Easy 1-Glance View",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCompactEasyView) GoldLight else TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isCompactEasyView) GoldPrimary else ObsidianBorder
                                    ) {
                                        Text(
                                            text = if (isCompactEasyView) "ACTIVE" else "OFF",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (isCompactEasyView) OnAccent else TextSecondary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Shows high-priority signal & direct trade targets with minimum clutter.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    lineHeight = 14.sp
                                )
                            }
                        }

                        Switch(
                            checked = isCompactEasyView,
                            onCheckedChange = { onToggleEasyView() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = OnAccent,
                                checkedTrackColor = GoldPrimary,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = ObsidianBorder
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "SELECT COLOR PALETTE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = TextGold,
                    letterSpacing = 0.6.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Themes List
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeMode.values().forEach { mode ->
                        val isChosen = tempTheme == mode
                        val previewPalette = getPaletteForMode(mode)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    tempTheme = mode
                                    onSelectTheme(mode)
                                }
                                .testTag("theme_option_${mode.name}"),
                            color = if (isChosen) ObsidianSurfaceElevated else ObsidianBackground,
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = if (isChosen) {
                                    Brush.horizontalGradient(listOf(previewPalette.primaryGold, previewPalette.lightGold))
                                } else {
                                    Brush.horizontalGradient(listOf(ObsidianBorder, ObsidianBorder))
                                }
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Color swatches preview box
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(previewPalette.background)
                                        .border(
                                            width = if (isChosen) 2.dp else 1.dp,
                                            color = if (isChosen) previewPalette.primaryGold else previewPalette.border,
                                            shape = RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = mode.icon,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(previewPalette.primaryGold)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(previewPalette.signalBuy)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(previewPalette.signalSell)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = previewPalette.lightGold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isChosen) TextPrimary else TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = mode.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        lineHeight = 13.sp
                                    )
                                }

                                if (isChosen) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = previewPalette.primaryGold,
                                        modifier = Modifier.size(20.dp)
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
                        onSelectTheme(tempTheme)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("apply_theme_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColors.primaryGold,
                        contentColor = OnAccent
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "APPLY THEME PREFERENCE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
