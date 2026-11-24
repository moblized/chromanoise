package app.chromanoise.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import app.chromanoise.audio.NoiseEngine
import app.chromanoise.clock.ClockText
import app.chromanoise.clock.TimerControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What's ringing right now, for the alarm screen. */
data class Ringing(
    val alarmId: Int,
    val label: String,
    val snoozeMinutes: Int,
    /** A finished countdown timer rather than an alarm: "snooze" becomes "+1 min". */
    val isTimer: Boolean = false,
    /** Extra line under the clock, e.g. "5 min timer". */
    val detail: String = "",
    /** Whether turning the phone face down will snooze / stop it (shown as a hint). */
    val flipEnabled: Boolean = true,
    /** For a timer: when it ran out (wall clock), to count the overtime "−0:05" like Google Clock. */
    val endedAt: Long? = null,
    /** Rung by another app (e.g. "TempWatch", see [ExternalAlarm]): no snooze, flipping stops it. */
    val fromApp: String? = null,
) {
    val canSnooze: Boolean get() = fromApp == null
}

/**
 * Rings an alarm or a finished timer: plays the sound, shows the full-screen alarm, and
 * handles Snooze / Stop / flip-to-snooze. An alarm also fades the white noise out; a timer
 * leaves it playing. Gives up after [RING_LIMIT_MS].
 */
class AlarmRingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var player: AlarmSoundPlayer? = null
    private var flip: FlipToSnooze? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val giveUp = Runnable { silence() }

    /** For a ring another app asked for: who asked, and what to send them when it's stopped here. */
    private var externalCaller: String? = null
    private var externalOnStop: PendingIntent? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> ringAlarm(intent.getIntExtra(EXTRA_ID, -1))
            ACTION_RING_TIMER -> ringTimer(
                lengthMs = intent.getLongExtra(EXTRA_LENGTH, 0L),
                endedAt = intent.getLongExtra(EXTRA_ENDED_AT, System.currentTimeMillis()),
            )
            ACTION_RING_EXTERNAL -> intent.extras?.let { ringExternal(it) } ?: stopIfIdle()
            ACTION_SNOOZE -> snooze()
            ACTION_DISMISS -> finish()
            ACTION_DISMISS_EXTERNAL ->
                if (externalCaller != null && externalCaller == intent.getStringExtra(ExternalAlarm.EXTRA_CALLER)) {
                    finish(tellCaller = false)
                } else {
                    stopIfIdle()
                }
            else -> stopIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun stopIfIdle() {
        if (_ringing.value == null) stopSelf()
    }

    private fun ringAlarm(id: Int) {
        forgetCaller()
        val alarm = AlarmStore.get(this).find(id)
        val ringing = Ringing(
            alarmId = id,
            label = alarm?.label?.takeIf { it.isNotBlank() } ?: "Alarm",
            snoozeMinutes = alarm?.snoozeMinutes ?: 10,
            flipEnabled = alarm?.flipToSnooze ?: false,
        )
        if (!enterForeground(ringing, TimeFormat.time(this, System.currentTimeMillis()))) return
        if (alarm == null) {
            finish()
            return
        }
        // Wake to the alarm, not to the noise.
        NoiseEngine.stop(fadeMillis = 3_000)
        startSound(alarm)
    }

    private fun ringTimer(lengthMs: Long, endedAt: Long) {
        val detail = if (lengthMs > 0) "${ClockText.shortDuration(lengthMs)} timer" else "Timer"
        val ringing = Ringing(TIMER_RING_ID, "Time's up", snoozeMinutes = 1, isTimer = true, detail = detail, endedAt = endedAt)
        forgetCaller()
        if (!enterForeground(ringing, detail)) return
        // Timers ring straight away at full volume; noise (if any) keeps playing.
        startSound(Alarm(id = TIMER_RING_ID, hour = 0, minute = 0, gentleWake = false, vibrate = true))
    }

    /** Another app's alarm, e.g. TempWatch's "Brisket is ready". Rings like a finished timer. */
    private fun ringExternal(request: Bundle) {
        val caller = request.getString(ExternalAlarm.EXTRA_CALLER)
        val from = ExternalAlarm.callerName(caller) ?: return stopIfIdle()
        val title = request.getString(ExternalAlarm.EXTRA_TITLE)?.ifBlank { null } ?: "$from alarm"
        val detail = request.getString(ExternalAlarm.EXTRA_DETAIL).orEmpty()
        val ringing = Ringing(ExternalAlarm.RING_ID, title, snoozeMinutes = 0, detail = detail, fromApp = from)
        forgetCaller()
        if (!enterForeground(ringing, if (detail.isBlank()) from else "$detail · $from")) return
        externalCaller = caller
        externalOnStop = request.pendingIntent(ExternalAlarm.EXTRA_ON_STOP)
        // Full volume straight away, like a timer; any noise keeps playing. The app picks the
        // sound (null = soft chimes); one we can't play falls back to the chimes.
        val sound = request.getString(ExternalAlarm.EXTRA_SOUND)
        startSound(Alarm(id = ExternalAlarm.RING_ID, hour = 0, minute = 0, soundUri = sound, gentleWake = false, vibrate = true))
        // Tell the app it's ringing here, so it doesn't ring as well.
        request.pendingIntent(ExternalAlarm.EXTRA_ON_RINGING)?.let { send(it) }
    }

    private fun forgetCaller() {
        externalCaller = null
        externalOnStop = null
    }

    private fun send(pi: PendingIntent) {
        try {
            pi.send()
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "The other app's callback is gone", e)
        }
    }

    /** Publishes [ringing], shows the full-screen notification and keeps the CPU awake. */
    private fun enterForeground(ringing: Ringing, text: String): Boolean {
        // Publish first, so the alarm screen has something to show the moment it opens.
        _ringing.value = ringing
        val snoozeLabel = when {
            !ringing.canSnooze -> null
            ringing.isTimer -> "+1 min"
            else -> "Snooze ${ringing.snoozeMinutes} min"
        }
        val notification = AlarmNotifications.ringing(this, ringing.label, text, snoozeLabel, overtimeSince = ringing.endedAt)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(AlarmNotifications.RINGING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(AlarmNotifications.RINGING_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Not allowed to run in the foreground", e)
            AlarmNotifications.postFallback(this, ringing.label)
            _ringing.value = null
            releaseStartupWakeLock()
            stopSelf()
            return false
        }
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChromaNoise:alarm")
                .apply { setReferenceCounted(false) }
        }
        wakeLock?.acquire(RING_LIMIT_MS + 60_000)
        releaseStartupWakeLock()
        return true
    }

    private fun startSound(sound: Alarm) {
        player?.stop()
        flip?.stop()
        player = AlarmSoundPlayer(this).also { it.start(sound) }
        flip = if (sound.flipToSnooze) FlipToSnooze(this) { onFlipped() }.also { it.start() } else null
        handler.removeCallbacks(giveUp)
        handler.postDelayed(giveUp, RING_LIMIT_MS)
    }

    /** Phone turned face down: snooze an alarm, silence a timer or another app's alarm, and buzz twice to confirm. */
    private fun onFlipped() {
        val r = _ringing.value ?: return
        if (r.isTimer || !r.canSnooze) finish() else snooze()
        Vibrations.play(applicationContext, FLIP_CONFIRM)
    }

    private fun snooze() {
        val r = _ringing.value
        when {
            r == null -> Unit
            !r.canSnooze -> Unit // nothing to snooze: just stop
            r.isTimer -> TimerControl.start(this, 60_000L)
            else -> {
                val until = System.currentTimeMillis() + r.snoozeMinutes * 60_000L
                AlarmStore.get(this).update(r.alarmId) { it.copy(snoozedUntil = until, nextAt = null) }
                AlarmScheduler.sync(this)
            }
        }
        finish()
    }

    /** Nobody answered: stop ringing, and for an alarm leave a "missed alarm" notification. */
    private fun silence() {
        val r = _ringing.value
        val alarm = r?.takeIf { !it.isTimer && it.canSnooze }?.let { AlarmStore.get(this).find(it.alarmId) }
        if (alarm != null) AlarmNotifications.postMissed(this, alarm, System.currentTimeMillis() - RING_LIMIT_MS)
        // Another app's alarm: leave it ringing over there (it has its own "done" notification).
        finish(tellCaller = false)
    }

    /** [tellCaller]: for another app's alarm, let it know it was stopped here so it stops too. */
    private fun finish(tellCaller: Boolean = true) {
        val onStop = externalOnStop
        forgetCaller()
        handler.removeCallbacks(giveUp)
        flip?.stop()
        flip = null
        player?.stop()
        player = null
        _ringing.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        wakeLock?.let { if (it.isHeld) it.release() }
        if (tellCaller && onStop != null) send(onStop)
    }

    override fun onDestroy() {
        handler.removeCallbacks(giveUp)
        flip?.stop()
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
        private const val ACTION_RING_TIMER = "app.chromanoise.alarm.RING_TIMER"
        private const val ACTION_RING_EXTERNAL = "app.chromanoise.alarm.RING_EXTERNAL"
        private const val ACTION_DISMISS_EXTERNAL = "app.chromanoise.alarm.DISMISS_EXTERNAL"
        private const val EXTRA_ID = "alarm_id"
        private const val EXTRA_LENGTH = "timer_length"
        private const val EXTRA_ENDED_AT = "timer_ended_at"
        private const val RING_LIMIT_MS = 10 * 60_000L
        private const val TIMER_RING_ID = -1
        private const val TAG = "AlarmRingService"

        /** Two short taps: "got it". */
        private val FLIP_CONFIRM = Waveform(longArrayOf(40, 70, 90, 70), intArrayOf(0, 255, 0, 255), -1)

        private val _ringing = MutableStateFlow<Ringing?>(null)
        val ringing: StateFlow<Ringing?> = _ringing.asStateFlow()

        private var startupLock: PowerManager.WakeLock? = null

        fun ring(context: Context, id: Int) {
            context.startForegroundService(
                Intent(context, AlarmRingService::class.java).setAction(ACTION_RING).putExtra(EXTRA_ID, id),
            )
        }

        /** [endedAt]: wall-clock time the timer ran out. */
        fun ringTimer(context: Context, lengthMs: Long, endedAt: Long) {
            context.startForegroundService(
                Intent(context, AlarmRingService::class.java)
                    .setAction(ACTION_RING_TIMER)
                    .putExtra(EXTRA_LENGTH, lengthMs)
                    .putExtra(EXTRA_ENDED_AT, endedAt),
            )
        }

        /** [request]: see [ExternalAlarm]. Call from an alarm firing, like [ring]. */
        fun ringExternal(context: Context, request: Bundle) {
            context.startForegroundService(
                Intent(context, AlarmRingService::class.java).setAction(ACTION_RING_EXTERNAL).putExtras(request),
            )
        }

        /** The app that asked for the ring stopped it on its side. */
        fun dismissExternal(context: Context, caller: String) {
            if (_ringing.value?.canSnooze != false) return
            try {
                context.startService(
                    Intent(context, AlarmRingService::class.java)
                        .setAction(ACTION_DISMISS_EXTERNAL)
                        .putExtra(ExternalAlarm.EXTRA_CALLER, caller),
                )
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Couldn't reach the ringing service", e)
            }
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
