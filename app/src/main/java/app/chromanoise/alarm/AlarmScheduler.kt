package app.chromanoise.alarm

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.util.Log
import java.time.ZoneId

/**
 * Keeps Android's AlarmManager in step with [AlarmStore]. Every change goes through [sync],
 * which works out each alarm's next ring time from scratch — so it's also what runs after a
 * reboot, a time-zone change or an app update.
 */
object AlarmScheduler {
    /** An alarm that was due while the phone was off (or restarting) still rings if we're this late. */
    private const val LATE_LIMIT_MS = 15 * 60_000L
    private const val TAG = "AlarmScheduler"

    fun sync(context: Context) {
        val ctx = context.applicationContext
        val store = AlarmStore.get(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()

        val updated = store.alarms.value.map { original ->
            var a = original
            val due = a.nextAt
            if (due != null && due <= now) {
                if (now - due <= LATE_LIMIT_MS) {
                    // It should have rung moments ago but didn't — ring it now.
                    schedule(ctx, am, a.id, now + 2_000)
                    return@map a
                }
                AlarmNotifications.postMissed(ctx, a, due)
                a = a.afterFiring()
            }
            val snooze = a.snoozedUntil
            if (snooze != null && snooze <= now) a = a.copy(snoozedUntil = null)

            val next = a.nextTrigger(now, zone)
            if (next == null) cancelAlarm(ctx, am, a.id) else schedule(ctx, am, a.id, next)
            a.copy(nextAt = next)
        }
        store.replaceAll(updated)
        AlarmNotifications.syncSnoozed(ctx, updated)
    }

    /** Call before deleting an alarm from the store. */
    fun cancel(context: Context, alarmId: Int) {
        val ctx = context.applicationContext
        cancelAlarm(ctx, ctx.getSystemService(AlarmManager::class.java), alarmId)
        AlarmNotifications.cancelFor(ctx, alarmId)
    }

    /** False when Android won't let us set exact alarms (they'd still ring, but maybe late). */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun schedule(ctx: Context, am: AlarmManager, id: Int, at: Long) {
        val fire = AlarmReceiver.fireIntent(ctx, id)
        if (canScheduleExact(ctx)) {
            try {
                // setAlarmClock: exact, wakes the phone from Doze, and shows the alarm icon.
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, AlarmNotifications.openAlarms(ctx)), fire)
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "Exact alarm refused", e)
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
    }

    private fun cancelAlarm(ctx: Context, am: AlarmManager, id: Int) {
        am.cancel(AlarmReceiver.fireIntent(ctx, id))
    }
}
