package com.example.englishwords

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Мои группы слов: список групп или (если [openId] задан) содержимое одной группы. */
@Composable
fun GroupsScreen(
    modifier: Modifier, tts: Tts, openId: String?,
    onOpen: (String?) -> Unit, onBack: () -> Unit, onStudy: (String) -> Unit
) {
    val ver = Repo.version
    val group = Repo.groups.find { it.id == openId }
    if (openId != null && group == null) {
        LaunchedEffect(openId) { onOpen(null) }
        return
    }
    if (group == null) GroupList(modifier, onOpen, onBack, onStudy)
    else GroupDetail(modifier, tts, group, onOpen, onStudy)
}

@Composable
private fun GroupList(modifier: Modifier, onOpen: (String?) -> Unit, onBack: () -> Unit, onStudy: (String) -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TopBar("Мои группы", onBack)
        Text(
            "Соберите свою подборку слов: добавляйте слова из словаря кнопкой «+» или записывайте свои. " +
                "Кнопка «Учить» запускает отдельный урок только из слов этой группы.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (Repo.groups.isEmpty()) {
                item { Text("Групп пока нет.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(Repo.groups, key = { it.id }) { g ->
                val (d, t) = Repo.groupStats(g)
                AppCard(Modifier.fillMaxWidth().clickable { onOpen(g.id) }) {
                    Text(g.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$t слов · выучено $d",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onStudy(g.id) },
                            enabled = t > 0,
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("Учить") }
                        OutlinedButton(onClick = { onOpen(g.id) }, shape = RoundedCornerShape(16.dp)) { Text("Открыть") }
                    }
                }
            }
        }
        Button(
            onClick = { showCreate = true },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("Новая группа") }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Новая группа") },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Название") }, singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        val g = Repo.createGroup(name)
                        name = ""
                        showCreate = false
                        onOpen(g.id)
                    }
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun GroupDetail(
    modifier: Modifier, tts: Tts, group: Repo.Group,
    onOpen: (String?) -> Unit, onStudy: (String) -> Unit
) {
    var en by remember { mutableStateOf("") }
    var ru by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var resetProgress by remember { mutableStateOf(false) }
    val ids = Repo.groupIds(group)

    Column(modifier.fillMaxSize().padding(16.dp)) {
        TopBar(group.name, onBack = { onOpen(null) })
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onStudy(group.id) },
                        enabled = ids.isNotEmpty(),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Учить группу") }
                    OutlinedButton(onClick = { confirmDelete = true }, shape = RoundedCornerShape(16.dp)) {
                        Text("Удалить", color = Danger)
                    }
                }
            }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Text("Добавить своё слово", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = en, onValueChange = { en = it },
                        label = { Text("Слово или фраза по-английски") }, singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = ru, onValueChange = { ru = it },
                        label = { Text("Перевод (можно пусто — переведу сам)") }, singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    Button(
                        onClick = {
                            if (en.isNotBlank()) {
                                Repo.addWordToGroup(group, en, ru)
                                en = ""; ru = ""
                                Translations.prefetch(force = true)
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(top = 10.dp)
                    ) { Text("Добавить") }
                    Text(
                        "Слова из словаря добавляются кнопкой «+» в разделе «Словарь».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            item {
                val missing = Repo.groupMissingTranslations(group)
                Text("Слов в группе: ${ids.size}", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp))
                if (missing > 0) {
                    Text(
                        "Переводятся: $missing — такие слова появятся в уроке, когда перевод будет готов.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(ids, key = { it }) { id ->
                val w = Repo.words[id]
                AppCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(w.en, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (w.ru.isNotBlank()) w.ru else "перевод загружается…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(w.levelLabel, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline)
                        }
                        IconButton(onClick = { tts.speak(w.en) }) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Произнести")
                        }
                        IconButton(onClick = { Repo.toggleInGroup(group, id) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Убрать из группы")
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить группу?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Слова из общего словаря останутся в словаре. Слова, которые вы написали сами " +
                            "и которых нет в других группах, будут удалены. Невыученные слова уйдут из списка на сегодня."
                    )
                    Row(
                        Modifier.fillMaxWidth().clickable { resetProgress = !resetProgress },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = resetProgress, onCheckedChange = { resetProgress = it })
                        Text("Также сбросить изучение слов этой группы")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    Repo.deleteGroup(group.id, resetProgress)
                    onOpen(null)
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } }
        )
    }
}
