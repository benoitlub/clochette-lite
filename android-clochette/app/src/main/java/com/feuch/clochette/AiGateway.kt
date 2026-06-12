package com.feuch.clochette

data class AiGatewayResult(
    val provider: Provider? = null,
    val text: String? = null,
    val skippedReason: String = "network_disabled",
)

class AiGateway {
    val plannedOrder: List<Provider> = listOf(
        Provider.MISTRAL,
        Provider.GEMINI,
        Provider.OPENAI,
        Provider.OLLAMA,
    )

    fun generateLocalOnly(prompt: String): AiGatewayResult = AiGatewayResult(
        provider = null,
        text = null,
        skippedReason = "No network call: ${prompt.take(24)}",
    )
}
