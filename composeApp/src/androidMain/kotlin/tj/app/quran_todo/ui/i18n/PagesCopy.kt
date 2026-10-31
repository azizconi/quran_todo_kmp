package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/**
 * Strings of the mushaf as pages (task F3a): the switch "Text / Pages", the placeholder of a page that is not on the phone, and the
 * "Mushaf pages" section of "Downloads and storage". The buttons, the player and the sheet of an ayah, "Check yourself" and the numbers of
 * the place ("Juz 2 · Hizb 3 · p. 22") reuse the copies of the reader, of the recall and of the mushaf marks. Texts with a number are
 * patterns with `{n}`.
 *
 * The Tajik, Uzbek and Turkish wording should be checked by a native speaker.
 */
@Immutable
data class PagesCopy(
    val language: AppLanguage,
    /** The label of the switch on the pages: "Open as text". */
    val openAsText: String,
    /** The label of the switch in the reader: "Open as pages". */
    val openAsPages: String,
    /** What a page that is neither on the phone nor reachable says. */
    val notLoaded: String,
    val retry: String,
    /** Pattern with `{n}`: what a screen reader says for a page ("Page 3 of 604"). */
    val pageOfPattern: String,
    // "Downloads and storage"
    val downloadsSection: String,
    val downloadsHint: String,
    /** Pattern with `{n}`: the pages of juz [n]. */
    val downloadsJuzTitle: String,
    val downloadsAllTitle: String,
    /** Pattern with `{n}`: how many pages a row holds ("21 p."). */
    val downloadsPagesCount: String,
    /** Pattern with `{n}`: the line of the queue when pages did not come ("3 pages did not download"). */
    val downloadsFailedPages: String,
) {
    private fun String.fill(n: Int): String = replace("{n}", n.toString())

    /** "Page 3 of 604". */
    fun pageOf(page: Int): String = pageOfPattern.fill(page)

    /** "Pages of juz 30". */
    fun juzRowTitle(juz: Int): String = downloadsJuzTitle.fill(juz)

    /** "21 p.". */
    fun pagesCount(n: Int): String = downloadsPagesCount.fill(n)

    /** "3 pages did not download"; empty when none failed. */
    fun failedPages(n: Int): String = if (n <= 0) "" else downloadsFailedPages.fill(n)
}

fun pagesCopy(language: AppLanguage): PagesCopy = when (language) {
    AppLanguage.RU -> PagesCopy(
        language = language,
        openAsText = "Открыть текстом", openAsPages = "Открыть страницами",
        notLoaded = "Страница не загружена. Нужен интернет один раз", retry = "Повторить",
        pageOfPattern = "Страница {n} из 604",
        downloadsSection = "Страницы мусхафа",
        downloadsHint = "Раскладка и шрифты режима «Страницы»: с ними страницы открываются без интернета.",
        downloadsJuzTitle = "Страницы джуза {n}", downloadsAllTitle = "Все 604 страницы", downloadsPagesCount = "{n} стр.",
        downloadsFailedPages = "Не скачано страниц: {n}",
    )
    AppLanguage.EN -> PagesCopy(
        language = language,
        openAsText = "Open as text", openAsPages = "Open as pages",
        notLoaded = "Page not loaded. The internet is needed once", retry = "Retry",
        pageOfPattern = "Page {n} of 604",
        downloadsSection = "Mushaf pages",
        downloadsHint = "The layout and fonts of the \"Pages\" mode: with them the pages open without the internet.",
        downloadsJuzTitle = "Pages of juz {n}", downloadsAllTitle = "All 604 pages", downloadsPagesCount = "{n} pages",
        downloadsFailedPages = "{n} pages did not download",
    )
    AppLanguage.TG -> PagesCopy(
        language = language,
        openAsText = "Бо матн кушодан", openAsPages = "Бо саҳифаҳо кушодан",
        notLoaded = "Саҳифа бор нашудааст. Як бор интернет лозим аст", retry = "Такроран",
        pageOfPattern = "Саҳифаи {n} аз 604",
        downloadsSection = "Саҳифаҳои мусҳаф",
        downloadsHint = "Тарҳбандӣ ва шрифтҳои ҳолати «Саҳифаҳо»: бо онҳо саҳифаҳо бе интернет кушода мешаванд.",
        downloadsJuzTitle = "Саҳифаҳои ҷузъи {n}", downloadsAllTitle = "Ҳамаи 604 саҳифа", downloadsPagesCount = "{n} саҳ.",
        downloadsFailedPages = "{n} саҳифа боргирӣ нашуд",
    )
    AppLanguage.UZ -> PagesCopy(
        language = language,
        openAsText = "Matn ko‘rinishida ochish", openAsPages = "Sahifalar ko‘rinishida ochish",
        notLoaded = "Sahifa yuklanmagan. Bir marta internet kerak", retry = "Qayta urinish",
        pageOfPattern = "604 betdan {n}-bet",
        downloadsSection = "Mushaf sahifalari",
        downloadsHint = "«Sahifalar» rejimining sahifa tuzilishi va shriftlari: ular bilan sahifalar internetsiz ochiladi.",
        downloadsJuzTitle = "{n}-juz sahifalari", downloadsAllTitle = "Barcha 604 sahifa", downloadsPagesCount = "{n} bet",
        downloadsFailedPages = "{n} ta sahifa yuklab olinmadi",
    )
    AppLanguage.TR -> PagesCopy(
        language = language,
        openAsText = "Metin olarak aç", openAsPages = "Sayfalar olarak aç",
        notLoaded = "Sayfa yüklenmedi. Bir kez internet gerekir", retry = "Tekrar dene",
        pageOfPattern = "604 sayfadan {n}. sayfa",
        downloadsSection = "Mushaf sayfaları",
        downloadsHint = "«Sayfalar» kipinin düzeni ve yazı tipleri: bunlarla sayfalar internetsiz açılır.",
        downloadsJuzTitle = "{n}. cüzün sayfaları", downloadsAllTitle = "604 sayfanın tamamı", downloadsPagesCount = "{n} sayfa",
        downloadsFailedPages = "{n} sayfa indirilemedi",
    )
}

@Composable
fun rememberPagesCopy(language: AppLanguage = LocalAppLanguage.current): PagesCopy = remember(language) { pagesCopy(language) }
