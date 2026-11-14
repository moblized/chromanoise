package app.chromanoise.playback

import android.content.Context
import app.chromanoise.audio.NoiseEngine
import app.chromanoise.audio.Presets
import app.chromanoise.data.Prefs
import app.chromanoise.data.TriggerStore

/**
 * Starts noise the way the saved trigger settings say to — the shared path for both the
 * NFC-tag tap ([PlayTriggerActivity]) and the nightly schedule ([NoiseScheduleReceiver]).
 *
 * Resolves the spectrum before starting the service, so it works even from a cold process
 * where [NoiseEngine] hasn't been primed yet. Overrides let a caller force a preset/timer;
 * otherwise the saved [TriggerStore] settings decide.
 */
object TriggerStarter {
    fun start(context: Context, presetOverride: String? = null, minutesOverride: Int? = null) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        val saved = TriggerStore.get(app).settings.value

        val presetName = (presetOverride ?: saved.preset).trim()
        val preset = presetName.takeIf { it.isNotEmpty() }?.let { name ->
            (Presets.builtIn + prefs.savedPresets).firstOrNull { it.name.equals(name, ignoreCase = true) }
        }

        val bands = preset?.bands ?: prefs.bands ?: Presets.default.bands
        val label = preset?.name ?: prefs.presetName ?: Presets.default.name

        NoiseEngine.setSpectrum(bands.toFloatArray())
        NoiseEngine.setVolume(prefs.volume)
        NoiseEngine.setLabel(label)

        NoisePlaybackService.play(app)

        val minutes = minutesOverride ?: saved.autoOffMinutes
        NoiseEngine.setTimer(if (minutes > 0) minutes else null)
    }
}
