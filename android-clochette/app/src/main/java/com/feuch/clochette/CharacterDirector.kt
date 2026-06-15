package com.feuch.clochette

import android.content.Context
import kotlin.math.abs

data class CharacterIntervention(
    val character: CharacterProfile,
    val visualState: String,
    val phrase: String,
    val voiceProfileId: String?,
    val durationMillis: Long,
    val intensity: Int,
    val reason: String,
)

object CharacterDirector {
    private const val PREFS = "clochette_character_director"
    private const val KEY_LAST_CHARACTER = "last_character"
    private const val KEY_LAST_GLOBAL_AT = "last_global_at"
    private const val KEY_PREFIX = "last_at_"
    private const val GLOBAL_COOLDOWN_MS = 90_000L
    private const val MAX_PER_HOUR = 5
    private const val KEY_HOUR_START = "hour_start"
    private const val KEY_HOUR_COUNT = "hour_count"

    fun choose(
        context: Context,
        trigger: String,
        state: ContextState,
        baseLine: String,
        source: PhraseSource,
    ): CharacterIntervention {
        val appContext = context.applicationContext
        val config = CharacterSettings.read(appContext)
        val clochette = CharacterRegistry.get(appContext, CharacterRegistry.CLOCHETTE)
        if (!config.guestsEnabled || config.castingMode == CastingMode.CLOCHETTE_ONLY) {
            return host(clochette, baseLine, "clochette_only")
        }
        if (ProactiveSettings.read(appContext).mode == ProactiveMode.PAUSE) {
            return host(clochette, baseLine, "pause")
        }
        if (!config.letOctopusChoose) {
            return host(clochette, baseLine, "octopus_choice_disabled")
        }
        if (trigger == OctopusCore.TRIGGER_GATEWAY_TEST || trigger == OctopusCore.TRIGGER_SAFE_VOICE_TEST) {
            return host(clochette, baseLine, "technical_trigger")
        }
        if (!underGlobalLimits(appContext)) {
            return host(clochette, baseLine, "cooldown_global")
        }

        val candidates = mutableListOf<CharacterProfile>()
        if (config.allowNatasha) candidates += CharacterRegistry.get(appContext, CharacterRegistry.NATASHA)
        if (config.allowFeuch) candidates += CharacterRegistry.get(appContext, CharacterRegistry.FEUCH)
        val eligible = candidates.filter { it.allowedInOverlay && it.canAppearProactively && cooldownOk(appContext, it) }
        if (eligible.isEmpty()) return host(clochette, baseLine, "no_guest_eligible")

        val chance = guestChance(config, trigger, state, source)
        val seed = abs(
            trigger.sumOf { it.code } +
                state.recentAppSwitches * 7 +
                state.durationMinutes * 3 +
                System.currentTimeMillis().div(60_000L).toInt(),
        ) % 100
        if (seed >= chance) return host(clochette, baseLine, "guest_chance_$chance")

        val picked = pickGuest(eligible, config, state)
        val phrase = guestPhrase(picked.id, config, state, trigger).withVisibleFrenchAccents()
        recordGuest(appContext, picked.id)
        return CharacterIntervention(
            character = picked,
            visualState = "pop_in",
            phrase = phrase,
            voiceProfileId = picked.voiceProfileId,
            durationMillis = 8_000L,
            intensity = picked.defaultIntensity,
            reason = "guest_${picked.id}",
        )
    }

