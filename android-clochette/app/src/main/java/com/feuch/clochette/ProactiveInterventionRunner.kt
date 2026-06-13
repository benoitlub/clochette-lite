package com.feuch.clochette

import android.content.Context
import android.content.Intent

data class ProactiveInterventionResult(
    val originalLine: String,
    val finalLine: String,
    val source: PhraseSource,
    val guardianReason: String,
    val shouldSpeak: Boolean,
    val voiceStatus: String,
    val shouldOpenMic: Boolean,
    val providerUsed: String,
)

object ProactiveInterventionRunner {
    fun run(
        context: Context,
        force: Boolean = false,
        safeTest: Boolean = false,
        openMic: Boolean = true,
    ): ProactiveInterventionResult {
        val appContext = context.applicationContext
        ClochetteRuntimeStatus.recordTick(appContext)

        val usage = UsageObserver(appContext).snapshot()
        val memory = ClochetteMemory(appContext)
        val recentMemory = memory.recent(24)
        val contextEngine = ContextRemarkEngine(appContext)
        val state = contextEngine.buildState(usage)
        val relationshipMode = RelationshipModeSettings.selected(appContext)
        val proactiveConfig = RelationshipModeSettings.effectiveConfig(appContext)

        val generated = generateLine(
            context = appContext,
            force = force,
            safeTest = safeTest,
            state = state,
            recentMemory = recentMemory,
            contextEngine = contextEngine,
        )

        val guardianMode = if (force) relationshipMode.copy(voiceDefault = true) else relationshipMode
        val decision = GuardianRulesLoader(appContext).approve(
            candidate = generated.line,
            state = state,
            recentLines = if (force) emptyList() else recentMemory.mapNotNull { it.clochetteLine },
            recentEntries = if (force) emptyList() else recentMemory,
            relationshipMode = guardianMode,
            wantsVoice = force || proactiveConfig.voiceInterventions,
            allowTestOverride = safeTest,
        )

        val repeatSoftened = decision.reason == "anti_repeat" &&
            generated.source in setOf(PhraseSource.LOCAL_PROACTIVE, PhraseSource.PROACTIVE_QUESTION) &&
            proactiveConfig.voiceInterventions
        val approved = decision.line != null && decision.reason == "approved" || repeatSoftened
        val bypassForSafeTest = force && safeTest && !approved
        val finalLine = when {
            repeatSoftened -> generated.line
            approved -> decision.line.orEmpty()
            bypassForSafeTest -> generated.line
            decision.line != null -> decision.line
            else -> generated.line
        }.withVisibleFrenchAccents()
        val finalSource = when {
            bypassForSafeTest -> PhraseSource.LOCAL_PROACTIVE_TEST
            repeatSoftened -> generated.source
            decision.line != null && decision.reason != "approved" -> PhraseSource.GUARDIAN_FALLBACK
            else -> generated.source
        }
        val guardianReason = when {
            bypassForSafeTest -> "approved_test_bypass_${decision.reason}"
            repeatSoftened -> "approved_repeat_softened"
            else -> decision.reason
        }
        val shouldSpeak = ClochetteVoiceSettings.read(appContext).enabled &&
            (decision.shouldSpeak || force || bypassForSafeTest || repeatSoftened)
        val shouldOpenMic = openMic &&
            generated.canOpenMic &&
            finalLine.contains("?") &&
            relationshipMode.id == "alive"

        ClochetteRuntimeStatus.recordDecision(appContext, guardianReason, shouldSpeak)
        ClochetteWidget.updateAll(appContext, finalLine, finalSource)
        memory.add(
            ClochetteMemoryEntry(
                context = "proactive_runner",
                observedSignal = if (force) "forced_proactive" else "proactive",
                project = null,
                energy = null,
                clochetteLine = finalLine,
                userReaction = null,
                result = guardianReason,
            ),
        )

        val voiceStatus = if (shouldSpeak) {
            ClochetteVoice.speakProactive(appContext, finalLine)
            ClochetteRuntimeStatus.recordVoiceAction(appContext, "spoken")
            "spoken"
        } else {
            val status = if (!ClochetteVoiceSettings.read(appContext).enabled) {
                "skipped_voice_disabled"
            } else {
                "skipped_guardian_$guardianReason"
            }
            ClochetteRuntimeStatus.recordVoiceAction(appContext, status)
            status
        }

        if (shouldOpenMic) {
            ClochetteRuntimeStatus.recordAction(appContext, "micro ouvert")
            appContext.startService(
                Intent(appContext, ClochetteOverlayService::class.java)
                    .setAction(ClochetteOverlayService.ACTION_OPEN_MIC),
            )
        }

        return ProactiveInterventionResult(
            originalLine = generated.line,
            finalLine = finalLine,
            source = finalSource,
            guardianReason = guardianReason,
            shouldSpeak = shouldSpeak,
            voiceStatus = voiceStatus,
            shouldOpenMic = shouldOpenMic,
            providerUsed = AiGatewaySettings.read(appContext).lastProviderUsed ?: "aucun",
        )
    }

