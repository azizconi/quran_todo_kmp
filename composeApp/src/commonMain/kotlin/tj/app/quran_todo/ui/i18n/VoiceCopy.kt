package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/**
 * Strings of "My reading" (task F2a): the button that records the person's own reading of a passage, the sheet with the
 * recordings and the messages about the microphone, in the five app languages. Texts with a number are patterns with `{n}`.
 */
@Immutable
data class VoiceCopy(
    val language: AppLanguage,

    // The entry in "Recall"
    val recordAction: String,
    val myReading: String,
    val myReadingCountFmt: String,

    // The sheet
    val sheetHint: String,
    val emptyList: String,
    val record: String,
    val recordMore: String,
    val listenReciter: String,
    val play: String,
    val pause: String,
    val delete: String,
    val deleted: String,
    val undo: String,

    // Recording
    val recording: String,
    val stop: String,
    val recordingHint: String,
    val loudness: String,
    val startFailed: String,
    val tooShort: String,
    val limitReached: String,

    // No microphone
    val deniedTitle: String,
    val deniedBody: String,
    val openSettings: String,
    val tryAgain: String,
) {
    /** "My reading · 3": the entry once the passage has recordings. */
    fun myReadingCount(n: Int): String = myReadingCountFmt.replace("{n}", n.toString())
}

@Composable
fun rememberVoiceCopy(language: AppLanguage = LocalAppLanguage.current): VoiceCopy = remember(language) { voiceCopy(language) }

