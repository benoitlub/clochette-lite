package com.feuch.clochette

enum class PhraseSource(val id: String) {
    CLOCHETTE_ENGINE("clochette_engine"),
    CONTEXT_LINES("context_lines"),
    APP_CONTEXT_LINES("app_context_lines"),
    GUARDIAN_FALLBACK("guardian_fallback"),
    PROACTIVE_QUESTION("proactive_question"),
    AI_GATEWAY("ai_gateway"),
    MISTRAL("mistral"),
    GEMINI("gemini"),
    LOCAL_FALLBACK("local_fallback"),
    NOW_PLAYING("now_playing"),
    UNKNOWN("unknown"),
}
