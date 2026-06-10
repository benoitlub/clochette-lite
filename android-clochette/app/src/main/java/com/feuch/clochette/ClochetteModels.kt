package com.feuch.clochette

data class ActivitySnapshot(
    val foregroundPackage: String? = null,
    val recentSwitchCount: Int = 0,
    val approximateDurationMs: Long = 0L,
)

data class SensorSnapshot(
    val walkingPossible: Boolean = false,
    val phoneStill: Boolean = true,
    val lowLight: Boolean = false,
    val orientation: String = "unknown",
    val screenActive: Boolean = true,
)

data class ClochetteMemoryEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val context: String,
    val observedSignal: String,
    val project: String?,
    val energy: String?,
    val clochetteLine: String?,
    val userReaction: String?,
    val result: String?,
)

enum class ClochetteState {
    ASLEEP,
    OBSERVING,
    PAUSED
}
