package com.feuch.clochette

import android.content.Context
import android.content.Intent
import android.util.Log

object ClochetteRemarkStore {
    const val ACTION_LINE_CHANGED = "com.feuch.clochette.ACTION_LINE_CHANGED"
    const val EXTRA_LINE = "line"
    const val EXTRA_SOURCE = "source"

    private const val PREFS = "clochette_remark_store"
    private const val KEY_LINE = "latest_line"
    private const val KEY_SOURCE = "latest_source"
    private const val DEFAULT_LINE = "Clochette attend sur l'écran d'accueil. C'est suspectement raisonnable."

    fun latest(context: Context): String = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_LINE, DEFAULT_LINE) ?: DEFAULT_LINE

    fun latestSource(context: Context): PhraseSource {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SOURCE, PhraseSource.UNKNOWN.id)
        return PhraseSource.entries.firstOrNull { it.id == raw } ?: PhraseSource.UNKNOWN
    }

    fun announce(context: Context, line: String, source: PhraseSource = PhraseSource.UNKNOWN) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LINE, line)
            .putString(KEY_SOURCE, source.id)
            .apply()

        Log.d("ClochettePhrase", "source=${source.id} line=${line.take(96)}")
        context.sendBroadcast(
            Intent(ACTION_LINE_CHANGED)
                .setPackage(context.packageName)
                .putExtra(EXTRA_LINE, line)
                .putExtra(EXTRA_SOURCE, source.id),
        )
    }
}
