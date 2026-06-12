package com.feuch.clochette

data class PersonaTraits(
    val personaId: String,
    val publicName: String,
    val traitWeights: Map<String, Double> = emptyMap(),
    val contextWeights: Map<String, Double> = emptyMap(),
    val maxIntrusion: Double = 0.7,
    val minKindness: Double = 0.65,
    val maxAbsurdity: Double = 0.45,
    val maxWords: Int = 25,
    val preferredPhrasing: List<String> = emptyList(),
    val blockedPhrasing: List<String> = emptyList(),
)
