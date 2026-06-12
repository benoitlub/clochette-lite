package com.feuch.clochette

import android.content.Context
import org.json.JSONObject

class Archivist(private val context: Context? = null) {
    fun summarizeReply(
        reply: String?,
        context: ContextState,
        result: String? = null,
        relationshipMode: RelationshipMode? = null,
    ): MemorySummary {
        val normalized = reply.orEmpty().trim()
        val intent = when {
            normalized.isBlank() -> "silence"
            normalized.contains("pause", ignoreCase = true) -> "pause_requested"
            normalized.contains("oui", ignoreCase = true) || normalized.contains("reprendre", ignoreCase = true) -> "resume_possible"
            normalized.contains("non", ignoreCase = true) || normalized.contains("stop", ignoreCase = true) -> "decline"
            else -> "reply_noted"
        }
        val confidence = when {
            normalized.isBlank() -> MemorySignal.LOW
            normalized.length < 8 -> MemorySignal.LOW
            else -> MemorySignal.MEDIUM
        }
        val usefulness = when (intent) {
            "resume_possible" -> MemorySignal.HIGH
            "pause_requested", "decline" -> MemorySignal.MEDIUM
            else -> MemorySignal.LOW
        }
        val hints = buildList {
            context.currentAppName?.let { add("app:$it") }
            add("intent:$intent")
            result?.let { add("result:$it") }
            relationshipMode?.id?.let { add("relationship:$it") }
        }
        return MemorySummary(
            summary = "Réponse résumée : $intent",
            hints = hints,
            shouldCooldown = intent == "silence" || intent == "pause_requested" || intent == "decline",
            confidence = confidence,
            usefulness = usefulness,
            expiresInDays = expiryFor(intent),
        )
    }

    fun toMemoryEntry(summary: MemorySummary, context: ContextState): MemoryEntry = MemoryEntry(
        context = context.currentAppName,
        lightweightSummary = summary.summary,
        userIntent = summary.hints.firstOrNull { it.startsWith("intent:") }?.removePrefix("intent:"),
        reaction = summary.hints.firstOrNull { it.startsWith("result:") }?.removePrefix("result:"),
        result = if (summary.shouldCooldown) "cooldown" else "kept",
        confidence = summary.confidence,
        usefulness = summary.usefulness,
        expiresInDays = summary.expiresInDays,
    )

    fun contractAvailable(): Boolean = runCatching {
        val appContext = context?.applicationContext ?: return false
        val raw = appContext.assets.open(CONTRACT_PATH).bufferedReader().use { it.readText() }
        JSONObject(raw).optString("id") == "octopus_archivist_contract"
    }.getOrDefault(false)

    private fun expiryFor(intent: String): Int = when (intent) {
        "resume_possible" -> 7
        "pause_requested", "decline", "silence" -> 2
        else -> 1
    }

    companion object {
        private const val CONTRACT_PATH = "octopus/archivist_contract.json"
    }
}
