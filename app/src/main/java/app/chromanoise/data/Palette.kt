package app.chromanoise.data

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Colour themes. Each is two hues: an accent (buttons, sliders, selected chips) and a base
 * that tints the backgrounds. Full light / dark / pure-black schemes are generated from
 * them, then nudged until every text colour passes WCAG contrast — so any new palette added
 * here is readable automatically (the unit tests check all of them).
 */
enum class AccentPalette(
    val label: String,
    val accentHue: Float,
    val baseHue: Float,
    val saturation: Float = 1f,
) {
    VIOLET("Violet", 251f, 231f),
    OCEAN("Ocean", 196f, 212f),
    FOREST("Forest", 146f, 162f),
    EMBER("Ember", 24f, 14f),
    ROSE("Rose", 338f, 322f),
    MONO("Mono", 230f, 230f, saturation = 0f),
    ;

    /** A mid-tone dot for the colour picker. */
    val swatch: Int get() = Palettes.hsl(accentHue, 0.62f * saturation, 0.6f)

    companion object {
        fun fromName(name: String?): AccentPalette = entries.firstOrNull { it.name == name } ?: VIOLET
    }
}

/** Every colour the app's theme needs, as ARGB ints. */
data class SchemeColors(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val background: Int,
    val onBackground: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int,
    val surfaceContainerLowest: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val outline: Int,
    val outlineVariant: Int,
)

object Palettes {
    fun dark(p: AccentPalette, pureBlack: Boolean): SchemeColors {
        val s = p.saturation
        val b = p.baseHue
        val a = p.accentHue
        val bg = if (pureBlack) BLACK else hsl(b, 0.38f * s, 0.065f)
        val highest = if (pureBlack) hsl(b, 0.31f * s, 0.16f) else hsl(b, 0.29f * s, 0.19f)
        val primary = lightenUntil(hsl(a, 1f * s, 0.84f)) { contrast(it, highest) >= 6.0 }
        val onPrimary = darkenUntil(hsl(a, 0.55f * s, 0.18f)) { contrast(it, primary) >= 7.0 }
        return SchemeColors(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = hsl(a, 0.44f * s, 0.30f),
            onPrimaryContainer = hsl(a, 1f * s, 0.94f),
            secondaryContainer = hsl(b, 0.31f * s, 0.22f),
            onSecondaryContainer = hsl(b, 0.40f * s, 0.90f),
            background = bg,
            onBackground = hsl(b, 0.30f * s, 0.93f),
            surfaceVariant = if (pureBlack) hsl(b, 0.40f * s, 0.11f) else hsl(b, 0.35f * s, 0.13f),
            onSurfaceVariant = lightenUntil(hsl(b, 0.14f * s, 0.66f)) { contrast(it, highest) >= 4.5 },
            surfaceContainerLowest = if (pureBlack) BLACK else hsl(b, 0.36f * s, 0.045f),
            surfaceContainerLow = if (pureBlack) hsl(b, 0.36f * s, 0.045f) else hsl(b, 0.35f * s, 0.095f),
            surfaceContainer = if (pureBlack) hsl(b, 0.33f * s, 0.075f) else hsl(b, 0.38f * s, 0.12f),
            surfaceContainerHigh = if (pureBlack) hsl(b, 0.33f * s, 0.12f) else hsl(b, 0.35f * s, 0.16f),
            surfaceContainerHighest = highest,
            outline = hsl(b, 0.19f * s, 0.36f),
            outlineVariant = if (pureBlack) hsl(b, 0.29f * s, 0.2f) else hsl(b, 0.29f * s, 0.24f),
        )
    }

    fun light(p: AccentPalette): SchemeColors {
        val s = p.saturation
        val b = p.baseHue
        val a = p.accentHue
        val bg = hsl(b, 0.55f * s, 0.975f)
        val highest = hsl(b, 0.31f * s, 0.90f)
        // Dark enough for white text on it, and to read as a text colour on the background.
        val primary = darkenUntil(hsl(a, 0.51f * s, 0.53f)) { contrast(WHITE, it) >= 4.6 && contrast(it, highest) >= 4.5 }
        return SchemeColors(
            primary = primary,
            onPrimary = WHITE,
            primaryContainer = hsl(a, 1f * s, 0.93f),
            onPrimaryContainer = hsl(a, 0.55f * s, 0.18f),
            secondaryContainer = hsl(b, 0.42f * s, 0.92f),
            onSecondaryContainer = hsl(b, 0.29f * s, 0.155f),
            background = bg,
            onBackground = hsl(b, 0.19f * s, 0.125f),
            surfaceVariant = hsl(b, 0.30f * s, 0.925f),
            onSurfaceVariant = darkenUntil(hsl(b, 0.11f * s, 0.40f)) { contrast(it, highest) >= 4.5 },
            surfaceContainerLowest = WHITE,
            surfaceContainerLow = hsl(b, 0.50f * s, 0.96f),
            surfaceContainer = hsl(b, 0.38f * s, 0.945f),
            surfaceContainerHigh = hsl(b, 0.33f * s, 0.93f),
            surfaceContainerHighest = highest,
            outline = hsl(b, 0.12f * s, 0.59f),
            outlineVariant = hsl(b, 0.19f * s, 0.82f),
        )
    }

    // ---- Colour maths ------------------------------------------------------------------

    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()

    /** HSL (hue 0–360, saturation and lightness 0–1) to opaque ARGB. */
    fun hsl(h: Float, s: Float, l: Float): Int {
        val c = (1 - abs(2 * l - 1)) * s
        val hp = ((h % 360f) + 360f) % 360f / 60f
        val x = c * (1 - abs(hp % 2 - 1))
        val (r1, g1, b1) = when (hp.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2
        return argb(r1 + m, g1 + m, b1 + m)
    }

    /** WCAG relative luminance. */
    fun luminance(color: Int): Double {
        fun ch(shift: Int): Double {
            val v = (color shr shift and 0xFF) / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }

    /** WCAG contrast ratio, 1–21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun argb(r: Float, g: Float, b: Float): Int {
        fun ch(v: Float) = (v.coerceIn(0f, 1f) * 255).roundToInt()
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    private fun scale(color: Int, f: Float): Int {
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        return argb(r * f, g * f, b * f)
    }

    /** Moves each channel 6% of the way to white until [ok], so hue is kept. */
    private fun lightenUntil(color: Int, ok: (Int) -> Boolean): Int {
        var c = color
        repeat(40) {
            if (ok(c)) return c
            val r = (c shr 16 and 0xFF) / 255f
            val g = (c shr 8 and 0xFF) / 255f
            val b = (c and 0xFF) / 255f
            c = argb(r + (1 - r) * 0.06f, g + (1 - g) * 0.06f, b + (1 - b) * 0.06f)
        }
        return c
    }

    private fun darkenUntil(color: Int, ok: (Int) -> Boolean): Int {
        var c = color
        repeat(60) {
            if (ok(c)) return c
            c = scale(c, 0.94f)
        }
        return c
    }
}
