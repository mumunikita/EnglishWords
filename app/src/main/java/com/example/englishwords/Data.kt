package com.example.englishwords

import java.time.LocalDate
import kotlin.math.max

enum class LessonMode { NORMAL, REVIEW, PRACTICE, GROUP }

val LEVELS = listOf("A1", "A2", "B1", "B2", "C1", "C2")

data class Word(
    val id: Int,
    val en: String,
    val ru: String,
    val level: Int,          // индекс в LEVELS
    val pos: String,         // часть речи (может быть пустой)
    val alt: List<String>,   // допустимые варианты написания (colour/color и т.п.)
    val kind: Int = 0        // 0 — слово, 1 — фраза/идиома, 2 — слово с предлогом (belong to), 3 — моё слово, -1 — удалено
) {
    /** Подпись уровня: A1..C2 или «Моё слово». */
    val levelLabel: String get() = if (kind == 3) "Моё слово" else LEVELS[level]

    /** Идиомы и фразы (их можно отключить в настройках). Слова с предлогом сюда не входят. */
    val isPhrase: Boolean get() = kind == 1

    fun matches(answer: String): Boolean {
        val a = norm(answer)
        return a == norm(en) || alt.any { a == norm(it) }
    }

    /** Можно ли предложить собрать слово из букв. */
    val canAssemble: Boolean
        get() = !en.contains(' ') && en.length in 3..14 && en.all { it.isLetter() || it == '\'' || it == '-' }
}

/**
 * Состояние слова в интервальных повторениях.
 * stage — сколько повторений уже пройдено (0 = слово только что выучено).
 * due — момент следующего повторения в минутах от начала эпохи.
 */
data class Progress(
    val stage: Int,
    val due: Long,
    val lapses: Int,
    val preKnown: Boolean = false   // отмечено кнопкой «Знаю»
)

object Srs {
    /** 1-е: 12 мин, 2-е: 4 ч, 3-е: сутки, 4-е: 3 дня, 5-е: неделя, 6-е: месяц. */
    val STEPS_MIN = longArrayOf(12, 4 * 60, 24 * 60, 3 * 24 * 60, 7 * 24 * 60, 30 * 24 * 60)
    const val MASTERED_STAGE = 6

    fun nowMin(): Long = System.currentTimeMillis() / 60000
    fun today(): Long = LocalDate.now().toEpochDay()

    /** Интервал до следующего повторения для слова, прошедшего [stage] повторений. */
    fun intervalFor(stage: Int): Long =
        if (stage < STEPS_MIN.size) STEPS_MIN[stage]
        else minOf(365L * 24 * 60, STEPS_MIN.last() * (1L shl (stage - STEPS_MIN.size + 1)))

    /** «Знаю»: сразу переходим к месячной проверке. */
    fun known(now: Long) = Progress(stage = 5, due = now + STEPS_MIN[5], lapses = 0, preKnown = true)

    fun next(p: Progress?, success: Boolean, now: Long): Progress {
        if (p == null) return Progress(0, now + STEPS_MIN[0], if (success) 0 else 1)
        return if (success) {
            val s = p.stage + 1
            Progress(s, now + intervalFor(s), p.lapses, p.preKnown)
        } else {
            val s = max(0, p.stage - 2)
            Progress(s, now + intervalFor(s), p.lapses + 1, p.preKnown)
        }
    }
}
