package com.example.livegoldai.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * V13 theme engine.
 * The enum names are kept from older versions because they are what is saved on the phone;
 * only the look and the titles changed.
 */
enum class ThemeMode(
    val title: String,
    val subtitle: String,
    val badge: String,
    val icon: String,
    val shortLabel: String
) {
    ROYAL_OBSIDIAN(
        title = "Obsidian Gold",
        subtitle = "Near-black obsidian, graphite cards, champagne gold",
        badge = "DEFAULT",
        icon = "◆",
        shortLabel = "OBSIDIAN"
    ),
    CYBER_NEON(
        title = "Platinum Ice",
        subtitle = "Cold charcoal, metallic graphite, platinum and ice cyan",
        badge = "QUANT LAB",
        icon = "◇",
        shortLabel = "PLATINUM"
    ),
    SWISS_BANK(
        title = "Midnight Sapphire",
        subtitle = "Deep navy-black with sapphire and restrained gold",
        badge = "PRIVATE BANK",
        icon = "◈",
        shortLabel = "MIDNIGHT"
    ),
    EMERALD_ALPHA(
        title = "Emerald Black",
        subtitle = "Green-black depth with emerald and soft gold",
        badge = "PRIVATE CAPITAL",
        icon = "❖",
        shortLabel = "EMERALD"
    ),
    MONACO_ROSE(
        title = "Royal Amethyst",
        subtitle = "Black-plum graphite with royal violet and platinum",
        badge = "AI EXECUTIVE",
        icon = "✦",
        shortLabel = "ROYAL"
    ),
    NEWS_ALERT(
        title = "Carbon Red",
        subtitle = "Carbon black with deep crimson and gunmetal",
        badge = "PERFORMANCE",
        icon = "▲",
        shortLabel = "CARBON"
    ),
    DUBAI_ROYALE(
        title = "Ivory Executive",
        subtitle = "Light theme: warm ivory, pearl cards, dark champagne",
        badge = "LIGHT",
        icon = "○",
        shortLabel = "IVORY"
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
    val signalWaitBg: Color,
    val info: Color,
    val learn: Color,
    /** Text / icon colour that sits on top of the accent colour. */
    val onAccent: Color,
    val isLight: Boolean
) {
    val goldGradient: androidx.compose.ui.graphics.Brush
        get() = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(lightGold, primaryGold))

    val cardGradient: androidx.compose.ui.graphics.Brush
        get() = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(surfaceElevated, surfaceCard))

    val borderGlow: androidx.compose.ui.graphics.Brush
        get() = androidx.compose.ui.graphics.Brush.linearGradient(listOf(borderHighlight, border))
}

// Obsidian Gold
val RoyalObsidianPalette = AppThemeColors(
    themeMode = ThemeMode.ROYAL_OBSIDIAN,
    background = Color(0xFF070808),
    surface = Color(0xFF15181C),
    surfaceElevated = Color(0xFF1A1C20),
    surfaceCard = Color(0xFF101215),
    border = Color(0xFF24272C),
    borderHighlight = Color(0xFFD4B56A),
    primaryGold = Color(0xFFD4B56A),
    lightGold = Color(0xFFE6CF94),
    darkGold = Color(0xFFA8873F),
    textPrimary = Color(0xFFF2F0EA),
    textSecondary = Color(0xFFB9B4A8),
    textMuted = Color(0xFF7C786F),
    signalBuy = Color(0xFF2FBF8A),
    signalBuyBg = Color(0xFF0C2A20),
    signalSell = Color(0xFFD9475A),
    signalSellBg = Color(0xFF2E1116),
    signalWait = Color(0xFFE0A94A),
    signalWaitBg = Color(0xFF2B2110),
    info = Color(0xFF6FB6D9),
    learn = Color(0xFFA99BD6),
    onAccent = Color(0xFF0A0B0D),
    isLight = false
)

// Platinum Ice
val CyberNeonPalette = AppThemeColors(
    themeMode = ThemeMode.CYBER_NEON,
    background = Color(0xFF0B0E12),
    surface = Color(0xFF171C23),
    surfaceElevated = Color(0xFF1F262F),
    surfaceCard = Color(0xFF12161C),
    border = Color(0xFF2A323C),
    borderHighlight = Color(0xFF8FD3E8),
    primaryGold = Color(0xFFC7D3DE),
    lightGold = Color(0xFFE4ECF3),
    darkGold = Color(0xFF8FA3B5),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFFAAB6C3),
    textMuted = Color(0xFF6E7B89),
    signalBuy = Color(0xFF3CC6A0),
    signalBuyBg = Color(0xFF0E2B24),
    signalSell = Color(0xFFE0566B),
    signalSellBg = Color(0xFF30141A),
    signalWait = Color(0xFFD9B36A),
    signalWaitBg = Color(0xFF2C2413),
    info = Color(0xFF8FD3E8),
    learn = Color(0xFFA9A6E0),
    onAccent = Color(0xFF0B0E12),
    isLight = false
)

