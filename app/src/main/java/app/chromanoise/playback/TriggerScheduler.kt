package app.chromanoise.playback

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.util.Log
import app.chromanoise.data.TriggerStore
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Keeps Android's AlarmManager in step with the nightly-start setting in [TriggerStore].
 * [sync] recomputes the next start from scratch, so it's also what runs after a reboot,
 * a clock change or an app update.
 *
 * Uses an exact "allow while idle" alarm: an app holding USE_EXACT_ALARM gets a brief
 * power-allowlist when it fires, which is what lets the receiver start the playback
 * foreground service with the screen off.
 */
object TriggerScheduler {
    private const val TAG = "TriggerScheduler"

    fun sync(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(AlarmManager::class.java)
        val pi = NoiseScheduleReceiver.fireIntent(app)
        val s = TriggerStore.get(app).settings.value

        if (!s.scheduleEnabled) {
            am.cancel(pi)
            return
        }

        val at = nextOccurrence(s.hour, s.minute)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "Exact alarm refused", e)
            }
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun nextOccurrence(hour: Int, minute: Int): Long {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var next = now.withHour(hour.coerceIn(0, 23)).withMinute(minute.coerceIn(0, 59))
            .withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.toInstant().toEpochMilli()
    }
}
