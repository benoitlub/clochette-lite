package com.feuch.clochette

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PersonaLoadResult(
    val profile: PersonaProfile,
    val source: PersonaSource,
)

enum class PersonaSource {
    CACHE,
    ASSET,
    DEFAULT,
}

class PersonaLoader(private val context: Context) {
    private val appContext = context.applicationContext
    private val cacheFile: File
        get() = File(File(appContext.filesDir, "personas").apply { mkdirs() }, PERSONA_FILE)

    fun load(): PersonaLoadResult {
        readCache()?.let { return PersonaLoadResult(it, PersonaSource.CACHE) }
        readAsset()?.let { return PersonaLoadResult(it, PersonaSource.ASSET) }
        return PersonaLoadResult(DEFAULT_PERSONA, PersonaSource.DEFAULT)
    }

    fun synchronizeLocal(): PersonaLoadResult {
        val current = load().profile
        val remote = RemotePersonaRepository(appContext).fetchPersonaOrNull(current)
        if (remote != null) {
            cachePersona(remote)
            return PersonaLoadResult(remote, PersonaSource.CACHE)
        }
        return load()
    }

    fun cachePersona(profile: PersonaProfile) {
        cacheFile.writeText(profile.toJson().toString(), Charsets.UTF_8)
    }

    private fun readCache(): PersonaProfile? = runCatching {
        if (!cacheFile.exists()) return@runCatching null
        PersonaProfile.fromJson(cacheFile.readText(Charsets.UTF_8))
    }.getOrNull()

    private fun readAsset(): PersonaProfile? = runCatching {
        appContext.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { PersonaProfile.fromJson(it.readText()) }
    }.getOrNull()

    private companion object {
        const val PERSONA_FILE = "clochette.json"
        const val ASSET_PATH = "personas/clochette.json"

        val DEFAULT_PERSONA = PersonaProfile(
            id = "clochette",
            name = "Clochette",
            origin = "default",
            version = "0",
            bio = "",
            temperament = emptyMap(),
            behavior = emptyMap(),
            visual = emptyMap(),
            voice = emptyMap(),
            remote = mapOf("enabled" to false),
            typicalLines = emptyList(),
        )
    }
}

private fun PersonaProfile.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("origin", origin)
    .put("version", version)
    .put("bio", bio)
    .put("temperament", temperament.toJsonObject())
    .put("behavior", behavior.toJsonObject())
    .put("visual", visual.toJsonObject())
    .put("voice", voice.toJsonObject())
    .put("remote", remote.toJsonObject())
    .put("typicalLines", JSONArray(typicalLines))

private fun Map<String, Any?>.toJsonObject(): JSONObject = JSONObject().also { json ->
    forEach { (key, value) -> json.put(key, value.toJsonValue()) }
}

private fun Any?.toJsonValue(): Any? = when (this) {
    null -> JSONObject.NULL
    is Map<*, *> -> JSONObject().also { json ->
        forEach { (key, value) -> json.put(key.toString(), value.toJsonValue()) }
    }
    is List<*> -> JSONArray().also { array -> forEach { array.put(it.toJsonValue()) } }
    else -> this
}
