package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage
import tj.app.quran_todo.common.tafsir.TafsirSource

/**
 * Strings of the commentary screen and of the word meanings (task F4b) in the five app languages. The commentary and the meanings
 * themselves are not translated here: they come from quran.com in their own language (Russian, English, Arabic; the meanings of the
 * words only in English), and the screen says so.
 */
@Immutable
data class TafsirCopy(
    val language: AppLanguage,

    // The row of the ayah sheet and the screen
    val sheetRow: String,
    val title: String,
    val ayahWord: String,
    val previousAyah: String,
    val nextAyah: String,
    val loading: String,
    val needInternet: String,
    val retry: String,
    val sourceLabel: String,
    val noCommentary: String,

    // The chips: the name of the commentary and the language it is written in
    val chipSaadi: String,
    val chipIbnKathir: String,
    val chipMuyassar: String,
    val chipMokhtasarRu: String,
    val chipMokhtasarEn: String,
    val chipMokhtasarTr: String,
    val chipMokhtasarUz: String,
    val inRussian: String,
    val inEnglish: String,
    val inArabic: String,
    val inTurkish: String,
    val inUzbek: String,
    /** Said of the Uzbek Mukhtasar, which QuranEnc publishes in Cyrillic only and the app shows as it is. */
    val cyrillicNote: String,
    /** The line under a QuranEnc commentary: the work and the publisher. */
    val credit: String,
    val versionWord: String,
    /** "Commentary on ayahs %s": `%s` is "2:1–5". */
    val groupNote: String,

    // The word meanings: the "Aa" sheet and the popup over the word
    val wordsSwitchTitle: String,
    val wordsSwitchHint: String,
    /** "Word {n} of {total} · in English". */
    val wordNumber: String,
    val wordsLoading: String,
    val wordsNeedInternet: String,
    val wordsUnavailable: String,
) {
    fun chip(source: TafsirSource): String = when (source) {
        TafsirSource.Saadi -> chipSaadi
        TafsirSource.IbnKathir -> chipIbnKathir
        TafsirSource.Muyassar -> chipMuyassar
        TafsirSource.MokhtasarRu -> chipMokhtasarRu
        TafsirSource.MokhtasarEn -> chipMokhtasarEn
        TafsirSource.MokhtasarTr -> chipMokhtasarTr
        TafsirSource.MokhtasarUz -> chipMokhtasarUz
    }

    /** "Commentary in Russian": said under the title when the commentary is not in the language of the app. */
    fun writtenIn(source: TafsirSource): String = when (source) {
        TafsirSource.Saadi, TafsirSource.MokhtasarRu -> inRussian
        TafsirSource.IbnKathir, TafsirSource.MokhtasarEn -> inEnglish
        TafsirSource.Muyassar -> inArabic
        TafsirSource.MokhtasarTr -> inTurkish
        TafsirSource.MokhtasarUz -> inUzbek
    }

    /** "Al-Mukhtasar fi Tafsir al-Quran · QuranEnc.com · version 1.0.2": the version only when the source gave one. */
    fun creditLine(version: String?): String = if (version.isNullOrBlank()) credit else "$credit · $versionWord $version"

    fun group(surah: Int, first: Int, last: Int): String = groupNote.replace("%s", "$surah:$first–$last")

    fun word(number: Int, total: Int): String = wordNumber.replace("{n}", "$number").replace("{total}", "$total")
}

@Composable
fun rememberTafsirCopy(language: AppLanguage = LocalAppLanguage.current): TafsirCopy = remember(language) { tafsirCopy(language) }

