package com.feuch.clochette

import android.content.Context

class RemotePersonaRepository(private val context: Context) {
    fun fetchPersonaOrNull(current: PersonaProfile?): PersonaProfile? {
        val remoteConfig = current?.remote.orEmpty()
        val enabled = remoteConfig["enabled"] as? Boolean ?: false
        val url = remoteConfig["url"] as? String
        if (!enabled || url.isNullOrBlank()) return null

        // Remote sync is intentionally not activated yet. Future code can fetch `url`,
        // validate the JSON, then hand it to PersonaLoader.cachePersona.
        return runCatching {
            context.applicationContext
            null
        }.getOrNull()
    }
}
