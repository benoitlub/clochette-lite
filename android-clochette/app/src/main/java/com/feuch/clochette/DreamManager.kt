package com.feuch.clochette

import android.content.Context
import org.json.JSONObject
import kotlin.math.abs

class DreamManager(context: Context) {
    private val appContext = context.applicationContext

    fun preparePrivateCycle(seed: Int = 0): DreamCycle {
        val contract = loadContract()
        return DreamCycle(
            state = DreamState.PRIVATE_MOMENT,
            note = contract.privateMomentLines.pick(seed) ?: "Je vais relire mon carnet.",
            candidates = emptyList(),
            automatic = false,
        )
    }

    fun prepareCandidateLines(memory: List<MemorySummary> = emptyList()): List<DreamCandidate> {
        if (memory.isEmpty()) return emptyList()
        return memory.takeLast(3).mapIndexed { index, summary ->
            DreamCandidate(
                line = "Je garde une idée courte depuis le carnet : ${summary.summary}",
                reason = "memory_summary_$index",
                accepted = false,
            )
        }
    }

    fun prepareFromConsolidatedMemory(limit: Int = 3): DreamCycle {
        val memories = ConsolidatedMemoryStore(appContext)
            .active(24)
            .filter { it.confidence != MemorySignal.LOW && it.usefulness != MemorySignal.LOW }
            .takeLast(limit.coerceIn(1, 5))

        val candidates = memories.mapIndexed { index, memory ->
            DreamCandidate(
                line = memory.lightweightSummary,
                reason = "consolidated_${memory.userIntent ?: "signal"}_$index",
                source = "consolidated_memory",
                accepted = false,
            )
        }
        return DreamCycle(
            state = if (candidates.isEmpty()) DreamState.SLEEPING else DreamState.REVIEWING_CANDIDATES,
            note = if (candidates.isEmpty()) {
                "Rien d'assez solide à rêvasser pour le moment."
            } else {
                "Je relis uniquement les souvenirs déjà consolidés."
            },
            candidates = candidates,
            automatic = false,
        )
    }

    fun returnFromPrivateMoment(rejectedCount: Int = 0, adoptedCount: Int = 0): DreamReturn {
        val contract = loadContract()
        val line = contract.returnLines.pick(rejectedCount + adoptedCount)
            ?: "J'ai rejeté $rejectedCount idées absurdes."
        return DreamReturn(
            line = line,
            miniChangelog = listOf(
                "adopted=$adoptedCount",
                "rejected=$rejectedCount",
                "automatic=false",
            ),
            adoptedCount = adoptedCount,
            rejectedCount = rejectedCount,
        )
    }

    fun moduleAvailable(): Boolean = loadContract().available

    private fun loadContract(): DreamerContract = runCatching {
        val raw = appContext.assets.open(CONTRACT_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = JSONObject(raw)
        DreamerContract(
            available = json.optString("id") == "octopus_dreamer_contract",
            privateMomentLines = json.optJSONArray("privateMomentLines").toDreamStringList(),
            returnLines = json.optJSONArray("returnLines").toDreamStringList(),
        )
    }.getOrDefault(DreamerContract())

    private fun List<String>.pick(seed: Int): String? {
        if (isEmpty()) return null
        return this[abs(seed) % size]
    }

    private data class DreamerContract(
        val available: Boolean = false,
        val privateMomentLines: List<String> = emptyList(),
        val returnLines: List<String> = emptyList(),
    )

    companion object {
        private const val CONTRACT_PATH = "octopus/dreamer_contract.json"
    }
}

private fun org.json.JSONArray?.toDreamStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
}
