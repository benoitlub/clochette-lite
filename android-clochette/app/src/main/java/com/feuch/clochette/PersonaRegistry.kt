package com.feuch.clochette

data class PersonaRegistry(
    val personas: List<PersonaDescriptor>,
    val defaultPersonaId: String = "clochette",
)
