package app.chromanoise.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.chromanoise.audio.SpectrumModel
import app.chromanoise.audio.SpectrumModel.BAND_COUNT

/**
 * The heart of the app: one bar per frequency band, low pitches on the left. Touch and drag
 * anywhere to draw the curve; fast strokes fill in every band they cross.
 */
@Composable
fun SpectrumEditor(
    bands: List<Float>,
    playing: Boolean,
    onBandsChange: (List<Float>) -> Unit,
    onEditFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentBands by rememberUpdatedState(bands)
    val onChange by rememberUpdatedState(onBandsChange)
    val onFinish by rememberUpdatedState(onEditFinished)

    val bandColors = if (LocalDarkTheme.current) BAND_COLORS_DARK else BAND_COLORS_LIGHT
    val panelColor = MaterialTheme.colorScheme.surfaceContainer
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()

    // A slow "breathing" glow while sound is playing; read only in the draw phase.
    val glow: State<Float> = if (playing) {
        rememberInfiniteTransition(label = "glow").animateFloat(
            initialValue = 0.7f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glow",
        )
    } else {
        remember { mutableFloatStateOf(0.8f) }
    }

    Canvas(
        modifier
            .semantics {
                contentDescription =
                    "Noise spectrum. Drag up or down on the bars to shape the sound: low pitches on the left, high on the right."
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val area = plotArea(size.width.toFloat(), size.height.toFloat())
                    val work = currentBands.toMutableList()
                    var last = down.position
                    paintStroke(work, area, last, last)
                    onChange(work.toList())
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.position != last) {
                            paintStroke(work, area, last, change.position)
                            last = change.position
                            onChange(work.toList())
                        }
                        change.consume()
                    }
                    onFinish()
                }
            },
    ) {
        drawRoundRect(panelColor, cornerRadius = CornerRadius(24.dp.toPx()))
        val area = plotArea(size.width, size.height)
        val barW = area.width / BAND_COUNT
        val hairline = 1.dp.toPx()

        for (k in 1..3) {
            val y = area.top + area.height * k / 4f
            drawLine(gridColor, Offset(area.left, y), Offset(area.right, y), hairline)
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx()))
        for ((freq, text) in FREQ_LABELS) {
            val x = area.left + (SpectrumModel.positionOf(freq).toFloat() + 0.5f) * barW
            drawLine(gridColor, Offset(x, area.top), Offset(x, area.bottom), hairline, pathEffect = dash)
            val layout = textMeasurer.measure(text, labelStyle)
            drawText(layout, topLeft = Offset(x - layout.size.width / 2f, area.bottom + 7.dp.toPx()))
        }

        val gap = barW * 0.22f
        val minH = 3.dp.toPx()
        val g = glow.value
        bands.forEachIndexed { i, v ->
            val color = bandColors[i]
            val alpha = if (v <= 0.005f) 0.25f else g
            val h = maxOf(v * area.height, minH)
            val top = area.bottom - h
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(color.copy(alpha = alpha), color.copy(alpha = 0.15f * alpha)),
                    startY = top,
                    endY = area.bottom,
                ),
                topLeft = Offset(area.left + i * barW + gap / 2, top),
                size = Size(barW - gap, h),
                cornerRadius = CornerRadius((barW - gap) / 2),
            )
        }
    }
}

/** Sets every band the finger passed over between [from] and [to], following the line between them. */
private fun paintStroke(work: MutableList<Float>, area: PlotArea, from: Offset, to: Offset) {
    val i0 = area.bandAt(from.x)
    val i1 = area.bandAt(to.x)
    if (i0 == i1) {
        work[i1] = area.valueAt(to.y)
        return
    }
    val step = if (i1 > i0) 1 else -1
    var i = i0
    while (true) {
        val t = (i - i0).toFloat() / (i1 - i0)
        work[i] = area.valueAt(from.y + (to.y - from.y) * t)
        if (i == i1) break
        i += step
    }
}

private class PlotArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun bandAt(x: Float): Int = ((x - left) / width * BAND_COUNT).toInt().coerceIn(0, BAND_COUNT - 1)

    /** 0 at the bottom, 1 at the top; the bottom sliver snaps to 0 so a band can be silenced. */
    fun valueAt(y: Float): Float {
        val v = (1f - (y - top) / height).coerceIn(0f, 1f)
        return if (v < 0.02f) 0f else v
    }
}

/** Same geometry for drawing and for touch, so a finger lands on the bar it's over. */
private fun Density.plotArea(w: Float, h: Float) =
    PlotArea(left = 14.dp.toPx(), top = 18.dp.toPx(), right = w - 14.dp.toPx(), bottom = h - 30.dp.toPx())

private val FREQ_LABELS = listOf(100.0 to "100 Hz", 1_000.0 to "1 kHz", 10_000.0 to "10 kHz")

/** Red for the deepest bass through to violet for the highest treble. Soft pastels on dark. */
private val BAND_COLORS_DARK = List(BAND_COUNT) { Color.hsv(275f * it / (BAND_COUNT - 1), 0.5f, 1f) }

/**
 * The same rainbow for the light theme, darkened until each colour stands out at least 3:1
 * against the light panel (yellows and greens need the most darkening).
 */
private val BAND_COLORS_LIGHT = List(BAND_COUNT) {
    var c = Color.hsv(275f * it / (BAND_COUNT - 1), 0.8f, 1f)
    while (c.luminance() > 0.24f) c = Color(c.red * 0.94f, c.green * 0.94f, c.blue * 0.94f)
    c
}
