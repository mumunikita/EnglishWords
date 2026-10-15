package com.example.englishwords

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Ex {
    INTRO,          // знакомство: слово + перевод + звук
    CHOOSE_RU,      // слово -> выбрать перевод
    LISTEN_CHOOSE,  // звук -> выбрать перевод
    CHOOSE_EN,      // перевод -> выбрать слово
    ASSEMBLE,       // перевод -> собрать слово из букв
    TYPE,           // перевод -> написать слово
    LISTEN_TYPE     // звук -> написать слово
}

data class Task(val wordId: Int, val ex: Ex)

class Feedback(val correct: Boolean, val text: String, val yours: String?)

private class WordPlan(val id: Int, val steps: ArrayDeque<Ex>) {
    var done = 0
}

class LessonVm : ViewModel() {
    private val queue = ArrayDeque<Task>()
    private val remaining = HashMap<Int, Int>()   // сколько шагов осталось у слова
    private val mistakes = HashSet<Int>()          // слова, где была ошибка в этом уроке
    private val isNew = HashSet<Int>()
    private val extraAdded = HashSet<Int>()        // для слова уже добавлено проверочное задание
    private var job: Job? = null

    var total by mutableIntStateOf(0); private set
    var done by mutableIntStateOf(0); private set
    var step by mutableIntStateOf(0); private set
    var current by mutableStateOf<Task?>(null); private set
    var options by mutableStateOf<List<Int>>(emptyList()); private set
    var chosen by mutableIntStateOf(-1); private set
    var feedback by mutableStateOf<Feedback?>(null); private set
    var finished by mutableStateOf(false); private set
    var rightCount by mutableIntStateOf(0); private set
    var wrongCount by mutableIntStateOf(0); private set
    var newDone by mutableIntStateOf(0); private set
    var reviewDone by mutableIntStateOf(0); private set
    val isPractice: Boolean get() = practice

    // ---- финальное задание: соединить слова и переводы ----
    private val matchBoards = ArrayDeque<List<Int>>()
    var inMatch by mutableStateOf(false); private set
    var leftIds by mutableStateOf<List<Int>>(emptyList()); private set     // английские слова
    var rightIds by mutableStateOf<List<Int>>(emptyList()); private set    // переводы
    var matchedL by mutableStateOf(setOf<Int>()); private set
    var matchedR by mutableStateOf(setOf<Int>()); private set
    var selected by mutableStateOf<Pair<Int, Int>?>(null); private set     // (колонка 0/1, id слова)
    var flash by mutableStateOf<Pair<Int, Int>?>(null); private set        // неверная пара (id слева, id справа)

    private val mode = Repo.lessonMode
    private val newWords: List<Word> = when (mode) {
        LessonMode.NORMAL -> Repo.pendingNew()
        LessonMode.GROUP -> Repo.groupNewWords()
        else -> emptyList()
    }
    private val reviewWords: List<Word> = when (mode) {
        LessonMode.PRACTICE -> Repo.practiceWords(12)
        LessonMode.GROUP -> Repo.groupDueWords().ifEmpty {
            if (newWords.isEmpty()) Repo.groupPracticeWords(12) else emptyList()
        }
        else -> Repo.dueReviews()
    }
    // тренировка не меняет расписание: либо выбрана явно, либо в группе нечего ни учить, ни повторять по расписанию
    private val practice = mode == LessonMode.PRACTICE ||
        (mode == LessonMode.GROUP && newWords.isEmpty() && Repo.groupDueWords().isEmpty())
    val lessonMode: LessonMode get() = mode

    init {
        build()
        total = queue.size + matchBoards.size
        advance()
    }

