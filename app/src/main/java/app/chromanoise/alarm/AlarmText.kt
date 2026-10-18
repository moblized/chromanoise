package app.chromanoise.alarm

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.ceil

/** Plain-text helpers that don't need Android (so they're unit-tested). */
object AlarmText {
    /** The 7 days in the order this locale's calendar shows them (Sunday first in the US). */
    fun weekOrder(locale: Locale): List<DayOfWeek> {
        val first = WeekFields.of(locale).firstDayOfWeek
        return (0L until 7L).map { first.plus(it) }
    }

    /** "Once", "Every day", "Weekdays", "Weekends", or e.g. "Mon, Wed, Fri". */
    fun describeDays(days: Int, locale: Locale): String = when (days) {
        0 -> "Once"
        Alarm.EVERY_DAY -> "Every day"
        Alarm.WEEKDAYS -> "Weekdays"
        Alarm.WEEKENDS -> "Weekends"
        else -> weekOrder(locale)
            .filter { days and Alarm.dayBit(it) != 0 }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }

    /** "9 h 40 min", "1 d 2 h", "25 min"… rounded up to the minute, as alarm clocks do. */
    fun duration(ms: Long): String {
        if (ms < 60_000) return "less than a minute"
        val totalMin = ceil(ms / 60_000.0).toLong()
        val d = totalMin / 1440
        val h = totalMin % 1440 / 60
        val m = totalMin % 60
        return buildList {
            if (d > 0) add("$d d")
            if (h > 0) add("$h h")
            if (m > 0 && d == 0L) add("$m min")
        }.joinToString(" ")
    }
}
