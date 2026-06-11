package com.feuch.clochette

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale

object ClochetteVoice {
    private var tts: TextToSpeech? = null
    private var ready = false

    fun speak(context: Context, text: String, automatic: Boolean = false) {
        val config = ClochetteVoiceSettings.read(context)
        if (!config.enabled || (automatic && !config.autoSpeak)) return

        val appContext = context.applicationContext
        val current = tts
        if (current == null) {
            tts = TextToSpeech(appContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                configure(config)
                if (ready) say(config, text)
            }
            return
        }
        configure(config)
        if (ready) say(config, text)
    }

    fun speakAfterRemark(context: Context, text: String) {
        speak(context, text, automatic = true)
    }

    fun stop() {
        tts?.stop()
    }

    private fun configure(config: ClochetteVoiceConfig) {
        tts?.language = Locale.FRANCE
        tts?.setPitch(modePitch(config))
        tts?.setSpeechRate(modeRate(config))
    }

    private fun say(config: ClochetteVoiceConfig, text: String) {
        playEffect(config.soundEffect)
        tts?.speak(clean(text), TextToSpeech.QUEUE_FLUSH, null, "clochette-${System.currentTimeMillis()}")
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
