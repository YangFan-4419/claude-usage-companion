package com.usagecompanion.claude.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.usagecompanion.claude.MainActivity
import com.usagecompanion.claude.R
import com.usagecompanion.claude.data.UsageRepository

class UsageWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, manager, appWidgetId)
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, UsageWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { appWidgetId ->
                updateWidget(context, manager, appWidgetId)
            }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val snapshot = UsageRepository(context).currentSnapshot()
            val views = RemoteViews(context.packageName, R.layout.usage_widget).apply {
                setTextViewText(
                    R.id.widget_percent,
                    if (snapshot.hasUsage) "5h ${snapshot.fiveHourPercent}%" else "--",
                )
                setTextViewText(
                    R.id.widget_detail,
                    if (snapshot.hasUsage) "used / 7d ${snapshot.sevenDayPercent}%" else "Set up on phone",
                )
                setOnClickPendingIntent(R.id.widget_root, launchIntent(context))
            }
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun launchIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
