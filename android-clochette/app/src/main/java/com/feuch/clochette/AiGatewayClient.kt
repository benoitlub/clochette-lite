package com.feuch.clochette

import android.content.Context
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.measureTimeMillis

class AiGatewayClient(context: Context) {
    private val appContext = context.applicationContext

    fun health(): GatewayHealthResult {
        val config = AiGatewaySettings.read(appContext)
        val baseUrl = config.gatewayUrl.trim().trimEnd('/')
        if (!config.enabled) {
            AiGatewaySettings.record(appContext, "aucun", "désactivée", 0L)
            return GatewayHealthResult(false, "désactivée", "IA distante désactivée")
        }
        if (baseUrl.isBlank()) {
            AiGatewaySettings.record(appContext, "fallback", "Non configuré", 0L, "Gateway URL vide")
            return GatewayHealthResult(false, "Non configuré", "Gateway URL vide")
        }
        var result = GatewayHealthResult(false, "Erreur", "Réponse absente")
        val latency = measureTimeMillis {
            result = runCatching {
                getHealth("$baseUrl/health")
            }.getOrElse {
                GatewayHealthResult(false, "Erreur", it.message ?: it.javaClass.simpleName)
            }
        }
        AiGatewaySettings.record(
            appContext,
            result.service.takeIf { it.isNotBlank() } ?: config.preferredProvider,
            if (result.ok) "OK" else "Erreur",
            latency,
            error = result.error,
            rawResponse = result.rawResponse,
        )
        return result
    }

    fun generateRemark(request: AiRemarkRequest): AiRemarkResult? {
        val config = AiGatewaySettings.read(appContext)
        if (!config.enabled) {
            AiGatewaySettings.record(appContext, "local", "disabled")
            return null
        }
        if (config.preferredProvider == AiGatewaySettings.PROVIDER_LOCAL) {
            val local = localNaturalRemark(request)
            AiGatewaySettings.record(appContext, "local", "local", 0L)
            return local
        }
        val baseUrl = config.gatewayUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            AiGatewaySettings.record(appContext, "fallback", "missing_gateway_url", error = "Gateway URL vide")
            return null
        }

