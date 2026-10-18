@file:OptIn(ExperimentalMaterial3Api::class)

package app.chromanoise.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import app.chromanoise.alarm.Alarm
import app.chromanoise.alarm.AlarmText
import app.chromanoise.alarm.TimeFormat
import app.chromanoise.alarm.VibrationPattern
import app.chromanoise.alarm.Vibrations
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val SNOOZE_CHOICES = listOf(5, 10, 15, 20)

@Composable
fun AlarmEditor(
    initial: Alarm,
    isNew: Boolean,
    onSave: (Alarm) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val timeState = rememberTimePickerState(initial.hour, initial.minute, TimeFormat.is24h(context))
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var showSounds by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val canVibrate = remember { Vibrations.hasVibrator(context) }

    BackHandler(onBack = onCancel)

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.let {
            IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } ?: return@rememberLauncherForActivityResult
        val title = runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull()
        draft = draft.copy(soundUri = uri.toString(), soundName = title ?: "Phone sound")
    }

    fun current() = draft.copy(hour = timeState.hour, minute = timeState.minute)

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Text(
                if (isNew) "New alarm" else "Edit alarm",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { onSave(current()) }, modifier = Modifier.padding(end = 8.dp)) { Text("Save") }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = timeState)
            }

            // Live "rings in…" so you can sanity-check AM/PM before saving.
            val now = rememberMinuteClock()
            val at = current().copy(enabled = true, snoozedUntil = null).nextTrigger(now, ZoneId.systemDefault())
            if (at != null) {
                Text(
                    "Rings ${TimeFormat.dayAndTime(context, at).lowercaseDayWord()} · in ${AlarmText.duration(at - now)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionLabel("Repeat")
            DayPicker(draft.days) { draft = draft.copy(days = it) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0 to "Once", Alarm.WEEKDAYS to "Weekdays", Alarm.WEEKENDS to "Weekends", Alarm.EVERY_DAY to "Every day")
                    .forEach { (days, name) ->
                        FilterChip(
                            selected = draft.days == days,
                            onClick = { draft = draft.copy(days = days) },
                            label = { Text(name) },
                        )
                    }
            }

            OutlinedTextField(
                value = draft.label,
                onValueChange = { draft = draft.copy(label = it.take(40)) },
                label = { Text("Label") },
                placeholder = { Text("Wake up") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingRow(
                    title = "Sound",
                    subtitle = draft.soundName,
                    onClick = { showSounds = true },
                )
                SettingRow(
                    title = "Gentle wake",
                    subtitle = when {
                        draft.silent -> "Vibration builds up over a minute"
                        draft.vibrates && canVibrate -> "Sound and vibration build up over a minute"
                        else -> "Sound fades in over a minute"
                    },
                    checked = draft.gentleWake,
                    onCheckedChange = { draft = draft.copy(gentleWake = it) },
                )
                if (canVibrate) {
                    SettingRow(
                        title = "Vibrate",
                        subtitle = if (draft.silent) "Always on for a vibrate-only alarm" else null,
                        checked = draft.vibrates,
                        enabled = !draft.silent,
                        onCheckedChange = { draft = draft.copy(vibrate = it) },
                    )
                    if (draft.vibrates) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            VibrationPattern.entries.forEach { p ->
                                FilterChip(
                                    selected = draft.vibration == p,
                                    onClick = {
                                        draft = draft.copy(vibration = p)
                                        Vibrations.preview(context, p) // feel it straight away
                                    },
                                    label = { Text(p.label) },
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            SectionLabel("Snooze length")
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SNOOZE_CHOICES.forEach { m ->
                    FilterChip(
                        selected = draft.snoozeMinutes == m,
                        onClick = { draft = draft.copy(snoozeMinutes = m) },
                        label = { Text("$m min") },
                    )
                }
            }

            if (!isNew) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Delete alarm", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    if (showSounds) {
        AlertDialog(
            onDismissRequest = { showSounds = false },
            title = { Text("Alarm sound") },
            text = {
                Column {
                    SoundOption(
                        title = Alarm.DEFAULT_SOUND_NAME,
                        subtitle = "Built in, always available",
                        selected = draft.soundUri == null,
                    ) {
                        draft = draft.copy(soundUri = null, soundName = Alarm.DEFAULT_SOUND_NAME)
                        showSounds = false
                    }
                    if (canVibrate) {
                        SoundOption(
                            title = "No sound",
                            subtitle = "Vibrate only",
                            selected = draft.silent,
                        ) {
                            draft = draft.copy(soundUri = Alarm.SILENT_SOUND, soundName = Alarm.SILENT_SOUND_NAME)
                            showSounds = false
                        }
                    }
                    val phoneSound = draft.soundUri != null && !draft.silent
                    SoundOption(
                        title = "Phone alarm sound…",
                        subtitle = if (phoneSound) draft.soundName else "Pick one of your phone's sounds",
                        selected = phoneSound,
                    ) {
                        showSounds = false
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
                            .putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                draft.soundUri?.takeIf { !draft.silent }?.let(Uri::parse),
                            )
                        try {
                            ringtonePicker.launch(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, "This phone has no sound picker", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSounds = false }) { Text("Close") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this alarm?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** One circle per weekday, in this locale's week order. */
@Composable
private fun DayPicker(days: Int, onChange: (Int) -> Unit) {
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        AlarmText.weekOrder(locale).forEach { day ->
            val bit = Alarm.dayBit(day)
            val on = days and bit != 0
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .toggleable(value = on, role = Role.Checkbox) { checked ->
                        onChange(if (checked) days or bit else days and bit.inv())
                    }
                    .semantics { contentDescription = day.getDisplayName(TextStyle.FULL, locale) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A list row that either opens something ([onClick]) or has a switch ([checked]). */
@Composable
private fun SettingRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean? = null,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = when {
        onClick != null -> Modifier.selectable(selected = false, onClick = onClick, role = Role.Button)
        checked != null -> Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        )
        else -> Modifier
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) {
            { Text(subtitle) }
        } else {
            null
        },
        trailingContent = if (checked != null) {
            { Switch(checked = checked, onCheckedChange = null, enabled = enabled) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = rowModifier,
    )
}

@Composable
private fun SoundOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Rings Tomorrow 7:30 AM" reads better as "Rings tomorrow 7:30 AM". */
private fun String.lowercaseDayWord(): String = when {
    startsWith("Today") || startsWith("Tomorrow") -> replaceFirstChar { it.lowercase() }
    else -> "on $this"
}
