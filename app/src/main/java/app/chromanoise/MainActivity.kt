package app.chromanoise

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import app.chromanoise.ui.AlarmsViewModel
import app.chromanoise.ui.AppRoot
import app.chromanoise.ui.ChromaNoiseTheme
import app.chromanoise.ui.MainViewModel
import app.chromanoise.ui.TAB_ALARMS

class MainActivity : ComponentActivity() {
    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* the Alarms tab warns if denied */ }

    /** Set when we're opened from an alarm notification, so the Alarms tab shows. */
    private var tabRequest by mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // ChromaNoiseTheme then matches the bar icons to the chosen theme
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            ChromaNoiseTheme {
                val noiseVm: MainViewModel = viewModel()
                val alarmsVm: AlarmsViewModel = viewModel()
                AppRoot(
                    noiseVm = noiseVm,
                    alarmsVm = alarmsVm,
                    tabRequest = tabRequest,
                    onTabRequestHandled = { tabRequest = null },
                    onPlayPressed = { askForNotifications(onlyOnce = true) },
                    onAlarmSaved = { askForNotifications(onlyOnce = false) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_ALARMS, false) == true) tabRequest = TAB_ALARMS
    }

    /**
     * Notifications need permission on Android 13+. For noise it's a nicety (ask once);
     * for alarms it's needed for the Stop button, so ask each time an alarm is saved —
     * Android itself stops showing the prompt if it's refused twice.
     */
    private fun askForNotifications(onlyOnce: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return
        if (onlyOnce) {
            val prefs = getSharedPreferences("chroma_noise", MODE_PRIVATE)
            if (prefs.getBoolean("asked_notifications", false)) return
            prefs.edit().putBoolean("asked_notifications", true).apply()
        }
        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_OPEN_ALARMS = "app.chromanoise.OPEN_ALARMS"
    }
}
