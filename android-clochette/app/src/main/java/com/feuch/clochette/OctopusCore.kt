package com.feuch.clochette

import android.content.Context
import android.content.Intent

data class OctopusDecision(
    val originalLine: String,
    val finalLine: String,
    val phraseSource: PhraseSource,
    val providerUsed: String,
    val guardianReason: String,
    val shouldDisplay: Boolean,
    val shouldSpeak: Boolean,
    val shouldOpenMic: Boolean,
    val listenSeconds: Int,
    val overlayState: String,
    val voiceStatus: String,
    val diagnosticText: String,
)

object OctopusCore {
    const val TRIGGER_MANUAL_TAP = "manual_tap"
    const val TRIGGER_PROACTIVE_TICK = "proactive_tick"
    const val TRIGGER_PROACTIVE_TEST = "proactive_test"
    const val TRIGGER_SAFE_VOICE_TEST = "safe_voice_test"
    const val TRIGGER_OVERLAY_REPLY = "overlay_reply"
    const val TRIGGER_VOICE_TRANSCRIPTION = "voice_transcription"
    const val TRIGGER_NOW_PLAYING_DETECTED = "now_playing_detected"
    const val TRIGGER_GATEWAY_TEST = "gateway_test"

    fun intervene(
        context: Context,
        trigger: String,
        transcription: String? = null,
        forceSpeak: Boolean = false,
        openMic: Boolean = false,
    ): OctopusDecision {
        val appContext = context.applicationContext
        val usage = UsageObserver(appContext).snapshot()
        val memory = ClochetteMemory(appContext)
        val recentMemory = memory.recent(24)
        val contextEngine = ContextRemarkEngine(appContext)
        val state = contextEngine.buildState(usage)
        val aiConfig = AiGatewaySettings.read(appContext)
        val relationshipMode = RelationshipModeSettings.selected(appContext)

        val aiRequest = buildRequest(appContext, state, recentMemory, transcription)
        val aiResult = if (aiConfig.enabled && aiConfig.gatewayUrl.isNotBlank() && aiConfig.preferredProvider != AiGatewaySettings.PROVIDER_LOCAL) {
            runCatching { AiGatewayClient(appContext).generateRemark(aiRequest) }.getOrNull()
        } else {
            null
        }

        val generated = when {
            trigger == TRIGGER_SAFE_VOICE_TEST -> Generated(
                "Test Octopus réussi. Une seule décision, une seule phrase, zéro théâtre inutile.",
                PhraseSource.OCTOPUS_SAFE_TEST,
                "local",
                true,
                false,
                15,
            )
            aiResult != null -> Generated(
                aiResult.line,
                aiResult.source,
                aiResult.providerUsed,
                aiResult.shouldSpeak,
                aiResult.shouldOpenMic,
                aiResult.listenSeconds,
            )
            transcription?.isNotBlank() == true -> Generated(
                replyToTranscription(transcription),
                PhraseSource.LOCAL_NATURAL,
                "local",
                forceSpeak,
                false,
                15,
            )
            else -> localGenerated(trigger, state, contextEngine, usage, recentMemory)
        }

        val guardian = GuardianRulesLoader(appContext).approve(
            candidate = generated.line.withVisibleFrenchAccents(),
            state = state,
            recentLines = if (trigger == TRIGGER_SAFE_VOICE_TEST) emptyList() else recentMemory.mapNotNull { it.clochetteLine },
            recentEntries = if (trigger == TRIGGER_SAFE_VOICE_TEST) emptyList() else recentMemory,
            relationshipMode = if (forceSpeak) relationshipMode.copy(voiceDefault = true) else relationshipMode,
            wantsVoice = generated.wantsVoice || forceSpeak,
            allowTestOverride = trigger == TRIGGER_SAFE_VOICE_TEST,
        )

        val finalLine = (guardian.line ?: generated.line).withVisibleFrenchAccents()
        val source = if (guardian.reason == "approved") generated.source else PhraseSource.GUARDIAN_FALLBACK
        val shouldSpeak = ClochetteVoiceSettings.read(appContext).enabled &&
            (guardian.shouldSpeak || forceSpeak || trigger == TRIGGER_SAFE_VOICE_TEST)
        val shouldOpenMic = openMic || generated.openMic && finalLine.contains("?")

        ClochetteWidget.updateAll(appContext, finalLine, source)
        memory.add(
            ClochetteMemoryEntry(
                context = "octopus_core",
                observedSignal = trigger,
                project = null,
                energy = null,
                clochetteLine = finalLine,
                userReaction = transcription,
                result = guardian.reason,
            ),
        )

        val voiceStatus = if (shouldSpeak) {
            ClochetteVoice.speakProactive(appContext, finalLine)
            "spoken"
        } else {
            "silent"
        }
        ClochetteRuntimeStatus.recordDecision(appContext, guardian.reason, shouldSpeak)
        ClochetteRuntimeStatus.recordVoiceAction(appContext, voiceStatus)

        if (shouldOpenMic) {
            appContext.startService(
                Intent(appContext, ClochetteOverlayService::class.java)
                    .setAction(ClochetteOverlayService.ACTION_OPEN_MIC),
            )
        } else {
            appContext.startService(
                Intent(appContext, ClochetteOverlayService::class.java)
                    .setAction(ClochetteOverlayService.ACTION_SHOW)
                    .putExtra(ClochetteRemarkStore.EXTRA_LINE, finalLine),
            )
        }

        val decision = OctopusDecision(
            originalLine = generated.line,
            finalLine = finalLine,
            phraseSource = source,
            providerUsed = generated.provider,
            guardianReason = guardian.reason,
            shouldDisplay = true,
            shouldSpeak = shouldSpeak,
            shouldOpenMic = shouldOpenMic,
            listenSeconds = generated.listenSeconds,
            overlayState = if (shouldOpenMic) "micro" else "expanded",
            voiceStatus = voiceStatus,
            diagnosticText = "trigger=$trigger · source=${source.id} · provider=${generated.provider} · guardian=${guardian.reason} · voix=$voiceStatus",
        )
        OctopusDiagnosticsStore.save(appContext, decision.toDiagnostics(trigger, transcription, aiConfig))
        return decision
    }

