package com.feuch.clochette

import android.content.Context
import org.json.JSONObject
import kotlin.math.abs

class ContextRemarkEngine(context: Context) {
    private val appContext = context.applicationContext

    fun remark(activity: ActivitySnapshot, memory: List<ClochetteMemoryEntry>): String? {
        val model = loadModel() ?: return null
        val durationBand = model.durationBands
            .firstOrNull { activity.approximateDurationMs.toMinutes() in it.minMinutes..it.maxMinutes }
            ?.id
            ?: model.durationBands.firstOrNull()?.id
            ?: return model.fallbackLines.pick(activity, memory)

        val packageName = activity.foregroundPackage.orEmpty().lowercase()
        val displayName = activity.foregroundDisplayName.orEmpty().lowercase()
        val app = model.apps.firstOrNull { candidate ->
            candidate.packageHints.any { hint ->
                val normalized = hint.lowercase()
                packageName.contains(normalized) || displayName.contains(normalized)
            }
        }
        val lines = app?.lines?.get(durationBand)
            ?: app?.lines?.values?.firstOrNull { it.isNotEmpty() }
            ?: model.fallbackLines
        return lines.pick(activity, memory)
    }

    private fun loadModel(): ContextLinesModel? = runCatching {
        val raw = appContext.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val json = JSONObject(raw)
        val durationBands = json.optJSONArray("durationBands").toList { item ->
            DurationBand(
                id = item.optString("id"),
                minMinutes = item.optInt("minMinutes", 0),
                maxMinutes = item.optInt("maxMinutes", Int.MAX_VALUE),
            )
        }.filter { it.id.isNotBlank() }
        val apps = json.optJSONArray("apps").toList { item ->
            val linesJson = item.optJSONObject("lines")
            ContextAppLines(
                displayName = item.optString("displayName"),
                packageHints = item.optJSONArray("packageHints").toStringList(),
                lines = linesJson?.keys()?.asSequence()?.associateWith { key ->
                    linesJson.optJSONArray(key).toStringList()
                }.orEmpty(),
            )
        }
        ContextLinesModel(
            durationBands = durationBands,
            apps = apps,
            fallbackLines = json.optJSONArray("fallbackLines").toStringList(),
        )
    }.getOrNull()

    private fun List<String>.pick(activity: ActivitySnapshot, memory: List<ClochetteMemoryEntry>): String? {
        if (isEmpty()) return null
        val seed = activity.foregroundPackage.orEmpty().sumOf { it.code } +
            activity.recentSwitchCount +
            memory.fold(0) { total, entry -> total + (entry.timestamp % 31).toInt() }
        return this[abs(seed) % size]
    }

    private fun Long.toMinutes(): Int = (this / 60_000L).toInt().coerceAtLeast(0)

    private data class ContextLinesModel(
        val durationBands: List<DurationBand>,
        val apps: List<ContextAppLines>,
        val fallbackLines: List<String>,
    )

    private data class DurationBand(
        val id: String,
        val minMinutes: Int,
        val maxMinutes: Int,
    )

    private data class ContextAppLines(
        val displayName: String,
        val packageHints: List<String>,
        val lines: Map<String, List<String>>,
    )

    private companion object {
        const val ASSET_PATH = "personas/clochette/app_context_lines.json"
    }
}

private fun <T> org.json.JSONArray?.toList(mapper: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(mapper) }
}

private fun org.json.JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
}
