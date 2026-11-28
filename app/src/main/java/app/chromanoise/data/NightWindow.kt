package app.chromanoise.data

/**
 * A daily time window in minutes after midnight, like 20:00–08:00 for "at night".
 * Pure logic, unit-tested; used to decide whether putting the phone on the charger should
 * start the noise.
 */
object NightWindow {
    /**
     * True if [minuteOfDay] is inside [from, to). The window may run past midnight
     * (from 20:00 to 08:00); from == to means the whole day.
     */
    fun contains(minuteOfDay: Int, from: Int, to: Int): Boolean = when {
        from == to -> true
        from < to -> minuteOfDay in from until to
        else -> minuteOfDay >= from || minuteOfDay < to
    }
}
