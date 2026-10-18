package app.chromanoise.ui

import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chromanoise.clock.ClockStore
import app.chromanoise.clock.ClockText
import app.chromanoise.clock.TimerControl
import app.chromanoise.clock.TimerInput
import app.chromanoise.clock.TimerState

private val QUICK_MINUTES = listOf(1, 3, 5, 10, 15, 30)

/** Digits that don't shift about as they change. */
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun TimerScreen(onOpenAppearance: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store = remember { ClockStore.get(context) }
    val timer by store.timer.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        ScreenHeader("Timer", onOpenAppearance, subtitle = if (timer.idle) "Rings even with the screen off" else null)
        if (timer.idle) {
            TimerSetup(lastMs = store.lastTimerMs, onStart = { TimerControl.start(context, it) })
        } else {
            TimerRunning(
                t = timer,
                onPause = { TimerControl.pause(context) },
                onResume = { TimerControl.resume(context) },
                onAdd = { TimerControl.addMinute(context) },
                onCancel = { TimerControl.cancel(context) },
            )
        }
    }
}

@Composable
private fun TimerSetup(lastMs: Long, onStart: (Long) -> Unit) {
    var digits by rememberSaveable { mutableStateOf("") }
    val ms = TimerInput.toMillis(digits)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EntryDisplay(digits)

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (lastMs > 0 && QUICK_MINUTES.none { it * 60_000L == lastMs }) {
                AssistChip(onClick = { onStart(lastMs) }, label = { Text("Again · ${ClockText.shortDuration(lastMs)}") })
            }
            QUICK_MINUTES.forEach { m ->
                AssistChip(onClick = { onStart(m * 60_000L) }, label = { Text("$m min") })
            }
        }

        Keypad(
            onKey = { digits = TimerInput.type(digits, it) },
            onBackspace = { digits = TimerInput.backspace(digits) },
        )

        FilledIconButton(
            onClick = { onStart(ms) },
            enabled = ms > 0,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
        ) {
            Icon(AppIcons.Play, contentDescription = "Start timer", modifier = Modifier.size(34.dp))
        }
    }
}

/** "00h 05m 00s", with the digits you've typed bright and the rest dimmed. */
@Composable
private fun EntryDisplay(digits: String) {
    val (h, m, s) = TimerInput.parts(digits)
    Row(verticalAlignment = Alignment.Bottom) {
        listOf(h to "h", m to "m", s to "s").forEachIndexed { i, (value, unit) ->
            // Digits fill from the right: seconds first, then minutes, then hours.
            val filled = digits.length > (2 - i) * 2
            val color = if (filled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            Text("%02d".format(value), fontSize = 54.sp, fontWeight = FontWeight.Light, color = color, style = Tabular)
            Text(
                unit,
                style = MaterialTheme.typography.titleMedium,
                color = color,
                modifier = Modifier.padding(start = 2.dp, end = 10.dp, bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun Keypad(onKey: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("00", "0", BACK))
    Column(
        Modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Surface(
                        onClick = { if (key == BACK) onBackspace() else onKey(key) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (key == BACK) {
                                Icon(AppIcons.Backspace, contentDescription = "Delete digit")
                            } else {
                                Text(key, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val BACK = "back"

@Composable
private fun TimerRunning(
    t: TimerState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
) {
    val now = rememberMonotonicNow(ticking = t.running)
    val remaining = t.remaining(now)
    val fraction = t.fractionLeft(now)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val progress = MaterialTheme.colorScheme.primary
    // The time gently pulses while paused.
    val pulse by rememberInfiniteTransition(label = "paused").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "paused",
    )

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(0.5f))
        Box(
            Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 12.dp.toPx()
                val topLeft = Offset(stroke / 2, stroke / 2)
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arc, style = Stroke(stroke))
                drawArc(
                    progress, -90f, 360f * fraction, useCenter = false, topLeft = topLeft, size = arc,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    ClockText.timer(remaining),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light,
                    style = Tabular,
                    modifier = Modifier.alpha(if (t.paused) pulse else 1f),
                )
                Text(
                    if (t.paused) "Paused" else "of ${ClockText.shortDuration(t.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.weight(0.5f))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            FilledIconButton(onClick = if (t.running) onPause else onResume, modifier = Modifier.size(72.dp), shape = CircleShape) {
                Icon(
                    if (t.running) AppIcons.Pause else AppIcons.Play,
                    contentDescription = if (t.running) "Pause" else "Resume",
                    modifier = Modifier.size(34.dp),
                )
            }
            OutlinedButton(onClick = onAdd) { Text("+1 min") }
        }
    }
}

/** The monotonic clock (same one the timer and stopwatch use), updated every frame while [ticking]. */
@Composable
fun rememberMonotonicNow(ticking: Boolean): Long {
    val now by produceState(SystemClock.elapsedRealtime(), ticking) {
        value = SystemClock.elapsedRealtime()
        while (ticking) {
            withFrameMillis { }
            value = SystemClock.elapsedRealtime()
        }
    }
    return now
}
