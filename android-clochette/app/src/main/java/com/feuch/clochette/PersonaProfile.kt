package com.feuch.clochette

import org.json.JSONArray
import org.json.JSONObject

data class PersonaProfile(
    val id: String,
    val name: String,
    val origin: String,
    val version: String,
    val bio: String,
    val temperament: Map<String, Any?>,
    val behavior: Map<String, Any?>,
    val visual: Map<String, Any?>,
    val voice: Map<String, Any?>,
    val remote: Map<String, Any?>,
    val typicalLines: List<String>,
) {
    companion object {
        fun fromJson(raw: String): PersonaProfile {
            val json = JSONObject(raw)
            return PersonaProfile(
                id = json.optString("id", "clochette"),
                name = json.optString("name", "Clochette"),
                origin = json.optString("origin", "unknown"),
                version = json.optString("version", "0"),
                bio = json.optString("bio", ""),
                temperament = json.opt("temperament").toStructuredMap(),
                behavior = json.opt("behavior").toStructuredMap(),
                visual = json.optJSONObject("visual").toMap(),
                voice = json.optJSONObject("voice").toMap(),
                remote = json.optJSONObject("remote").toMap(),
                typicalLines = json.optJSONArray("typicalLines").toStringList(),
            )
        }

        private fun JSONObject?.toMap(): Map<String, Any?> {
            if (this == null) return emptyMap()
            return keys().asSequence().associateWith { key -> normalizedValue(opt(key)) }
        }

        private fun Any?.toStructuredMap(): Map<String, Any?> = when (this) {
            is JSONObject -> toMap()
            is JSONArray -> mapOf("items" to normalizedValue(this))
            else -> emptyMap()
        }

        private fun JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
        }

        private fun normalizedValue(value: Any?): Any? = when (value) {
            JSONObject.NULL -> null
            is JSONObject -> value.toMap()
            is JSONArray -> (0 until value.length()).map { index -> normalizedValue(value.opt(index)) }
            else -> value
        }
    }
}
