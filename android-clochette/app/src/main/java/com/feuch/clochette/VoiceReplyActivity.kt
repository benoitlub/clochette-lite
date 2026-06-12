package com.feuch.clochette

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class VoiceReplyActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening by mutableStateOf(false)
    private var transcript by mutableStateOf("")
    private var clochetteReply by mutableStateOf("")
    private var status by mutableStateOf("Micro fermé")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) startListening() else status = "Micro refusé"
            }

            MaterialTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFF7F3EA)),
                    color = Color(0xFFF7F3EA),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("Répondre à Clochette", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Le micro ne s'ouvre que maintenant, avec ton accord, et se ferme au bout de 15 secondes maximum.")
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(status, fontWeight = FontWeight.SemiBold)
                                Text(if (transcript.isBlank()) "Transcription en attente." else transcript)
                                if (clochetteReply.isNotBlank()) {
                                    Text("Clochette", fontWeight = FontWeight.SemiBold)
                                    Text(clochetteReply)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        enabled = !listening,
                                        onClick = {
                                            if (ContextCompat.checkSelfPermission(this@VoiceReplyActivity, Manifest.permission.RECORD_AUDIO) ==
                                                PackageManager.PERMISSION_GRANTED
                                            ) {
                                                startListening()
                                            } else {
                                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        },
                                    ) {
                                        Text("Parler 15 s")
                                    }
                                    OutlinedButton(onClick = { finish() }) {
                                        Text("Fermer")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status = "Reconnaissance vocale indisponible"
            Toast.makeText(this, status, Toast.LENGTH_SHORT).show()
            return
        }
        transcript = ""
        clochetteReply = ""
        status = "J'écoute. Quinze secondes maximum."
        listening = true
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(replyListener)
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1),
            )
        }
        handler.postDelayed({ stopListening("Temps écoulé.") }, MAX_LISTEN_MS)
    }

    private fun stopListening(message: String) {
        if (!listening) return
        status = message
        listening = false
        recognizer?.stopListening()
    }

    private val replyListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            status = "J'écoute."
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            status = "Je transforme ça en mots."
            listening = false
        }

        override fun onError(error: Int) {
            listening = false
            status = "Je n'ai pas bien attrapé la phrase."
        }

        override fun onResults(results: Bundle?) {
            listening = false
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            transcript = text
            replyWithAi(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            transcript = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun replyTo(text: String): String {
        if (text.isBlank()) return "Je peux me tromper, mais le silence avait l'air très occupé."
        val lower = text.lowercase()
        return when {
            "pause" in lower || "fatigu" in lower -> "Je remarque la fatigue. On réduit la voilure, pas la dignité."
            "reprendre" in lower || "continuer" in lower -> "Hypothèse : tu veux reprendre. Très bien. Un geste petit, puis on parade."
            "bloqu" in lower || "bug" in lower -> "Je soupçonne un blocage. On nomme le monstre, puis on lui vole ses chaussures."
            else -> "Je note. Je peux me tromper, mais il y a une piste exploitable là-dedans."
        }
    }

    private fun replyWithAi(text: String) {
        status = "Clochette réfléchit."
        val appContext = applicationContext
        val activity = UsageObserver(this).snapshot()
        val memory = ClochetteMemory(this).recent(12)
        val state = ContextRemarkEngine(this).buildState(activity)
        val config = AiGatewaySettings.read(this)
        val nowPlaying = NowPlayingObserver.snapshot(this)
        if (!config.enabled) {
            finishReply(text, replyTo(text), "local_fallback")
            return
        }
        val request = AiRemarkRequest(
            relationshipMode = RelationshipModeSettings.selected(this).id,
            preferredProvider = config.preferredProvider,
            styleLevel = config.styleLevel,
            foregroundApp = state.currentAppName,
            durationMinutes = state.durationMinutes,
            appSwitchCount = state.recentAppSwitches,
            sensorSummary = "voice_reply",
            energy = null,
            recentMemorySummary = memory.mapNotNull { it.clochetteLine }.takeLast(3).joinToString(" | "),
            userLastReply = text,
            nowPlayingAppName = nowPlaying.appName,
            nowPlayingTitle = nowPlaying.title,
            nowPlayingArtist = nowPlaying.artist,
        )
        thread(name = "clochette-voice-reply-ai") {
            val aiResult = AiGatewayClient(appContext).generateRemark(request)
            handler.post {
                if (aiResult != null) {
                    val guardian = GuardianRulesLoader(this).approve(
                        candidate = aiResult.line,
                        state = state,
                        recentLines = memory.mapNotNull { it.clochetteLine },
                        recentEntries = memory,
                        relationshipMode = RelationshipModeSettings.selected(this),
                        wantsVoice = true,
                    )
                    finishReply(
                        text,
                        guardian.line?.withVisibleFrenchAccents() ?: replyTo(text),
                        aiResult.providerUsed,
                    )
                } else {
                    finishReply(text, replyTo(text), "local_fallback")
                }
            }
        }
    }

    private fun finishReply(userText: String, reply: String, provider: String) {
        clochetteReply = reply.withVisibleFrenchAccents()
        status = "Réponse prête · provider : $provider"
        saveReply(userText, clochetteReply)
        if (clochetteReply.isNotBlank()) ClochetteVoice.speak(this@VoiceReplyActivity, clochetteReply)
    }

    private fun saveReply(userReply: String, reply: String) {
        ObservationJournal(this).add(
            ObservationJournalEntry(
                activity = "voice_reply",
                question = ClochetteRemarkStore.latest(this),
                userReply = userReply,
                reaction = reply,
                result = "answered",
            ),
        )
    }

    companion object {
        private const val MAX_LISTEN_MS = 15_000L
    }
}
