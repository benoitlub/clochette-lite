package com.feuch.clochette

import android.content.Context
import org.json.JSONObject

class LibraryRepository(context: Context) {
    private val appContext = context.applicationContext

    fun sharedModel(): SharedLibraryModel = runCatching {
        val raw = appContext.assets.open(SHARED_MODEL_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = JSONObject(raw)
        SharedLibraryModel(
            id = json.optString("id", "shared_library_model"),
            version = json.optInt("version", 1),
            targetJsonFiles = json.optJSONArray("targetJsonFiles").toLibraryStringList(),
            rules = json.optJSONArray("rules").toLibraryStringList(),
            selectionFlow = json.optJSONArray("selectionFlow").toLibraryStringList(),
        )
    }.getOrDefault(SharedLibraryModel())

    fun acceptedPack(): List<AcceptedLine> = acceptedLines().filter { it.status == "accepted" }

    fun candidatePool(state: ContextState, traits: PersonaTraits): List<AcceptedLine> {
        val app = state.currentAppName.orEmpty()
        return acceptedLines().filter { line ->
            line.preferredFor.isEmpty() || traits.personaId in line.preferredFor || line.contexts.any { app.contains(it, true) }
        }
    }

    fun acceptedLines(): List<AcceptedLine> = loadContextLines()

    fun health(): LibraryHealth {
        val lines = acceptedLines()
        val warnings = buildList {
            if (sharedModel().id.isBlank()) add("shared_model_missing")
            if (!contractAvailable(LIBRARIAN_CONTRACT_PATH)) add("librarian_contract_missing")
            if (lines.isEmpty()) add("accepted_lines_empty")
        }
        return LibraryHealth(
            sharedModelAvailable = sharedModel().id.isNotBlank(),
            librarianContractAvailable = contractAvailable(LIBRARIAN_CONTRACT_PATH),
            acceptedLineCount = lines.size,
            warnings = warnings,
        )
    }

    fun missingTagsReport(lines: List<AcceptedLine> = acceptedLines()): MissingTagsReport = MissingTagsReport(
        missingContexts = lines.filter { it.contexts.isEmpty() }.map { it.id },
        missingTones = lines.filter { it.tones.isEmpty() }.map { it.id },
        weakLines = lines.filter { it.kindness < 0.6 || it.absurdity > 0.5 || it.intrusion > 0.75 }.map { it.id },
    )

    private fun loadContextLines(): List<AcceptedLine> = runCatching {
        val raw = appContext.assets.open(CONTEXT_LINES_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = JSONObject(raw)
        val apps = json.optJSONArray("apps")
        buildList {
            for (appIndex in 0 until (apps?.length() ?: 0)) {
                val app = apps?.optJSONObject(appIndex) ?: continue
                val contexts = app.optJSONArray("names").toLibraryStringList()
                val lines = app.optJSONObject("lines") ?: continue
                val keys = lines.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    lines.optJSONArray(key).toLibraryStringList().forEachIndexed { index, text ->
                        add(
                            AcceptedLine(
                                id = "${app.optString("id", "line")}_${key}_$index",
                                text = text,
                                contexts = contexts + key,
                                tones = listOf("playful", "teasing"),
                                requires = listOf("context_state"),
                                preferredFor = listOf("clochette"),
                                source = "context_lines",
                                status = "accepted",
                            ),
                        )
                    }
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun contractAvailable(path: String): Boolean = runCatching {
        val raw = appContext.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        JSONObject(raw).optString("id").isNotBlank()
    }.getOrDefault(false)

    companion object {
        private const val SHARED_MODEL_PATH = "personas/shared_library_model.json"
        private const val CONTEXT_LINES_PATH = "personas/clochette/context_lines.json"
        private const val LIBRARIAN_CONTRACT_PATH = "octopus/librarian_contract.json"
    }
}

private fun org.json.JSONArray?.toLibraryStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
}
