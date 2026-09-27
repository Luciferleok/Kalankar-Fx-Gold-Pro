package com.example.livegoldai.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeMode(
    val title: String,
    val subtitle: String,
    val badge: String,
    val icon: String
) {
    DUBAI_ROYALE(
        title = "Dubai Royale 24K Bullion",
        subtitle = "Prestige Dubai Gold Souk bullion metallic finish with diamond velvet",
        badge = "✨ 24K PURE GOLD",
        icon = "✨"
    ),
    ROYAL_OBSIDIAN(
        title = "Royal Obsidian Gold",
        subtitle = "Pitch black OLED contrast with 24K bullion gold accents",
        badge = "👑 SIGNATURE LUXURY",
        icon = "👑"
    ),
    MONACO_ROSE(
        title = "Monaco Rose Gold Sovereign",
        subtitle = "Ultra-luxury French Riviera rose gold bullion with satin caviar black",
        badge = "🌹 ROSE GOLD BULLION",
        icon = "🌹"
    ),
    CYBER_NEON(
        title = "Cyber Tokyo Matrix",
        subtitle = "Ultra high-contrast neon cyan & emerald trading terminal",
        badge = "⚡ HIGH FREQUENCY",
        icon = "⚡"
    ),
    SWISS_BANK(
        title = "Swiss Bullion Navy",
        subtitle = "Deep institutional sapphire midnight & champagne accents",
        badge = "🏦 INSTITUTIONAL",
        icon = "🏛️"
    ),
    EMERALD_ALPHA(
        title = "Emerald Alpha Hedge",
        subtitle = "Prestige deep jade forest with glowing mint buy triggers",
        badge = "💎 WEALTH HEDGE",
        icon = "💎"
    ),
    NEWS_ALERT(
        title = "News Volatility Alert (High-Impact)",
        subtitle = "Emergency crimson & electric amber theme for CPI, NFP & FOMC volatility",
        badge = "🚨 LIVE NEWS IMPACT",
        icon = "🚨"
    )
}

data class AppThemeColors(
    val themeMode: ThemeMode,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceCard: Color,
    val border: Color,
    val borderHighlight: Color,
    val primaryGold: Color,
    val lightGold: Color,
    val darkGold: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val signalBuy: Color,
    val signalBuyBg: Color,
    val signalSell: Color,
    val signalSellBg: Color,
    val signalWait: Color,
    val signalWaitBg: Color
) {
    val goldGradient: androidx.compose.ui.graphics.Brush
        get() = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(lightGold, primaryGold))

    val cardGradient: androidx.compose.ui.graphics.Brush
        get() = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(surfaceElevated, surfaceCard))

    val borderGlow: androidx.compose.ui.graphics.Brush
        get() = androidx.compose.ui.graphics.Brush.linearGradient(listOf(borderHighlight, border))
}

val RoyalObsidianPalette = AppThemeColors(
    themeMode = ThemeMode.ROYAL_OBSIDIAN,
    background = Color(0xFF07090E),
    surface = Color(0xFF0F141F),
    surfaceElevated = Color(0xFF171E2D),
    surfaceCard = Color(0xFF121824),
    border = Color(0xFF242E40),
    borderHighlight = Color(0xFFFFC72C),
    primaryGold = Color(0xFFFFC72C),
    lightGold = Color(0xFFFFE082),
    darkGold = Color(0xFFD4AF37),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    signalBuy = Color(0xFF00E676),
    signalBuyBg = Color(0xFF072B19),
    signalSell = Color(0xFFFF334B),
    signalSellBg = Color(0xFF330B12),
    signalWait = Color(0xFFFFB800),
    signalWaitBg = Color(0xFF332408)
)

val DubaiRoyalePalette = AppThemeColors(
    themeMode = ThemeMode.DUBAI_ROYALE,
    background = Color(0xFF050608),
    surface = Color(0xFF0D1016),
    surfaceElevated = Color(0xFF171B24),
    surfaceCard = Color(0xFF12151D),
    border = Color(0xFF382F1D),
    borderHighlight = Color(0xFFFFD700),
    primaryGold = Color(0xFFFFD700), // Pure 24K Gold
    lightGold = Color(0xFFFFF0A6),
    darkGold = Color(0xFFE5A700),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFE0D5BE),
    textMuted = Color(0xFF998E78),
    signalBuy = Color(0xFF00FF88),
    signalBuyBg = Color(0xFF052B19),
    signalSell = Color(0xFFFF264D),
    signalSellBg = Color(0xFF380710),
    signalWait = Color(0xFFFFD700),
    signalWaitBg = Color(0xFF302408)
)

val MonacoRosePalette = AppThemeColors(
    themeMode = ThemeMode.MONACO_ROSE,
    background = Color(0xFF080507),
    surface = Color(0xFF130C10),
    surfaceElevated = Color(0xFF1C1318),
    surfaceCard = Color(0xFF170F14),
    border = Color(0xFF361E26),
    borderHighlight = Color(0xFFFF94A6),
    primaryGold = Color(0xFFFF8599), // Monaco Rose Gold
    lightGold = Color(0xFFFFB8C6),
    darkGold = Color(0xFFC7556A),
    textPrimary = Color(0xFFFFF6F8),
    textSecondary = Color(0xFFDFB0BC),
    textMuted = Color(0xFF966F7B),
    signalBuy = Color(0xFF00FFAB),
    signalBuyBg = Color(0xFF052B1E),
    signalSell = Color(0xFFFF2E67),
    signalSellBg = Color(0xFF3B0917),
    signalWait = Color(0xFFFFB300),
    signalWaitBg = Color(0xFF332308)
)

