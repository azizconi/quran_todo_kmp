package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/**
 * Strings of the daily lesson reminder (task B4) in the five app languages: the "Reminder" section of the settings and the
 * notification itself. Texts with names and numbers are patterns with `{what}`, `{min}`, `{name}` and `{a}` placeholders, so each
 * language keeps its own word order. The repetition count and the minutes use the same words as the lesson card
 * ([LessonCopy.repetitions], [HomeCopy.lessonMinutes]).
 */
@Immutable
data class ReminderCopy(
    val language: AppLanguage,

    // Settings
    val sectionTitle: String,
    val toggleTitle: String,
    val footnote: String,
    val timeTitle: String,
    val timeDialogTitle: String,
    val done: String,
    val cancel: String,
    val blockedTitle: String,
    val blockedBody: String,
    val openSettings: String,
    val deniedToast: String,

    // Notification
    val channelName: String,
    val channelDescription: String,
    val snoozeAction: String,
    val lessonTitle: String,
    val readingTitle: String,
    val todayFmt: String,
    val todayRepetitionsOnlyFmt: String,
    val leftFmt: String,
    val readingFmt: String,
    val readingNoAyahFmt: String,
) {
    /**
     * The text of the lesson notification, for example "Today: An-Naba 1–5 and 3 repetitions · ~12 min".
     *
     * @param newRange the new passage still to learn as "An-Naba 1–5", null when there is none.
     * @param repetitions how many repetitions are still to do.
     * @param minutes the estimate of what is left, at least 1.
     * @param partlyDone something of today's lesson is already done: the text says what is left instead of what is today.
     */
    fun lessonBody(newRange: String?, repetitions: Int, minutes: Int, partlyDone: Boolean): String {
        val lesson = lessonCopy(language)
        val parts = buildList {
            newRange?.let { add(it) }
            if (repetitions > 0) add(lesson.repetitions(repetitions))
        }
        val what = parts.joinToString(" ${lesson.tomorrowAnd} ")
        val min = homeCopy(language).lessonMinutes(minutes.coerceAtLeast(1))
        val pattern = when {
            partlyDone -> leftFmt
            newRange == null -> todayRepetitionsOnlyFmt
            else -> todayFmt
        }
        return pattern.fill("what" to what, "min" to min)
    }

    /** "Continue reading: Al-Kahf, ayah 12"; without an ayah (a position kept by an old version) just the surah. */
    fun readingBody(surahName: String, ayah: Int?): String =
        if (ayah == null) readingNoAyahFmt.fill("name" to surahName) else readingFmt.fill("name" to surahName, "a" to ayah)
}

@Composable
fun rememberReminderCopy(language: AppLanguage = LocalAppLanguage.current): ReminderCopy =
    remember(language) { reminderCopy(language) }

private fun String.fill(vararg values: Pair<String, Any>): String {
    var result = this
    values.forEach { (key, value) -> result = result.replace("{$key}", value.toString()) }
    return result
}