fun tafsirCopy(language: AppLanguage): TafsirCopy = when (language) {
    AppLanguage.RU -> TafsirCopy(
        language = language,
        sheetRow = "Тафсир",
        title = "Тафсир",
        ayahWord = "аят",
        previousAyah = "Предыдущий аят",
        nextAyah = "Следующий аят",
        loading = "Загружаю толкование…",
        needInternet = "Нужен интернет, чтобы загрузить толкование",
        retry = "Повторить",
        sourceLabel = "Источник толкования",
        noCommentary = "В этом источнике нет толкования этого аята",
        chipSaadi = "ас-Саади · рус.",
        chipIbnKathir = "Ибн Касир · англ.",
        chipMuyassar = "Муяссар · араб.",
        chipMokhtasarRu = "Мухтасар · рус.",
        chipMokhtasarEn = "Мухтасар · англ.",
        chipMokhtasarTr = "Мухтасар · тур.",
        chipMokhtasarUz = "Мухтасар · узб.",
        inRussian = "Толкование на русском",
        inEnglish = "Толкование на английском",
        inArabic = "Толкование на арабском",
        inTurkish = "Толкование на турецком",
        inUzbek = "Толкование на узбекском",
        cyrillicNote = "кириллица",
        credit = "Аль-Мухтасар фи тафсир аль-Куран · QuranEnc.com",
        versionWord = "версия",
        groupNote = "Толкование аятов %s",
        wordsSwitchTitle = "Значения слов (англ.)",
        wordsSwitchHint = "Нажмите на слово в тексте",
        wordNumber = "Слово {n} из {total} · по-английски",
        wordsLoading = "Загружаю значения…",
        wordsNeedInternet = "Нужен интернет один раз для этой суры",
        wordsUnavailable = "Для этого аята значения слов недоступны",
    )
    AppLanguage.EN -> TafsirCopy(
        language = language,
        sheetRow = "Tafsir",
        title = "Tafsir",
        ayahWord = "ayah",
        previousAyah = "Previous ayah",
        nextAyah = "Next ayah",
        loading = "Loading the commentary…",
        needInternet = "An internet connection is needed to load the commentary",
        retry = "Retry",
        sourceLabel = "Commentary source",
        noCommentary = "This source has no commentary on this ayah",
        chipSaadi = "As-Sa'di · Russian",
        chipIbnKathir = "Ibn Kathir · English",
        chipMuyassar = "Al-Muyassar · Arabic",
        chipMokhtasarRu = "Al-Mukhtasar · Russian",
        chipMokhtasarEn = "Al-Mukhtasar · English",
        chipMokhtasarTr = "Al-Mukhtasar · Turkish",
        chipMokhtasarUz = "Al-Mukhtasar · Uzbek",
        inRussian = "Commentary in Russian",
        inEnglish = "Commentary in English",
        inArabic = "Commentary in Arabic",
        inTurkish = "Commentary in Turkish",
        inUzbek = "Commentary in Uzbek",
        cyrillicNote = "Cyrillic script",
        credit = "Al-Mukhtasar fi Tafsir al-Quran · QuranEnc.com",
        versionWord = "version",
        groupNote = "Commentary on ayahs %s",
        wordsSwitchTitle = "Word meanings (English)",
        wordsSwitchHint = "Tap a word in the text",
        wordNumber = "Word {n} of {total} · in English",
        wordsLoading = "Loading the meanings…",
        wordsNeedInternet = "An internet connection is needed once for this surah",
        wordsUnavailable = "Word meanings are not available for this ayah",
    )
    AppLanguage.TG -> TafsirCopy(
        language = language,
        sheetRow = "Тафсир",
        title = "Тафсир",
        ayahWord = "оят",
        previousAyah = "Ояти қаблӣ",
        nextAyah = "Ояти оянда",
        loading = "Тафсир бор карда мешавад…",
        needInternet = "Барои боркунии тафсир интернет лозим аст",
        retry = "Бори дигар",
        sourceLabel = "Сарчашмаи тафсир",
        noCommentary = "Дар ин сарчашма тафсири ин оят нест",
        chipSaadi = "Саъдӣ · рус.",
        chipIbnKathir = "Ибни Касир · англ.",
        chipMuyassar = "Муяссар · араб.",
        chipMokhtasarRu = "Мухтасар · рус.",
        chipMokhtasarEn = "Мухтасар · англ.",
        chipMokhtasarTr = "Мухтасар · тур.",
        chipMokhtasarUz = "Мухтасар · ӯзб.",
        inRussian = "Тафсир ба забони русӣ",
        inEnglish = "Тафсир ба забони англисӣ",
        inArabic = "Тафсир ба забони арабӣ",
        inTurkish = "Тафсир ба забони туркӣ",
        inUzbek = "Тафсир ба забони ӯзбекӣ",
        cyrillicNote = "бо алифбои кириллӣ",
        credit = "Ал-Мухтасар фи тафсир ал-Қуръон · QuranEnc.com",
        versionWord = "нусха",
        groupNote = "Тафсири оятҳои %s",
        wordsSwitchTitle = "Маънои калимаҳо (англисӣ)",
        wordsSwitchHint = "Ба калимаи матн зер кунед",
        wordNumber = "Калимаи {n} аз {total} · бо забони англисӣ",
        wordsLoading = "Маъноҳо бор карда мешаванд…",
        wordsNeedInternet = "Барои ин сура як бор интернет лозим аст",
        wordsUnavailable = "Барои ин оят маънои калимаҳо дастнорас аст",
    )
    AppLanguage.UZ -> TafsirCopy(
        language = language,
        sheetRow = "Tafsir",
        title = "Tafsir",
        ayahWord = "oyat",
        previousAyah = "Oldingi oyat",
        nextAyah = "Keyingi oyat",
        loading = "Tafsir yuklanmoqda…",
        needInternet = "Tafsirni yuklash uchun internet kerak",
        retry = "Qayta urinish",
        sourceLabel = "Tafsir manbasi",
        noCommentary = "Bu manbada ushbu oyat tafsiri yo‘q",
        chipSaadi = "Sa'diy · rus.",
        chipIbnKathir = "Ibn Kasir · ingl.",
        chipMuyassar = "Muyassar · arab.",
        chipMokhtasarRu = "Muxtasar · rus.",
        chipMokhtasarEn = "Muxtasar · ingl.",
        chipMokhtasarTr = "Muxtasar · turk.",
        chipMokhtasarUz = "Muxtasar · o‘zb.",
        inRussian = "Tafsir rus tilida",
        inEnglish = "Tafsir ingliz tilida",
        inArabic = "Tafsir arab tilida",
        inTurkish = "Tafsir turk tilida",
        inUzbek = "Tafsir o‘zbek tilida",
        cyrillicNote = "kirill alifbosida",
        credit = "Al-Muxtasar fi tafsir al-Qur’on · QuranEnc.com",
        versionWord = "versiya",
        groupNote = "%s oyatlar tafsiri",
        wordsSwitchTitle = "So‘zlar ma’nosi (inglizcha)",
        wordsSwitchHint = "Matndagi so‘zga bosing",
        wordNumber = "{n}-so‘z, jami {total} ta · inglizcha",
        wordsLoading = "Ma’nolar yuklanmoqda…",
        wordsNeedInternet = "Bu sura uchun internet bir marta kerak",
        wordsUnavailable = "Bu oyat uchun so‘zlar ma’nosi mavjud emas",
    )
    AppLanguage.TR -> TafsirCopy(
        language = language,
        sheetRow = "Tefsir",
        title = "Tefsir",
        ayahWord = "ayet",
        previousAyah = "Önceki ayet",
        nextAyah = "Sonraki ayet",
        loading = "Tefsir yükleniyor…",
        needInternet = "Tefsiri yüklemek için internet gerekli",
        retry = "Tekrar dene",
        sourceLabel = "Tefsir kaynağı",
        noCommentary = "Bu kaynakta bu ayetin tefsiri yok",
        chipSaadi = "Sa‘dî · Rusça",
        chipIbnKathir = "İbn Kesîr · İngilizce",
        chipMuyassar = "Müyesser · Arapça",
        chipMokhtasarRu = "Muhtasar · Rusça",
        chipMokhtasarEn = "Muhtasar · İngilizce",
        chipMokhtasarTr = "Muhtasar · Türkçe",
        chipMokhtasarUz = "Muhtasar · Özbekçe",
        inRussian = "Tefsir Rusça",
        inEnglish = "Tefsir İngilizce",
        inArabic = "Tefsir Arapça",
        inTurkish = "Tefsir Türkçe",
        inUzbek = "Tefsir Özbekçe",
        cyrillicNote = "Kiril alfabesiyle",
        credit = "el-Muhtasar fî Tefsiri’l-Kur’ân · QuranEnc.com",
        versionWord = "sürüm",
        groupNote = "%s ayetlerinin tefsiri",
        wordsSwitchTitle = "Kelime anlamları (İngilizce)",
        wordsSwitchHint = "Metindeki bir kelimeye dokunun",
        wordNumber = "Kelime {n} / {total} · İngilizce",
        wordsLoading = "Anlamlar yükleniyor…",
        wordsNeedInternet = "Bu sure için bir kez internet gerekli",
        wordsUnavailable = "Bu ayet için kelime anlamları yok",
    )
}
