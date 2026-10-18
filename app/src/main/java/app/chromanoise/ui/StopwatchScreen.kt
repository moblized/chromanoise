package app.chromanoise.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chromanoise.clock.ClockStore
import app.chromanoise.clock.ClockText
import app.chromanoise.clock.StopwatchControl
import app.chromanoise.clock.StopwatchState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val TabularDigits = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun StopwatchScreen(onOpenAppearance: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { ClockStore.get(context) }
    val sw by store.stopwatch.collectAsStateWithLifecycle()
    val now = rememberMonotonicNow(ticking = sw.running)
    val elapsed = sw.elapsed(now)
    val track = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ScreenHeader("Stopwatch", onOpenAppearance)

        Box(
            Modifier
                .padding(vertical = 12.dp)
                .fillMaxWidth(0.7f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            // A dot that goes round once a minute.
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 3.dp.toPx()
                val dot = 7.dp.toPx()
                val r = size.minDimension / 2 - dot
                drawCircle(track, radius = r, style = Stroke(stroke))
                val angle = (elapsed % 60_000) / 60_000.0 * 2 * PI - PI / 2
                drawCircle(accent, radius = dot, center = Offset(center.x + r * cos(angle).toFloat(), center.y + r * sin(angle).toFloat()))
            }
            Text(ClockText.stopwatch(elapsed), fontSize = 40.sp, fontWeight = FontWeight.Light, style = TabularDigits)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { StopwatchControl.reset(context) },
                enabled = !sw.running && !sw.idle,
                modifier = Modifier.width(104.dp),
            ) { Text("Reset") }
            FilledIconButton(
                onClick = { if (sw.running) StopwatchControl.pause(context) else StopwatchControl.start(context) },
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    if (sw.running) AppIcons.Pause else AppIcons.Play,
                    contentDescription = if (sw.running) "Pause" else "Start",
                    modifier = Modifier.size(34.dp),
                )
            }
            OutlinedButton(
                onClick = { StopwatchControl.lap(context) },
                enabled = sw.running,
                modifier = Modifier.width(104.dp),
            ) { Text("Lap") }
        }

        Laps(sw, elapsed, Modifier.weight(1f))
    }
}

/** Newest lap first; with two or more laps the fastest and slowest are labelled. */
@Composable
private fun Laps(sw: StopwatchState, elapsed: Long, modifier: Modifier) {
    val laps = sw.lapTimes()
    if (laps.isEmpty()) {
        Spacer(modifier)
        return
    }
    val fastest = if (laps.size >= 2) laps.indices.minBy { laps[it] } else -1
    val slowest = if (laps.size >= 2) laps.indices.maxBy { laps[it] } else -1
    val current = elapsed - sw.lapMarks.last()
    val indices = laps.indices.reversed().toList()

    LazyColumn(modifier.fillMaxWidth().padding(top = 16.dp)) {
        item {
            LapRow("Lap", "Lap time", "Total", header = true)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item {
            LapRow("${laps.size + 1}", ClockText.stopwatch(current), ClockText.stopwatch(elapsed), dim = true)
        }
        items(indices, key = { it }) { i ->
            LapRow(
                "${i + 1}",
                ClockText.stopwatch(laps[i]),
                ClockText.stopwatch(sw.lapMarks[i]),
                tag = when (i) {
                    fastest -> "Fastest"
                    slowest -> "Slowest"
                    else -> null
                },
                tagColor = if (i == fastest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LapRow(
    name: String,
    lap: String,
    total: String,
    header: Boolean = false,
    dim: Boolean = false,
    tag: String? = null,
    tagColor: Color = Color.Unspecified,
) {
    val style = if (header) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyLarge.merge(TabularDigits)
    val main = when {
        header -> MaterialTheme.colorScheme.onSurfaceVariant
        dim -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = style, color = main)
            if (tag != null) {
                Text(
                    tag,
                    style = MaterialTheme.typography.labelMedium,
                    color = tagColor,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Text(lap, style = style, color = main, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(
            total,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
