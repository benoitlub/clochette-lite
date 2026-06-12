package com.feuch.clochette

class LocalProvider {
    fun respond(task: String, contextSummary: String): ProviderResponse = ProviderResponse(
        provider = Provider.LOCAL,
        candidates = emptyList(),
        summary = "LocalProvider no-op for $task",
        notes = listOf("external_calls_disabled", contextSummary.take(80)),
        cooldownSuggestionMinutes = 0,
        externalCallMade = false,
    )
}
