package com.feuch.clochette

data class ContextState(
    val currentAppName: String? = null,
    val durationMinutes: Int = 0,
    val batteryPercent: Int? = null,
    val isCharging: Boolean = false,
    val hourOfDay: Int = 12,
    val dayPeriod: DayPeriod = DayPeriod.AFTERNOON,
    val movementState: MovementState = MovementState.STILL,
    val screenOnDuration: Long = 0L,
    val recentAppSwitches: Int = 0,
    val userEnergyEstimate: UserEnergyEstimate = UserEnergyEstimate.MEDIUM,
)

enum class DayPeriod {
    MORNING,
    AFTERNOON,
    EVENING,
    NIGHT,
}

enum class MovementState {
    STILL,
    WALKING,
    VEHICLE,
}

enum class UserEnergyEstimate {
    LOW,
    MEDIUM,
    HIGH,
}
