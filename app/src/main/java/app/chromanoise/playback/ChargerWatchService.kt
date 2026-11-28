package app.chromanoise.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.UserManager
import android.util.Log
import app.chromanoise.MainActivity
import app.chromanoise.R
import app.chromanoise.alarm.AlarmRingService
import app.chromanoise.alarm.TimeFormat
import app.chromanoise.audio.NoiseEngine
import app.chromanoise.data.NightWindow
import app.chromanoise.data.TriggerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Bedside mode: starts the noise when the phone is put on a wireless charger, and stops it
 * again when the phone is picked up. Charging by cable never starts it.
 *
 * Android stopped delivering "charger connected" to apps that aren't running, so this small
 * service stays running while the setting is on and listens for it. It does nothing between
 * those events and holds no wake lock. Its notification is minimised (no status-bar icon).
 *
 * Wireless pads often drop out for a moment when the phone shifts, so a reconnect within
 * [SAME_SESSION_MS] of a disconnect counts as the same session: it neither restarts noise you
 * stopped nor needs to restart noise that's still playing.
 */
class ChargerWatchService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** elapsedRealtime of the last time the phone came off the charger; 0 = not yet. */
    private var lastDisconnectAt = 0L

    /** The noise now playing was started by this service (so lifting the phone may stop it). */
    private var startedByCharger = false

    private val pendingStart = Runnable { maybeStartNoise() }
    private val pendingStop = Runnable { stopAfterLift() }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> onConnected()
                Intent.ACTION_POWER_DISCONNECTED -> onDisconnected()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED) // system broadcasts still arrive
        } else {
            registerReceiver(receiver, filter)
        }
        scope.launch {
            NoiseEngine.isPlaying.collect { playing ->
                if (!playing) {
                    startedByCharger = false // stopped by you, the auto-off timer or an alarm
                    handler.removeCallbacks(pendingStop)
                }
                if (running) refreshNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TURN_OFF -> {
                TriggerStore.get(this).update { it.copy(chargerEnabled = false) }
                shutDown()
                return START_NOT_STICKY
            }
            ACTION_STOP_NOISE -> NoiseEngine.stop()
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            running = true
        } catch (e: Exception) {
            Log.w(TAG, "Not allowed to run in the foreground right now", e)
            shutDown()
            return START_NOT_STICKY
        }
        return START_STICKY // if Android ever stops it, have it brought back to keep watching
    }

    private fun onConnected() {
        handler.removeCallbacks(pendingStop) // back on the charger: keep playing
        val sinceLift = SystemClock.elapsedRealtime() - lastDisconnectAt
        if (lastDisconnectAt != 0L && sinceLift < SAME_SESSION_MS) return // just a blip
        handler.removeCallbacks(pendingStart)
        handler.postDelayed(pendingStart, SETTLE_MS) // let the charger type settle first
    }

    private fun onDisconnected() {
        lastDisconnectAt = SystemClock.elapsedRealtime()
        handler.removeCallbacks(pendingStart)
        val s = TriggerStore.get(this).settings.value
        if (s.chargerStopOnLift && startedByCharger && NoiseEngine.isPlaying.value) {
            handler.postDelayed(pendingStop, SAME_SESSION_MS)
        }
    }

    private fun maybeStartNoise() {
        val s = TriggerStore.get(this).settings.value
        if (!s.chargerEnabled) return
        val plugged = pluggedType()
        if (plugged == 0) return // already taken off again
        if (plugged != BatteryManager.BATTERY_PLUGGED_WIRELESS) return // cables never count
        val now = LocalTime.now()
        if (s.chargerNightOnly && !NightWindow.contains(now.hour * 60 + now.minute, s.nightFrom, s.nightTo)) return
        if (NoiseEngine.isPlaying.value) return // already playing (you started it yourself)
        if (AlarmRingService.ringing.value != null) return
        if (inCall()) return
        try {
            TriggerStarter.start(this, playInProcessIfBlocked = true)
            startedByCharger = true
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't start the noise", e)
        }
    }

    private fun stopAfterLift() {
        if (startedByCharger && NoiseEngine.isPlaying.value) NoiseEngine.stop(fadeMillis = 2_000)
        startedByCharger = false
    }

    /** BATTERY_PLUGGED_* of the current power source, or 0 when on battery. */
    private fun pluggedType(): Int {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val battery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(null, filter)
        }
        return battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    }

    private fun inCall(): Boolean = when (getSystemService(AudioManager::class.java).mode) {
        AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE -> true
        else -> false
    }

    private fun refreshNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Bedside mode", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shown while Chroma Noise is waiting for you to put the phone on the charger"
                setShowBadge(false)
            },
        )
        val s = TriggerStore.get(this).settings.value
        val playing = NoiseEngine.isPlaying.value
        val window = if (s.chargerNightOnly) {
            " between ${TimeFormat.time(this, s.nightFrom / 60, s.nightFrom % 60)} and " +
                TimeFormat.time(this, s.nightTo / 60, s.nightTo % 60)
        } else {
            ""
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_noise)
            .setContentTitle(if (playing) "Noise playing · bedside mode on" else "Bedside mode on")
            .setContentText("Noise starts when you put the phone on your wireless charger$window")
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(open)
        if (playing) b.addAction(action(R.drawable.ic_notif_stop, "Stop noise", ACTION_STOP_NOISE))
        b.addAction(action(R.drawable.ic_notif_stop, "Turn off bedside mode", ACTION_TURN_OFF))
        return b.build()
    }

    private fun action(icon: Int, title: String, what: String): Notification.Action {
        val pi = PendingIntent.getService(
            this, what.hashCode(), Intent(this, ChargerWatchService::class.java).setAction(what),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), title, pi).build()
    }

    private fun shutDown() {
        running = false
        handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(receiver) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ChargerWatch"
        private const val CHANNEL = "bedside"
        private const val NOTIFICATION_ID = 30
        private const val ACTION_TURN_OFF = "app.chromanoise.bedside.TURN_OFF"
        private const val ACTION_STOP_NOISE = "app.chromanoise.bedside.STOP_NOISE"

        /** Back on the charger within this long counts as the same session (a wireless pad blip). */
        const val SAME_SESSION_MS = 15_000L

        /** Wait this long after "charger connected" so the charger type (wireless or cable) is known. */
        private const val SETTLE_MS = 2_000L

        @Volatile var running = false
            private set
    }
}

/** Starts or stops [ChargerWatchService] to match the setting. Safe to call any time. */
object ChargerWatch {
    fun sync(context: Context) {
        val app = context.applicationContext
        // Settings live in normal storage, which is locked until the phone is first unlocked after a
        // reboot; BOOT_COMPLETED arrives after that and calls this again.
        if (!app.getSystemService(UserManager::class.java).isUserUnlocked) return
        val intent = Intent(app, ChargerWatchService::class.java)
        if (TriggerStore.get(app).settings.value.chargerEnabled) {
            try {
                app.startForegroundService(intent) // also refreshes its notification text
            } catch (e: Exception) {
                Log.w("ChargerWatch", "Couldn't start bedside mode", e)
            }
        } else {
            app.stopService(intent)
        }
    }
}
