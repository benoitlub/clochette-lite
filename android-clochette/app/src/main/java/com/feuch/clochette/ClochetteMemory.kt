package com.feuch.clochette

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ClochetteMemory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("carnet_indices", Context.MODE_PRIVATE)

    fun add(entry: ClochetteMemoryEntry) {
        val entries = readJsonArray()
        entries.put(entry.toJson())
        while (entries.length() > MAX_ENTRIES) {
            entries.remove(0)
        }
        prefs.edit().putString(KEY_ENTRIES, entries.toString()).apply()
    }

    fun recent(limit: Int = 8): List<ClochetteMemoryEntry> {
        val entries = readJsonArray()
        val start = (entries.length() - limit).coerceAtLeast(0)
        return (start until entries.length()).mapNotNull { index ->
            entries.optJSONObject(index)?.toEntry()
        }
    }

    private fun readJsonArray(): JSONArray {
        return runCatching { JSONArray(prefs.getString(KEY_ENTRIES, "[]")) }.getOrDefault(JSONArray())
    }

    private fun ClochetteMemoryEntry.toJson(): JSONObject = JSONObject()
        .put("timestamp", timestamp)
        .put("context", context)
        .put("observedSignal", observedSignal)
        .put("project", project)
        .put("energy", energy)
        .put("clochetteLine", clochetteLine)
        .put("userReaction", userReaction)
        .put("result", result)

    private fun JSONObject.toEntry(): ClochetteMemoryEntry = ClochetteMemoryEntry(
        timestamp = optLong("timestamp"),
        context = optString("context"),
        observedSignal = optString("observedSignal"),
        project = optString("project").takeIf { it.isNotBlank() && it != "null" },
        energy = optString("energy").takeIf { it.isNotBlank() && it != "null" },
        clochetteLine = optString("clochetteLine").takeIf { it.isNotBlank() && it != "null" },
        userReaction = optString("userReaction").takeIf { it.isNotBlank() && it != "null" },
        result = optString("result").takeIf { it.isNotBlank() && it != "null" },
    )

    private companion object {
        const val KEY_ENTRIES = "entries"
        const val MAX_ENTRIES = 300
    }
}
