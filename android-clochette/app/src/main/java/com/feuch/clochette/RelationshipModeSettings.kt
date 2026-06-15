package com.feuch.clochette

import android.content.Context
import org.json.JSONObject

data class RelationshipMode(
    val id: String,
    val name: String,
    val questionFrequency: String,
    val voiceDefault: Boolean,
    val cooldownMultiplier: Double,
)

object RelationshipModeSettings {
    private const val PREFS = "clochette_relationship_mode"
    private const val KEY_MODE_ID = "mode_id"
    private const val ASSET_PATH = "personas/clochette/relationship_modes.json"
    const val DEFAULT_MODE_ID = "discrete"

    fun selectedId(context: Context): String = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_MODE_ID, DEFAULT_MODE_ID) ?: DEFAULT_MODE_ID

    fun saveSelectedId(context: Context, id: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE_ID, id)
            .apply()
    }

    fun modes(context: Context): List<RelationshipMode> = runCatching {
        val raw = context.applicationContext.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val json = JSONObject(raw)
        val array = json.optJSONArray("modes")
        (0 until (array?.length() ?: 0)).mapNotNull { index ->
            array?.optJSONObject(index)?.let { item ->
                RelationshipMode(
                    id = item.optString("id"),
                    name = item.optString("name", item.optString("id")),
                    questionFrequency = item.optString("questionFrequency", "low"),
                    voiceDefault = item.optBoolean("voiceDefault", false),
                    cooldownMultiplier = item.optDouble("cooldownMultiplier", 1.0).coerceIn(0.5, 4.0),
                )
            }
        }.filter { it.id.isNotBlank() }
    }.getOrDefault(defaultModes())

    fun selected(context: Context): RelationshipMode {
        val id = selectedId(context)
        return modes(context).firstOrNull { it.id == id } ?: modes(context).first()
    }

    fun effectiveConfig(context: Context, base: ProactiveConfig = ProactiveSettings.read(context)): ProactiveConfig {
        val mode = selected(context)
        val personality = ClochettePersonalitySettings.read(context)
        val personalityFrequency = when {
            personality.talkativeness >= 70 -> ProactiveFrequency.BAVARDE
            personality.talkativeness <= 25 -> ProactiveFrequency.DISCRETE
            personality.talkativeness !in 40..60 -> ProactiveFrequency.NORMALE
            else -> null
        }
        return base.copy(
            voiceInterventions = base.voiceInterventions && mode.voiceDefault,
            spontaneousQuestions = base.spontaneousQuestions &&
                mode.questionFrequency != "very_low" &&
                personality.curiosity > 10,
            frequency = personalityFrequency ?: when (mode.questionFrequency) {
                "high" -> ProactiveFrequency.BAVARDE
                "medium" -> ProactiveFrequency.NORMALE
                else -> ProactiveFrequency.DISCRETE
            },
        )
    }

    private fun defaultModes(): List<RelationshipMode> = listOf(
        RelationshipMode("discrete", "Discrète", "low", false, 2.0),
        RelationshipMode("manual", "Manuelle", "very_low", false, 3.0),
        RelationshipMode("companion", "Compagne", "medium", true, 1.0),
        RelationshipMode("alive", "Vivante", "high", true, 0.75),
        RelationshipMode("quiet_night", "Nuit douce", "low", false, 2.5),
    )
}
