package com.feuch.clochette

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ClochettePresenceService : Service() {
    private lateinit var usageObserver: UsageObserver
    private lateinit var sensorObserver: SensorObserver
    private lateinit var memory: ClochetteMemory

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        usageObserver = UsageObserver(this)
        sensorObserver = SensorObserver(this)
        memory = ClochetteMemory(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pauseClochette()
            else -> startObserving()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        sensorObserver.stop()
        setState(ClochetteState.ASLEEP)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startObserving() {
        setState(ClochetteState.OBSERVING)
        sensorObserver.start()
        startForeground(NOTIFICATION_ID, buildNotification("observe"))

        val activity = usageObserver.snapshot()
        val sensors = sensorObserver.snapshot()
        memory.add(
            ClochetteMemoryEntry(
                context = "presence_service",
                observedSignal = listOfNotNull(
                    activity.foregroundPackage?.let { "foreground=$it" },
                    "switches=${activity.recentSwitchCount}",
                    "screen_active=${sensors.screenActive}",
                ).joinToString(", "),
                project = null,
                energy = null,
                clochetteLine = null,
                userReaction = null,
                result = "observe",
            ),
        )
    }

    private fun pauseClochette() {
        sensorObserver.stop()
        setState(ClochetteState.PAUSED)
        memory.add(
            ClochetteMemoryEntry(
                context = "presence_service",
                observedSignal = "pause_from_notification",
                project = null,
                energy = null,
                clochetteLine = null,
                userReaction = "pause",
                result = "paused",
            ),
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(state: String): Notification {
        val pauseIntent = Intent(this, ClochettePresenceService::class.java).setAction(ACTION_PAUSE)
        val pausePendingIntent = PendingIntent.getService(
            this,
            1,
            pauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clochette)
            .setContentTitle("Clochette $state")
            .setContentText("Presence visible active. Elle observe des signaux simples, pas le contenu.")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_stat_clochette, "Pause Clochette", pausePendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Presence Clochette",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun setState(state: ClochetteState) {
        getSharedPreferences("clochette_state", Context.MODE_PRIVATE)
            .edit()
            .putString("state", state.name)
            .apply()
    }

    companion object {
        const val ACTION_PAUSE = "com.feuch.clochette.PAUSE"
        private const val CHANNEL_ID = "clochette_presence"
        private const val NOTIFICATION_ID = 31
    }
}
