package com.feuch.clochette

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.json.JSONObject
import java.util.Calendar
import kotlin.math.abs

class ContextRemarkEngine(context: Context) {
    private val appContext = context.applicationContext
    private var lastSource: PhraseSource = PhraseSource.UNKNOWN

    fun lastSource(): PhraseSource = lastSource

    fun remark(
        activity: ActivitySnapshot,
        memory: List<ClochetteMemoryEntry>,
        sensors: SensorSnapshot = SensorSnapshot(),
        energy: String? = null,
    ): String? {
        val state = buildState(activity = activity, sensors = sensors, energy = energy)
        val model = loadModel() ?: return builtInFallback(state, memory).also { lastSource = PhraseSource.GUARDIAN_FALLBACK }
        val mood = MoodManager.moodFor(state, memory)
        val candidates = rankedCandidates(model, state, mood)
        val chosen = candidates
            .filterNot { isForbidden(it.line) }
            .maxWithOrNull(compareBy<RemarkCandidate> { it.score }.thenBy { seededTieBreaker(it.line, state, memory) })

        return if (chosen != null && chosen.score >= MIN_RELEVANCE_SCORE) {
            lastSource = chosen.source
            chosen.line.withVisibleFrenchAccents()
        } else {
            simpleFallback(model, state, memory)
        }
    }

    fun buildState(
        activity: ActivitySnapshot,
        sensors: SensorSnapshot = SensorSnapshot(),
        energy: String? = null,
    ): ContextState {
        val battery = batteryState()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val movement = when {
            sensors.walkingPossible -> MovementState.WALKING
            sensors.phoneStill -> MovementState.STILL
            else -> MovementState.STILL
        }
        return ContextState(
            currentAppName = activity.foregroundDisplayName ?: activity.foregroundPackage,
            durationMinutes = activity.approximateDurationMs.toMinutes(),
            batteryPercent = battery.percent,
            isCharging = battery.isCharging,
            hourOfDay = hour,
            dayPeriod = dayPeriod(hour),
            movementState = movement,
            screenOnDuration = activity.approximateDurationMs,
            recentAppSwitches = activity.recentSwitchCount,
            userEnergyEstimate = energy.toEnergyEstimate(),
        )
    }

    private fun rankedCandidates(
        model: ContextLinesModel,
        state: ContextState,
        mood: ClochetteMood,
    ): List<RemarkCandidate> {
        val results = mutableListOf<RemarkCandidate>()
        val appName = state.currentAppName.orEmpty().lowercase()
        val band = durationBand(state.durationMinutes)

        model.apps.forEach { app ->
            val appMatches = app.names.any { name -> appName.contains(name.lowercase()) } ||
                app.packageHints.any { hint -> appName.contains(hint.lowercase()) }
            if (appMatches) {
                val lines = app.lines[band].orEmpty() + app.lines["any"].orEmpty()
                lines.forEach { line ->
                    results += RemarkCandidate(
                        line = line.withMood(mood),
                        score = 100 + state.durationMinutes.coerceAtMost(180) / 10,
                        source = app.source,
                    )
                }
            }
        }

        if (state.durationMinutes >= 120) {
            model.states["duration_long"].orEmpty().forEach { results += RemarkCandidate(it.withMood(mood), 70, PhraseSource.CONTEXT_LINES) }
        }
        if (state.dayPeriod == DayPeriod.NIGHT || state.hourOfDay >= 22) {
            model.states["night"].orEmpty().forEach { results += RemarkCandidate(it.withMood(mood), 60, PhraseSource.CONTEXT_LINES) }
        }
        if (state.batteryPercent != null && state.batteryPercent <= 18 && !state.isCharging) {
            model.states["battery_low"].orEmpty().forEach { results += RemarkCandidate(it.withMood(ClochetteMood.CONCERNED), 55, PhraseSource.CONTEXT_LINES) }
        }
        if (state.movementState == MovementState.WALKING) {
            model.states["walking"].orEmpty().forEach { results += RemarkCandidate(it.withMood(mood), 45, PhraseSource.CONTEXT_LINES) }
        }
        if (state.movementState == MovementState.STILL && state.durationMinutes >= 45) {
            model.states["still_long"].orEmpty().forEach { results += RemarkCandidate(it.withMood(mood), 40, PhraseSource.CONTEXT_LINES) }
        }
        if (state.recentAppSwitches >= 5) {
            model.states["switching"].orEmpty().forEach { results += RemarkCandidate(it.withMood(ClochetteMood.PLAYFUL), 35, PhraseSource.CONTEXT_LINES) }
        }

        return results
    }

