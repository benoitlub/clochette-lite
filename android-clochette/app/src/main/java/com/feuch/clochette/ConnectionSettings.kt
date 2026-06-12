package com.feuch.clochette

import android.content.Context

data class ConnectionConfig(
    val observationMode: String = ConnectionSettings.MODE_OFF,
    val chatgptEnabled: Boolean = true,
    val chromeEnabled: Boolean = true,
    val youtubeEnabled: Boolean = true,
    val githubEnabled: Boolean = true,
    val reminderDelayMinutes: Int = 120,
    val spontaneousRemarks: Boolean = false,
)

object ConnectionSettings {
    const val MODE_OFF = "Désactivée"
    const val MODE_SOFT = "Douce"
    const val MODE_NORMAL = "Normale"
    const val MODE_TEASING = "Taquine"

    private const val PREFS = "clochette_connection_settings"
    private const val KEY_MODE = "observation_mode"
    private const val KEY_CHATGPT = "chatgpt_enabled"
    private const val KEY_CHROME = "chrome_enabled"
    private const val KEY_YOUTUBE = "youtube_enabled"
    private const val KEY_GITHUB = "github_enabled"
    private const val KEY_DELAY = "reminder_delay_minutes"
    private const val KEY_SPONTANEOUS = "spontaneous_remarks"

    fun read(context: Context): ConnectionConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ConnectionConfig(
            observationMode = prefs.getString(KEY_MODE, MODE_OFF) ?: MODE_OFF,
            chatgptEnabled = prefs.getBoolean(KEY_CHATGPT, true),
            chromeEnabled = prefs.getBoolean(KEY_CHROME, true),
            youtubeEnabled = prefs.getBoolean(KEY_YOUTUBE, true),
            githubEnabled = prefs.getBoolean(KEY_GITHUB, true),
            reminderDelayMinutes = prefs.getInt(KEY_DELAY, 120).coerceIn(30, 180),
            spontaneousRemarks = prefs.getBoolean(KEY_SPONTANEOUS, false),
        )
    }

    fun save(context: Context, config: ConnectionConfig) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, config.observationMode)
            .putBoolean(KEY_CHATGPT, config.chatgptEnabled)
            .putBoolean(KEY_CHROME, config.chromeEnabled)
            .putBoolean(KEY_YOUTUBE, config.youtubeEnabled)
            .putBoolean(KEY_GITHUB, config.githubEnabled)
            .putInt(KEY_DELAY, config.reminderDelayMinutes.coerceIn(30, 180))
            .putBoolean(KEY_SPONTANEOUS, config.spontaneousRemarks)
            .apply()
    }

    fun delayLabel(minutes: Int): String = when (minutes) {
        30 -> "30 min"
        60 -> "1 h"
        120 -> "2 h"
        180 -> "3 h"
        else -> "$minutes min"
    }

    fun privacyNote(): String =
        "Clochette ne lit jamais le contenu des applications. Elle observe uniquement le nom des apps et leur durée d'utilisation locale."
}
