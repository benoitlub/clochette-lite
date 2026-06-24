package com.feuch.clochette

import android.content.Context

object OverlayDebugSettings {
    private const val PREFS = "clochette_overlay_debug"
    private const val KEY_ENABLED = "debug_overlay_enabled"
    private const val KEY_EXPANDED = "debug_overlay_expanded"

    const val DEFAULT_ENABLED = false

    fun debugOverlayEnabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    fun isEnabled(context: Context): Boolean = debugOverlayEnabled(context)

    fun isExpanded(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_EXPANDED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun setExpanded(context: Context, expanded: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_EXPANDED, expanded)
            .apply()
    }
}
