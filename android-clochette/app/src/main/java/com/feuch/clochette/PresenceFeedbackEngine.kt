package com.feuch.clochette

data class PresenceFeedback(
    val cooldown: Boolean = false,
    val refusalCount: Int = 0,
    val engagementCount: Int = 0,
    val lastReactionAt: Long? = null,
)

object PresenceFeedbackEngine {
    fun from(entries: List<ClochetteMemoryEntry>, now: Long = System.currentTimeMillis()): PresenceFeedback {
        val recent = entries.filter { now - it.timestamp <= WINDOW_MS }
        val refusals = recent.count { entry ->
            entry.userReaction?.contains("pause", ignoreCase = true) == true ||
                entry.userReaction?.contains("non", ignoreCase = true) == true ||
                entry.userReaction?.contains("stop", ignoreCase = true) == true ||
                entry.result?.contains("paused", ignoreCase = true) == true ||
                entry.result?.contains("refused", ignoreCase = true) == true
        }
        val engagements = recent.count { entry ->
            !entry.userReaction.isNullOrBlank() &&
                entry.userReaction?.contains("pause", ignoreCase = true) != true &&
                entry.userReaction?.contains("stop", ignoreCase = true) != true
        }
        val lastReaction = recent.lastOrNull { !it.userReaction.isNullOrBlank() }?.timestamp
        return PresenceFeedback(
            cooldown = refusals > 0 && refusals >= engagements,
            refusalCount = refusals,
            engagementCount = engagements,
            lastReactionAt = lastReaction,
        )
    }

    private const val WINDOW_MS = 30 * 60_000L
}
