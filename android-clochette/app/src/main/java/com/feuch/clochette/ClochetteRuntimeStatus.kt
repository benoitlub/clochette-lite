package com.feuch.clochette

import android.content.Context

data class ClochetteRuntimeSnapshot(
    val lastAction: String = "silencieux",
)

object ClochetteRuntimeStatus {
    private const val PREFS = "clochette_runtime_status"
    private const val KEY_LAST_ACTION = "last_action"

    fun read(context: Context): ClochetteRuntimeSnapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return ClochetteRuntimeSnapshot(
            lastAction = prefs.getString(KEY_LAST_ACTION, "silencieux") ?: "silencieux",
        )
    }

    fun recordAction(context: Context, action: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_ACTION, action)
            .apply()
    }
}
