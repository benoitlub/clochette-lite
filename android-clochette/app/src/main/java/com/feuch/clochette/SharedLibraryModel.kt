package com.feuch.clochette

data class SharedLibraryModel(
    val id: String = "shared_library_model",
    val version: Int = 1,
    val targetJsonFiles: List<String> = emptyList(),
    val rules: List<String> = emptyList(),
)
