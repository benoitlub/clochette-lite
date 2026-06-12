package com.feuch.clochette

data class MemorySummary(
    val summary: String,
    val hints: List<String> = emptyList(),
    val shouldCooldown: Boolean = false,
)
