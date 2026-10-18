package app.chromanoise.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.chromanoise.alarm.Alarm
import app.chromanoise.alarm.AlarmScheduler
import app.chromanoise.alarm.AlarmStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlarmsViewModel(app: Application) : AndroidViewModel(app) {
    private val store = AlarmStore.get(app)
    val alarms: StateFlow<List<Alarm>> = store.alarms

    private val _editing = MutableStateFlow<Alarm?>(null)
    /** The alarm open in the editor (new or existing), or null when the list is showing. */
    val editing: StateFlow<Alarm?> = _editing.asStateFlow()

    init {
        // Re-arm everything on launch, in case Android dropped our alarms (e.g. after a force stop).
        AlarmScheduler.sync(app)
    }

    fun isNew(alarm: Alarm): Boolean = store.find(alarm.id) == null

    fun newAlarm() {
        _editing.value = Alarm(id = store.newId(), hour = 7, minute = 0)
    }

    fun edit(alarm: Alarm) {
        _editing.value = alarm
    }

    fun closeEditor() {
        _editing.value = null
    }

    /** Saves the alarm, switches it on, and returns when it will ring. */
    fun save(alarm: Alarm): Long? {
        store.put(alarm.copy(enabled = true, snoozedUntil = null, nextAt = null))
        AlarmScheduler.sync(getApplication<Application>())
        _editing.value = null
        return store.find(alarm.id)?.nextAt
    }

    /** Turns an alarm on or off (off also cancels a snooze). Returns when it will ring, if on. */
    fun setOn(alarm: Alarm, on: Boolean): Long? {
        store.update(alarm.id) { it.copy(enabled = on, snoozedUntil = null, nextAt = null) }
        AlarmScheduler.sync(getApplication<Application>())
        return store.find(alarm.id)?.nextAt
    }

    fun delete(alarm: Alarm) {
        AlarmScheduler.cancel(getApplication<Application>(), alarm.id)
        store.delete(alarm.id)
        _editing.value = null
    }

    /** Re-checks every alarm (cheap); run when the app comes back to the foreground. */
    fun resync() = AlarmScheduler.sync(getApplication<Application>())
}
