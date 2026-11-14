package app.chromanoise.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the NFC-tag trigger and the nightly auto-start should play, and when.
 *
 * @param preset         a built-in or saved preset name; empty means "whatever played last"
 * @param autoOffMinutes stop the noise after this many minutes once it starts; 0 means never
 * @param scheduleEnabled start the noise on its own every night
 * @param hour,minute    local time of the nightly start (24-hour)
 */
data class TriggerSettings(
    val preset: String = "",
    val autoOffMinutes: Int = 0,
    val scheduleEnabled: Boolean = false,
    val hour: Int = 22,
    val minute: Int = 0,
)

/** Persists [TriggerSettings] and exposes them as a flow for the settings UI. */
class TriggerStore private constructor(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("chroma_trigger", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<TriggerSettings> = _settings

    private fun load() = TriggerSettings(
        preset = sp.getString(KEY_PRESET, "") ?: "",
        autoOffMinutes = sp.getInt(KEY_AUTO_OFF, 0),
        scheduleEnabled = sp.getBoolean(KEY_SCHED, false),
        hour = sp.getInt(KEY_HOUR, 22),
        minute = sp.getInt(KEY_MINUTE, 0),
    )

    fun update(block: (TriggerSettings) -> TriggerSettings) {
        val next = block(_settings.value)
        sp.edit()
            .putString(KEY_PRESET, next.preset)
            .putInt(KEY_AUTO_OFF, next.autoOffMinutes)
            .putBoolean(KEY_SCHED, next.scheduleEnabled)
            .putInt(KEY_HOUR, next.hour)
            .putInt(KEY_MINUTE, next.minute)
            .apply()
        _settings.value = next
    }

    companion object {
        @Volatile private var instance: TriggerStore? = null

        fun get(context: Context): TriggerStore =
            instance ?: synchronized(this) {
                instance ?: TriggerStore(context).also { instance = it }
            }

        private const val KEY_PRESET = "preset"
        private const val KEY_AUTO_OFF = "auto_off_minutes"
        private const val KEY_SCHED = "schedule_enabled"
        private const val KEY_HOUR = "hour"
        private const val KEY_MINUTE = "minute"
    }
}
