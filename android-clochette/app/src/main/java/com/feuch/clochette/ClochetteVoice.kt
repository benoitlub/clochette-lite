package com.feuch.clochette

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

object ClochetteVoice {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var listenerInstalled = false
    private var appContext: Context? = null

    fun speak(context: Context, text: String, automatic: Boolean = false) {
        if (!VoiceInteractionController.canSpeak(context)) {
            ClochetteRuntimeStatus.recordVoiceAction(context, "skipped_listening")
            return
        }
        val config = ClochetteVoiceSettings.read(context)
        if (!config.enabled) {
            ClochetteRuntimeStatus.recordVoiceAction(context, "skipped_voice_disabled")
            return
        }
        if (automatic && !config.autoSpeak) {
            ClochetteRuntimeStatus.recordVoiceAction(context, "skipped_auto_speak_disabled")
            return
        }

        val applicationContext = context.applicationContext
        appContext = applicationContext
        VoiceInteractionController.markTtsQueued(applicationContext)
        val current = tts
        if (current == null) {
            tts = TextToSpeech(applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                configure(config)
                installProgressListener()
                if (ready) {
                    say(config, text)
                    ClochetteRuntimeStatus.recordVoiceAction(applicationContext, "spoken")
                } else {
                    VoiceInteractionController.markTtsError(applicationContext, "init")
                    ClochetteRuntimeStatus.recordVoiceAction(applicationContext, "error_tts")
                }
            }
            return
        }
        configure(config)
        installProgressListener()
        if (ready) {
            say(config, text)
            ClochetteRuntimeStatus.recordVoiceAction(applicationContext, "spoken")
        } else {
            VoiceInteractionController.markTtsError(applicationContext, "not_ready")
            ClochetteRuntimeStatus.recordVoiceAction(applicationContext, "error_tts")
        }
    }

    fun speakAfterRemark(context: Context, text: String) {
        speak(context, text, automatic = true)
    }

    fun speakProactive(context: Context, text: String) {
        speak(context, text, automatic = false)
    }

    fun stop() {
        tts?.stop()
        appContext?.let { VoiceInteractionController.markTtsDone(it, "stopped") }
    }

    fun prepareForListening(context: Context): Boolean {
        if (VoiceInteractionController.isTtsSpeakingNow(context) || tts?.isSpeaking == true) {
            ClochetteRuntimeStatus.recordVoiceAction(context, "mic_blocked_tts_speaking")
            return false
        }
        return true
    }

    private fun installProgressListener() {
        if (listenerInstalled) return
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                appContext?.let {
                    VoiceInteractionController.markTtsStarted(it, utteranceId.orEmpty())
                }
            }

            override fun onDone(utteranceId: String?) {
                appContext?.let {
                    VoiceInteractionController.markTtsDone(it, utteranceId.orEmpty())
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                appContext?.let {
                    VoiceInteractionController.markTtsError(it, utteranceId.orEmpty())
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                appContext?.let {
                    VoiceInteractionController.markTtsError(it, "${utteranceId.orEmpty()}:$errorCode")
                }
            }
        })
        listenerInstalled = true
    }

    private fun configure(config: ClochetteVoiceConfig) {
        tts?.language = Locale.FRANCE
        tts?.setPitch(modePitch(config))
        tts?.setSpeechRate(modeRate(config))
    }

    private fun say(config: ClochetteVoiceConfig, text: String) {
        playEffect(config.soundEffect)
        val utteranceId = "clochette-${System.currentTimeMillis()}"
        tts?.speak(clean(text), TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun modeRate(config: ClochetteVoiceConfig): Float {
        val factor = when (config.mode) {
            ClochetteVoiceSettings.MODE_DOUCE -> 0.94f
            ClochetteVoiceSettings.MODE_COACH -> 1.08f
            ClochetteVoiceSettings.MODE_FEUCHIENNE -> 1.02f
            else -> 1.0f
        }
        return (config.speechRate * factor).coerceIn(0.7f, 1.4f)
    }

    private fun modePitch(config: ClochetteVoiceConfig): Float {
        val factor = when (config.mode) {
            ClochetteVoiceSettings.MODE_DOUCE -> 0.95f
            ClochetteVoiceSettings.MODE_COACH -> 0.9f
            ClochetteVoiceSettings.MODE_FEUCHIENNE -> 1.08f
            else -> 1.04f
        }
        return (config.pitch * factor).coerceIn(0.8f, 1.7f)
    }

    private fun playEffect(effect: String) {
        val tone = when (effect) {
            ClochetteVoiceSettings.EFFECT_BELL -> ToneGenerator.TONE_PROP_BEEP
            ClochetteVoiceSettings.EFFECT_POF -> ToneGenerator.TONE_PROP_ACK
            else -> return
        }
        runCatching {
            val generator = ToneGenerator(AudioManager.STREAM_MUSIC, 45)
            generator.startTone(tone, 90)
            Handler(Looper.getMainLooper()).postDelayed({ generator.release() }, 140)
        }
    }

    private fun clean(text: String): String = text
        .replace("Feuch Institut:", "Feuch Institut.")
        .replace("%", " pour cent ")
        .trim()
}
