package app.chromanoise.alarm

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * All alarms, saved on disk and shared by the UI, receivers and the ringing service.
 *
 * Uses device-protected storage, which is readable straight after a reboot even before the
 * phone is unlocked — so an alarm still rings if the phone restarted overnight.
 */
class AlarmStore private constructor(context: Context) {
    private val prefs: SharedPreferences = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("alarms", Context.MODE_PRIVATE)

    private val _alarms = MutableStateFlow(load())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    fun find(id: Int): Alarm? = _alarms.value.firstOrNull { it.id == id }

    /** An id no existing alarm uses. */
    @Synchronized
    fun newId(): Int = (_alarms.value.maxOfOrNull { it.id } ?: 0) + 1

    @Synchronized
    fun put(alarm: Alarm) = save(_alarms.value.filterNot { it.id == alarm.id } + alarm)

    @Synchronized
    fun update(id: Int, change: (Alarm) -> Alarm) =
        save(_alarms.value.map { if (it.id == id) change(it) else it })

    @Synchronized
    fun delete(id: Int) = save(_alarms.value.filterNot { it.id == id })

    @Synchronized
    fun replaceAll(alarms: List<Alarm>) = save(alarms)

    private fun save(list: List<Alarm>) {
        val sorted = list.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
        if (sorted == _alarms.value) return
        _alarms.value = sorted
        val arr = JSONArray()
        sorted.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<Alarm> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            .sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
    }.getOrDefault(emptyList())

    private fun toJson(a: Alarm) = JSONObject()
        .put("id", a.id)
        .put("hour", a.hour)
        .put("minute", a.minute)
        .put("days", a.days)
        .put("enabled", a.enabled)
        .put("label", a.label)
        .put("soundUri", a.soundUri ?: "")
        .put("soundName", a.soundName)
        .put("vibrate", a.vibrate)
        .put("vibration", a.vibration.name)
        .put("gentle", a.gentleWake)
        .put("snooze", a.snoozeMinutes)
        .put("flip", a.flipToSnooze)
        .put("snoozedUntil", a.snoozedUntil ?: 0L)
        .put("nextAt", a.nextAt ?: 0L)

    private fun fromJson(o: JSONObject) = Alarm(
        id = o.getInt("id"),
        hour = o.getInt("hour").coerceIn(0, 23),
        minute = o.getInt("minute").coerceIn(0, 59),
        days = o.optInt("days", 0) and Alarm.EVERY_DAY,
        enabled = o.optBoolean("enabled", true),
        label = o.optString("label", ""),
        soundUri = o.optString("soundUri", "").ifEmpty { null },
        soundName = o.optString("soundName", Alarm.DEFAULT_SOUND_NAME),
        vibrate = o.optBoolean("vibrate", true),
        vibration = VibrationPattern.fromName(o.optString("vibration", "")),
        gentleWake = o.optBoolean("gentle", true),
        snoozeMinutes = o.optInt("snooze", 10).coerceIn(1, 60),
        flipToSnooze = o.optBoolean("flip", true),
        snoozedUntil = o.optLong("snoozedUntil", 0L).takeIf { it > 0 },
        nextAt = o.optLong("nextAt", 0L).takeIf { it > 0 },
    )

    companion object {
        private const val KEY = "alarms"

        @Volatile private var instance: AlarmStore? = null

        fun get(context: Context): AlarmStore =
            instance ?: synchronized(this) {
                instance ?: AlarmStore(context.applicationContext).also { instance = it }
            }
    }
}
