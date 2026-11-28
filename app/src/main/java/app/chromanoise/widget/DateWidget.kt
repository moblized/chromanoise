package app.chromanoise.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import app.chromanoise.MainActivity
import app.chromanoise.R
import app.chromanoise.data.AppearanceStore
import app.chromanoise.data.DateIconColors
import app.chromanoise.data.DateIconStyle
import app.chromanoise.data.ThemeMode

/**
 * The home-screen date icon: a one-cell widget that looks like the app icon and shows
 * today's month, day and year — in words (OCT / 19 / 2026) or numbers (10/19 / 2026), as
 * chosen in Appearance. Tapping it opens the app.
 *
 * Android only lets the phone maker's own calendar/clock apps change their real launcher
 * icon, so this is how any other app gets a live date icon. The date itself is drawn by
 * TextClock views, which update on their own at midnight — no alarms or background work.
 *
 * The colours follow the app's colour theme and light/dark setting: a pale tile in light
 * mode, black (like the app icon) in dark mode. With "Follow phone setting", both sets are
 * handed to the home screen, which swaps between them by itself when the phone changes to
 * or from dark mode — the app doesn't have to be running.
 */
class DateWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        manager.updateAppWidget(appWidgetIds, views(context))
    }

    companion object {
        private fun views(context: Context): RemoteViews {
            val appearance = AppearanceStore.get(context).appearance.value
            val light = DateIconColors.light(appearance.palette)
            val dark = DateIconColors.dark(appearance.palette)
            val numbers = appearance.dateIcon == DateIconStyle.NUMBERS
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return RemoteViews(context.packageName, R.layout.widget_date).apply {
                when {
                    appearance.mode == ThemeMode.LIGHT -> paint(light, light)
                    appearance.mode == ThemeMode.DARK -> paint(dark, dark)
                    // Android 12+: the home screen picks light or dark itself, live.
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> paint(light, dark)
                    // Older phones: use the phone's current mode; redrawn when the app opens.
                    phoneIsDark(context) -> paint(dark, dark)
                    else -> paint(light, light)
                }
                setViewVisibility(R.id.date_words, if (numbers) View.GONE else View.VISIBLE)
                setViewVisibility(R.id.date_numbers, if (numbers) View.VISIBLE else View.GONE)
                setOnClickPendingIntent(R.id.date_root, open)
            }
        }

        /** Colours each part, with [night] used while the phone is in dark mode (Android 12+). */
        private fun RemoteViews.paint(day: DateIconColors, night: DateIconColors) {
            fun color(id: Int, method: String, pick: (DateIconColors) -> Int) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setColorInt(id, method, pick(day), pick(night))
                else setInt(id, method, pick(day))
            }
            color(R.id.date_bg, "setColorFilter") { it.background }
            color(R.id.date_month, "setTextColor") { it.accent }
            color(R.id.date_day, "setTextColor") { it.text }
            color(R.id.date_year, "setTextColor") { it.muted }
            color(R.id.date_month_day, "setTextColor") { it.text }
            color(R.id.date_year_numbers, "setTextColor") { it.accent }
        }

        private fun phoneIsDark(context: Context): Boolean =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        /** Redraws every date icon on the home screen (after the theme or date style changes). */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, DateWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context))
        }

        /**
         * Asks the launcher to put a date icon on the home screen (it shows its own
         * "Add to home screen?" prompt). False if this launcher can't do that.
         */
        fun requestPin(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context) ?: return false
            if (!manager.isRequestPinAppWidgetSupported) return false
            return manager.requestPinAppWidget(ComponentName(context, DateWidget::class.java), null, null)
        }
    }
}
