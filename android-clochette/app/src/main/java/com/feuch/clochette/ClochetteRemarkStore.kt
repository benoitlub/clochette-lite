package com.feuch.clochette

import android.content.Context
import android.content.Intent

object ClochetteRemarkStore {
    const val ACTION_LINE_CHANGED = "com.feuch.clochette.ACTION_LINE_CHANGED"
    const val EXTRA_LINE = "line"

    private const val PREFS = "clochette_remark_store"
    private const val KEY_LINE = "latest_line"
    private const val DEFAULT_LINE = "Clochette attend sur l'écran d'accueil. C'est suspectement raisonnable."

    fun latest(context: Context): String = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_LINE, DEFAULT_LINE) ?: DEFAULT_LINE

    fun announce(context: Context, line: String) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LINE, line)
            .apply()

        context.sendBroadcast(
            Intent(ACTION_LINE_CHANGED)
                .setPackage(context.packageName)
                .putExtra(EXTRA_LINE, line),
        )
    }
}
