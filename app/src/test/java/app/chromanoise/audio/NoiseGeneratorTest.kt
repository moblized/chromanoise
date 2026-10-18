package app.chromanoise.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.sqrt

/**
 * Plays each preset through the real generator + mixer for 20 seconds, then measures the
 * spectrum of what comes out and checks it has the slope that colour of noise should have.
 */
class NoiseGeneratorTest {
    private val sampleRate = 44_100
    private val blockSize = 1 shl 16

    @Test fun whiteIsFlat() = assertSlope("White", 0.0)
    @Test fun pinkFallsThreeDbPerOctave() = assertSlope("Pink", -3.0)
    @Test fun brownFallsSixDbPerOctave() = assertSlope("Brown", -6.0)
    @Test fun blueRisesThreeDbPerOctave() = assertSlope("Blue", 3.0)
    @Test fun violetRisesSixDbPerOctave() = assertSlope("Violet", 6.0)

    @Test fun greyFollowsInverseAWeighting() {
        val psd = measure(preset("Grey"))
        // A-weighting is about -16 dB around 125 Hz and 0 dB around 1 kHz,
        // so grey noise should be roughly 16 dB stronger an octave around 125 Hz.
        val diff = octaveDb(psd, 125.0) - octaveDb(psd, 1000.0)
        assertEquals(16.2, diff, 2.0)
    }

    @Test fun levelIsSteadyAcrossCrossfades() {
        val stream = render(preset("Pink"), seconds = 20)
        val window = sampleRate / 10 // 100 ms
        val start = blockSize / 2 // skip the opening fade-in
        val levels = (start until stream.size - window step window).map { i -> rms(stream, i, window) }
        val overall = rms(stream, start, stream.size - start)
        assertEquals(NoiseGenerator.TARGET_RMS, overall, 0.01)
        val minDb = 20 * log10(levels.min() / overall)
        val maxDb = 20 * log10(levels.max() / overall)
        assertTrue("level dips to $minDb dB", minDb > -1.5)
        assertTrue("level peaks at $maxDb dB", maxDb < 1.5)
        assertTrue("clipping", stream.none { abs(it) >= 1f })
    }

    @Test fun channelsAreUncorrelated() {
        val gen = NoiseGenerator(sampleRate, blockSize)
        val shape = gen.shapeFor(preset("White"))
        val rnd = Random(7)
        val pool = List(12) { gen.makeBlock(shape, rnd) }
        val mixer = BlockMixer(blockSize, Random(8))
        val frames = sampleRate * 10
        val buf = FloatArray(frames * 2)
        mixer.fill(buf, frames, pool, 1f, 1f)
        var lr = 0.0; var ll = 0.0; var rr = 0.0
        for (i in blockSize / 2 until frames) {
            val l = buf[2 * i].toDouble(); val r = buf[2 * i + 1].toDouble()
            lr += l * r; ll += l * l; rr += r * r
        }
        val corr = lr / sqrt(ll * rr)
        assertTrue("L/R correlation $corr", abs(corr) < 0.1)
    }

    // ---- helpers ----------------------------------------------------------------------

    private fun preset(name: String) = Presets.builtIn.first { it.name == name }.bands.toFloatArray()

    private fun assertSlope(name: String, expectedDbPerOctave: Double) {
        val psd = measure(preset(name))
        // Octaves from 63 Hz to 8 kHz; the 16 kHz octave would run past the 20 kHz cutoff.
        val centres = listOf(63.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0)
        val x = centres.map { log2(it) }
        val y = centres.map { octaveDb(psd, it) }
        val slope = fitSlope(x, y)
        assertEquals("$name slope", expectedDbPerOctave, slope, 0.5)
    }

    /** Left channel of [seconds] of mixer output at unity gain. */
    private fun render(bands: FloatArray, seconds: Int): FloatArray {
        val gen = NoiseGenerator(sampleRate, blockSize)
        val shape = gen.shapeFor(bands)
        val rnd = Random(1)
        val pool = List(12) { gen.makeBlock(shape, rnd) }
        val mixer = BlockMixer(blockSize, Random(2))
        val frames = sampleRate * seconds
        val buf = FloatArray(frames * 2)
        mixer.fill(buf, frames, pool, 1f, 1f)
        return FloatArray(frames) { buf[2 * it] }
    }

    /** Welch power spectral density (per bin) using Hann-windowed 8192-sample segments. */
    private fun measure(bands: FloatArray): DoubleArray {
        val signal = render(bands, seconds = 20)
        val n = 8192
        val fft = Fft(n)
        val win = DoubleArray(n) { 0.5 - 0.5 * kotlin.math.cos(2 * Math.PI * it / n) }
        val psd = DoubleArray(n / 2)
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        var segments = 0
        var start = blockSize / 2
        while (start + n <= signal.size) {
            for (i in 0 until n) { re[i] = signal[start + i] * win[i]; im[i] = 0.0 }
            fft.forward(re, im)
            for (k in 0 until n / 2) psd[k] += re[k] * re[k] + im[k] * im[k]
            segments++
            start += n / 2
        }
        for (k in psd.indices) psd[k] /= segments
        return psd
    }

    /** Mean power per bin across the octave centred on [centre], in dB. */
    private fun octaveDb(psd: DoubleArray, centre: Double): Double {
        val binHz = sampleRate.toDouble() / (psd.size * 2)
        val lo = (centre / sqrt(2.0) / binHz).toInt()
        val hi = (centre * sqrt(2.0) / binHz).toInt().coerceAtMost(psd.size - 1)
        var sum = 0.0
        for (k in lo..hi) sum += psd[k]
        return 10 * log10(sum / (hi - lo + 1))
    }

    private fun fitSlope(x: List<Double>, y: List<Double>): Double {
        val mx = x.average(); val my = y.average()
        var num = 0.0; var den = 0.0
        for (i in x.indices) { num += (x[i] - mx) * (y[i] - my); den += (x[i] - mx) * (x[i] - mx) }
        return num / den
    }

    private fun rms(a: FloatArray, from: Int, len: Int): Double {
        var s = 0.0
        for (i in from until from + len) s += a[i].toDouble() * a[i]
        return sqrt(s / len)
    }
}
