package app.chromanoise.clock

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Timer-finished alarms and the notification buttons. Not reachable by other apps. */
class ClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TIMER_FIRE -> TimerControl.onFired(context)
            TIMER_PAUSE -> TimerControl.pause(context)
            TIMER_RESUME -> TimerControl.resume(context)
            TIMER_ADD -> TimerControl.addMinute(context)
            TIMER_CANCEL -> TimerControl.cancel(context)
            STOPWATCH_PAUSE -> StopwatchControl.pause(context)
            STOPWATCH_RESUME -> StopwatchControl.start(context)
            STOPWATCH_LAP -> StopwatchControl.lap(context)
            STOPWATCH_RESET -> StopwatchControl.reset(context)
        }
    }

    companion object {
        const val TIMER_FIRE = "app.chromanoise.clock.TIMER_FIRE"
        const val TIMER_PAUSE = "app.chromanoise.clock.TIMER_PAUSE"
        const val TIMER_RESUME = "app.chromanoise.clock.TIMER_RESUME"
        const val TIMER_ADD = "app.chromanoise.clock.TIMER_ADD"
        const val TIMER_CANCEL = "app.chromanoise.clock.TIMER_CANCEL"
        const val STOPWATCH_PAUSE = "app.chromanoise.clock.SW_PAUSE"
        const val STOPWATCH_RESUME = "app.chromanoise.clock.SW_RESUME"
        const val STOPWATCH_LAP = "app.chromanoise.clock.SW_LAP"
        const val STOPWATCH_RESET = "app.chromanoise.clock.SW_RESET"

        fun intent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, ClockReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
