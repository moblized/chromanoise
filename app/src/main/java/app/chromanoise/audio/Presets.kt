package app.chromanoise.audio

import app.chromanoise.audio.SpectrumModel.BAND_COUNT
import app.chromanoise.audio.SpectrumModel.bandCenter
import app.chromanoise.audio.SpectrumModel.dbToValue
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sqrt

data class Preset(val name: String, val bands: List<Float>, val builtIn: Boolean = true)

object Presets {
    val builtIn: List<Preset> = listOf(
        slope("White", 0.0),
        slope("Pink", -3.0),
        slope("Brown", -6.0),
        fromCurve("Grey") { f -> -aWeighting(f) },
        slope("Blue", 3.0),
        slope("Violet", 6.0),
    )

    val default: Preset get() = builtIn[1]

    /** A straight line on the log-frequency axis: [dbPerOctave] dB per doubling of frequency. */
    private fun slope(name: String, dbPerOctave: Double) =
        fromCurve(name) { f -> dbPerOctave * log2(f / 1000.0) }

    /** Samples a dB curve at each band centre, normalised so the loudest band is 1.0. */
    private fun fromCurve(name: String, curve: (Double) -> Double): Preset {
        val db = DoubleArray(BAND_COUNT) { curve(bandCenter(it)) }
        val max = db.max()
        return Preset(name, db.map { dbToValue(it - max) })
    }

    /** IEC 61672 A-weighting in dB. Grey noise is its inverse: equally loud to the ear. */
    private fun aWeighting(f: Double): Double {
        val f2 = f * f
        val ra = 12194.0.pow(2) * f2 * f2 /
            ((f2 + 20.6.pow(2)) * sqrt((f2 + 107.7.pow(2)) * (f2 + 737.9.pow(2))) * (f2 + 12194.0.pow(2)))
        return 20 * log10(ra) + 2.0
    }
}
