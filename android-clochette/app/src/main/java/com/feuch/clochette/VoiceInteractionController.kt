package com.feuch.clochette

import android.content.Context
import android.util.Log

enum class VoiceInteractionState {
    IDLE,
    SPEAKING,
    LISTENING,
    TRANSCRIBING,
    THINKING,
    COOLDOWN,
    ERROR,
}

enum class VoiceTriggerSource {
    MICRO_BUTTON,
    AUTO_AFTER_TTS,
    PROACTIVE,
    DEBUG,
    WIDGET,
}

object VoiceInteractionController {
    private const val TAG = "ClochetteVoiceState"
    private const val PREFS = "clochette_voice_interaction"
    private const val KEY_STATE = "state"
    private const val KEY_UPDATED = "updated"
    private const val KEY_LAST_TRANSITION = "last_transition"
    private const val KEY_LAST_TAP = "last_tap"
    private const val KEY_LAST_TOUCH_TARGET = "last_touch_target"
    private const val KEY_LAST_VOICE_TRIGGER = "last_voice_trigger"
    private const val KEY_LAST_NO_SPEECH_AT = "last_no_speech_at"
    private const val KEY_LAST_NO_SPEECH_REASON = "last_no_speech_reason"
    private const val KEY_OVERLAY_MODE = "overlay_mode"
    private const val KEY_CAN_DRAG = "can_drag"
    private const val KEY_CAN_EXPAND = "can_expand"
    private const val KEY_RECOGNIZER_STATE = "recognizer_state"
    private const val KEY_TTS_STATE = "tts_state"
    private const val KEY_TTS_DONE_AT = "tts_done_at"
    private const val KEY_TTS_SPEAKING = "tts_speaking"
    private const val KEY_DEBUG_NON_MANUAL_MIC = "debug_non_manual_mic"
    private const val DEBOUNCE_MS = 450L
    private const val POST_TTS_SAFETY_MS = 650L

    fun state(context: Context): VoiceInteractionState {
        val raw = prefs(context).getString(KEY_STATE, VoiceInteractionState.IDLE.name)
        return runCatching { VoiceInteractionState.valueOf(raw ?: VoiceInteractionState.IDLE.name) }
            .getOrDefault(VoiceInteractionState.IDLE)
    }

    fun transition(context: Context, next: VoiceInteractionState, reason: String) {
        val appContext = context.applicationContext
        val previous = state(appContext)
        prefs(appContext).edit()
            .putString(KEY_STATE, next.name)
            .putLong(KEY_UPDATED, System.currentTimeMillis())
            .putString(KEY_LAST_TRANSITION, "$previous->$next:$reason")
            .apply()
        Log.d(TAG, "voice state $previous -> $next reason=$reason")
        ClochetteRuntimeStatus.recordAction(appContext, "voice_${next.name.lowercase()}_$reason")
    }

    fun markTtsStarted(context: Context, utteranceId: String) {
        prefs(context).edit()
            .putBoolean(KEY_TTS_SPEAKING, true)
            .putString(KEY_TTS_STATE, "SPEAKING:$utteranceId")
            .apply()
        transition(context, VoiceInteractionState.SPEAKING, "tts_on_start")
    }

