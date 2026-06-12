package com.feuch.clochette

import android.content.Context
import org.json.JSONObject

data class GuardianDecision(
    val line: String?,
    val shouldSpeak: Boolean,
    val reason: String,
)

class GuardianRulesLoader(context: Context) {
    private val appContext = context.applicationContext
    private val rules = loadRules()

    fun approve(
        candidate: String,
        state: ContextState,
        recentLines: List<String>,
        recentEntries: List<ClochetteMemoryEntry> = emptyList(),
        relationshipMode: RelationshipMode = RelationshipModeSettings.selected(appContext),
        wantsVoice: Boolean = false,
    ): GuardianDecision {
        val normalized = candidate.lowercase()
        if (candidate.isBlank()) {
            return GuardianDecision(rules.contextFallback, false, "empty_candidate")
        }
        if (isAbstractWithoutContext(normalized, state)) {
            return GuardianDecision(rules.contextFallback, false, "anti_absurd")
        }
        if (recentLines.any { similar(it, candidate) }) {
            return GuardianDecision(rules.repeatFallback, false, "anti_repeat")
        }
        if (recentEntries.takeLast(5).any { entry ->
                entry.userReaction?.contains("pause", ignoreCase = true) == true ||
                    entry.userReaction?.contains("refus", ignoreCase = true) == true ||
                    entry.result?.contains("closed", ignoreCase = true) == true ||
                    entry.result?.contains("refused", ignoreCase = true) == true
            }
        ) {
            return GuardianDecision(null, false, "silence_respect")
        }
        if (state.dayPeriod == DayPeriod.NIGHT || relationshipMode.id == "quiet_night") {
            val line = if (candidate.split(Regex("\\s+")).size > 18) rules.nightFallback else candidate
            return GuardianDecision(line, false, "night_care")
        }
        if (state.durationMinutes >= 90 && state.recentAppSwitches <= 1 && relationshipMode.id != "alive") {
            return GuardianDecision(rules.deepWorkFallback, false, "deep_work")
        }
        return GuardianDecision(
            line = candidate,
            shouldSpeak = wantsVoice && relationshipMode.voiceDefault,
            reason = "approved",
        )
    }

    private fun isAbstractWithoutContext(line: String, state: ContextState): Boolean {
        val hasContext = !state.currentAppName.isNullOrBlank() ||
            state.durationMinutes > 0 ||
            state.batteryPercent != null ||
            state.recentAppSwitches > 0
        val blocked = rules.blockedPatterns.any { pattern ->
            val normalized = pattern.lowercase()
            line.contains(normalized) ||
                (normalized.contains("mystique") && listOf("vide", "chemins", "portes", "destin").any { line.contains(it) }) ||
                (normalized.contains("certitude") && (line.startsWith("je sais") || line.contains("certainement")))
        }
        return blocked && !hasContext
    }

    private fun similar(previous: String, next: String): Boolean {
        val a = previous.lowercase().words()
        val b = next.lowercase().words()
        if (a.isEmpty() || b.isEmpty()) return false
        val overlap = a.intersect(b).size
        return previous.equals(next, ignoreCase = true) || overlap >= 6
    }

    private fun String.words(): Set<String> =
        split(Regex("[^a-zA-ZÀ-ÿ0-9']+")).filter { it.length > 3 }.toSet()

    private fun loadRules(): GuardianRules = runCatching {
        val raw = appContext.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val json = JSONObject(raw)
        val checks = json.optJSONArray("checks")
        val fallbacks = (0 until (checks?.length() ?: 0)).mapNotNull { index ->
            checks?.optJSONObject(index)?.optString("id") to checks?.optJSONObject(index)?.optString("fallback")
        }.toMap()
        GuardianRules(
            contextFallback = fallbacks["anti_absurd"].orEmpty().ifBlank { DEFAULT_CONTEXT_FALLBACK },
            repeatFallback = fallbacks["anti_repeat"].orEmpty().ifBlank { DEFAULT_REPEAT_FALLBACK },
            nightFallback = fallbacks["night_care"].orEmpty().ifBlank { DEFAULT_NIGHT_FALLBACK },
            deepWorkFallback = fallbacks["deep_work"].orEmpty().ifBlank { DEFAULT_DEEP_WORK_FALLBACK },
            blockedPatterns = json.optJSONArray("blockedPatterns").toGuardianStringList(),
        )
    }.getOrDefault(
        GuardianRules(
            contextFallback = DEFAULT_CONTEXT_FALLBACK,
            repeatFallback = DEFAULT_REPEAT_FALLBACK,
            nightFallback = DEFAULT_NIGHT_FALLBACK,
            deepWorkFallback = DEFAULT_DEEP_WORK_FALLBACK,
            blockedPatterns = emptyList(),
        ),
    )

    private data class GuardianRules(
        val contextFallback: String,
        val repeatFallback: String,
        val nightFallback: String,
        val deepWorkFallback: String,
        val blockedPatterns: List<String>,
    )

    companion object {
        private const val ASSET_PATH = "personas/clochette/guardian_rules.json"
        private const val DEFAULT_CONTEXT_FALLBACK = "Je peux me tromper, mais je remarque juste que ça dure un peu."
        private const val DEFAULT_REPEAT_FALLBACK = "Je garde celle-là pour plus tard."
        private const val DEFAULT_NIGHT_FALLBACK = "Je vais parler plus doucement. Les pixels aussi peuvent chuchoter."
        private const val DEFAULT_DEEP_WORK_FALLBACK = "Je te laisse dans ton tunnel. Je monte la garde près de l'entrée."
    }
}

private fun org.json.JSONArray?.toGuardianStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
}
