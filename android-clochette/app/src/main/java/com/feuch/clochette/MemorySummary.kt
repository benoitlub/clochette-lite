package com.feuch.clochette

data class MemorySummary(
    val summary: String,
    val hints: List<String> = emptyList(),
    val shouldCooldown: Boolean = false,
    val confidence: MemorySignal = MemorySignal.LOW,
    val usefulness: MemorySignal = MemorySignal.LOW,
    val expiresInDays: Int = 1,
)
