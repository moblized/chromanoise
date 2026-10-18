package app.chromanoise.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.os.Vibrator
import android.util.Log
import kotlin.concurrent.thread
import kotlin.math.pow

/**
 * Plays an alarm on the phone's alarm volume (so it works in silent mode), optionally fading
 * in, and vibrates in the alarm's pattern. If a chosen phone sound can't be played for any
 * reason it falls back to the built-in chimes — an alarm must never be silent by accident.
 */
class AlarmSoundPlayer(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val handler = Handler(Looper.getMainLooper())

    private var focus: AudioFocusRequest? = null
    private var media: MediaPlayer? = null
    private var chimes: ChimePlayer? = null
    private var vibrator: Vibrator? = null
    private var gentle = false
    private var startedAt = 0L

    private val ramp = object : Runnable {
        override fun run() {
            applyVolume()
            if (SystemClock.elapsedRealtime() - startedAt < Alarm.GENTLE_FADE_MS) handler.postDelayed(this, 200)
        }
    }

    fun start(alarm: Alarm) {
        gentle = alarm.gentleWake
        startedAt = SystemClock.elapsedRealtime()
        if (!alarm.silent) {
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .build()
                .also { audio.requestAudioFocus(it) }
            val uri = alarm.soundUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
            if (uri == null || !startMedia(uri)) startChimes()
            applyVolume()
            if (gentle) handler.postDelayed(ramp, 200)
        }
        if (alarm.vibrates) {
            vibrator = Vibrations.play(context, alarm.vibration.waveform(gentle))
            // A vibrate-only alarm on a phone that can't vibrate would be silent: use the chimes.
            if (vibrator == null && alarm.silent) {
                startChimes()
                applyVolume()
            }
        }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        media?.let {
            runCatching { it.stop() }
            it.release()
        }
        media = null
        chimes?.stop()
        chimes = null
        vibrator?.cancel()
        vibrator = null
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }

    private fun startMedia(uri: Uri): Boolean {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(attrs)
            mp.setDataSource(context, uri)
            mp.isLooping = true
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "Alarm sound failed ($what/$extra), using chimes")
                handler.post { switchToChimes() }
                true
            }
            mp.prepare()
            mp.start()
            media = mp
            true
        } catch (e: Exception) {
            Log.w(TAG, "Can't play $uri, using chimes", e)
            mp.release()
            false
        }
    }

    private fun switchToChimes() {
        media?.release()
        media = null
        if (chimes == null && focus != null) startChimes()
        applyVolume()
    }

    private fun startChimes() {
        chimes = ChimePlayer(attrs)
    }

    /** Gentle wake rises from -30 dB to full volume over a minute, evenly in loudness. */
    private fun applyVolume() {
        val gain = if (!gentle) {
            1f
        } else {
            val p = ((SystemClock.elapsedRealtime() - startedAt).toFloat() / Alarm.GENTLE_FADE_MS).coerceIn(0f, 1f)
            10f.pow(-30f * (1 - p) / 20f)
        }
        media?.setVolume(gain, gain)
        chimes?.volume = gain
    }

    /** Streams the chime loop on its own thread. */
    private class ChimePlayer(attrs: AudioAttributes) {
        @Volatile var volume = 1f
        @Volatile private var running = true

        init {
            thread(name = "alarm-chimes") {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                var track: AudioTrack? = null
                try {
                    val loop = ChimeSynth.loop(SAMPLE_RATE)
                    val minBytes = AudioTrack.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    )
                    val t = AudioTrack.Builder()
                        .setAudioAttributes(attrs)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(SAMPLE_RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build(),
                        )
                        .setBufferSizeInBytes(maxOf(minBytes, CHUNK * 2 * 4))
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                    track = t
                    t.play()
                    val chunk = ShortArray(CHUNK)
                    var pos = 0
                    var gain = 0f
                    while (running) {
                        val target = volume
                        for (i in 0 until CHUNK) {
                            val g = gain + (target - gain) * (i + 1) / CHUNK
                            chunk[i] = (loop[pos] * g).toInt().toShort()
                            if (++pos == loop.size) pos = 0
                        }
                        gain = target
                        t.write(chunk, 0, CHUNK)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Chimes failed", e)
                } finally {
                    track?.let {
                        runCatching {
                            it.pause()
                            it.flush()
                        }
                        it.release()
                    }
                }
            }
        }

        fun stop() {
            running = false
        }
    }

    private companion object {
        const val TAG = "AlarmSoundPlayer"
        const val SAMPLE_RATE = 44_100
        const val CHUNK = 1024
    }
}