    /**
     * Новое слово проходит путь «от простого к сложному»:
     * знакомство -> узнать перевод -> узнать на слух -> вспомнить слово по переводу
     * -> собрать из букв -> написать самому.
     */
    private fun newPlan(w: Word): List<Ex> = buildList {
        add(Ex.INTRO)
        add(Ex.CHOOSE_RU)
        add(Ex.LISTEN_CHOOSE)
        add(Ex.CHOOSE_EN)
        if (w.canAssemble) add(Ex.ASSEMBLE)
        add(Ex.TYPE)
    }

    /**
     * При повторении на каждое слово — 2 задания: сначала «лёгкое» (зависит от номера повторения),
     * затем обязательно написание слова с клавиатуры.
     */
    private fun reviewPlan(w: Word, p: Progress): List<Ex> {
        val first = when (p.stage) {
            0 -> listOf(Ex.CHOOSE_RU, Ex.LISTEN_CHOOSE).random()   // 1-е повторение (через ~12 мин)
            1 -> Ex.CHOOSE_EN                                       // 2-е (через несколько часов)
            2 -> if (w.canAssemble) Ex.ASSEMBLE else Ex.LISTEN_CHOOSE // 3-е (сутки)
            3 -> if (w.canAssemble) Ex.ASSEMBLE else Ex.CHOOSE_EN   // 4-е (3 дня)
            4 -> Ex.LISTEN_TYPE                                     // 5-е (неделя)
            else -> if (w.canAssemble) listOf(Ex.LISTEN_TYPE, Ex.ASSEMBLE).random() else Ex.LISTEN_TYPE
        }
        return listOf(first, Ex.TYPE)
    }

    private fun build() {
        val groups = ArrayList<List<WordPlan>>()

        balanced(reviewWords.shuffled(), 8).forEach { g ->
            groups.add(g.map { WordPlan(it.id, ArrayDeque(reviewPlan(it, Repo.progress[it.id]!!))) })
        }
        if (newWords.isNotEmpty()) {
            // новые слова — небольшими группами по 4, чтобы успевать запоминать
            balanced(newWords, 4).forEach { g ->
                g.forEach { isNew.add(it.id) }
                groups.add(g.map { WordPlan(it.id, ArrayDeque(newPlan(it))) })
            }
        }

        var last: Task? = null
        for (g in groups) {
            g.forEach { remaining[it.id] = it.steps.size }
            last = schedule(g, last)
        }

        // в конце урока — соединить слова и переводы (новые слова урока, а если их нет — повторяемые)
        val src = if (isNew.isNotEmpty()) newWords.map { it.id }
        else reviewWords.map { it.id }.shuffled().take(10)
        src.chunked(10).filter { it.size >= 2 }.forEach { matchBoards.add(it) }
    }

    /** Делим список на примерно равные группы размером не больше [max] (без «хвоста» из одного слова). */
    private fun <T> balanced(list: List<T>, max: Int): List<List<T>> {
        if (list.isEmpty()) return emptyList()
        val n = (list.size + max - 1) / max
        val base = list.size / n
        var extra = list.size % n
        var i = 0
        val out = ArrayList<List<T>>()
        repeat(n) {
            val size = base + if (extra > 0) 1 else 0
            if (extra > 0) extra--
            out.add(list.subList(i, i + size))
            i += size
        }
        return out
    }

