package app.chromanoise.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.chromanoise.ui.ChromaNoiseTheme
import app.chromanoise.ui.RingingScreen

/** The full-screen alarm that appears over the lock screen. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge() // ChromaNoiseTheme then matches the bar icons to the chosen theme
        setContent {
            ChromaNoiseTheme {
                val ringing by AlarmRingService.ringing.collectAsStateWithLifecycle()
                val r = ringing
                if (r == null) {
                    LaunchedEffect(Unit) { finish() }
                } else {
                    RingingScreen(
                        ringing = r,
                        onSnooze = { AlarmRingService.snooze(this) },
                        onStop = { AlarmRingService.dismiss(this) },
                    )
                }
            }
        }
    }
}
