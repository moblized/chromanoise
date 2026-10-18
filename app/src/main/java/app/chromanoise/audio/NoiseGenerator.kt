package app.chromanoise.audio

import java.util.Random
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Makes blocks of noise whose spectrum follows the drawn curve: a random complex value
 * for every FFT bin, scaled by the curve, then an inverse FFT.
 *
 * Every block is pre-multiplied by a sine window. Two such blocks overlapped by half a
 * block sum to constant power (sin² + cos² = 1), so [BlockMixer] can chain random blocks
 * forever without level dips or clicks.
 *
 * Not thread-safe: use one instance per thread.
 */
class NoiseGenerator(val sampleRate: Int, val blockSize: Int) {
    private val fft = Fft(blockSize)
    private val re = DoubleArray(blockSize)
    private val im = DoubleArray(blockSize)
    private val window = FloatArray(blockSize) { sin(PI * (it + 0.5) / blockSize).toFloat() }

    /** Per-bin amplitudes for one spectrum, plus the gain that brings the output to [TARGET_RMS]. */
    class Shape internal constructor(val amplitudes: DoubleArray, val gain: Double)

    fun shapeFor(bands: FloatArray): Shape {
        val half = blockSize / 2
        val amps = DoubleArray(half)
        var power = 0.0
        for (k in 1 until half) {
            val a = SpectrumModel.amplitudeAt(bands, k.toDouble() * sampleRate / blockSize)
            amps[k] = a
            power += a * a
        }
        // The real part of the inverse FFT has variance Σ a², so this gain gives a known
        // loudness without measuring anything — every block of a spectrum gets the same gain.
        val gain = if (power > 0) TARGET_RMS / sqrt(power) else 0.0
        return Shape(amps, gain)
    }

    fun makeBlock(shape: Shape, random: Random): FloatArray {
        re.fill(0.0)
        im.fill(0.0)
        val amps = shape.amplitudes
        for (k in 1 until blockSize / 2) {
            val a = amps[k]
            if (a == 0.0) continue
            re[k] = random.nextGaussian() * a
            im[k] = random.nextGaussian() * a
        }
        fft.inverse(re, im)
        val g = shape.gain
        return FloatArray(blockSize) { n -> (re[n] * g).toFloat() * window[n] }
    }

    companion object {
        /** About -16 dBFS: loud enough on a phone, with headroom so peaks don't clip. */
        const val TARGET_RMS = 0.16
    }
}
