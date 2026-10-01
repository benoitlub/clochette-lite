package com.feuch.clochette

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ConsolidatedMemoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("clochette_consolidated_memory", Context.MODE_PRIVATE)

    fun add(entry: MemoryEntry) {
        val entries = read().filterNot { isExpired(it) }.toMutableList()
        val duplicate = entries.any {
            it.context == entry.context &&
                it.userIntent == entry.userIntent &&
                it.result == entry.result &&
                System.currentTimeMillis() - it.timestamp < DEDUPE_MS
        }
        if (!duplicate) entries += entry
        save(entries.takeLast(MAX_ENTRIES))
    }

    fun active(limit: Int = 24): List<MemoryEntry> =
        read().filterNot { isExpired(it) }.takeLast(limit)

    private fun isExpired(entry: MemoryEntry, now: Long = System.currentTimeMillis()): Boolean =
        now - entry.timestamp > entry.expiresInDays * DAY_MS

    private fun read(): List<MemoryEntry> {
        val raw = runCatching { JSONArray(prefs.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        return (0 until raw.length()).mapNotNull { i ->
            raw.optJSONObject(i)?.let { json ->
                MemoryEntry(
                    timestamp = json.optLong("timestamp"),
                    context = json.optString("context").takeIf { it.isNotBlank() && it != "null" },
                    lightweightSummary = json.optString("summary"),
                    userIntent = json.optString("intent").takeIf { it.isNotBlank() && it != "null" },
                    reaction = json.optString("reaction").takeIf { it.isNotBlank() && it != "null" },
                    result = json.optString("result").takeIf { it.isNotBlank() && it != "null" },
                    confidence = runCatching { MemorySignal.valueOf(json.optString("confidence")) }.getOrDefault(MemorySignal.LOW),
                    usefulness = runCatching { MemorySignal.valueOf(json.optString("usefulness")) }.getOrDefault(MemorySignal.LOW),
                    expiresInDays = json.optInt("expiresInDays", 1),
                )
            }
        }
    }

    private fun save(entries: List<MemoryEntry>) {
        val json = JSONArray()
        entries.forEach { entry ->
            json.put(
                JSONObject()
                    .put("timestamp", entry.timestamp)
                    .put("context", entry.context)
                    .put("summary", entry.lightweightSummary)
                    .put("intent", entry.userIntent)
                    .put("reaction", entry.reaction)
                    .put("result", entry.result)
                    .put("confidence", entry.confidence.name)
                    .put("usefulness", entry.usefulness.name)
                    .put("expiresInDays", entry.expiresInDays),
            )
        }
        prefs.edit().putString(KEY, json.toString()).apply()
    }

    private companion object {
        const val KEY = "entries"
        const val MAX_ENTRIES = 80
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val DEDUPE_MS = 6L * 60L * 60L * 1000L
    }
}
