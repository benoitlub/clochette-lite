package com.feuch.clochette

import kotlin.math.abs

object ProactiveQuestionEngine {
    fun question(
        state: ContextState,
        journal: List<ObservationJournalEntry> = emptyList(),
    ): String {
        val app = state.currentAppName.orEmpty()
        val candidates = buildList {
            if (app.contains("ChatGPT", ignoreCase = true)) {
                add("Je peux me tromper, mais tu réfléchis ou tu négocies avec la procrastination ?")
                add("J'ai l'impression que ChatGPT sert de brouillon. Tu veux sortir une vraie prise ?")
            }
            if (app.contains("Codex", ignoreCase = true)) {
                add("Je soupçonne un duel avec Codex. Tu veux reprendre la main ou le laisser divaguer ?")
                add("Je peux me tromper, mais tu veux tester avant de re-prompter encore ?")
            }
            if (app.contains("GitHub", ignoreCase = true)) {
                add("Je remarque GitHub. Tu attends un build ou tu surveilles le destin ?")
            }
            if (state.durationMinutes >= 90) {
                add("Je me demande si tu avances, ou si tu gardes l'écran chaud par loyauté.")
            }
            if (state.batteryPercent != null && state.batteryPercent <= 20 && !state.isCharging) {
                add("Hypothèse : la batterie veut un chargeur avant ton prochain grand drame.")
            }
            if (state.dayPeriod == DayPeriod.NIGHT) {
                add("Je peux me tromper, mais tu veux finir ça ou esquiver le sommeil ?")
            }
            if (state.recentAppSwitches >= 5) {
                add("Je remarque des bascules. Tu cherches une réponse ou une porte de sortie ?")
            }
            if (state.movementState == MovementState.STILL) {
                add("Je me demande si tu lis, ou si tu médites sur un bug.")
            }
            add("Je peux me tromper, mais tu as l'air ailleurs. On reprend par quoi ?")
            add("Hypothèse : tu es revenu. Les humains ont cette habitude.")
            add("Je soupçonne une petite fuite élégante. Tu veux reprendre ou faire semblant encore un peu ?")
        }.map { it.trimToMaxWords(25) }

        val seed = app.sumOf { it.code } + state.durationMinutes + state.recentAppSwitches + journal.size
        return candidates[abs(seed) % candidates.size]
    }

    private fun String.trimToMaxWords(maxWords: Int): String {
        val words = split(Regex("\\s+"))
        return if (words.size <= maxWords) this else words.take(maxWords).joinToString(" ").trimEnd('.', '?', '!') + " ?"
    }
}
