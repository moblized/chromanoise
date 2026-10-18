package app.chromanoise.clock

import android.app.AlarmManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import app.chromanoise.MainActivity
import app.chromanoise.alarm.AlarmNotifications
import app.chromanoise.alarm.AlarmRingService
import app.chromanoise.alarm.AlarmScheduler

/** Every timer change goes through here: update state, (re)schedule the alarm, refresh the notification. */
object TimerControl {
    private const val TAG = "TimerControl"

    fun start(context: Context, durationMs: Long) {
        if (durationMs <= 0) return
        ClockStore.get(context).lastTimerMs = durationMs
        update(context) { _, now -> TimerState.start(durationMs, now) }
    }

    fun pause(context: Context) = update(context) { t, now -> t.pause(now) }

    fun resume(context: Context) = update(context) { t, now -> t.resume(now) }

    fun addMinute(context: Context) = update(context) { t, now -> t.add(60_000L, now) }

    fun cancel(context: Context) = update(context) { _, _ -> TimerState() }

    /** AlarmManager says time's up. */
    fun onFired(context: Context) {
        val store = ClockStore.get(context)
        val t = store.timer.value
        val now = SystemClock.elapsedRealtime()
        if (!t.running) return
        if (t.remaining(now) > 1_500) {
            sync(context) // woke early (wall clock moved): set it again
            return
        }
        store.setTimer(TimerState())
        sync(context)
        // When it actually ran out, on the wall clock (we may be a moment late).
        val endedAt = System.currentTimeMillis() - (now - (t.endsAt ?: now)).coerceAtLeast(0)
        AlarmRingService.holdStartupWakeLock(context)
        try {
            AlarmRingService.ringTimer(context, t.durationMs, endedAt)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't start the timer alarm", e)
            AlarmNotifications.postFallback(context, "Time's up")
            AlarmRingService.releaseStartupWakeLock()
        }
    }

    /** Makes AlarmManager and the notification match the saved state. Safe to call any time. */
    fun sync(context: Context) {
        val ctx = context.applicationContext
        val t = ClockStore.get(ctx).timer.value
        val am = ctx.getSystemService(AlarmManager::class.java)
        val fire = ClockReceiver.intent(ctx, ClockReceiver.TIMER_FIRE)
        val endsAt = t.endsAt
        if (endsAt == null) {
            am.cancel(fire)
        } else {
            // setAlarmClock is the one kind of alarm Android never delays, even in deep sleep.
            val wallEnd = System.currentTimeMillis() + (endsAt - SystemClock.elapsedRealtime())
            if (AlarmScheduler.canScheduleExact(ctx)) {
                try {
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(wallEnd, AlarmNotifications.openTab(ctx, MainActivity.TAB_TIMER)), fire)
                } catch (e: SecurityException) {
                    am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endsAt, fire)
                }
            } else {
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, endsAt, fire)
            }
        }
        ClockNotifications.showTimer(ctx, t)
    }

    private fun update(context: Context, change: (TimerState, Long) -> TimerState) {
        val store = ClockStore.get(context)
        store.setTimer(change(store.timer.value, SystemClock.elapsedRealtime()))
        sync(context)
    }
}

object StopwatchControl {
    fun start(context: Context) = update(context) { s, now -> s.start(now) }

    fun pause(context: Context) = update(context) { s, now -> s.pause(now) }

    fun lap(context: Context) = update(context) { s, now -> s.lap(now) }

    fun reset(context: Context) = update(context) { _, _ -> StopwatchState() }

    /** Re-shows the notification (e.g. after a reboot). */
    fun sync(context: Context) = ClockNotifications.showStopwatch(context.applicationContext, ClockStore.get(context).stopwatch.value)

    private fun update(context: Context, change: (StopwatchState, Long) -> StopwatchState) {
        val store = ClockStore.get(context)
        store.setStopwatch(change(store.stopwatch.value, SystemClock.elapsedRealtime()))
        sync(context)
    }
}
