package app.chromanoise.clock

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Timer and stopwatch state, saved so they keep going when the app is closed — and across a
 * reboot. Times are on the monotonic clock (elapsedRealtime), which restarts at boot, so on
 * the first load after a reboot every timestamp is shifted using the wall clock.
 * Device-protected storage, so a timer can still ring before the phone is unlocked.
 */
class ClockStore private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("clock", Context.MODE_PRIVATE)

    private val _timer: MutableStateFlow<TimerState>
    private val _stopwatch: MutableStateFlow<StopwatchState>
    val timer: StateFlow<TimerState>
    val stopwatch: StateFlow<StopwatchState>

    /** Length of the last timer started, offered as "Again". */
    var lastTimerMs: Long
        get() = prefs.getLong(KEY_LAST, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST, value).apply()

    init {
        val shift = rebootShift()
        _timer = MutableStateFlow(loadTimer().shifted(shift))
        _stopwatch = MutableStateFlow(loadStopwatch().shifted(shift))
        timer = _timer.asStateFlow()
        stopwatch = _stopwatch.asStateFlow()
        if (shift != 0L) save()
    }

    fun setTimer(t: TimerState) {
        _timer.value = t
        save()
    }

    fun setStopwatch(s: StopwatchState) {
        _stopwatch.value = s
        save()
    }

    private fun save() {
        val t = _timer.value
        val s = _stopwatch.value
        prefs.edit()
            .putLong(KEY_T_DURATION, t.durationMs)
            .putLong(KEY_T_REMAINING, t.remainingMs)
            .putLong(KEY_T_ENDS, t.endsAt ?: NONE)
            .putLong(KEY_S_ACC, s.accumulatedMs)
            .putLong(KEY_S_SINCE, s.runningSince ?: NONE)
            .putString(KEY_S_LAPS, s.lapMarks.joinToString(","))
            .putInt(KEY_BOOT, bootCount())
            .putLong(KEY_OFFSET, wallMinusElapsed())
            .apply()
    }

    private fun loadTimer() = TimerState(
        durationMs = prefs.getLong(KEY_T_DURATION, 0L),
        remainingMs = prefs.getLong(KEY_T_REMAINING, 0L),
        endsAt = prefs.getLong(KEY_T_ENDS, NONE).takeIf { it != NONE },
    )

    private fun loadStopwatch() = StopwatchState(
        accumulatedMs = prefs.getLong(KEY_S_ACC, 0L),
        runningSince = prefs.getLong(KEY_S_SINCE, NONE).takeIf { it != NONE },
        lapMarks = prefs.getString(KEY_S_LAPS, "").orEmpty().split(",").mapNotNull { it.toLongOrNull() },
    )

    /** How far to move saved timestamps if the phone has restarted since they were saved. */
    private fun rebootShift(): Long {
        if (!prefs.contains(KEY_OFFSET)) return 0L
        val savedBoot = prefs.getInt(KEY_BOOT, -1)
        val nowBoot = bootCount()
        val rebooted = if (savedBoot >= 0 && nowBoot >= 0) {
            savedBoot != nowBoot
        } else {
            // No boot counter: a reboot shows up as a big jump in (wall − uptime).
            kotlin.math.abs(prefs.getLong(KEY_OFFSET, 0L) - wallMinusElapsed()) > 60_000
        }
        return if (rebooted) prefs.getLong(KEY_OFFSET, 0L) - wallMinusElapsed() else 0L
    }

    private fun bootCount(): Int =
        runCatching { Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1)

    private fun wallMinusElapsed(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    companion object {
        private const val NONE = Long.MIN_VALUE
        private const val KEY_T_DURATION = "t_duration"
        private const val KEY_T_REMAINING = "t_remaining"
        private const val KEY_T_ENDS = "t_ends"
        private const val KEY_S_ACC = "s_acc"
        private const val KEY_S_SINCE = "s_since"
        private const val KEY_S_LAPS = "s_laps"
        private const val KEY_BOOT = "boot"
        private const val KEY_OFFSET = "wall_minus_elapsed"
        private const val KEY_LAST = "last_timer"

        @Volatile private var instance: ClockStore? = null

        fun get(context: Context): ClockStore =
            instance ?: synchronized(this) {
                instance ?: ClockStore(context.applicationContext).also { instance = it }
            }
    }
}
