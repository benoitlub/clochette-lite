package com.feuch.clochette

data class MissingTagsReport(
    val missingContexts: List<String> = emptyList(),
    val missingTones: List<String> = emptyList(),
    val weakLines: List<String> = emptyList(),
)
