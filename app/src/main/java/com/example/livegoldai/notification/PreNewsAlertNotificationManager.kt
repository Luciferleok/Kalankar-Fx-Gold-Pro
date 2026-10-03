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
                AppLanguage.ENGLISH -> "WAIT 🟡 (no new trade before the release)"
                AppLanguage.HINDI -> "WAIT (रिलीज़ से पहले नया ट्रेड नहीं 🟡)"
                AppLanguage.MARATHI -> "WAIT (रिलीजपूर्वी नवीन ट्रेड नाही 🟡)"
            }
        }

        val title = when (lang) {
            AppLanguage.ENGLISH -> if (isTest) "🚨 [TEST ALERT] 🇮🇳 $indiaTimeText • $eventTitle" else "🚨 1-HR NEWS ALERT: 🇮🇳 $indiaTimeText • $eventTitle"
            AppLanguage.HINDI -> if (isTest) "🚨 [टेस्ट अलर्ट] 🇮🇳 $indiaTimeText • $eventTitle" else "🚨 न्यूज़ अलर्ट: 🇮🇳 $indiaTimeText • $eventTitle"
            AppLanguage.MARATHI -> if (isTest) "🚨 [चाचणी अलर्ट] 🇮🇳 $indiaTimeText • $eventTitle" else "🚨 न्यूज अलर्ट: 🇮🇳 $indiaTimeText • $eventTitle"
        }

        val bodySummary = when (lang) {
            AppLanguage.ENGLISH -> "🇮🇳 IST: $indiaTimeText ($timeRemainingText) | Signal: $biasLabel"
            AppLanguage.HINDI -> "🇮🇳 भारत समय: $indiaTimeText ($timeRemainingText) | Signal: $biasLabel"
            AppLanguage.MARATHI -> "🇮🇳 भारत वेळ: $indiaTimeText ($timeRemainingText) | Signal: $biasLabel"
        }

        val detailedBigText = when (lang) {
            AppLanguage.ENGLISH -> """
                ⏰ High-Impact Release approaching in $timeRemainingText!
                🇮🇳 Indian Standard Time (IST): $indiaTimeText
                🎯 App signal: $biasLabel
                📊 Forecast Analysis: $forecastInfo
                📈 Price: $targetRange
                ⚡ Spreads usually widen around the release. The app shows WAIT 30 minutes before it.
            """.trimIndent()
            AppLanguage.HINDI -> """
                ⏰ लगभग $timeRemainingText में बड़ी मार्केट मूविंग न्यूज़ आने वाली है!
                🇮🇳 भारतीय समय (IST): $indiaTimeText (आज रात)
                🎯 ऐप सिग्नल: $biasLabel
                📊 डेटा एनालिसिस: $forecastInfo
                📈 भाव: $targetRange
                ⚡ रिलीज़ के आसपास स्प्रेड बढ़ता है। ऐप 30 मिनट पहले से WAIT दिखाता है।
            """.trimIndent()
            AppLanguage.MARATHI -> """
                ⏰ अंदाजे $timeRemainingText मध्ये महत्त्वाची मार्केट हलवणारी बातमी येणार!
                🇮🇳 भारतीय वेळ (IST): $indiaTimeText
                🎯 ॲप सिग्नल: $biasLabel
                📊 डेटा विश्लेषण: $forecastInfo
                📈 भाव: $targetRange
                ⚡ रिलीजच्या वेळी स्प्रेड वाढतो. ॲप 30 मिनिटे आधीपासून WAIT दाखवते.
            """.trimIndent()
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kalankar)
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
            } else continue // only alert when the real release time is known

            // Alert window: event approaching within 180 minutes
            if (diffMins in 0..180) {
                val eventKey = "${event.title}_${event.date}_${event.time}"
                if (!notifiedSet.contains(eventKey)) {
                    val bias = Signal.WAIT // the app does not predict news outcomes
                    val forecastText = if (event.forecast.isNotBlank()) "Est: ${event.forecast} (Prev: ${event.previous.ifBlank { "N/A" }})" else "High Volatility Expected"
                    val rangeText = "Not predicted: price can spike both ways (now $${String.format(Locale.US, "%.1f", currentPrice)})"

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
            aiBias = Signal.WAIT,
            forecastInfo = "Test notification (sample text, not real data)",
            targetRange = "test",
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