    /**
     * Порядок заданий в группе:
     *  - внутри слова шаги идут по порядку (знакомство -> ... -> письмо);
     *  - одно и то же слово не стоит два раза подряд, между его заданиями — минимум два других;
     *  - по возможности подряд не идут два одинаковых типа упражнений;
     *  - слова группы продвигаются вместе (разница не больше одного шага).
     * Если все условия выполнить нельзя (например, в конце группы), послабляем их по очереди,
     * но «то же слово подряд» разрешаем только когда других слов уже не осталось.
     */
    private fun schedule(group: List<WordPlan>, start: Task?): Task? {
        var last = start
        var idx = 0
        val lastPos = HashMap<Int, Int>()
        fun sameEx(a: Ex?, b: Ex) = a != null && a == b && b != Ex.INTRO

        while (group.any { it.steps.isNotEmpty() }) {
            val active = group.filter { it.steps.isNotEmpty() }
            val minDone = active.minOf { it.done }
            val window = active.filter { it.done <= minDone + 1 }
            val lastEx = last?.ex
            val lastWord = last?.wordId
            fun gap(w: WordPlan) = idx - (lastPos[w.id] ?: -10)
            fun exOk(w: WordPlan) = !sameEx(lastEx, w.steps.first())
            fun wordOk(w: WordPlan) = w.id != lastWord

            val tiers = listOf(
                window.filter { gap(it) >= 3 && exOk(it) },
                window.filter { gap(it) >= 2 && exOk(it) },
                window.filter { gap(it) >= 3 },
                window.filter { gap(it) >= 2 },
                window.filter { wordOk(it) && exOk(it) },
                window.filter { wordOk(it) },
                active.filter { wordOk(it) },
                active
            )
            val pick = tiers.first { it.isNotEmpty() }.random()
            val t = Task(pick.id, pick.steps.removeFirst())
            pick.done++
            lastPos[pick.id] = idx
            idx++
            queue.add(t)
            last = t
        }
        return last
    }

    private fun advance() {
        feedback = null
        chosen = -1
        step++
        val t = queue.removeFirstOrNull()
        current = t
        if (t == null) {
            if (matchBoards.isNotEmpty()) startMatch(matchBoards.removeFirst()) else finished = true
            return
        }
        options = when (t.ex) {
            Ex.CHOOSE_RU, Ex.CHOOSE_EN, Ex.LISTEN_CHOOSE -> makeOptions(t)
            else -> emptyList()
        }
    }

    /** Неправильные варианты берём только из слов, которые пользователь учит. */
    private fun makeOptions(t: Task): List<Int> {
        val words = Repo.words
        val showEn = t.ex == Ex.CHOOSE_EN
        fun key(w: Word): String = if (showEn) w.en else w.ru
        val right = words[t.wordId]

        val learning = (Repo.batch() + Repo.progress.filter { !it.value.preKnown }.keys + newWords.map { it.id }).toSet()
        val tiers: List<() -> Collection<Int>> = listOf(
            { learning },                                  // учу сейчас (включая сегодняшние)
            { Repo.progress.keys - learning },             // отмеченные «Знаю»
            { words.indices.shuffled().take(300) }         // запасной вариант, если слов мало
        )
        val wrong = ArrayList<Word>()
        for (tier in tiers) {
            if (wrong.size >= 3) break
            for (id in tier().shuffled()) {
                val w = words[id]
                if (wrong.size >= 3) break
                if (w.id != right.id && key(w).isNotBlank() && key(w) != key(right) &&
                    wrong.none { key(it) == key(w) }
                ) wrong.add(w)
            }
        }
        return (wrong.map { it.id } + right.id).shuffled()
    }

    // ---------- ответы ----------
    fun introNext() {
        val t = current ?: return
        completeStep(t)
        advance()
    }

    fun submitChoice(id: Int) {
        val t = current ?: return
        if (feedback != null) return
        chosen = id
        val picked = Repo.words[id]
        grade(id == t.wordId, "${picked.en} — ${picked.ru}")
    }

    fun submitText(text: String) {
        val t = current ?: return
        if (feedback != null) return
        val typed = text.trim()
        val known = Repo.findWord(typed)
        val shownAnswer = if (known != null && known.ru.isNotBlank()) "${known.en} — ${known.ru}" else typed
        grade(Repo.words[t.wordId].matches(text), shownAnswer)
    }

    /** Кнопка «Дальше» после ошибки. */
    fun next() {
        job?.cancel()
        advance()
    }

