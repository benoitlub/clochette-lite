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

        val approved = decision.line != null && decision.reason == "approved"
        val bypassForSafeTest = force && safeTest && !approved
        val finalLine = when {
            approved -> decision.line.orEmpty()
            bypassForSafeTest -> generated.line
            decision.line != null -> decision.line
            else -> generated.line
        }.withVisibleFrenchAccents()
        val finalSource = when {
            bypassForSafeTest -> PhraseSource.LOCAL_PROACTIVE_TEST
            decision.line != null && decision.reason != "approved" -> PhraseSource.GUARDIAN_FALLBACK
            else -> generated.source
        }
        val guardianReason = if (bypassForSafeTest) "approved_test_bypass_${decision.reason}" else decision.reason
        val shouldSpeak = ClochetteVoiceSettings.read(appContext).enabled &&
            (decision.shouldSpeak || force || bypassForSafeTest)
        val shouldOpenMic = openMic && finalLine.contains("?") && relationshipMode.id == "alive"

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
            appContext.startActivity(
                Intent(appContext, VoiceReplyActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
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
            )
        }
        if (force) {
            return GeneratedLine(localProactiveLine(state), PhraseSource.LOCAL_PROACTIVE)
        }
        val question = RelationshipModeSettings.effectiveConfig(context).spontaneousQuestions
        if (question) {
            return GeneratedLine(
                "Je suis là. Tu veux que je reste discrète ou que je t’aide à reprendre le fil ?",
                PhraseSource.LOCAL_PROACTIVE,
            )
        }
        val activity = UsageObserver(context).snapshot()
        val line = contextEngine.remark(activity, recentMemory)
        return if (line != null) {
            GeneratedLine(line, contextEngine.lastSource())
        } else {
            GeneratedLine(localProactiveLine(state), PhraseSource.LOCAL_PROACTIVE)
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

    private data class GeneratedLine(
        val line: String,
        val source: PhraseSource,
    )
}
