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
}

object VoiceInteractionController {
    private const val TAG = "ClochetteVoiceState"
    private const val PREFS = "clochette_voice_interaction"
    private const val KEY_STATE = "state"
    private const val KEY_UPDATED = "updated"
    private const val KEY_LAST_TRANSITION = "last_transition"
    private const val KEY_LAST_TAP = "last_tap"
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
        state(context) !in setOf(VoiceInteractionState.LISTENING, VoiceInteractionState.TRANSCRIBING)

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

    fun diagnostic(context: Context): String {
        val prefs = prefs(context)
        return "${state(context).name.lowercase()} · ${prefs.getString(KEY_LAST_TRANSITION, "-")}"
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
