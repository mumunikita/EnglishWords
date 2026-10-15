package com.example.englishwords

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val LearnColor = Success
private val KnowColor = Indigo

/** Выбор слов в виде карточек: вправо — учить, влево — уже знаю. */
@Composable
fun SortScreen(modifier: Modifier, tts: Tts, onDone: () -> Unit, onBack: () -> Unit) {
    val ver = Repo.version
    val cards = Repo.candidates(2)
    val word = cards.firstOrNull()
    val batch = Repo.batch()
    val target = Repo.target()

    // подгружаем переводы для ближайших слов (если их ещё нет)
    LaunchedEffect(word?.id) { Translations.prefetch() }

    if (batch.size >= target) {
        LaunchedEffect(Unit) { onDone() }
        return
    }
    if (word == null) {
        if (Repo.hasUntranslatedAhead()) {
            WaitingView(modifier, onBack)
        } else {
            LaunchedEffect(Unit) { onDone() }
        }
        return
    }
    LaunchedEffect(word.id) { tts.speak(word.en) }

    val density = LocalDensity.current
    val threshold = with(density) { 110.dp.toPx() }
    val fly = with(density) { 700.dp.toPx() }
    val offsetX = remember(word.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var busy by remember(word.id) { mutableStateOf(false) }

    fun finish(dir: Int) {
        if (busy) return
        busy = true
        scope.launch {
            offsetX.animateTo(dir * fly, tween(220))
            if (dir > 0) Repo.addToBatch(word.id) else Repo.markKnown(word.id)
        }
    }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TopBar("Выберите слова", onBack)
        Text(
            "Свайп вправо — учить, влево — уже знаю (проверю через 30 дней). " +
                "Нажмите на карточку, чтобы увидеть перевод.",
            style = MaterialTheme.typography.bodyMedium
        )
        Text("Выбрано для изучения: ${batch.size} / $target", style = MaterialTheme.typography.titleSmall)
        LinearProgressIndicator(progress = { batch.size.toFloat() / target }, modifier = Modifier.fillMaxWidth())

        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (cards.size > 1) {
                // карточка «в стопке» под текущей
                Card(
                    Modifier.fillMaxSize().padding(8.dp).graphicsLayer { scaleX = 0.94f; scaleY = 0.94f },
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {}
            }
            key(word.id) {
                SwipeCard(
                    word = word, tts = tts, offsetX = offsetX, threshold = threshold, enabled = !busy,
                    onRelease = {
                        val x = offsetX.value
                        when {
                            x > threshold -> finish(1)
                            x < -threshold -> finish(-1)
                            else -> scope.launch { offsetX.animateTo(0f, spring()) }
                        }
                    }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { finish(-1) }, shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f).height(56.dp)
            ) { Text("← Знаю", style = MaterialTheme.typography.titleMedium) }
            Button(
                onClick = { finish(1) }, shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f).height(56.dp)
            ) { Text("Учить →", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun SwipeCard(
    word: Word, tts: Tts,
    offsetX: Animatable<Float, AnimationVector1D>,
    threshold: Float, enabled: Boolean, onRelease: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf(false) }
    val p = (offsetX.value / threshold).coerceIn(-1f, 1f)
    val base = MaterialTheme.colorScheme.surface
    val tint = when {
        p > 0 -> lerp(base, LearnColor, p * 0.5f)
        p < 0 -> lerp(base, KnowColor, -p * 0.5f)
        else -> base
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
            .graphicsLayer { rotationZ = offsetX.value / 40f }
            .pointerInput(enabled) {
                if (enabled) {
                    detectHorizontalDragGestures(
                        onDragEnd = onRelease,
                        onDragCancel = onRelease
                    ) { change, amount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + amount) }
                    }
                }
            }
            .clickable { shown = !shown },
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = tint),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.align(Alignment.Center).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(word.en, style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center)
                Text(
                    listOf(word.levelLabel, word.pos).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline
                )
                IconButton(onClick = { tts.speak(word.en) }) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Произнести", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(12.dp))
                if (shown) {
                    Text(word.ru, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                } else {
                    Text("Нажмите, чтобы увидеть перевод", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                "УЧИТЬ", color = LearnColor, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.TopStart).padding(20.dp).alpha(p.coerceAtLeast(0f))
            )
            Text(
                "ЗНАЮ", color = KnowColor, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.align(Alignment.TopEnd).padding(20.dp).alpha((-p).coerceAtLeast(0f))
            )
        }
    }
}

@Composable
private fun WaitingView(modifier: Modifier, onBack: () -> Unit) {
    Column(modifier.fillMaxSize().padding(16.dp)) {
        TopBar("Выберите слова", onBack)
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (Translations.working) CircularProgressIndicator()
            Text("Готовлю переводы слов…", style = MaterialTheme.typography.titleMedium)
            Text(
                Translations.error ?: "При первом запуске нужен интернет: один раз скачается модель перевода (~30 МБ).",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!Translations.working) {
                Button(onClick = { Translations.prefetch(force = true) }, shape = RoundedCornerShape(20.dp)) {
                    Text("Повторить")
                }
            }
        }
    }
}
