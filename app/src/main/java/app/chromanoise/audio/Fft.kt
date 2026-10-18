package app.chromanoise.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place iterative radix-2 complex FFT. [size] must be a power of two. Not thread-safe. */
class Fft(val size: Int) {
    private val levels: Int
    private val cosTable = DoubleArray(size / 2)
    private val sinTable = DoubleArray(size / 2)
    private val bitReversed = IntArray(size)

    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two" }
        levels = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size / 2) {
            cosTable[i] = cos(2 * PI * i / size)
            sinTable[i] = sin(2 * PI * i / size)
        }
        for (i in 0 until size) bitReversed[i] = Integer.reverse(i) ushr (32 - levels)
    }

    /** Forward transform, e^(-2πi·kn/N). Unscaled. */
    fun forward(re: DoubleArray, im: DoubleArray) = transform(re, im, sign = -1.0)

    /** Inverse transform, e^(+2πi·kn/N). Unscaled (no 1/N). */
    fun inverse(re: DoubleArray, im: DoubleArray) = transform(re, im, sign = 1.0)

    private fun transform(re: DoubleArray, im: DoubleArray, sign: Double) {
        for (i in 0 until size) {
            val j = bitReversed[i]
            if (j > i) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var half = 1
        while (half < size) {
            val tableStep = size / (half * 2)
            var start = 0
            while (start < size) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = sign * sinTable[k]
                    val l = j + half
                    val tr = re[l] * wr - im[l] * wi
                    val ti = re[l] * wi + im[l] * wr
                    re[l] = re[j] - tr
                    im[l] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += tableStep
                }
                start += half * 2
            }
            half *= 2
        }
    }
}
