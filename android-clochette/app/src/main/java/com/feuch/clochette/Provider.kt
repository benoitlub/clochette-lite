package com.feuch.clochette

enum class Provider(val id: String) {
    MISTRAL("mistral"),
    GEMINI("gemini"),
    OPENAI("openai"),
    OLLAMA("ollama"),
    LOCAL("local"),
}
