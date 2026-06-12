package com.feuch.clochette

import android.content.Context
import org.json.JSONObject

data class PersonaModuleStatus(
    val fileName: String,
    val detected: Boolean,
    val validJson: Boolean,
)

class PersonaModuleLoader(context: Context) {
    private val assets = context.applicationContext.assets

    fun loadStatuses(): List<PersonaModuleStatus> = MODULE_FILES.map { fileName ->
        val path = "$MODULE_DIR/$fileName"
        runCatching {
            val raw = assets.open(path).bufferedReader().use { it.readText() }
            PersonaModuleStatus(
                fileName = fileName,
                detected = true,
                validJson = runCatching { JSONObject(raw) }.isSuccess,
            )
        }.getOrElse {
            PersonaModuleStatus(fileName = fileName, detected = false, validJson = false)
        }
    }

    companion object {
        private const val MODULE_DIR = "personas/clochette"

        val MODULE_FILES = listOf(
            "interaction.json",
            "sensor_profiles.json",
            "memory_rules.json",
            "ai_gateway.json",
            "notion_sync.json",
            "dreams.json",
            "octopus_core.json",
            "guardian_rules.json",
            "relationship_modes.json",
            "library_schema.json",
        )
    }
}
