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
    PROACTIVE_REPLY_REQUEST,
    AUTO_PROMPT,
    AVATAR_TAP,
    BUBBLE_TAP,
    WIDGET,
    UNKNOWN,
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
    private const val KEY_OVERLAY_MODE = "overlay_mode"
    private const val KEY_CAN_EXPAND = "can_expand"
    private const val DEBOUNCE_MS = 450L

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

    fun canStartListening(context: Context): Boolean =
        canStartListening(context, VoiceTriggerSource.UNKNOWN)

    fun canStartListening(context: Context, source: VoiceTriggerSource): Boolean {
        val appContext = context.applicationContext
        val currentState = state(appContext)
        val sourceAllowed = source in setOf(
            VoiceTriggerSource.MICRO_BUTTON,
            VoiceTriggerSource.PROACTIVE_REPLY_REQUEST,
            VoiceTriggerSource.AUTO_PROMPT,
        )
        val stateAllows = currentState !in setOf(VoiceInteractionState.LISTENING, VoiceInteractionState.TRANSCRIBING)
        val canStart = sourceAllowed && stateAllows
        prefs(appContext).edit()
            .putString(KEY_LAST_VOICE_TRIGGER, source.name)
            .apply()
        Log.d(TAG, "canStartListening=$canStart source=$source state=$currentState")
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

    fun recordTouch(context: Context, target: String, overlayMode: String, canExpand: Boolean) {
        val appContext = context.applicationContext
        prefs(appContext).edit()
            .putString(KEY_LAST_TOUCH_TARGET, target)
            .putString(KEY_OVERLAY_MODE, overlayMode)
            .putBoolean(KEY_CAN_EXPAND, canExpand)
            .apply()
        Log.d(TAG, "touch target=$target overlayMode=$overlayMode canExpand=$canExpand")
    }

    fun recordNoSpeech(context: Context) {
        prefs(context.applicationContext).edit()
            .putLong(KEY_LAST_NO_SPEECH_AT, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "no speech detected")
    }

    fun diagnostic(context: Context): String {
        val prefs = prefs(context)
        val noSpeechAt = prefs.getLong(KEY_LAST_NO_SPEECH_AT, 0L)
        return "voiceState=${state(context).name.lowercase()} · lastTouchTarget=${prefs.getString(KEY_LAST_TOUCH_TARGET, "-")} · lastVoiceTriggerSource=${prefs.getString(KEY_LAST_VOICE_TRIGGER, "-")} · overlayMode=${prefs.getString(KEY_OVERLAY_MODE, "-")} · lastNoSpeechAt=$noSpeechAt · canExpand=${prefs.getBoolean(KEY_CAN_EXPAND, true)} · ${prefs.getString(KEY_LAST_TRANSITION, "-")}"
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