    fun latestCharacterId(context: Context): String =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_CHARACTER, CharacterRegistry.CLOCHETTE)
            ?: CharacterRegistry.CLOCHETTE

    private fun host(profile: CharacterProfile, line: String, reason: String): CharacterIntervention =
        CharacterIntervention(
            character = profile,
            visualState = "talking",
            phrase = line,
            voiceProfileId = profile.voiceProfileId,
            durationMillis = 25_000L,
            intensity = profile.defaultIntensity,
            reason = reason,
        )

    private fun guestChance(
        config: CharacterCastingConfig,
        trigger: String,
        state: ContextState,
        source: PhraseSource,
    ): Int {
        var chance = when (config.castingMode) {
            CastingMode.CLOCHETTE_ONLY -> 0
            CastingMode.OCCASIONAL_GUESTS -> config.guestFrequency / 3
            CastingMode.BLACKLACE_ALIVE -> config.guestFrequency / 2 + 15
            CastingMode.CONTROLLED_CHAOS -> config.guestFrequency + 10
        }
        if (trigger == OctopusCore.TRIGGER_PROACTIVE_TICK) chance += 8
        if (state.recentAppSwitches >= 4) chance += 16
        if (state.durationMinutes >= 25) chance += 10
        if (source == PhraseSource.GUARDIAN_FALLBACK) chance -= 20
        return chance.coerceIn(0, 75)
    }

    private fun pickGuest(
        eligible: List<CharacterProfile>,
        config: CharacterCastingConfig,
        state: ContextState,
    ): CharacterProfile {
        val feuch = eligible.firstOrNull { it.id == CharacterRegistry.FEUCH }
        val natasha = eligible.firstOrNull { it.id == CharacterRegistry.NATASHA }
        return when {
            config.feuchChaos >= 70 && feuch != null -> feuch
            state.recentAppSwitches >= 4 && natasha != null -> natasha
            state.durationMinutes >= 25 && natasha != null -> natasha
            feuch != null && config.castingMode == CastingMode.CONTROLLED_CHAOS -> feuch
            else -> eligible.maxBy { it.priority }
        }
    }

    private fun guestPhrase(
        characterId: String,
        config: CharacterCastingConfig,
        state: ContextState,
        trigger: String,
    ): String = when (characterId) {
        CharacterRegistry.NATASHA -> natashaLine(config, state)
        CharacterRegistry.FEUCH -> feuchLine(config, state, trigger)
        else -> "Je reprends la main. Rien de dramatique, juste une petite correction de trajectoire."
    }

    private fun natashaLine(config: CharacterCastingConfig, state: ContextState): String = when {
        state.recentAppSwitches >= 4 ->
            if (config.acidity >= 65) "Natasha : beaucoup d'allers-retours pour quelqu'un qui prétend chercher une sortie."
            else "Natasha : je vois les bascules. On cherche une réponse ou une échappatoire élégante ?"
        state.durationMinutes >= 25 ->
            "Natasha : cette appli a maintenant un bail symbolique. On renouvelle ou on sort ?"
        else ->
            "Natasha : observation simple. Tu tournes autour du geste utile avec une certaine mise en scène."
    }

    private fun feuchLine(config: CharacterCastingConfig, state: ContextState, trigger: String): String = when {
        config.feuchChaos >= 75 ->
            "Feuch : mini-action immédiate. Dix secondes. Pas de comité, pas de nappe blanche."
        state.recentAppSwitches >= 4 ->
            "Feuch : stop ping-pong. Choisis une case, tape dedans, on verra les étincelles après."
        trigger == OctopusCore.TRIGGER_PROACTIVE_TICK ->
            "Feuch : je passe en météore. Un geste minuscule maintenant, puis tu redeviens mystérieux."
        else ->
            "Feuch : pas besoin d'un plan. Il faut une étincelle et deux doigts sur le clavier."
    }

    private fun cooldownOk(context: Context, profile: CharacterProfile): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_PREFIX + profile.id, 0L)
        return System.currentTimeMillis() - last >= profile.cooldownMillis
    }

    private fun underGlobalLimits(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val start = prefs.getLong(KEY_HOUR_START, 0L)
        if (now - start > 60 * 60_000L) {
            prefs.edit().putLong(KEY_HOUR_START, now).putInt(KEY_HOUR_COUNT, 0).apply()
            return true
        }
        val count = prefs.getInt(KEY_HOUR_COUNT, 0)
        val lastGlobal = prefs.getLong(KEY_LAST_GLOBAL_AT, 0L)
        return count < MAX_PER_HOUR && now - lastGlobal >= GLOBAL_COOLDOWN_MS
    }

    private fun recordGuest(context: Context, characterId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val start = prefs.getLong(KEY_HOUR_START, now)
        val reset = now - start > 60 * 60_000L
        prefs.edit()
            .putString(KEY_LAST_CHARACTER, characterId)
            .putLong(KEY_LAST_GLOBAL_AT, now)
            .putLong(KEY_PREFIX + characterId, now)
            .putLong(KEY_HOUR_START, if (reset) now else start)
            .putInt(KEY_HOUR_COUNT, if (reset) 1 else prefs.getInt(KEY_HOUR_COUNT, 0) + 1)
            .apply()
    }
}
