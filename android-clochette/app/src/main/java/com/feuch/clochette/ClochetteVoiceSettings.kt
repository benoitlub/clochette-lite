package com.feuch.clochette

import android.content.Context

data class ClochetteVoiceConfig(
    val enabled: Boolean = true,
    val autoSpeak: Boolean = true,
    val speechRate: Float = 1.03f,
    val pitch: Float = 1.14f,
    val mode: String = ClochetteVoiceSettings.MODE_ESPIEGLE,
    val soundEffect: String = ClochetteVoiceSettings.EFFECT_NONE,
    val phraseLength: String = ClochetteVoiceSettings.LENGTH_NORMAL,
)

object ClochetteVoiceSettings {
    const val MODE_DOUCE = "douce"
    const val MODE_ESPIEGLE = "espiegle"
    const val MODE_COACH = "coach"
    const val MODE_FEUCHIENNE = "feuchienne"

    const val EFFECT_NONE = "aucun"
    const val EFFECT_BELL = "clochette"
    const val EFFECT_POF = "pof feerique"

    const val LENGTH_SHORT = "courte"
    const val LENGTH_NORMAL = "normale"
    const val LENGTH_CHATTY = "bavarde"

    private const val PREFS = "clochette_voice_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_AUTO_SPEAK = "auto_speak"
    private const val KEY_RATE = "speech_rate"
    private const val KEY_PITCH = "pitch"
    private const val KEY_MODE = "mode"
    private const val KEY_EFFECT = "effect"
    private const val KEY_LENGTH = "phrase_length"

    fun read(context: Context): ClochetteVoiceConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ClochetteVoiceConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            autoSpeak = prefs.getBoolean(KEY_AUTO_SPEAK, true),
            speechRate = prefs.getFloat(KEY_RATE, 1.03f).coerceIn(0.7f, 1.4f),
            pitch = prefs.getFloat(KEY_PITCH, 1.14f).coerceIn(0.8f, 1.7f),
            mode = prefs.getString(KEY_MODE, MODE_ESPIEGLE) ?: MODE_ESPIEGLE,
            soundEffect = prefs.getString(KEY_EFFECT, EFFECT_NONE) ?: EFFECT_NONE,
            phraseLength = prefs.getString(KEY_LENGTH, LENGTH_NORMAL) ?: LENGTH_NORMAL,
        )
    }

    fun save(context: Context, config: ClochetteVoiceConfig) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putBoolean(KEY_AUTO_SPEAK, config.autoSpeak)
            .putFloat(KEY_RATE, config.speechRate.coerceIn(0.7f, 1.4f))
            .putFloat(KEY_PITCH, config.pitch.coerceIn(0.8f, 1.7f))
            .putString(KEY_MODE, config.mode)
            .putString(KEY_EFFECT, config.soundEffect)
            .putString(KEY_LENGTH, config.phraseLength)
            .apply()
    }
}
