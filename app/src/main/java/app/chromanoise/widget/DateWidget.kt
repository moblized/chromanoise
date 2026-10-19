package app.chromanoise.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import app.chromanoise.MainActivity
import app.chromanoise.R
import app.chromanoise.data.AppearanceStore
import app.chromanoise.data.DateIconStyle
import app.chromanoise.data.Palettes

/**
 * The home-screen date icon: a one-cell widget that looks like the app icon and shows
 * today's month, day and year — in words (OCT / 19 / 2026) or numbers (10/19 / 2026), as
 * chosen in Appearance. Tapping it opens the app.
 *
 * Android only lets the phone maker's own calendar/clock apps change their real launcher
 * icon, so this is how any other app gets a live date icon. The date itself is drawn by
 * TextClock views, which update on their own at midnight — no alarms or background work.
 * The colours follow the app's colour theme.
 */
class DateWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        manager.updateAppWidget(appWidgetIds, views(context))
    }

    companion object {
        private fun views(context: Context): RemoteViews {
            val appearance = AppearanceStore.get(context).appearance.value
            val c = Palettes.dark(appearance.palette, pureBlack = false)
            val numbers = appearance.dateIcon == DateIconStyle.NUMBERS
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return RemoteViews(context.packageName, R.layout.widget_date).apply {
                setInt(R.id.date_bg, "setColorFilter", c.surfaceContainerHigh)
                setTextColor(R.id.date_month, c.primary)
                setTextColor(R.id.date_day, c.onBackground)
                setTextColor(R.id.date_year, c.onSurfaceVariant)
                setTextColor(R.id.date_month_day, c.onBackground)
                setTextColor(R.id.date_year_numbers, c.primary)
                setViewVisibility(R.id.date_words, if (numbers) View.GONE else View.VISIBLE)
                setViewVisibility(R.id.date_numbers, if (numbers) View.VISIBLE else View.GONE)
                setOnClickPendingIntent(R.id.date_root, open)
            }
        }

        /** Redraws every date icon on the home screen (after the colour theme changes). */
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
