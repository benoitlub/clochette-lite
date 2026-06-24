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
    val score: Double = 0.0,
    val reason: String = "",
    val rejectedRecent: Int = 0,
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
        character: CharacterProfile? = null,
        conversation: ConversationContext? = null,
    ): PhraseBankSelection? {
        val tags = contextTags(trigger, state, preferQuestion, conversation)
        val modeId = relationshipMode.id
        val personality = ClochettePersonalitySettings.read(context)
        val tone = preferredTone ?: ClochettePersonalitySettings.preferredTone(personality)
        val recentLines = recentMemory.mapNotNull { it.clochetteLine }.map { it.normalizedLine() }.toSet()
        var rejectedRecent = 0
        val candidates = loadEntries(context)
            .filter { it.status == "accepted" }
            .filter { it.line.isNotBlank() }
            .filter { it.triggers.isEmpty() || trigger in it.triggers }
            .filter { it.relationshipModes.isEmpty() || modeId in it.relationshipModes }
            .filterNot {
                val rejected = it.line.normalizedLine() in recentLines
                if (rejected) rejectedRecent += 1
                rejected
            }
            .mapNotNull { entry ->
                val score = score(entry, trigger, tags, modeId, preferQuestion, tone, personality, character, conversation)
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
            score = bestScore,
            reason = "matched tags=${tags.intersect(picked.contexts.toSet()).joinToString("+").ifBlank { "general" }} character=${character?.id ?: "none"} mood=${conversation?.mood ?: "-"} intent=${conversation?.intent ?: "-"}",
            rejectedRecent = rejectedRecent,
        )
    }

    private fun score(
        entry: PhraseBankEntry,
        trigger: String,
        tags: Set<String>,
        modeId: String,
        preferQuestion: Boolean,
        preferredTone: String?,
        personality: ClochettePersonalityConfig,
        character: CharacterProfile?,
        conversation: ConversationContext?,
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
        character?.let {
            val characterToneMatches = it.toneTags.count { tag -> tag in entry.contexts || tag.equals(entry.tone, ignoreCase = true) }
            score += characterToneMatches * 14.0
            if (entry.bankId in it.phraseBanks) score += 18.0
        }
        conversation?.let {
            val conversationMatches = it.tags.count { tag -> tag in entry.contexts }
            score += conversationMatches * 24.0
            if (it.intent == "needs_motivation" && ("focus" in entry.contexts || entry.bankId == "focus")) score += 18.0
            if (it.mood == "tired" && (entry.bankId == "fatigue" || "fatigue" in entry.contexts || "soft" in entry.contexts)) score += 22.0
            if (it.mood == "playful" && (entry.bankId == "teasing" || "playful" in entry.contexts)) score += 12.0
        }
        if (entry.line.contains("?") && preferQuestion) score += 12.0
        score += personalityToneScore(entry, personality)
        score += personalityLengthScore(entry, personality)
        return score
    }

    private fun personalityToneScore(entry: PhraseBankEntry, personality: ClochettePersonalityConfig): Double {
        var score = 0.0
        val tone = entry.tone.lowercase()
        val bank = entry.bankId.lowercase()
        val contexts = entry.contexts.map { it.lowercase() }.toSet()
        if (personality.teasing >= 60 && (tone == "teasing" || bank == "teasing" || "playful" in contexts)) {
            score += (personality.teasing - 50) * 0.9
        }
        if (personality.teasing <= 25 && (tone == "teasing" || bank == "teasing")) {
            score -= 35.0
        }
        if (personality.softness >= 60 && (tone == "soft" || bank == "soft" || "calm" in contexts || "soft" in contexts)) {
            score += (personality.softness - 50) * 0.9
        }
        if (personality.softness <= 25 && (tone == "soft" || bank == "soft")) {
            score -= 18.0
        }
        if (personality.curiosity >= 60 && (entry.canAskMic || entry.line.contains("?") || bank == "micro_questions" || "curious" in contexts)) {
            score += (personality.curiosity - 50) * 0.9
        }
        if (personality.curiosity <= 25 && (entry.canAskMic || entry.line.contains("?"))) {
            score -= 35.0
        }
        return score
    }

    private fun personalityLengthScore(entry: PhraseBankEntry, personality: ClochettePersonalityConfig): Double {
        val words = entry.line.split(Regex("\\s+")).count { it.isNotBlank() }
        return when {
            personality.phraseLength <= 30 && words <= 12 -> 18.0
            personality.phraseLength <= 30 && words >= 20 -> -24.0
            personality.phraseLength >= 70 && words >= 16 -> 16.0
            personality.phraseLength >= 70 && words <= 8 -> -10.0
            else -> 0.0
        }
    }

    private fun contextTags(trigger: String, state: ContextState, preferQuestion: Boolean, conversation: ConversationContext?): Set<String> {
        val tags = mutableSetOf(trigger, "local", "focus")
        conversation?.let {
            tags += it.intent
            tags += it.mood
            tags += "energy_${it.energy}"
            tags += it.tags
        }
        if (preferQuestion) {
            tags += "micro"
            tags += "asking"
        }
        if (state.durationMinutes >= 20) tags += "app_long"
        if (state.recentAppSwitches >= 4) tags += "switching"
        if (state.dayPeriod == DayPeriod.NIGHT) tags += "night"
        if (state.userEnergyEstimate == UserEnergyEstimate.LOW) tags += "fatigue"
        val app = state.currentAppName.orEmpty().lowercase()
        if ("codex" in app) tags += "codex"
        if ("chatgpt" in app || "chat gpt" in app) tags += "creative"
        if ("github" in app) tags += "codex"
        if ("notion" in app) tags += "creative"
        if ("blacklace" in app) tags += "blacklace"
        return tags
    }

    private fun loadEntries(context: Context): List<PhraseBankEntry> {
        val assets = context.applicationContext.assets
        return BANKS.flatMap { bank ->
            runCatching {
                val raw = assets.open(bank.path).bufferedReader(Charsets.UTF_8).use { it.readText() }
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

    private const val BASE_DIR = "personas/clochette/phrase_banks"

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
}

private fun <T> JSONArray?.toObjectList(mapper: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(mapper) }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
}
