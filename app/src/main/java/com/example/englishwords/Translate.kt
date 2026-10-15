package com.example.englishwords

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

/**
 * Автоперевод слов en -> ru на устройстве (Google ML Kit).
 * Модель (~30 МБ) скачивается один раз, дальше работает без интернета.
 * Переводятся все слова таблицы (сначала ближайшие для уроков), результат сохраняется навсегда.
 */
object Translations {
    private val translator: Translator by lazy {
        Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.RUSSIAN)
                .build()
        )
    }

    private var running = false
    private var stalled = false

    var working by mutableStateOf(false); private set
    var total by mutableIntStateOf(0); private set      // сколько слов переводим в этом запуске
    var finished by mutableIntStateOf(0); private set   // сколько уже переведено
    var error by mutableStateOf<String?>(null); private set

    private fun prepare(w: Word): String {
        var t = w.en
            .replace("sb/sth", "somebody or something")
            .replace(Regex("\\bsth\\b"), "something")
            .replace(Regex("\\bsb\\b"), "somebody")
            .replace("...", "").replace("…", "").replace("(that)", "")
            .trim()
        if (w.pos == "verb") t = "to $t"
        return t
    }

    fun prefetch(force: Boolean = false) {
        if (running) return
        if (stalled && !force) return
        val ids = Repo.untranslatedAll()
        if (ids.isEmpty()) return
        running = true
        working = true
        error = null
        stalled = false
        total = ids.size
        finished = 0
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { step(ArrayDeque(ids), 0) }
            .addOnFailureListener {
                running = false
                working = false
                stalled = true
                error = "Не удалось скачать модель перевода. Нужен интернет (один раз, ~30 МБ)."
                Repo.bumpUi()
            }
    }

    private fun step(queue: ArrayDeque<Int>, done: Int) {
        val id = queue.removeFirstOrNull()
        if (id == null) {
            Repo.saveTranslations()
            running = false
            working = false
            if (done == 0) {
                stalled = true
                error = "Не удалось перевести слова. Проверьте интернет и повторите."
            }
            Repo.bumpUi()
            return
        }
        translator.translate(prepare(Repo.words[id]))
            .addOnSuccessListener { r ->
                Repo.setTranslation(id, r.trim(), notify = false)
                val n = done + 1
                finished = n
                if (n % 25 == 0) Repo.bumpUi()              // обновляем экраны пачками
                if (n % 100 == 0) Repo.saveTranslations()
                step(queue, n)
            }
            .addOnFailureListener { step(queue, done) }
    }
}
