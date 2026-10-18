package app.chromanoise.clock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import app.chromanoise.MainActivity
import app.chromanoise.R
import app.chromanoise.alarm.AlarmNotifications

/**
 * The running timer / stopwatch outside the app, like Google Clock: an ongoing notification
 * that counts live by itself (the notification chronometer), shown on the lock screen, and —
 * on Android 16+ — promoted to a **Live Update**: a ticking chip in the status bar and a
 * card pinned at the top of the lock screen and notification shade.
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
        val b = base(context, R.drawable.ic_stat_timer, MainActivity.TAB_TIMER)
        if (t.running) {
            b.setContentTitle("Timer")
                .setContentText("${ClockText.shortDuration(t.durationMs)} timer")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + remaining)
                .setShowWhen(true)
                .addAction(action(context, "Pause", ClockReceiver.TIMER_PAUSE))
                .addAction(action(context, "+1:00", ClockReceiver.TIMER_ADD))
                .addAction(action(context, "Reset", ClockReceiver.TIMER_CANCEL))
            LiveUpdates.request(b, chipText = null) // the chronometer itself ticks in the chip
        } else {
            b.setContentTitle("Timer paused")
                .setContentText("${ClockText.timer(remaining)} left")
                .setShowWhen(false)
                .addAction(action(context, "Resume", ClockReceiver.TIMER_RESUME))
                .addAction(action(context, "Reset", ClockReceiver.TIMER_CANCEL))
            LiveUpdates.request(b, chipText = ClockText.timer(remaining))
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
        val laps = s.lapMarks.size
        val lapText = if (laps > 0) "$laps lap${if (laps == 1) "" else "s"}" else null
        val b = base(context, R.drawable.ic_stat_stopwatch, MainActivity.TAB_STOPWATCH)
        if (s.running) {
            b.setContentTitle("Stopwatch")
                .setContentText(lapText)
                .setUsesChronometer(true)
                .setWhen(System.currentTimeMillis() - elapsed)
                .setShowWhen(true)
                .addAction(action(context, "Pause", ClockReceiver.STOPWATCH_PAUSE))
                .addAction(action(context, "Lap", ClockReceiver.STOPWATCH_LAP))
            LiveUpdates.request(b, chipText = null)
        } else {
            b.setContentTitle("Stopwatch paused")
                .setContentText(listOfNotNull(ClockText.stopwatch(elapsed), lapText).joinToString(" · "))
                .setShowWhen(false)
                .addAction(action(context, "Resume", ClockReceiver.STOPWATCH_RESUME))
                .addAction(action(context, "Reset", ClockReceiver.STOPWATCH_RESET))
            LiveUpdates.request(b, chipText = ClockText.clock(elapsed))
        }
        nm.notify(STOPWATCH_ID, b.build())
    }

    private fun base(context: Context, icon: Int, tab: Int): Notification.Builder =
        Notification.Builder(context, CHANNEL)
            .setSmallIcon(icon)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC) // full detail on the lock screen
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(AlarmNotifications.openTab(context, tab))

    private fun action(context: Context, title: String, clockAction: String): Notification.Action =
        Notification.Action.Builder(null as Icon?, title, ClockReceiver.intent(context, clockAction)).build()

    private fun ensureChannel(context: Context) {
        // LOW: silent, but still allowed to become a Live Update (MIN isn't).
        val channel = NotificationChannel(CHANNEL, "Timer and stopwatch", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows a running timer or stopwatch on the lock screen and status bar"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager(context).createNotificationChannel(channel)
    }

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
}

/**
 * Android 16 "Live Updates" (promoted ongoing notifications): the status-bar chip and the
 * pinned lock-screen card. On older phones these calls do nothing and the ordinary ongoing
 * notification is shown instead.
 */
object LiveUpdates {
    /** Notification.EXTRA_REQUEST_PROMOTED_ONGOING, spelled out so it also works on the API 36 SDK. */
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    fun request(builder: Notification.Builder, chipText: String?) {
        builder.addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
        if (chipText != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            builder.setShortCriticalText(chipText)
        }
    }

    enum class Status { ON, NOTIFICATIONS_OFF, LIVE_UPDATES_OFF, NOT_SUPPORTED }

    fun status(context: Context): Status {
        val nm = context.getSystemService(NotificationManager::class.java)
        return when {
            !nm.areNotificationsEnabled() -> Status.NOTIFICATIONS_OFF
            Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA -> Status.NOT_SUPPORTED
            !nm.canPostPromotedNotifications() -> Status.LIVE_UPDATES_OFF
            else -> Status.ON
        }
    }

    /** Opens the screen where the user can switch Live Updates (or notifications) on for this app. */
    fun settingsIntent(context: Context, status: Status): Intent {
        val action = if (status == Status.LIVE_UPDATES_OFF && Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS
        } else {
            Settings.ACTION_APP_NOTIFICATION_SETTINGS
        }
        return Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
}
