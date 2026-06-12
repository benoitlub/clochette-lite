package com.feuch.clochette

data class MemoryEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val context: String? = null,
    val lightweightSummary: String,
    val userIntent: String? = null,
    val reaction: String? = null,
    val result: String? = null,
    val confidence: MemorySignal = MemorySignal.LOW,
    val usefulness: MemorySignal = MemorySignal.LOW,
    val expiresInDays: Int = 1,
)

enum class MemorySignal {
    LOW,
    MEDIUM,
    HIGH,
}
