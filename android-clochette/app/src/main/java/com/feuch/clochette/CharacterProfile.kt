package com.feuch.clochette

import android.content.Context
import android.util.Log
import org.json.JSONObject

data class CharacterProfile(
    val id: String,
    val displayName: String,
    val shortDescription: String,
    val roleLabel: String,
    val role: CharacterRole,
    val visualAssets: CharacterVisualAssets,
    val phraseBankId: String,
    val voiceProfileId: String? = null,
    val phraseBanks: List<String>,
    val defaultPersonality: CharacterDefaultPersonality,
    val toneTags: List<String>,
    val defaultIntensity: Int,
    val maxLineLength: Int,
    val canAppearProactively: Boolean,
    val cooldownMillis: Long,
    val priority: Int,
    val allowedInOverlay: Boolean,
    val allowedDuringFocus: Boolean,
    val allowedWhenLockedAvatar: Boolean,
    val enabled: Boolean = true,
    val userSelectable: Boolean = true,
)

data class CharacterDefaultPersonality(
    val bavardage: Int,
    val initiative: Int,
    val taquinerie: Int,
    val douceur: Int,
    val longueur: Int,
    val curiosite: Int,
)

data class CharacterVisualAssets(
    val idle: Int,
    val talking: Int,
    val listening: Int? = null,
    val closedEdge: Int? = null,
    val callDot: Int? = null,
    val thumbnail: Int = idle,
)

enum class CharacterRole {
    HOST,
    GUEST,
    CHAOS,
    COMMENTATOR,
}

object CharacterRegistry {
    const val FEE_BRUNE = "fee_brune"
    const val SOFIA = "sofia"
    const val BIRDY = "birdy"
    const val AUDREY = "audrey"
    const val FEUNETTE_VERTE = "feunette_verte"
    const val FEE_BELETTE = "fee_belette"
    const val BRUMEUX = "brumeux"
    const val FEUCH = "feuch"
    const val NATASHA = "natasha"
    const val LEGACY_CLOCHETTE = "clochette"
    const val DEFAULT_ACTIVE = FEE_BRUNE

