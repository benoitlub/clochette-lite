package com.feuch.clochette

import android.content.Context

data class ProactiveConfig(
    val voiceInterventions: Boolean = false,
    val spontaneousQuestions: Boolean = true,
    val frequency: ProactiveFrequency = ProactiveFrequency.DISCRETE,
    val mode: ProactiveMode = ProactiveMode.PAUSE,
)

enum class ProactiveMode {
    PAUSE,
    OBSERVE,
}

enum class ProactiveFrequency {
    DISCRETE,
    NORMALE,
    BAVARDE,
}

object ProactiveSettings {
    private const val PREFS = "clochette_proactive_settings"
    private const val KEY_VOICE_INTERVENTIONS = "voice_interventions"
    private const val KEY_SPONTANEOUS_QUESTIONS = "spontaneous_questions"
    private const val KEY_FREQUENCY = "frequency"
    private const val KEY_MODE = "mode"

    fun read(context: Context): ProactiveConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ProactiveConfig(
            voiceInterventions = prefs.getBoolean(KEY_VOICE_INTERVENTIONS, false),
            spontaneousQuestions = prefs.getBoolean(KEY_SPONTANEOUS_QUESTIONS, true),
            frequency = prefs.getString(KEY_FREQUENCY, ProactiveFrequency.DISCRETE.name)
                ?.let { runCatching { ProactiveFrequency.valueOf(it) }.getOrNull() }
                ?: ProactiveFrequency.DISCRETE,
            mode = prefs.getString(KEY_MODE, ProactiveMode.PAUSE.name)
                ?.let { runCatching { ProactiveMode.valueOf(it) }.getOrNull() }
                ?: ProactiveMode.PAUSE,
        )
    }

    fun save(context: Context, config: ProactiveConfig) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VOICE_INTERVENTIONS, config.voiceInterventions)
            .putBoolean(KEY_SPONTANEOUS_QUESTIONS, config.spontaneousQuestions)
            .putString(KEY_FREQUENCY, config.frequency.name)
            .putString(KEY_MODE, config.mode.name)
            .apply()
    }
}
