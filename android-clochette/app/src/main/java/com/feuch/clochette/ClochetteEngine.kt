package com.feuch.clochette

import kotlin.math.min

object ClochetteEngine {
    fun remark(
        activity: ActivitySnapshot,
        sensors: SensorSnapshot,
        energy: String?,
        project: String?,
        memory: List<ClochetteMemoryEntry>,
        phraseLength: String = ClochetteVoiceSettings.LENGTH_NORMAL,
    ): String {
        val projectInfo = ProjectKnowledge.byName(project)
        val candidates = buildList {
            if (activity.recentSwitchCount >= 3) {
                add(
                "Trois bascules d'app. Le mammifere creatif cherche une sortie de secours."
                )
                add("Je remarque : ça saute d'app en app. Hypothèse : tu cherches une porte sans toucher la poignée.")
            }
            if (sensors.lowLight && sensors.walkingPossible) {
                add(
                "Telephone en foret. Decision structurelle interdite. Marche, note une idee, reviens avec une proie."
                )
                add("J'imagine dehors et presque sans lumière. Garde les grandes décisions pour une chaise et un verre d'eau.")
            }
            if (sensors.phoneStill && activity.foregroundPackage != null) {
                add(
                "Je remarque seulement ceci : téléphone immobile, app ouverte. Hypothèse : le premier geste attend son majordome."
                )
                add("Telephone immobile. Je suppose que le cerveau negocie. Fais une action trop petite pour etre noble.")
            }
            if (projectInfo?.name == "Pro.Hibited Online") {
                add(
                "Pro.Hibited n'est pas un site. C'est une table en poche. Lisibilite d'abord, tournevis interdit."
                )
                add("Je remarque le projet : cartes, table, doigts. Hypothèse : si mobile flanche, tout le jeu tousse.")
            }
            if (energy == "basse") {
                add(
                "Hypothèse : énergie basse. Je propose une action minuscule, presque humiliante, donc praticable."
                )
                add("Énergie basse déclarée. Pas d'épopée. Une seule prise, un seul geste, puis on avise.")
            }
            if (memory.any { it.result == "utile" }) {
                add(
                "Je suppose que la derniere morsure a aide. On recommence petit, sans discours."
                )
            }
            add(
                "Hypothèse : tu évites le début, pas la tâche. Je peux me tromper. Un peu."
            )
            add("J'imagine un seuil minuscule devant toi. Ne l'analyse pas. Enjambe-le mal, mais enjambe.")
            add("Je sais peu. Je soupconne beaucoup. Commence par la version brouillon, celle qui ferait rire ton futur toi.")
            add("Supposition locale : l'elan attend une permission officielle. Refuse-lui le guichet. Fais le premier geste.")
            add("Je peux me tromper : ce n'est pas le plan qui manque, c'est l'entree de service.")
            add("Hypothèse : tu veux la bonne forme avant la première trace. Mauvais contrat. Trace d'abord.")
        }
        val recentLines = memory.mapNotNull { it.clochetteLine }.takeLast(6).toSet()
        val freshCandidates = candidates.filterNot { it in recentLines }
        val source = freshCandidates.ifEmpty { candidates }
        val packageSeed = activity.foregroundPackage?.sumOf { it.code } ?: 0
        val historySeed = memory.fold(0) { seed, entry -> seed + (entry.timestamp % 97).toInt() }
        val line = source[(memory.size + activity.recentSwitchCount + packageSeed + historySeed) % source.size]
        val maxWords = when (phraseLength) {
            ClochetteVoiceSettings.LENGTH_SHORT -> 14
            ClochetteVoiceSettings.LENGTH_CHATTY -> 36
            else -> 25
        }
        return line.limitWords(maxWords)
    }

    private fun String.limitWords(maxWords: Int): String {
        val words = trim().split(Regex("\\s+"))
        return if (words.size <= maxWords) this else words.take(min(maxWords, words.size)).joinToString(" ")
    }
}
