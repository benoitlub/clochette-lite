package com.feuch.clochette

class Archivist {
    fun summarizeReply(
        reply: String?,
        context: ContextState,
        result: String? = null,
    ): MemorySummary {
        val normalized = reply.orEmpty().trim()
        val intent = when {
            normalized.isBlank() -> "silence"
            normalized.contains("pause", ignoreCase = true) -> "pause_requested"
            normalized.contains("oui", ignoreCase = true) || normalized.contains("reprendre", ignoreCase = true) -> "resume_possible"
            normalized.contains("non", ignoreCase = true) || normalized.contains("stop", ignoreCase = true) -> "decline"
            else -> "reply_noted"
        }
        val hints = buildList {
            context.currentAppName?.let { add("app:$it") }
            add("intent:$intent")
            result?.let { add("result:$it") }
        }
        return MemorySummary(
            summary = "Réponse résumée : $intent",
            hints = hints,
            shouldCooldown = intent == "silence" || intent == "pause_requested" || intent == "decline",
        )
    }

    fun toMemoryEntry(summary: MemorySummary, context: ContextState): MemoryEntry = MemoryEntry(
        context = context.currentAppName,
        lightweightSummary = summary.summary,
        userIntent = summary.hints.firstOrNull { it.startsWith("intent:") }?.removePrefix("intent:"),
        reaction = summary.hints.firstOrNull { it.startsWith("result:") }?.removePrefix("result:"),
        result = if (summary.shouldCooldown) "cooldown" else "kept",
    )
}
