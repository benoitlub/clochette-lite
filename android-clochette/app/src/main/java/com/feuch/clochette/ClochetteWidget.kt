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
            val voiceConfig = ClochetteVoiceSettings.read(context)
            val line = ClochetteEngine.remark(
                activity = UsageObserver(context).snapshot(),
                sensors = SensorSnapshot(),
                energy = null,
                project = ProjectKnowledge.projects.firstOrNull()?.name,
                memory = memory.recent(24),
                phraseLength = voiceConfig.phraseLength,
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
            updateAll(context, line)
            ClochetteVoice.speakAfterRemark(context, line)
        }
    }

    companion object {
        const val ACTION_REMARK = "com.feuch.clochette.ACTION_WIDGET_REMARK"

        fun updateAll(context: Context, line: String = latestLine(context)) {
            ClochetteRemarkStore.announce(context, line)
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

        private fun latestLine(context: Context): String = ClochetteRemarkStore.latest(context)
    }
}
