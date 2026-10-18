package app.chromanoise.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import app.chromanoise.MainActivity
import app.chromanoise.R
import app.chromanoise.clock.LiveUpdates

/** Notifications for ringing, snoozed and missed alarms. */
object AlarmNotifications {
    private const val CH_RINGING = "alarm_ringing"
    private const val CH_STATUS = "alarm_status"
    private const val CH_FALLBACK = "alarm_fallback"
    const val RINGING_ID = 2
    private const val FALLBACK_ID = 3
    private const val SNOOZED_BASE = 1_000
    private const val MISSED_BASE = 2_000

    fun ensureChannels(context: Context) {
        val ringing = NotificationChannel(CH_RINGING, "Ringing alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Opens the alarm screen when an alarm goes off"
            setSound(null, null) // the app plays the alarm sound itself
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val status = NotificationChannel(CH_STATUS, "Snoozed and missed alarms", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows when an alarm is snoozed or was missed"
        }
        manager(context).createNotificationChannels(listOf(ringing, status))
    }

    /**
     * @param overtimeSince for a finished timer: when it ran out (wall clock). The notification
     *   then counts up from it, like Google Clock's "−0:05", and is promoted to a Live Update.
     */
    fun ringing(
        context: Context,
        title: String,
        text: String,
        snoozeLabel: String,
        overtimeSince: Long? = null,
    ): Notification {
        ensureChannels(context)
        val screen = PendingIntent.getActivity(
            context, 0,
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = Notification.Builder(context, CH_RINGING)
            .setSmallIcon(if (overtimeSince != null) R.drawable.ic_stat_timer else R.drawable.ic_stat_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .addAction(action(snoozeLabel, AlarmRingService.actionIntent(context, AlarmRingService.ACTION_SNOOZE)))
            .addAction(action("Stop", AlarmRingService.actionIntent(context, AlarmRingService.ACTION_DISMISS)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        if (overtimeSince != null) {
            b.setUsesChronometer(true).setWhen(overtimeSince).setShowWhen(true)
            LiveUpdates.request(b, chipText = null)
        }
        return b.build()
    }

    /** Shows a "Snoozed until…" notification for each snoozed alarm and clears the rest. */
    fun syncSnoozed(context: Context, alarms: List<Alarm>) {
        val nm = manager(context)
        for (a in alarms) {
            val until = a.snoozedUntil
            if (until == null) {
                nm.cancel(SNOOZED_BASE + a.id)
                continue
            }
            ensureChannels(context)
            val n = Notification.Builder(context, CH_STATUS)
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle(if (a.label.isBlank()) "Alarm snoozed" else "${a.label} · snoozed")
                .setContentText("Rings again at ${TimeFormat.time(context, until)}")
                .setCategory(Notification.CATEGORY_ALARM)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(openAlarms(context))
                .addAction(action("Dismiss", AlarmReceiver.cancelSnoozeIntent(context, a.id)))
                .build()
            nm.notify(SNOOZED_BASE + a.id, n)
        }
    }

    fun cancelFor(context: Context, alarmId: Int) {
        manager(context).cancel(SNOOZED_BASE + alarmId)
        manager(context).cancel(MISSED_BASE + alarmId)
    }

    fun postMissed(context: Context, alarm: Alarm, dueAt: Long) {
        ensureChannels(context)
        val time = TimeFormat.time(context, dueAt)
        val n = Notification.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Missed alarm")
            .setContentText(if (alarm.label.isBlank()) time else "$time · ${alarm.label}")
            .setCategory(Notification.CATEGORY_ALARM)
            .setWhen(dueAt)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(openAlarms(context))
            .build()
        manager(context).notify(MISSED_BASE + alarm.id, n)
    }

    /**
     * Last resort if the ringing service can't start: a notification that plays the phone's
     * alarm sound over and over (FLAG_INSISTENT) until it's tapped or swiped away.
     */
    fun postFallback(context: Context, label: String) {
        val channel = NotificationChannel(CH_FALLBACK, "Backup alarm sound", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Only used if an alarm can't start normally"
            setSound(
                Settings.System.DEFAULT_ALARM_ALERT_URI,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 800)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager(context).createNotificationChannel(channel)
        val n = Notification.Builder(context, CH_FALLBACK)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(label)
            .setContentText("Alarm · tap to stop")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openAlarms(context))
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        manager(context).notify(FALLBACK_ID, n)
    }

    /** Opens the app on the Alarms tab. */
    fun openAlarms(context: Context): PendingIntent = openTab(context, MainActivity.TAB_ALARMS)

    /** Opens the app on one of its tabs (MainActivity.TAB_…). */
    fun openTab(context: Context, tab: Int): PendingIntent = PendingIntent.getActivity(
        context, 100 + tab,
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_TAB, tab),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(title: String, intent: PendingIntent): Notification.Action =
        Notification.Action.Builder(null as Icon?, title, intent).build()

    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)
}
