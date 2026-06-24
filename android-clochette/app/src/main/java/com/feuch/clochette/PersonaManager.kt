package com.feuch.clochette

import android.content.Context
import org.json.JSONObject

class PersonaManager(context: Context) {
    private val appContext = context.applicationContext

    fun registry(): PersonaRegistry = runCatching {
        val raw = appContext.assets.open(CHAMELEON_CONTRACT_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = JSONObject(raw)
        val items = json.optJSONArray("registry")
        val personas = (0 until (items?.length() ?: 0)).mapNotNull { index ->
            items?.optJSONObject(index)?.let { item ->
                PersonaDescriptor(
                    id = item.optString("id"),
                    publicName = item.optString("name", item.optString("id")),
                    role = item.optString("role"),
                    traitsPath = item.optString("traitsPath").takeIf { it.isNotBlank() },
                    defaultMode = item.optString("defaultMode", "discrete"),
                    active = item.optString("id") == DEFAULT_PERSONA_ID,
                )
            }
        }.ifEmpty { fallbackPersonas() }
        PersonaRegistry(personas = personas, defaultPersonaId = DEFAULT_PERSONA_ID)
    }.getOrDefault(PersonaRegistry(fallbackPersonas(), DEFAULT_PERSONA_ID))

    fun availablePersonas(): List<PersonaDescriptor> = registry().personas

    fun activePersonaId(): String = registry().defaultPersonaId

    fun traits(personaId: String = activePersonaId()): PersonaTraits {
        val descriptor = availablePersonas().firstOrNull { it.id == personaId }
        if (personaId != DEFAULT_PERSONA_ID) return fallbackTraits(descriptor ?: fallbackDescriptor(personaId))
        return runCatching {
            val raw = appContext.assets.open(descriptor?.traitsPath ?: "personas/clochette/persona_traits.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
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
        }.getOrDefault(fallbackTraits(descriptor ?: fallbackDescriptor(DEFAULT_PERSONA_ID)))
    }

    private fun fallbackTraits(descriptor: PersonaDescriptor): PersonaTraits = PersonaTraits(
        personaId = descriptor.id,
        publicName = descriptor.publicName,
    )

    private fun fallbackDescriptor(personaId: String): PersonaDescriptor = PersonaDescriptor(
        id = personaId,
        publicName = personaId.replaceFirstChar { it.uppercase() },
    )

    private fun fallbackPersonas(): List<PersonaDescriptor> = listOf(
        PersonaDescriptor("clochette", "Clochette", "présence espiègle du téléphone", "personas/clochette/persona_traits.json", "companion", true),
        PersonaDescriptor("natasha", "Natasha", "guide narrative Blacklace", "personas/natasha/persona_traits.json"),
        PersonaDescriptor("pattou", "Pattou", "présence tendre et lunaire", "personas/pattou/persona_traits.json"),
        PersonaDescriptor("aloisia", "Aloisia", "mémoire et logique de l'île", "personas/aloisia/persona_traits.json", "manual"),
    )

    companion object {
        private const val DEFAULT_PERSONA_ID = "clochette"
        private const val CHAMELEON_CONTRACT_PATH = "octopus/chameleon_contract.json"
    }
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
