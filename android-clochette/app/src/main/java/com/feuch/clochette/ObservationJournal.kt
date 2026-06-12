package com.feuch.clochette

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ObservationJournalEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val activity: String? = null,
    val question: String? = null,
    val userReply: String? = null,
    val reaction: String? = null,
    val result: String? = null,
)

class ObservationJournal(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun add(entry: ObservationJournalEntry) {
        val entries = recent(MAX_ENTRIES - 1).toMutableList()
        entries.add(entry)
        prefs.edit().putString(KEY_ENTRIES, entries.toJson().toString()).apply()
    }

    fun recent(limit: Int = MAX_ENTRIES): List<ObservationJournalEntry> {
        val raw = prefs.getString(KEY_ENTRIES, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                array.optJSONObject(index)?.toEntry()
            }.takeLast(limit.coerceIn(1, MAX_ENTRIES))
        }.getOrDefault(emptyList())
    }

    private fun List<ObservationJournalEntry>.toJson(): JSONArray {
        val array = JSONArray()
        forEach { entry ->
            array.put(
                JSONObject()
                    .put("timestamp", entry.timestamp)
                    .put("activity", entry.activity)
                    .put("question", entry.question)
                    .put("userReply", entry.userReply)
                    .put("reaction", entry.reaction)
                    .put("result", entry.result),
            )
        }
        return array
    }

    private fun JSONObject.toEntry(): ObservationJournalEntry = ObservationJournalEntry(
        timestamp = optLong("timestamp", System.currentTimeMillis()),
        activity = optStringOrNull("activity"),
        question = optStringOrNull("question"),
        userReply = optStringOrNull("userReply"),
        reaction = optStringOrNull("reaction"),
        result = optStringOrNull("result"),
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    companion object {
        private const val PREFS = "clochette_observation_journal"
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 100
    }
}
