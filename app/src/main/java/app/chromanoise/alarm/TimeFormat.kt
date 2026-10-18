package app.chromanoise.alarm

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Clock times in the phone's own 12- or 24-hour style. */
object TimeFormat {
    fun is24h(context: Context): Boolean = DateFormat.is24HourFormat(context)

    /** "7:30 AM" or "07:30". */
    fun time(context: Context, hour: Int, minute: Int): String {
        val (main, suffix) = timeParts(context, hour, minute)
        return if (suffix == null) main else "$main $suffix"
    }

    fun time(context: Context, millis: Long): String {
        val t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime()
        return time(context, t.hour, t.minute)
    }

    /** ("7:30", "AM") in 12-hour mode, ("07:30", null) in 24-hour mode. */
    fun timeParts(context: Context, hour: Int, minute: Int): Pair<String, String?> {
        val t = LocalTime.of(hour, minute)
        val locale = Locale.getDefault()
        return if (is24h(context)) {
            t.format(DateTimeFormatter.ofPattern("HH:mm", locale)) to null
        } else {
            t.format(DateTimeFormatter.ofPattern("h:mm", locale)) to
                t.format(DateTimeFormatter.ofPattern("a", locale))
        }
    }

    /** "Today 7:30 AM", "Tomorrow 7:30 AM" or "Sat 7:30 AM". */
    fun dayAndTime(context: Context, millis: Long): String {
        val zone = ZoneId.systemDefault()
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = LocalDate.now(zone)
        val day = when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }
        return "$day ${time(context, millis)}"
    }
}
