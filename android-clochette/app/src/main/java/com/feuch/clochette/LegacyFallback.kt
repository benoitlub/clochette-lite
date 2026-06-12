package com.feuch.clochette

object LegacyFallback {
    fun remark(
        activity: ActivitySnapshot = ActivitySnapshot(),
        sensors: SensorSnapshot = SensorSnapshot(),
        energy: String? = null,
        project: String? = null,
        memory: List<ClochetteMemoryEntry> = emptyList(),
        phraseLength: String = ClochetteVoiceSettings.LENGTH_NORMAL,
    ): String = ClochetteEngine.remark(
        activity = activity,
        sensors = sensors,
        energy = energy,
        project = project,
        memory = memory,
        phraseLength = phraseLength,
    )

    fun sourceInfo(debug: Boolean, tracer: DebugSourceTracer? = null): String =
        tracer?.prefixFor(DebugSource.LEGACY, debug).orEmpty()
}
