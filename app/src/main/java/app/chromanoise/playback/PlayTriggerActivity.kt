package app.chromanoise.playback

import android.app.Activity
import android.os.Bundle

/**
 * No-UI entry point so another app on the phone (e.g. the bedside NFC-tag app) can start
 * noise without the user opening ChromaNoise.
 *
 * By default it plays whatever the in-app trigger settings say (preset + auto-off timer).
 * A caller may override those with intent extras, but the normal flow is to set everything
 * inside ChromaNoise and just launch this with no extras.
 *
 * It's an activity (not a service/broadcast) so the foreground-service start counts as
 * user-visible and isn't blocked by background-start rules. It finishes at once.
 *
 * Intent (explicit component `app.chromanoise/.playback.PlayTriggerActivity`):
 *   extra PRESET          (String, optional) override the saved preset for this start
 *   extra TIMER_MINUTES   (Int, optional)    override the saved auto-off timer for this start
 */
class PlayTriggerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preset = intent?.getStringExtra(EXTRA_PRESET)
        val minutes = intent?.getIntExtra(EXTRA_TIMER_MINUTES, -1)?.takeIf { it >= 0 }

        TriggerStarter.start(this, presetOverride = preset, minutesOverride = minutes)
        finish()
    }

    companion object {
        const val EXTRA_PRESET = "app.chromanoise.extra.PRESET"
        const val EXTRA_TIMER_MINUTES = "app.chromanoise.extra.TIMER_MINUTES"
    }
}
