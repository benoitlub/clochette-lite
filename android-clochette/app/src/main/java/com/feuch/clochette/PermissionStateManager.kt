package com.feuch.clochette

import android.content.Context

enum class ClochettePermissionKey(val id: String) {
    OVERLAY("overlay"),
    MICROPHONE("microphone"),
    NOTIFICATIONS("notifications"),
    ACCESSIBILITY("accessibility"),
    BATTERY_OPTIMIZATION("battery_optimization"),
    USAGE_ACCESS("usage_access"),
    NOTIFICATION_LISTENER("notification_listener"),
    SMS("sms"),
}

object PermissionStateManager {
    private const val PREFS = "clochette_permission_state"
    private fun key(permission: ClochettePermissionKey) = "asked_${permission.id}"

    fun markAsked(context: Context, permission: ClochettePermissionKey) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key(permission), true)
            .putLong("${key(permission)}_at", System.currentTimeMillis())
            .apply()
    }

    fun wasAsked(context: Context, permission: ClochettePermissionKey): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(key(permission), false)

    fun label(context: Context, permission: ClochettePermissionKey, granted: Boolean, requiredText: String): String =
        when {
            granted -> "autorisé"
            wasAsked(context, permission) -> "$requiredText · déjà demandé"
            else -> requiredText
        }
}
