@file:OptIn(ExperimentalMaterial3Api::class)

package app.chromanoise.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chromanoise.alarm.Alarm
import app.chromanoise.alarm.AlarmScheduler
import app.chromanoise.alarm.AlarmText
import app.chromanoise.alarm.TimeFormat
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AlarmsScreen(vm: AlarmsViewModel, onOpenAppearance: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val alarms by vm.alarms.collectAsStateWithLifecycle()
    var issues by remember { mutableStateOf(findIssues(context)) }

    // Settings can change while we're in the background, and alarms may have rung meanwhile.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        issues = findIssues(context)
        vm.resync()
    }

    val now = rememberMinuteClock()
    val next = alarms.filter { it.isOn }.mapNotNull { it.nextAt }.minOrNull()

    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.padding(bottom = 4.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("Alarms", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (next == null) {
                                "No alarms on"
                            } else {
                                "Next: ${TimeFormat.dayAndTime(context, next)} · in ${AlarmText.duration(next - now)}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AppearanceButton(onOpenAppearance)
                }
            }
            if (alarms.isNotEmpty()) {
                items(issues, key = { it.text }) { issue ->
                    IssueBanner(issue) {
                        issue.fix(context)
                        issues = findIssues(context)
                    }
                }
            }
            if (alarms.isEmpty()) item { EmptyState() }
            items(alarms, key = { it.id }) { alarm ->
                AlarmCard(
                    alarm = alarm,
                    onClick = { vm.edit(alarm) },
                    onToggle = { on ->
                        val at = vm.setOn(alarm, on)
                        if (on && at != null) toastRingsIn(context, at)
                    },
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = vm::newAlarm,
            icon = { Icon(AppIcons.Add, contentDescription = null) },
            text = { Text("New alarm") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
}

@Composable
private fun AlarmCard(alarm: Alarm, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    val context = LocalContext.current
    val (time, suffix) = TimeFormat.timeParts(context, alarm.hour, alarm.minute)
    val on = alarm.isOn
    val timeColor = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val details = listOf(
        AlarmText.describeDays(alarm.days, Locale.getDefault()),
        if (alarm.silent) "Vibrate only" else "",
        alarm.label,
    )
        .filter { it.isNotBlank() }
        .joinToString(" · ")

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(time, fontSize = 44.sp, fontWeight = FontWeight.Light, color = timeColor)
                    if (suffix != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            suffix,
                            style = MaterialTheme.typography.titleMedium,
                            color = timeColor,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                Text(
                    details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                alarm.snoozedUntil?.let {
                    Text(
                        "Snoozed until ${TimeFormat.time(context, it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Switch(checked = on, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 64.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            AppIcons.Alarm,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.size(16.dp))
        Text("No alarms yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(6.dp))
        Text(
            "Tap New alarm to set one. If noise is playing when it rings, the noise fades out and the alarm fades in.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ---- Things that would stop an alarm from working ------------------------------------

private class Issue(val text: String, val button: String, val fix: (Context) -> Unit)

private fun findIssues(context: Context): List<Issue> = buildList {
    val pkg = Uri.parse("package:${context.packageName}")
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        add(
            Issue("Notifications are off, so a ringing alarm can't show its Stop button.", "Turn on") {
                it.openSettings(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, it.packageName),
                )
            },
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !AlarmScheduler.canScheduleExact(context)) {
        add(
            Issue("Alarms could ring late until you allow “Alarms & reminders”.", "Allow") {
                it.openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
            },
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    ) {
        add(
            Issue("Alarms can't open over the lock screen until you allow full-screen notifications.", "Allow") {
                it.openSettings(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg))
            },
        )
    }
    val audio = context.getSystemService(AudioManager::class.java)
    if (audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
        add(
            Issue("Your phone's alarm volume is all the way down.", "Turn up") {
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                runCatching {
                    audio.setStreamVolume(
                        AudioManager.STREAM_ALARM,
                        (max * 0.6f).roundToInt().coerceAtLeast(1),
                        AudioManager.FLAG_SHOW_UI,
                    )
                }
            },
        )
    }
}

private fun Context.openSettings(intent: Intent) {
    runCatching { startActivity(intent) }
        .onFailure { Toast.makeText(this, "Couldn't open that setting", Toast.LENGTH_SHORT).show() }
}

@Composable
private fun IssueBanner(issue: Issue, onFix: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(issue.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onFix) { Text(issue.button) }
        }
    }
}

// ---- Shared bits -----------------------------------------------------------------------

/** System time, refreshed at the top of every minute. */
@Composable
fun rememberMinuteClock(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000)
        }
    }
    return now
}

fun toastRingsIn(context: Context, at: Long) {
    val text = "Alarm set for ${AlarmText.duration(at - System.currentTimeMillis())} from now"
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
