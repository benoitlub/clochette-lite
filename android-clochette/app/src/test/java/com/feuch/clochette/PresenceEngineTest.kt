package com.feuch.clochette

import org.junit.Assert.assertEquals
import org.junit.Test

class PresenceEngineTest {
    @Test
    fun screenOffIsSilent() {
        val decision = PresenceEngine.decide(snapshot(screenActive = false))
        assertEquals(PresenceIntent.SILENT, decision.intent)
        assertEquals("screen_off", decision.reason)
    }

    @Test
    fun deepWorkIsGuarded() {
        val decision = PresenceEngine.decide(snapshot(durationMinutes = 100, switches = 1))
        assertEquals(PresenceIntent.GUARD, decision.intent)
        assertEquals("deep_work", decision.reason)
    }

    @Test
    fun consolidatedDeclinesCreateCaution() {
        val learned = listOf(
            learned("decline", "cooldown"),
            learned("pause_requested", "cooldown"),
        )
        val decision = PresenceEngine.decide(snapshot(learned = learned))
        assertEquals(PresenceIntent.GUARD, decision.intent)
        assertEquals("consolidated_caution", decision.reason)
    }

    @Test
    fun learnedEngagementCanMakeSwitchingCurious() {
        val learned = listOf(
            learned("resume_possible", "kept", MemorySignal.HIGH),
            learned("reply_noted", "kept", MemorySignal.MEDIUM),
        )
        val decision = PresenceEngine.decide(snapshot(switches = 6, learned = learned))
        assertEquals(PresenceIntent.CURIOUS, decision.intent)
        assertEquals("frequent_switching_engaged", decision.reason)
    }

    @Test
    fun recentRefusalOverridesPositiveLearnedMemory() {
        val recent = listOf(
            ClochetteMemoryEntry(
                timestamp = 999_999L,
                context = "voice_reply",
                observedSignal = "user_replied_to_clochette",
                project = null,
                energy = null,
                clochetteLine = "On fait un point ?",
                userReaction = "pause",
                result = "paused",
            ),
        )
        val learned = listOf(
            learned("resume_possible", "kept", MemorySignal.HIGH),
            learned("reply_noted", "kept", MemorySignal.HIGH),
        )
        val decision = PresenceEngine.decide(
            snapshot(durationMinutes = 50, learned = learned, recent = recent),
        )
        assertEquals(PresenceIntent.GUARD, decision.intent)
        assertEquals("recent_feedback_cooldown", decision.reason)
    }

    @Test
    fun longSessionIntervenesWithoutNegativeLearning() {
        val decision = PresenceEngine.decide(snapshot(durationMinutes = 50))
        assertEquals(PresenceIntent.INTERVENE, decision.intent)
        assertEquals("context_change_or_long_session", decision.reason)
    }

    private fun snapshot(
        screenActive: Boolean = true,
        durationMinutes: Long = 0,
        switches: Int = 0,
        learned: List<MemoryEntry> = emptyList(),
        recent: List<ClochetteMemoryEntry> = emptyList(),
    ) = PresenceContextSnapshot(
        activity = ActivitySnapshot(
            recentSwitchCount = switches,
            approximateDurationMs = durationMinutes * 60_000L,
        ),
        sensors = SensorSnapshot(screenActive = screenActive),
        recentMemory = recent,
        learnedMemory = learned,
        capturedAt = 1_000_000L,
    )

    private fun learned(
        intent: String,
        result: String,
        signal: MemorySignal = MemorySignal.MEDIUM,
    ) = MemoryEntry(
        timestamp = 1L,
        lightweightSummary = intent,
        userIntent = intent,
        result = result,
        confidence = signal,
        usefulness = signal,
        expiresInDays = 2,
    )
}
