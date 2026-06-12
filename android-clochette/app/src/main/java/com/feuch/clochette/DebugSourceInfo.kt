package com.feuch.clochette

import android.content.Context
import org.json.JSONObject

data class DebugSourceInfo(
    val source: DebugSource,
    val prefix: String,
    val visibleOnlyInDebug: Boolean = true,
)

enum class DebugSource {
    SHARED_LIBRARY,
    PERSONA_TRAITS,
    GUARDIAN,
    RELATIONSHIP_MODE,
    DREAMER,
    ARCHIVIST,
    DIPLOMAT,
    LEGACY,
}

class DebugSourceTracer(context: Context) {
    private val appContext = context.applicationContext

    fun prefixFor(source: DebugSource, debug: Boolean): String {
        if (!debug) return ""
        return witnessMap()[source]?.prefix ?: defaultPrefix(source)
    }

    fun witnessLine(source: DebugSource, debug: Boolean): String? {
        if (!debug) return null
        return witnessMap()[source]?.line
    }

    fun info(source: DebugSource): DebugSourceInfo = DebugSourceInfo(
        source = source,
        prefix = witnessMap()[source]?.prefix ?: defaultPrefix(source),
    )

    fun testPlanAvailable(): Boolean = runCatching {
        val raw = appContext.assets.open(DEBUG_PLAN_PATH).bufferedReader().use { it.readText() }
        JSONObject(raw).optString("id") == "octopus_debug_test_plan"
    }.getOrDefault(false)

    private fun witnessMap(): Map<DebugSource, Witness> = runCatching {
        val raw = appContext.assets.open(WITNESS_PATH).bufferedReader().use { it.readText() }
        val json = JSONObject(raw)
        val witnesses = json.optJSONArray("witnesses")
        buildMap {
            for (index in 0 until (witnesses?.length() ?: 0)) {
                val item = witnesses?.optJSONObject(index) ?: continue
                mapSource(item.optString("source"))?.let { source ->
                    put(
                        source,
                        Witness(
                            prefix = item.optString("debugPrefix", defaultPrefix(source)),
                            line = item.optString("line"),
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptyMap())

    private fun mapSource(raw: String): DebugSource? = when (raw) {
        "shared_library" -> DebugSource.SHARED_LIBRARY
        "persona_traits" -> DebugSource.PERSONA_TRAITS
        "guardian" -> DebugSource.GUARDIAN
        "relationship_mode" -> DebugSource.RELATIONSHIP_MODE
        "dreamer" -> DebugSource.DREAMER
        "archivist" -> DebugSource.ARCHIVIST
        "diplomat_local_noop" -> DebugSource.DIPLOMAT
        "legacy_engine" -> DebugSource.LEGACY
        else -> null
    }

    private fun defaultPrefix(source: DebugSource): String = when (source) {
        DebugSource.SHARED_LIBRARY -> "[LIBRARY]"
        DebugSource.PERSONA_TRAITS -> "[TRAITS]"
        DebugSource.GUARDIAN -> "[GUARDIAN]"
        DebugSource.RELATIONSHIP_MODE -> "[MODE]"
        DebugSource.DREAMER -> "[DREAMER]"
        DebugSource.ARCHIVIST -> "[ARCHIVIST]"
        DebugSource.DIPLOMAT -> "[DIPLOMAT:LOCAL]"
        DebugSource.LEGACY -> "[LEGACY]"
    }

    private data class Witness(
        val prefix: String,
        val line: String,
    )

    companion object {
        private const val WITNESS_PATH = "octopus/test_witness_lines.json"
        private const val DEBUG_PLAN_PATH = "octopus/debug_test_plan.json"
    }
}
