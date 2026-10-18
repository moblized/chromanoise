package app.chromanoise.audio

import kotlin.math.ln
import kotlin.math.pow

/**
 * The drawn spectrum: [BAND_COUNT] bars, each 0..1, whose centres sit evenly on a
 * log-frequency axis from [MIN_FREQ] to [MAX_FREQ].
 *
 * A bar at 1.0 is full level; lower bars are cut on a dB scale spanning [DB_RANGE]
 * (so 0.5 is -35 dB). A bar at 0 is silence. Between bar centres the level is
 * interpolated in dB, which keeps straight-line presets (pink, brown…) exact.
 */
object SpectrumModel {
    const val BAND_COUNT = 32
    const val MIN_FREQ = 20.0
    const val MAX_FREQ = 20_000.0
    const val DB_RANGE = 70.0
    private const val SILENT = 0.005

    private val logSpan = ln(MAX_FREQ / MIN_FREQ)

    fun bandCenter(i: Int): Double = MIN_FREQ * (MAX_FREQ / MIN_FREQ).pow(i.toDouble() / (BAND_COUNT - 1))

    /** Fractional band index (0 at [MIN_FREQ], BAND_COUNT-1 at [MAX_FREQ]) for a frequency. */
    fun positionOf(freq: Double): Double = ln(freq / MIN_FREQ) / logSpan * (BAND_COUNT - 1)

    fun valueToAmplitude(v: Double): Double =
        if (v <= SILENT) 0.0 else 10.0.pow(-DB_RANGE * (1 - v) / 20)

    /** Bar height for a level in dB relative to the loudest bar (0 dB → 1.0, -70 dB → 0). */
    fun dbToValue(db: Double): Float = (1 + db / DB_RANGE).coerceIn(0.0, 1.0).toFloat()

    /** Linear amplitude at [freq]. Outside 20 Hz–20 kHz the output is silent. */
    fun amplitudeAt(bands: FloatArray, freq: Double): Double {
        if (freq < MIN_FREQ || freq > MAX_FREQ) return 0.0
        val pos = positionOf(freq).coerceIn(0.0, (BAND_COUNT - 1).toDouble())
        val i = pos.toInt().coerceAtMost(BAND_COUNT - 2)
        val t = pos - i
        val a = bands[i].toDouble()
        val b = bands[i + 1].toDouble()
        if (a <= SILENT && b <= SILENT) return 0.0
        return valueToAmplitude(a + (b - a) * t)
    }
}
