package com.feuch.clochette

data class DreamReturn(
    val line: String,
    val miniChangelog: List<String> = emptyList(),
    val adoptedCount: Int = 0,
    val rejectedCount: Int = 0,
)
