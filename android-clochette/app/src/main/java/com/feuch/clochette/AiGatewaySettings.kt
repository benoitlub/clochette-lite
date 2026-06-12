package com.feuch.clochette

import android.content.Context

data class AiGatewayConfig(
    val enabled: Boolean = false,
    val gatewayUrl: String = "",
    val preferredProvider: String = AiGatewaySettings.PROVIDER_AUTO,
    val styleLevel: String = AiGatewaySettings.STYLE_NATUREL,
    val lastProviderUsed: String? = null,
    val lastStatus: String? = null,
    val lastLatencyMs: Long? = null,
)

object AiGatewaySettings {
    const val PROVIDER_AUTO = "auto"
    const val PROVIDER_MISTRAL = "mistral"
    const val PROVIDER_GEMINI = "gemini"
    const val PROVIDER_LOCAL = "local"

    const val STYLE_NATUREL = "naturel"
    const val STYLE_ESPIEGLE = "espiegle"
    const val STYLE_FEUCH = "feuch"

    private const val PREFS = "clochette_ai_gateway"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_GATEWAY_URL = "gateway_url"
    private const val KEY_PROVIDER = "preferred_provider"
    private const val KEY_STYLE = "style_level"
    private const val KEY_LAST_PROVIDER = "last_provider_used"
    private const val KEY_LAST_STATUS = "last_status"
    private const val KEY_LAST_LATENCY = "last_latency_ms"

    fun read(context: Context): AiGatewayConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val latency = prefs.getLong(KEY_LAST_LATENCY, -1L).takeIf { it >= 0L }
        return AiGatewayConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            gatewayUrl = prefs.getString(KEY_GATEWAY_URL, "").orEmpty(),
            preferredProvider = prefs.getString(KEY_PROVIDER, PROVIDER_AUTO) ?: PROVIDER_AUTO,
            styleLevel = prefs.getString(KEY_STYLE, STYLE_NATUREL) ?: STYLE_NATUREL,
            lastProviderUsed = prefs.getString(KEY_LAST_PROVIDER, null),
            lastStatus = prefs.getString(KEY_LAST_STATUS, null),
            lastLatencyMs = latency,
        )
    }

    fun save(context: Context, config: AiGatewayConfig) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putString(KEY_GATEWAY_URL, config.gatewayUrl)
            .putString(KEY_PROVIDER, config.preferredProvider)
            .putString(KEY_STYLE, config.styleLevel)
            .putString(KEY_LAST_PROVIDER, config.lastProviderUsed)
            .putString(KEY_LAST_STATUS, config.lastStatus)
            .putLong(KEY_LAST_LATENCY, config.lastLatencyMs ?: -1L)
            .apply()
    }

    fun record(context: Context, provider: String?, status: String, latencyMs: Long? = null) {
        val current = read(context)
        save(
            context,
            current.copy(
                lastProviderUsed = provider ?: current.lastProviderUsed,
                lastStatus = status,
                lastLatencyMs = latencyMs,
            ),
        )
    }
}
