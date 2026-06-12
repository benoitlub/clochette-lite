package com.feuch.clochette

data class PersonaDescriptor(
    val id: String,
    val publicName: String,
    val role: String = "",
    val traitsPath: String? = null,
    val defaultMode: String = "discrete",
    val active: Boolean = false,
)
