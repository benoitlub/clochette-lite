package com.feuch.clochette

data class DreamCandidate(
    val line: String,
    val reason: String,
    val source: String = "local_stub",
    val accepted: Boolean = false,
)
