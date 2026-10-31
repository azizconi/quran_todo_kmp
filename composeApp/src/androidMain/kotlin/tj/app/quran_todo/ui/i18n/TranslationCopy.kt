package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/**
 * Strings of the choice of the translation, its download as a whole and the search in it (task F4a): the row of the "Aa" sheet, the
 * sheet of editions, the section "Translations" of "Downloads and storage" and the tab "In translation" of the search of the home
 * screen. Texts with values are patterns with `{name}`, `{size}`, `{n}` placeholders. The names of the editions and of their authors
 * are in `TranslationCatalog`, not here.
 *
 * The Tajik, Uzbek and Turkish wording should be checked by a native speaker.
 */
@Immutable
data class TranslationCopy(
    val language: AppLanguage,
    // The "Aa" sheet and the sheet of editions
    /** Pattern with `{name}`: the row of the "Aa" sheet ("Translation: Kuliev"). */
    val rowPattern: String,
    val sheetTitle: String,
    val sheetHint: String,
    val otherLanguages: String,
    val downloadedBadge: String,
    val selected: String,
    /** The mark of an edition in Cyrillic next to a Latin interface ("Cyrillic"). */
    val cyrillicMark: String,
    /** Pattern with `{version}`: the source line of a QuranEnc edition, as its terms ask. */
    val quranEncLine: String,
    /** Shown when the edition on the phone is older than the version the app knows. */
    val updateAvailable: String,
    // "Downloads and storage"
    val downloadsSection: String,
    val downloadsHint: String,
    /** Pattern with `{size}`: the line of an edition that is not on the phone as a whole. */
    val downloadsOffer: String,
    val downloadsComplete: String,
    val downloadsRunning: String,
    /** Pattern with `{name}`: the question of "Delete". */
    val downloadsDeleteBody: String,
    val downloadsFailed: String,
    // The search
    val searchTab: String,
    val searchPlaceholder: String,
    val searchTooShort: String,
    /** Pattern with `{name}`: what is searched ("Search in: Kuliev"). */
    val searchingIn: String,
    /** Pattern with `{size}`. */
    val needDownload: String,
    val download: String,
    val downloading: String,
    val downloadFailed: String,
    val retry: String,
    val loading: String,
    val noResultsTitle: String,
    val noResultsBody: String,
    /** Pattern with `{n}`: "Showing the first 200". */
    val truncated: String,
) {
    private fun String.fill(vararg values: Pair<String, Any>): String {
        var text = this
        values.forEach { (name, value) -> text = text.replace("{$name}", value.toString()) }
        return text
    }

    fun quranEnc(version: String?): String = quranEncLine.fill("version" to (version ?: "1"))

    fun row(name: String): String = rowPattern.fill("name" to name)

    fun deleteBody(name: String): String = downloadsDeleteBody.fill("name" to name)

    fun offer(size: String): String = downloadsOffer.fill("size" to size)

    fun searchIn(name: String): String = searchingIn.fill("name" to name)

    fun needDownload(size: String): String = needDownload.fill("size" to size)

    fun truncatedLabel(n: Int): String = truncated.fill("n" to n)
}

