package app.chromanoise.playback

import android.app.Activity
import android.os.Bundle
import app.chromanoise.audio.NoiseEngine
import app.chromanoise.audio.Presets
import app.chromanoise.data.Prefs

/**
 * No-UI entry point so another app on the phone (e.g. the bedside NFC-tag app) can start
 * noise without the user opening ChromaNoise.
 *
 * It loads the last-used spectrum — or one named in the intent — primes [NoiseEngine],
 * starts [NoisePlaybackService], optionally arms a sleep timer, then finishes at once.
 * It's launched as an activity (not a service/broadcast) so the foreground-service start
 * counts as user-visible and isn't blocked by background-start rules.
 *
 * Intent (explicit component `app.chromanoise/.playback.PlayTriggerActivity`):
 *   extra PRESET          (String, optional) a built-in or saved preset name; else last used
 *   extra TIMER_MINUTES   (Int, optional)    sleep-timer length; 0 or absent means none
 */
class PlayTriggerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)

        val requested = intent?.getStringExtra(EXTRA_PRESET)?.trim()?.takeIf { it.isNotEmpty() }
        val preset = requested?.let { name ->
            (Presets.builtIn + prefs.savedPresets).firstOrNull { it.name.equals(name, ignoreCase = true) }
        }

        val bands = preset?.bands ?: prefs.bands ?: Presets.default.bands
        val label = preset?.name ?: prefs.presetName ?: Presets.default.name

        NoiseEngine.setSpectrum(bands.toFloatArray())
        NoiseEngine.setVolume(prefs.volume)
        NoiseEngine.setLabel(label)

        NoisePlaybackService.play(this)

        val minutes = intent?.getIntExtra(EXTRA_TIMER_MINUTES, 0) ?: 0
        if (minutes > 0) NoiseEngine.setTimer(minutes)

        finish()
    }

    companion object {
        const val EXTRA_PRESET = "app.chromanoise.extra.PRESET"
        const val EXTRA_TIMER_MINUTES = "app.chromanoise.extra.TIMER_MINUTES"
    }
}
