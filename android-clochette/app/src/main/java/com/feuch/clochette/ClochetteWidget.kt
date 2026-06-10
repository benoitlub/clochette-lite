package com.feuch.clochette

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class ClochetteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it, latestLine(context)) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REMARK) {
            val memory = ClochetteMemory(context)
            val line = ClochetteEngine.remark(
                activity = UsageObserver(context).snapshot(),
                sensors = SensorSnapshot(),
                energy = null,
                project = ProjectKnowledge.projects.firstOrNull()?.name,
                memory = memory.recent(),
            )
            memory.add(
                ClochetteMemoryEntry(
                    context = "home_widget",
                    observedSignal = "widget_tap",
                    project = ProjectKnowledge.projects.firstOrNull()?.name,
                    energy = null,
                    clochetteLine = line,
                    userReaction = "tap",
                    result = "spoken_from_widget",
                ),
            )
            saveLatestLine(context, line)
            ClochetteVoice.speak(context, line)
            updateAll(context, line)
        }
    }

    companion object {
        const val ACTION_REMARK = "com.feuch.clochette.ACTION_WIDGET_REMARK"
        private const val PREFS = "clochette_widget"
        private const val KEY_LINE = "latest_line"
        private const val DEFAULT_LINE = "Clochette attend sur l'ecran d'accueil. C'est suspectement raisonnable."

        fun updateAll(context: Context, line: String = latestLine(context)) {
            saveLatestLine(context, line)
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, ClochetteWidget::class.java)
            manager.getAppWidgetIds(component).forEach { update(context, manager, it, line) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int, line: String) {
            val views = RemoteViews(context.packageName, R.layout.widget_clochette)
            views.setTextViewText(R.id.widgetLine, line)
            views.setOnClickPendingIntent(R.id.widgetRoot, remarkIntent(context))
            views.setOnClickPendingIntent(R.id.widgetSprite, remarkIntent(context))
            views.setOnClickPendingIntent(R.id.widgetLine, remarkIntent(context))
            manager.updateAppWidget(id, views)
        }

        private fun remarkIntent(context: Context): PendingIntent {
            val intent = Intent(context, ClochetteWidget::class.java).setAction(ACTION_REMARK)
            return PendingIntent.getBroadcast(
                context,
                42,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun latestLine(context: Context): String = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LINE, DEFAULT_LINE) ?: DEFAULT_LINE

        private fun saveLatestLine(context: Context, line: String) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LINE, line)
                .apply()
        }
    }
}