fun translationCopy(language: AppLanguage): TranslationCopy = when (language) {
    AppLanguage.RU -> TranslationCopy(
        language = language,
        rowPattern = "Перевод: {name}",
        sheetTitle = "Перевод",
        sheetHint = "Какой перевод показывать под аятом. Текст Корана не меняется.",
        otherLanguages = "Другие языки",
        downloadedBadge = "Скачан целиком",
        selected = "Выбрано",
        cyrillicMark = "кириллица",
        quranEncLine = "QuranEnc.com · версия {version}",
        updateAvailable = "Есть обновление",
        downloadsSection = "Переводы",
        downloadsHint = "Перевод, скачанный целиком, читается и ищется без интернета.",
        downloadsOffer = "Скачать целиком · ≈ {size}",
        downloadsComplete = "Скачан целиком",
        downloadsRunning = "Скачивается…",
        downloadsDeleteBody = "Перевод «{name}» будет удалён с телефона. Его можно скачать снова.",
        downloadsFailed = "Перевод не скачался",
        searchTab = "В переводе",
        searchPlaceholder = "Слово из перевода",
        searchTooShort = "Введите хотя бы две буквы",
        searchingIn = "Ищем в переводе: {name}",
        needDownload = "Чтобы искать по переводу, скачайте его целиком · ≈ {size}",
        download = "Скачать",
        downloading = "Скачивается…",
        downloadFailed = "Не получилось скачать. Проверьте интернет",
        retry = "Повторить",
        loading = "Открываем перевод…",
        noResultsTitle = "Ничего не нашлось",
        noResultsBody = "Попробуйте другое слово или другой перевод.",
        truncated = "Показаны первые {n}",
    )
    AppLanguage.EN -> TranslationCopy(
        language = language,
        rowPattern = "Translation: {name}",
        sheetTitle = "Translation",
        sheetHint = "Which translation is shown under the ayah. The text of the Quran does not change.",
        otherLanguages = "Other languages",
        downloadedBadge = "Downloaded",
        selected = "Selected",
        cyrillicMark = "Cyrillic",
        quranEncLine = "QuranEnc.com · version {version}",
        updateAvailable = "Update available",
        downloadsSection = "Translations",
        downloadsHint = "A translation that is downloaded whole can be read and searched without the internet.",
        downloadsOffer = "Download whole · ≈ {size}",
        downloadsComplete = "Downloaded",
        downloadsRunning = "Downloading…",
        downloadsDeleteBody = "The translation \"{name}\" will be removed from the phone. You can download it again.",
        downloadsFailed = "The translation did not download",
        searchTab = "In translation",
        searchPlaceholder = "A word from the translation",
        searchTooShort = "Type at least two letters",
        searchingIn = "Searching in: {name}",
        needDownload = "To search the translation, download it whole · ≈ {size}",
        download = "Download",
        downloading = "Downloading…",
        downloadFailed = "Could not download. Check the internet",
        retry = "Retry",
        loading = "Opening the translation…",
        noResultsTitle = "Nothing found",
        noResultsBody = "Try another word or another translation.",
        truncated = "Showing the first {n}",
    )
    AppLanguage.TG -> TranslationCopy(
        language = language,
        rowPattern = "Тарҷума: {name}",
        sheetTitle = "Тарҷума",
        sheetHint = "Кадом тарҷума дар зери оят нишон дода мешавад. Матни Қуръон тағйир намеёбад.",
        otherLanguages = "Забонҳои дигар",
        downloadedBadge = "Пурра боргирӣ шудааст",
        selected = "Интихоб шуд",
        cyrillicMark = "хатти кириллӣ",
        quranEncLine = "QuranEnc.com · нусхаи {version}",
        updateAvailable = "Навсозӣ мавҷуд аст",
        downloadsSection = "Тарҷумаҳо",
        downloadsHint = "Тарҷумаи пурра боргиришуда бе интернет хонда ва ҷустуҷӯ мешавад.",
        downloadsOffer = "Пурра боргирӣ · ≈ {size}",
        downloadsComplete = "Пурра боргирӣ шудааст",
        downloadsRunning = "Боргирӣ мешавад…",
        downloadsDeleteBody = "Тарҷумаи «{name}» аз телефон тоза карда мешавад. Онро дубора боргирӣ кардан мумкин аст.",
        downloadsFailed = "Тарҷума боргирӣ нашуд",
        searchTab = "Дар тарҷума",
        searchPlaceholder = "Калима аз тарҷума",
        searchTooShort = "Ақаллан ду ҳарф нависед",
        searchingIn = "Дар тарҷума ҷустуҷӯ: {name}",
        needDownload = "Барои ҷустуҷӯ дар тарҷума, онро пурра боргирӣ кунед · ≈ {size}",
        download = "Боргирӣ",
        downloading = "Боргирӣ мешавад…",
        downloadFailed = "Боргирӣ нашуд. Интернетро санҷед",
        retry = "Такроран",
        loading = "Тарҷума кушода мешавад…",
        noResultsTitle = "Чизе ёфт нашуд",
        noResultsBody = "Калимаи дигар ё тарҷумаи дигарро кӯшиш кунед.",
        truncated = "{n}-и аввал нишон дода шуд",
    )
    AppLanguage.UZ -> TranslationCopy(
        language = language,
        rowPattern = "Tarjima: {name}",
        sheetTitle = "Tarjima",
        sheetHint = "Oyat ostida qaysi tarjima ko‘rsatiladi. Qur’on matni o‘zgarmaydi.",
        otherLanguages = "Boshqa tillar",
        downloadedBadge = "To‘liq yuklangan",
        selected = "Tanlangan",
        cyrillicMark = "kirill yozuvi",
        quranEncLine = "QuranEnc.com · {version}-versiya",
        updateAvailable = "Yangilanish bor",
        downloadsSection = "Tarjimalar",
        downloadsHint = "To‘liq yuklangan tarjimani internetsiz o‘qish va qidirish mumkin.",
        downloadsOffer = "To‘liq yuklab olish · ≈ {size}",
        downloadsComplete = "To‘liq yuklangan",
        downloadsRunning = "Yuklanmoqda…",
        downloadsDeleteBody = "«{name}» tarjimasi telefondan o‘chiriladi. Uni qayta yuklab olish mumkin.",
        downloadsFailed = "Tarjima yuklanmadi",
        searchTab = "Tarjimada",
        searchPlaceholder = "Tarjimadan so‘z",
        searchTooShort = "Kamida ikki harf kiriting",
        searchingIn = "Tarjimada qidiryapmiz: {name}",
        needDownload = "Tarjimadan qidirish uchun uni to‘liq yuklab oling · ≈ {size}",
        download = "Yuklab olish",
        downloading = "Yuklanmoqda…",
        downloadFailed = "Yuklab bo‘lmadi. Internetni tekshiring",
        retry = "Qayta urinish",
        loading = "Tarjima ochilmoqda…",
        noResultsTitle = "Hech narsa topilmadi",
        noResultsBody = "Boshqa so‘z yoki boshqa tarjimani sinab ko‘ring.",
        truncated = "Dastlabki {n} ta ko‘rsatildi",
    )
    AppLanguage.TR -> TranslationCopy(
        language = language,
        rowPattern = "Çeviri: {name}",
        sheetTitle = "Çeviri",
        sheetHint = "Ayetin altında hangi çevirinin gösterileceği. Kur’an metni değişmez.",
        otherLanguages = "Diğer diller",
        downloadedBadge = "Tamamı indirildi",
        selected = "Seçili",
        cyrillicMark = "Kiril alfabesi",
        quranEncLine = "QuranEnc.com · sürüm {version}",
        updateAvailable = "Güncelleme var",
        downloadsSection = "Çeviriler",
        downloadsHint = "Tamamı indirilen çeviri internetsiz okunur ve aranır.",
        downloadsOffer = "Tamamını indir · ≈ {size}",
        downloadsComplete = "Tamamı indirildi",
        downloadsRunning = "İndiriliyor…",
        downloadsDeleteBody = "«{name}» çevirisi telefondan silinecek. Tekrar indirebilirsiniz.",
        downloadsFailed = "Çeviri indirilemedi",
        searchTab = "Çeviride",
        searchPlaceholder = "Çeviriden bir kelime",
        searchTooShort = "En az iki harf yazın",
        searchingIn = "Çeviride aranıyor: {name}",
        needDownload = "Çeviride aramak için tamamını indirin · ≈ {size}",
        download = "İndir",
        downloading = "İndiriliyor…",
        downloadFailed = "İndirilemedi. İnterneti kontrol edin",
        retry = "Tekrar dene",
        loading = "Çeviri açılıyor…",
        noResultsTitle = "Hiçbir şey bulunamadı",
        noResultsBody = "Başka bir kelime veya başka bir çeviri deneyin.",
        truncated = "İlk {n} gösteriliyor",
    )
}

@Composable
fun rememberTranslationCopy(language: AppLanguage = LocalAppLanguage.current): TranslationCopy =
    remember(language) { translationCopy(language) }
