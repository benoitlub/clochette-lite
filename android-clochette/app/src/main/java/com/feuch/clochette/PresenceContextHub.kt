package com.feuch.clochette

data class PresenceContextSnapshot(
    val activity: ActivitySnapshot,
    val sensors: SensorSnapshot = SensorSnapshot(),
    val recentMemory: List<ClochetteMemoryEntry> = emptyList(),
    val capturedAt: Long = System.currentTimeMillis(),
)

object PresenceContextHub {
    fun capture(context: android.content.Context): PresenceContextSnapshot {
        val appContext = context.applicationContext
        return PresenceContextSnapshot(
            activity = UsageObserver(appContext).snapshot(),
            sensors = SensorObserver(appContext).snapshot(),
            recentMemory = ClochetteMemory(appContext).recent(24),
        )
    }
}
