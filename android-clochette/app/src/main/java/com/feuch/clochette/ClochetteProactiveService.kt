package com.feuch.clochette

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlin.random.Random

class ClochetteProactiveService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var usageObserver: UsageObserver
    private lateinit var memory: ClochetteMemory
    private lateinit var journal: ObservationJournal
    private var running = false

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            maybeIntervene()
            handler.postDelayed(this, nextDelayMillis(ProactiveSettings.read(this@ClochetteProactiveService).frequency))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        usageObserver = UsageObserver(this)
        memory = ClochetteMemory(this)
        journal = ObservationJournal(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pause()
            else -> observe()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observe() {
        val config = ProactiveSettings.read(this).copy(mode = ProactiveMode.OBSERVE)
        ProactiveSettings.save(this, config)
        running = true
        startForeground(NOTIFICATION_ID, buildNotification(config))
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(tick, nextDelayMillis(config.frequency))
    }

    private fun pause() {
        ProactiveSettings.save(this, ProactiveSettings.read(this).copy(mode = ProactiveMode.PAUSE))
        running = false
        handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun maybeIntervene() {
        val config = ProactiveSettings.read(this)
        if (config.mode != ProactiveMode.OBSERVE) return
        if (!config.voiceInterventions && !config.spontaneousQuestions) return

        val activity = usageObserver.snapshot()
        val state = ContextRemarkEngine(this).buildState(activity)
        val question = config.spontaneousQuestions && Random.nextInt(100) < questionChance(config.frequency)
        val line = if (question) {
            ProactiveQuestionEngine.question(state, journal.recent(12))
        } else {
            ContextRemarkEngine(this).remark(activity, memory.recent(24)) ?: ClochetteEngine.remark(
                activity = activity,
                sensors = SensorSnapshot(),
                energy = null,
                project = null,
                memory = memory.recent(24),
                phraseLength = ClochetteVoiceSettings.read(this).phraseLength,
            )
        }

        ClochetteRemarkStore.announce(this, line)
        ClochetteWidget.updateAll(this, line)
        if (config.voiceInterventions) ClochetteVoice.speakAfterRemark(this, line)

        memory.add(
            ClochetteMemoryEntry(
                context = "proactive_service",
                observedSignal = if (question) "spontaneous_question" else "spontaneous_remark",
                project = null,
                energy = null,
                clochetteLine = line,
                userReaction = null,
                result = "shown",
            ),
        )
        journal.add(
            ObservationJournalEntry(
                activity = state.currentAppName,
                question = line.takeIf { question },
                reaction = if (config.voiceInterventions) "spoken" else "silent",
                result = "proactive",
            ),
        )
    }

    private fun buildNotification(config: ProactiveConfig): Notification {
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
            .setContentText("Mode $mode · fréquence ${config.frequency.name.lowercase()}")
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

    private fun nextDelayMillis(frequency: ProactiveFrequency): Long {
        val minutes = when (frequency) {
            ProactiveFrequency.DISCRETE -> Random.nextLong(18L, 21L)
            ProactiveFrequency.NORMALE -> Random.nextLong(13L, 18L)
            ProactiveFrequency.BAVARDE -> Random.nextLong(10L, 13L)
        }
        return minutes * 60_000L
    }

    private fun questionChance(frequency: ProactiveFrequency): Int = when (frequency) {
        ProactiveFrequency.DISCRETE -> 35
        ProactiveFrequency.NORMALE -> 50
        ProactiveFrequency.BAVARDE -> 65
    }

    companion object {
        const val ACTION_OBSERVE = "com.feuch.clochette.proactive.OBSERVE"
        const val ACTION_PAUSE = "com.feuch.clochette.proactive.PAUSE"
        private const val CHANNEL_ID = "clochette_proactive"
        private const val NOTIFICATION_ID = 41
    }
}
