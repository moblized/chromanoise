package app.chromanoise.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.chromanoise.MainActivity
import app.chromanoise.R
import app.chromanoise.audio.NoiseEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the app alive (and shows the "now playing" notification) while noise is playing,
 * so it carries on with the screen off. Stops itself once the sound has faded out.
 */
class NoisePlaybackService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows while noise is playing"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            NoiseEngine.stop()
            if (!NoiseEngine.isActive.value) stopSelf()
            return START_NOT_STICKY
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(true, NoiseEngine.timerEndsAt.value, NoiseEngine.label.value),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        NoiseEngine.play()
        if (watcher == null) watcher = scope.launch { watchPlayback() }
        return START_NOT_STICKY
    }

    private suspend fun watchPlayback() {
        combine(
            NoiseEngine.isActive,
            NoiseEngine.isPlaying,
            NoiseEngine.timerEndsAt,
            NoiseEngine.label,
        ) { active, playing, endsAt, label -> Update(active, playing, endsAt, label) }
            .collect { u ->
                if (!u.active) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(u.playing, u.endsAt, u.label))
                }
            }
    }

    private data class Update(val active: Boolean, val playing: Boolean, val endsAt: Long?, val label: String)

    private fun buildNotification(playing: Boolean, endsAt: Long?, label: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, NoisePlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when {
            !playing -> "Fading out…"
            endsAt != null -> "Sleep timer on"
            else -> "Playing"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_noise)
            .setContentTitle(if (label == "Custom") "Custom noise" else "$label noise")
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (endsAt != null && playing) {
                    setWhen(endsAt)
                    setShowWhen(true)
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                } else {
                    setShowWhen(false)
                }
            }
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        // If the system tears us down while sound is still going, stop it cleanly.
        if (NoiseEngine.isActive.value) NoiseEngine.stop(fadeMillis = 0)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_PLAY = "app.chromanoise.PLAY"
        private const val ACTION_STOP = "app.chromanoise.STOP"

        fun play(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, NoisePlaybackService::class.java).setAction(ACTION_PLAY),
            )
        }
    }
}
