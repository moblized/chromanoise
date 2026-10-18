package app.chromanoise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chromanoise.alarm.AlarmRingService
import app.chromanoise.alarm.TimeFormat

const val TAB_NOISE = 0
const val TAB_ALARMS = 1

/**
 * Two tabs (Noise, Alarms). A ringing alarm takes over the whole screen, and so does the
 * alarm editor while it's open.
 */
@Composable
fun AppRoot(
    noiseVm: MainViewModel,
    alarmsVm: AlarmsViewModel,
    tabRequest: Int?,
    onTabRequestHandled: () -> Unit,
    onPlayPressed: () -> Unit,
    onAlarmSaved: () -> Unit,
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(TAB_NOISE) }
    var showAppearance by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(tabRequest) {
        if (tabRequest != null) {
            tab = tabRequest
            onTabRequestHandled()
        }
    }

    val ringing by AlarmRingService.ringing.collectAsStateWithLifecycle()
    val editing by alarmsVm.editing.collectAsStateWithLifecycle()
    val alarms by alarmsVm.alarms.collectAsStateWithLifecycle()
    rememberMinuteClock() // keeps "Today/Tomorrow" in the header current
    val nextAlarm = alarms.filter { it.isOn }.mapNotNull { it.nextAt }.minOrNull()

    val r = ringing
    val e = editing
    when {
        r != null -> RingingScreen(
            ringing = r,
            onSnooze = { AlarmRingService.snooze(context) },
            onStop = { AlarmRingService.dismiss(context) },
        )

        e != null -> AlarmEditor(
            initial = e,
            isNew = alarmsVm.isNew(e),
            onSave = { alarm ->
                val at = alarmsVm.save(alarm)
                onAlarmSaved()
                if (at != null) toastRingsIn(context, at)
            },
            onDelete = { alarmsVm.delete(e) },
            onCancel = alarmsVm::closeEditor,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding(),
        )

        else -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    NavigationBarItem(
                        selected = tab == TAB_NOISE,
                        onClick = { tab = TAB_NOISE },
                        icon = { Icon(AppIcons.Bars, contentDescription = null) },
                        label = { Text("Noise") },
                    )
                    NavigationBarItem(
                        selected = tab == TAB_ALARMS,
                        onClick = { tab = TAB_ALARMS },
                        icon = { Icon(AppIcons.Alarm, contentDescription = null) },
                        label = { Text("Alarms") },
                    )
                }
            },
        ) { padding ->
            when (tab) {
                TAB_ALARMS -> AlarmsScreen(
                    vm = alarmsVm,
                    onOpenAppearance = { showAppearance = true },
                    modifier = Modifier.padding(padding),
                )
                else -> NoiseScreen(
                    vm = noiseVm,
                    nextAlarm = nextAlarm?.let { TimeFormat.dayAndTime(context, it) },
                    onAlarmClick = { tab = TAB_ALARMS },
                    onOpenAppearance = { showAppearance = true },
                    onPlayPressed = onPlayPressed,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    if (showAppearance) AppearanceDialog(onDismiss = { showAppearance = false })
}
