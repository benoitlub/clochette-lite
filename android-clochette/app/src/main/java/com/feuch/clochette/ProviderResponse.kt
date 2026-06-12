package com.feuch.clochette

data class ProviderResponse(
    val provider: Provider,
    val candidates: List<String> = emptyList(),
    val summary: String? = null,
    val notes: List<String> = emptyList(),
    val cooldownSuggestionMinutes: Int = 0,
    val externalCallMade: Boolean = false,
)
