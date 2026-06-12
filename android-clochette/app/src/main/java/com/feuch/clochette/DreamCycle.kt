package com.feuch.clochette

data class DreamCycle(
    val timestamp: Long = System.currentTimeMillis(),
    val privateMoment: Boolean = true,
    val note: String,
    val candidates: List<DreamCandidate> = emptyList(),
    val automatic: Boolean = false,
)
