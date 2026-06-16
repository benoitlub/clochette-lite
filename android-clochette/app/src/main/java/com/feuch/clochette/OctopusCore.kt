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
    val phraseBankId: String = "",
    val phraseEntryId: String = "",
    val phraseTone: String = "",
    val appearanceId: String = "",
    val appearanceRole: String = "",
    val appearancePath: String = "",
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
        val activeCharacter = CharacterRegistry.get(appContext, CharacterSettings.read(appContext).activeCharacterId)
        val conversation = transcription
            ?.takeIf { it.isNotBlank() }
            ?.let {
                VoiceInteractionController.transition(appContext, VoiceInteractionState.THINKING, "voice_transcription")
                ConversationContextStore.analyzeAndStore(
                    context = appContext,
                    userText = it,
                    characterId = activeCharacter.id,
                    lastAvatarLine = ClochetteRemarkStore.latest(appContext),
                )
            }

        val aiResult = if (trigger == TRIGGER_GATEWAY_TEST && canUseGateway(aiConfig)) {
            runCatching { AiGatewayClient(appContext).generateRemark(buildRequest(appContext, state, recentMemory, transcription)) }.getOrNull()
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
                replyToTranscription(activeCharacter, conversation ?: ConversationContext(userText = transcription.orEmpty())),
                PhraseSource.LOCAL_NATURAL,
                "local",
                forceSpeak,
                false,
                15,
                entryId = "conversation_${conversation?.intent ?: "unknown"}_${conversation?.mood ?: "neutral"}",
                bankId = "user_answer_reactions",
                tone = conversation?.tags?.joinToString(",").orEmpty(),
            )
            else -> localGenerated(appContext, trigger, state, relationshipMode, contextEngine, usage, recentMemory)
        }

        val casting = CharacterDirector.choose(
            context = appContext,
            trigger = trigger,
            state = state,
            baseLine = generated.line.withVisibleFrenchAccents(),
            source = generated.source,
        )

        val guardian = GuardianRulesLoader(appContext).approve(
            candidate = casting.phrase.withVisibleFrenchAccents(),
            state = state,
            recentLines = if (trigger == TRIGGER_SAFE_VOICE_TEST) emptyList() else recentMemory.mapNotNull { it.clochetteLine },
            recentEntries = if (trigger == TRIGGER_SAFE_VOICE_TEST) emptyList() else recentMemory,
            relationshipMode = if (forceSpeak) relationshipMode.copy(voiceDefault = true) else relationshipMode,
            wantsVoice = generated.wantsVoice || forceSpeak,
            allowTestOverride = trigger == TRIGGER_SAFE_VOICE_TEST,
        )

        val finalLine = (guardian.line ?: casting.phrase).withVisibleFrenchAccents()
        val source = if (guardian.reason == "approved") generated.source else PhraseSource.GUARDIAN_FALLBACK
        val shouldSpeak = ClochetteVoiceSettings.read(appContext).enabled &&
            VoiceInteractionController.canSpeak(appContext) &&
            (guardian.shouldSpeak || forceSpeak || trigger == TRIGGER_SAFE_VOICE_TEST)
        val shouldOpenMic = openMic || generated.openMic && finalLine.contains("?")

        ClochetteWidget.updateAll(appContext, finalLine, source, casting.character.id)
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

        val bankDiagnostic = if (generated.bankId.isNotBlank()) {
            " | bank=${generated.bankId} | entry=${generated.entryId} | tone=${generated.tone} | score=${"%.1f".format(generated.selectionScore)} | rejectedRecent=${generated.rejectedRecent}"
        } else {
            ""
        }
        val conversationDiagnostic = conversation?.let {
            " | userMood=${it.mood} | intent=${it.intent} | energy=${it.energy} | tags=${it.tags.joinToString("+")}"
        }.orEmpty()
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
            diagnosticText = "trigger=$trigger | character=${casting.character.id} | source=${source.id} | provider=${generated.provider} | guardian=${guardian.reason} | voix=$voiceStatus | casting=${casting.reason}$bankDiagnostic$conversationDiagnostic",
            phraseBankId = generated.bankId,
            phraseEntryId = generated.entryId,
            phraseTone = generated.tone,
            appearanceId = casting.character.id,
            appearanceRole = if (shouldOpenMic) "listening" else casting.visualState,
            appearancePath = "character:${casting.character.id}",
        )
        OctopusDiagnosticsStore.save(appContext, decision.toDiagnostics(trigger, transcription, aiConfig, conversation, generated))
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

    private fun canUseGateway(config: AiGatewayConfig): Boolean =
        config.enabled &&
            config.gatewayUrl.isNotBlank() &&
            config.preferredProvider != AiGatewaySettings.PROVIDER_LOCAL

    private fun localGenerated(
        context: Context,
        trigger: String,
        state: ContextState,
        relationshipMode: RelationshipMode,
        contextEngine: ContextRemarkEngine,
        usage: ActivitySnapshot,
        recentMemory: List<ClochetteMemoryEntry>,
    ): Generated {
        val proactiveConfig = RelationshipModeSettings.effectiveConfig(context)
        val activeCharacter = CharacterRegistry.get(context, CharacterSettings.read(context).activeCharacterId)
        val conversation = ConversationContextStore.latest(context)
        val preferQuestion = trigger == TRIGGER_PROACTIVE_TEST || proactiveConfig.spontaneousQuestions
        if (trigger != TRIGGER_GATEWAY_TEST) {
            PhraseBankSelector.select(
                context = context,
                trigger = trigger,
                state = state,
                relationshipMode = relationshipMode,
                recentMemory = recentMemory,
                preferQuestion = preferQuestion,
                preferredTone = preferredToneFor(activeCharacter),
                character = activeCharacter,
                conversation = conversation,
            )?.let { selection ->
                return Generated(
                    line = selection.line,
                    source = selection.source,
                    provider = "local",
                    wantsVoice = selection.canSpeak || trigger == TRIGGER_PROACTIVE_TEST,
                    openMic = selection.canAskMic,
                    listenSeconds = 15,
                    entryId = selection.id,
                    bankId = selection.bankId,
                    tone = selection.tone,
                    selectionScore = selection.score,
                    selectionReason = selection.reason,
                    rejectedRecent = selection.rejectedRecent,
                )
            }
        }

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

    private fun preferredToneFor(character: CharacterProfile): String? = when {
        "sarcastic" in character.toneTags -> "teasing"
        "chaotic" in character.toneTags -> "badass"
        "soft" in character.toneTags -> "soft"
        "direct" in character.toneTags -> "focus"
        "curious" in character.toneTags -> "micro_questions"
        else -> null
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

    private fun replyToTranscription(character: CharacterProfile, conversation: ConversationContext): String =
        CharacterReplyStyle.reply(character.id, conversation)

    private fun OctopusDecision.toDiagnostics(
        trigger: String,
        transcription: String?,
        aiConfig: AiGatewayConfig,
        conversation: ConversationContext?,
        generated: Generated,
    ): OctopusDiagnostics =
        OctopusDiagnostics(
            lastTrigger = trigger,
            lastOriginalLine = originalLine,
            lastFinalLine = finalLine,
            lastPhraseSource = phraseSource.id,
            lastPhraseBankId = phraseBankId,
            lastPhraseEntryId = phraseEntryId,
            lastPhraseTone = phraseTone,
            lastProviderUsed = providerUsed,
            lastGuardianReason = guardianReason,
            lastShouldSpeak = shouldSpeak,
            lastVoiceStatus = voiceStatus,
            lastOverlayState = overlayState,
            lastMicStatus = if (shouldOpenMic) "ouvert" else "fermé",
            lastTranscription = transcription.orEmpty(),
            lastGatewayStatus = aiConfig.lastStatus ?: if (aiConfig.enabled) "non testé" else "désactivée",
            lastAppearance = "$appearanceId/$appearanceRole/$appearancePath",
            lastError = aiConfig.lastError.orEmpty(),
            lastUserIntent = conversation?.intent.orEmpty(),
            lastUserMood = conversation?.mood.orEmpty(),
            lastUserEnergy = conversation?.energy.orEmpty(),
            lastSelectedTags = conversation?.tags?.joinToString("+").orEmpty(),
            lastSelectionReason = generated.selectionReason.ifBlank { diagnosticText },
            updatedAt = System.currentTimeMillis(),
        )

    private data class Generated(
        val line: String,
        val source: PhraseSource,
        val provider: String,
        val wantsVoice: Boolean,
        val openMic: Boolean,
        val listenSeconds: Int,
        val entryId: String = "",
        val bankId: String = "",
        val tone: String = "",
        val selectionScore: Double = 0.0,
        val selectionReason: String = "",
        val rejectedRecent: Int = 0,
    )
}
