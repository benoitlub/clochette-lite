package com.feuch.clochette

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

data class PhraseBankSelection(
    val id: String,
    val line: String,
    val tone: String,
    val bankId: String,
    val source: PhraseSource,
    val canSpeak: Boolean,
    val canAskMic: Boolean,
)

object PhraseBankSelector {
    fun select(
        context: Context,
        trigger: String,
        state: ContextState,
        relationshipMode: RelationshipMode,
        recentMemory: List<ClochetteMemoryEntry>,
        preferQuestion: Boolean = false,
        preferredTone: String? = null,
    ): PhraseBankSelection? {
        val tags = contextTags(trigger, state, preferQuestion)
        val modeId = relationshipMode.id
        val recentLines = recentMemory.mapNotNull { it.clochetteLine }.map { it.normalizedLine() }.toSet()
        val candidates = loadEntries(context)
            .filter { it.status == "accepted" }
            .filter { it.line.isNotBlank() }
            .filter { it.triggers.isEmpty() || trigger in it.triggers }
            .filter { it.relationshipModes.isEmpty() || modeId in it.relationshipModes }
            .filterNot { it.line.normalizedLine() in recentLines }
            .mapNotNull { entry ->
                val score = score(entry, trigger, tags, modeId, preferQuestion, preferredTone)
                if (score <= 0.0) null else entry to score
            }

        if (candidates.isEmpty()) return null
        val bestScore = candidates.maxOf { it.second }
        val best = candidates.filter { it.second == bestScore }.map { it.first }
        val seed = abs(
            state.currentAppName.orEmpty().sumOf { it.code } +
                state.durationMinutes +
                state.recentAppSwitches +
                recentMemory.size +
                trigger.sumOf { it.code },
        )
        val picked = best[seed % best.size]
        return PhraseBankSelection(
            id = picked.id,
            line = picked.line.withVisibleFrenchAccents(),
            tone = picked.tone,
            bankId = picked.bankId,
            source = picked.source,
            canSpeak = picked.canSpeak,
            canAskMic = picked.canAskMic,
        )
    }

    private fun score(
        entry: PhraseBankEntry,
        trigger: String,
        tags: Set<String>,
        modeId: String,
        preferQuestion: Boolean,
        preferredTone: String?,
    ): Double {
        val contextMatches = entry.contexts.count { it in tags }
        val hasContextMatch = entry.contexts.isEmpty() || contextMatches > 0
        if (!hasContextMatch) return 0.0
        var score = entry.weight * 100.0
        score += contextMatches * 18.0
        if (trigger in entry.triggers) score += 18.0
        if (modeId in entry.relationshipModes) score += 10.0
        if (preferQuestion && entry.canAskMic) score += 28.0
        if (!preferQuestion && entry.canAskMic) score -= 4.0
        if (preferredTone != null && entry.tone.equals(preferredTone, ignoreCase = true)) score += 22.0
        if (entry.line.contains("?") && preferQuestion) score += 12.0
        return score
    }

    private fun contextTags(trigger: String, state: ContextState, preferQuestion: Boolean): Set<String> = buildSet {
        add(trigger)
        add("local")
        add("focus")
        if (preferQuestion) {
            add("micro")
            add("asking")
        }
        if (state.durationMinutes >= 20) add("app_long")
        if (state.recentAppSwitches >= 4) add("switching")
        if (state.dayPeriod == DayPeriod.NIGHT) add("night")
        if (state.userEnergyEstimate == UserEnergyEstimate.LOW) add("fatigue")
        val app = state.currentAppName.orEmpty().lowercase()
        if ("codex" in app) add("codex")
        if ("chatgpt" in app || "chat gpt" in app) add("creative")
        if ("github" in app) add("codex")
        if ("notion" in app) add("creative")
        if ("blacklace" in app) add("blacklace")
    }

    private fun loadEntries(context: Context): List<PhraseBankEntry> {
        val assets = context.applicationContext.assets
        return BANKS.flatMap { bank ->
            runCatching {
                val raw = assets.open(bank.path).bufferedReader().use { it.readText() }
                val json = JSONObject(raw)
                val tone = json.optString("tone", bank.id).ifBlank { bank.id }
                json.optJSONArray("entries").toObjectList { item ->
                    PhraseBankEntry(
                        id = item.optString("id"),
                        line = item.optString("line"),
                        tone = item.optString("tone", tone).ifBlank { tone },
                        contexts = item.optJSONArray("contexts").toStringList(),
                        triggers = item.optJSONArray("triggers").toStringList(),
                        relationshipModes = item.optJSONArray("relationshipModes").toStringList(),
                        canSpeak = item.optBoolean("canSpeak", true),
                        canAskMic = item.optBoolean("canAskMic", false),
                        weight = item.optDouble("weight", 1.0),
                        status = item.optString("status", "draft"),
                        bankId = bank.id,
                        source = bank.source,
                    )
                }
            }.getOrDefault(emptyList())
        }
    }

    private fun String.normalizedLine(): String = lowercase().trim().replace(Regex("\\s+"), " ")

    private data class Bank(
        val id: String,
        val fileName: String,
        val source: PhraseSource,
    ) {
        val path: String = "$BASE_DIR/$fileName"
    }

    private data class PhraseBankEntry(
        val id: String,
        val line: String,
        val tone: String,
        val contexts: List<String>,
        val triggers: List<String>,
        val relationshipModes: List<String>,
        val canSpeak: Boolean,
        val canAskMic: Boolean,
        val weight: Double,
        val status: String,
        val bankId: String,
        val source: PhraseSource,
    )

    private val BANKS = listOf(
        Bank("micro_questions", "micro_questions.json", PhraseSource.PROACTIVE_QUESTION),
        Bank("focus", "focus.json", PhraseSource.LOCAL_PROACTIVE),
        Bank("natural", "natural.json", PhraseSource.LOCAL_NATURAL),
        Bank("teasing", "teasing.json", PhraseSource.LOCAL_PROACTIVE),
        Bank("soft", "soft.json", PhraseSource.LOCAL_NATURAL),
        Bank("badass", "badass.json", PhraseSource.LOCAL_PROACTIVE),
        Bank("fatigue", "fatigue.json", PhraseSource.LOCAL_NATURAL),
        Bank("creative", "creative.json", PhraseSource.LOCAL_PROACTIVE),
        Bank("silence_responses", "silence_responses.json", PhraseSource.LOCAL_NATURAL),
    )

    private const val BASE_DIR = "personas/clochette/phrase_banks"
}

private fun <T> JSONArray?.toObjectList(mapper: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(mapper) }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
}
