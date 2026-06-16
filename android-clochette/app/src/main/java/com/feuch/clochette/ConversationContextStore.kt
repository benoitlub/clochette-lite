package com.feuch.clochette

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ConversationContext(
    val userText: String = "",
    val intent: String = "unknown",
    val mood: String = "neutral",
    val energy: String = "medium",
    val explicitRequest: String = "",
    val subject: String = "",
    val characterId: String = CharacterRegistry.DEFAULT_ACTIVE,
    val lastAvatarLine: String = "",
    val tags: Set<String> = emptySet(),
    val updatedAt: Long = 0L,
)

object ConversationContextStore {
    private const val PREFS = "clochette_conversation_context"
    private const val KEY_LAST = "last"
    private const val KEY_HISTORY = "history"
    private const val MAX_HISTORY = 10

    fun analyzeAndStore(
        context: Context,
        userText: String,
        characterId: String,
        lastAvatarLine: String,
    ): ConversationContext {
        val lower = userText.lowercase()
        val mood = when {
            listOf("crevé", "crevee", "fatigu", "épuis", "epuis", "dodo").any { it in lower } -> "tired"
            listOf("flemme", "pas envie", "procrast").any { it in lower } -> "avoidant"
            listOf("stress", "ango", "panique", "peur").any { it in lower } -> "anxious"
            listOf("haha", "mdr", "lol", "drôle", "drole").any { it in lower } -> "playful"
            listOf("énerv", "enerve", "saoule", "marre").any { it in lower } -> "irritated"
            else -> "neutral"
        }
        val intent = when {
            "?" in userText || listOf("comment", "quoi", "pourquoi", "tu peux", "aide").any { it in lower } -> "asks_help"
            listOf("reprendre", "continuer", "go", "lance").any { it in lower } -> "wants_action"
            listOf("pause", "repos", "stop").any { it in lower } -> "wants_pause"
            listOf("flemme", "crevé", "fatigu", "bloqué", "bloque").any { it in lower } -> "needs_motivation"
            else -> "small_talk"
        }
        val energy = when (mood) {
            "tired", "avoidant", "anxious" -> "low"
            "playful" -> "high"
            else -> "medium"
        }
        val subject = lower
            .split(Regex("\\s+"))
            .firstOrNull { it.length >= 5 && it !in COMMON_WORDS }
            .orEmpty()
        val tags = buildSet {
            add(intent)
            add(mood)
            add("energy_$energy")
            if (subject.isNotBlank()) add("subject_$subject")
            if (userText.contains("?")) add("question")
        }
        val value = ConversationContext(
            userText = userText.trim(),
            intent = intent,
            mood = mood,
            energy = energy,
            explicitRequest = if (intent == "asks_help") userText.trim() else "",
            subject = subject,
            characterId = characterId,
            lastAvatarLine = lastAvatarLine,
            tags = tags,
            updatedAt = System.currentTimeMillis(),
        )
        save(context, value)
        return value
    }

    fun latest(context: Context): ConversationContext? {
        val raw = prefs(context).getString(KEY_LAST, null) ?: return null
        return runCatching { fromJson(JSONObject(raw)) }.getOrNull()
    }

    fun recent(context: Context): List<ConversationContext> {
        val raw = prefs(context).getString(KEY_HISTORY, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { runCatching { fromJson(it) }.getOrNull() }
        }
    }

    private fun save(context: Context, value: ConversationContext) {
        val history = (recent(context) + value).takeLast(MAX_HISTORY)
        val array = JSONArray()
        history.forEach { array.put(toJson(it)) }
        prefs(context).edit()
            .putString(KEY_LAST, toJson(value).toString())
            .putString(KEY_HISTORY, array.toString())
            .apply()
    }

    private fun toJson(value: ConversationContext): JSONObject = JSONObject()
        .put("userText", value.userText)
        .put("intent", value.intent)
        .put("mood", value.mood)
        .put("energy", value.energy)
        .put("explicitRequest", value.explicitRequest)
        .put("subject", value.subject)
        .put("characterId", value.characterId)
        .put("lastAvatarLine", value.lastAvatarLine)
        .put("tags", JSONArray(value.tags.toList()))
        .put("updatedAt", value.updatedAt)

