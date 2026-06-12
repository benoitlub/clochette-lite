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

    fun loadStatuses(): List<PersonaModuleStatus> = MODULE_FILES.map { module ->
        runCatching {
            val raw = assets.open(module.path).bufferedReader().use { it.readText() }
            PersonaModuleStatus(
                fileName = module.label,
                detected = true,
                validJson = runCatching { JSONObject(raw) }.isSuccess,
            )
        }.getOrElse {
            PersonaModuleStatus(fileName = module.label, detected = false, validJson = false)
        }
    }

    companion object {
        private const val MODULE_DIR = "personas/clochette"

        private data class ModuleFile(
            val label: String,
            val path: String,
        )

        private fun clochetteModule(fileName: String): ModuleFile =
            ModuleFile(fileName, "$MODULE_DIR/$fileName")

        private val MODULE_FILES = listOf(
            clochetteModule("interaction.json"),
            clochetteModule("sensor_profiles.json"),
            clochetteModule("memory_rules.json"),
            clochetteModule("ai_gateway.json"),
            clochetteModule("notion_sync.json"),
            clochetteModule("dreams.json"),
            clochetteModule("octopus_core.json"),
            clochetteModule("guardian_rules.json"),
            clochetteModule("relationship_modes.json"),
            clochetteModule("library_schema.json"),
            clochetteModule("persona_traits.json"),
            ModuleFile("shared_library_model.json", "personas/shared_library_model.json"),
        )
    }
}
