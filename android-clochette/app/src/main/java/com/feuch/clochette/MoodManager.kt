package com.feuch.clochette

enum class ClochetteMood {
    PLAYFUL,
    CALM,
    CONCERNED,
    SLEEPY,
    PROUD,
}

object MoodManager {
    fun moodFor(state: ContextState, memory: List<ClochetteMemoryEntry> = emptyList()): ClochetteMood {
        val app = state.currentAppName.orEmpty().lowercase()
        return when {
            state.dayPeriod == DayPeriod.NIGHT || state.userEnergyEstimate == UserEnergyEstimate.LOW -> ClochetteMood.SLEEPY
            state.batteryPercent != null && state.batteryPercent <= 18 && !state.isCharging -> ClochetteMood.CONCERNED
            app.contains("github") || app.contains("codex") || app.contains("clochette") -> ClochetteMood.PROUD
            state.recentAppSwitches >= 5 || memory.any { it.clochetteLine?.contains("Je remarque que") == true } -> ClochetteMood.PLAYFUL
            else -> ClochetteMood.CALM
        }
    }
}
