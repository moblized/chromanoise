package app.chromanoise.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Our own alarm events: "time to ring" and "dismiss this snooze". Not reachable by other apps. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val store = AlarmStore.get(context)
        when (intent.action) {
            ACTION_FIRE -> {
                val alarm = store.find(id) ?: return
                if (!alarm.isOn) return // switched off after it was scheduled
                // Keep the CPU awake until the ringing service has started.
                AlarmRingService.holdStartupWakeLock(context)
                store.update(id) { it.afterFiring() }
                AlarmScheduler.sync(context)
                try {
                    AlarmRingService.ring(context, id)
                } catch (e: Exception) {
                    // Android refused to start the service — still make a noise.
                    Log.e("AlarmReceiver", "Couldn't start the alarm service", e)
                    AlarmNotifications.postFallback(context, alarm.label.ifBlank { "Alarm" })
                    AlarmRingService.releaseStartupWakeLock()
                }
            }
            ACTION_CANCEL_SNOOZE -> {
                store.update(id) { it.copy(snoozedUntil = null, nextAt = null) }
                AlarmScheduler.sync(context)
            }
        }
    }

    companion object {
        private const val ACTION_FIRE = "app.chromanoise.alarm.FIRE"
        private const val ACTION_CANCEL_SNOOZE = "app.chromanoise.alarm.CANCEL_SNOOZE"
        private const val EXTRA_ID = "alarm_id"

        fun fireIntent(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
            context, id,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun cancelSnoozeIntent(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
            context, id,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_CANCEL_SNOOZE).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/** System events after which alarms must be set again: reboot, clock or time-zone change, app update. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> AlarmScheduler.sync(context)
        }
    }
}
