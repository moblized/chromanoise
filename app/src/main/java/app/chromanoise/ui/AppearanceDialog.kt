package app.chromanoise.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chromanoise.data.AppearanceStore
import app.chromanoise.data.ThemeMode

/** Theme picker. Changes apply live, so you see each option as you tap it. */
@Composable
fun AppearanceDialog(onDismiss: () -> Unit) {
    val store = AppearanceStore.get(LocalContext.current)
    val appearance by store.appearance.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Appearance") },
        text = {
            Column {
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = appearance.mode == mode,
                                    role = Role.RadioButton,
                                    onClick = { store.update(appearance.copy(mode = mode)) },
                                )
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = appearance.mode == mode, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                val blackAvailable = appearance.mode != ThemeMode.LIGHT
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = appearance.pureBlack,
                            enabled = blackAvailable,
                            role = Role.Switch,
                            onValueChange = { store.update(appearance.copy(pureBlack = it)) },
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Pure black", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (blackAvailable) "Darkest background in dark mode; saves battery on OLED screens" else "Only used in dark mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = appearance.pureBlack, onCheckedChange = null, enabled = blackAvailable)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
