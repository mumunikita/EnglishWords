package com.example.englishwords

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Озвучка английских слов (встроенный синтезатор речи Android). */
class Tts(context: Context) {
    private lateinit var engine: TextToSpeech
    private var ready = false
    private var pending: String? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine.language = Locale.US
                engine.setSpeechRate(0.9f)
                ready = true
                pending?.let { speak(it); pending = null }
            }
        }
    }

    fun speak(text: String) {
        if (!ready) { pending = text; return }
        engine.speak(spoken(text), TextToSpeech.QUEUE_FLUSH, null, "word")
    }

    fun shutdown() {
        engine.stop()
        engine.shutdown()
    }
}

/** Что произносить: «sth» -> something, «…» убираем. */
fun spoken(text: String): String =
    text.replace("sb/sth", "somebody or something")
        .replace(Regex("\\bsth\\b"), "something")
        .replace(Regex("\\bsb\\b"), "somebody")
        .replace("...", "").replace("…", "")
        .trim()

/** Нормализация для сравнения ответа с правильным словом. */
fun norm(s: String): String =
    s.trim().lowercase().replace('’', '\'')
        .replace("...", "").replace("…", "")
        .replace(Regex("[.,!?]"), "")
        .replace(Regex("\\b(someone|somebody)\\b"), "sb")
        .replace(Regex("\\bsomething\\b"), "sth")
        .replace(Regex("\\s+"), " ")
        .trim()
