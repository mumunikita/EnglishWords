package com.example.englishwords

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Repo.init(this)
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    App(Modifier.safeDrawingPadding())
                }
            }
        }
    }
}

enum class Screen { HOME, DICT, STATS, SETTINGS, SORT, LESSON, GROUPS }

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Screen.HOME, "Главная", Icons.Filled.Home),
    Tab(Screen.DICT, "Словарь", Icons.AutoMirrored.Filled.MenuBook),
    Tab(Screen.STATS, "Статистика", Icons.Filled.BarChart),
    Tab(Screen.SETTINGS, "Настройки", Icons.Filled.Settings)
)

@Composable
fun App(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val tts = remember { Tts(ctx) }
    DisposableEffect(Unit) { onDispose { tts.shutdown() } }
    LaunchedEffect(Unit) {
        Translations.prefetch()
        if (Repo.reminderOn) Reminder.start(ctx)
    }

    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var lessonKey by rememberSaveable { mutableIntStateOf(0) }
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = screen != Screen.HOME) {
        when {
            screen == Screen.GROUPS && openGroup != null -> openGroup = null
            screen == Screen.GROUPS -> screen = Screen.DICT
            else -> screen = Screen.HOME
        }
    }

    fun startLesson() {
        if (Repo.pendingNew().isEmpty() && Repo.dueCount() == 0) {
            screen = Screen.HOME
        } else {
            lessonKey++
            screen = Screen.LESSON
        }
    }

    fun startGroupLesson(id: String) {
        Repo.lessonGroupId = id
        Repo.lessonMode = LessonMode.GROUP
        if (Repo.groupHasWork()) {
            lessonKey++
            screen = Screen.LESSON
        } else {
            Repo.levelNotice = "В группе пока нет слов, готовых к изучению. Проверьте, что переводы загрузились."
            screen = Screen.HOME
        }
    }

    val showBar = tabs.any { it.screen == screen }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = screen == tab.screen,
                            onClick = { screen = tab.screen },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { inner ->
        val m = Modifier.padding(inner)
        when (screen) {
            Screen.HOME -> HomeScreen(
                m,
                onStart = {
                    Repo.lessonMode = LessonMode.NORMAL
                    if (Repo.needsSorting()) screen = Screen.SORT else startLesson()
                },
                onReview = {
                    Repo.lessonMode = if (Repo.dueCount() > 0) LessonMode.REVIEW else LessonMode.PRACTICE
                    lessonKey++
                    screen = Screen.LESSON
                },
                onGroups = { openGroup = null; screen = Screen.GROUPS },
                onStudyGroup = { startGroupLesson(it) }
            )
            Screen.DICT -> DictionaryScreen(m, tts, onGroups = { openGroup = null; screen = Screen.GROUPS })
            Screen.GROUPS -> GroupsScreen(
                m, tts, openGroup,
                onOpen = { openGroup = it },
                onBack = { if (openGroup != null) openGroup = null else screen = Screen.DICT },
                onStudy = { startGroupLesson(it) }
            )
            Screen.STATS -> StatsScreen(m)
            Screen.SETTINGS -> SettingsScreen(m)
            Screen.SORT -> SortScreen(m, tts, onDone = { startLesson() }, onBack = { screen = Screen.HOME })
            Screen.LESSON -> {
                val vm: LessonVm = viewModel(key = "lesson-$lessonKey")
                LessonScreen(
                    m, vm, tts,
                    onExit = { screen = Screen.HOME },
                    onMore = {
                        if (Repo.lessonMode == LessonMode.GROUP) {
                            startGroupLesson(Repo.lessonGroupId ?: "")
                        } else {
                            Repo.lessonMode = LessonMode.NORMAL
                            Repo.learnMore()
                            if (Repo.needsSorting()) screen = Screen.SORT else screen = Screen.HOME
                        }
                    }
                )
            }
        }
    }
}
