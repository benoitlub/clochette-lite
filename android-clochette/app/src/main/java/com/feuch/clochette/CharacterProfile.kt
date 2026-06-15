package com.feuch.clochette

import android.content.Context

data class CharacterProfile(
    val id: String,
    val displayName: String,
    val role: CharacterRole,
    val visualAssets: CharacterVisualAssets,
    val voiceProfileId: String? = null,
    val phraseBanks: List<String>,
    val toneTags: List<String>,
    val defaultIntensity: Int,
    val maxLineLength: Int,
    val canAppearProactively: Boolean,
    val cooldownMillis: Long,
    val priority: Int,
    val allowedInOverlay: Boolean,
    val allowedDuringFocus: Boolean,
    val allowedWhenLockedAvatar: Boolean,
)

data class CharacterVisualAssets(
    val idle: Int,
    val talking: Int,
    val listening: Int? = null,
    val closedEdge: Int? = null,
    val callDot: Int? = null,
)

enum class CharacterRole {
    HOST,
    GUEST,
    CHAOS,
    COMMENTATOR,
}

object CharacterRegistry {
    const val CLOCHETTE = "clochette"
    const val NATASHA = "natasha"
    const val FEUCH = "feuch"

    fun all(context: Context): List<CharacterProfile> {
        val fallback = CharacterVisualAssets(
            idle = R.drawable.clochette_overlay_model,
            talking = R.drawable.clochette_overlay_model,
            listening = R.drawable.clochette_blacklace_portrait,
            closedEdge = R.drawable.clochette_blacklace_portrait,
            callDot = R.drawable.clochette_blacklace_portrait,
        )
        return listOf(
            CharacterProfile(
                id = CLOCHETTE,
                displayName = "Clochette",
                role = CharacterRole.HOST,
                visualAssets = fallback,
                voiceProfileId = "clochette",
                phraseBanks = listOf("natural", "teasing", "soft", "focus", "micro_questions"),
                toneTags = listOf("soft", "teasing", "caring", "direct", "curious"),
                defaultIntensity = 55,
                maxLineLength = 25,
                canAppearProactively = true,
                cooldownMillis = 20_000L,
                priority = 100,
                allowedInOverlay = true,
                allowedDuringFocus = true,
                allowedWhenLockedAvatar = true,
            ),
            CharacterProfile(
                id = NATASHA,
                displayName = "Natasha",
                role = CharacterRole.COMMENTATOR,
                visualAssets = fallback,
                voiceProfileId = null,
                phraseBanks = listOf("teasing", "focus", "creative"),
                toneTags = listOf("sarcastic", "direct", "curious", "teasing"),
                defaultIntensity = 60,
                maxLineLength = 25,
                canAppearProactively = true,
                cooldownMillis = 12 * 60_000L,
                priority = 50,
                allowedInOverlay = true,
                allowedDuringFocus = false,
                allowedWhenLockedAvatar = false,
            ),
            CharacterProfile(
                id = FEUCH,
                displayName = "Feuch",
                role = CharacterRole.CHAOS,
                visualAssets = fallback,
                voiceProfileId = null,
                phraseBanks = listOf("badass", "focus", "teasing"),
                toneTags = listOf("chaotic", "direct", "playful"),
                defaultIntensity = 70,
                maxLineLength = 22,
                canAppearProactively = true,
                cooldownMillis = 10 * 60_000L,
                priority = 40,
                allowedInOverlay = true,
                allowedDuringFocus = false,
                allowedWhenLockedAvatar = false,
            ),
        )
    }

    fun get(context: Context, id: String): CharacterProfile =
        all(context).firstOrNull { it.id == id } ?: all(context).first()
}
