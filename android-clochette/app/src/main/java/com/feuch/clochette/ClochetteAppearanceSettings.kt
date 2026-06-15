package com.feuch.clochette

import android.content.Context

data class ClosedAppearanceConfig(
    val mode: ClosedAppearanceMode = ClosedAppearanceMode.EDGE_PEEK,
    val side: ClosedAppearanceSide = ClosedAppearanceSide.AUTO,
    val x: Int = 14,
    val y: Int = 28,
)

enum class ClosedAppearanceMode {
    EDGE_PEEK,
    CALL_DOT,
}

enum class ClosedAppearanceSide {
    LEFT,
    RIGHT,
    AUTO,
}

object ClochetteAppearanceSettings {
    private const val PREFS = "clochette_appearance_settings"
    private const val KEY_MODE = "closed_mode"
    private const val KEY_SIDE = "closed_side"
    private const val KEY_X = "closed_x"
    private const val KEY_Y = "closed_y"

    fun read(context: Context): ClosedAppearanceConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ClosedAppearanceConfig(
            mode = prefs.getString(KEY_MODE, ClosedAppearanceMode.EDGE_PEEK.name)
                ?.let { runCatching { ClosedAppearanceMode.valueOf(it) }.getOrNull() }
                ?: ClosedAppearanceMode.EDGE_PEEK,
            side = prefs.getString(KEY_SIDE, ClosedAppearanceSide.AUTO.name)
                ?.let { runCatching { ClosedAppearanceSide.valueOf(it) }.getOrNull() }
                ?: ClosedAppearanceSide.AUTO,
            x = prefs.getInt(KEY_X, 14),
            y = prefs.getInt(KEY_Y, 28),
        )
    }

    fun save(context: Context, config: ClosedAppearanceConfig) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, config.mode.name)
            .putString(KEY_SIDE, config.side.name)
            .putInt(KEY_X, config.x)
            .putInt(KEY_Y, config.y)
            .apply()
    }

    fun savePosition(context: Context, x: Int, y: Int) {
        val current = read(context)
        save(context, current.copy(x = x.coerceAtLeast(0), y = y.coerceAtLeast(0)))
    }
}
