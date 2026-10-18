package app.chromanoise.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import app.chromanoise.audio.NoiseEngine
import app.chromanoise.audio.Preset
import app.chromanoise.audio.Presets
import app.chromanoise.data.Prefs
import app.chromanoise.playback.NoisePlaybackService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    private val _bands = MutableStateFlow(prefs.bands ?: Presets.default.bands)
    val bands: StateFlow<List<Float>> = _bands.asStateFlow()

    private val _presetName = MutableStateFlow(prefs.presetName ?: Presets.default.name)
    val presetName: StateFlow<String> = _presetName.asStateFlow()

    private val _volume = MutableStateFlow(prefs.volume)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _savedPresets = MutableStateFlow(prefs.savedPresets)
    val savedPresets: StateFlow<List<Preset>> = _savedPresets.asStateFlow()

    private val _timerChoice = MutableStateFlow<Int?>(null)
    /** The last sleep-timer length picked, in minutes (only meaningful while a timer runs). */
    val timerChoice: StateFlow<Int?> = _timerChoice.asStateFlow()

    val isPlaying: StateFlow<Boolean> = NoiseEngine.isPlaying
    val isActive: StateFlow<Boolean> = NoiseEngine.isActive
    val timerEndsAt: StateFlow<Long?> = NoiseEngine.timerEndsAt

    init {
        NoiseEngine.setSpectrum(_bands.value.toFloatArray())
        NoiseEngine.setVolume(_volume.value)
        NoiseEngine.setLabel(_presetName.value)
    }

    /** Called continuously while a finger drags across the spectrum. */
    fun editBands(newBands: List<Float>) {
        _bands.value = newBands
        _presetName.value = CUSTOM
        NoiseEngine.setSpectrum(newBands.toFloatArray())
        NoiseEngine.setLabel(CUSTOM)
    }

    fun finishEdit() {
        prefs.bands = _bands.value
        prefs.presetName = _presetName.value
    }

    fun applyPreset(preset: Preset) {
        _bands.value = preset.bands
        _presetName.value = preset.name
        NoiseEngine.setSpectrum(preset.bands.toFloatArray())
        NoiseEngine.setLabel(preset.name)
        finishEdit()
    }

    fun savePreset(rawName: String) {
        val name = rawName.trim().take(24)
        if (name.isEmpty()) return
        val preset = Preset(name, _bands.value, builtIn = false)
        _savedPresets.value = _savedPresets.value.filterNot { it.name == name } + preset
        prefs.savedPresets = _savedPresets.value
        _presetName.value = name
        NoiseEngine.setLabel(name)
        finishEdit()
    }

    fun deletePreset(preset: Preset) {
        _savedPresets.value = _savedPresets.value.filterNot { it.name == preset.name }
        prefs.savedPresets = _savedPresets.value
        if (_presetName.value == preset.name) {
            _presetName.value = CUSTOM
            NoiseEngine.setLabel(CUSTOM)
            finishEdit()
        }
    }

    fun setVolume(v: Float) {
        _volume.value = v
        NoiseEngine.setVolume(v)
        prefs.volume = v
    }

    fun togglePlay() {
        if (isPlaying.value) NoiseEngine.stop() else NoisePlaybackService.play(getApplication<Application>())
    }

    /** Picking a timer also starts the noise if it isn't playing — there'd be nothing to time otherwise. */
    fun setTimer(minutes: Int?) {
        _timerChoice.value = minutes
        if (minutes != null && !isPlaying.value) NoisePlaybackService.play(getApplication<Application>())
        NoiseEngine.setTimer(minutes)
    }

    companion object {
        const val CUSTOM = "Custom"
    }
}
