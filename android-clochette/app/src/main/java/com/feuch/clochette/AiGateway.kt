package com.feuch.clochette

import android.content.Context
import org.json.JSONObject

class AiGateway(context: Context? = null) {
    private val appContext = context?.applicationContext
    private val localProvider = LocalProvider()

    val plannedOrder: List<Provider> = listOf(
        Provider.MISTRAL,
        Provider.GEMINI,
        Provider.OPENAI,
        Provider.OLLAMA,
    )

    fun routeLocalOnly(
        personaId: String = "clochette",
        task: String,
        contextSummary: String,
    ): ProviderResponse {
        val contractOk = contractAvailable()
        return localProvider.respond(
            task = task,
            contextSummary = "persona=$personaId contract=$contractOk $contextSummary",
        )
    }

    fun generateLocalOnly(prompt: String): ProviderResponse = routeLocalOnly(
        task = "generate_lines",
        contextSummary = prompt.take(120),
    )

    fun contractAvailable(): Boolean = runCatching {
        val context = appContext ?: return false
        val raw = context.assets.open(CONTRACT_PATH).bufferedReader().use { it.readText() }
        JSONObject(raw).optString("id") == "octopus_diplomat_contract"
    }.getOrDefault(false)

    companion object {
        private const val CONTRACT_PATH = "octopus/diplomat_contract.json"
    }
}
