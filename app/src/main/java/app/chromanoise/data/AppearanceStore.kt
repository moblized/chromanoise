package app.chromanoise.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) {
    SYSTEM("Follow phone setting"),
    LIGHT("Light"),
    DARK("Dark"),
}

data class Appearance(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val palette: AccentPalette = AccentPalette.VIOLET,
) {
    /** Whether to use dark colours, given whether the phone itself is in dark mode. */
    fun isDark(phoneIsDark: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> phoneIsDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

/** The theme choice. Changes apply instantly everywhere, including the lock-screen alarm. */
class AppearanceStore private constructor(context: Context) {
    // Device-protected storage, so the alarm screen can read it even right after a reboot.
    private val prefs = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private val _appearance = MutableStateFlow(
        Appearance(
            mode = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) } ?: ThemeMode.SYSTEM,
            pureBlack = prefs.getBoolean(KEY_BLACK, false),
            palette = AccentPalette.fromName(prefs.getString(KEY_PALETTE, null)),
        ),
    )
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    fun update(appearance: Appearance) {
        _appearance.value = appearance
        prefs.edit()
            .putString(KEY_MODE, appearance.mode.name)
            .putBoolean(KEY_BLACK, appearance.pureBlack)
            .putString(KEY_PALETTE, appearance.palette.name)
            .apply()
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_BLACK = "pure_black"
        private const val KEY_PALETTE = "palette"

        @Volatile private var instance: AppearanceStore? = null

        fun get(context: Context): AppearanceStore =
            instance ?: synchronized(this) {
                instance ?: AppearanceStore(context.applicationContext).also { instance = it }
            }
    }
}
