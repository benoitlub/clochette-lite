package com.feuch.clochette

import android.content.Context
import org.json.JSONObject

class LibraryRepository(context: Context) {
    private val appContext = context.applicationContext

    fun sharedModel(): SharedLibraryModel = runCatching {
        val raw = appContext.assets.open(SHARED_MODEL_PATH).bufferedReader().use { it.readText() }
        val json = JSONObject(raw)
        SharedLibraryModel(
            id = json.optString("id", "shared_library_model"),
            version = json.optInt("version", 1),
            targetJsonFiles = json.optJSONArray("targetJsonFiles").toLibraryStringList(),
            rules = json.optJSONArray("rules").toLibraryStringList(),
        )
    }.getOrDefault(SharedLibraryModel())

    fun acceptedLines(): List<AcceptedLine> = loadContextLines()

    private fun loadContextLines(): List<AcceptedLine> = runCatching {
        val raw = appContext.assets.open(CONTEXT_LINES_PATH).bufferedReader().use { it.readText() }
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
                                preferredFor = listOf("clochette"),
                            ),
                        )
                    }
                }
            }
        }
    }.getOrDefault(emptyList())

    companion object {
        private const val SHARED_MODEL_PATH = "personas/shared_library_model.json"
        private const val CONTEXT_LINES_PATH = "personas/clochette/context_lines.json"
    }
}

private fun org.json.JSONArray?.toLibraryStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
}
