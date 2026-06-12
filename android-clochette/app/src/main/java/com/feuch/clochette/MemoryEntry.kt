package com.feuch.clochette

data class MemoryEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val context: String? = null,
    val lightweightSummary: String,
    val userIntent: String? = null,
    val reaction: String? = null,
    val result: String? = null,
)
