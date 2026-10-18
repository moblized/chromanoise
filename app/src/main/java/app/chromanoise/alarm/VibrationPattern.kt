package app.chromanoise.alarm

import kotlin.math.roundToInt

/** A vibration in the shape Android's VibrationEffect.createWaveform wants. */
class Waveform(val timings: LongArray, val amplitudes: IntArray, val repeatFrom: Int)

/**
 * Alarm vibration patterns. Each is one cycle of (milliseconds, strength 0–255) steps that
 * repeats until the alarm is stopped. Pure Kotlin, so the shapes are unit-tested.
 */
enum class VibrationPattern(val label: String, private val cycle: List<Pair<Long, Int>>) {
    PULSE("Pulse", listOf(700L to 255, 800L to 0)),
    HEARTBEAT("Heartbeat", listOf(110L to 255, 120L to 0, 160L to 200, 1_000L to 0)),
    RAPID("Rapid", listOf(180L to 255, 180L to 0)),

    /** Swells up and fades away. Phones without strength control feel it as a long buzz. */
    WAVES(
        "Waves",
        (1..10).map { 80L to 40 + 215 * it / 10 } + (4 downTo 1).map { 80L to 60 * it } + listOf(500L to 0),
    ),
    ;

    val cycleMs: Long get() = cycle.sumOf { it.first }

    /**
     * The repeating alarm vibration. With [gentle] it starts at about a fifth of full strength
     * and builds up over [Alarm.GENTLE_FADE_MS], in step with the sound's fade-in.
     */
    fun waveform(gentle: Boolean): Waveform {
        val timings = ArrayList<Long>()
        val amps = ArrayList<Int>()
        if (gentle) {
            var t = 0L
            while (t < Alarm.GENTLE_FADE_MS) {
                val strength = 0.2 + 0.8 * t / Alarm.GENTLE_FADE_MS
                for ((ms, a) in cycle) {
                    timings += ms
                    amps += if (a == 0) 0 else (a * strength).roundToInt().coerceIn(1, 255)
                }
                t += cycleMs
            }
        }
        val repeatFrom = timings.size
        for ((ms, a) in cycle) {
            timings += ms
            amps += a
        }
        return Waveform(timings.toLongArray(), amps.toIntArray(), repeatFrom)
    }

    /** Two cycles at full strength, played once — for trying a pattern in the editor. */
    fun preview(): Waveform {
        val steps = cycle + cycle
        return Waveform(steps.map { it.first }.toLongArray(), steps.map { it.second }.toIntArray(), -1)
    }

    companion object {
        fun fromName(name: String?): VibrationPattern = entries.firstOrNull { it.name == name } ?: PULSE
    }
}
