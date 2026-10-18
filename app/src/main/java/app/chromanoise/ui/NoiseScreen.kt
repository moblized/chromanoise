@file:OptIn(ExperimentalMaterial3Api::class)

package app.chromanoise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chromanoise.audio.Preset
import app.chromanoise.audio.Presets
import kotlinx.coroutines.delay

private val TIMER_OPTIONS = listOf(15, 30, 60, 120, 480)

private val PRESET_DOTS = mapOf(
    "White" to Color(0xFFEDEDF2),
    "Pink" to Color(0xFFF4A6C6),
    "Brown" to Color(0xFFB0835E),
    "Grey" to Color(0xFF9C9CA8),
    "Blue" to Color(0xFF86B6FF),
    "Violet" to Color(0xFFB99CFF),
)

/**
 * @param nextAlarm e.g. "Tomorrow 7:30 AM", shown as a little pill in the header; null hides it.
 */
@Composable
fun NoiseScreen(
    vm: MainViewModel,
    nextAlarm: String?,
    onAlarmClick: () -> Unit,
    onOpenAppearance: () -> Unit,
    onPlayPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bands by vm.bands.collectAsStateWithLifecycle()
    val presetName by vm.presetName.collectAsStateWithLifecycle()
    val volume by vm.volume.collectAsStateWithLifecycle()
    val saved by vm.savedPresets.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    val active by vm.isActive.collectAsStateWithLifecycle()
    val timerEndsAt by vm.timerEndsAt.collectAsStateWithLifecycle()
    val timerChoice by vm.timerChoice.collectAsStateWithLifecycle()

    var showSave by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Preset?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(presetName, playing, active, nextAlarm, onAlarmClick, onOpenAppearance)
        SpectrumEditor(
            bands = bands,
            playing = active,
            onBandsChange = vm::editBands,
            onEditFinished = vm::finishEdit,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 140.dp),
        )
        PresetRow(
            selected = presetName,
            saved = saved,
            onPick = vm::applyPreset,
            onSave = { showSave = true },
            onDelete = { pendingDelete = it },
        )
        VolumeRow(volume, vm::setVolume)
        TimerRow(timerEndsAt, timerChoice, vm::setTimer)
        Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
            FilledIconButton(
                onClick = {
                    onPlayPressed()
                    vm.togglePlay()
                },
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
            ) {
                Icon(
                    if (playing) AppIcons.Pause else AppIcons.Play,
                    contentDescription = if (playing) "Stop" else "Play",
                    modifier = Modifier.size(34.dp),
                )
            }
        }
    }

    if (showSave) SaveDialog(onSave = { vm.savePreset(it); showSave = false }, onDismiss = { showSave = false })

    pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete “${p.name}”?") },
            confirmButton = {
                TextButton(onClick = { vm.deletePreset(p); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(
    presetName: String,
    playing: Boolean,
    active: Boolean,
    nextAlarm: String?,
    onAlarmClick: () -> Unit,
    onOpenAppearance: () -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Chroma Noise",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (nextAlarm != null) {
                Surface(
                    onClick = onAlarmClick,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            AppIcons.Alarm,
                            contentDescription = "Next alarm",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(nextAlarm, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            AppearanceButton(onOpenAppearance)
        }
        val status = when {
            playing -> "playing"
            active -> "fading out"
            else -> "drag the bars to shape the sound"
        }
        Text(
            "$presetName · $status",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PresetRow(
    selected: String,
    saved: List<Preset>,
    onPick: (Preset) -> Unit,
    onSave: () -> Unit,
    onDelete: (Preset) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Presets.builtIn.forEach { p ->
            FilterChip(
                selected = selected == p.name,
                onClick = { onPick(p) },
                label = { Text(p.name) },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(PRESET_DOTS[p.name] ?: MaterialTheme.colorScheme.primary)
                            // Outline so the White dot still shows on the light theme.
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape),
                    )
                },
            )
        }
        saved.forEach { p ->
            val isSelected = selected == p.name
            FilterChip(
                selected = isSelected,
                onClick = { onPick(p) },
                label = { Text(p.name) },
                trailingIcon = {
                    if (isSelected) {
                        Icon(
                            AppIcons.Close,
                            contentDescription = "Delete ${p.name}",
                            modifier = Modifier.size(18.dp).clip(CircleShape).clickable { onDelete(p) },
                        )
                    }
                },
            )
        }
        AssistChip(
            onClick = onSave,
            label = { Text("Save") },
            leadingIcon = { Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
}

@Composable
private fun VolumeRow(volume: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(AppIcons.Speaker, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Slider(
            value = volume,
            onValueChange = onChange,
            modifier = Modifier.weight(1f).semantics { contentDescription = "Volume" },
        )
    }
}

@Composable
private fun TimerRow(endsAt: Long?, choice: Int?, onPick: (Int?) -> Unit) {
    val remaining = rememberCountdown(endsAt)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (endsAt == null) "Sleep timer" else "Sleep timer · stops in ${formatRemaining(remaining)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = endsAt == null, onClick = { onPick(null) }, label = { Text("Off") })
            TIMER_OPTIONS.forEach { m ->
                FilterChip(
                    selected = endsAt != null && choice == m,
                    onClick = { onPick(m) },
                    label = { Text(if (m < 60) "$m min" else "${m / 60} h") },
                )
            }
        }
    }
}

@Composable
private fun SaveDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val trimmed = name.trim()
    val taken = trimmed.equals(MainViewModel.CUSTOM, ignoreCase = true) ||
        Presets.builtIn.any { it.name.equals(trimmed, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save this sound") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                singleLine = true,
                label = { Text("Name") },
                isError = taken,
                supportingText = { if (taken) Text("That name is already used by a built-in sound") },
            )
        },
        confirmButton = {
            TextButton(enabled = trimmed.isNotEmpty() && !taken, onClick = { onSave(trimmed) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Milliseconds left until [endsAt], ticking once a second; 0 when there's no timer. */
@Composable
private fun rememberCountdown(endsAt: Long?): Long {
    val remaining by produceState(initialValue = 0L, endsAt) {
        if (endsAt == null) {
            value = 0L
            return@produceState
        }
        while (true) {
            val left = (endsAt - System.currentTimeMillis()).coerceAtLeast(0L)
            value = left
            if (left == 0L) break
            delay((left - 1) % 1000 + 1)
        }
    }
    return remaining
}

private fun formatRemaining(ms: Long): String {
    val totalSeconds = (ms + 999) / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
