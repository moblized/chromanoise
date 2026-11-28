@file:OptIn(ExperimentalMaterial3Api::class)

package app.chromanoise.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.chromanoise.alarm.TimeFormat
import app.chromanoise.audio.Presets
import app.chromanoise.data.TriggerStore
import app.chromanoise.playback.ChargerWatch
import app.chromanoise.playback.TriggerScheduler

private val AUTO_OFF_OPTIONS = listOf(0, 15, 30, 45, 60, 90)

/** Which end of the night window the time picker is editing. */
private enum class NightEnd { FROM, TO }

/**
 * Auto-start settings: what the NFC tag, the nightly start and the bedside charger play,
 * when the noise stops, and when it starts on its own. Saving writes [TriggerStore], re-arms
 * the nightly schedule and turns the charger watcher on or off.
 */
@Composable
fun TriggerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { TriggerStore.get(context) }
    val initial = remember { store.settings.value }

    var preset by remember { mutableStateOf(initial.preset) }
    var autoOff by remember { mutableIntStateOf(initial.autoOffMinutes) }
    var scheduled by remember { mutableStateOf(initial.scheduleEnabled) }
    val timeState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false,
    )

    var charger by remember { mutableStateOf(initial.chargerEnabled) }
    var nightOnly by remember { mutableStateOf(initial.chargerNightOnly) }
    var nightFrom by remember { mutableIntStateOf(initial.nightFrom) }
    var nightTo by remember { mutableIntStateOf(initial.nightTo) }
    var stopOnLift by remember { mutableStateOf(initial.chargerStopOnLift) }
    var editing by remember { mutableStateOf<NightEnd?>(null) }

    // Re-checked when coming back from the battery settings screen.
    var unrestricted by remember { mutableStateOf(isUnrestricted(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { unrestricted = isUnrestricted(context) }

    val presets = remember { listOf("" to "Last used") + Presets.builtIn.map { it.name to it.name } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-start") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "For your NFC tag, the nightly start and the bedside wireless charger.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Label("Play")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { (value, text) ->
                        FilterChip(
                            selected = preset == value,
                            onClick = { preset = value },
                            label = { Text(text) },
                        )
                    }
                }

                Label("Stop after")
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AUTO_OFF_OPTIONS.forEach { m ->
                        FilterChip(
                            selected = autoOff == m,
                            onClick = { autoOff = m },
                            label = { Text(if (m == 0) "Never" else "$m m") },
                        )
                    }
                }

                SwitchLine("Start every night", checked = scheduled) { scheduled = it }
                if (scheduled) TimeInput(state = timeState)

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SwitchLine(
                    "Start on the wireless charger",
                    subtitle = "When you put the phone on your bedside wireless charger. Charging by cable never starts it.",
                    checked = charger,
                ) { charger = it }
                if (charger) {
                    SwitchLine("Only at night", checked = nightOnly) { nightOnly = it }
                    if (nightOnly) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("From", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { editing = NightEnd.FROM }) { Text(timeText(context, nightFrom)) }
                            Text("to", style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { editing = NightEnd.TO }) { Text(timeText(context, nightTo)) }
                        }
                    }
                    SwitchLine(
                        "Stop when I pick it up",
                        subtitle = "Fades out if the phone stays off the charger for 15 seconds",
                        checked = stopOnLift,
                    ) { stopOnLift = it }
                    if (!unrestricted) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "To work reliably all night, let Chroma Noise run in the background.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { askUnrestricted(context) }) { Text("Allow") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                store.update {
                    it.copy(
                        preset = preset,
                        autoOffMinutes = autoOff,
                        scheduleEnabled = scheduled,
                        hour = timeState.hour,
                        minute = timeState.minute,
                        chargerEnabled = charger,
                        chargerNightOnly = nightOnly,
                        nightFrom = nightFrom,
                        nightTo = nightTo,
                        chargerStopOnLift = stopOnLift,
                    )
                }
                TriggerScheduler.sync(context)
                ChargerWatch.sync(context)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    editing?.let { end ->
        NightTimeDialog(
            title = if (end == NightEnd.FROM) "Night starts at" else "Night ends at",
            minutes = if (end == NightEnd.FROM) nightFrom else nightTo,
            onPicked = { m ->
                if (end == NightEnd.FROM) nightFrom = m else nightTo = m
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun NightTimeDialog(title: String, minutes: Int, onPicked: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimeInput(state = state) },
        confirmButton = { TextButton(onClick = { onPicked(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SwitchLine(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun timeText(context: Context, minutes: Int) = TimeFormat.time(context, minutes / 60, minutes % 60)

/** True when Android's battery saver won't put the app to sleep (Samsung: Battery → Unrestricted). */
private fun isUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@SuppressLint("BatteryLife") // a bedside watcher that must keep running overnight is the intended use
private fun askUnrestricted(context: Context) {
    val ask = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(ask) }
        .recoverCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
}
