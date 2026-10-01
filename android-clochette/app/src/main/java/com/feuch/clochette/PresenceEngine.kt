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
        val feedback = PresenceFeedbackEngine.from(recent, snapshot.capturedAt)

        if (!snapshot.sensors.screenActive) {
            return PresenceDecision(PresenceIntent.SILENT, "screen_off")
        }

        if (feedback.cooldown) {
            return PresenceDecision(PresenceIntent.GUARD, "learned_cooldown")
        }

        val durationMinutes = activity.approximateDurationMs / 60_000L
        if (durationMinutes >= 90 && activity.recentSwitchCount <= 1) {
            return PresenceDecision(PresenceIntent.GUARD, "deep_work")
        }

        if (activity.recentSwitchCount >= 5) {
            return if (feedback.engagementCount > feedback.refusalCount) {
                PresenceDecision(PresenceIntent.CURIOUS, "frequent_switching_engaged")
            } else {
                PresenceDecision(PresenceIntent.OBSERVING, "frequent_switching_no_engagement")
            }
        }

        if (durationMinutes >= 45 || snapshot.sensors.walkingPossible) {
            return PresenceDecision(PresenceIntent.INTERVENE, "context_change_or_long_session")
        }

        return PresenceDecision(PresenceIntent.OBSERVING, "nothing_worth_interrupting")
    }
}
