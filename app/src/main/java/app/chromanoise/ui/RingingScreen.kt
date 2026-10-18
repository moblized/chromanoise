package app.chromanoise.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.chromanoise.alarm.Ringing
import app.chromanoise.alarm.TimeFormat
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

/** Full-screen "alarm is ringing" view: the time, a big Stop, and Snooze. */
@Composable
fun RingingScreen(ringing: Ringing, onSnooze: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000 - value % 1_000)
        }
    }
    val minute = now / 60_000
    val (time, suffix) = remember(minute) {
        val t = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        TimeFormat.timeParts(context, t.hour, t.minute)
    }
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.8f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )

    // A sky that starts in the theme's own colour and warms to sunrise at the bottom:
    // night → plum in dark mode, pale tint → peach in light mode.
    val dark = LocalDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    val sky = if (dark) {
        listOf(scheme.background, lerp(scheme.background, scheme.primaryContainer, 0.55f), Color(0xFF5A3048))
    } else {
        listOf(scheme.primaryContainer, Color(0xFFFBE3D6), Color(0xFFFFC7A3))
    }
    val clockColor = if (dark) Color.White else Color(0xFF1A1B26)
    val glowColor = if (dark) Color(0x55FFB37A) else Color(0x66FFFFFF)

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(sky)),
    ) {
        // A slow sunrise glow behind the clock.
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height * 0.36f)
            val r = size.minDimension * 0.55f * glow
            drawCircle(
                Brush.radialGradient(listOf(glowColor, Color.Transparent), center = center, radius = r),
                radius = r,
                center = center,
            )
        }
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.8f))
            Text(
                ringing.label,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(time, fontSize = 88.sp, fontWeight = FontWeight.Light, color = clockColor)
                if (suffix != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        suffix,
                        fontSize = 26.sp,
                        color = clockColor.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 18.dp),
                    )
                }
            }
            if (ringing.detail.isNotBlank()) {
                Text(ringing.detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1.2f))
            if (ringing.flipEnabled) {
                Text(
                    if (ringing.isTimer) "Turn the phone face down to stop" else "Turn the phone face down to snooze",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(
                        if (ringing.isTimer) "+1 min" else "Snooze · ${ringing.snoozeMinutes} min",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(76.dp)) {
                    Text("Stop", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}
