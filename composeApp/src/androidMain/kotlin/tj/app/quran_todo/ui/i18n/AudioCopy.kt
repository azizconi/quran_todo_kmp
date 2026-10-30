package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.audio.Reciter
import tj.app.quran_todo.common.audio.Reciters
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/** Strings of the sound settings: the reciter sheet. (The audio on the phone is in "Downloads and storage": `DownloadsCopy`.) */
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
) {
    /** One line about how the reciter reads: the style and the tempo. */
    fun styleOf(reciter: Reciter): String = when (reciter.id) {
        Reciters.Husary.id -> husaryStyle
        Reciters.Minshawi.id -> minshawiStyle
        Reciters.AbdulBasit.id -> abdulBasitStyle
        else -> alafasyStyle
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
    )
}
