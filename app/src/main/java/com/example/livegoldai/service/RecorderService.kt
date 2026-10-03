package com.example.livegoldai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.livegoldai.MainActivity
import com.example.livegoldai.R
import com.example.livegoldai.data.AppEngine
import com.example.livegoldai.data.BackgroundRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 24/7 PREDICTION RECORDER
 *
 * A foreground service (permanent notification) that calls [BackgroundRecorder.tick] once a minute:
 * records one prediction per candle on every timeframe and checks the ones whose time is over.
 * It raises a separate alert notification when something real is broken (no live price, ledger cannot be
 * written, results cannot be checked) and removes it when the problem is gone.
 * Phone battery savers can still pause it: set the app's battery mode to "Unrestricted" for true 24/7.
 */
class RecorderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val lastAlertAt = HashMap<String, Long>()
    private var activeAlerts: Set<String> = emptySet()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        AppEngine.init(filesDir)
        createChannels(this)
        goForeground(statusNotification("Prediction recorder starting…", "Recording begins within a minute", null))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (job?.isActive != true) {
            job = scope.launch { loop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try { scope.cancel() } catch (_: Exception) { }
        super.onDestroy()
    }

    private suspend fun loop() {
        while (scope.isActive) {
            val wake = try {
                (getSystemService(Context.POWER_SERVICE) as PowerManager)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kfx:recorder").apply { acquire(110_000L) }
            } catch (_: Exception) { null }
            try {
                val r = AppEngine.recorder.tick(System.currentTimeMillis())
                show(r)
                alerts(r)
            } catch (e: Exception) {
                notifySafely(STATUS_ID, statusNotification("Recorder error", e.javaClass.simpleName + ": " + (e.message ?: ""), null))
            } finally {
                try { if (wake != null && wake.isHeld) wake.release() } catch (_: Exception) { }
            }
            delay(TICK_MS)
        }
    }

    private fun show(r: BackgroundRecorder.TickReport) {
        val title = when {
            r.problems.isNotEmpty() -> "⚠ " + r.problems.first().title
            r.marketClosed -> "Market closed • recorder waiting"
            else -> "Recording predictions • 5M to 1D"
        }
        val line = "Recorded ${r.recorded} • checked ${r.checked} • active ${r.active} • today ✓${r.todayCorrect} ✗${r.todayWrong}"
        val big = buildString {
            append(line)
            append("\nLast recorded: ").append(r.lastRecorded)
            append("\nNext result due: ").append(r.nextDue)
            if (r.recordedNow.isNotEmpty()) append("\nJust recorded: ").append(r.recordedNow.joinToString(", "))
            if (r.checkedNow.isNotEmpty()) append("\nJust checked: ").append(r.checkedNow.joinToString(", "))
            r.problems.forEach { append("\n⚠ ").append(it.text) }
        }
        notifySafely(STATUS_ID, statusNotification(title, line, big))
    }

    /** One alert per problem, repeated at most once an hour; cancelled when the problem is gone. */
    private fun alerts(r: BackgroundRecorder.TickReport) {
        val now = System.currentTimeMillis()
        val codes = r.problems.map { it.code }.toSet()
        for (p in r.problems) {
            if (now - (lastAlertAt[p.code] ?: 0L) < ALERT_REPEAT_MS) continue
            lastAlertAt[p.code] = now
            val n = NotificationCompat.Builder(this, ALERT_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_kalankar)
                .setContentTitle("Kalankar FX: " + p.title)
                .setContentText(p.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(p.text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build()
            notifySafely(ALERT_BASE_ID + alertIndex(p.code), n)
        }
        for (gone in activeAlerts - codes) {
            try { NotificationManagerCompat.from(this).cancel(ALERT_BASE_ID + alertIndex(gone)) } catch (_: Exception) { }
            lastAlertAt.remove(gone)
        }
        activeAlerts = codes
    }

    private fun alertIndex(code: String) = when (code) { "NO_LIVE_DATA" -> 1; "LEDGER_WRITE" -> 2; "VERIFY_FAIL" -> 3; else -> 9 }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 7101,
        Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun statusNotification(title: String, text: String, big: String?): Notification {
        val b = NotificationCompat.Builder(this, STATUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_kalankar)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openApp())
        if (big != null) b.setStyle(NotificationCompat.BigTextStyle().bigText(big))
        return b.build()
    }

    private fun goForeground(n: Notification) {
        try {
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, STATUS_ID, n, type)
        } catch (_: Exception) {
            // the system refused (for example started from the background): stop instead of crashing
            stopSelf()
        }
    }

    private fun notifySafely(id: Int, n: Notification) {
        try {
            NotificationManagerCompat.from(this).notify(id, n)
        } catch (_: SecurityException) {
            // notification permission not granted: the service still records, it just cannot show the text
        } catch (_: Exception) {
        }
    }

    companion object {
        const val STATUS_CHANNEL = "kfx_recorder_status"
        const val ALERT_CHANNEL = "kfx_recorder_alerts"
        const val STATUS_ID = 7100
        const val ALERT_BASE_ID = 7110
        const val TICK_MS = 60_000L
        const val ALERT_REPEAT_MS = 60 * 60_000L
        const val PREFS = "kalankar_gold_prefs"
        const val PREF_ON = "bg_recorder_on"

        fun createChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(
                    NotificationChannel(STATUS_CHANNEL, "Prediction recorder", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Shows that predictions are being recorded and checked"
                    }
                )
                nm.createNotificationChannel(
                    NotificationChannel(ALERT_CHANNEL, "Recorder problems", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Alerts when live data, saving or checking stops working"
                    }
                )
            }
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, RecorderService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecorderService::class.java))
        }
    }
}

/** Restarts the recorder after the phone reboots or the app is updated, if the user left it switched on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val on = context.getSharedPreferences(RecorderService.PREFS, Context.MODE_PRIVATE).getBoolean(RecorderService.PREF_ON, true)
        if (!on) return
        try { RecorderService.start(context) } catch (_: Exception) { }
    }
}
