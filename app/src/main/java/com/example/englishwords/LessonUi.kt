package com.example.englishwords

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Снимок шага: нужен, чтобы уходящий экран не перерисовывался новыми данными во время анимации. */
private data class Snap(val step: Int, val task: Task, val options: List<Int>)

@Composable
fun LessonScreen(modifier: Modifier, vm: LessonVm, tts: Tts, onExit: () -> Unit, onMore: () -> Unit) {
    if (vm.finished) {
        FinishView(modifier, vm, onExit, onMore)
        return
    }
    if (vm.inMatch) {
        MatchScreen(modifier, vm, tts, onExit)
        return
    }
    val task = vm.current ?: return
    val word = Repo.words[task.wordId]

    // После ответа в письменных заданиях всегда озвучиваем слово; после ошибки — в любых.
    LaunchedEffect(vm.feedback) {
        val f = vm.feedback
        val typing = task.ex == Ex.TYPE || task.ex == Ex.LISTEN_TYPE || task.ex == Ex.ASSEMBLE
        if (f != null && (typing || task.ex == Ex.CHOOSE_EN || !f.correct)) tts.speak(word.en)
    }

    val snap = remember(vm.step) { Snap(vm.step, task, vm.options) }

    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) { Icon(Icons.Filled.Close, contentDescription = "Выйти") }
            LinearProgressIndicator(
                progress = { if (vm.total == 0) 0f else vm.done.toFloat() / vm.total },
                modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50))
            )
        }

        AnimatedContent(
            targetState = snap,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                (fadeIn(tween(250)) + slideInHorizontally(tween(250)) { it / 4 }) togetherWith
                    (fadeOut(tween(150)) + slideOutHorizontally(tween(250)) { -it / 4 })
            },
            label = "step"
        ) { s ->
            val w = Repo.words[s.task.wordId]
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (s.task.ex) {
                    Ex.INTRO -> IntroView(w, tts) { vm.introNext() }
                    Ex.CHOOSE_RU -> ChoiceView(
                        s, "Что означает это слово?", w.en, w.en,
                        s.options.map { Repo.words[it].ru to it }, vm, tts, autoSpeak = true
                    )
                    Ex.CHOOSE_EN -> ChoiceView(
                        s, "Выберите перевод на английский", w.ru, null,
                        s.options.map { Repo.words[it].en to it }, vm, tts, autoSpeak = false,
                        speakOptions = true
                    )
                    Ex.LISTEN_CHOOSE -> ChoiceView(
                        s, "Что вы услышали? Выберите перевод", null, w.en,
                        s.options.map { Repo.words[it].ru to it }, vm, tts, autoSpeak = true
                    )
                    Ex.ASSEMBLE -> AssembleView(s, w, vm)
                    Ex.TYPE -> TypeView(s, "Напишите по-английски", w.ru, null, vm, tts)
                    Ex.LISTEN_TYPE -> TypeView(s, "Напишите то, что услышали", null, w.en, vm, tts)
                }
            }
        }

        FeedbackBar(vm)
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SpeakerButton(text: String, tts: Tts, big: Boolean = false) {
    if (big) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(BrandBrush).clickable { tts.speak(text) },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Произнести",
                tint = Color.White, modifier = Modifier.size(44.dp))
        }
    } else {
        IconButton(onClick = { tts.speak(text) }) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Произнести",
                tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun IntroView(word: Word, tts: Tts, onNext: () -> Unit) {
    LaunchedEffect(word.id) { tts.speak(word.en) }
    Label("Новое слово")
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(BrandBrush).padding(28.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(word.en, style = MaterialTheme.typography.displayMedium, color = Color.White, textAlign = TextAlign.Center)
            IconButton(onClick = { tts.speak(word.en) }) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Произнести", tint = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Text(word.ru, style = MaterialTheme.typography.headlineSmall, color = Color.White.copy(alpha = 0.92f), textAlign = TextAlign.Center)
        }
    }
    Button(
        onClick = onNext, shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) { Text("Понятно", style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun ChoiceView(
    s: Snap, title: String, big: String?, speakText: String?,
    options: List<Pair<String, Int>>, vm: LessonVm, tts: Tts, autoSpeak: Boolean,
    speakOptions: Boolean = false
) {
    LaunchedEffect(s.step) { if (autoSpeak && speakText != null) tts.speak(speakText) }
    Label(title)
    if (big != null) {
        AppCard(Modifier.fillMaxWidth()) {
            Text(big, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
    if (speakText != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            SpeakerButton(speakText, tts, big = big == null)
        }
    }
    val fb = vm.feedback
    val shape = RoundedCornerShape(20.dp)
    options.forEachIndexed { i, (label, id) ->
        val right = fb != null && id == s.task.wordId
        val wrongPick = fb != null && id == vm.chosen && id != s.task.wordId
        val accent: Color? = when { right -> Success; wrongPick -> Danger; else -> null }
        val bg = accent?.copy(alpha = 0.14f) ?: MaterialTheme.colorScheme.surface
        val borderColor = accent ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp)
                .clip(shape).background(bg).border(2.dp, borderColor, shape)
                .clickable { vm.submitChoice(id) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape)
                    .background(accent ?: MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "ABCD"[i].toString(), fontWeight = FontWeight.Bold,
                    color = if (accent != null) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (speakOptions) {
                IconButton(onClick = { tts.speak(label) }) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Произнести",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (right) Icon(Icons.Filled.Check, contentDescription = null, tint = Success)
            if (wrongPick) Icon(Icons.Filled.Close, contentDescription = null, tint = Danger)
        }
    }
}

@Composable
private fun TypeView(s: Snap, title: String, big: String?, speakText: String?, vm: LessonVm, tts: Tts) {
    var text by remember(s.step) { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(s.step) {
        if (speakText != null) tts.speak(speakText)
        runCatching { focus.requestFocus() }
    }
    Label(title)
    if (big != null) {
        AppCard(Modifier.fillMaxWidth()) {
            Text(big, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
    if (speakText != null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            SpeakerButton(speakText, tts, big = true)
        }
    }
    val submit = { if (text.isNotBlank()) vm.submitText(text) }
    OutlinedTextField(
        value = text,
        onValueChange = { if (vm.feedback == null) text = it },
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
        textStyle = MaterialTheme.typography.titleLarge,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { submit() })
    )
    Button(
        onClick = { submit() }, shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) { Text("Проверить", style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun LetterTile(c: Char, filled: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        filled -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    Box(
        Modifier.size(46.dp).clip(shape).background(bg)
            .border(2.dp, if (enabled || filled) MaterialTheme.colorScheme.outline.copy(alpha = 0.35f) else Color.Transparent, shape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (enabled || filled) {
            Text(
                c.uppercase(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge,
                color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssembleView(s: Snap, w: Word, vm: LessonVm) {
    val letters = remember(s.step) { w.en.lowercase().toList().shuffled() }
    var placed by remember(s.step) { mutableStateOf(listOf<Int>()) }
    val locked = vm.feedback != null
    val shape = RoundedCornerShape(20.dp)

    Label("Составьте слово из букв")
    AppCard(Modifier.fillMaxWidth()) {
        Text(w.ru, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape)
            .padding(10.dp)
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            placed.forEach { idx ->
                LetterTile(letters[idx], filled = true) { if (!locked) placed = placed - idx }
            }
        }
    }
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        letters.forEachIndexed { i, c ->
            LetterTile(c, filled = false, enabled = i !in placed && !locked) { placed = placed + i }
        }
    }
    Button(
        onClick = { vm.submitText(placed.joinToString("") { letters[it].toString() }) },
        enabled = placed.size == letters.size && !locked,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) { Text("Проверить", style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun MatchCell(text: String, side: Int, id: Int, vm: LessonVm, tts: Tts) {
    val matched = if (side == 0) id in vm.matchedL else id in vm.matchedR
    val isSel = vm.selected == (side to id)
    val isFlash = vm.flash?.let { if (side == 0) it.first == id else it.second == id } == true
    val accent: Color? = when {
        matched -> Success
        isFlash -> Danger
        isSel -> MaterialTheme.colorScheme.primary
        else -> null
    }
    val shape = RoundedCornerShape(16.dp)
    val bg = accent?.copy(alpha = 0.16f) ?: MaterialTheme.colorScheme.surface
    val borderColor = accent ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    Box(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clip(shape).background(bg).border(2.dp, borderColor, shape)
            .clickable(enabled = !matched) {
                if (side == 0) tts.speak(text)
                vm.tapMatch(side, id)
            }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
            color = if (matched) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MatchScreen(modifier: Modifier, vm: LessonVm, tts: Tts, onExit: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) { Icon(Icons.Filled.Close, contentDescription = "Выйти") }
            LinearProgressIndicator(
                progress = { if (vm.total == 0) 0f else vm.done.toFloat() / vm.total },
                modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50))
            )
        }
        Label("Соедините слова и переводы")
        Text(
            "Нажмите слово, затем его пару. Можно начинать с любой колонки.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.leftIds.forEach { id -> MatchCell(Repo.words[id].en, 0, id, vm, tts) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vm.rightIds.forEach { id -> MatchCell(Repo.words[id].ru, 1, id, vm, tts) }
            }
        }
        if (vm.matchedL.size == vm.leftIds.size && vm.leftIds.isNotEmpty()) {
            Text("Все пары найдены! 🎉", style = MaterialTheme.typography.titleMedium, color = Success,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun FeedbackBar(vm: LessonVm) {
    val fb = vm.feedback ?: return
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (fb.correct) Success else Danger)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (fb.correct) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Верно!", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Неверно — повторим", color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
                Text("Правильно: ${fb.text}", color = Color.White, style = MaterialTheme.typography.titleMedium)
                if (!fb.yours.isNullOrBlank()) Text("Ваш ответ: ${fb.yours}", color = Color.White)
                OutlinedButton(
                    onClick = { vm.next() },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) { Text("Дальше") }
            }
        }
    }
}

@Composable
private fun FinishView(modifier: Modifier, vm: LessonVm, onExit: () -> Unit, onMore: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(104.dp).clip(CircleShape).background(BrandBrush), contentAlignment = Alignment.Center) {
            Text("🎉", style = MaterialTheme.typography.displayMedium)
        }
        Text("Урок завершён", style = MaterialTheme.typography.headlineMedium)
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Новых слов: ${vm.newDone}")
                Text("Повторено: ${vm.reviewDone}")
                Text("Верных ответов: ${vm.rightCount}, ошибок: ${vm.wrongCount}")
                Text("🔥 Серия: ${Repo.streak()} дн.")
                if (vm.isPractice) {
                    Text("Это была тренировка: расписание повторений не изменилось.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Button(
            onClick = onExit, shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("На главную", style = MaterialTheme.typography.titleMedium) }
        val groupMode = vm.lessonMode == LessonMode.GROUP
        if (if (groupMode) Repo.groupNewWords().isNotEmpty() else Repo.nextCandidate() != null) {
            OutlinedButton(
                onClick = onMore, shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text(if (groupMode) "Ещё слова из группы" else "Выучить ещё ${Repo.newPerDay} слов") }
        }
    }
}
