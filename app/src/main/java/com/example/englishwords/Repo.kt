package com.example.englishwords

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Слова, прогресс и настройки. Прогресс хранится по тексту слова, а не по номеру. */
object Repo {
    const val MAX_REVIEWS = 50

    private var inited = false
    private lateinit var appCtx: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var file: File
    private lateinit var trFile: File

    val words = ArrayList<Word>()
    private val idOf = HashMap<String, Int>()
    private val aliasOf = HashMap<String, Int>()   // прежние названия слов (belong -> belong to)
    var lessonMode = LessonMode.NORMAL

    /** Группа слов пользователя. В keys — слова в нижнем регистре (так прогресс не зависит от номеров). */
    class Group(val id: String, var name: String, val keys: MutableList<String>)
    val groups = ArrayList<Group>()
    private lateinit var groupsFile: File
    private lateinit var customFile: File
    val progress = HashMap<Int, Progress>()
    private val translations = HashMap<String, String>()
    private var orderByLevel: List<List<Int>> = emptyList()

    /** Экраны читают это значение, чтобы перерисовываться после изменений. */
    var version by mutableIntStateOf(0)
        private set

    /** Сообщение «уровень пройден», показывается на главной. */
    var levelNotice by mutableStateOf<String?>(null)

    private fun bump() { version++ }
    fun bumpUi() = bump()
    fun today(): Long = Srs.today()
    private fun key(w: Word) = w.en.lowercase()

