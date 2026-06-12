package com.feuch.clochette

import android.content.Context
import org.json.JSONObject

class DreamManager(context: Context) {
    private val appContext = context.applicationContext

    fun preparePrivateCycle(): DreamCycle = DreamCycle(
        note = "Je vais relire mon carnet.",
        candidates = emptyList(),
        automatic = false,
    )

    fun rejectionNote(rejectedCount: Int): DreamCycle = DreamCycle(
        note = "J'ai rejeté $rejectedCount idées absurdes.",
        candidates = emptyList(),
        automatic = false,
    )

    fun moduleAvailable(): Boolean = runCatching {
        val raw = appContext.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        JSONObject(raw).optString("id").isNotBlank()
    }.getOrDefault(false)

    companion object {
        private const val ASSET_PATH = "personas/clochette/dreams.json"
    }
}
