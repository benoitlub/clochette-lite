package com.feuch.clochette

import android.content.Context
import org.json.JSONObject

data class PersonaDescriptor(
    val id: String,
    val publicName: String,
)

class PersonaManager(context: Context) {
    private val appContext = context.applicationContext

    fun availablePersonas(): List<PersonaDescriptor> = listOf(
        PersonaDescriptor("clochette", "Clochette"),
        PersonaDescriptor("natasha", "Natasha"),
        PersonaDescriptor("pattou", "Pattou"),
        PersonaDescriptor("aloisia", "Aloisia"),
    )

    fun activePersonaId(): String = "clochette"

    fun traits(personaId: String = activePersonaId()): PersonaTraits {
        if (personaId != "clochette") return fallbackTraits(personaId)
        return runCatching {
            val raw = appContext.assets.open("personas/clochette/persona_traits.json").bufferedReader().use { it.readText() }
            val json = JSONObject(raw)
            val limits = json.optJSONObject("limits")
            PersonaTraits(
                personaId = json.optString("personaId", "clochette"),
                publicName = json.optString("publicName", "Clochette"),
                traitWeights = json.optJSONObject("traitWeights").toDoubleMap(),
                contextWeights = json.optJSONObject("contextWeights").toDoubleMap(),
                maxIntrusion = limits?.optDouble("maxIntrusion", 0.7) ?: 0.7,
                minKindness = limits?.optDouble("minKindness", 0.65) ?: 0.65,
                maxAbsurdity = limits?.optDouble("maxAbsurdity", 0.45) ?: 0.45,
                maxWords = limits?.optInt("maxWords", 25) ?: 25,
                preferredPhrasing = json.optJSONArray("preferredPhrasing").toPersonaStringList(),
                blockedPhrasing = json.optJSONArray("blockedPhrasing").toPersonaStringList(),
            )
        }.getOrDefault(fallbackTraits("clochette"))
    }

    private fun fallbackTraits(personaId: String): PersonaTraits = PersonaTraits(
        personaId = personaId,
        publicName = personaId.replaceFirstChar { it.uppercase() },
    )
}

private fun JSONObject?.toDoubleMap(): Map<String, Double> {
    if (this == null) return emptyMap()
    val keys = keys()
    return buildMap {
        while (keys.hasNext()) {
            val key = keys.next()
            put(key, optDouble(key, 1.0))
        }
    }
}

private fun org.json.JSONArray?.toPersonaStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
}