fun reminderCopy(language: AppLanguage): ReminderCopy = when (language) {
    AppLanguage.RU -> ReminderCopy(
        language = language,
        sectionTitle = "Напоминание",
        toggleTitle = "Напоминать об уроке",
        footnote = "Не приходит, если урок уже сделан",
        timeTitle = "Время",
        timeDialogTitle = "Время напоминания",
        done = "Готово",
        cancel = "Отмена",
        blockedTitle = "Уведомления выключены",
        blockedBody = "Включите их для Quran Hifz в настройках телефона, иначе напоминание не придёт.",
        openSettings = "Открыть настройки",
        deniedToast = "Без разрешения напоминание не придёт",
        channelName = "Урок дня",
        channelDescription = "Ежедневное напоминание об уроке заучивания",
        snoozeAction = "Через час",
        lessonTitle = "Урок дня",
        readingTitle = "Quran Hifz",
        todayFmt = "Сегодня: {what} · {min}",
        todayRepetitionsOnlyFmt = "Сегодня {what} · {min}",
        leftFmt = "Осталось: {what} · {min}",
        readingFmt = "Продолжить чтение: {name}, аят {a}",
        readingNoAyahFmt = "Продолжить чтение: {name}",
    )
    AppLanguage.EN -> ReminderCopy(
        language = language,
        sectionTitle = "Reminder",
        toggleTitle = "Remind me about the lesson",
        footnote = "Not sent if today’s lesson is already done",
        timeTitle = "Time",
        timeDialogTitle = "Reminder time",
        done = "Done",
        cancel = "Cancel",
        blockedTitle = "Notifications are off",
        blockedBody = "Turn them on for Quran Hifz in the phone settings, otherwise the reminder will not arrive.",
        openSettings = "Open settings",
        deniedToast = "Without permission the reminder will not arrive",
        channelName = "Today’s lesson",
        channelDescription = "A daily reminder about the memorization lesson",
        snoozeAction = "In an hour",
        lessonTitle = "Today’s lesson",
        readingTitle = "Quran Hifz",
        todayFmt = "Today: {what} · {min}",
        todayRepetitionsOnlyFmt = "Today: {what} · {min}",
        leftFmt = "Left: {what} · {min}",
        readingFmt = "Continue reading: {name}, ayah {a}",
        readingNoAyahFmt = "Continue reading: {name}",
    )
    AppLanguage.TG -> ReminderCopy(
        language = language,
        sectionTitle = "Ёдраскунӣ",
        toggleTitle = "Дар бораи дарс ёдрас кардан",
        footnote = "Агар дарс аллакай анҷом шуда бошад, намеояд",
        timeTitle = "Вақт",
        timeDialogTitle = "Вақти ёдраскунӣ",
        done = "Тайёр",
        cancel = "Бекор",
        blockedTitle = "Огоҳиномаҳо хомӯшанд",
        blockedBody = "Онҳоро барои Quran Hifz дар танзимоти телефон фаъол кунед, вагарна ёдраскунӣ намеояд.",
        openSettings = "Кушодани танзимот",
        deniedToast = "Бе иҷозат ёдраскунӣ намеояд",
        channelName = "Дарси рӯз",
        channelDescription = "Ёдраскунии ҳаррӯза дар бораи дарси ҳифз",
        snoozeAction = "Баъд аз як соат",
        lessonTitle = "Дарси рӯз",
        readingTitle = "Quran Hifz",
        todayFmt = "Имрӯз: {what} · {min}",
        todayRepetitionsOnlyFmt = "Имрӯз: {what} · {min}",
        leftFmt = "Боқӣ мондааст: {what} · {min}",
        readingFmt = "Хонишро идома диҳед: {name}, оят {a}",
        readingNoAyahFmt = "Хонишро идома диҳед: {name}",
    )
    AppLanguage.UZ -> ReminderCopy(
        language = language,
        sectionTitle = "Eslatma",
        toggleTitle = "Dars haqida eslatish",
        footnote = "Dars bajarilgan bo'lsa, kelmaydi",
        timeTitle = "Vaqt",
        timeDialogTitle = "Eslatma vaqti",
        done = "Tayyor",
        cancel = "Bekor qilish",
        blockedTitle = "Bildirishnomalar o'chirilgan",
        blockedBody = "Quran Hifz uchun ularni telefon sozlamalarida yoqing, aks holda eslatma kelmaydi.",
        openSettings = "Sozlamalarni ochish",
        deniedToast = "Ruxsatsiz eslatma kelmaydi",
        channelName = "Kunlik dars",
        channelDescription = "Yodlash darsi haqida har kungi eslatma",
        snoozeAction = "Bir soatdan keyin",
        lessonTitle = "Kunlik dars",
        readingTitle = "Quran Hifz",
        todayFmt = "Bugun: {what} · {min}",
        todayRepetitionsOnlyFmt = "Bugun: {what} · {min}",
        leftFmt = "Qoldi: {what} · {min}",
        readingFmt = "O'qishni davom ettiring: {name}, {a}-oyat",
        readingNoAyahFmt = "O'qishni davom ettiring: {name}",
    )
    AppLanguage.TR -> ReminderCopy(
        language = language,
        sectionTitle = "Hatırlatıcı",
        toggleTitle = "Dersi hatırlat",
        footnote = "Ders zaten yapıldıysa gelmez",
        timeTitle = "Saat",
        timeDialogTitle = "Hatırlatma saati",
        done = "Tamam",
        cancel = "Vazgeç",
        blockedTitle = "Bildirimler kapalı",
        blockedBody = "Quran Hifz için bildirimleri telefon ayarlarından açın, aksi hâlde hatırlatma gelmez.",
        openSettings = "Ayarları aç",
        deniedToast = "İzin olmadan hatırlatma gelmez",
        channelName = "Günün dersi",
        channelDescription = "Ezber dersi için günlük hatırlatma",
        snoozeAction = "1 saat sonra",
        lessonTitle = "Günün dersi",
        readingTitle = "Quran Hifz",
        todayFmt = "Bugün: {what} · {min}",
        todayRepetitionsOnlyFmt = "Bugün: {what} · {min}",
        leftFmt = "Kalan: {what} · {min}",
        readingFmt = "Okumaya devam et: {name}, ayet {a}",
        readingNoAyahFmt = "Okumaya devam et: {name}",
    )
}
