package com.feuch.clochette

import android.content.Context

data class CharacterCastingConfig(
    val activeCharacterId: String = CharacterRegistry.DEFAULT_ACTIVE,
    val castingMode: CastingMode = CastingMode.LOCKED_CHARACTER,
    val guestsEnabled: Boolean = false,
    val allowNatasha: Boolean = true,
    val allowFeuch: Boolean = true,
    val guestFrequency: Int = 35,
    val acidity: Int = 45,
    val feuchChaos: Int = 45,
    val lockClochetteHost: Boolean = true,
    val letOctopusChoose: Boolean = true,
)

enum class CastingMode {
    LOCKED_CHARACTER,
    SUGGEST_CHANGES,
    OCCASIONAL_GUESTS,
    BLACKLACE_ALIVE,
}

object CharacterSettings {
    private const val PREFS = "clochette_character_casting"
    private const val KEY_ACTIVE = "active_character_id"
    private const val KEY_MODE = "mode"
    private const val KEY_GUESTS = "guests_enabled"
    private const val KEY_NATASHA = "allow_natasha"
    private const val KEY_FEUCH = "allow_feuch"
    private const val KEY_FREQUENCY = "guest_frequency"
    private const val KEY_ACIDITY = "acidity"
    private const val KEY_FEUCH_CHAOS = "feuch_chaos"
    private const val KEY_LOCK_HOST = "lock_clochette_host"
    private const val KEY_OCTOPUS_CHOOSE = "let_octopus_choose"

    fun read(context: Context): CharacterCastingConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return CharacterCastingConfig(
            activeCharacterId = CharacterRegistry.normalizeId(
                prefs.getString(KEY_ACTIVE, CharacterRegistry.DEFAULT_ACTIVE) ?: CharacterRegistry.DEFAULT_ACTIVE,
            ),
            castingMode = prefs.getString(KEY_MODE, CastingMode.LOCKED_CHARACTER.name)
                ?.let { runCatching { CastingMode.valueOf(it) }.getOrNull() }
                ?: CastingMode.LOCKED_CHARACTER,
            guestsEnabled = prefs.getBoolean(KEY_GUESTS, false),
            allowNatasha = prefs.getBoolean(KEY_NATASHA, true),
            allowFeuch = prefs.getBoolean(KEY_FEUCH, true),
            guestFrequency = prefs.getInt(KEY_FREQUENCY, 35).coerceIn(0, 100),
            acidity = prefs.getInt(KEY_ACIDITY, 45).coerceIn(0, 100),
            feuchChaos = prefs.getInt(KEY_FEUCH_CHAOS, 45).coerceIn(0, 100),
            lockClochetteHost = prefs.getBoolean(KEY_LOCK_HOST, true),
            letOctopusChoose = prefs.getBoolean(KEY_OCTOPUS_CHOOSE, true),
        )
    }

    fun save(context: Context, config: CharacterCastingConfig) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE, CharacterRegistry.normalizeId(config.activeCharacterId))
            .putString(KEY_MODE, config.castingMode.name)
            .putBoolean(KEY_GUESTS, config.guestsEnabled)
            .putBoolean(KEY_NATASHA, config.allowNatasha)
            .putBoolean(KEY_FEUCH, config.allowFeuch)
            .putInt(KEY_FREQUENCY, config.guestFrequency.coerceIn(0, 100))
            .putInt(KEY_ACIDITY, config.acidity.coerceIn(0, 100))
            .putInt(KEY_FEUCH_CHAOS, config.feuchChaos.coerceIn(0, 100))
            .putBoolean(KEY_LOCK_HOST, config.lockClochetteHost)
            .putBoolean(KEY_OCTOPUS_CHOOSE, config.letOctopusChoose)
            .apply()
    }
}