    fun markTtsQueued(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_TTS_SPEAKING, true)
            .putString(KEY_TTS_STATE, "QUEUED")
            .apply()
        transition(context, VoiceInteractionState.SPEAKING, "tts_queued")
    }

    fun markTtsDone(context: Context, utteranceId: String) {
        prefs(context).edit()
            .putBoolean(KEY_TTS_SPEAKING, false)
            .putLong(KEY_TTS_DONE_AT, System.currentTimeMillis())
            .putString(KEY_TTS_STATE, "DONE:$utteranceId")
            .apply()
        transition(context, VoiceInteractionState.IDLE, "tts_on_done")
    }

    fun markTtsError(context: Context, utteranceId: String) {
        prefs(context).edit()
            .putBoolean(KEY_TTS_SPEAKING, false)
            .putLong(KEY_TTS_DONE_AT, System.currentTimeMillis())
            .putString(KEY_TTS_STATE, "ERROR:$utteranceId")
            .apply()
        transition(context, VoiceInteractionState.ERROR, "tts_error")
    }

    fun markRecognizerState(context: Context, recognizerState: String) {
        prefs(context).edit().putString(KEY_RECOGNIZER_STATE, recognizerState).apply()
        Log.d(TAG, "recognizer state=$recognizerState")
    }

    fun isTtsSpeakingNow(context: Context): Boolean =
        prefs(context).getBoolean(KEY_TTS_SPEAKING, false) ||
            state(context) == VoiceInteractionState.SPEAKING

    fun timeSinceTtsDoneMs(context: Context): Long {
        val doneAt = prefs(context).getLong(KEY_TTS_DONE_AT, 0L)
        return if (doneAt == 0L) Long.MAX_VALUE else (System.currentTimeMillis() - doneAt).coerceAtLeast(0L)
    }

    fun canStartListening(context: Context, source: VoiceTriggerSource): Boolean {
        val appContext = context.applicationContext
        val currentState = state(appContext)
        val debugEnabled = prefs(appContext).getBoolean(KEY_DEBUG_NON_MANUAL_MIC, false)
        val sourceAllowed = source == VoiceTriggerSource.MICRO_BUTTON ||
            (source == VoiceTriggerSource.DEBUG && debugEnabled)
        val ttsQuiet = !isTtsSpeakingNow(appContext)
        val safetyElapsed = timeSinceTtsDoneMs(appContext) >= POST_TTS_SAFETY_MS
        val stateAllows = currentState !in setOf(
            VoiceInteractionState.SPEAKING,
            VoiceInteractionState.LISTENING,
            VoiceInteractionState.TRANSCRIBING,
            VoiceInteractionState.THINKING,
        )
        val canStart = sourceAllowed && ttsQuiet && safetyElapsed && stateAllows
        prefs(appContext).edit().putString(KEY_LAST_VOICE_TRIGGER, source.name).apply()
        Log.d(
            TAG,
            "canStartListening=$canStart source=$source state=$currentState ttsQuiet=$ttsQuiet safetyMs=${timeSinceTtsDoneMs(appContext)}",
        )
        return canStart
    }

    fun canSpeak(context: Context): Boolean =
        state(context) !in setOf(VoiceInteractionState.LISTENING, VoiceInteractionState.TRANSCRIBING)

    fun shouldAcceptTap(context: Context): Boolean {
        val appContext = context.applicationContext
        val now = System.currentTimeMillis()
        val previous = prefs(appContext).getLong(KEY_LAST_TAP, 0L)
        if (now - previous < DEBOUNCE_MS) {
            Log.d(TAG, "tap debounced after ${now - previous}ms")
            return false
        }
        prefs(appContext).edit().putLong(KEY_LAST_TAP, now).apply()
        return true
    }

    fun recordTouch(
        context: Context,
        target: String,
        overlayMode: String,
        canExpand: Boolean,
        canDrag: Boolean = true,
    ) {
        prefs(context).edit()
            .putString(KEY_LAST_TOUCH_TARGET, target)
            .putString(KEY_OVERLAY_MODE, overlayMode)
            .putBoolean(KEY_CAN_EXPAND, canExpand)
            .putBoolean(KEY_CAN_DRAG, canDrag)
            .apply()
        Log.d(TAG, "touch target=$target overlayMode=$overlayMode canExpand=$canExpand canDrag=$canDrag")
    }

    fun recordNoSpeech(context: Context, reason: String) {
        prefs(context).edit()
            .putLong(KEY_LAST_NO_SPEECH_AT, System.currentTimeMillis())
            .putString(KEY_LAST_NO_SPEECH_REASON, reason)
            .apply()
        Log.d(TAG, "no speech detected reason=$reason")
    }

    fun diagnostic(context: Context): String {
        val prefs = prefs(context)
        return listOf(
            "voiceState=${state(context).name}",
            "ttsState=${prefs.getString(KEY_TTS_STATE, "IDLE")}",
            "speechRecognizerState=${prefs.getString(KEY_RECOGNIZER_STATE, "IDLE")}",
            "lastMicTriggerSource=${prefs.getString(KEY_LAST_VOICE_TRIGGER, "-")}",
            "lastTouchTarget=${prefs.getString(KEY_LAST_TOUCH_TARGET, "-")}",
            "overlayMode=${prefs.getString(KEY_OVERLAY_MODE, "-")}",
            "canDrag=${prefs.getBoolean(KEY_CAN_DRAG, true)}",
            "canExpand=${prefs.getBoolean(KEY_CAN_EXPAND, true)}",
            "canStartListening=${canStartListeningForDiagnostic(context)}",
            "lastNoSpeechReason=${prefs.getString(KEY_LAST_NO_SPEECH_REASON, "-")}",
            "timeSinceTtsDoneMs=${timeSinceTtsDoneMs(context)}",
            "isTtsSpeakingNow=${isTtsSpeakingNow(context)}",
        ).joinToString(" · ")
    }

    private fun canStartListeningForDiagnostic(context: Context): Boolean {
        val currentState = state(context)
        return !isTtsSpeakingNow(context) &&
            timeSinceTtsDoneMs(context) >= POST_TTS_SAFETY_MS &&
            currentState !in setOf(
                VoiceInteractionState.SPEAKING,
                VoiceInteractionState.LISTENING,
                VoiceInteractionState.TRANSCRIBING,
                VoiceInteractionState.THINKING,
            )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