    private fun simpleFallback(
        model: ContextLinesModel,
        state: ContextState,
        memory: List<ClochetteMemoryEntry>,
    ): String {
        val hints = MemoryHints.hintsFor(state, memory)
        if (hints.isNotEmpty() && state.currentAppName.isNullOrBlank()) {
            lastSource = PhraseSource.GUARDIAN_FALLBACK
            return (hints.pick(state, memory) ?: builtInFallback(state, memory)).withVisibleFrenchAccents()
        }
        val lines = model.fallbackLines.filterNot { isForbidden(it.line) }
        val picked = lines.pickSourceLine(state, memory)
        lastSource = picked?.source ?: PhraseSource.GUARDIAN_FALLBACK
        return (picked?.line ?: builtInFallback(state, memory)).withVisibleFrenchAccents()
    }

    private fun builtInFallback(state: ContextState, memory: List<ClochetteMemoryEntry>): String {
        val fallback = listOf(
            "Je remarque juste que ça dure. Pas un drame, mais je pose ma petite lampe dessus.",
            "Je peux me tromper, mais ton attention fait des zigzags.",
            "J'ai l'impression que quelque chose cherche une sortie simple.",
        )
        return fallback.pick(state, memory) ?: fallback.first()
    }

    private fun loadModel(): ContextLinesModel? {
        val models = ASSET_PATHS.mapNotNull { path -> runCatching { parseModel(path) }.getOrNull() }
        if (models.isEmpty()) return null
        val states = models
            .flatMap { it.states.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { (_, values) -> values.flatten() }
        return ContextLinesModel(
            apps = models.flatMap { it.apps },
            states = states,
            fallbackLines = models.flatMap { it.fallbackLines },
        )
    }

    private fun parseModel(path: String): ContextLinesModel {
        val raw = appContext.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = JSONObject(raw)
        val source = sourceForPath(path)
        val apps = json.optJSONArray("apps").toObjectList { item ->
            val linesJson = item.optJSONObject("lines")
            val displayName = item.optString("displayName").takeIf { it.isNotBlank() }
            val names = (item.optJSONArray("names").toStringList() + listOfNotNull(displayName)).distinct()
            ContextAppLines(
                id = item.optString("id"),
                names = names,
                packageHints = item.optJSONArray("packageHints").toStringList(),
                source = source,
                lines = linesJson?.keys()?.asSequence()?.associateWith { key ->
                    linesJson.optJSONArray(key).toStringList()
                }.orEmpty(),
            )
        }
        val statesJson = json.optJSONObject("states")
        val states = statesJson?.keys()?.asSequence()?.associateWith { key ->
            statesJson.optJSONArray(key).toStringList()
        }.orEmpty()
        return ContextLinesModel(
            apps = apps,
            states = states,
            fallbackLines = json.optJSONArray("fallbackLines").toStringList().map { SourceLine(it, source) },
        )
    }

    private fun sourceForPath(path: String): PhraseSource = when {
        path.endsWith("app_context_lines.json") -> PhraseSource.APP_CONTEXT_LINES
        path.endsWith("context_lines.json") -> PhraseSource.CONTEXT_LINES
        else -> PhraseSource.UNKNOWN
    }

    private fun batteryState(): BatteryState {
        val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return BatteryState(
            percent = if (level >= 0 && scale > 0) ((level * 100f) / scale).toInt() else null,
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
        )
    }

    private fun durationBand(minutes: Int): String = when {
        minutes >= 180 -> "marathon"
        minutes >= 120 -> "very_long"
        minutes >= 60 -> "long"
        minutes >= 20 -> "medium"
        else -> "short"
    }

    private fun dayPeriod(hour: Int): DayPeriod = when (hour) {
        in 5..11 -> DayPeriod.MORNING
        in 12..17 -> DayPeriod.AFTERNOON
        in 18..21 -> DayPeriod.EVENING
        else -> DayPeriod.NIGHT
    }

    private fun String?.toEnergyEstimate(): UserEnergyEstimate = when (this?.lowercase()) {
        "basse", "low", "fatiguee", "fatigue" -> UserEnergyEstimate.LOW
        "haute", "high" -> UserEnergyEstimate.HIGH
        else -> UserEnergyEstimate.MEDIUM
    }

    private fun String.withMood(mood: ClochetteMood): String = when (mood) {
        ClochetteMood.SLEEPY -> if (contains("sommeil", ignoreCase = true)) this else this
        ClochetteMood.CONCERNED -> if (startsWith("Je")) this else "Je remarque juste que $this"
        ClochetteMood.PROUD,
        ClochetteMood.PLAYFUL,
        ClochetteMood.CALM -> this
    }

    private fun List<String>.pick(state: ContextState, memory: List<ClochetteMemoryEntry>): String? {
        if (isEmpty()) return null
        val seed = state.currentAppName.orEmpty().sumOf { it.code } +
            state.durationMinutes +
            state.recentAppSwitches +
            memory.fold(0) { total, entry -> total + (entry.timestamp % 29).toInt() }
        return this[abs(seed) % size]
    }

    private fun List<SourceLine>.pickSourceLine(state: ContextState, memory: List<ClochetteMemoryEntry>): SourceLine? {
        if (isEmpty()) return null
        val seed = state.currentAppName.orEmpty().sumOf { it.code } +
            state.durationMinutes +
            state.recentAppSwitches +
            memory.fold(0) { total, entry -> total + (entry.timestamp % 29).toInt() }
        return this[abs(seed) % size]
    }

    private fun seededTieBreaker(line: String, state: ContextState, memory: List<ClochetteMemoryEntry>): Int {
        return abs(line.sumOf { it.code } + state.durationMinutes + memory.size)
    }

    private fun isForbidden(line: String): Boolean {
        val normalized = line.lowercase()
        return listOf(
            "le plan n'est pas l'entree de service",
            "le plan n'est pas l'entrée de service",
            "le vide repond au vide",
            "le vide répond au vide",
            "les chemins sont des portes",
        ).any { normalized.contains(it) }
    }

    private fun Long.toMinutes(): Int = (this / 60_000L).toInt().coerceAtLeast(0)

    private data class ContextLinesModel(
        val apps: List<ContextAppLines>,
        val states: Map<String, List<String>>,
        val fallbackLines: List<SourceLine>,
    )

    private data class ContextAppLines(
        val id: String,
        val names: List<String>,
        val packageHints: List<String>,
        val source: PhraseSource,
        val lines: Map<String, List<String>>,
    )

    private data class RemarkCandidate(
        val line: String,
        val score: Int,
        val source: PhraseSource,
    )

    private data class SourceLine(
        val line: String,
        val source: PhraseSource,
    )

    private data class BatteryState(
        val percent: Int?,
        val isCharging: Boolean,
    )

    private companion object {
        val ASSET_PATHS = listOf(
            "personas/clochette/context_lines.json",
            "personas/clochette/app_context_lines.json",
        )
        const val MIN_RELEVANCE_SCORE = 30
    }
}

private fun <T> org.json.JSONArray?.toObjectList(mapper: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(mapper) }
}

private fun org.json.JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
}
