package com.feuch.clochette

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings

data class NowPlayingSnapshot(
    val appName: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val playing: Boolean? = null,
)

object NowPlayingObserver {
    private const val PREFS = "clochette_now_playing"
    private const val KEY_APP = "app"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_PLAYING = "playing"

    fun snapshot(context: Context): NowPlayingSnapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return NowPlayingSnapshot(
            appName = prefs.getString(KEY_APP, null),
            title = prefs.getString(KEY_TITLE, null),
            artist = prefs.getString(KEY_ARTIST, null),
            playing = prefs.getString(KEY_PLAYING, null)?.toBooleanStrictOrNull(),
        )
    }

    fun save(context: Context, snapshot: NowPlayingSnapshot) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP, snapshot.appName)
            .putString(KEY_TITLE, snapshot.title)
            .putString(KEY_ARTIST, snapshot.artist)
            .putString(KEY_PLAYING, snapshot.playing?.toString())
            .apply()
    }

    fun hasPermission(context: Context): Boolean {
        val flat = ComponentName(context, ClochetteNotificationListenerService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return !enabled.isNullOrBlank() && enabled.split(':').any { it.equals(flat, ignoreCase = true) }
    }

    fun fromNotification(context: Context, packageName: String, notification: Notification): NowPlayingSnapshot? {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
        val artist = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.takeIf { it.isNotBlank() }
        if (title.isNullOrBlank() && artist.isNullOrBlank()) return null
        val appName = runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
        val isMedia = notification.category == Notification.CATEGORY_TRANSPORT ||
            notification.actions?.any { action ->
                val label = action.title?.toString().orEmpty().lowercase()
                label.contains("pause") || label.contains("play") || label.contains("lecture")
            } == true
        if (!isMedia) return null
        return NowPlayingSnapshot(
            appName = appName,
            title = title,
            artist = artist,
            playing = true,
        )
    }
}
