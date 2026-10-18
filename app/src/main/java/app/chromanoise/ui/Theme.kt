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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.chromanoise.data.AccentPalette
import app.chromanoise.data.AppearanceStore
import app.chromanoise.data.Palettes
import app.chromanoise.data.SchemeColors

/** True when the app is currently showing a dark theme (night or pure black). */
val LocalDarkTheme = staticCompositionLocalOf { true }

@Composable
fun ChromaNoiseTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val appearance by AppearanceStore.get(context).appearance.collectAsState()
    val dark = appearance.isDark(isSystemInDarkTheme())
    val colors = remember(appearance.palette, dark, appearance.pureBlack) {
        colorScheme(appearance.palette, dark, appearance.pureBlack)
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

/** Builds the Material colour scheme from one of the generated [Palettes]. */
private fun colorScheme(palette: AccentPalette, dark: Boolean, pureBlack: Boolean): ColorScheme {
    val c: SchemeColors = if (dark) Palettes.dark(palette, pureBlack) else Palettes.light(palette)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(c.primary),
        onPrimary = Color(c.onPrimary),
        primaryContainer = Color(c.primaryContainer),
        onPrimaryContainer = Color(c.onPrimaryContainer),
        secondary = Color(c.primary),
        onSecondary = Color(c.onPrimary),
        secondaryContainer = Color(c.secondaryContainer),
        onSecondaryContainer = Color(c.onSecondaryContainer),
        tertiary = Color(c.primary),
        onTertiary = Color(c.onPrimary),
        background = Color(c.background),
        onBackground = Color(c.onBackground),
        surface = Color(c.background),
        onSurface = Color(c.onBackground),
        surfaceVariant = Color(c.surfaceVariant),
        onSurfaceVariant = Color(c.onSurfaceVariant),
        surfaceTint = Color(c.primary),
        surfaceContainerLowest = Color(c.surfaceContainerLowest),
        surfaceContainerLow = Color(c.surfaceContainerLow),
        surfaceContainer = Color(c.surfaceContainer),
        surfaceContainerHigh = Color(c.surfaceContainerHigh),
        surfaceContainerHighest = Color(c.surfaceContainerHighest),
        surfaceBright = Color(c.surfaceContainerHighest),
        surfaceDim = Color(c.background),
        outline = Color(c.outline),
        outlineVariant = Color(c.outlineVariant),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
