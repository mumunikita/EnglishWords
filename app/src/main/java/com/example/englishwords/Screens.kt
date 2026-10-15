package com.example.englishwords

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

// ======================= общие компоненты =======================
@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun ProgressRing(progress: Float, label: String, modifier: Modifier = Modifier, ringSize: Dp = 104.dp) {
    val anim by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "ring")
    Box(modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                Color.White.copy(alpha = 0.25f), -90f, 360f, false,
                topLeft = Offset(inset, inset), size = arc,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            drawArc(
                Color.White, -90f, 360f * anim, false,
                topLeft = Offset(inset, inset), size = arc,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        Text(label, color = Color.White, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun StatTile(modifier: Modifier, title: String, value: String, progress: Float) {
    AppCard(modifier) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
        )
    }
}

private fun timeUntil(due: Long): String {
    val m = due - Srs.nowMin()
    return when {
        m <= 0 -> "сейчас"
        m < 60 -> "через $m мин"
        m < 24 * 60 -> "через ${m / 60} ч"
        else -> "через ${m / (24 * 60)} дн."
    }
}

private val MOTIVATION = listOf(
    "Немного каждый день — и результат",
    "Маленькие шаги ведут к большим победам",
    "Сегодняшние слова — завтрашняя уверенность",
    "Каждое слово — кирпичик твоего английского",
    "10 минут каждый день лучше, чем 3 часа раз в месяц",
    "Ты уже знаешь больше, чем вчера",
    "Практика делает прогресс",
    "Английский любит постоянство",
    "Один урок — и день засчитан",
    "Слова не выучатся сами, но ты справишься",
    "Ошибки — это тоже прогресс",
    "Сегодня отличный день для новых слов",
    "Повторил слова — закрепил надолго",
    "Твоя серия растёт — не прерывай её!",
    "Ещё немного — и ты заговоришь свободно",
    "Английский открывает двери. Начни с одного слова",
    "Терпение и повторение — весь секрет",
    "Каждый день на шаг ближе к цели",
    "Давай позанимаемся — ты молодец, что зашёл",
    "Знание слов — твоя суперсила",
    "Начни с малого и продолжай каждый день",
    "Мозг любит повторение. Дай ему шанс",
    "Ты на шаг впереди себя вчерашнего",
    "Не бойся ошибаться — бойся не пробовать"
)

// ======================= ГЛАВНЫЙ ЭКРАН =======================
@Composable
fun HomeScreen(
    modifier: Modifier, onStart: () -> Unit, onReview: () -> Unit,
    onGroups: () -> Unit, onStudyGroup: (String) -> Unit
) {
    val ver = Repo.version // подписка на изменения
    val batch = Repo.batch()
    val target = Repo.target()
    val doneNew = batch.count { it in Repo.progress }
    val pendingNew = batch.size - doneNew
    val dueAll = Repo.dueCount()
    val due = min(dueAll, Repo.MAX_REVIEWS)
    val needSort = Repo.needsSorting()
    val hasWork = needSort || pendingNew + due > 0
    val waiting = !hasWork && batch.size < target && Repo.hasUntranslatedAhead()
    val total = Repo.words.size
    val seen = Repo.progress.size
    val mastered = Repo.progress.values.count { it.preKnown || it.stage >= Srs.MASTERED_STAGE }
    val active = Repo.activeLevel()
    var phraseIdx by remember { mutableIntStateOf(MOTIVATION.indices.random()) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("English words", style = MaterialTheme.typography.headlineMedium)
                Text(
                    MOTIVATION[phraseIdx],
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { phraseIdx = (phraseIdx + 1) % MOTIVATION.size }
                )
            }
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(Amber.copy(alpha = 0.18f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) { Text("🔥 ${Repo.streak()}", style = MaterialTheme.typography.titleMedium) }
        }

        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(BrandBrush).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    ProgressRing(
                        progress = if (target == 0) 0f else doneNew.toFloat() / target,
                        label = "$doneNew/$target"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Сегодня", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                        Text("Новых слов: $doneNew из $target", color = Color.White.copy(alpha = 0.9f))
                        Text("Повторений ждёт: $due", color = Color.White.copy(alpha = 0.9f))
                    }
                }
                val btnColors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Indigo)
                if (hasWork) {
                    Button(
                        onClick = onStart, colors = btnColors, shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) { Text("Начать урок", style = MaterialTheme.typography.titleMedium) }
                } else if (waiting) {
                    Text("Готовлю переводы слов…", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(
                        Translations.error ?: "При первом запуске нужен интернет: один раз скачается модель перевода.",
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Button(
                        onClick = { Translations.prefetch(force = true) }, colors = btnColors,
                        shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) { Text("Повторить") }
                } else {
                    Text("На сегодня всё 🎉", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    if (Repo.nextCandidate() != null) {
                        Button(
                            onClick = { Repo.learnMore(); onStart() }, colors = btnColors,
                            shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) { Text("Выучить ещё ${Repo.newPerDay} слов") }
                    } else {
                        Text("Вы прошли все слова из списка!", color = Color.White)
                    }
                }
            }
        }

        AppCard(Modifier.fillMaxWidth()) {
            Text("Повторение", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val nextDue = Repo.nextDueMinutes()
            Text(
                when {
                    dueAll > 0 -> "Ждёт повторения: $dueAll"
                    nextDue != null -> "Следующее повторение ${timeUntil(nextDue)}"
                    else -> "Пока нечего повторять"
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 2.dp)
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onReview,
                enabled = Repo.progress.isNotEmpty(),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (dueAll > 0) "Повторить ($due)" else "Тренировка", style = MaterialTheme.typography.titleMedium) }
            if (dueAll == 0 && Repo.progress.isNotEmpty()) {
                Text(
                    "Тренировка не меняет расписание повторений.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        AppCard(Modifier.fillMaxWidth()) {
            Text("Уровень слов", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LEVELS.forEachIndexed { i, name ->
                    FilterChip(selected = i == active, onClick = { Repo.level = i }, label = { Text(name) })
                }
            }
            if (active >= 0) {
                val (d, t) = Repo.levelStats(active)
                Text("${LEVELS[active]}: пройдено или выбрано $d из $t", style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = { if (t == 0) 0f else d.toFloat() / t },
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
                )
            } else {
                Text("Все слова пройдены!")
            }
            Text(
                "Когда слова уровня закончатся, приложение само перейдёт на следующий.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (pendingNew > 0) {
                TextButton(onClick = { Repo.clearPendingBatch() }) {
                    Text("Сбросить выбранные на сегодня слова ($pendingNew)")
                }
            }
            Repo.levelNotice?.let { n ->
                Text(n, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = { Repo.levelNotice = null }) { Text("Ок") }
            }
        }

        AppCard(Modifier.fillMaxWidth()) {
            Text("Мои группы", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (Repo.groups.isEmpty()) {
                Text(
                    "Соберите свою подборку слов и учите её отдельным уроком.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Repo.groups.forEach { g ->
                val (d, t) = Repo.groupStats(g)
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(g.name, style = MaterialTheme.typography.titleMedium)
                        Text("выучено $d из $t", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { onStudyGroup(g.id) }, enabled = t > 0, shape = RoundedCornerShape(16.dp)) {
                        Text("Учить")
                    }
                }
            }
            OutlinedButton(
                onClick = onGroups, shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) { Text(if (Repo.groups.isEmpty()) "＋ Создать группу" else "Управлять группами") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Modifier.weight(1f), "Пройдено", "$seen / $total", seen.toFloat() / total)
            StatTile(Modifier.weight(1f), "Закреплено", "$mastered", mastered.toFloat() / total)
        }
    }
}

// ======================= СТАТИСТИКА =======================
@Composable
fun StatsScreen(modifier: Modifier) {
    val ver = Repo.version
    val total = Repo.words.size
    val all = Repo.progress.values
    val now = Srs.nowMin()
    val unseen = total - all.size
    val known = all.count { it.preKnown }
    val mature = all.count { !it.preKnown && it.stage >= Srs.MASTERED_STAGE }
    val learning = all.count { !it.preKnown && it.stage < Srs.MASTERED_STAGE }
    val dueNow = all.count { it.due <= now }
    val due24 = all.count { it.due > now && it.due <= now + 24 * 60 }
    val dueWeek = all.count { it.due > now + 24 * 60 && it.due <= now + 7 * 24 * 60 }
    val lapses = all.sumOf { it.lapses }
    val mastered = known + mature

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Статистика", style = MaterialTheme.typography.headlineMedium)

        AppCard(Modifier.fillMaxWidth()) {
            Text("Освоено", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$mastered из $total", style = MaterialTheme.typography.headlineSmall)
            LinearProgressIndicator(
                progress = { mastered.toFloat() / total },
                modifier = Modifier.padding(top = 10.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50))
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Modifier.weight(1f), "Учу", "$learning", learning.toFloat() / total)
            StatTile(Modifier.weight(1f), "Закреплено", "$mature", mature.toFloat() / total)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Modifier.weight(1f), "Знаю", "$known", known.toFloat() / total)
            StatTile(Modifier.weight(1f), "Не начато", "$unseen", unseen.toFloat() / total)
        }

        AppCard(Modifier.fillMaxWidth()) {
            Text("По уровням", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LEVELS.forEachIndexed { i, name ->
                    val (d, t) = Repo.levelStats(i)
                    Column {
                        Row(Modifier.fillMaxWidth()) {
                            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            Text("$d / $t", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(
                            progress = { if (t == 0) 0f else d.toFloat() / t },
                            modifier = Modifier.padding(top = 4.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50))
                        )
                    }
                }
            }
        }

        AppCard(Modifier.fillMaxWidth()) {
            Text("Нагрузка повторений", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Прямо сейчас: $dueNow")
                Text("В ближайшие 24 часа: $due24")
                Text("Через 2–7 дней: $dueWeek")
                Text("Ошибок за всё время: $lapses")
                Text("Серия занятий: ${Repo.streak()} дн.")
            }
        }
    }
}

@Composable
private fun HourStepper(hour: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { onChange((hour + 23) % 24) }, shape = RoundedCornerShape(12.dp)) { Text("−") }
        Text("%02d:00".format(hour), Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onChange((hour + 1) % 24) }, shape = RoundedCornerShape(12.dp)) { Text("+") }
    }
}