        var result: AiRemarkResult? = null
        val latency = measureTimeMillis {
            result = runCatching {
                postJson("$baseUrl/mission", missionPayload(request, config))
            }.getOrElse {
                AiGatewaySettings.record(appContext, "fallback", "erreur", error = it.message ?: it.javaClass.simpleName)
                null
            }
        }
        val provider = result?.providerUsed ?: "fallback"
        AiGatewaySettings.record(
            appContext,
            provider,
            if (result != null) "OK" else "fallback local",
            latency,
            rawResponse = result?.line,
        )
        return result
    }

    private fun getHealth(endpoint: String): GatewayHealthResult {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doInput = true
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val raw = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            }
            val json = runCatching { JSONObject(raw) }.getOrNull()
            GatewayHealthResult(
                ok = connection.responseCode in 200..299 && json?.optBoolean("ok", false) == true,
                service = json?.optString("mode", "octopus-engine").orEmpty(),
                error = json?.optString("error").orEmpty().ifBlank { null },
                rawResponse = raw.take(240),
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun postJson(endpoint: String, payload: JSONObject): AiRemarkResult? {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }
            if (connection.responseCode !in 200..299) return null
            val raw = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            AiGatewaySettings.record(appContext, "gateway", "réponse reçue", rawResponse = raw.take(240))
            parseResult(JSONObject(raw))
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResult(json: JSONObject): AiRemarkResult? {
        if (json.optString("status") != "completed") return null
        val output = json.optJSONObject("output") ?: return null
        val line = output.optString("text").takeIf { it.isNotBlank() } ?: return null
        return AiRemarkResult(
            line = line.withVisibleFrenchAccents(),
            shouldSpeak = true,
            shouldOpenMic = line.contains("?"),
            listenSeconds = 15,
            providerUsed = json.optString("executorId", "octopus").ifBlank { "octopus" },
            source = PhraseSource.AI_GATEWAY,
        )
    }

    private fun missionPayload(request: AiRemarkRequest, config: AiGatewayConfig): JSONObject {
        val operationId = "clochette_" + System.currentTimeMillis()
        val context = JSONObject()
            .put("id", "clochette-android")
            .put("label", "Clochette Android")
            .put("objective", "Generate one short contextual Clochette response.")
            .put("metadata", JSONObject()
                .put("personaId", "clochette")
                .put("relationshipMode", request.relationshipMode)
                .put("styleLevel", config.styleLevel)
                .put("foregroundApp", request.foregroundApp)
                .put("durationMinutes", request.durationMinutes)
                .put("appSwitchCount", request.appSwitchCount)
                .put("sensorSummary", request.sensorSummary)
                .put("energy", request.energy)
                .put("recentMemorySummary", request.recentMemorySummary)
                .put("userLastReply", request.userLastReply)
                .put("language", "fr-FR"))

        val prompt = buildString {
            append(SYSTEM_PROMPT)
            append("\nStyle: ").append(config.styleLevel)
            append("\nRelationship: ").append(request.relationshipMode)
            request.foregroundApp?.let { append("\nForeground app: ").append(it) }
            append("\nDuration minutes: ").append(request.durationMinutes)
            append("\nApp switches: ").append(request.appSwitchCount)
            request.sensorSummary?.let { append("\nSensors: ").append(it) }
            request.recentMemorySummary?.let { append("\nRecent memory: ").append(it) }
            request.userLastReply?.let { append("\nUser reply: ").append(it) }
            append("\nReturn only Clochette's line, no JSON and no explanation.")
        }

        return JSONObject()
            .put("operationId", operationId)
            .put("title", "Clochette contextual reply")
            .put("objective", "Generate one concise contextual line in Clochette's persona.")
            .put("context", context)
            .put("requiredCapabilities", org.json.JSONArray().put("copy.generate"))
            .put("prompt", prompt)
    }

    private fun localNaturalRemark(request: AiRemarkRequest): AiRemarkResult {
        val app = request.foregroundApp?.takeIf { it.isNotBlank() } ?: "cette app"
        val line = when {
            request.userLastReply?.isNotBlank() == true ->
                "Je note ta réponse. Je peux te relancer doucement, ou rester discrète."
            request.appSwitchCount >= 5 ->
                "Je vois que tu changes souvent d’application. Tu cherches quelque chose ou tu évites quelque chose ?"
            request.durationMinutes >= 45 ->
                "Tu es sur $app depuis un moment. Tu veux continuer ou faire le point ?"
            else ->
                "Je remarque $app. Je reste discrète, sauf si tu veux une question courte."
        }
        return AiRemarkResult(
            line = line.withVisibleFrenchAccents(),
            shouldSpeak = false,
            shouldOpenMic = false,
            listenSeconds = 15,
            providerUsed = "local",
            source = PhraseSource.LOCAL_FALLBACK,
        )
    }

    private fun sourceForProvider(provider: String, source: String): PhraseSource = when {
        source == PhraseSource.AI_GATEWAY.id -> PhraseSource.AI_GATEWAY
        provider.equals("mistral", ignoreCase = true) -> PhraseSource.MISTRAL
        provider.equals("gemini", ignoreCase = true) -> PhraseSource.GEMINI
        provider.equals("local", ignoreCase = true) -> PhraseSource.LOCAL_FALLBACK
        else -> PhraseSource.AI_GATEWAY
    }

    companion object {
        private const val TIMEOUT_MS = 8_000
        const val SYSTEM_PROMPT =
            "Tu es Clochette, une présence Android légère. Tu n’es pas un assistant corporate. " +
                "Tu parles français naturellement, avec accents. Tu fais des phrases courtes, humaines, " +
                "parfois drôles, jamais cryptiques par défaut. Tu observes seulement les signaux autorisés : " +
                "app au premier plan, durée, bascules, média en cours, mémoire locale résumée. " +
                "Tu ne prétends pas lire le contenu privé. Tu peux poser une question courte. " +
                "Réponds en moins de 25 mots sauf demande contraire."
    }
}

data class GatewayHealthResult(
    val ok: Boolean,
    val service: String,
    val error: String? = null,
    val rawResponse: String? = null,
)
