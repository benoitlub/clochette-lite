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
        val line = when {
            activity.recentSwitchCount >= 3 ->
                "Trois bascules d'app. Le mammifere creatif cherche une sortie de secours."
            sensors.lowLight && sensors.walkingPossible ->
                "Telephone en foret. Decision structurelle interdite. Marche, note une idee, reviens avec une proie."
            sensors.phoneStill && activity.foregroundPackage != null ->
                "Je sais seulement ceci : telephone immobile, app ouverte. Hypothese : le premier geste attend son majordome."
            projectInfo?.name == "Pro.Hibited Online" ->
                "Pro.Hibited n'est pas un site. C'est une table en poche. Lisibilite d'abord, tournevis interdit."
            energy == "basse" ->
                "Hypothese : energie basse. Je propose une action minuscule, presque humiliante, donc praticable."
            memory.any { it.result == "utile" } ->
                "Je suppose que la derniere morsure a aide. On recommence petit, sans discours."
            else ->
                "Hypothese : tu evites le debut, pas la tache. Je peux me tromper. Un peu."
        }
        return line.limitWords(25)
    }

    private fun String.limitWords(maxWords: Int): String {
        val words = trim().split(Regex("\\s+"))
        return if (words.size <= maxWords) this else words.take(min(maxWords, words.size)).joinToString(" ")
    }
}
