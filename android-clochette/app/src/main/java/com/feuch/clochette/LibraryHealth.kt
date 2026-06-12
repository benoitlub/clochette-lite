package com.feuch.clochette

data class LibraryHealth(
    val sharedModelAvailable: Boolean,
    val librarianContractAvailable: Boolean,
    val acceptedLineCount: Int,
    val warnings: List<String> = emptyList(),
)