    fun all(context: Context): List<CharacterProfile> {
        val fallback = CharacterVisualAssets(
            idle = R.drawable.clochette_overlay_model,
            talking = R.drawable.clochette_overlay_model,
            listening = R.drawable.clochette_blacklace_portrait,
            closedEdge = R.drawable.clochette_blacklace_portrait,
            callDot = R.drawable.clochette_blacklace_portrait,
            thumbnail = R.drawable.clochette_blacklace_portrait,
        )
        val feeBruneVisuals = CharacterVisualAssets(
            idle = R.drawable.character_fee_brune_idle,
            talking = R.drawable.character_fee_brune_idle,
            listening = R.drawable.character_fee_brune_idle,
            closedEdge = R.drawable.character_fee_brune_idle,
            callDot = R.drawable.character_fee_brune_idle,
            thumbnail = R.drawable.character_fee_brune_idle,
        )
        val sofiaVisuals = CharacterVisualAssets(
            idle = R.drawable.character_sofia_idle,
            talking = R.drawable.character_sofia_idle,
            listening = R.drawable.character_sofia_idle,
            closedEdge = R.drawable.character_sofia_idle,
            callDot = R.drawable.character_sofia_idle,
            thumbnail = R.drawable.character_sofia_idle,
        )
        val birdyVisuals = CharacterVisualAssets(
            idle = R.drawable.character_birdy_idle,
            talking = R.drawable.character_birdy_idle,
            listening = R.drawable.character_birdy_idle,
            closedEdge = R.drawable.character_birdy_idle,
            callDot = R.drawable.character_birdy_idle,
            thumbnail = R.drawable.character_birdy_idle,
        )
        val audreyVisuals = CharacterVisualAssets(
            idle = R.drawable.character_audrey_idle,
            talking = R.drawable.character_audrey_idle,
            listening = R.drawable.character_audrey_idle,
            closedEdge = R.drawable.character_audrey_idle,
            callDot = R.drawable.character_audrey_idle,
            thumbnail = R.drawable.character_audrey_idle,
        )
        val feunetteVerteVisuals = CharacterVisualAssets(
            idle = R.drawable.character_feunette_verte_idle,
            talking = R.drawable.character_feunette_verte_idle,
            listening = R.drawable.character_feunette_verte_idle,
            closedEdge = R.drawable.character_feunette_verte_idle,
            callDot = R.drawable.character_feunette_verte_idle,
            thumbnail = R.drawable.character_feunette_verte_idle,
        )
        val feeBeletteVisuals = CharacterVisualAssets(
            idle = R.drawable.character_fee_belette_idle,
            talking = R.drawable.character_fee_belette_idle,
            listening = R.drawable.character_fee_belette_idle,
            closedEdge = R.drawable.character_fee_belette_idle,
            callDot = R.drawable.character_fee_belette_idle,
            thumbnail = R.drawable.character_fee_belette_idle,
        )
        val brumeuxVisuals = CharacterVisualAssets(
            idle = R.drawable.character_brumeux_idle,
            talking = R.drawable.character_brumeux_idle,
            listening = R.drawable.character_brumeux_idle,
            closedEdge = R.drawable.character_brumeux_idle,
            callDot = R.drawable.character_brumeux_idle,
            thumbnail = R.drawable.character_brumeux_idle,
        )
        val feuchVisuals = CharacterVisualAssets(
            idle = R.drawable.character_feuch_idle,
            talking = R.drawable.character_feuch_idle,
            listening = R.drawable.character_feuch_idle,
            closedEdge = R.drawable.character_feuch_idle,
            callDot = R.drawable.character_feuch_idle,
            thumbnail = R.drawable.character_feuch_idle,
        )
        val natashaVisuals = CharacterVisualAssets(
            idle = R.drawable.character_natasha_idle,
            talking = R.drawable.character_natasha_idle,
            listening = R.drawable.character_natasha_idle,
            closedEdge = R.drawable.character_natasha_idle,
            callDot = R.drawable.character_natasha_idle,
            thumbnail = R.drawable.character_natasha_idle,
        )
        fun profile(
            id: String,
            name: String,
            description: String,
            roleLabel: String,
            role: CharacterRole,
            banks: List<String>,
            tones: List<String>,
            personality: CharacterDefaultPersonality,
            priority: Int,
            cooldown: Long = 8 * 60_000L,
            visuals: CharacterVisualAssets = fallback,
        ) = CharacterProfile(
            id = id,
            displayName = name,
            shortDescription = description,
            roleLabel = roleLabel,
            role = role,
            visualAssets = visuals,
            phraseBankId = "phrases_$id",
            voiceProfileId = id,
            phraseBanks = banks,
            defaultPersonality = personality,
            toneTags = tones,
            defaultIntensity = personality.taquinerie.coerceAtLeast(personality.initiative),
            maxLineLength = if (personality.longueur >= 65) 30 else 25,
            canAppearProactively = true,
            cooldownMillis = cooldown,
            priority = priority,
            allowedInOverlay = true,
            allowedDuringFocus = role == CharacterRole.HOST,
            allowedWhenLockedAvatar = role == CharacterRole.HOST,
        )
        return listOf(
            profile(
                FEE_BRUNE,
                "Fée brune",
                "Présence principale, espiègle et protectrice.",
                "hôte",
                CharacterRole.HOST,
                listOf("natural", "teasing", "soft", "focus", "micro_questions"),
                listOf("soft", "teasing", "caring", "direct", "curious"),
                CharacterDefaultPersonality(55, 50, 55, 60, 50, 55),
                100,
                20_000L,
                feeBruneVisuals,
            ),
            profile(
                SOFIA,
                "Sofia",
                "Douce, claire, encourageante.",
                "alliée calme",
                CharacterRole.GUEST,
                listOf("soft", "natural", "encouragement"),
                listOf("soft", "caring", "direct"),
                CharacterDefaultPersonality(35, 35, 20, 85, 55, 35),
                70,
                visuals = sofiaVisuals,
            ),
            profile(
                BIRDY,
                "Birdy",
                "Observation légère, curieuse, mobile.",
                "observatrice",
                CharacterRole.GUEST,
                listOf("creative", "natural", "micro_questions"),
                listOf("curious", "soft", "mysterious"),
                CharacterDefaultPersonality(45, 55, 35, 55, 45, 80),
                62,
                visuals = birdyVisuals,
            ),
            profile(
                AUDREY,
                "Audrey",
                "Précise, directe, orientée action.",
                "stratège",
                CharacterRole.GUEST,
                listOf("focus", "natural"),
                listOf("direct", "caring"),
                CharacterDefaultPersonality(40, 60, 35, 45, 35, 45),
                65,
                visuals = audreyVisuals,
            ),
            profile(
                FEUNETTE_VERTE,
                "Feunette verte",
                "Élan frais, piquant doux, relance courte.",
                "relance",
                CharacterRole.GUEST,
                listOf("teasing", "focus", "creative"),
                listOf("teasing", "soft", "curious"),
                CharacterDefaultPersonality(55, 65, 60, 55, 35, 60),
                60,
                visuals = feunetteVerteVisuals,
            ),
            profile(
                FEE_BELETTE,
                "Fée Belette",
                "Discrète, vive, un peu malicieuse.",
                "furtive",
                CharacterRole.GUEST,
                listOf("teasing", "soft"),
                listOf("teasing", "soft", "absurd"),
                CharacterDefaultPersonality(35, 45, 65, 55, 35, 55),
                55,
                visuals = feeBeletteVisuals,
            ),
            profile(
                BRUMEUX,
                "Brumeux",
                "Mystérieux, lent, utile quand le mental sature.",
                "brume",
                CharacterRole.GUEST,
                listOf("fatigue", "soft", "natural"),
                listOf("mysterious", "soft", "caring"),
                CharacterDefaultPersonality(25, 25, 20, 80, 70, 40),
                45,
                visuals = brumeuxVisuals,
            ),
            profile(
                FEUCH,
                "Feuch",
                "Chaotique, énergique, absurde, orienté mini-action.",
                "chaos contrôlé",
                CharacterRole.CHAOS,
                listOf("badass", "focus", "teasing"),
                listOf("chaotic", "direct", "absurd"),
                CharacterDefaultPersonality(70, 85, 70, 20, 35, 45),
                50,
                10 * 60_000L,
                feuchVisuals,
            ),
            profile(
                NATASHA,
                "Natasha",
                "Acerbe, lucide, journaliste Blacklace.",
                "commentatrice",
                CharacterRole.COMMENTATOR,
                listOf("teasing", "focus", "creative"),
                listOf("sarcastic", "direct", "curious", "teasing"),
                CharacterDefaultPersonality(55, 60, 80, 30, 45, 70),
                58,
                12 * 60_000L,
                natashaVisuals,
            ),
        ).also { CharacterAssetValidator.validate(context, it) }
    }