    private fun grade(ok: Boolean, yours: String?) {
        val t = current ?: return
        val w = Repo.words[t.wordId]
        if (ok) {
            rightCount++
            feedback = Feedback(true, w.en, yours)
            completeStep(t)
            val typing = t.ex == Ex.TYPE || t.ex == Ex.LISTEN_TYPE || t.ex == Ex.ASSEMBLE
            job = viewModelScope.launch {
                delay(if (typing) 1600 else 900)   // время дослушать озвучку
                advance()
            }
        } else {
            wrongCount++
            mistakes.add(t.wordId)
            feedback = Feedback(false, "${w.en} — ${w.ru}", yours)
            // 1) то же задание вернётся через 2–4 шага и повторяется, пока не ответите верно
            insertTask(t, (2..4).random())
            // 2) при первой ошибке по слову добавляем проверочное задание другого (более простого) типа
            if (extraAdded.add(t.wordId)) {
                val other = buildList {
                    add(Ex.CHOOSE_RU); add(Ex.CHOOSE_EN); add(Ex.LISTEN_CHOOSE)
                    if (w.canAssemble) add(Ex.ASSEMBLE)
                }.filter { it != t.ex }.random()
                remaining[t.wordId] = (remaining[t.wordId] ?: 1) + 1
                insertTask(Task(t.wordId, other), 6)
            }
        }
    }

    /**
     * Вставляем задание в очередь не раньше позиции [from]:
     * сначала ищем место, где рядом нет того же слова и того же типа упражнения,
     * потом — где рядом нет хотя бы того же слова, иначе в конец.
     */
    private fun insertTask(t: Task, from: Int) {
        fun prevOf(p: Int): Task? = if (p == 0) current else queue[p - 1]
        fun wordClash(p: Int) = prevOf(p)?.wordId == t.wordId || queue.getOrNull(p)?.wordId == t.wordId
        fun exClash(p: Int) = prevOf(p)?.ex == t.ex || queue.getOrNull(p)?.ex == t.ex
        val start = minOf(queue.size, from)
        val pos = (start..queue.size).firstOrNull { !wordClash(it) && !exClash(it) }
            ?: (start..queue.size).firstOrNull { !wordClash(it) }
            ?: queue.size
        queue.add(pos, t)
        total++
    }

    // ---------- соединение пар ----------
    private fun startMatch(ids: List<Int>) {
        val l = ids.shuffled()
        var r = ids.shuffled()
        if (ids.size > 2) while (r == l) r = ids.shuffled()
        step++
        current = null
        feedback = null
        leftIds = l
        rightIds = r
        matchedL = emptySet()
        matchedR = emptySet()
        selected = null
        flash = null
        inMatch = true
    }

    /** side: 0 — английское слово, 1 — перевод. Можно начинать с любой колонки. */
    fun tapMatch(side: Int, id: Int) {
        if (!inMatch || flash != null) return
        if ((side == 0 && id in matchedL) || (side == 1 && id in matchedR)) return
        val s = selected
        if (s == null || s.first == side) { selected = side to id; return }
        val leftId = if (side == 1) s.second else id
        val rightId = if (side == 1) id else s.second
        selected = null
        val ok = leftId == rightId || Repo.words[leftId].ru == Repo.words[rightId].ru
        if (ok) {
            rightCount++
            matchedL = matchedL + leftId
            matchedR = matchedR + rightId
            if (matchedL.size == leftIds.size) {
                done++
                job = viewModelScope.launch {
                    delay(900)
                    inMatch = false
                    advance()
                }
            }
        } else {
            wrongCount++
            flash = leftId to rightId
            viewModelScope.launch {
                delay(600)
                flash = null
            }
        }
    }

    private fun completeStep(t: Task) {
        done++
        val left = (remaining[t.wordId] ?: 1) - 1
        remaining[t.wordId] = left
        if (left <= 0) {
            if (!practice) Repo.finish(t.wordId, t.wordId !in mistakes)
            if (t.wordId in isNew) newDone++ else reviewDone++
        }
    }
}
