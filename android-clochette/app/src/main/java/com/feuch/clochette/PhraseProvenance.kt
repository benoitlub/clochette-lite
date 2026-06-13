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
    LOCAL_NATURAL("local_natural"),
    LOCAL_FALLBACK("local_fallback"),
    LOCAL_PROACTIVE("local_proactive"),
    LOCAL_PROACTIVE_TEST("local_proactive_test"),
    OCTOPUS_SAFE_TEST("octopus_safe_test"),
    NOW_PLAYING("now_playing"),
    UNKNOWN("unknown"),
}
