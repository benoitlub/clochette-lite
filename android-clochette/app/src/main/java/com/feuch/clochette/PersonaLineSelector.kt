package com.feuch.clochette

class PersonaLineSelector {
    fun select(
        lines: List<AcceptedLine>,
        traits: PersonaTraits,
        state: ContextState,
    ): AcceptedLine? {
        return lines
            .filter { line ->
                traits.personaId !in line.blockedFor &&
                    line.intrusion <= traits.maxIntrusion &&
                    line.kindness >= traits.minKindness &&
                    line.absurdity <= traits.maxAbsurdity
            }
            .maxByOrNull { line -> score(line, traits, state) }
    }

    fun adapt(line: AcceptedLine, traits: PersonaTraits): String {
        val words = line.text.split(Regex("\\s+"))
        return if (words.size <= traits.maxWords) {
            line.text
        } else {
            words.take(traits.maxWords).joinToString(" ").trimEnd('.', '?', '!') + "."
        }
    }

    fun selectApproved(
        contextState: ContextState,
        lines: List<AcceptedLine>,
        traits: PersonaTraits,
        guardian: GuardianRulesLoader,
        recentLines: List<String>,
        debugTracer: DebugSourceTracer? = null,
        debug: Boolean = false,
    ): String? {
        val selected = select(lines, traits, contextState) ?: return null
        val candidate = adapt(selected, traits)
        val approved = guardian.approve(
            candidate = candidate,
            state = contextState,
            recentLines = recentLines,
        ).line
        return approved?.let {
            debugTracer?.prefixFor(DebugSource.PERSONA_TRAITS, debug).orEmpty() + it
        }
    }

    private fun score(line: AcceptedLine, traits: PersonaTraits, state: ContextState): Double {
        val toneScore = line.tones.sumOf { traits.traitWeights[it] ?: 0.5 }
        val contextScore = line.contexts.sumOf { traits.contextWeights[it] ?: 0.5 }
        val appBonus = state.currentAppName?.let { app ->
            if (line.contexts.any { app.contains(it, ignoreCase = true) }) 1.0 else 0.0
        } ?: 0.0
        return toneScore + contextScore + appBonus + line.kindness - line.intrusion - line.absurdity
    }
}
