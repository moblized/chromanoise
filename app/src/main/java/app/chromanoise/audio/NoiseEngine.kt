package app.chromanoise.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Random
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Process-wide noise player.
 *
 * A background thread keeps a pool of ~1.5 s noise blocks shaped to the current spectrum;
 * an audio thread chains random blocks from the pool into an [AudioTrack]. In steady state
 * no FFTs run at all — playback is just adding two arrays — so it's light on the battery.
 */
object NoiseEngine {
    const val SAMPLE_RATE = 44_100
    private const val BLOCK_SIZE = 1 shl 16
    private const val POOL_SIZE = 12
    private const val CHUNK_FRAMES = 1024
    private const val NO_FADE = -1
    private const val RECOVER_PER_CHUNK = 0.08f
    private const val TIMER_FADE_MS = 30_000
    private const val TAG = "NoiseEngine"

    // ---- Block generation -------------------------------------------------------------

    private val generator = NoiseGenerator(SAMPLE_RATE, BLOCK_SIZE)
    private val genExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "noise-gen") }
    private val generation = AtomicInteger()
    private val latestBands = AtomicReference<FloatArray?>(null)

    @Volatile private var pool: List<FloatArray> = emptyList()

    /** Reshape the noise. Cheap to call on every drag event: stale requests are skipped. */
    fun setSpectrum(bands: FloatArray) {
        latestBands.set(bands.copyOf())
        val gen = generation.incrementAndGet()
        genExecutor.execute { regenerate(gen) }
    }

    private fun regenerate(gen: Int) {
        if (gen != generation.get()) return
        val bands = latestBands.get() ?: return
        val shape = generator.shapeFor(bands)
        val random = Random()
        val fresh = ArrayList<FloatArray>(POOL_SIZE)
        while (fresh.size < POOL_SIZE) {
            if (gen != generation.get()) return
            fresh += generator.makeBlock(shape, random)
            // Publish early so a change is heard within about a second, then keep filling.
            if (fresh.size == 2 || fresh.size % 4 == 0) pool = fresh.toList()
        }
        pool = fresh.toList()
    }

    // ---- Playback ---------------------------------------------------------------------

    @Volatile private var volume = 0.7f

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
    }

    private val _label = MutableStateFlow("Noise")
    /** Name shown in the notification ("Pink", "Custom", a saved preset…). */
    val label: StateFlow<String> = _label.asStateFlow()

    fun setLabel(name: String) {
        _label.value = name
    }

    private val _isPlaying = MutableStateFlow(false)
    /** What the user wants: true from [play] until [stop] (or the sleep timer). */
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    /** True while sound is actually coming out, including the fade after [stop]. */
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private class Session {
        /** Fade-out length in frames requested by [stop], or [NO_FADE]. */
        @Volatile var fadeFrames = NO_FADE
        @Volatile var hardStop = false
        var ended = false // guarded by lock
    }

    private val lock = Any()
    private var session: Session? = null // guarded by lock

    fun play() {
        synchronized(lock) {
            val s = session
            if (s != null && !s.ended && !s.hardStop) {
                s.fadeFrames = NO_FADE // still running (maybe fading out): just fade back in
            } else {
                val fresh = Session()
                session = fresh
                thread(name = "noise-audio") { runSession(fresh) }
            }
            _isPlaying.value = true
            _isActive.value = true
        }
    }

    /** Stops after fading out over [fadeMillis]; 0 stops at once. Also cancels the sleep timer. */
    fun stop(fadeMillis: Int = 400) {
        cancelTimer()
        synchronized(lock) {
            val s = session
            if (s != null) {
                if (fadeMillis <= 0) {
                    s.hardStop = true
                } else {
                    val frames = (fadeMillis.toLong() * SAMPLE_RATE / 1000).toInt()
                    if (s.fadeFrames == NO_FADE || frames < s.fadeFrames) s.fadeFrames = frames
                }
            }
            _isPlaying.value = false
        }
    }

    private fun runSession(s: Session) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        var track: AudioTrack? = null
        try {
            val t = buildTrack()
            track = t
            t.play()
            val mixer = BlockMixer(BLOCK_SIZE)
            val buf = FloatArray(CHUNK_FRAMES * 2)
            var gain = 0f
            var fadeLevel = 1f
            var fadeStep = 0f
            var appliedFade = NO_FADE
            while (!s.hardStop) {
                val requested = s.fadeFrames
                if (requested != appliedFade) {
                    appliedFade = requested
                    if (requested != NO_FADE) {
                        fadeStep = fadeLevel * CHUNK_FRAMES / maxOf(requested, CHUNK_FRAMES)
                    }
                }
                fadeLevel = if (appliedFade == NO_FADE) {
                    minOf(1f, fadeLevel + RECOVER_PER_CHUNK)
                } else {
                    maxOf(0f, fadeLevel - fadeStep)
                }
                val v = volume
                val target = v * v * fadeLevel // squared: closer to how loudness is heard
                mixer.fill(buf, CHUNK_FRAMES, pool, gain, target)
                gain = target
                t.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)

                if (appliedFade != NO_FADE && fadeLevel <= 0f) {
                    val done = synchronized(lock) {
                        if (s.fadeFrames != NO_FADE) {
                            s.ended = true
                            true
                        } else {
                            false
                        }
                    }
                    if (done) break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Playback failed", e)
        } finally {
            track?.let {
                runCatching {
                    it.pause()
                    it.flush()
                }
                it.release()
            }
            synchronized(lock) {
                s.ended = true
                if (session === s) {
                    session = null
                    _isPlaying.value = false
                    _isActive.value = false
                }
            }
        }
    }

    private fun buildTrack(): AudioTrack {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT,
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBytes, CHUNK_FRAMES * 2 * 4 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    // ---- Sleep timer ------------------------------------------------------------------

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _timerEndsAt = MutableStateFlow<Long?>(null)
    /** Wall-clock time (ms) when the sleep timer stops playback, or null. */
    val timerEndsAt: StateFlow<Long?> = _timerEndsAt.asStateFlow()

    private val timerRunnable = Runnable { stop(fadeMillis = TIMER_FADE_MS) }

    /** Fade out so the sound is gone exactly [minutes] from now. null or 0 cancels. */
    fun setTimer(minutes: Int?) {
        mainHandler.removeCallbacks(timerRunnable)
        if (minutes == null || minutes <= 0) {
            _timerEndsAt.value = null
            return
        }
        val totalMs = minutes * 60_000L
        _timerEndsAt.value = System.currentTimeMillis() + totalMs
        mainHandler.postDelayed(timerRunnable, maxOf(0L, totalMs - TIMER_FADE_MS))
    }

    private fun cancelTimer() {
        mainHandler.removeCallbacks(timerRunnable)
        _timerEndsAt.value = null
    }
}
