package com.feuch.clochette

data class AcceptedLine(
    val id: String,
    val text: String,
    val contexts: List<String> = emptyList(),
    val tones: List<String> = emptyList(),
    val intensity: Double = 0.5,
    val intrusion: Double = 0.4,
    val kindness: Double = 0.8,
    val absurdity: Double = 0.2,
    val requires: List<String> = emptyList(),
    val preferredFor: List<String> = emptyList(),
    val blockedFor: List<String> = emptyList(),
    val language: String = "fr",
    val source: String = "local_json",
    val status: String = "accepted",
)