// Midnight Sapphire
val SwissBankPalette = AppThemeColors(
    themeMode = ThemeMode.SWISS_BANK,
    background = Color(0xFF050A16),
    surface = Color(0xFF0F1B36),
    surfaceElevated = Color(0xFF152444),
    surfaceCard = Color(0xFF0A1429),
    border = Color(0xFF1E2E52),
    borderHighlight = Color(0xFFD4B56A),
    primaryGold = Color(0xFF5B8DEF),
    lightGold = Color(0xFF9DBBFA),
    darkGold = Color(0xFF3A66C4),
    textPrimary = Color(0xFFF3F6FC),
    textSecondary = Color(0xFFA7B4CE),
    textMuted = Color(0xFF65749A),
    signalBuy = Color(0xFF34C38F),
    signalBuyBg = Color(0xFF0B2A24),
    signalSell = Color(0xFFE05A6D),
    signalSellBg = Color(0xFF2F1420),
    signalWait = Color(0xFFD4B56A),
    signalWaitBg = Color(0xFF2A2414),
    info = Color(0xFF7FC4E8),
    learn = Color(0xFFA9A6E0),
    onAccent = Color(0xFF050A16),
    isLight = false
)

// Emerald Black
val EmeraldAlphaPalette = AppThemeColors(
    themeMode = ThemeMode.EMERALD_ALPHA,
    background = Color(0xFF050B08),
    surface = Color(0xFF0F1C16),
    surfaceElevated = Color(0xFF14261D),
    surfaceCard = Color(0xFF0A1510),
    border = Color(0xFF1C3327),
    borderHighlight = Color(0xFFD4B56A),
    primaryGold = Color(0xFF2FBF8A),
    lightGold = Color(0xFF7FDDB8),
    darkGold = Color(0xFF1E8A62),
    textPrimary = Color(0xFFF0F7F3),
    textSecondary = Color(0xFFA6BBB0),
    textMuted = Color(0xFF667A70),
    signalBuy = Color(0xFF2FBF8A),
    signalBuyBg = Color(0xFF0C2A20),
    signalSell = Color(0xFFE0566B),
    signalSellBg = Color(0xFF2E1318),
    signalWait = Color(0xFFD4B56A),
    signalWaitBg = Color(0xFF2A2412),
    info = Color(0xFF6FB6D9),
    learn = Color(0xFFA99BD6),
    onAccent = Color(0xFF050B08),
    isLight = false
)

// Royal Amethyst
val MonacoRosePalette = AppThemeColors(
    themeMode = ThemeMode.MONACO_ROSE,
    background = Color(0xFF0A070E),
    surface = Color(0xFF19121F),
    surfaceElevated = Color(0xFF21182C),
    surfaceCard = Color(0xFF120D18),
    border = Color(0xFF2E2340),
    borderHighlight = Color(0xFFC7D3DE),
    primaryGold = Color(0xFF9B7BE0),
    lightGold = Color(0xFFC4B0F2),
    darkGold = Color(0xFF7252B8),
    textPrimary = Color(0xFFF5F2FA),
    textSecondary = Color(0xFFB5AACB),
    textMuted = Color(0xFF756A8C),
    signalBuy = Color(0xFF3CC6A0),
    signalBuyBg = Color(0xFF0E2A24),
    signalSell = Color(0xFFE0566B),
    signalSellBg = Color(0xFF30131C),
    signalWait = Color(0xFFD9B36A),
    signalWaitBg = Color(0xFF2B2314),
    info = Color(0xFF8FD3E8),
    learn = Color(0xFFC4B0F2),
    onAccent = Color(0xFF0A070E),
    isLight = false
)

