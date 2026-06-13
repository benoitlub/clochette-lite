package com.feuch.clochette

object LegacyFallback {
    fun remark(
        activity: ActivitySnapshot = ActivitySnapshot(),
        sensors: SensorSnapshot = SensorSnapshot(),
        energy: String? = null,
        project: String? = null,
        memory: List<ClochetteMemoryEntry> = emptyList(),
        phraseLength: String = ClochetteVoiceSettings.LENGTH_NORMAL,
    ): String = "Je reste en local. Octopus décide maintenant, les vieux couloirs sont fermés."

    fun sourceInfo(debug: Boolean, tracer: DebugSourceTracer? = null): String =
        tracer?.prefixFor(DebugSource.LEGACY, debug).orEmpty()
}