fun voiceCopy(language: AppLanguage): VoiceCopy = when (language) {
    AppLanguage.RU -> VoiceCopy(
        language = language,
        recordAction = "Записать своё чтение", myReading = "Моё чтение", myReadingCountFmt = "Моё чтение · {n}",
        sheetHint = "Записи остаются только на этом телефоне: они не попадают в копию и никуда не отправляются.",
        emptyList = "Пока нет записей", record = "Записать", recordMore = "Записать ещё",
        listenReciter = "Послушать чтеца", play = "Слушать запись", pause = "Пауза",
        delete = "Удалить запись", deleted = "Запись удалена", undo = "Отменить",
        recording = "Идёт запись", stop = "Стоп",
        recordingHint = "Читайте отрывок вслух. Запись длится не больше 10 минут.",
        loudness = "Уровень громкости", startFailed = "Не удалось начать запись",
        tooShort = "Запись слишком короткая и не сохранена", limitReached = "Достигнут предел в 10 минут, запись сохранена",
        deniedTitle = "Без микрофона записать не получится",
        deniedBody = "Разрешите доступ к микрофону в настройках телефона.",
        openSettings = "Открыть настройки", tryAgain = "Попробовать снова",
    )
    AppLanguage.EN -> VoiceCopy(
        language = language,
        recordAction = "Record my reading", myReading = "My reading", myReadingCountFmt = "My reading · {n}",
        sheetHint = "Recordings stay on this phone only: they are not in the backup and are never sent anywhere.",
        emptyList = "No recordings yet", record = "Record", recordMore = "Record another",
        listenReciter = "Listen to the reciter", play = "Play recording", pause = "Pause",
        delete = "Delete recording", deleted = "Recording deleted", undo = "Undo",
        recording = "Recording", stop = "Stop",
        recordingHint = "Read the passage aloud. A recording lasts up to 10 minutes.",
        loudness = "Loudness", startFailed = "Could not start recording",
        tooShort = "The recording was too short and was not saved", limitReached = "The 10-minute limit was reached, the recording is saved",
        deniedTitle = "Can't record without the microphone",
        deniedBody = "Allow the microphone in the settings of the phone.",
        openSettings = "Open settings", tryAgain = "Try again",
    )
    AppLanguage.TG -> VoiceCopy(
        language = language,
        recordAction = "Қироати худро сабт кунед", myReading = "Қироати ман", myReadingCountFmt = "Қироати ман · {n}",
        sheetHint = "Сабтҳо танҳо дар ҳамин телефон мемонанд: онҳо ба нусхаи эҳтиётӣ намераванд ва ба ҳеҷ ҷо фиристода намешаванд.",
        emptyList = "Ҳоло сабт нест", record = "Сабт кардан", recordMore = "Боз сабт кардан",
        listenReciter = "Қориро гӯш кардан", play = "Гӯш кардани сабт", pause = "Таваққуф",
        delete = "Нест кардани сабт", deleted = "Сабт нест карда шуд", undo = "Бекор кардан",
        recording = "Сабт идома дорад", stop = "Бас",
        recordingHint = "Порчаро бо овози баланд хонед. Сабт то 10 дақиқа давом мекунад.",
        loudness = "Баландии садо", startFailed = "Сабт оғоз нашуд",
        tooShort = "Сабт хеле кӯтоҳ буд ва нигоҳ дошта нашуд", limitReached = "Ҳади 10 дақиқа расид, сабт нигоҳ дошта шуд",
        deniedTitle = "Бе микрофон сабт кардан намешавад",
        deniedBody = "Дастрасӣ ба микрофонро дар танзимоти телефон иҷозат диҳед.",
        openSettings = "Кушодани танзимот", tryAgain = "Боз кӯшиш кардан",
    )
    AppLanguage.UZ -> VoiceCopy(
        language = language,
        recordAction = "O‘qishimni yozib olish", myReading = "Mening o‘qishim", myReadingCountFmt = "Mening o‘qishim · {n}",
        sheetHint = "Yozuvlar faqat shu telefonda qoladi: ular zaxira nusxaga kirmaydi va hech qayoqqa yuborilmaydi.",
        emptyList = "Hozircha yozuv yo‘q", record = "Yozib olish", recordMore = "Yana yozib olish",
        listenReciter = "Qorini tinglash", play = "Yozuvni tinglash", pause = "Pauza",
        delete = "Yozuvni o‘chirish", deleted = "Yozuv o‘chirildi", undo = "Bekor qilish",
        recording = "Yozilmoqda", stop = "To‘xtatish",
        recordingHint = "Parchani ovoz chiqarib o‘qing. Yozuv 10 daqiqagacha davom etadi.",
        loudness = "Ovoz balandligi", startFailed = "Yozuvni boshlab bo‘lmadi",
        tooShort = "Yozuv juda qisqa edi va saqlanmadi", limitReached = "10 daqiqalik chegaraga yetdi, yozuv saqlandi",
        deniedTitle = "Mikrofonsiz yozib bo‘lmaydi",
        deniedBody = "Telefon sozlamalarida mikrofonga ruxsat bering.",
        openSettings = "Sozlamalarni ochish", tryAgain = "Qayta urinish",
    )
    AppLanguage.TR -> VoiceCopy(
        language = language,
        recordAction = "Okuyuşumu kaydet", myReading = "Okuyuşum", myReadingCountFmt = "Okuyuşum · {n}",
        sheetHint = "Kayıtlar yalnızca bu telefonda kalır: yedeğe girmez ve hiçbir yere gönderilmez.",
        emptyList = "Henüz kayıt yok", record = "Kaydet", recordMore = "Bir kayıt daha",
        listenReciter = "Kâriyi dinle", play = "Kaydı dinle", pause = "Duraklat",
        delete = "Kaydı sil", deleted = "Kayıt silindi", undo = "Geri al",
        recording = "Kaydediliyor", stop = "Durdur",
        recordingHint = "Bölümü sesli okuyun. Bir kayıt en çok 10 dakika sürer.",
        loudness = "Ses seviyesi", startFailed = "Kayıt başlatılamadı",
        tooShort = "Kayıt çok kısaydı ve kaydedilmedi", limitReached = "10 dakikalık sınıra ulaşıldı, kayıt kaydedildi",
        deniedTitle = "Mikrofon olmadan kayıt yapılamaz",
        deniedBody = "Telefonun ayarlarından mikrofona izin verin.",
        openSettings = "Ayarları aç", tryAgain = "Tekrar dene",
    )
}
