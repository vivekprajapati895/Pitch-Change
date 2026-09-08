package com.pitchplayer.app.export

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.util.UnstableApi
import com.pitchplayer.app.MainActivity
import com.pitchplayer.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the export alive when the user switches away from the app.
 *
 * Android kills background work aggressively, and a five minute karaoke track
 * can take longer than the grace period allows.
 */
@UnstableApi
class ExportService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            job?.cancel()
            stopSelfSafely()
            return START_NOT_STICKY
        }

        val request = ExportManager.consumePending()
        if (request == null) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        startInForeground(request.source.title, "Starting", 0)

        job = scope.launch {
            var lastNotified = -1
            try {
                val saved = Exporter(applicationContext).run(request) { stage, fraction ->
                    ExportManager.publishProgress(stage, fraction)
                    val pct = (fraction.coerceIn(0f, 1f) * 100).toInt()
                    if (pct != lastNotified) {
                        lastNotified = pct
                        notify(buildProgress(request.source.title, stage, pct))
                    }
                }
                ExportManager.publishDone(saved, request.audioOnly)
                notify(buildDone(saved))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val message = (e as? ExportFailure)?.message
                    ?: e.message
                    ?: "Export failed."
                ExportManager.publishFailure(message)
                notify(buildFailed(message))
            } finally {
                stopSelfSafely()
            }
        }

        return START_NOT_STICKY
    }

    private fun stopSelfSafely() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------- notifications

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.export_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.export_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun base() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentIntent(contentIntent())
        .setOnlyAlertOnce(true)

    private fun buildProgress(title: String, stage: String, percent: Int): Notification =
        base()
            .setContentTitle(title)
            .setContentText("$stage  $percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun buildDone(saved: MediaStoreWriter.Saved): Notification =
        base()
            .setContentTitle(getString(R.string.export_done_title))
            .setContentText("Saved to ${saved.folder}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("${saved.displayName}\nSaved to ${saved.folder}"))
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

    private fun buildFailed(message: String): Notification =
        base()
            .setContentTitle(getString(R.string.export_failed_title))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

    private fun startInForeground(title: String, stage: String, percent: Int) {
        val notification = buildProgress(title, stage, percent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notify(notification: Notification) {
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "exports"
        private const val NOTIFICATION_ID = 4201
        const val ACTION_CANCEL = "com.pitchplayer.app.CANCEL_EXPORT"
    }
}
