package com.example.englishwords

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object Reminder {
    private const val TICK = "tick_reminder"
    private const val DUE = "due_nudge"
    const val MAX_PER_DAY = 12

    /**
     * Включает напоминания: каждые 30 минут приложение проверяет, о чём пора напомнить
     * (повторения, новые слова, пропущенный день), плюс отдельный сигнал к моменту повторения.
     */
    fun start(context: Context, replace: Boolean = false) {
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(30, TimeUnit.MINUTES)
            .setInputData(workDataOf("kind" to "tick"))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TICK,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            req
        )
        scheduleDue(context)
    }

    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(TICK)
        wm.cancelUniqueWork(DUE)
    }

    /** Напоминание ровно к моменту, когда ближайшее слово пора повторять. */
    fun scheduleDue(context: Context) {
        val next = Repo.nextDueMinutes() ?: return
        val delay = maxOf(1L, next - Srs.nowMin())
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MINUTES)
            .setInputData(workDataOf("kind" to "due"))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(DUE, ExistingWorkPolicy.REPLACE, req)
    }
}

// ------------------------------------------------------------------ тексты

private fun wordsRu(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> "слово"
        m10 in 2..4 && m100 !in 12..14 -> "слова"
        else -> "слов"
    }
}

private fun <T> pick(list: List<T>): T = list.random()

/** Если сегодня ещё не занимались. [attempt] — сколько уведомлений уже было сегодня (0, 1, 2+). */
private fun dailyMessage(attempt: Int): Pair<String, String> {
    val gap = Repo.daysSinceStudy()
    val streak = Repo.streak()
    val due = Repo.dueCount()
    val dueTail = if (due > 0) " Ещё $due ${wordsRu(due)} ждут повторения." else ""
    return when {
        gap >= 7 -> pick(listOf(
            "Прошла неделя. 💀" to "Твои слова написали завещание. Один урок — и они воскреснут.",
            "Мы ещё знакомы?" to "Неделя тишины. Английский помнит тебя. Пока что."
        ))
        gap >= 4 -> pick(listOf(
            "Эти уведомления не работают?" to "Тогда придётся тебя найти. 🔍 Шутка. Пока что. Открой приложение.",
            "Ты бросил английский." to "А английский тебя — нет. Он ждёт. И я тоже. 👀"
        ))
        gap >= 3 -> "Три дня без слов 😢" to "Они уже забываются. Скоро забудешь и меня.$dueTail"
        gap >= 2 -> pick(listOf(
            "Тебя не было вчера 👀" to "Слова заметили. Они расстроены. Я тоже, но молча.$dueTail",
            "Серия 🔥 сгорела." to "Вчера ты её потерял. Начни новую, пока я не обиделся."
        ))
        attempt >= 3 -> "Последнее предупреждение ⚠️" to
            (if (streak >= 2) "Серия 🔥$streak сгорит в полночь. Я не шучу. Почти."
            else "Дальше будет хуже. Я умею быть назойливым.")
        attempt >= 1 -> "Ты же не забыл про английский?" to
            (if (streak >= 2) "Серия 🔥$streak дн. в опасности. Один урок её спасёт.$dueTail"
            else "Я просто напоминаю. Пока вежливо.$dueTail")
        else -> pick(listOf(
            "Время английского ⏰" to "10 слов — 5 минут. Не усложняй.$dueTail",
            "Слова скучают" to "Они сидят в телефоне и ждут. Не заставляй их ждать.$dueTail"
        ))
    }
}

/** Пора повторить слова. */
private fun dueMessage(due: Int): Pair<String, String> = pick(listOf(
    "Слова вот-вот забудутся! 🧠" to "$due ${wordsRu(due)} ждут повторения. Сейчас самый эффективный момент. Не упусти.",
    "Тик-так ⏳" to "Повторение $due ${wordsRu(due)} созрело. Забудешь — начнёшь заново. Я не шучу.",
    "Алло, это твоя память 📞" to "Мне нужно повторить $due ${wordsRu(due)}. Это займёт пару минут.",
    "Повторение созрело 🍎" to "$due ${wordsRu(due)} ждут. Сорви, пока не пропало.",
    "Не дай словам убежать 🏃" to "Они уже на полпути к забвению. Повтори $due ${wordsRu(due)} — и они твои."
))

/** Не выучены новые слова на сегодня. */
private fun newWordsMessage(left: Int): Pair<String, String> = pick(listOf(
    "Новые слова ждут 📚" to "Сегодня осталось выучить ещё $left ${wordsRu(left)}. Начнём?",
    "Один урок — и цель дня выполнена" to "Осталось $left ${wordsRu(left)}. Это быстрее, чем кажется.",
    "Сегодня ещё ничего нового?" to "$left ${wordsRu(left)} на сегодня ждут. Не откладывай на завтра.",
    "Слова сами себя не выучат 😏" to "Осталось $left ${wordsRu(left)}. Давай, ты можешь.",
    "Твой словарь хочет расти 🌱" to "Добавь $left ${wordsRu(left)} — и день засчитан."
))

// ------------------------------------------------------------------ показ

private fun canNotify(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun show(ctx: Context, id: Int, msg: Pair<String, String>) {
    val nm = ctx.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(
        NotificationChannel("nudge", "Напоминания", NotificationManager.IMPORTANCE_HIGH)
    )
    val pi = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )
    val n = NotificationCompat.Builder(ctx, "nudge")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(msg.first)
        .setContentText(msg.second)
        .setStyle(NotificationCompat.BigTextStyle().bigText(msg.second))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pi)
        .setAutoCancel(true)
        .build()
    nm.notify(id, n)
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        Repo.init(ctx)
        if (!Repo.reminderOn || !canNotify(ctx)) return Result.success()
        // время тишины и дневной лимит
        if (Repo.isQuietHour(LocalTime.now().hour)) return Result.success()
        if (Repo.notifCountToday() >= Reminder.MAX_PER_DAY) return Result.success()

        val kind = inputData.getString("kind") ?: "tick"
        val now = Srs.nowMin()
        val sinceAny = now - Repo.lastNotif("any")
        val gap = Repo.notifyGapHours * 60L

        // 1) повторения — важнее всего, напоминаем чаще (не реже чем раз в 45 минут)
        val due = Repo.dueCount()
        if (due > 0 && now - Repo.lastNotif("due") >= 45 && sinceAny >= 10) {
            show(ctx, 2, dueMessage(due))
            Repo.markNotified("due")
            return Result.success()
        }
        if (kind == "due") return Result.success()   // одноразовая работа проверяет только повторения

        // 2) сегодня вообще не занимались
        if (!Repo.studiedToday() && sinceAny >= gap) {
            show(ctx, 1, dailyMessage(Repo.notifCountToday()))
            Repo.markNotified("threat")
            return Result.success()
        }
        // 3) занимались, но новые слова на сегодня не доучены
        val left = Repo.newWordsLeft()
        if (left > 0 && sinceAny >= gap && now - Repo.lastNotif("new") >= gap) {
            show(ctx, 3, newWordsMessage(left))
            Repo.markNotified("new")
        }
        return Result.success()
    }
}
