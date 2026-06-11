package com.feuch.clochette

import kotlin.math.min

object ClochetteEngine {
    fun remark(
        activity: ActivitySnapshot,
        sensors: SensorSnapshot,
        energy: String?,
        project: String?,
        memory: List<ClochetteMemoryEntry>,
    ): String {
        val projectInfo = ProjectKnowledge.byName(project)
        val candidates = buildList {
            if (activity.recentSwitchCount >= 3) {
                add(
                "Trois bascules d'app. Le mammifere creatif cherche une sortie de secours."
                )
                add("Je sais : ca saute d'app en app. Hypothese : tu cherches une porte sans toucher la poignee.")
            }
            if (sensors.lowLight && sensors.walkingPossible) {
                add(
                "Telephone en foret. Decision structurelle interdite. Marche, note une idee, reviens avec une proie."
                )
                add("J'imagine dehors et presque sans lumiere. Garde les grandes decisions pour une chaise et un verre d'eau.")
            }
            if (sensors.phoneStill && activity.foregroundPackage != null) {
                add(
                "Je sais seulement ceci : telephone immobile, app ouverte. Hypothese : le premier geste attend son majordome."
                )
                add("Telephone immobile. Je suppose que le cerveau negocie. Fais une action trop petite pour etre noble.")
            }
            if (projectInfo?.name == "Pro.Hibited Online") {
                add(
                "Pro.Hibited n'est pas un site. C'est une table en poche. Lisibilite d'abord, tournevis interdit."
                )
                add("Je sais le projet : cartes, table, doigts. Hypothese : si mobile flanche, tout le jeu tousse.")
            }
            if (energy == "basse") {
                add(
                "Hypothese : energie basse. Je propose une action minuscule, presque humiliante, donc praticable."
                )
                add("Energie basse declaree. Pas d'epopee. Une seule prise, un seul geste, puis on avise.")
            }
            if (memory.any { it.result == "utile" }) {
                add(
                "Je suppose que la derniere morsure a aide. On recommence petit, sans discours."
                )
            }
            add(
                "Hypothese : tu evites le debut, pas la tache. Je peux me tromper. Un peu."
            )
            add("J'imagine un seuil minuscule devant toi. Ne l'analyse pas. Enjambe-le mal, mais enjambe.")
            add("Je sais peu. Je soupconne beaucoup. Commence par la version brouillon, celle qui ferait rire ton futur toi.")
            add("Supposition locale : l'elan attend une permission officielle. Refuse-lui le guichet. Fais le premier geste.")
            add("Je peux me tromper : ce n'est pas le plan qui manque, c'est l'entree de service.")
            add("Hypothese : tu veux la bonne forme avant la premiere trace. Mauvais contrat. Trace d'abord.")
        }
        val recentLines = memory.mapNotNull { it.clochetteLine }.take(6).toSet()
        val freshCandidates = candidates.filterNot { it in recentLines }
        val source = freshCandidates.ifEmpty { candidates }
        val packageSeed = activity.foregroundPackage?.sumOf { it.code } ?: 0
        val line = source[(memory.size + activity.recentSwitchCount + packageSeed) % source.size]
        return line.limitWords(25)
    }

    private fun String.limitWords(maxWords: Int): String {
        val words = trim().split(Regex("\\s+"))
        return if (words.size <= maxWords) this else words.take(min(maxWords, words.size)).joinToString(" ")
    }
}
