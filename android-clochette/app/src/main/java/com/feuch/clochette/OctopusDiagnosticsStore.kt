package com.feuch.clochette

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

data class OctopusDiagnostics(
    val lastTrigger: String = "jamais",
    val lastOriginalLine: String = "",
    val lastFinalLine: String = "",
    val lastPhraseSource: String = PhraseSource.UNKNOWN.id,
    val lastPhraseBankId: String = "",
    val lastPhraseEntryId: String = "",
    val lastPhraseTone: String = "",
    val lastProviderUsed: String = "aucun",
    val lastGuardianReason: String = "jamais",
    val lastShouldSpeak: Boolean = false,
    val lastVoiceStatus: String = "silencieux",
    val lastOverlayState: String = "unknown",
    val lastMicStatus: String = "fermé",
    val lastTranscription: String = "",
    val lastGatewayStatus: String = "non configuré",
    val lastAppearance: String = "",
    val lastError: String = "",
    val updatedAt: Long = 0L,
) {
    fun asText(): String = listOf(
        "trigger=$lastTrigger",
        "source=$lastPhraseSource",
        "bank=$lastPhraseBankId",
        "entry=$lastPhraseEntryId",
        "tone=$lastPhraseTone",
        "provider=$lastProviderUsed",
        "guardian=$lastGuardianReason",
        "shouldSpeak=$lastShouldSpeak",
        "voice=$lastVoiceStatus",
        "overlay=$lastOverlayState",
        "micro=$lastMicStatus",
        "gateway=$lastGatewayStatus",
        "appearance=$lastAppearance",
        "transcription=$lastTranscription",
        "original=$lastOriginalLine",
        "final=$lastFinalLine",
        "error=$lastError",
        "updatedAt=$updatedAt",
    ).joinToString("\n")
}

object OctopusDiagnosticsStore {
    private const val PREFS = "clochette_octopus_diagnostics"
    private const val KEY_TRIGGER = "trigger"
    private const val KEY_ORIGINAL = "original"
    private const val KEY_FINAL = "final"
    private const val KEY_SOURCE = "source"
    private const val KEY_BANK = "bank"
    private const val KEY_ENTRY = "entry"
    private const val KEY_TONE = "tone"
    private const val KEY_PROVIDER = "provider"
    private const val KEY_GUARDIAN = "guardian"
    private const val KEY_SHOULD_SPEAK = "should_speak"
    private const val KEY_VOICE = "voice"
    private const val KEY_OVERLAY = "overlay"
    private const val KEY_MIC = "mic"
    private const val KEY_TRANSCRIPTION = "transcription"
    private const val KEY_GATEWAY = "gateway"
    private const val KEY_APPEARANCE = "appearance"
    private const val KEY_ERROR = "error"
    private const val KEY_UPDATED = "updated"

    fun read(context: Context): OctopusDiagnostics {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return OctopusDiagnostics(
            lastTrigger = prefs.getString(KEY_TRIGGER, "jamais") ?: "jamais",
            lastOriginalLine = prefs.getString(KEY_ORIGINAL, "").orEmpty(),
            lastFinalLine = prefs.getString(KEY_FINAL, "").orEmpty(),
            lastPhraseSource = prefs.getString(KEY_SOURCE, PhraseSource.UNKNOWN.id) ?: PhraseSource.UNKNOWN.id,
            lastPhraseBankId = prefs.getString(KEY_BANK, "").orEmpty(),
            lastPhraseEntryId = prefs.getString(KEY_ENTRY, "").orEmpty(),
            lastPhraseTone = prefs.getString(KEY_TONE, "").orEmpty(),
            lastProviderUsed = prefs.getString(KEY_PROVIDER, "aucun") ?: "aucun",
            lastGuardianReason = prefs.getString(KEY_GUARDIAN, "jamais") ?: "jamais",
            lastShouldSpeak = prefs.getBoolean(KEY_SHOULD_SPEAK, false),
            lastVoiceStatus = prefs.getString(KEY_VOICE, "silencieux") ?: "silencieux",
            lastOverlayState = prefs.getString(KEY_OVERLAY, "unknown") ?: "unknown",
            lastMicStatus = prefs.getString(KEY_MIC, "fermé") ?: "fermé",
            lastTranscription = prefs.getString(KEY_TRANSCRIPTION, "").orEmpty(),
            lastGatewayStatus = prefs.getString(KEY_GATEWAY, "non configuré") ?: "non configuré",
            lastAppearance = prefs.getString(KEY_APPEARANCE, "").orEmpty(),
            lastError = prefs.getString(KEY_ERROR, "").orEmpty(),
            updatedAt = prefs.getLong(KEY_UPDATED, 0L),
        )
    }

    fun save(context: Context, diagnostics: OctopusDiagnostics) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TRIGGER, diagnostics.lastTrigger)
            .putString(KEY_ORIGINAL, diagnostics.lastOriginalLine)
            .putString(KEY_FINAL, diagnostics.lastFinalLine)
            .putString(KEY_SOURCE, diagnostics.lastPhraseSource)
            .putString(KEY_BANK, diagnostics.lastPhraseBankId)
            .putString(KEY_ENTRY, diagnostics.lastPhraseEntryId)
            .putString(KEY_TONE, diagnostics.lastPhraseTone)
            .putString(KEY_PROVIDER, diagnostics.lastProviderUsed)
            .putString(KEY_GUARDIAN, diagnostics.lastGuardianReason)
            .putBoolean(KEY_SHOULD_SPEAK, diagnostics.lastShouldSpeak)
            .putString(KEY_VOICE, diagnostics.lastVoiceStatus)
            .putString(KEY_OVERLAY, diagnostics.lastOverlayState)
            .putString(KEY_MIC, diagnostics.lastMicStatus)
            .putString(KEY_TRANSCRIPTION, diagnostics.lastTranscription)
            .putString(KEY_GATEWAY, diagnostics.lastGatewayStatus)
            .putString(KEY_APPEARANCE, diagnostics.lastAppearance)
            .putString(KEY_ERROR, diagnostics.lastError)
            .putLong(KEY_UPDATED, diagnostics.updatedAt)
            .apply()
    }

    fun copyToClipboard(context: Context): Boolean = runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Diagnostic Clochette", read(context).asText()))
        true
    }.getOrDefault(false)
}
