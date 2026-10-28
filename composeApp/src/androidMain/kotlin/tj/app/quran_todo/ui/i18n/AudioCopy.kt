package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.audio.Reciter
import tj.app.quran_todo.common.audio.Reciters
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/** Strings of the sound settings: the reciter sheet and the audio storage row. */
data class AudioCopy(
    val reciterSheetTitle: String,
    val reciterSheetHint: String,
    val playSample: String,
    val stopSample: String,
    val sampleFailed: String,
    val alafasyStyle: String,
    val husaryStyle: String,
    val minshawiStyle: String,
    val abdulBasitStyle: String,
    val audioOnDevice: String,
    val audioClear: String,
    val audioClearTitle: String,
    val audioClearMessage: String,
    val audioClearConfirm: String,
    val audioCleared: String,
    val cancel: String,
    val unitKb: String,
    val unitMb: String,
    val unitGb: String,
    val decimalSeparator: Char,
) {
    /** One line about how the reciter reads: the style and the tempo. */
    fun styleOf(reciter: Reciter): String = when (reciter.id) {
        Reciters.Husary.id -> husaryStyle
        Reciters.Minshawi.id -> minshawiStyle
        Reciters.AbdulBasit.id -> abdulBasitStyle
        else -> alafasyStyle
    }

    /** "230 MB", "1.4 GB", "0 KB": the size of the cached audio in the units of the language. */
    fun sizeLabel(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            bytes < 1024L * 1024L -> "${kb.toInt()} $unitKb"
            mb < 1024 -> "${mb.toInt()} $unitMb"
            else -> "${(gb * 10).toInt() / 10}$decimalSeparator${(gb * 10).toInt() % 10} $unitGb"
        }
    }
}

@Composable
fun rememberAudioCopy(language: AppLanguage = LocalAppLanguage.current): AudioCopy =
    remember(language) { audioCopy(language) }

fun audioCopy(language: AppLanguage): AudioCopy = when (language) {
    AppLanguage.EN -> AudioCopy(
        reciterSheetTitle = "Reciter",
        reciterSheetHint = "The voice that reads the ayahs. Tap ▶ to hear a sample.",
        playSample = "Play sample",
        stopSample = "Stop sample",
        sampleFailed = "No connection",
        alafasyStyle = "Medium tempo, soft voice",
        husaryStyle = "Slow and very clear, good for memorizing",
        minshawiStyle = "Calm and unhurried",
        abdulBasitStyle = "Classic voice, lively tempo",
        audioOnDevice = "Audio on this device",
        audioClear = "Clear",
        audioClearTitle = "Delete the downloaded audio?",
        audioClearMessage = "Ayahs are downloaded again when you play them. Without a connection only ayahs that are downloaded again will play.",
        audioClearConfirm = "Delete",
        audioCleared = "Audio deleted",
        cancel = "Cancel",
        unitKb = "KB", unitMb = "MB", unitGb = "GB", decimalSeparator = '.',
    )
    AppLanguage.RU -> AudioCopy(
        reciterSheetTitle = "Чтец",
        reciterSheetHint = "Голос, которым читаются аяты. Нажмите ▶, чтобы послушать образец.",
        playSample = "Послушать образец",
        stopSample = "Остановить образец",
        sampleFailed = "Нет связи",
        alafasyStyle = "Средний темп, мягкий голос",
        husaryStyle = "Медленно и очень чётко, удобно для заучивания",
        minshawiStyle = "Спокойно и неторопливо",
        abdulBasitStyle = "Классический голос, живой темп",
        audioOnDevice = "Аудио на устройстве",
        audioClear = "Очистить",
        audioClearTitle = "Удалить загруженное аудио?",
        audioClearMessage = "Аяты скачаются снова, когда вы их включите. Без сети будут играть только заново скачанные.",
        audioClearConfirm = "Удалить",
        audioCleared = "Аудио удалено",
        cancel = "Отмена",
        unitKb = "КБ", unitMb = "МБ", unitGb = "ГБ", decimalSeparator = ',',
    )
    AppLanguage.TG -> AudioCopy(
        reciterSheetTitle = "Қорӣ",
        reciterSheetHint = "Овозе, ки оятҳоро мехонад. Барои шунидани намуна ▶-ро пахш кунед.",
        playSample = "Шунидани намуна",
        stopSample = "Қатъи намуна",
        sampleFailed = "Пайваст нест",
        alafasyStyle = "Суръати миёна, овози мулоим",
        husaryStyle = "Суст ва хеле равшан, барои азбар кардан қулай",
        minshawiStyle = "Ором ва оҳиста",
        abdulBasitStyle = "Овози классикӣ, суръати зинда",
        audioOnDevice = "Аудио дар дастгоҳ",
        audioClear = "Тоза кардан",
        audioClearTitle = "Аудиои боргирифташуда нест карда шавад?",
        audioClearMessage = "Оятҳо ҳангоми пахш кардан аз нав бор карда мешаванд. Бе интернет танҳо оятҳои аз нав бор кардашуда садо медиҳанд.",
        audioClearConfirm = "Нест кардан",
        audioCleared = "Аудио нест карда шуд",
        cancel = "Бекор кардан",
        unitKb = "КБ", unitMb = "МБ", unitGb = "ГБ", decimalSeparator = ',',
    )
    AppLanguage.UZ -> AudioCopy(
        reciterSheetTitle = "Qori",
        reciterSheetHint = "Oyatlarni o‘qiydigan ovoz. Namunani eshitish uchun ▶ ni bosing.",
        playSample = "Namunani eshitish",
        stopSample = "Namunani to‘xtatish",
        sampleFailed = "Aloqa yo‘q",
        alafasyStyle = "O‘rtacha tezlik, mayin ovoz",
        husaryStyle = "Sekin va juda aniq, yodlash uchun qulay",
        minshawiStyle = "Sokin va shoshilmasdan",
        abdulBasitStyle = "Klassik ovoz, jonli tezlik",
        audioOnDevice = "Qurilmadagi audio",
        audioClear = "Tozalash",
        audioClearTitle = "Yuklab olingan audio o‘chirilsinmi?",
        audioClearMessage = "Oyatlar ijro etilganda qayta yuklanadi. Aloqasiz faqat qayta yuklab olinganlari eshitiladi.",
        audioClearConfirm = "O‘chirish",
        audioCleared = "Audio o‘chirildi",
        cancel = "Bekor qilish",
        unitKb = "KB", unitMb = "MB", unitGb = "GB", decimalSeparator = ',',
    )
    AppLanguage.TR -> AudioCopy(
        reciterSheetTitle = "Kâri",
        reciterSheetHint = "Ayetleri okuyan ses. Örneği dinlemek için ▶ simgesine dokunun.",
        playSample = "Örneği dinle",
        stopSample = "Örneği durdur",
        sampleFailed = "Bağlantı yok",
        alafasyStyle = "Orta tempo, yumuşak ses",
        husaryStyle = "Yavaş ve çok net, ezberlemek için uygun",
        minshawiStyle = "Sakin ve acele etmeden",
        abdulBasitStyle = "Klasik ses, canlı tempo",
        audioOnDevice = "Cihazdaki ses",
        audioClear = "Temizle",
        audioClearTitle = "İndirilen sesler silinsin mi?",
        audioClearMessage = "Ayetler çalındıkça yeniden indirilir. Bağlantı yokken yalnızca yeniden indirilenler çalar.",
        audioClearConfirm = "Sil",
        audioCleared = "Sesler silindi",
        cancel = "Vazgeç",
        unitKb = "KB", unitMb = "MB", unitGb = "GB", decimalSeparator = ',',
    )
}
