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
import kotlin.concurrent.thread
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
            val config = RelationshipModeSettings.effectiveConfig(this@ClochetteProactiveService)
            val mode = RelationshipModeSettings.selected(this@ClochetteProactiveService)
            handler.postDelayed(this, nextDelayMillis(config.frequency, mode.cooldownMultiplier))
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
        val savedConfig = ProactiveSettings.read(this).copy(mode = ProactiveMode.OBSERVE)
        ProactiveSettings.save(this, savedConfig)
        val config = RelationshipModeSettings.effectiveConfig(this, savedConfig)
        val relationshipMode = RelationshipModeSettings.selected(this)
        running = true
        startForeground(NOTIFICATION_ID, buildNotification(config, relationshipMode))
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed(tick, nextDelayMillis(config.frequency, relationshipMode.cooldownMultiplier))
    }

    private fun pause() {
        ProactiveSettings.save(this, ProactiveSettings.read(this).copy(mode = ProactiveMode.PAUSE))
        running = false
        handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun maybeIntervene() {
        val config = RelationshipModeSettings.effectiveConfig(this)
        val relationshipMode = RelationshipModeSettings.selected(this)
        if (config.mode != ProactiveMode.OBSERVE) return
        if (!config.voiceInterventions && !config.spontaneousQuestions) return

        val activity = usageObserver.snapshot()
        val contextEngine = ContextRemarkEngine(this)
        val state = contextEngine.buildState(activity)
        val recentMemory = memory.recent(24)
        val question = config.spontaneousQuestions &&
            Random.nextInt(100) < questionChance(relationshipMode.questionFrequency)

        val aiConfig = AiGatewaySettings.read(this)
        val mayUseAi = aiConfig.enabled &&
            !question &&
            (relationshipMode.id == "alive" || config.frequency == ProactiveFrequency.BAVARDE)
        if (mayUseAi) {
            val nowPlaying = NowPlayingObserver.snapshot(this)
            val request = AiRemarkRequest(
                relationshipMode = relationshipMode.id,
                preferredProvider = aiConfig.preferredProvider,
                styleLevel = aiConfig.styleLevel,
                foregroundApp = state.currentAppName,
                durationMinutes = state.durationMinutes,
                appSwitchCount = state.recentAppSwitches,
                sensorSummary = "movement=${state.movementState.name.lowercase()} battery=${state.batteryPercent ?: "unknown"}",
                energy = null,
                recentMemorySummary = recentMemory.mapNotNull { it.clochetteLine }.takeLast(3).joinToString(" | "),
                nowPlayingAppName = nowPlaying.appName,
                nowPlayingTitle = nowPlaying.title,
                nowPlayingArtist = nowPlaying.artist,
            )
            thread(name = "clochette-proactive-ai") {
                val aiResult = AiGatewayClient(this@ClochetteProactiveService).generateRemark(request)
                handler.post {
                    if (aiResult != null) {
                        handleCandidate(
                            candidateLine = aiResult.line,
                            source = aiResult.source,
                            question = aiResult.shouldOpenMic && aiResult.line.contains("?"),
                            state = state,
                            recentMemory = recentMemory,
                            relationshipMode = relationshipMode,
                            wantsVoice = config.voiceInterventions && aiResult.shouldSpeak,
                            openMicAfter = aiResult.shouldOpenMic,
                        )
                    } else {
                        handleLocalCandidate(question, state, recentMemory, relationshipMode, config)
                    }
                }
            }
            return
        }

        handleLocalCandidate(question, state, recentMemory, relationshipMode, config)
    }

    private fun handleLocalCandidate(
        question: Boolean,
        state: ContextState,
        recentMemory: List<ClochetteMemoryEntry>,
        relationshipMode: RelationshipMode,
        config: ProactiveConfig,
    ) {
        val activity = usageObserver.snapshot()
        val contextEngine = ContextRemarkEngine(this)
        var source = if (question) PhraseSource.PROACTIVE_QUESTION else PhraseSource.UNKNOWN
        val candidateLine = if (question) {
            ProactiveQuestionEngine.question(state, journal.recent(12))
        } else {
            contextEngine.remark(activity, recentMemory)?.also {
                source = contextEngine.lastSource()
            } ?: ClochetteEngine.remark(
                activity = activity,
                sensors = SensorSnapshot(),
                energy = null,
                project = null,
                memory = recentMemory,
                phraseLength = ClochetteVoiceSettings.read(this).phraseLength,
            ).also {
                source = PhraseSource.CLOCHETTE_ENGINE
            }
        }
        handleCandidate(
            candidateLine = candidateLine,
            source = source,
            question = question,
            state = state,
            recentMemory = recentMemory,
            relationshipMode = relationshipMode,
            wantsVoice = config.voiceInterventions,
            openMicAfter = question,
        )
    }

    private fun handleCandidate(
        candidateLine: String,
        source: PhraseSource,
        question: Boolean,
        state: ContextState,
        recentMemory: List<ClochetteMemoryEntry>,
        relationshipMode: RelationshipMode,
        wantsVoice: Boolean,
        openMicAfter: Boolean,
    ) {
        var effectiveSource = source
        val decision = GuardianRulesLoader(this).approve(
            candidate = candidateLine,
            state = state,
            recentLines = recentMemory.mapNotNull { it.clochetteLine },
            recentEntries = recentMemory,
            relationshipMode = relationshipMode,
            wantsVoice = wantsVoice,
        )
        val line = decision.line ?: return
        if (line != candidateLine || decision.reason != "approved") {
            effectiveSource = PhraseSource.GUARDIAN_FALLBACK
        }

        ClochetteWidget.updateAll(this, line, effectiveSource)
        if (decision.shouldSpeak) ClochetteVoice.speakProactive(this, line)
        if (openMicAfter && question && line.contains("?")) {
            startActivity(
                Intent(this, VoiceReplyActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        memory.add(
            ClochetteMemoryEntry(
                context = "proactive_service",
                observedSignal = if (question) "spontaneous_question" else "spontaneous_remark",
                project = null,
                energy = null,
                clochetteLine = line,
                userReaction = null,
                result = decision.reason,
            ),
        )
        journal.add(
            ObservationJournalEntry(
                activity = state.currentAppName,
                question = line.takeIf { question },
                reaction = decision.reason,
                result = "proactive",
            ),
        )
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
        val minutes = when (frequency) {
            ProactiveFrequency.DISCRETE -> Random.nextLong(18L, 21L)
            ProactiveFrequency.NORMALE -> Random.nextLong(13L, 18L)
            ProactiveFrequency.BAVARDE -> Random.nextLong(10L, 13L)
        }
        return (minutes * cooldownMultiplier).toLong().coerceAtLeast(10L) * 60_000L
    }

    private fun questionChance(questionFrequency: String): Int = when (questionFrequency) {
        "high" -> 65
        "medium" -> 50
        "low" -> 25
        else -> 10
    }

    companion object {
        const val ACTION_OBSERVE = "com.feuch.clochette.proactive.OBSERVE"
        const val ACTION_PAUSE = "com.feuch.clochette.proactive.PAUSE"
        private const val CHANNEL_ID = "clochette_proactive"
        private const val NOTIFICATION_ID = 41
    }
}
