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
        private const val BANK_DIR = "$MODULE_DIR/phrase_banks"

        private data class ModuleFile(
            val label: String,
            val path: String,
        )

        private fun clochetteModule(fileName: String): ModuleFile =
            ModuleFile(fileName, "$MODULE_DIR/$fileName")

        private fun phraseBank(fileName: String): ModuleFile =
            ModuleFile("phrase_banks/$fileName", "$BANK_DIR/$fileName")

        private val MODULE_FILES = listOf(
            clochetteModule("interaction.json"),
            clochetteModule("sensor_profiles.json"),
            clochetteModule("memory_rules.json"),
            clochetteModule("ai_gateway.json"),
            clochetteModule("notion_sync.json"),
            clochetteModule("dreams.json"),
            clochetteModule("context_lines.json"),
            clochetteModule("app_context_lines.json"),
            clochetteModule("octopus_core.json"),
            clochetteModule("guardian_rules.json"),
            clochetteModule("relationship_modes.json"),
            clochetteModule("library_schema.json"),
            clochetteModule("persona_traits.json"),
            clochetteModule("appearance_library.json"),
            phraseBank("natural.json"),
            phraseBank("teasing.json"),
            phraseBank("soft.json"),
            phraseBank("badass.json"),
            phraseBank("focus.json"),
            phraseBank("fatigue.json"),
            phraseBank("creative.json"),
            phraseBank("micro_questions.json"),
            phraseBank("silence_responses.json"),
            ModuleFile("shared_library_model.json", "personas/shared_library_model.json"),
            ModuleFile("octopus/archivist_contract.json", "octopus/archivist_contract.json"),
            ModuleFile("octopus/dreamer_contract.json", "octopus/dreamer_contract.json"),
            ModuleFile("octopus/librarian_contract.json", "octopus/librarian_contract.json"),
            ModuleFile("octopus/diplomat_contract.json", "octopus/diplomat_contract.json"),
            ModuleFile("octopus/chameleon_contract.json", "octopus/chameleon_contract.json"),
            ModuleFile("octopus/test_witness_lines.json", "octopus/test_witness_lines.json"),
            ModuleFile("octopus/debug_test_plan.json", "octopus/debug_test_plan.json"),
        )
    }
}
