package com.example.englishwords

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

// статусы: 1 — учу, 2 — закреплено, 3 — «знаю», 4 — не начато
private class Entry(val w: Word, val p: Progress?, val status: Int)

private fun statusOf(w: Word, p: Progress?, batch: Set<Int>): Int = when {
    p != null && p.preKnown -> 3
    p != null && p.stage >= Srs.MASTERED_STAGE -> 2
    p != null -> 1
    w.id in batch -> 1      // выбрано на сегодня, но урок ещё не пройден
    else -> 4
}

private fun dueText(due: Long): String {
    val m = due - Srs.nowMin()
    return when {
        m <= 0 -> "повторить сейчас"
        m < 60 -> "повтор через $m мин"
        m < 24 * 60 -> "повтор через ${m / 60} ч"
        else -> "повтор через ${m / (24 * 60)} дн."
    }
}

/**
 * Словарь: все слова таблицы и ваши слова. По умолчанию показаны те, которые вы учите,
 * причём сверху — те, которые скоро надо повторять.
 */
@Composable
fun DictionaryScreen(modifier: Modifier, tts: Tts, onGroups: () -> Unit) {
    val ver = Repo.version
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }       // 0 учу, 1 закреплено, 2 знаю, 3 не начато, 4 все
    var levelFilter by rememberSaveable { mutableIntStateOf(-1) } // -1 все уровни, 0..5 — A1..C2, 6 — мои слова
    var revealed by remember { mutableStateOf(setOf<Int>()) }
    var addFor by remember { mutableStateOf<Int?>(null) }

    val entries = remember(ver) {
        val batch = Repo.batch().toSet()
        Repo.words.filter { it.kind >= 0 }.map { w ->
            val p = Repo.progress[w.id]
            Entry(w, p, statusOf(w, p, batch))
        }
    }
    val counts = remember(entries) {
        IntArray(5).also { c ->
            c[4] = entries.size
            entries.forEach { c[it.status - 1]++ }
        }
    }
    val levelCounts = remember(entries) {
        IntArray(7).also { c -> entries.forEach { c[if (it.w.kind == 3) 6 else it.w.level]++ } }
    }
    val untranslated = remember(entries) { entries.count { it.w.ru.isBlank() } }

    val q = query.trim().lowercase()
    val shown = remember(entries, filter, levelFilter, q) {
        entries.filter { e ->
            (filter == 4 || e.status == filter + 1) &&
                (levelFilter == -1 ||
                    (levelFilter in 0..5 && e.w.kind != 3 && e.w.level == levelFilter) ||
                    (levelFilter == 6 && e.w.kind == 3)) &&
                (q.isEmpty() || e.w.en.lowercase().contains(q) || e.w.ru.lowercase().contains(q))
        }.let { list ->
            if (filter <= 2) {
                // учу / закреплено / знаю: сверху те, что раньше надо повторять
                list.sortedWith(compareBy<Entry>({ it.p?.due ?: Long.MAX_VALUE }, { it.w.en.lowercase() }))
            } else {
                list.sortedWith(compareBy<Entry>({ if (it.w.kind == 3) 99 else it.w.level }, { it.w.en.lowercase() }))
            }
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Словарь", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Всего слов: ${entries.size} · учу: ${counts[0]}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onGroups, shape = RoundedCornerShape(16.dp)) { Text("Мои группы") }
        }
        Box(Modifier.height(12.dp))

        if (untranslated > 0) {
            AppCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text(
                    "Переводим словарь: готово ${entries.size - untranslated} из ${entries.size}",
                    style = MaterialTheme.typography.titleSmall
                )
                LinearProgressIndicator(
                    progress = { (entries.size - untranslated).toFloat() / entries.size },
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
                )
                if (!Translations.working) {
                    Text(
                        Translations.error ?: "Перевод идёт на телефоне. Для первого запуска нужен интернет (скачается модель ~30 МБ).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Button(
                        onClick = { Translations.prefetch(force = true) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) { Text("Продолжить перевод") }
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("Поиск по слову или переводу") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        )

        // статус
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Учу", "Закреплено", "Знаю", "Не начато", "Все").forEachIndexed { i, name ->
                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text("$name (${counts[i]})") })
            }
        }
        // уровень языка
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = levelFilter == -1, onClick = { levelFilter = -1 }, label = { Text("Все уровни") })
            LEVELS.forEachIndexed { i, name ->
                FilterChip(
                    selected = levelFilter == i,
                    onClick = { levelFilter = i },
                    label = { Text("$name (${levelCounts[i]})") }
                )
            }
            FilterChip(selected = levelFilter == 6, onClick = { levelFilter = 6 }, label = { Text("Мои (${levelCounts[6]})") })
        }

        if (shown.isEmpty()) {
            Text(
                if (filter == 0 && q.isEmpty() && levelFilter == -1) "Здесь появятся слова, которые вы начнёте учить."
                else "Ничего не найдено.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp)
            )
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shown, key = { it.w.id }) { e ->
                    val w = e.w
                    val color = when (e.status) {
                        1 -> Amber
                        2 -> Teal
                        3 -> Indigo
                        else -> MaterialTheme.colorScheme.outline
                    }
                    val label = when (e.status) { 1 -> "Учу"; 2 -> "Закреплено"; 3 -> "Знаю"; else -> "Не начато" }
                    val open = w.id in revealed
                    Row(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { revealed = if (open) revealed - w.id else revealed + w.id }
                            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(w.en, style = MaterialTheme.typography.titleMedium)
                            if (open) {
                                Text(
                                    when {
                                        w.ru.isNotBlank() -> w.ru
                                        Translations.working -> "перевод загружается…"
                                        else -> "перевод ещё не загружен — нужен интернет"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            val inGroups = Repo.groupsOf(w.id).joinToString { "«${it.name}»" }
                            val meta = buildList {
                                add(w.levelLabel)
                                if (w.pos.isNotBlank()) add(w.pos)
                                if (e.p != null) add(dueText(e.p.due))
                                else if (e.status == 1) add("в уроке сегодня")
                                if (inGroups.isNotEmpty()) add("группа: $inGroups")
                            }.joinToString(" · ")
                            Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text(label, style = MaterialTheme.typography.labelMedium, color = color) }
                        IconButton(onClick = { addFor = w.id }) {
                            Icon(Icons.Filled.Add, contentDescription = "Добавить в группу")
                        }
                        IconButton(onClick = { tts.speak(w.en) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Произнести")
                        }
                    }
                }
            }
        }
    }

    addFor?.let { id -> AddToGroupDialog(Repo.words[id]) { addFor = null } }
}

/** Выбор групп, в которые входит слово; можно создать новую группу. */
@Composable
private fun AddToGroupDialog(w: Word, onDismiss: () -> Unit) {
    val ver = Repo.version
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("В группу: ${w.en}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (Repo.groups.isEmpty()) Text("У вас пока нет групп. Создайте первую:")
                Repo.groups.forEach { g ->
                    Row(
                        Modifier.fillMaxWidth().clickable { Repo.toggleInGroup(g, w.id) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = Repo.isInGroup(g, w.id), onCheckedChange = { Repo.toggleInGroup(g, w.id) })
                        Text(g.name)
                    }
                }
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it },
                    label = { Text("Новая группа") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        val g = Repo.createGroup(newName)
                        Repo.toggleInGroup(g, w.id)
                        newName = ""
                    }
                }) { Text("Создать и добавить") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } }
    )
}