    fun gatewayHealth(context: Context): GatewayHealthResult {
        val result = AiGatewayClient(context.applicationContext).health()
        val config = AiGatewaySettings.read(context)
        val current = OctopusDiagnosticsStore.read(context)
        OctopusDiagnosticsStore.save(
            context,
            current.copy(
                lastTrigger = TRIGGER_GATEWAY_TEST,
                lastProviderUsed = config.lastProviderUsed ?: "aucun",
                lastGatewayStatus = config.lastStatus ?: if (result.ok) "OK" else "Erreur",
                lastError = result.error.orEmpty(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return result
    }

    private fun localGenerated(
        trigger: String,
        state: ContextState,
        contextEngine: ContextRemarkEngine,
        usage: ActivitySnapshot,
        recentMemory: List<ClochetteMemoryEntry>,
    ): Generated {
        val contextLine = contextEngine.remark(usage, recentMemory)
        if (contextLine != null && trigger != TRIGGER_PROACTIVE_TEST) {
            return Generated(contextLine, contextEngine.lastSource(), "local", false, false, 15)
        }
        val app = state.currentAppName?.takeIf { it.isNotBlank() } ?: "cette appli"
        val line = when {
            state.recentAppSwitches >= 4 ->
                "Je remarque les bascules. Tu cherches quelque chose ou tu évites quelque chose ?"
            state.durationMinutes >= 20 ->
                "Tu es sur $app depuis un moment. Tu veux continuer ou faire un point rapide ?"
            trigger == TRIGGER_GATEWAY_TEST ->
                "Relais absent ou muet. Je passe en local, dignement."
            else ->
                "Je suis là. Tu veux que je reste discrète ou que je t’aide à reprendre le fil ?"
        }
        return Generated(line, PhraseSource.LOCAL_NATURAL, "local", trigger.contains("test"), line.contains("?"), 15)
    }

    private fun buildRequest(
        context: Context,
        state: ContextState,
        recentMemory: List<ClochetteMemoryEntry>,
        transcription: String?,
    ): AiRemarkRequest {
        val config = AiGatewaySettings.read(context)
        val nowPlaying = NowPlayingObserver.snapshot(context)
        return AiRemarkRequest(
            relationshipMode = RelationshipModeSettings.selected(context).id,
            preferredProvider = config.preferredProvider,
            styleLevel = config.styleLevel,
            foregroundApp = state.currentAppName,
            durationMinutes = state.durationMinutes,
            appSwitchCount = state.recentAppSwitches,
            sensorSummary = "movement=${state.movementState.name.lowercase()} battery=${state.batteryPercent ?: "unknown"}",
            energy = state.userEnergyEstimate.name.lowercase(),
            recentMemorySummary = recentMemory.mapNotNull { it.clochetteLine }.takeLast(3).joinToString(" | "),
            userLastReply = transcription,
            nowPlayingAppName = nowPlaying.appName,
            nowPlayingTitle = nowPlaying.title,
            nowPlayingArtist = nowPlaying.artist,
        )
    }

    private fun replyToTranscription(text: String): String {
        val lower = text.lowercase()
        return when {
            "pause" in lower || "fatigu" in lower -> "Je remarque la fatigue. On baisse le volume, pas l’ambition."
            "reprendre" in lower || "continuer" in lower -> "Très bien. Un geste simple, maintenant. Je surveille l’élan."
            "bug" in lower || "bloqu" in lower -> "Je soupçonne un bug. On nomme l’endroit exact, puis on attaque proprement."
            else -> "Je note. Je peux me tromper, mais ça ressemble à une piste exploitable."
        }
    }

    private fun OctopusDecision.toDiagnostics(
        trigger: String,
        transcription: String?,
        aiConfig: AiGatewayConfig,
    ): OctopusDiagnostics = OctopusDiagnostics(
        lastTrigger = trigger,
        lastOriginalLine = originalLine,
        lastFinalLine = finalLine,
        lastPhraseSource = phraseSource.id,
        lastProviderUsed = providerUsed,
        lastGuardianReason = guardianReason,
        lastShouldSpeak = shouldSpeak,
        lastVoiceStatus = voiceStatus,
        lastOverlayState = overlayState,
        lastMicStatus = if (shouldOpenMic) "ouvert" else "fermé",
        lastTranscription = transcription.orEmpty(),
        lastGatewayStatus = aiConfig.lastStatus ?: if (aiConfig.enabled) "non testé" else "désactivée",
        lastError = aiConfig.lastError.orEmpty(),
        updatedAt = System.currentTimeMillis(),
    )

    private data class Generated(
        val line: String,
        val source: PhraseSource,
        val provider: String,
        val wantsVoice: Boolean,
        val openMic: Boolean,
        val listenSeconds: Int,
    )
}
