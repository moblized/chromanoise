package app.chromanoise.alarm

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The built-in alarm sound: a soft four-note mallet chime (E major arpeggio), synthesized so
 * it needs no audio file and can't go missing. The loop is rendered circularly — each note's
 * tail wraps round to the start — so it repeats without a seam.
 */
object ChimeSynth {
    const val LOOP_SECONDS = 2.4
    private const val PEAK = 0.7

    private val NOTES = listOf(
        0.00 to 659.26, // E5
        0.18 to 830.61, // G#5
        0.36 to 987.77, // B5
        0.54 to 1318.51, // E6
    )

    fun loop(sampleRate: Int): ShortArray {
        val len = (sampleRate * LOOP_SECONDS).roundToInt()
        val mix = DoubleArray(len)
        for ((start, f) in NOTES) {
            val s0 = (start * sampleRate).roundToInt()
            for (i in 0 until len) {
                val t = i.toDouble() / sampleRate
                val attack = 1 - exp(-t / 0.004)
                val v = sin(2 * PI * f * t) * exp(-t / 0.5) +
                    0.30 * sin(2 * PI * 2 * f * t) * exp(-t / 0.22) +
                    0.08 * sin(2 * PI * 3 * f * t) * exp(-t / 0.1)
                mix[(s0 + i) % len] += attack * v
            }
        }
        val peak = mix.maxOf { abs(it) }
        val scale = PEAK * Short.MAX_VALUE / peak
        return ShortArray(len) { (mix[it] * scale).roundToInt().toShort() }
    }
}
