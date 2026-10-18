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
 * The running timer / stopwatch outside the app, like Google Clock:
 *  - a ticking chip in the status bar, and on Samsung (One UI 8) the pop-down card you get by
 *    tapping it — big time, round pause button, progress line;
 *  - a live card pinned to the top of the lock screen and notification shade.
 * These are Android 16 **Live Updates**: an ongoing notification with a self-ticking
 * chronometer, icon buttons and (for the timer) a progress bar. Older Android versions show
 * the same thing as a normal ongoing notification.
 *
 * A running timer's progress bar is kept moving by [ClockTickService]; everything else is
 * posted directly.
 */
object ClockNotifications {
    private const val CHANNEL = "clock"

    /** Paused timer (posted directly). */
    private const val TIMER_ID = 20
    private const val STOPWATCH_ID = 21

    /** Running timer (owned by [ClockTickService] while it runs). */
    const val TIMER_RUNNING_ID = 22

    private const val PROGRESS_STEPS = 1000

    fun showTimer(context: Context, t: TimerState) {
        val nm = manager(context)
        when {
            t.running -> {
                nm.cancel(TIMER_ID)
                // The service keeps the progress line moving; if Android won't start it, post once.
                if (!ClockTickService.start(context)) nm.notify(TIMER_RUNNING_ID, buildTimer(context, t))
            }
            t.paused -> {
                ClockTickService.stop(context)
                nm.cancel(TIMER_RUNNING_ID)
                nm.notify(TIMER_ID, buildTimer(context, t))
            }
            else -> {
                ClockTickService.stop(context)
                nm.cancel(TIMER_RUNNING_ID)
                nm.cancel(TIMER_ID)
            }
        }
    }

    /** The timer card: running (ticking down, progress filling) or paused. */
    fun buildTimer(context: Context, t: TimerState): Notification {
        ensureChannel(context)
        val now = SystemClock.elapsedRealtime()
        val remaining = t.remaining(now)
        val done = ((1f - t.fractionLeft(now)) * PROGRESS_STEPS).toInt().coerceIn(0, PROGRESS_STEPS)
        val b = base(context, R.drawable.ic_stat_timer, MainActivity.TAB_TIMER)
            .setContentText("${ClockText.shortDuration(t.durationMs)} timer")
        if (t.running) {
            b.setContentTitle("Timer")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + remaining)
                .setShowWhen(true)
                // First button = the round button in Samsung's pop-down card.
                .addAction(action(context, R.drawable.ic_notif_pause, "Pause", ClockReceiver.TIMER_PAUSE))
                .addAction(action(context, R.drawable.ic_notif_add, "+1:00", ClockReceiver.TIMER_ADD))
                .addAction(action(context, R.drawable.ic_notif_stop, "Reset", ClockReceiver.TIMER_CANCEL))
            LiveUpdates.request(b, chipText = null) // the chronometer itself ticks in the chip
        } else {
            b.setContentTitle("Timer paused · ${ClockText.timer(remaining)} left")
                .setShowWhen(false)
                .addAction(action(context, R.drawable.ic_notif_play, "Resume", ClockReceiver.TIMER_RESUME))
                .addAction(action(context, R.drawable.ic_notif_stop, "Reset", ClockReceiver.TIMER_CANCEL))
            LiveUpdates.request(b, chipText = ClockText.timer(remaining))
        }
        progress(b, done)
        return b.build()
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
                .addAction(action(context, R.drawable.ic_notif_pause, "Pause", ClockReceiver.STOPWATCH_PAUSE))
                .addAction(action(context, R.drawable.ic_notif_lap, "Lap", ClockReceiver.STOPWATCH_LAP))
            LiveUpdates.request(b, chipText = null)
        } else {
            b.setContentTitle("Stopwatch paused")
                .setContentText(listOfNotNull(ClockText.stopwatch(elapsed), lapText).joinToString(" · "))
                .setShowWhen(false)
                .addAction(action(context, R.drawable.ic_notif_play, "Resume", ClockReceiver.STOPWATCH_RESUME))
                .addAction(action(context, R.drawable.ic_notif_stop, "Reset", ClockReceiver.STOPWATCH_RESET))
            LiveUpdates.request(b, chipText = ClockText.clock(elapsed))
        }
        nm.notify(STOPWATCH_ID, b.build())
    }

    /** Android 16: Live Update progress style (the line in the card). Older: a plain progress bar. */
    private fun progress(b: Notification.Builder, done: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            b.setStyle(
                Notification.ProgressStyle()
                    .setProgressSegments(listOf(Notification.ProgressStyle.Segment(PROGRESS_STEPS)))
                    .setProgress(done),
            )
        } else {
            b.setProgress(PROGRESS_STEPS, done, false)
        }
    }

    private fun base(context: Context, icon: Int, tab: Int): Notification.Builder =
        Notification.Builder(context, CHANNEL)
            .setSmallIcon(icon)
            .setCategory(Notification.CATEGORY_STOPWATCH)
            .setVisibility(Notification.VISIBILITY_PUBLIC) // full detail on the lock screen
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(AlarmNotifications.openTab(context, tab))

    private fun action(context: Context, icon: Int, title: String, clockAction: String): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(context, icon), title, ClockReceiver.intent(context, clockAction)).build()

    fun ensureChannel(context: Context) {
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
