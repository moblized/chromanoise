package app.chromanoise.clock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.SystemClock
import app.chromanoise.MainActivity
import app.chromanoise.R
import app.chromanoise.alarm.AlarmNotifications

/**
 * Ongoing notifications for a running timer or stopwatch. They count live by themselves
 * (Android's notification chronometer), so nothing in the app needs to stay awake.
 */
object ClockNotifications {
    private const val CHANNEL = "clock"
    private const val TIMER_ID = 20
    private const val STOPWATCH_ID = 21

    fun showTimer(context: Context, t: TimerState) {
        val nm = manager(context)
        if (t.idle) {
            nm.cancel(TIMER_ID)
            return
        }
        ensureChannel(context)
        val remaining = t.remaining(SystemClock.elapsedRealtime())
        val b = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(AlarmNotifications.openTab(context, MainActivity.TAB_TIMER))
        if (t.running) {
            b.setContentTitle("Timer · ${ClockText.shortDuration(t.durationMs)}")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + remaining)
                .setShowWhen(true)
                .addAction(action(context, "Pause", ClockReceiver.TIMER_PAUSE))
                .addAction(action(context, "+1 min", ClockReceiver.TIMER_ADD))
                .addAction(action(context, "Cancel", ClockReceiver.TIMER_CANCEL))
        } else {
            b.setContentTitle("Timer paused")
                .setContentText("${ClockText.timer(remaining)} left")
                .setShowWhen(false)
                .addAction(action(context, "Resume", ClockReceiver.TIMER_RESUME))
                .addAction(action(context, "Cancel", ClockReceiver.TIMER_CANCEL))
        }
        nm.notify(TIMER_ID, b.build())
    }

    fun showStopwatch(context: Context, s: StopwatchState) {
        val nm = manager(context)
        if (s.idle) {
            nm.cancel(STOPWATCH_ID)
            return
        }
        ensureChannel(context)
        val elapsed = s.elapsed(SystemClock.elapsedRealtime())
        val b = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_stopwatch)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(AlarmNotifications.openTab(context, MainActivity.TAB_STOPWATCH))
        if (s.running) {
            val laps = s.lapMarks.size
            b.setContentTitle("Stopwatch")
                .setContentText(if (laps > 0) "$laps lap${if (laps == 1) "" else "s"}" else null)
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - elapsed)
                .setShowWhen(true)
                .addAction(action(context, "Pause", ClockReceiver.STOPWATCH_PAUSE))
                .addAction(action(context, "Lap", ClockReceiver.STOPWATCH_LAP))
        } else {
            b.setContentTitle("Stopwatch paused")
                .setContentText(ClockText.stopwatch(elapsed))
                .setShowWhen(false)
                .addAction(action(context, "Resume", ClockReceiver.STOPWATCH_RESUME))
                .addAction(action(context, "Reset", ClockReceiver.STOPWATCH_RESET))
        }
        nm.notify(STOPWATCH_ID, b.build())
    }

    private fun action(context: Context, title: String, clockAction: String): Notification.Action =
        Notification.Action.Builder(null as Icon?, title, ClockReceiver.intent(context, clockAction)).build()

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Timer and stopwatch", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows a running timer or stopwatch"
            setShowBadge(false)
        }
        manager(context).createNotificationChannel(channel)
    }

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
}
