package com.example.livegoldai.ui.v8

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * KALANKAR DESIGN TOKENS (V24 foundation)
 *
 * One place for type, spacing, shapes and surfaces. Colours always come from the active theme (V8 / ActivePalette),
 * so every token follows the selected theme. New and reworked screens use these names instead of loose numbers.
 */
internal object KType {
    private fun t(size: Int, weight: FontWeight, track: Double = 0.0, line: Int = 0) = TextStyle(
        fontFamily = FontFamily.SansSerif, fontWeight = weight, fontSize = size.sp,
        letterSpacing = track.sp, lineHeight = if (line > 0) line.sp else (size * 1.35).sp,
        fontFeatureSettings = "tnum"          // digits keep the same width: numbers never jump when they update
    )
    val BrandDisplay = t(20, FontWeight.Medium, 6.0)
    val BrandSecondary = t(10, FontWeight.Medium, 3.0)
    val HeroFinancial = t(40, FontWeight.Light, -0.5)
    val FinancialNumeric = t(15, FontWeight.Medium)
    val ScreenTitle = t(22, FontWeight.Medium, 0.5)
    val SectionLabel = t(9, FontWeight.SemiBold, 1.6)
    val BodyPrimary = t(12, FontWeight.Normal, 0.0, 17)
    val BodySecondary = t(11, FontWeight.Normal, 0.0, 15)
    val Caption = t(9, FontWeight.Normal)
    val MicroLabel = t(8, FontWeight.Medium, 1.0)
}

internal object KSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val gutter = 16.dp
}

internal object KShape {
    val small = 10.dp
    val medium = 14.dp
    val hero = 24.dp
}

/** Surface roles. Each one has a purpose; they map to the theme's own surface colours. */
internal object KSurface {
    val Base: Color get() = V8.Bg            // obsidian base
    val Plane: Color get() = V8.Card2        // graphite plane: quiet groups, banners
    val Raised: Color get() = V8.Card        // ceramic raised: cards that can be tapped
    val Hero: Color get() = V8.Hero          // signature hero: the one main card per screen
    val Edge: Color get() = V8.Line          // micro edge
}
