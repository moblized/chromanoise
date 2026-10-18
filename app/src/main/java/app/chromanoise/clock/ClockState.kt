package app.chromanoise.clock

/*
 * Timer and stopwatch state as plain values. Every time is in milliseconds on a monotonic
 * clock (SystemClock.elapsedRealtime on the phone), passed in as `now`, so the maths is
 * unit-tested without a phone and isn't thrown off if the wall clock changes.
 */

data class TimerState(
    /** Total length, including any minutes added. */
    val durationMs: Long = 0,
    /** Time left while paused. */
    val remainingMs: Long = 0,
    /** When it finishes, while running; null when paused or idle. */
    val endsAt: Long? = null,
) {
    val running: Boolean get() = endsAt != null
    val paused: Boolean get() = endsAt == null && remainingMs > 0
    val idle: Boolean get() = !running && !paused

    fun remaining(now: Long): Long = endsAt?.let { (it - now).coerceAtLeast(0) } ?: remainingMs

    /** 1 at the start, 0 when done — for the progress ring. */
    fun fractionLeft(now: Long): Float =
        if (durationMs <= 0) 0f else (remaining(now).toFloat() / durationMs).coerceIn(0f, 1f)

    fun pause(now: Long): TimerState = if (!running) this else copy(remainingMs = remaining(now), endsAt = null)

    fun resume(now: Long): TimerState = if (!paused) this else copy(endsAt = now + remainingMs)

    fun add(ms: Long, now: Long): TimerState = when {
        running -> copy(durationMs = durationMs + ms, endsAt = endsAt!! + ms)
        paused -> copy(durationMs = durationMs + ms, remainingMs = remainingMs + ms)
        else -> start(ms, now)
    }

    /** Shifts every timestamp by [deltaMs] (used to carry a timer across a reboot). */
    fun shifted(deltaMs: Long): TimerState = copy(endsAt = endsAt?.plus(deltaMs))

    companion object {
        fun start(durationMs: Long, now: Long) = TimerState(durationMs, durationMs, now + durationMs)
    }
}

data class StopwatchState(
    /** Time banked from earlier runs (before the last pause). */
    val accumulatedMs: Long = 0,
    /** When the current run started; null while paused. */
    val runningSince: Long? = null,
    /** Total elapsed time at each press of Lap. */
    val lapMarks: List<Long> = emptyList(),
) {
    val running: Boolean get() = runningSince != null
    val idle: Boolean get() = !running && accumulatedMs == 0L

    fun elapsed(now: Long): Long = accumulatedMs + (runningSince?.let { now - it } ?: 0)

    fun start(now: Long): StopwatchState = if (running) this else copy(runningSince = now)

    fun pause(now: Long): StopwatchState =
        if (!running) this else copy(accumulatedMs = elapsed(now), runningSince = null)

    fun lap(now: Long): StopwatchState =
        if (!running || lapMarks.size >= MAX_LAPS) this else copy(lapMarks = lapMarks + elapsed(now))

    /** Each lap's own length, oldest first. */
    fun lapTimes(): List<Long> = lapMarks.mapIndexed { i, mark -> mark - (if (i == 0) 0 else lapMarks[i - 1]) }

    fun shifted(deltaMs: Long): StopwatchState = copy(runningSince = runningSince?.plus(deltaMs))

    companion object {
        const val MAX_LAPS = 999
    }
}

object ClockText {
    /** Stopwatch style: "02:03.45", or "1:02:03.45" past an hour. */
    fun stopwatch(ms: Long): String {
        val cs = ms.coerceAtLeast(0) / 10
        val h = cs / 360_000
        val m = cs / 6_000 % 60
        val s = cs / 100 % 60
        val c = cs % 100
        return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, c) else "%02d:%02d.%02d".format(m, s, c)
    }

    /** Timer style, rounded up so it reads 5:00 at the start and 0:01 in the last second. */
    fun timer(ms: Long): String {
        val total = (ms.coerceAtLeast(0) + 999) / 1000
        val h = total / 3600
        val m = total / 60 % 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** Plain clock style, whole seconds, rounded down: "0:05", "12:34", "1:02:03". */
    fun clock(ms: Long): String {
        val total = ms.coerceAtLeast(0) / 1000
        val h = total / 3600
        val m = total / 60 % 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** "5 min", "1 h 30 min", "45 s" — for labels. */
    fun shortDuration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = total / 60 % 60
        val s = total % 60
        return buildList {
            if (h > 0) add("$h h")
            if (m > 0) add("$m min")
            if (s > 0 || (h == 0L && m == 0L)) add("$s s")
        }.joinToString(" ")
    }
}

/**
 * The timer's keypad entry. Digits fill from the right like a microwave: typing 1, 3, 0
 * gives 1 min 30 s. Up to six digits (hh mm ss); 90 in the seconds place means 1:30.
 */
object TimerInput {
    const val MAX_DIGITS = 6

    fun type(digits: String, key: String): String = (digits + key).trimStart('0').take(MAX_DIGITS)

    fun backspace(digits: String): String = digits.dropLast(1)

    /** (hours, minutes, seconds) as typed, for display. */
    fun parts(digits: String): Triple<Int, Int, Int> {
        val p = digits.padStart(MAX_DIGITS, '0')
        return Triple(p.substring(0, 2).toInt(), p.substring(2, 4).toInt(), p.substring(4, 6).toInt())
    }

    fun toMillis(digits: String): Long {
        val (h, m, s) = parts(digits)
        return ((h * 3600L) + (m * 60L) + s) * 1000
    }
}
