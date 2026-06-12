package com.feuch.clochette

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlin.random.Random

class ClochetteProactiveService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            ProactiveInterventionRunner.run(this@ClochetteProactiveService)
            val config = RelationshipModeSettings.effectiveConfig(this@ClochetteProactiveService)
            val mode = RelationshipModeSettings.selected(this@ClochetteProactiveService)
            val delay = nextDelayMillis(config.frequency, mode.cooldownMultiplier)
            ClochetteRuntimeStatus.recordNextAttempt(this@ClochetteProactiveService, delay)
            handler.postDelayed(this, delay)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pause()
            ACTION_TEST_INTERVENTION -> {
                observe()
                ProactiveInterventionRunner.run(this, force = true)
            }
            ACTION_FORCE_SAFE_SPOKEN -> {
                observe()
                ProactiveInterventionRunner.run(this, force = true, safeTest = true, openMic = false)
            }
            else -> observe()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        ClochetteRuntimeStatus.recordProactiveActive(this, false)
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observe() {
        val savedConfig = ProactiveSettings.read(this).copy(mode = ProactiveMode.OBSERVE)
        ProactiveSettings.save(this, savedConfig)
        val config = RelationshipModeSettings.effectiveConfig(this, savedConfig)
        val relationshipMode = RelationshipModeSettings.selected(this)
        running = true
        ClochetteRuntimeStatus.recordProactiveActive(this, true)
        startForeground(NOTIFICATION_ID, buildNotification(config, relationshipMode))
        handler.removeCallbacksAndMessages(null)
        ClochetteRuntimeStatus.recordNextAttempt(this, FIRST_ATTEMPT_MS)
        handler.postDelayed(tick, FIRST_ATTEMPT_MS)
    }

    private fun pause() {
        ProactiveSettings.save(this, ProactiveSettings.read(this).copy(mode = ProactiveMode.PAUSE))
        running = false
        ClochetteRuntimeStatus.recordProactiveActive(this, false)
        ClochetteRuntimeStatus.recordVoiceAction(this, "skipped_mode")
        handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(config: ProactiveConfig, relationshipMode: RelationshipMode): Notification {
        val pauseIntent = PendingIntent.getService(
            this,
            41,
            Intent(this, ClochetteProactiveService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            42,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val mode = if (config.mode == ProactiveMode.OBSERVE) "observe" else "pause"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_clochette)
            .setContentTitle("Clochette veille")
            .setContentText("Mode $mode · présence ${relationshipMode.name.lowercase()}")
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_stat_clochette, "Pause", pauseIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Clochette active",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun nextDelayMillis(frequency: ProactiveFrequency, cooldownMultiplier: Double): Long {
        if (DEBUG_FAST_PROACTIVE) {
            val seconds = when (frequency) {
                ProactiveFrequency.DISCRETE -> 180L
                ProactiveFrequency.NORMALE -> 90L
                ProactiveFrequency.BAVARDE -> Random.nextLong(35L, 61L)
            }
            return (seconds * 1000L * cooldownMultiplier).toLong().coerceAtLeast(30_000L)
        }
        val minutes = when (frequency) {
            ProactiveFrequency.DISCRETE -> Random.nextLong(18L, 21L)
            ProactiveFrequency.NORMALE -> Random.nextLong(13L, 18L)
            ProactiveFrequency.BAVARDE -> Random.nextLong(10L, 13L)
        }
        return (minutes * cooldownMultiplier).toLong().coerceAtLeast(10L) * 60_000L
    }

    companion object {
        const val ACTION_TEST_INTERVENTION = "com.feuch.clochette.proactive.TEST_INTERVENTION"
        const val ACTION_FORCE_SAFE_SPOKEN = "com.feuch.clochette.proactive.FORCE_SAFE_SPOKEN"
        const val ACTION_OBSERVE = "com.feuch.clochette.proactive.OBSERVE"
        const val ACTION_PAUSE = "com.feuch.clochette.proactive.PAUSE"
        const val DEBUG_FAST_PROACTIVE = true
        private const val FIRST_ATTEMPT_MS = 10_000L
        private const val CHANNEL_ID = "clochette_proactive"
        private const val NOTIFICATION_ID = 41
    }
}
