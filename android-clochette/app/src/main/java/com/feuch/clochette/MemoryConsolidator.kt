package com.feuch.clochette

import android.content.Context

object MemoryConsolidator {
    fun consolidate(context: Context, snapshot: PresenceContextSnapshot): MemoryEntry? {
        val reactions = snapshot.recentMemory.filter {
            !it.userReaction.isNullOrBlank() && it.context != "memory_consolidation"
        }
        if (reactions.size < MIN_REACTIONS) return null

        val latest = reactions.last()
        val similar = reactions.filter {
            classify(it.userReaction) == classify(latest.userReaction)
        }
        if (similar.size < MIN_REPEATED_SIGNAL) return null

        val state = ContextRemarkEngine(context).buildState(snapshot.activity, snapshot.sensors)
        val summary = Archivist(context).summarizeReply(
            reply = latest.userReaction,
            context = state,
            result = latest.result,
            relationshipMode = RelationshipModeSettings.selected(context),
        )
        if (summary.confidence == MemorySignal.LOW || summary.usefulness == MemorySignal.LOW) return null

        val entry = Archivist(context).toMemoryEntry(summary, state)
        ConsolidatedMemoryStore(context).add(entry)
        return entry
    }

    private fun classify(reply: String?): String {
        val text = reply.orEmpty().lowercase()
        return when {
            text.contains("pause") || text.contains("stop") || text.contains("non") -> "decline"
            text.contains("oui") || text.contains("reprendre") -> "engage"
            text.isBlank() -> "silence"
            else -> "reply"
        }
    }

    private const val MIN_REACTIONS = 3
    private const val MIN_REPEATED_SIGNAL = 2
}
