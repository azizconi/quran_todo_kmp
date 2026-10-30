package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.audio.Reciter
import tj.app.quran_todo.common.audio.ReciterGroup
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
    /** The titles of the groups of the reciter list. */
    val groupTeaching: String,
    val groupMurattal: String,
    val groupMujawwad: String,
    /** The line under the title of the group for learning by heart. */
    val groupTeachingHint: String,
    /** One line per reciter ([Reciter.id]): how the reciter reads, the style and the tempo. */
    private val styles: Map<String, String>,
) {
    /** One line about how the reciter reads: the style and the tempo. */
    fun styleOf(reciter: Reciter): String = styles[reciter.id] ?: styles.getValue(Reciters.default.id)

    fun groupTitle(group: ReciterGroup): String = when (group) {
        ReciterGroup.Teaching -> groupTeaching
        ReciterGroup.Murattal -> groupMurattal
        ReciterGroup.Mujawwad -> groupMujawwad
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
        groupTeaching = "For memorizing",
        groupMurattal = "Reading (murattal)",
        groupMujawwad = "Melodic reading (mujawwad)",
        groupTeachingHint = "Slow, with pauses to repeat",
        styles = mapOf(
            Reciters.HusaryMuallim.id to "The teacher: a slow phrase, then a pause for you to repeat it",
            Reciters.Alafasy.id to "Medium tempo, soft voice",
            Reciters.Husary.id to "Slow and very clear, good for memorizing",
            Reciters.Minshawi.id to "Calm and unhurried",
            Reciters.AbdulBasit.id to "Classic voice, lively tempo",
            Reciters.Sudais.id to "Imam of the Sacred Mosque, clear and flowing",
            Reciters.Shatri.id to "Soft and quiet voice",
            Reciters.Rifai.id to "Tender and unhurried",
            Reciters.Shuraym.id to "Imam of the Sacred Mosque, fast and strong",
            Reciters.Tablawi.id to "Calm, in the Egyptian school",
            Reciters.AbdulBasitMujawwad.id to "Melodic, with long drawn-out breaths",
            Reciters.MinshawiMujawwad.id to "Melodic, slow and drawn-out",
        ),
    )
    AppLanguage.RU -> AudioCopy(
        reciterSheetTitle = "Чтец",
        reciterSheetHint = "Голос, которым читаются аяты. Нажмите ▶, чтобы послушать образец.",
        playSample = "Послушать образец",
        stopSample = "Остановить образец",
        sampleFailed = "Нет связи",
        groupTeaching = "Для заучивания",
        groupMurattal = "Чтение (муратталь)",
        groupMujawwad = "Распевное чтение (муджаввад)",
        groupTeachingHint = "Медленно, с паузами для повтора",
        styles = mapOf(
            Reciters.HusaryMuallim.id to "Учитель: медленная фраза, затем пауза, чтобы повторить за ним",
            Reciters.Alafasy.id to "Средний темп, мягкий голос",
            Reciters.Husary.id to "Медленно и очень чётко, удобно для заучивания",
            Reciters.Minshawi.id to "Спокойно и неторопливо",
            Reciters.AbdulBasit.id to "Классический голос, живой темп",
            Reciters.Sudais.id to "Имам Запретной мечети, чётко и плавно",
            Reciters.Shatri.id to "Тихий, мягкий голос",
            Reciters.Rifai.id to "Нежно и неторопливо",
            Reciters.Shuraym.id to "Имам Запретной мечети, быстро и сильно",
            Reciters.Tablawi.id to "Спокойно, египетская школа",
            Reciters.AbdulBasitMujawwad.id to "Распевно, с долгими протяжными дыханиями",
            Reciters.MinshawiMujawwad.id to "Распевно, медленно и протяжно",
        ),
    )
    AppLanguage.TG -> AudioCopy(
        reciterSheetTitle = "Қорӣ",
        reciterSheetHint = "Овозе, ки оятҳоро мехонад. Барои шунидани намуна ▶-ро пахш кунед.",
        playSample = "Шунидани намуна",
        stopSample = "Қатъи намуна",
        sampleFailed = "Пайваст нест",
        groupTeaching = "Барои азбар кардан",
        groupMurattal = "Қироат (муратталь)",
        groupMujawwad = "Қироати оҳангин (муҷаввад)",
        groupTeachingHint = "Суст, бо таваққуф барои такрор",
        styles = mapOf(
            Reciters.HusaryMuallim.id to "Муаллим: ибораи суст, сипас таваққуф барои такрор кардан",
            Reciters.Alafasy.id to "Суръати миёна, овози мулоим",
            Reciters.Husary.id to "Суст ва хеле равшан, барои азбар кардан қулай",
            Reciters.Minshawi.id to "Ором ва оҳиста",
            Reciters.AbdulBasit.id to "Овози классикӣ, суръати зинда",
            Reciters.Sudais.id to "Имоми Масҷидулҳаром, равшан ва равон",
            Reciters.Shatri.id to "Овози нарм ва ором",
            Reciters.Rifai.id to "Мулоим ва оҳиста",
            Reciters.Shuraym.id to "Имоми Масҷидулҳаром, тез ва пурқувват",
            Reciters.Tablawi.id to "Ором, мактаби мисрӣ",
            Reciters.AbdulBasitMujawwad.id to "Оҳангин, бо нафаскашии дароз",
            Reciters.MinshawiMujawwad.id to "Оҳангин, суст ва кашол",
        ),
    )
    AppLanguage.UZ -> AudioCopy(
        reciterSheetTitle = "Qori",
        reciterSheetHint = "Oyatlarni o‘qiydigan ovoz. Namunani eshitish uchun ▶ ni bosing.",
        playSample = "Namunani eshitish",
        stopSample = "Namunani to‘xtatish",
        sampleFailed = "Aloqa yo‘q",
        groupTeaching = "Yodlash uchun",
        groupMurattal = "O‘qish (murattal)",
        groupMujawwad = "Navoli o‘qish (mujavvad)",
        groupTeachingHint = "Sekin, takrorlash uchun pauzalar bilan",
        styles = mapOf(
            Reciters.HusaryMuallim.id to "Muallim: sekin ibora, so‘ng takrorlash uchun pauza",
            Reciters.Alafasy.id to "O‘rtacha tezlik, mayin ovoz",
            Reciters.Husary.id to "Sekin va juda aniq, yodlash uchun qulay",
            Reciters.Minshawi.id to "Sokin va shoshilmasdan",
            Reciters.AbdulBasit.id to "Klassik ovoz, jonli tezlik",
            Reciters.Sudais.id to "Haram imomi, aniq va ravon",
            Reciters.Shatri.id to "Yumshoq, sokin ovoz",
            Reciters.Rifai.id to "Mayin va shoshilmasdan",
            Reciters.Shuraym.id to "Haram imomi, tez va kuchli",
            Reciters.Tablawi.id to "Sokin, Misr maktabi",
            Reciters.AbdulBasitMujawwad.id to "Navoli, uzoq cho‘ziladigan nafas bilan",
            Reciters.MinshawiMujawwad.id to "Navoli, sekin va cho‘ziq",
        ),
    )
    AppLanguage.TR -> AudioCopy(
        reciterSheetTitle = "Kâri",
        reciterSheetHint = "Ayetleri okuyan ses. Örneği dinlemek için ▶ simgesine dokunun.",
        playSample = "Örneği dinle",
        stopSample = "Örneği durdur",
        sampleFailed = "Bağlantı yok",
        groupTeaching = "Ezberlemek için",
        groupMurattal = "Okuyuş (murattal)",
        groupMujawwad = "Makamlı okuyuş (mücevved)",
        groupTeachingHint = "Yavaş, tekrar için duraklamalarla",
        styles = mapOf(
            Reciters.HusaryMuallim.id to "Muallim: yavaş bir ibare, ardından tekrar etmeniz için bir duraklama",
            Reciters.Alafasy.id to "Orta tempo, yumuşak ses",
            Reciters.Husary.id to "Yavaş ve çok net, ezberlemek için uygun",
            Reciters.Minshawi.id to "Sakin ve acele etmeden",
            Reciters.AbdulBasit.id to "Klasik ses, canlı tempo",
            Reciters.Sudais.id to "Mescid-i Harâm imamı, net ve akıcı",
            Reciters.Shatri.id to "Yumuşak, sakin ses",
            Reciters.Rifai.id to "Zarif ve acele etmeden",
            Reciters.Shuraym.id to "Mescid-i Harâm imamı, hızlı ve güçlü",
            Reciters.Tablawi.id to "Sakin, Mısır ekolü",
            Reciters.AbdulBasitMujawwad.id to "Makamlı, uzun soluklu",
            Reciters.MinshawiMujawwad.id to "Makamlı, yavaş ve uzatmalı",
        ),
    )
}
