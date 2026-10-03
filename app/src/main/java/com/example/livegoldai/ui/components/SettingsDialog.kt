package com.example.livegoldai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.localization.LocalizationStrings
import com.example.livegoldai.theme.*

@Composable
fun SettingsDialog(
    currentApiKey: String,
    currentLanguage: AppLanguage,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSaveKey: (String) -> Unit,
    onOpenThemeSelector: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var keyText by remember { mutableStateOf(currentApiKey) }
    var selectedLanguage by remember(currentLanguage) { mutableStateOf(currentLanguage) }
    var showLanguageSuccessBanner by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .testTag("settings_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(listOf(GoldPrimary.copy(alpha = 0.8f), ObsidianBorderHighlight, ObsidianBorder))
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = GoldPrimary.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = GoldLight,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = LocalizationStrings.settingsTitle(selectedLanguage),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            Text(
                                text = when (selectedLanguage) {
                                    AppLanguage.ENGLISH -> "Language Selection & Pro Controls"
                                    AppLanguage.HINDI -> "भाषा चयन एवं प्रो कंट्रोल्स"
                                    AppLanguage.MARATHI -> "भाषा निवड आणि प्रो नियंत्रणे"
                                },
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Success Banner when language is switched
                    AnimatedVisibility(
                        visible = showLanguageSuccessBanner,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SignalBuyContainer,
                            border = BorderStroke(1.dp, SignalBuy),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SignalBuy,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = LocalizationStrings.languageChangedToast(selectedLanguage),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = SignalBuyText
                                )
                            }
                        }
                    }

                    // 1. PRIMARY LANGUAGE SELECTOR CARD (ENGLISH / HINDI / MARATHI)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ObsidianSurfaceCard,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(
                                listOf(GoldPrimary.copy(alpha = 0.8f), GoldDark, ObsidianBorderHighlight)
                            )
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("language_selector_container")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = LocalizationStrings.selectLanguage(selectedLanguage),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Black,
                                        color = GoldLight
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = GoldPrimary,
                                ) {
                                    Text(
                                        text = "${selectedLanguage.flag} ${selectedLanguage.displayName.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Black,
                                        color = ObsidianBackground,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = LocalizationStrings.selectLanguageDesc(selectedLanguage),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextMuted
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // 3 Language Options: English, Hindi, Marathi
                            AppLanguage.entries.forEach { lang ->
                                val isSelected = selectedLanguage == lang
                                val activeBorder = if (isSelected) GoldPrimary else ObsidianBorder
                                val activeBg = if (isSelected) ObsidianSurfaceElevated else ObsidianBackground.copy(alpha = 0.6f)

                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = activeBg,
                                    border = BorderStroke(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) GoldPrimary else ObsidianBorder
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            selectedLanguage = lang
                                            onSelectLanguage(lang)
                                            showLanguageSuccessBanner = true
                                        }
                                        .testTag("lang_option_${lang.code}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = lang.flag,
                                                fontSize = 24.sp
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = lang.nativeName,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Black,
                                                        color = if (isSelected) GoldLight else TextPrimary,
                                                        fontSize = 16.sp
                                                    )
                                                    if (lang != AppLanguage.ENGLISH) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "(${lang.displayName})",
                                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                            color = TextMuted
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = lang.subLabel,
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = if (isSelected) TextSecondary else TextMuted
                                                )
                                            }
                                        }

                                        if (isSelected) {
                                            Surface(
                                                shape = CircleShape,
                                                color = GoldPrimary,
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = ObsidianBackground,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(22.dp)
                                                    .clip(CircleShape)
                                                    .background(ObsidianSurface)
                                                    .padding(1.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // LIVE TRANSLATION PREVIEW BOX
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = ObsidianBackground,
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = LocalizationStrings.liveLanguagePreviewTitle(selectedLanguage),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            fontWeight = FontWeight.Black,
                                            color = GoldLight
                                        )
                                        Text(
                                            text = "${selectedLanguage.displayName} • Active",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            color = SignalBuy
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Sample Trade Signal in this Language
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.TrendingUp,
                                                contentDescription = null,
                                                tint = SignalBuy,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = when (selectedLanguage) {
                                                    AppLanguage.ENGLISH -> "Sample Signal: BUY EXPECTED"
                                                    AppLanguage.HINDI -> "सैंपल सिग्नल: BUY HO SAKTA HAI"
                                                    AppLanguage.MARATHI -> "नमुना सिग्नल: BUY HO SAKTO"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = SignalBuy
                                            )
                                        }
                                        Text(
                                            text = "Win rate: see Learning Center",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = GoldLight
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Sample Decision advice in this Language
                                    Text(
                                        text = when (selectedLanguage) {
                                            AppLanguage.ENGLISH -> "Advice: Strong bullish accumulation • Target upper resistance"
                                            AppLanguage.HINDI -> "सलाह: मजबूत खरीदारी दबाव • ऊपरी रेजिस्टेंस का लक्ष्य रखें"
                                            AppLanguage.MARATHI -> "सल्ला: मजबूत खरेदीचा जोर • वरील रेझिस्टन्सचे लक्ष्य ठेवा"
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // 2. THEME & VIEW MODE CUSTOMIZATION
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onOpenThemeSelector()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("change_theme_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.8f)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = ObsidianSurfaceCard)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = GoldLight,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = LocalizationStrings.customizeTheme(selectedLanguage),
                            color = GoldLight,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    // 3.5 VIP PRO LIFETIME PASS & PAYGLOCAL PAYMENT LINK
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurfaceCard,
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(listOf(GoldPrimary, GoldDark, ObsidianBorderHighlight))
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.payglocal.com/gl/v1/payments/pl?x-gl-link-id=96be0127-4217-48ef-8dbc-9444b435d028"))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                            .testTag("payglocal_vip_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = GoldPrimary,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Diamond,
                                        contentDescription = null,
                                        tint = ObsidianBackground,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "KALANKAR FX VIP LIFETIME",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Black,
                                        color = GoldLight
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = SignalBuy
                                    ) {
                                        Text(
                                            text = "VIP",
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                            fontWeight = FontWeight.Black,
                                            color = OnAccent
                                        )
                                    }
                                }
                                Text(
                                    text = when (selectedLanguage) {
                                        AppLanguage.ENGLISH -> "PayGlocal Instant Pass • Complete Payment / Private VIP Desk"
                                        AppLanguage.HINDI -> "PayGlocal पेमेंट पास • प्राइवेट VIP डेस्क और डायरेक्ट एक्सेस"
                                        AppLanguage.MARATHI -> "PayGlocal पेमेंट पास • प्रायव्हेट VIP डेस्क आणि थेट ॲक्सेस"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    // 4. API KEY CONFIGURATION
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurfaceCard,
                        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(ObsidianBorderHighlight, ObsidianBorder))),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = LocalizationStrings.apiKeyLabel(selectedLanguage),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = GoldLight
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = keyText,
                                onValueChange = { keyText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("api_key_input"),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = ObsidianBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = GoldPrimary
                                ),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = LocalizationStrings.apiKeyDesc(selectedLanguage),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                color = TextMuted
                            )
                        }
                    }

                    // 5. BRANDING BADGE
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianBackground,
                        border = BorderStroke(0.5.dp, ObsidianBorderHighlight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = LocalizationStrings.appTitle(selectedLanguage),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = GoldLight
                            )
                            Text(
                                text = LocalizationStrings.appSubtitle(selectedLanguage),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = LocalizationStrings.cancel(selectedLanguage),
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSelectLanguage(selectedLanguage)
                            onSaveKey(keyText)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_settings_button")
                    ) {
                        Text(
                            text = LocalizationStrings.saveAndRefresh(selectedLanguage),
                            fontWeight = FontWeight.Black,
                            color = ObsidianBackground
                        )
                    }
                }
            }
        }
    }
}
