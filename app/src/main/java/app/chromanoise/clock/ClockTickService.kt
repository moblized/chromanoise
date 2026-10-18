package app.chromanoise.clock

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log

/**
 * Runs only while a timer is counting down, to keep its progress line moving (the time
 * itself ticks on its own). Refreshes once a second while the screen is on. With the screen
 * off it holds no wake lock, so the phone sleeps as normal; the finish alarm is handled
 * separately by AlarmManager.
 */
class ClockTickService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!refresh()) return
            handler.postDelayed(this, 1_000 - SystemClock.elapsedRealtime() % 1_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val t = ClockStore.get(this).timer.value
        if (!t.running) {
            finish()
            return START_NOT_STICKY
        }
        val notification = ClockNotifications.buildTimer(this, t)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(ClockNotifications.TIMER_RUNNING_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ClockNotifications.TIMER_RUNNING_ID, notification)
            }
        } catch (e: Exception) {
            // Not allowed to run in the foreground right now: the card still shows, just without a moving bar.
            Log.w(TAG, "Couldn't start in the foreground", e)
            getSystemService(NotificationManager::class.java).notify(ClockNotifications.TIMER_RUNNING_ID, notification)
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_NOT_STICKY
    }

    /** Re-posts the card; false once the timer has stopped running. */
    private fun refresh(): Boolean {
        val t = ClockStore.get(this).timer.value
        if (!t.running) {
            finish()
            return false
        }
        if (getSystemService(PowerManager::class.java).isInteractive) {
            getSystemService(NotificationManager::class.java)
                .notify(ClockNotifications.TIMER_RUNNING_ID, ClockNotifications.buildTimer(this, t))
        }
        return true
    }

    private fun finish() {
        handler.removeCallbacks(tick)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ClockTickService"

        /** Returns false if Android won't start it (the caller then posts the card itself). */
        fun start(context: Context): Boolean = try {
            context.startForegroundService(Intent(context, ClockTickService::class.java))
            true
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start", e)
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ClockTickService::class.java))
        }
    }
}
