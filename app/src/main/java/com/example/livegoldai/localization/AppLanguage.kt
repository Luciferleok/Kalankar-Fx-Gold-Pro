package com.example.livegoldai.localization

import androidx.compose.runtime.compositionLocalOf

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val subLabel: String,
    val flag: String,
    val greeting: String
) {
    ENGLISH(
        code = "en",
        displayName = "English",
        nativeName = "English",
        subLabel = "Global Trading Standard",
        flag = "🇬🇧",
        greeting = "Welcome to KALANKAR FX GOLD PRO"
    ),
    HINDI(
        code = "hi",
        displayName = "Hindi",
        nativeName = "हिंदी",
        subLabel = "भारतीय भाषा • सरल विश्लेषण",
        flag = "🇮🇳",
        greeting = "KALANKAR FX GOLD PRO में आपका स्वागत है"
    ),
    MARATHI(
        code = "mr",
        displayName = "Marathi",
        nativeName = "मराठी",
        subLabel = "महाराष्ट्र राजभाषा • अचूक मार्गदर्शन",
        flag = "🚩",
        greeting = "KALANKAR FX GOLD PRO मध्ये आपले स्वागत आहे"
    );

    companion object {
        fun fromCode(code: String?): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: HINDI
        }
    }
}

val LocalAppLanguage = compositionLocalOf { AppLanguage.HINDI }