    fun get(context: Context, id: String): CharacterProfile =
        all(context).firstOrNull { it.id == normalizeId(id) }
            ?: all(context).first()

    fun selectable(context: Context): List<CharacterProfile> =
        all(context).filter { it.enabled && it.userSelectable }

    fun normalizeId(id: String): String =
        if (id == LEGACY_CLOCHETTE) FEE_BRUNE else id
}

object CharacterAssetValidator {
    private const val TAG = "ClochetteCharacters"

    fun validate(context: Context, profiles: List<CharacterProfile>) {
        profiles.forEach { profile ->
            runCatching {
                val manifestPath = "characters/${profile.id}/manifest.json"
                val manifest = context.assets.open(manifestPath)
                    .bufferedReader(Charsets.UTF_8)
                    .use { JSONObject(it.readText()) }
                val manifestId = manifest.optString("id")
                if (manifestId != profile.id) {
                    Log.e(TAG, "Character asset mismatch: expected ${profile.id} but got $manifestId")
                }
                listOf("idle", "thumbnail").forEach { field ->
                    val fileName = manifest.optString(field)
                    if (fileName.isBlank()) {
                        Log.e(TAG, "Character asset missing field: characterId=${profile.id} field=$field")
                    } else {
                        runCatching { context.assets.open("characters/${profile.id}/$fileName").close() }
                            .onFailure {
                                Log.e(TAG, "Character asset missing file: characterId=${profile.id} field=$field file=$fileName")
                            }
                    }
                }
                listOf("idle", "talking", "thumbnail", "closed_edge", "call_dot").forEach { field ->
                    val fileName = manifest.optString(field)
                    if (fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true)) {
                        Log.e(TAG, "Invalid avatar asset: non-alpha format for characterId=${profile.id} file=$fileName")
                    }
                }
            }.onFailure {
                Log.e(TAG, "Character asset manifest error: characterId=${profile.id}", it)
            }
        }
    }
}
