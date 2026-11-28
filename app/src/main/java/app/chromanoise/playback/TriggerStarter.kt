package app.chromanoise.playback

import android.content.Context
import android.util.Log
import app.chromanoise.audio.NoiseEngine
import app.chromanoise.audio.Presets
import app.chromanoise.data.Prefs
import app.chromanoise.data.TriggerStore

/**
 * Starts noise the way the saved Auto-start settings say to — the shared path for the
 * nightly schedule ([NoiseScheduleReceiver]), the bedside charger ([ChargerWatchService])
 * and other apps ([PlayTriggerActivity]).
 *
 * Resolves the spectrum before starting the service, so it works even from a cold process
 * where [NoiseEngine] hasn't been primed yet. Overrides let a caller force a preset/timer;
 * otherwise the saved [TriggerStore] settings decide.
 */
object TriggerStarter {
    /**
     * @param playInProcessIfBlocked for callers that are themselves a running foreground service
     *   (the charger watcher): if Android won't let us start the playback service from the
     *   background, play directly in this process instead — the caller's service keeps it alive.
     */
    fun start(
        context: Context,
        presetOverride: String? = null,
        minutesOverride: Int? = null,
        playInProcessIfBlocked: Boolean = false,
    ) {
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

        try {
            NoisePlaybackService.play(app)
        } catch (e: Exception) {
            if (!playInProcessIfBlocked) throw e
            Log.w("TriggerStarter", "Playback service blocked; playing in-process", e)
            NoiseEngine.play()
        }

        val minutes = minutesOverride ?: saved.autoOffMinutes
        NoiseEngine.setTimer(if (minutes > 0) minutes else null)
    }
}
