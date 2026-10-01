package com.feuch.clochette

enum class PresenceIntent {
    SILENT,
    OBSERVING,
    CURIOUS,
    INTERVENE,
    GUARD,
}

data class PresenceDecision(
    val intent: PresenceIntent,
    val reason: String,
)

object PresenceEngine {
    fun decide(snapshot: PresenceContextSnapshot): PresenceDecision {
        val activity = snapshot.activity
        val recent = snapshot.recentMemory

        if (!snapshot.sensors.screenActive) {
            return PresenceDecision(PresenceIntent.SILENT, "screen_off")
        }

        val recentRefusal = recent.takeLast(5).any { entry ->
            entry.userReaction?.contains("pause", ignoreCase = true) == true ||
                entry.userReaction?.contains("refus", ignoreCase = true) == true ||
                entry.result?.contains("closed", ignoreCase = true) == true ||
                entry.result?.contains("refused", ignoreCase = true) == true
        }
        if (recentRefusal) {
            return PresenceDecision(PresenceIntent.GUARD, "recent_refusal")
        }

        val durationMinutes = activity.approximateDurationMs / 60_000L
        if (durationMinutes >= 90 && activity.recentSwitchCount <= 1) {
            return PresenceDecision(PresenceIntent.GUARD, "deep_work")
        }

        if (activity.recentSwitchCount >= 5) {
            return PresenceDecision(PresenceIntent.CURIOUS, "frequent_switching")
        }

        if (durationMinutes >= 45 || snapshot.sensors.walkingPossible) {
            return PresenceDecision(PresenceIntent.INTERVENE, "context_change_or_long_session")
        }

        return PresenceDecision(PresenceIntent.OBSERVING, "nothing_worth_interrupting")
    }
}