val CyberNeonPalette = AppThemeColors(
    themeMode = ThemeMode.CYBER_NEON,
    background = Color(0xFF030710),
    surface = Color(0xFF081324),
    surfaceElevated = Color(0xFF0E1F38),
    surfaceCard = Color(0xFF0A182C),
    border = Color(0xFF16365C),
    borderHighlight = Color(0xFF00F0FF),
    primaryGold = Color(0xFF00F0FF), // Electric Cyan
    lightGold = Color(0xFF80F7FF),
    darkGold = Color(0xFF009AB0),
    textPrimary = Color(0xFFF0FDF4),
    textSecondary = Color(0xFF93C5FD),
    textMuted = Color(0xFF477199),
    signalBuy = Color(0xFF00FF9D),
    signalBuyBg = Color(0xFF033320),
    signalSell = Color(0xFFFF1744),
    signalSellBg = Color(0xFF3B0713),
    signalWait = Color(0xFFFFD600),
    signalWaitBg = Color(0xFF332900)
)

val SwissBankPalette = AppThemeColors(
    themeMode = ThemeMode.SWISS_BANK,
    background = Color(0xFF060B14),
    surface = Color(0xFF0E182A),
    surfaceElevated = Color(0xFF15233C),
    surfaceCard = Color(0xFF111D32),
    border = Color(0xFF243657),
    borderHighlight = Color(0xFFE5C07B),
    primaryGold = Color(0xFFE5C07B), // Champagne Bullion
    lightGold = Color(0xFFF6E3B8),
    darkGold = Color(0xFFB58B35),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFFA5B4CB),
    textMuted = Color(0xFF677794),
    signalBuy = Color(0xFF22C55E),
    signalBuyBg = Color(0xFF0F311C),
    signalSell = Color(0xFFEF4444),
    signalSellBg = Color(0xFF3B1214),
    signalWait = Color(0xFFF59E0B),
    signalWaitBg = Color(0xFF382306)
)

val EmeraldAlphaPalette = AppThemeColors(
    themeMode = ThemeMode.EMERALD_ALPHA,
    background = Color(0xFF030E09),
    surface = Color(0xFF081B13),
    surfaceElevated = Color(0xFF0F261C),
    surfaceCard = Color(0xFF0B2117),
    border = Color(0xFF184230),
    borderHighlight = Color(0xFF10B981),
    primaryGold = Color(0xFF10B981), // Emerald Alpha
    lightGold = Color(0xFF6EE7B7),
    darkGold = Color(0xFF047857),
    textPrimary = Color(0xFFF0FDF4),
    textSecondary = Color(0xFF86EFAC),
    textMuted = Color(0xFF4D7C66),
    signalBuy = Color(0xFF10B981),
    signalBuyBg = Color(0xFF063321),
    signalSell = Color(0xFFF43F5E),
    signalSellBg = Color(0xFF3D0C17),
    signalWait = Color(0xFFEAB308),
    signalWaitBg = Color(0xFF332605)
)

val NewsAlertPalette = AppThemeColors(
    themeMode = ThemeMode.NEWS_ALERT,
    background = Color(0xFF0F0407), // Alarm Crimson Midnight
    surface = Color(0xFF1E080F),
    surfaceElevated = Color(0xFF2E0D17),
    surfaceCard = Color(0xFF240A12),
    border = Color(0xFF5C1525),
    borderHighlight = Color(0xFFFF264D), // Glowing Neon Red Alert
    primaryGold = Color(0xFFFF5500), // Electric Flame Orange
    lightGold = Color(0xFFFF9944),
    darkGold = Color(0xFFD63B00),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFFFB3BA),
    textMuted = Color(0xFFC4717F),
    signalBuy = Color(0xFF00FF88),
    signalBuyBg = Color(0xFF063319),
    signalSell = Color(0xFFFF1744),
    signalSellBg = Color(0xFF450613),
    signalWait = Color(0xFFFF9100),
    signalWaitBg = Color(0xFF3B1E05)
)

fun getPaletteForMode(mode: ThemeMode): AppThemeColors {
    return when (mode) {
        ThemeMode.DUBAI_ROYALE -> DubaiRoyalePalette
        ThemeMode.ROYAL_OBSIDIAN -> RoyalObsidianPalette
        ThemeMode.MONACO_ROSE -> MonacoRosePalette
        ThemeMode.CYBER_NEON -> CyberNeonPalette
        ThemeMode.SWISS_BANK -> SwissBankPalette
        ThemeMode.EMERALD_ALPHA -> EmeraldAlphaPalette
        ThemeMode.NEWS_ALERT -> NewsAlertPalette
    }
}

val LocalAppColors = staticCompositionLocalOf { RoyalObsidianPalette }

@Composable
fun LiveGoldAITheme(
    themeMode: ThemeMode = ThemeMode.ROYAL_OBSIDIAN,
    isNewsModeActive: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (isNewsModeActive) NewsAlertPalette else getPaletteForMode(themeMode)

    val colorScheme = darkColorScheme(
        primary = palette.primaryGold,
        onPrimary = palette.background,
        primaryContainer = palette.surfaceElevated,
        onPrimaryContainer = palette.lightGold,
        secondary = palette.lightGold,
        onSecondary = palette.background,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceCard,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.border,
        outlineVariant = palette.borderHighlight
    )

    CompositionLocalProvider(LocalAppColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

