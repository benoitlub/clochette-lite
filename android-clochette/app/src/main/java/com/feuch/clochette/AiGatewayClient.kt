package com.feuch.clochette

import android.content.Context
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.system.measureTimeMillis

class AiGatewayClient(context: Context) {
    private val appContext = context.applicationContext

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
            AiGatewaySettings.record(appContext, "fallback", "missing_gateway_url")
            return null
        }

        var result: AiRemarkResult? = null
        val latency = measureTimeMillis {
            result = runCatching {
                postJson("$baseUrl/generate-remark", payload(request, config))
            }.getOrNull()
        }
        val provider = result?.providerUsed ?: "fallback"
        AiGatewaySettings.record(
            appContext,
            provider,
            if (result != null) "OK" else "fallback local",
            latency,
        )
        return result
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
            parseResult(JSONObject(raw))
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResult(json: JSONObject): AiRemarkResult? {
        val line = json.optString("line").takeIf { it.isNotBlank() } ?: return null
        val provider = json.optString("providerUsed", "gateway").ifBlank { "gateway" }
        return AiRemarkResult(
            line = line.withVisibleFrenchAccents(),
            shouldSpeak = json.optBoolean("shouldSpeak", true),
            shouldOpenMic = json.optBoolean("shouldOpenMic", false),
            listenSeconds = json.optInt("listenSeconds", 15).coerceIn(1, 15),
            providerUsed = provider,
            source = sourceForProvider(provider, json.optString("source")),
        )
    }

    private fun payload(request: AiRemarkRequest, config: AiGatewayConfig): JSONObject = JSONObject()
        .put("systemPrompt", SYSTEM_PROMPT)
        .put("personaId", "clochette")
        .put("relationshipMode", request.relationshipMode)
        .put("preferredProvider", config.preferredProvider)
        .put("styleLevel", config.styleLevel)
        .put("foregroundApp", request.foregroundApp)
        .put("durationMinutes", request.durationMinutes)
        .put("appSwitchCount", request.appSwitchCount)
        .put("sensorSummary", request.sensorSummary)
        .put("energy", request.energy)
        .put("recentMemorySummary", request.recentMemorySummary)
        .put("nowPlaying", JSONObject()
            .put("appName", request.nowPlayingAppName)
            .put("title", request.nowPlayingTitle)
            .put("artist", request.nowPlayingArtist))
        .put("userLastReply", request.userLastReply)
        .put("language", "fr-FR")

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
