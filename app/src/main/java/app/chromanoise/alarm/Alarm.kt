package app.chromanoise.alarm

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One alarm. Pure data plus the "when does it ring next" maths, so it can be unit-tested
 * without a phone.
 */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    /** Repeat days as bits: Monday = bit 0 … Sunday = bit 6. 0 means it rings once. */
    val days: Int = 0,
    val enabled: Boolean = true,
    val label: String = "",
    /** A phone ringtone URI, [SILENT_SOUND] for vibrate-only, or null for the built-in soft chimes. */
    val soundUri: String? = null,
    val soundName: String = DEFAULT_SOUND_NAME,
    val vibrate: Boolean = true,
    val vibration: VibrationPattern = VibrationPattern.PULSE,
    /** Fade the sound in over [GENTLE_FADE_MS] instead of starting at full volume. */
    val gentleWake: Boolean = true,
    val snoozeMinutes: Int = 10,
    /** Turning the phone face down while it rings snoozes it. */
    val flipToSnooze: Boolean = true,
    /** Wall-clock ms when a snoozed alarm rings again, or null. */
    val snoozedUntil: Long? = null,
    /** Wall-clock ms this alarm is currently scheduled for (kept up to date by AlarmScheduler). */
    val nextAt: Long? = null,
) {
    val repeats: Boolean get() = days != 0

    /** Vibrate-only alarm: no sound at all. */
    val silent: Boolean get() = soundUri == SILENT_SOUND

    /** A silent alarm always vibrates — otherwise it would do nothing. */
    val vibrates: Boolean get() = vibrate || silent

    /** What the on/off switch shows: a once-alarm that's been snoozed is still "on". */
    val isOn: Boolean get() = enabled || snoozedUntil != null

    fun repeatsOn(day: DayOfWeek): Boolean = days and dayBit(day) != 0

    fun withDay(day: DayOfWeek, on: Boolean): Alarm =
        copy(days = if (on) days or dayBit(day) else days and dayBit(day).inv())

    /** The first time strictly after [now] that matches the alarm's time and repeat days. */
    fun nextOccurrence(now: ZonedDateTime): ZonedDateTime {
        val time = LocalTime.of(hour, minute)
        for (offset in 0L..7L) {
            val date = now.toLocalDate().plusDays(offset)
            if (repeats && !repeatsOn(date.dayOfWeek)) continue
            // In a spring-forward gap (e.g. 2:30 that night) this lands on the shifted time, 3:30.
            val candidate = ZonedDateTime.of(date, time, now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        error("No occurrence within a week for days=$days")
    }

    /** When this alarm should next go off, or null if it's off. A pending snooze wins. */
    fun nextTrigger(nowMillis: Long, zone: ZoneId): Long? {
        val snooze = snoozedUntil
        if (snooze != null && snooze > nowMillis) return snooze
        if (!enabled) return null
        return nextOccurrence(Instant.ofEpochMilli(nowMillis).atZone(zone)).toInstant().toEpochMilli()
    }

    /** State after it has rung: snooze used up, and a one-time alarm switches itself off. */
    fun afterFiring(): Alarm = copy(enabled = enabled && repeats, snoozedUntil = null, nextAt = null)

    companion object {
        const val DEFAULT_SOUND_NAME = "Soft chimes"
        const val SILENT_SOUND = "silent"
        const val SILENT_SOUND_NAME = "No sound (vibrate only)"
        const val GENTLE_FADE_MS = 60_000L
        const val EVERY_DAY = 0b1111111
        const val WEEKDAYS = 0b0011111
        const val WEEKENDS = 0b1100000

        fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    }
}
