package com.feuch.clochette

data class AiRemarkResult(
    val line: String,
    val shouldSpeak: Boolean = true,
    val shouldOpenMic: Boolean = false,
    val listenSeconds: Int = 15,
    val providerUsed: String = "local",
    val source: PhraseSource = PhraseSource.AI_GATEWAY,
)

data class AiRemarkRequest(
    val relationshipMode: String,
    val preferredProvider: String,
    val styleLevel: String,
    val foregroundApp: String?,
    val durationMinutes: Int,
    val appSwitchCount: Int,
    val sensorSummary: String,
    val energy: String?,
    val recentMemorySummary: String,
    val userLastReply: String? = null,
    val nowPlayingAppName: String? = null,
    val nowPlayingTitle: String? = null,
    val nowPlayingArtist: String? = null,
)
