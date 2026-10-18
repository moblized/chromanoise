package app.chromanoise.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.chromanoise.data.AppearanceStore

/** Night: deep navy. The original look. */
private val NightColors = darkColorScheme(
    primary = Color(0xFFB9ADFF),
    onPrimary = Color(0xFF1C1548),
    primaryContainer = Color(0xFF332B6E),
    onPrimaryContainer = Color(0xFFE4DEFF),
    secondaryContainer = Color(0xFF262A48),
    onSecondaryContainer = Color(0xFFDCDAF2),
    background = Color(0xFF0A0C16),
    onBackground = Color(0xFFE8E7F3),
    surface = Color(0xFF0A0C16),
    onSurface = Color(0xFFE8E7F3),
    surfaceVariant = Color(0xFF161A2E),
    onSurfaceVariant = Color(0xFF9E9DB6),
    surfaceContainerLowest = Color(0xFF07080F),
    surfaceContainerLow = Color(0xFF0F1220),
    surfaceContainer = Color(0xFF13172A),
    surfaceContainerHigh = Color(0xFF1A1E36),
    surfaceContainerHighest = Color(0xFF22263F),
    outline = Color(0xFF4B4F6E),
    outlineVariant = Color(0xFF2C3050),
)

/** Pure black: same accents, true-black background (saves battery on OLED screens). */
private val BlackColors = NightColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF101326),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF07080F),
    surfaceContainer = Color(0xFF0D0F1A),
    surfaceContainerHigh = Color(0xFF14172A),
    surfaceContainerHighest = Color(0xFF1C2036),
    outlineVariant = Color(0xFF242842),
)

/** Day: soft lavender-white with the same violet accent. */
private val DayColors = lightColorScheme(
    primary = Color(0xFF5B4BC4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DEFF),
    onPrimaryContainer = Color(0xFF1C1548),
    secondaryContainer = Color(0xFFE3E1F4),
    onSecondaryContainer = Color(0xFF1E1C33),
    background = Color(0xFFF7F6FC),
    onBackground = Color(0xFF1A1B26),
    surface = Color(0xFFF7F6FC),
    onSurface = Color(0xFF1A1B26),
    surfaceVariant = Color(0xFFE6E5F2),
    onSurfaceVariant = Color(0xFF5C5C72),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF2F1FA),
    surfaceContainer = Color(0xFFEDECF6),
    surfaceContainerHigh = Color(0xFFE7E6F3),
    surfaceContainerHighest = Color(0xFFDFDEEE),
    outline = Color(0xFF8A8AA3),
    outlineVariant = Color(0xFFCBCADB),
)

/** True when the app is currently showing a dark theme (night or pure black). */
val LocalDarkTheme = staticCompositionLocalOf { true }

@Composable
fun ChromaNoiseTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val appearance by AppearanceStore.get(context).appearance.collectAsState()
    val dark = appearance.isDark(isSystemInDarkTheme())
    val colors: ColorScheme = when {
        !dark -> DayColors
        appearance.pureBlack -> BlackColors
        else -> NightColors
    }

    // Light status-bar icons on dark backgrounds, dark icons on light ones.
    val activity = context.findActivity()
    DisposableEffect(activity, dark) {
        if (activity is ComponentActivity) {
            val transparent = android.graphics.Color.TRANSPARENT
            val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
            activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
        onDispose { }
    }

    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
