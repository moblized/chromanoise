package app.chromanoise.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * Lets another of your apps ring a Chroma Noise alarm right now. TempWatch uses it when a
 * cook is done: "Brisket is ready" rings full screen, with the same sound, vibration and
 * flip-to-stop as a finished timer.
 *
 * Ring: send [ACTION_RING] to [ExternalAlarmReceiver] with
 *  - [EXTRA_TITLE]       big line on the alarm screen, e.g. "Brisket is ready"
 *  - [EXTRA_DETAIL]      line under the clock, e.g. "203°F · target 203°F"
 *  - [EXTRA_ON_STOP]     a PendingIntent of yours, required. Sent when someone presses Stop here
 *                        (or flips the phone), so your app can stop too. Its creator is how we
 *                        know who is asking; only apps in [CALLERS] are listened to.
 *  - [EXTRA_ON_RINGING]  optional PendingIntent, sent once the alarm is actually ringing, so your
 *                        app can ring by itself if that never comes.
 * Stop: send [ACTION_STOP] with your [EXTRA_ON_STOP]. Stops the ring without sending it back.
 */
object ExternalAlarm {
    const val ACTION_RING = "app.chromanoise.action.RING_EXTERNAL"
    const val ACTION_STOP = "app.chromanoise.action.STOP_EXTERNAL"
    const val EXTRA_TITLE = "app.chromanoise.extra.TITLE"
    const val EXTRA_DETAIL = "app.chromanoise.extra.DETAIL"
    const val EXTRA_ON_STOP = "app.chromanoise.extra.ON_STOP"
    const val EXTRA_ON_RINGING = "app.chromanoise.extra.ON_RINGING"
    internal const val EXTRA_CALLER = "app.chromanoise.extra.CALLER"

    /** Apps that may ring, and the name shown on the alarm screen. */
    private val CALLERS = mapOf("app.tempwatch" to "TempWatch")

    /** Alarm id used for the ring and its one-second alarm; real alarms are ≥ 0, a timer is −1. */
    const val RING_ID = -2

    private const val TAG = "ExternalAlarm"

    fun callerName(pkg: String?): String? = pkg?.let { CALLERS[it] }

    /**
     * Rings one second from now through setAlarmClock. That alarm firing is what lets Android
     * start the ringing service while Chroma Noise is in the background.
     */
    fun schedule(context: Context, request: Bundle) {
        val ctx = context.applicationContext
        val fire = AlarmReceiver.fireExternalIntent(ctx, request)
        val at = System.currentTimeMillis() + 1_000
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            if (!AlarmScheduler.canScheduleExact(ctx)) throw SecurityException("exact alarms not allowed")
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, AlarmNotifications.openAlarms(ctx)), fire)
        } catch (e: SecurityException) {
            // No exact alarms: try ringing directly, and make a noise some other way if that's refused too.
            Log.w(TAG, "Exact alarm refused, ringing directly", e)
            ring(ctx, request)
        }
    }

    /** Called from the one-second alarm (or directly as a fallback). */
    fun ring(context: Context, request: Bundle) {
        AlarmRingService.holdStartupWakeLock(context)
        try {
            AlarmRingService.ringExternal(context, request)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't start the alarm service", e)
            AlarmNotifications.postFallback(context, request.getString(EXTRA_TITLE)?.ifBlank { null } ?: "Alarm")
            AlarmRingService.releaseStartupWakeLock()
        }
    }

    /** The calling app stopped on its side: cancel a ring that hasn't started, or silence it. */
    fun stop(context: Context, caller: String) {
        val ctx = context.applicationContext
        ctx.getSystemService(AlarmManager::class.java).cancel(AlarmReceiver.fireExternalIntent(ctx, null))
        AlarmRingService.dismissExternal(ctx, caller)
    }
}

/** The one door other apps can use (see [ExternalAlarm]). */
class ExternalAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val onStop = intent.pendingIntentExtra(ExternalAlarm.EXTRA_ON_STOP)
        val caller = onStop?.creatorPackage
        if (ExternalAlarm.callerName(caller) == null) {
            Log.w("ExternalAlarm", "Ignored ${intent.action} from ${caller ?: "an unknown app"}")
            return
        }
        when (intent.action) {
            ExternalAlarm.ACTION_RING -> ExternalAlarm.schedule(
                context,
                Bundle().apply {
                    putString(ExternalAlarm.EXTRA_TITLE, intent.getStringExtra(ExternalAlarm.EXTRA_TITLE)?.take(80).orEmpty())
                    putString(ExternalAlarm.EXTRA_DETAIL, intent.getStringExtra(ExternalAlarm.EXTRA_DETAIL)?.take(120).orEmpty())
                    putString(ExternalAlarm.EXTRA_CALLER, caller)
                    putParcelable(ExternalAlarm.EXTRA_ON_STOP, onStop)
                    putParcelable(ExternalAlarm.EXTRA_ON_RINGING, intent.pendingIntentExtra(ExternalAlarm.EXTRA_ON_RINGING))
                },
            )
            ExternalAlarm.ACTION_STOP -> ExternalAlarm.stop(context, caller!!)
        }
    }
}

// Typed getters only from Android 14: Android 13's could crash on some extras.
internal fun Intent.pendingIntentExtra(key: String): PendingIntent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        getParcelableExtra(key, PendingIntent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(key)
    }

internal fun Bundle.pendingIntent(key: String): PendingIntent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        getParcelable(key, PendingIntent::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelable(key)
    }
