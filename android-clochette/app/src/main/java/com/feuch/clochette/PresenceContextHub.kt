package com.feuch.clochette

import android.content.Context

data class PresenceContextSnapshot(
    val activity: ActivitySnapshot,
    val sensors: SensorSnapshot = SensorSnapshot(),
    val recentMemory: List<ClochetteMemoryEntry> = emptyList(),
    val learnedMemory: List<MemoryEntry> = emptyList(),
    val capturedAt: Long = System.currentTimeMillis(),
)

object PresenceContextHub {
    private const val PREFS = "clochette_presence_context"

    fun publishSensors(context: Context, sensors: SensorSnapshot) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("walking_possible", sensors.walkingPossible)
            .putBoolean("phone_still", sensors.phoneStill)
            .putBoolean("low_light", sensors.lowLight)
            .putString("orientation", sensors.orientation)
            .putBoolean("screen_active", sensors.screenActive)
            .putLong("sensor_captured_at", System.currentTimeMillis())
            .apply()
    }

    fun capture(context: Context): PresenceContextSnapshot {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return PresenceContextSnapshot(
            activity = UsageObserver(appContext).snapshot(),
            sensors = SensorSnapshot(
                walkingPossible = prefs.getBoolean("walking_possible", false),
                phoneStill = prefs.getBoolean("phone_still", true),
                lowLight = prefs.getBoolean("low_light", false),
                orientation = prefs.getString("orientation", "unknown") ?: "unknown",
                screenActive = prefs.getBoolean("screen_active", true),
            ),
            recentMemory = ClochetteMemory(appContext).recent(24),
            learnedMemory = ConsolidatedMemoryStore(appContext).active(24),
        )
    }
}