// Carbon Red
val NewsAlertPalette = AppThemeColors(
    themeMode = ThemeMode.NEWS_ALERT,
    background = Color(0xFF0A0909),
    surface = Color(0xFF181415),
    surfaceElevated = Color(0xFF201B1C),
    surfaceCard = Color(0xFF121011),
    border = Color(0xFF302629),
    borderHighlight = Color(0xFF8A9099),
    primaryGold = Color(0xFFC23A4B),
    lightGold = Color(0xFFE27A87),
    darkGold = Color(0xFF8E2231),
    textPrimary = Color(0xFFF4F1F1),
    textSecondary = Color(0xFFB8AEB0),
    textMuted = Color(0xFF7A7072),
    signalBuy = Color(0xFF3CC6A0),
    signalBuyBg = Color(0xFF0E2A23),
    signalSell = Color(0xFFF0705E),
    signalSellBg = Color(0xFF331612),
    signalWait = Color(0xFFE0A94A),
    signalWaitBg = Color(0xFF2B2110),
    info = Color(0xFF8A9099),
    learn = Color(0xFFA99BD6),
    onAccent = Color(0xFFFFFFFF),
    isLight = false
)

// Ivory Executive
val DubaiRoyalePalette = AppThemeColors(
    themeMode = ThemeMode.DUBAI_ROYALE,
    background = Color(0xFFF6F1E7),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEFE8DA),
    surfaceCard = Color(0xFFFBF8F1),
    border = Color(0xFFE2D9C6),
    borderHighlight = Color(0xFF9A7B2F),
    primaryGold = Color(0xFF9A7B2F),
    lightGold = Color(0xFF7D6325),
    darkGold = Color(0xFF5E4A1A),
    textPrimary = Color(0xFF23211D),
    textSecondary = Color(0xFF5C574D),
    textMuted = Color(0xFF8C8678),
    signalBuy = Color(0xFF1E8A62),
    signalBuyBg = Color(0xFFE1F1EA),
    signalSell = Color(0xFFB8324A),
    signalSellBg = Color(0xFFF7E3E6),
    signalWait = Color(0xFFA8741A),
    signalWaitBg = Color(0xFFF5EAD3),
    info = Color(0xFF2D7FA6),
    learn = Color(0xFF6B55B0),
    onAccent = Color(0xFFFFFFFF),
    isLight = true
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

/**
 * The palette every screen reads. The old colour names (GoldPrimary, TextPrimary, SignalBuy ...)
 * in Color.kt read from here, so screens that were written with fixed colours now follow the theme.
 * Backed by Compose state: changing it redraws the UI.
 */
object ActivePalette {
    var current: AppThemeColors by mutableStateOf(RoyalObsidianPalette)
}

/** One stable AMOLED copy per theme, so the palette object does not change on every recomposition. */
private object AmoledCache {
    private val map = HashMap<ThemeMode, AppThemeColors>()
    fun of(base: AppThemeColors): AppThemeColors = map.getOrPut(base.themeMode) { base.copy(background = Color(0xFF000000)) }
}

val LocalAppColors = staticCompositionLocalOf { RoyalObsidianPalette }

@Composable
fun LiveGoldAITheme(
    themeMode: ThemeMode = ThemeMode.ROYAL_OBSIDIAN,
    isNewsModeActive: Boolean = false,
    amoled: Boolean = false,
    content: @Composable () -> Unit
) {
    // High-impact news no longer repaints the whole app red: the news banner and cards carry the warning.
    @Suppress("UNUSED_VARIABLE") val news = isNewsModeActive
    val base = getPaletteForMode(themeMode)
    // AMOLED: only the background goes to pure black; cards keep their own (slightly raised) surface
    val palette = if (amoled && !base.isLight) AmoledCache.of(base) else base
    if (ActivePalette.current !== palette) ActivePalette.current = palette

    val colorScheme = if (palette.isLight) lightColorScheme(
        primary = palette.primaryGold,
        onPrimary = palette.onAccent,
        primaryContainer = palette.surfaceElevated,
        onPrimaryContainer = palette.textPrimary,
        secondary = palette.darkGold,
        onSecondary = palette.onAccent,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceCard,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.border,
        outlineVariant = palette.border
    ) else darkColorScheme(
        primary = palette.primaryGold,
        onPrimary = palette.onAccent,
        primaryContainer = palette.surfaceElevated,
        onPrimaryContainer = palette.lightGold,
        secondary = palette.lightGold,
        onSecondary = palette.onAccent,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceCard,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.border,
        outlineVariant = palette.borderHighlight
    )

    // status / navigation bar icons must be dark on the light theme and light on the dark ones
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val c = WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = palette.isLight
                c.isAppearanceLightNavigationBars = palette.isLight
            }
        }
    }

    CompositionLocalProvider(LocalAppColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