// ======================= НАСТРОЙКИ =======================
@Composable
fun SettingsScreen(modifier: Modifier) {
    val ver = Repo.version
    val ctx = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)

        AppCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Новых слов в день", Modifier.weight(1f))
                OutlinedButton(onClick = { Repo.newPerDay = (Repo.newPerDay - 1).coerceAtLeast(1) }) { Text("−") }
                Text("${Repo.newPerDay}", Modifier.padding(horizontal = 14.dp), style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = { Repo.newPerDay = (Repo.newPerDay + 1).coerceAtMost(50) }) { Text("+") }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Слова по алфавиту")
                    Text("Выкл. — в случайном порядке", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = Repo.alphabetical, onCheckedChange = { Repo.alphabetical = it })
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Учить фразы")
                    Text("Например: a bit of, look at sb/sth", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = Repo.includePhrases, onCheckedChange = { Repo.includePhrases = it })
            }
        }

        AppCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Напоминания в стиле Duolingo 😈")
                    Text(
                        "О повторениях, новых словах и если вы пропали",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = Repo.reminderOn, onCheckedChange = { on ->
                    Repo.reminderOn = on
                    if (on) {
                        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        Reminder.start(ctx, replace = true)
                    } else Reminder.cancel(ctx)
                })
            }
            if (Repo.reminderOn) {
                Text("Как часто напоминать", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 14.dp))
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(4 to "Редко", 2 to "Обычно", 1 to "Часто").forEach { (h, name) ->
                        FilterChip(
                            selected = Repo.notifyGapHours == h,
                            onClick = { Repo.notifyGapHours = h },
                            label = { Text(name) }
                        )
                    }
                }
                Text(
                    "Редко — раз в 4 часа, обычно — раз в 2 часа, часто — раз в час. " +
                        "О повторениях напомню в нужный момент, пока слово не забылось.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Text("Не беспокоить", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp))
                val quiet = Repo.quietIntervals
                if (quiet.isEmpty()) {
                    Text("Тишина не задана — напоминания приходят в любое время.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                quiet.forEachIndexed { i, q ->
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("С", Modifier.padding(end = 8.dp))
                            HourStepper(q.start) { h -> Repo.quietIntervals = quiet.toMutableList().also { it[i] = q.copy(start = h) } }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { Repo.quietIntervals = quiet.toMutableList().also { it.removeAt(i) } }) {
                                Icon(Icons.Filled.Close, contentDescription = "Удалить период")
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("До", Modifier.padding(end = 8.dp))
                            HourStepper(q.end) { h -> Repo.quietIntervals = quiet.toMutableList().also { it[i] = q.copy(end = h) } }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { Repo.quietIntervals = quiet + Repo.Quiet(13, 14) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) { Text("＋ Добавить время тишины") }
            }
        }

        OutlinedButton(
            onClick = { confirmReset = true },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp)
        ) { Text("Сбросить весь прогресс", color = Danger) }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Сбросить прогресс?") },
            text = { Text("Все изученные слова и статистика будут удалены.") },
            confirmButton = { TextButton(onClick = { Repo.resetAll(); confirmReset = false }) { Text("Сбросить") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } }
        )
    }
}
