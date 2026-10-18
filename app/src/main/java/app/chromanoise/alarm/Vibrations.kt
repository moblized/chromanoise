package app.chromanoise.alarm

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Plays [VibrationPattern]s on the phone's vibrator, as alarm vibrations (so silent mode doesn't block them). */
object Vibrations {
    /** The phone's vibrator, or null if it hasn't got one. */
    fun vibrator(context: Context): Vibrator? {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        return v?.takeIf { it.hasVibrator() }
    }

    fun hasVibrator(context: Context): Boolean = vibrator(context) != null

    /** Starts [waveform]; returns the vibrator so the caller can cancel it, or null if there's none. */
    fun play(context: Context, waveform: Waveform): Vibrator? {
        val v = vibrator(context) ?: return null
        val effect = VibrationEffect.createWaveform(waveform.timings, waveform.amplitudes, waveform.repeatFrom)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
        return v
    }

    fun preview(context: Context, pattern: VibrationPattern) {
        play(context, pattern.preview())
    }
}
