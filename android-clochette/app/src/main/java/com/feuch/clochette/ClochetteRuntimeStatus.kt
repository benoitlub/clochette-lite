package com.feuch.clochette

import android.content.Context

data class ClochetteRuntimeSnapshot(
    val proactiveActive: Boolean = false,
    val lastAction: String = "silencieux",
    val lastTickAt: Long = 0L,
    val lastGuardianDecision: String = "jamais",
    val lastShouldSpeak: Boolean = false,
    val lastVoiceAction: String = "silencieux",
    val nextAttemptAt: Long = 0L,
)

object ClochetteRuntimeStatus {
    private const val PREFS = "clochette_runtime_status"
    private const val KEY_PROACTIVE_ACTIVE = "proactive_active"
    private const val KEY_LAST_ACTION = "last_action"
    private const val KEY_LAST_TICK_AT = "last_tick_at"
    private const val KEY_LAST_GUARDIAN = "last_guardian"
    private const val KEY_LAST_SHOULD_SPEAK = "last_should_speak"
    private const val KEY_LAST_VOICE_ACTION = "last_voice_action"
    private const val KEY_NEXT_ATTEMPT_AT = "next_attempt_at"

    fun read(context: Context): ClochetteRuntimeSnapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ClochetteRuntimeSnapshot(
            proactiveActive = prefs.getBoolean(KEY_PROACTIVE_ACTIVE, false),
            lastAction = prefs.getString(KEY_LAST_ACTION, "silencieux") ?: "silencieux",
            lastTickAt = prefs.getLong(KEY_LAST_TICK_AT, 0L),
            lastGuardianDecision = prefs.getString(KEY_LAST_GUARDIAN, "jamais") ?: "jamais",
            lastShouldSpeak = prefs.getBoolean(KEY_LAST_SHOULD_SPEAK, false),
            lastVoiceAction = prefs.getString(KEY_LAST_VOICE_ACTION, "silencieux") ?: "silencieux",
            nextAttemptAt = prefs.getLong(KEY_NEXT_ATTEMPT_AT, 0L),
        )
    }

    fun recordAction(context: Context, action: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_ACTION, action)
            .apply()
    }

    fun recordVoiceAction(context: Context, action: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_ACTION, action)
            .putString(KEY_LAST_VOICE_ACTION, action)
            .apply()
    }

    fun recordProactiveActive(context: Context, active: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PROACTIVE_ACTIVE, active)
            .apply()
    }

    fun recordTick(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_TICK_AT, System.currentTimeMillis())
            .apply()
    }

    fun recordDecision(context: Context, guardianDecision: String, shouldSpeak: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_GUARDIAN, guardianDecision)
            .putBoolean(KEY_LAST_SHOULD_SPEAK, shouldSpeak)
            .apply()
    }

    fun recordNextAttempt(context: Context, delayMs: Long) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_NEXT_ATTEMPT_AT, System.currentTimeMillis() + delayMs)
            .apply()
    }
}
