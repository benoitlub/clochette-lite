package com.feuch.clochette

import android.content.Context

data class ClochetteOverlayConfig(
    val size: String = ClochetteOverlaySettings.SIZE_NORMAL,
    val position: String = ClochetteOverlaySettings.POSITION_BOTTOM_END,
    val presenceStyle: String = ClochetteOverlaySettings.PRESENCE_NORMAL,
    val bubbleAlpha: Float = 0.86f,
)

object ClochetteOverlaySettings {
    const val SIZE_SMALL = "Petite"
    const val SIZE_NORMAL = "Normale"
    const val SIZE_LARGE = "Grande"

    const val POSITION_BOTTOM_END = "Bas droite"
    const val POSITION_BOTTOM_START = "Bas gauche"

    const val PRESENCE_DISCRETE = "Discrete"
    const val PRESENCE_NORMAL = "Normale"
    const val PRESENCE_INTRUSIVE = "Intrusive"

    private const val PREFS = "clochette_overlay_settings"
    private const val KEY_SIZE = "size"
    private const val KEY_POSITION = "position"
    private const val KEY_PRESENCE = "presence_style"
    private const val KEY_BUBBLE_ALPHA = "bubble_alpha"

    fun read(context: Context): ClochetteOverlayConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ClochetteOverlayConfig(
            size = prefs.getString(KEY_SIZE, SIZE_NORMAL) ?: SIZE_NORMAL,
            position = prefs.getString(KEY_POSITION, POSITION_BOTTOM_END) ?: POSITION_BOTTOM_END,
            presenceStyle = prefs.getString(KEY_PRESENCE, PRESENCE_NORMAL) ?: PRESENCE_NORMAL,
            bubbleAlpha = prefs.getFloat(KEY_BUBBLE_ALPHA, 0.86f).coerceIn(0.35f, 1f),
        )
    }

    fun save(context: Context, config: ClochetteOverlayConfig) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SIZE, config.size)
            .putString(KEY_POSITION, config.position)
            .putString(KEY_PRESENCE, config.presenceStyle)
            .putFloat(KEY_BUBBLE_ALPHA, config.bubbleAlpha.coerceIn(0.35f, 1f))
            .apply()
    }

    fun spriteWidthDp(config: ClochetteOverlayConfig): Int = when (config.size) {
        SIZE_SMALL -> 68
        SIZE_LARGE -> 112
        else -> 88
    }

    fun spriteHeightDp(config: ClochetteOverlayConfig): Int = when (config.size) {
        SIZE_SMALL -> 120
        SIZE_LARGE -> 194
        else -> 154
    }

    fun edgeOffsetDp(config: ClochetteOverlayConfig): Int = when (config.presenceStyle) {
        PRESENCE_DISCRETE -> 18
        PRESENCE_INTRUSIVE -> 0
        else -> 8
    }

    fun bottomOffsetDp(config: ClochetteOverlayConfig): Int = when (config.presenceStyle) {
        PRESENCE_DISCRETE -> 36
        PRESENCE_INTRUSIVE -> 8
        else -> 20
    }

    fun bubbleTextSizeSp(config: ClochetteOverlayConfig): Float = when (config.presenceStyle) {
        PRESENCE_DISCRETE -> 12f
        PRESENCE_INTRUSIVE -> 14f
        else -> 13f
    }

    fun bubbleMaxLines(config: ClochetteOverlayConfig): Int = when (config.presenceStyle) {
        PRESENCE_DISCRETE -> 2
        PRESENCE_INTRUSIVE -> 4
        else -> 3
    }

    fun bubbleMaxWidthDp(config: ClochetteOverlayConfig): Int = when (config.presenceStyle) {
        PRESENCE_DISCRETE -> 166
        PRESENCE_INTRUSIVE -> 224
        else -> 196
    }

    fun spriteAlpha(config: ClochetteOverlayConfig): Float = when (config.presenceStyle) {
        PRESENCE_DISCRETE -> 0.72f
        else -> 1f
    }
}
