package app.chromanoise.alarm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.UserManager
import android.util.Log
import app.chromanoise.clock.StopwatchControl
import app.chromanoise.clock.TimerControl
import app.chromanoise.playback.ChargerWatch
import app.chromanoise.playback.TriggerScheduler

/** Our own alarm events: "time to ring" and "dismiss this snooze". Not reachable by other apps (they use [ExternalAlarmReceiver]). */
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
            ACTION_FIRE_EXTERNAL -> intent.extras?.let { ExternalAlarm.ring(context, it) }
            ACTION_CANCEL_SNOOZE -> {
                store.update(id) { it.copy(snoozedUntil = null, nextAt = null) }
                AlarmScheduler.sync(context)
            }
        }
    }

    companion object {
        private const val ACTION_FIRE = "app.chromanoise.alarm.FIRE"
        private const val ACTION_CANCEL_SNOOZE = "app.chromanoise.alarm.CANCEL_SNOOZE"
        private const val ACTION_FIRE_EXTERNAL = "app.chromanoise.alarm.FIRE_EXTERNAL"
        private const val EXTRA_ID = "alarm_id"

        fun fireIntent(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
            context, id,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** The one-second alarm for a ring another app asked for ([ExternalAlarm]); null [request] to cancel it. */
        fun fireExternalIntent(context: Context, request: Bundle?): PendingIntent = PendingIntent.getBroadcast(
            context, ExternalAlarm.RING_ID,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE_EXTERNAL).apply { request?.let { putExtras(it) } },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        fun cancelSnoozeIntent(context: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
            context, id,
            Intent(context, AlarmReceiver::class.java).setAction(ACTION_CANCEL_SNOOZE).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/**
 * System events after which alarms and the timer must be set again: reboot, clock or
 * time-zone change, app update. Also brings back the timer/stopwatch notifications.
 */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> {
                AlarmScheduler.sync(context)
                TimerControl.sync(context)
                StopwatchControl.sync(context)
                // The auto-start settings are in normal storage, readable only once the phone has
                // been unlocked; BOOT_COMPLETED (sent after unlock) gets here again.
                if (context.getSystemService(UserManager::class.java).isUserUnlocked) {
                    TriggerScheduler.sync(context)
                    ChargerWatch.sync(context)
                }
            }
        }
    }
}
