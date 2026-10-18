package app.chromanoise.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import app.chromanoise.audio.NoiseEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What's ringing right now, for the alarm screen. */
data class Ringing(val alarmId: Int, val label: String, val snoozeMinutes: Int)

/**
 * Rings an alarm: fades the white noise out, plays the alarm sound, shows the full-screen
 * alarm, and handles Snooze / Stop. Gives up after [RING_LIMIT_MS] and leaves a "missed" note.
 */
class AlarmRingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: AlarmSoundPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val giveUp = Runnable { silence() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> ring(intent.getIntExtra(EXTRA_ID, -1))
            ACTION_SNOOZE -> snooze()
            ACTION_DISMISS -> finish()
            else -> if (_ringing.value == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun ring(id: Int) {
        val alarm = AlarmStore.get(this).find(id)
        val label = alarm?.label?.takeIf { it.isNotBlank() } ?: "Alarm"
        val snoozeMinutes = alarm?.snoozeMinutes ?: 10

        // Publish first, so the alarm screen has something to show the moment it opens.
        _ringing.value = Ringing(id, label, snoozeMinutes)
        val notification = AlarmNotifications.ringing(
            this, label, TimeFormat.time(this, System.currentTimeMillis()), snoozeMinutes,
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(AlarmNotifications.RINGING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(AlarmNotifications.RINGING_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Not allowed to run in the foreground", e)
            AlarmNotifications.postFallback(this, label)
            _ringing.value = null
            releaseStartupWakeLock()
            stopSelf()
            return
        }

        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChromaNoise:alarm")
                .apply { setReferenceCounted(false) }
        }
        wakeLock?.acquire(RING_LIMIT_MS + 60_000)
        releaseStartupWakeLock()

        if (alarm == null) {
            finish()
            return
        }

        // Wake to the alarm, not to the noise.
        NoiseEngine.stop(fadeMillis = 3_000)

        player?.stop()
        player = AlarmSoundPlayer(this).also { it.start(alarm) }
        handler.removeCallbacks(giveUp)
        handler.postDelayed(giveUp, RING_LIMIT_MS)
    }

    private fun snooze() {
        val r = _ringing.value
        if (r != null) {
            val until = System.currentTimeMillis() + r.snoozeMinutes * 60_000L
            AlarmStore.get(this).update(r.alarmId) { it.copy(snoozedUntil = until, nextAt = null) }
            AlarmScheduler.sync(this)
        }
        finish()
    }

    /** Nobody answered: stop ringing and leave a "missed alarm" notification. */
    private fun silence() {
        val r = _ringing.value
        val alarm = r?.let { AlarmStore.get(this).find(it.alarmId) }
        if (alarm != null) AlarmNotifications.postMissed(this, alarm, System.currentTimeMillis() - RING_LIMIT_MS)
        finish()
    }

    private fun finish() {
        handler.removeCallbacks(giveUp)
        player?.stop()
        player = null
        _ringing.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onDestroy() {
        handler.removeCallbacks(giveUp)
        player?.stop()
        player = null
        _ringing.value = null
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_SNOOZE = "app.chromanoise.alarm.SNOOZE"
        const val ACTION_DISMISS = "app.chromanoise.alarm.DISMISS"
        private const val ACTION_RING = "app.chromanoise.alarm.RING"
        private const val EXTRA_ID = "alarm_id"
        private const val RING_LIMIT_MS = 10 * 60_000L
        private const val TAG = "AlarmRingService"

        private val _ringing = MutableStateFlow<Ringing?>(null)
        val ringing: StateFlow<Ringing?> = _ringing.asStateFlow()

        private var startupLock: PowerManager.WakeLock? = null

        fun ring(context: Context, id: Int) {
            context.startForegroundService(
                Intent(context, AlarmRingService::class.java).setAction(ACTION_RING).putExtra(EXTRA_ID, id),
            )
        }

        fun snooze(context: Context) = send(context, ACTION_SNOOZE)

        fun dismiss(context: Context) = send(context, ACTION_DISMISS)

        private fun send(context: Context, action: String) {
            if (_ringing.value == null) return
            context.startService(Intent(context, AlarmRingService::class.java).setAction(action))
        }

        fun actionIntent(context: Context, action: String): PendingIntent = PendingIntent.getService(
            context, action.hashCode(),
            Intent(context, AlarmRingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Held from the alarm broadcast until the service takes over, so the phone can't doze in between. */
        @Synchronized
        fun holdStartupWakeLock(context: Context) {
            val lock = startupLock ?: context.applicationContext.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChromaNoise:alarm-start")
                .apply { setReferenceCounted(false) }
                .also { startupLock = it }
            lock.acquire(30_000)
        }

        @Synchronized
        fun releaseStartupWakeLock() {
            startupLock?.let { if (it.isHeld) it.release() }
        }
    }
}
