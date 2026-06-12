package com.feuch.clochette

data class DreamCycle(
    val timestamp: Long = System.currentTimeMillis(),
    val state: DreamState = DreamState.IDLE,
    val privateMoment: Boolean = true,
    val note: String,
    val candidates: List<DreamCandidate> = emptyList(),
    val automatic: Boolean = false,
)

enum class DreamState {
    IDLE,
    PRIVATE_MOMENT,
    REVIEWING_CANDIDATES,
    SLEEPING,
}
