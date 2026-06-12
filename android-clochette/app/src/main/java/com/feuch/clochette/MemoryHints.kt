package com.feuch.clochette

object MemoryHints {
    fun fromSummary(summary: MemorySummary): List<String> = summary.hints.mapNotNull { hint ->
        when {
            hint == "intent:silence" -> "Je remarque que le silence compte comme une réponse."
            hint == "intent:pause_requested" -> "Je remarque qu'il faut baisser la présence."
            hint == "intent:resume_possible" -> "Je remarque qu'une reprise est possible."
            else -> null
        }
    }

    fun hintsFor(state: ContextState, memory: List<ClochetteMemoryEntry> = emptyList()): List<String> {
        val app = state.currentAppName.orEmpty().lowercase()
        val text = memory.joinToString(" ") {
            listOfNotNull(it.context, it.project, it.observedSignal, it.clochetteLine).joinToString(" ")
        }.lowercase()
        val hints = mutableListOf<String>()
        if (app.contains("clochette") || text.contains("clochette")) {
            hints += "Je remarque que Clochette revient souvent dans l'atelier."
        }
        if (app.contains("chatgpt") || text.contains("chatgpt")) {
            hints += "Je remarque que tu parles souvent avec ChatGPT."
        }
        if (app.contains("github") || text.contains("github") || app.contains("codex") || text.contains("codex")) {
            hints += "Je remarque que GitHub et Codex font partie du paysage ces temps-ci."
        }
        return hints.distinct()
    }
}