    private fun generateLine(
        context: Context,
        force: Boolean,
        safeTest: Boolean,
        state: ContextState,
        recentMemory: List<ClochetteMemoryEntry>,
        contextEngine: ContextRemarkEngine,
    ): GeneratedLine {
        if (safeTest) {
            return GeneratedLine(
                "Test vocal réussi. Je sais faire autre chose que décorer l’écran.",
                PhraseSource.LOCAL_PROACTIVE_TEST,
                canOpenMic = false,
            )
        }

        val relationshipMode = RelationshipModeSettings.selected(context)
        val proactiveConfig = RelationshipModeSettings.effectiveConfig(context)
        val preferQuestion = force || proactiveConfig.spontaneousQuestions
        val bankTrigger = if (force) "proactive_test" else "proactive_tick"
        PhraseBankSelector.select(
            context = context,
            trigger = bankTrigger,
            state = state,
            relationshipMode = relationshipMode,
            recentMemory = recentMemory,
            preferQuestion = preferQuestion,
        )?.let { selection ->
            return GeneratedLine(
                line = selection.line,
                source = selection.source,
                canOpenMic = selection.canAskMic && selection.source == PhraseSource.PROACTIVE_QUESTION,
            )
        }

        if (force) {
            return GeneratedLine(localProactiveLine(state), PhraseSource.LOCAL_PROACTIVE, canOpenMic = false)
        }
        if (proactiveConfig.spontaneousQuestions) {
            return GeneratedLine(
                localQuestionLine(state),
                PhraseSource.LOCAL_PROACTIVE,
                canOpenMic = true,
            )
        }
        val activity = UsageObserver(context).snapshot()
        val line = contextEngine.remark(activity, recentMemory)
        return if (line != null) {
            GeneratedLine(line, contextEngine.lastSource(), canOpenMic = false)
        } else {
            GeneratedLine(localProactiveLine(state), PhraseSource.LOCAL_PROACTIVE, canOpenMic = false)
        }
    }

    private fun localProactiveLine(state: ContextState): String {
        val app = state.currentAppName?.takeIf { it.isNotBlank() } ?: "cette appli"
        return when {
            state.recentAppSwitches >= 4 ->
                "Tu changes souvent d’application. Tu cherches quelque chose ou tu évites quelque chose ?"
            state.durationMinutes >= 20 ->
                "Je vois que tu es sur $app depuis un moment. Tu veux continuer ou faire une pause ?"
            else ->
                "Je suis là. Tu veux que je reste discrète ou que je t’aide à reprendre le fil ?"
        }.withVisibleFrenchAccents()
    }

    private fun localQuestionLine(state: ContextState): String {
        val app = state.currentAppName?.takeIf { it.isNotBlank() } ?: "cette appli"
        return listOf(
            "Je suis là. Tu veux que je reste discrète ou que je t’aide à reprendre le fil ?",
            "Je remarque $app depuis un moment. Tu continues ou tu veux une pause propre ?",
            "Je peux me tromper, mais tu tournes autour du sujet. Tu veux nommer le prochain geste ?",
            "Tu veux répondre maintenant, ou je garde mon nez dans mes affaires trente secondes ?",
            "Hypothèse : tu cherches l’élan. Je lance le micro ou je te laisse respirer ?",
        ).random().withVisibleFrenchAccents()
    }

    private data class GeneratedLine(
        val line: String,
        val source: PhraseSource,
        val canOpenMic: Boolean,
    )
}
