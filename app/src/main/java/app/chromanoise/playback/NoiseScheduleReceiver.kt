package app.chromanoise.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Fires at the nightly start time: begins playback with the saved trigger settings, then
 * schedules the next night. Reached only by our own exact alarm, so it isn't exported.
 */
class NoiseScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        try {
            TriggerStarter.start(context)
        } catch (e: Exception) {
            Log.e("NoiseSchedule", "Couldn't start scheduled noise", e)
        } finally {
            TriggerScheduler.sync(context) // arm tomorrow night
        }
    }

    companion object {
        private const val ACTION_FIRE = "app.chromanoise.trigger.FIRE"
        private const val REQUEST_CODE = 7731

        fun fireIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, NoiseScheduleReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
