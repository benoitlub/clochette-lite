package com.feuch.clochette

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

object ClochetteVoice {
    private var tts: TextToSpeech? = null
    private var ready = false

    fun speak(context: Context, text: String) {
        val appContext = context.applicationContext
        val current = tts
        if (current == null) {
            tts = TextToSpeech(appContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                tts?.language = Locale.FRANCE
                tts?.setPitch(1.14f)
                tts?.setSpeechRate(1.03f)
                if (ready) say(text)
            }
            return
        }
        if (ready) say(text)
    }

    fun stop() {
        tts?.stop()
    }

    private fun say(text: String) {
        tts?.speak(clean(text), TextToSpeech.QUEUE_FLUSH, null, "clochette-${System.currentTimeMillis()}")
    }

    private fun clean(text: String): String = text
        .replace("Feuch Institut:", "Feuch Institut.")
        .replace("%", " pour cent ")
        .trim()
}
