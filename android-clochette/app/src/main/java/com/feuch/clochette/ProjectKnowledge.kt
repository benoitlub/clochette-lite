package com.feuch.clochette

data class ProjectInfo(
    val name: String,
    val kind: String,
    val notes: List<String>,
    val risks: List<String>,
)

object ProjectKnowledge {
    val projects: List<ProjectInfo> = listOf(
        ProjectInfo(
            name = "Blacklace Island",
            kind = "univers transmedia web",
            notes = listOf("Ile narrative", "hub Natasha", "zones visitables", "ambiance ARG"),
            risks = listOf("dispersion", "nouvelle zone avant stabilisation", "cache GitHub Pages"),
        ),
        ProjectInfo(
            name = "Pro.Hibited Online",
            kind = "jeu de cartes web / table de jeu",
            notes = listOf(
                "Ce n'est pas juste un site : c'est une table de jeu numerique.",
                "Les regles existantes sont sensibles et ne doivent pas etre cassees.",
                "Cartes weed, hash, CBD, feuilles, filtres, contenants et feu.",
                "Mode Junky a conserver comme mecanique identifiee.",
                "La lisibilite mobile est un critere majeur."
            ),
            risks = listOf("toucher aux regles", "perdre la lisibilite mobile", "confondre habillage et gameplay"),
        ),
        ProjectInfo(
            name = "Creature-sync",
            kind = "traducteur animalier absurde",
            notes = listOf("detection", "journal", "partage", "dons", "ton pseudo-scientifique drole"),
            risks = listOf("traductions trop basses", "interface illisible", "cache"),
        ),
        ProjectInfo(
            name = "SpecTRL",
            kind = "traducteur spectral",
            notes = listOf("radar", "fragments", "SLS", "journal", "ambiance Feuch Institut"),
            risks = listOf("residus Creature-sync", "page blanche", "header trop lourd"),
        ),
        ProjectInfo(
            name = "Terra",
            kind = "livre publie / fable cosmique",
            notes = listOf("fable cosmique publiee", "lumiere verte", "Disclosure Day"),
            risks = listOf("reecriture infinie", "fuite dans le volume suivant"),
        ),
        ProjectInfo(
            name = "Neverland Ltd",
            kind = "projet narratif / entreprise-fiction",
            notes = listOf("territoire de marque", "fiction jouable", "ton etrange mais clair"),
            risks = listOf("surdecorer avant de rendre testable", "perdre la promesse"),
        ),
        ProjectInfo(
            name = "Prospection IA",
            kind = "prospection outillee",
            notes = listOf("messages utiles", "ciblage sobre", "preuve rapide"),
            risks = listOf("sonner corporate", "automatiser avant de comprendre"),
        ),
        ProjectInfo(
            name = "Ateliers IA",
            kind = "offre d'ateliers",
            notes = listOf("pedagogie concrete", "cas d'usage", "supports reutilisables"),
            risks = listOf("programme trop large", "promesse floue"),
        ),
    )

    fun byName(name: String?): ProjectInfo? = projects.firstOrNull { it.name == name }
}
