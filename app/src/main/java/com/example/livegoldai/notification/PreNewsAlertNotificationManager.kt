package com.example.livegoldai.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.livegoldai.MainActivity
import com.example.livegoldai.R
import com.example.livegoldai.localization.AppLanguage
import com.example.livegoldai.model.EconomicEvent
import com.example.livegoldai.model.Signal
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object PreNewsAlertNotificationManager {

    const val CHANNEL_ID = "gold_pre_news_channel"
    private const val PREFS_NAME = "pre_news_alert_prefs"
    private const val KEY_NOTIFIED_EVENTS = "notified_event_keys"
    private const val NOTIFICATION_ID = 24001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Gold Pre-News High-Impact Alerts"
            val descriptionText = "1-Hour Prior Notifications for High-Impact Gold News Releases with AI Buy/Sell Pre-Analysis"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300, 150, 450)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendPreNewsAlertNotification(
        context: Context,
        eventTitle: String,
        country: String = "USD",
        timeRemainingText: String = "1 Hour",
        indiaTimeText: String = "08:30 PM IST",
        aiBias: Signal = Signal.BUY,
        forecastInfo: String = "Expected vs Previous reading",
        targetRange: String = "$2,735 - $2,765",
        lang: AppLanguage = AppLanguage.HINDI,
        isTest: Boolean = false
    ): Boolean {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAV_TAB", 2) // Open News Tab
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val biasLabel = when (aiBias) {
            Signal.BUY -> when (lang) {
                AppLanguage.ENGLISH -> "BUY GOLD 🟢 (Bullish Surge Expected)"
                AppLanguage.HINDI -> "BUY (गोल्ड में तेजी की संभावना 🟢)"
                AppLanguage.MARATHI -> "BUY (सोन्यात तेजीची शक्यता 🟢)"
            }
            Signal.SELL -> when (lang) {
                AppLanguage.ENGLISH -> "SELL GOLD 🔴 (Bearish Drop Expected)"
                AppLanguage.HINDI -> "SELL (गोल्ड में मंदी की संभावना 🔴)"
                AppLanguage.MARATHI -> "SELL (सोन्यात मंदीची शक्यता 🔴)"
            }
            Signal.WAIT -> when (lang) {
                AppLanguage.ENGLISH -> "WAIT 🟡 (Straddle Pre-Coil)"
                AppLanguage.HINDI -> "WAIT (सावधानी - स्ट्रैडल तैयार रखें 🟡)"
                AppLanguage.MARATHI -> "WAIT (सावध राहा - स्ट्रॅडल तयार ठेवा 🟡)"
            }
        }

        val title = when (lang) {
            AppLanguage.ENGLISH -> if (isTest) "🚨 [TEST ALERT] 🇮🇳 $indiaTimeText • $eventTitle" else "🚨 1-HR NEWS ALERT: 🇮🇳 $indiaTimeText • $eventTitle"
            AppLanguage.HINDI -> if (isTest) "🚨 [टेस्ट अलर्ट] 🇮🇳 $indiaTimeText • $eventTitle" else "🚨 न्यूज़ अलर्ट: 🇮🇳 $indiaTimeText • $eventTitle"
            AppLanguage.MARATHI -> if (isTest) "🚨 [चाचणी अलर्ट] 🇮🇳 $indiaTimeText • $eventTitle" else "🚨 न्यूज अलर्ट: 🇮🇳 $indiaTimeText • $eventTitle"
        }

        val bodySummary = when (lang) {
            AppLanguage.ENGLISH -> "🇮🇳 IST: $indiaTimeText ($timeRemainingText) | AI: $biasLabel"
            AppLanguage.HINDI -> "🇮🇳 भारत समय: $indiaTimeText ($timeRemainingText) | AI: $biasLabel"
            AppLanguage.MARATHI -> "🇮🇳 भारत वेळ: $indiaTimeText ($timeRemainingText) | AI: $biasLabel"
        }

        val detailedBigText = when (lang) {
            AppLanguage.ENGLISH -> """
                ⏰ High-Impact Release approaching in $timeRemainingText!
                🇮🇳 Indian Standard Time (IST): $indiaTimeText
                🎯 AI Predictive Bias: $biasLabel
                📊 Forecast Analysis: $forecastInfo
                📈 Expected Gold Volatility Range: $targetRange
                ⚡ Action: Pre-news Straddle Levels & 90-sec freeze rule are armed in Kalankar FX Gold Pro!
            """.trimIndent()
            AppLanguage.HINDI -> """
                ⏰ लगभग $timeRemainingText में बड़ी मार्केट मूविंग न्यूज़ आने वाली है!
                🇮🇳 भारतीय समय (IST): $indiaTimeText (आज रात)
                🎯 AI का पूर्व-अनुमान (Prediction): $biasLabel
                📊 डेटा एनालिसिस: $forecastInfo
                📈 संभावित गोल्ड वोलैटिलिटी रेंज: $targetRange
                ⚡ एक्शन: न्यूज़ स्ट्रैडल लेवल्स और 90-सेकंड नो-ऑर्डर रूल ऐप में लोड हैं!
            """.trimIndent()
            AppLanguage.MARATHI -> """
                ⏰ अंदाजे $timeRemainingText मध्ये महत्त्वाची मार्केट हलवणारी बातमी येणार!
                🇮🇳 भारतीय वेळ (IST): $indiaTimeText
                🎯 AI पूर्व-अंदाज (Prediction): $biasLabel
                📊 डेटा विश्लेषण: $forecastInfo
                📈 संभाव्य गोल्ड व्होलॅटिलिटी रेंज: $targetRange
                ⚡ ॲक्शन: न्यूज स्ट्रॅडल लेव्हल्स व 90-सेकंद नो-ऑर्डर नियम ॲपमध्ये तयार आहेत!
            """.trimIndent()
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(bodySummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailedBigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val manager = NotificationManagerCompat.from(context)
            manager.notify(if (isTest) NOTIFICATION_ID + 1 else NOTIFICATION_ID, builder.build())
            return true
        } catch (_: SecurityException) {
            return false
        }
    }

    /**
     * Checks upcoming economic events to see if any high-impact event is approaching
     * (within 180 minutes). If found and not yet alerted, automatically fires the notification.
     */
    fun checkAndTriggerUpcomingAlert(
        context: Context,
        events: List<EconomicEvent>,
        currentPrice: Double,
        lang: AppLanguage
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val notifiedSet = prefs.getStringSet(KEY_NOTIFIED_EVENTS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val now = System.currentTimeMillis()

        for (event in events) {
            if (!event.impact.equals("High", ignoreCase = true)) continue
            val eventMs = parseIsoTimeMs(event.isoTime)
            val diffMins: Long = if (eventMs != null) {
                (eventMs - now) / 60_000L
            } else if (event.date.equals("Today", ignoreCase = true)) {
                50L // Default within active radar
            } else continue

            // Alert window: event approaching within 180 minutes
            if (diffMins in 0..180) {
                val eventKey = "${event.title}_${event.date}_${event.time}"
                if (!notifiedSet.contains(eventKey)) {
                    val bias = if (event.title.contains("CPI", true) || event.title.contains("Fed", true) || event.title.contains("FOMC", true)) {
                        Signal.BUY
                    } else if (event.title.contains("NFP", true) || event.title.contains("Employment", true)) {
                        Signal.SELL
                    } else {
                        Signal.BUY
                    }
                    val forecastText = if (event.forecast.isNotBlank()) "Est: ${event.forecast} (Prev: ${event.previous.ifBlank { "N/A" }})" else "High Volatility Expected"
                    val rangeText = "$${String.format(Locale.US, "%.1f", currentPrice - 18.0)} - $${String.format(Locale.US, "%.1f", currentPrice + 22.0)}"

                    val timeRemainingText = when (lang) {
                        AppLanguage.ENGLISH -> "~$diffMins Minutes"
                        AppLanguage.HINDI -> "~$diffMins मिनट"
                        AppLanguage.MARATHI -> "~$diffMins मिनिटे"
                    }

                    val sent = sendPreNewsAlertNotification(
                        context = context,
                        eventTitle = event.title,
                        country = event.country,
                        timeRemainingText = timeRemainingText,
                        indiaTimeText = event.getIndiaTimeFormatted(),
                        aiBias = bias,
                        forecastInfo = forecastText,
                        targetRange = rangeText,
                        lang = lang,
                        isTest = false
                    )

                    if (sent) {
                        notifiedSet.add(eventKey)
                        prefs.edit().putStringSet(KEY_NOTIFIED_EVENTS, notifiedSet).apply()
                    }
                }
            }
        }
    }

    /**
     * Instantly fires a test pre-news notification on demand so the user can verify their device alerts!
     */
    fun triggerInstantTestAlert(
        context: Context,
        eventTitle: String = "US Core CPI Inflation (MoM & YoY)",
        indiaTime: String = "08:30 PM IST",
        currentPrice: Double = 2742.50,
        lang: AppLanguage = AppLanguage.HINDI
    ): Boolean {
        return sendPreNewsAlertNotification(
            context = context,
            eventTitle = eventTitle,
            country = "USD",
            timeRemainingText = "45 Minutes",
            indiaTimeText = indiaTime,
            aiBias = Signal.BUY,
            forecastInfo = "Est: 0.3% vs Prev: 0.2%",
            targetRange = "$${String.format(Locale.US, "%.1f", currentPrice - 20.0)} - $${String.format(Locale.US, "%.1f", currentPrice + 25.0)}",
            lang = lang,
            isTest = true
        )
    }

    private fun parseIsoTimeMs(iso: String): Long? {
        return try {
            if (!iso.contains("T") || iso.length < 19) return null
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val localMs = sdf.parse(iso.substring(0, 19))?.time ?: return null
            val tz = iso.substring(19)
            val offsetMin = if (tz.length >= 6 && (tz[0] == '+' || tz[0] == '-')) {
                val sign = if (tz[0] == '-') -1 else 1
                sign * (tz.substring(1, 3).toInt() * 60 + tz.substring(4, 6).toInt())
            } else 0
            localMs - offsetMin * 60_000L
        } catch (_: Exception) {
            null
        }
    }
}
