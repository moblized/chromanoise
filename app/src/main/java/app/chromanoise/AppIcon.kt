package app.chromanoise

import android.content.Context
import android.content.ComponentName
import android.content.pm.PackageManager

/**
 * Keeps the app to a single home-screen icon.
 *
 * The icon itself now themes light/dark automatically through day/night resources
 * (`drawable-night` + `values-night`), so there's no need to swap launcher components
 * by theme — which on some launchers (notably Samsung One UI) left a duplicate icon
 * behind. This just makes sure the one launcher alias is enabled and the old dark
 * alias is off, cleaning up any duplicate from earlier builds.
 */
object AppIcon {
    fun ensureSingle(context: Context) {
        val base = javaClass.`package`?.name ?: context.packageName
        val pm = context.packageManager
        val light = ComponentName(context.packageName, "$base.LauncherLight")
        val dark = ComponentName(context.packageName, "$base.LauncherDark")

        runCatching {
            if (pm.getComponentEnabledSetting(dark) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(
                    dark, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
                )
            }
            if (pm.getComponentEnabledSetting(light) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                pm.setComponentEnabledSetting(
                    light, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
                )
            }
        }
    }
}
