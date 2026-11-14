@file:OptIn(ExperimentalMaterial3Api::class)

package app.chromanoise.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import app.chromanoise.audio.Presets
import app.chromanoise.playback.TriggerScheduler
import app.chromanoise.data.TriggerStore

private val AUTO_OFF_OPTIONS = listOf(0, 15, 30, 45, 60, 90)

/**
 * Sets what the NFC tag and the nightly auto-start play, and when the noise stops/starts.
 * Saving writes [TriggerStore] and re-arms the schedule.
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

    val presets = remember { listOf("" to "Last used") + Presets.builtIn.map { it.name to it.name } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tag & schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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

                Row(
                    Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Start every night", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = scheduled, onCheckedChange = { scheduled = it })
                }
                if (scheduled) TimeInput(state = timeState)
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
                    )
                }
                TriggerScheduler.sync(context)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