    private fun fromJson(json: JSONObject): ConversationContext = ConversationContext(
        userText = json.optString("userText"),
        intent = json.optString("intent", "unknown"),
        mood = json.optString("mood", "neutral"),
        energy = json.optString("energy", "medium"),
        explicitRequest = json.optString("explicitRequest"),
        subject = json.optString("subject"),
        characterId = json.optString("characterId", CharacterRegistry.DEFAULT_ACTIVE),
        lastAvatarLine = json.optString("lastAvatarLine"),
        tags = json.optJSONArray("tags").toStringSet(),
        updatedAt = json.optLong("updatedAt", 0L),
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun JSONArray?.toStringSet(): Set<String> {
        if (this == null) return emptySet()
        return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }.toSet()
    }

    private val COMMON_WORDS = setOf(
        "alors", "comme", "avoir", "faire", "encore", "juste", "vraiment", "clochette",
        "bonjour", "merci", "maintenant", "besoin", "envie",
    )
}

object CharacterReplyStyle {
    fun reply(characterId: String, context: ConversationContext): String {
        val mood = context.mood
        val intent = context.intent
        return when (characterId) {
            CharacterRegistry.NATASHA -> when {
                mood == "tired" -> "Flash spécial : batterie humaine basse. On réduit le format, pas l’ambition."
                intent == "needs_motivation" -> "Breaking news : ton cerveau négocie. Je propose une micro-action de trois minutes."
                mood == "irritated" -> "Je note la turbulence. On garde le style, on baisse le volume."
                else -> "Je prends l’info. Version courte : on transforme ça en angle exploitable."
            }
            CharacterRegistry.FEUCH -> when {
                intent == "needs_motivation" -> "Flemme détectée. Solution Feuch : un truc minuscule, mais avec panache."
                mood == "tired" -> "Mode cendres chaudes. On souffle doucement et on rallume une étincelle."
                else -> "Reçu. Je secoue le bocal, mais je garde les morceaux utiles."
            }
            CharacterRegistry.FEE_BELETTE -> when {
                mood == "tired" -> "La fatigue parle bas. On l’écoute, puis on choisit une petite lumière."
                intent == "needs_motivation" -> "La flemme est peut-être une couverture. Regarde dessous, doucement."
                else -> "Je garde ça près de l’oreille. Il y a une piste qui bouge."
            }
            CharacterRegistry.SOFIA -> when {
                mood == "tired" -> "Alors on baisse l’ambition, pas la tendresse. Une petite chose, pas une montagne."
                intent == "asks_help" -> "Je peux t’aider à le découper simplement. Une étape claire d’abord."
                else -> "Je comprends. On garde ça simple et praticable."
            }
            CharacterRegistry.BIRDY -> when {
                mood == "tired" -> "Mini-mode activé. Pas de marathon, juste un battement d’aile."
                intent == "needs_motivation" -> "Hop, micro-départ. Une action minuscule, et je fais le bruitage mental."
                else -> "J’ai capté le signal. Ça pépie un peu, mais c’est exploitable."
            }
            CharacterRegistry.AUDREY -> when {
                mood == "tired" -> "Ok. Pas de grand discours. Une seule action ridicule, et on sauve l’honneur."
                intent == "needs_motivation" -> "Très bien, flemme admise. Maintenant une action laide mais réelle."
                else -> "Reçu. Je retire le décoratif, il reste quoi à faire ?"
            }
            CharacterRegistry.FEUNETTE_VERTE -> when {
                mood == "tired" -> "Petite feuille molle détectée. On cherche le soleil le plus proche."
                intent == "needs_motivation" -> "La flemme pousse comme une mousse. On marche dessus tout doucement ?"
                else -> "Je renifle l’idée. Elle n’est pas morte, elle dort dans un pot."
            }
            CharacterRegistry.BRUMEUX -> when {
                mood == "tired" -> "Le brouillard ne demande pas de courir. Il demande de choisir une lumière."
                intent == "asks_help" -> "La question est une lanterne. Approche-la du sol."
                else -> "Je vois une forme dans la brume. Pas tout, mais assez pour avancer."
            }
            else -> when {
                mood == "tired" -> "Je te crois. On baisse la marche et on garde le fil."
                intent == "needs_motivation" -> "Hypothèse : tu bloques au seuil. On fait juste le premier geste."
                intent == "asks_help" -> "Je peux t’aider. On prend la version courte et on la rend faisable."
                else -> "Je note. Ça change la suite, pas juste la décoration."
            }
        }.withVisibleFrenchAccents()
    }
}