    fun init(context: Context) {
        if (inited) return
        appCtx = context.applicationContext
        prefs = appCtx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        file = File(appCtx.filesDir, "progress_v2.json")
        trFile = File(appCtx.filesDir, "translations.json")
        groupsFile = File(appCtx.filesDir, "groups.json")
        customFile = File(appCtx.filesDir, "custom_words.json")
        File(appCtx.filesDir, "progress.json").delete() // старый формат (по номерам слов)
        loadTranslations()

        appCtx.assets.open("words.csv").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                val p = line.split('\t')
                val en = p[0].trim()
                val alt = p.getOrElse(4) { "" }.split('|').map { it.trim() }.filter { it.isNotEmpty() }
                val kind = p.getOrElse(5) { "0" }.trim().toIntOrNull() ?: 0
                val aliases = p.getOrElse(6) { "" }.split('|').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                val ru = p.getOrElse(3) { "" }.trim().ifEmpty {
                    translations[en.lowercase()] ?: aliases.firstNotNullOfOrNull { translations[it] } ?: ""
                }
                val id = words.size
                words.add(Word(id, en, ru, p[1].toInt(), p.getOrElse(2) { "" }.trim(), alt, kind))
                idOf[en.lowercase()] = id
                aliases.forEach { aliasOf.putIfAbsent(it, id) }
            }
        }
        prefs.edit().remove("activeGroup").apply()   // старый режим «активной группы» больше не используется
        loadCustom()
        loadGroups()
        load()
        rebuildOrder()
        inited = true
    }

    // ---------- сохранение ----------
    private fun load() {
        progress.clear()
        if (!file.exists()) return
        try {
            val o = JSONObject(file.readText())
            for (k in o.keys()) {
                val id = idOf[k] ?: aliasOf[k] ?: continue
                val j = o.getJSONObject(k)
                progress[id] = Progress(j.getInt("s"), j.getLong("d"), j.getInt("l"), j.optBoolean("k", false))
            }
        } catch (_: Exception) {
        }
    }

    private fun save() {
        val o = JSONObject()
        for ((id, p) in progress) {
            o.put(
                key(words[id]),
                JSONObject().put("s", p.stage).put("d", p.due).put("l", p.lapses).put("k", p.preKnown)
            )
        }
        file.writeText(o.toString())
    }

    private fun loadTranslations() {
        if (!trFile.exists()) return
        try {
            val o = JSONObject(trFile.readText())
            for (k in o.keys()) translations[k] = o.getString(k)
        } catch (_: Exception) {
        }
    }

    fun saveTranslations() {
        val o = JSONObject()
        for ((k, v) in translations) o.put(k, v)
        trFile.writeText(o.toString())
    }

    fun setTranslation(id: Int, ru: String, notify: Boolean = true) {
        if (ru.isBlank()) return
        val w = words[id]
        words[id] = w.copy(ru = ru)
        translations[key(w)] = ru
        if (notify) bump()
    }

    // ---------- настройки ----------
    var newPerDay: Int
        get() = prefs.getInt("newPerDay", 10)
        set(v) {
            val delta = v - newPerDay
            rollDay()
            prefs.edit().putInt("newPerDay", v)
                .putInt("target", (prefs.getInt("target", v) + delta).coerceAtLeast(batch().size.coerceAtLeast(1)))
                .apply()
            bump()
        }

    var alphabetical: Boolean
        get() = prefs.getBoolean("alphabetical", false)
        set(v) { prefs.edit().putBoolean("alphabetical", v).apply(); rebuildOrder(); bump() }

    var includePhrases: Boolean
        get() = prefs.getBoolean("includePhrases", true)
        set(v) { prefs.edit().putBoolean("includePhrases", v).apply(); normalizeLevel(); bump() }

    var reminderOn: Boolean
        get() = prefs.getBoolean("reminderOn", false)
        set(v) { prefs.edit().putBoolean("reminderOn", v).apply(); bump() }

    var reminderHour: Int
        get() = prefs.getInt("reminderHour", 20)
        set(v) { prefs.edit().putInt("reminderHour", v.coerceIn(0, 23)).apply(); bump() }

    /** Периоды тишины для уведомлений: с [start] часов до [end] часов (если start > end — через полночь). */
    data class Quiet(val start: Int, val end: Int)

    var quietIntervals: List<Quiet>
        get() = (prefs.getString("quiet", "23-8") ?: "").split(';').mapNotNull { s ->
            val p = s.split('-')
            val a = p.getOrNull(0)?.toIntOrNull()
            val b = p.getOrNull(1)?.toIntOrNull()
            if (a != null && b != null) Quiet(a.coerceIn(0, 23), b.coerceIn(0, 23)) else null
        }
        set(v) {
            prefs.edit().putString("quiet", v.joinToString(";") { "${it.start}-${it.end}" }).apply()
            bump()
        }

    fun isQuietHour(h: Int): Boolean = quietIntervals.any { q ->
        when {
            q.start == q.end -> false
            q.start < q.end -> h >= q.start && h < q.end
            else -> h >= q.start || h < q.end
        }
    }

    /** Как часто напоминать: минимальный промежуток между уведомлениями в часах (1, 2 или 4). */
    var notifyGapHours: Int
        get() = prefs.getInt("notifyGap", 2)
        set(v) { prefs.edit().putInt("notifyGap", v).apply(); bump() }

    fun lastNotif(kind: String): Long = prefs.getLong("lastNotif_$kind", 0)
    fun notifCountToday(): Int = if (prefs.getLong("notifDay", -1) == today()) prefs.getInt("notifCount", 0) else 0
    fun markNotified(kind: String) {
        val n = Srs.nowMin()
        val cnt = notifCountToday() + 1
        prefs.edit().putLong("lastNotif_any", n).putLong("lastNotif_$kind", n)
            .putLong("notifDay", today()).putInt("notifCount", cnt).apply()
    }

    /** Сколько новых слов ещё осталось выучить сегодня (для напоминаний). */
    fun newWordsLeft(): Int {
        val b = batch()
        val pending = b.count { it !in progress }
        val more = if (needsSorting()) (target() - b.size).coerceAtLeast(0) else 0
        return pending + more
    }

    /** Выбранный пользователем уровень (индекс в LEVELS). */
    var level: Int
        get() = prefs.getInt("level", 0).coerceIn(0, LEVELS.size - 1)
        set(v) { prefs.edit().putInt("level", v.coerceIn(0, LEVELS.size - 1)).apply(); bump() }

    private fun rebuildOrder() {
        val byLevel = List(LEVELS.size) { ArrayList<Int>() }
        words.forEach { if (it.kind in 0..2) byLevel[it.level].add(it.id) }
        orderByLevel = byLevel.map { ids ->
            if (alphabetical) ids.sortedBy { words[it].en.lowercase() } else ids.shuffled(java.util.Random(42))
        }
    }

    // ---------- день, новые слова ----------
    private fun rollDay() {
        val t = today()
        if (prefs.getLong("day", -1) != t) {
            prefs.edit().putLong("day", t).putInt("target", newPerDay).putString("batchKeys", "").apply()
        }
    }

    fun target(): Int { rollDay(); return prefs.getInt("target", newPerDay) }

    /** id слов, которые пользователь решил учить сегодня. */
    fun batch(): List<Int> {
        rollDay()
        return prefs.getString("batchKeys", "")!!.split('|').filter { it.isNotEmpty() }.mapNotNull { idOf[it] }
    }

    fun addToBatch(id: Int) {
        val cur = prefs.getString("batchKeys", "")!!
        val k = key(words[id])
        prefs.edit().putString("batchKeys", if (cur.isEmpty()) k else "$cur|$k").apply()
        normalizeLevel()
        bump()
    }

    fun learnMore() {
        prefs.edit().putInt("target", target() + newPerDay).apply()
        bump()
    }

    // ---------- уровни ----------
    private fun eligible(id: Int, b: Set<Int>): Boolean =
        id !in progress && id !in b && (includePhrases || !words[id].isPhrase)

    /** Уровень, из которого сейчас берутся слова: выбранный или следующий, если он уже пройден. */
    fun activeLevel(): Int {
        val b = batch().toSet()
        for (l in level until LEVELS.size) if (orderByLevel[l].any { eligible(it, b) }) return l
        for (l in 0 until level) if (orderByLevel[l].any { eligible(it, b) }) return l
        return -1
    }

    private fun normalizeLevel() {
        val a = activeLevel()
        if (a >= 0 && a != level) {
            if (a > level) levelNotice = "Уровень ${LEVELS[level]} пройден! Переходим на ${LEVELS[a]} 🎉"
            prefs.edit().putInt("level", a).apply()
        }
    }

    /** (пройдено или выбрано, всего) для уровня. */
    fun levelStats(l: Int): Pair<Int, Int> {
        val b = batch().toSet()
        var total = 0
        var done = 0
        for (id in orderByLevel[l]) {
            if (!includePhrases && words[id].isPhrase) continue
            total++
            if (id in progress || id in b) done++
        }
        return done to total
    }

    /** Следующие n слов для карточек (только те, у которых уже есть перевод). */
    fun candidates(n: Int): List<Word> {
        val b = batch().toSet()
        val l = activeLevel()
        if (l < 0) return emptyList()
        return orderByLevel[l].asSequence()
            .filter { eligible(it, b) && words[it].ru.isNotBlank() }
            .take(n).map { words[it] }.toList()
    }

    fun nextCandidate(): Word? = candidates(1).firstOrNull()

    fun untranslatedAhead(n: Int): List<Int> {
        val b = batch().toSet()
        val l = activeLevel()
        if (l < 0) return emptyList()
        return orderByLevel[l].asSequence()
            .filter { eligible(it, b) }
            .take(n + 40)           // ближайшие слова (часть уже переведена)
            .filter { words[it].ru.isBlank() }
            .take(n).toList()
    }

    /** Слово таблицы по введённому тексту (для показа перевода неверного ответа). */
    fun findWord(text: String): Word? {
        val k = text.trim().lowercase()
        if (k.isEmpty()) return null
        val id = idOf[k] ?: aliasOf[k] ?: return null
        return words[id]
    }

    /** Все слова без перевода: сначала ближайшие для уроков, затем остальные по уровням. */
    fun untranslatedAll(): List<Int> {
        val first = untranslatedAhead(200)
        val seen = first.toHashSet()
        val mine = words.filter { it.kind == 3 && it.ru.isBlank() && it.id !in seen }.map { it.id }
        val rest = words.filter { it.kind in 0..2 && it.ru.isBlank() && it.id !in seen }.sortedBy { it.level }.map { it.id }
        return first + mine + rest
    }

    fun hasUntranslatedAhead(): Boolean = untranslatedAhead(1).isNotEmpty()

    fun needsSorting(): Boolean = batch().size < target() && candidates(1).isNotEmpty()

    /** Слова из сегодняшней пачки, которые ещё не пройдены. */
    fun pendingNew(): List<Word> = batch().filter { it !in progress }.map { words[it] }

    fun dueCount(): Int { val now = Srs.nowMin(); return progress.values.count { it.due <= now } }

    fun dueReviews(): List<Word> {
        val now = Srs.nowMin()
        return progress.entries.filter { it.value.due <= now }
            .sortedBy { it.value.due }
            .take(MAX_REVIEWS)
            .map { words[it.key] }
    }

    /** Слова для тренировки, когда повторять по расписанию пока нечего. */
    fun practiceWords(n: Int): List<Word> =
        progress.entries.sortedBy { it.value.due }.take(n).map { words[it.key] }

    /** Ближайший будущий момент повторения (минуты) или null. */
    fun nextDueMinutes(): Long? {
        val now = Srs.nowMin()
        return progress.values.map { it.due }.filter { it > now }.minOrNull()
    }

    // ---------- действия ----------
    fun markKnown(id: Int) {
        progress[id] = Srs.known(Srs.nowMin())
        save()
        normalizeLevel()
        bump()
    }

    fun finish(id: Int, success: Boolean) {
        progress[id] = Srs.next(progress[id], success, Srs.nowMin())
        save()
        touchStreak()
        normalizeLevel()
        bump()
        if (reminderOn) Reminder.scheduleDue(appCtx)
    }

    fun resetAll() {
        progress.clear()
        file.delete()
        prefs.edit().remove("day").remove("batchKeys").remove("target")
            .remove("lastStudy").remove("streak").remove("level").apply()
        bump()
    }

    // ---------- свои слова и группы ----------
    /** Группа, по которой идёт урок (режим LessonMode.GROUP). Общие карточки и новые слова дня она не трогает. */
    var lessonGroupId: String? = null
    fun lessonGroup(): Group? = groups.find { it.id == lessonGroupId }

    /** Невыученные слова группы (с переводом) — их учим в групповом уроке, не больше «слов в день». */
    fun groupNewWords(): List<Word> {
        val g = lessonGroup() ?: return emptyList()
        return groupIds(g).filter { it !in progress && words[it].ru.isNotBlank() }.take(newPerDay).map { words[it] }
    }

    /** Слова группы, которые пора повторять. */
    fun groupDueWords(): List<Word> {
        val g = lessonGroup() ?: return emptyList()
        val now = Srs.nowMin()
        return groupIds(g).filter { (progress[it]?.due ?: Long.MAX_VALUE) <= now }
            .sortedBy { progress[it]!!.due }.take(MAX_REVIEWS).map { words[it] }
    }

    /** Выученные слова группы для тренировки, когда повторять нечего. */
    fun groupPracticeWords(n: Int): List<Word> {
        val g = lessonGroup() ?: return emptyList()
        return groupIds(g).filter { it in progress }.sortedBy { progress[it]!!.due }.take(n).map { words[it] }
    }

    fun groupHasWork(): Boolean =
        groupNewWords().isNotEmpty() || groupDueWords().isNotEmpty() || groupPracticeWords(1).isNotEmpty()

    fun groupMissingTranslations(g: Group): Int = groupIds(g).count { words[it].ru.isBlank() }

    /** Убирает из сегодняшнего списка слова, которые ещё не выучены. */
    fun clearPendingBatch() {
        val keep = batch().filter { it in progress }.map { key(words[it]) }
        prefs.edit().putString("batchKeys", keep.joinToString("|")).apply()
        normalizeLevel()
        bump()
    }

    private fun removeFromBatch(ids: Collection<Int>) {
        val drop = ids.map { key(words[it]) }.toSet()
        val keep = prefs.getString("batchKeys", "")!!.split('|').filter { it.isNotEmpty() && it !in drop }
        prefs.edit().putString("batchKeys", keep.joinToString("|")).apply()
    }

    private fun loadCustom() {
        if (!customFile.exists()) return
        try {
            val a = JSONArray(customFile.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val en = o.getString("en").trim()
                val k = en.lowercase()
                if (k.isEmpty() || idOf.containsKey(k)) continue
                val ru = o.optString("ru", "").ifEmpty { translations[k] ?: "" }
                val id = words.size
                words.add(Word(id, en, ru, 0, "", emptyList(), 3))
                idOf[k] = id
            }
        } catch (_: Exception) {
        }
    }

    private fun saveCustom() {
        val a = JSONArray()
        words.filter { it.kind == 3 }.forEach { a.put(JSONObject().put("en", it.en).put("ru", it.ru)) }
        customFile.writeText(a.toString())
    }

    private fun loadGroups() {
        groups.clear()
        if (!groupsFile.exists()) return
        try {
            val a = JSONArray(groupsFile.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val ks = o.getJSONArray("words")
                groups.add(Group(o.getString("id"), o.getString("name"), MutableList(ks.length()) { ks.getString(it) }))
            }
        } catch (_: Exception) {
        }
    }

    private fun saveGroups() {
        val a = JSONArray()
        groups.forEach { g ->
            a.put(JSONObject().put("id", g.id).put("name", g.name).put("words", JSONArray(g.keys)))
        }
        groupsFile.writeText(a.toString())
    }

    /** Добавляет своё слово (или находит уже существующее). Возвращает его id. */
    fun addCustomWord(enRaw: String, ruRaw: String): Int? {
        val en = enRaw.trim().replace(Regex("\\s+"), " ")
        if (en.isEmpty()) return null
        val k = en.lowercase()
        val ru = ruRaw.trim()
        val existing = idOf[k]
        if (existing != null) {
            val w = words[existing]
            if (ru.isNotEmpty() && (w.kind == 3 || w.ru.isBlank())) {
                words[existing] = w.copy(ru = ru)
                translations[k] = ru
                saveTranslations()
                if (w.kind == 3) saveCustom()
                bump()
            }
            return existing
        }
        val id = words.size
        words.add(Word(id, en, ru.ifEmpty { translations[k] ?: "" }, 0, "", emptyList(), 3))
        idOf[k] = id
        saveCustom()
        bump()
        return id
    }

    /** Удаляет своё слово совсем (номер остаётся занятым пустой записью). */
    fun deleteCustomWord(id: Int) {
        val w = words[id]
        if (w.kind != 3) return
        val k = key(w)
        idOf.remove(k)
        progress.remove(id)
        groups.forEach { it.keys.remove(k) }
        words[id] = Word(id, "", "", 0, "", emptyList(), -1)
        saveCustom(); save(); saveGroups()
        bump()
    }

    fun createGroup(name: String): Group {
        val g = Group(java.util.UUID.randomUUID().toString().take(8), name.trim().ifEmpty { "Моя группа" }, ArrayList())
        groups.add(g)
        saveGroups()
        bump()
        return g
    }

    /**
     * Удаляет группу. Слова, выбранные на сегодня и ещё не выученные, убираются из списка дня.
     * Собственные слова, которых нет в других группах, удаляются совсем.
     * Если [resetProgress] — сбрасывается и изучение слов группы.
     */
    fun deleteGroup(id: String, resetProgress: Boolean = false) {
        val g = groups.find { it.id == id } ?: return
        val ids = groupIds(g)
        groups.remove(g)
        if (lessonGroupId == id) lessonGroupId = null
        removeFromBatch(ids.filter { it !in progress || resetProgress })
        if (resetProgress) {
            ids.forEach { progress.remove(it) }
            save()
        }
        ids.filter { words[it].kind == 3 && groupsOf(it).isEmpty() }.forEach { deleteCustomWord(it) }
        saveGroups()
        normalizeLevel()
        bump()
    }

    fun groupIds(g: Group): List<Int> = g.keys.mapNotNull { idOf[it] }
    fun isInGroup(g: Group, id: Int): Boolean = key(words[id]) in g.keys
    fun groupsOf(id: Int): List<Group> { val k = key(words[id]); return groups.filter { k in it.keys } }

    fun toggleInGroup(g: Group, id: Int) {
        val k = key(words[id])
        val removed = g.keys.remove(k)
        if (!removed) g.keys.add(k)
        if (removed && words[id].kind == 3 && groupsOf(id).isEmpty()) {
            deleteCustomWord(id)   // своё слово без группы больше не нужно
            return
        }
        saveGroups()
        bump()
    }

    fun addWordToGroup(g: Group, en: String, ru: String) {
        val id = addCustomWord(en, ru) ?: return
        val k = key(words[id])
        if (k !in g.keys) g.keys.add(k)
        saveGroups()
        bump()
    }

    /** (выучено, всего) для группы. */
    fun groupStats(g: Group): Pair<Int, Int> {
        val ids = groupIds(g)
        return ids.count { it in progress } to ids.size
    }

    // ---------- серия дней ----------
    private fun touchStreak() {
        val t = today()
        val last = prefs.getLong("lastStudy", -1)
        if (last == t) return
        val s = if (last == t - 1) prefs.getInt("streak", 0) + 1 else 1
        prefs.edit().putLong("lastStudy", t).putInt("streak", s).apply()
    }

    fun streak(): Int {
        val last = prefs.getLong("lastStudy", -1)
        return if (last >= today() - 1) prefs.getInt("streak", 0) else 0
    }

    fun studiedToday(): Boolean = prefs.getLong("lastStudy", -1) == today()

    /** Сколько дней назад занимались (0 — сегодня, 1 — вчера...). Если ни разу — 0. */
    fun daysSinceStudy(): Int {
        val last = prefs.getLong("lastStudy", -1)
        return if (last < 0) 0 else (today() - last).toInt()
    }
}
