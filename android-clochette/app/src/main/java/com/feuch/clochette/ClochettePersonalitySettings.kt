package com.feuch.clochette

import android.content.Context

data class ClochettePersonalityConfig(
    val talkativeness: Int = 50,
    val initiative: Int = 50,
    val teasing: Int = 50,
    val softness: Int = 50,
    val phraseLength: Int = 50,
    val curiosity: Int = 50,
) {
    fun clamp(): ClochettePersonalityConfig = copy(
        talkativeness = talkativeness.coerceIn(0, 100),
        initiative = initiative.coerceIn(0, 100),
        teasing = teasing.coerceIn(0, 100),
        softness = softness.coerceIn(0, 100),
        phraseLength = phraseLength.coerceIn(0, 100),
        curiosity = curiosity.coerceIn(0, 100),
    )
}

object ClochettePersonalitySettings {
    private const val PREFS = "clochette_personality_settings"
    private const val KEY_TALKATIVENESS = "talkativeness"
    private const val KEY_INITIATIVE = "initiative"
    private const val KEY_TEASING = "teasing"
    private const val KEY_SOFTNESS = "softness"
    private const val KEY_PHRASE_LENGTH = "phrase_length"
    private const val KEY_CURIOSITY = "curiosity"

    fun read(context: Context): ClochettePersonalityConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ClochettePersonalityConfig(
            talkativeness = prefs.getInt(KEY_TALKATIVENESS, 50),
            initiative = prefs.getInt(KEY_INITIATIVE, 50),
            teasing = prefs.getInt(KEY_TEASING, 50),
            softness = prefs.getInt(KEY_SOFTNESS, 50),
            phraseLength = prefs.getInt(KEY_PHRASE_LENGTH, 50),
            curiosity = prefs.getInt(KEY_CURIOSITY, 50),
        ).clamp()
    }

    fun save(context: Context, config: ClochettePersonalityConfig) {
        val safe = config.clamp()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TALKATIVENESS, safe.talkativeness)
            .putInt(KEY_INITIATIVE, safe.initiative)
            .putInt(KEY_TEASING, safe.teasing)
            .putInt(KEY_SOFTNESS, safe.softness)
            .putInt(KEY_PHRASE_LENGTH, safe.phraseLength)
            .putInt(KEY_CURIOSITY, safe.curiosity)
            .apply()
    }

    fun preferredTone(config: ClochettePersonalityConfig): String? = when {
        config.teasing >= 70 -> "teasing"
        config.softness >= 70 -> "soft"
        config.phraseLength <= 25 -> "focus"
        config.curiosity >= 70 -> "micro_questions"
        else -> null
    }
}
