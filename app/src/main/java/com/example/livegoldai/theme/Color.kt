package com.example.livegoldai.theme

import androidx.compose.ui.graphics.Color

/*
 * These names are used on almost every screen. Until V13 they were fixed colours, which is why
 * every theme looked the same. They now read the active theme, so no screen has to change.
 */
private val p: AppThemeColors get() = ActivePalette.current

val GoldPrimary: Color get() = p.primaryGold
val GoldLight: Color get() = p.lightGold
val GoldDark: Color get() = p.darkGold
val GoldContainer: Color get() = p.signalWaitBg
val GoldOnContainer: Color get() = p.lightGold

val ObsidianBackground: Color get() = p.background
val ObsidianSurface: Color get() = p.surface
val ObsidianSurfaceElevated: Color get() = p.surfaceElevated
val ObsidianSurfaceCard: Color get() = p.surfaceCard
val ObsidianBorder: Color get() = p.border
val ObsidianBorderHighlight: Color get() = p.borderHighlight

val SignalBuy: Color get() = p.signalBuy
val SignalBuyContainer: Color get() = p.signalBuyBg
val SignalBuyBg: Color get() = p.signalBuyBg
val SignalBuyText: Color get() = p.signalBuy
val NeonGreen: Color get() = p.signalBuy

val SignalSell: Color get() = p.signalSell
val SignalSellContainer: Color get() = p.signalSellBg
val SignalSellBg: Color get() = p.signalSellBg
val SignalSellText: Color get() = p.signalSell
val NeonRed: Color get() = p.signalSell

val SignalWait: Color get() = p.signalWait
val SignalWaitContainer: Color get() = p.signalWaitBg
val SignalWaitBg: Color get() = p.signalWaitBg
val SignalWaitText: Color get() = p.signalWait
val AmberWarning: Color get() = p.signalWait

val TextPrimary: Color get() = p.textPrimary
val TextSecondary: Color get() = p.textSecondary
val TextMuted: Color get() = p.textMuted
val TextGold: Color get() = p.primaryGold

/** Text on top of the accent colour (was a fixed black). */
val OnAccent: Color get() = p.onAccent
val InfoBlue: Color get() = p.info
val LearnViolet: Color get() = p.learn
