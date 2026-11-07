package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/**
 * Strings of the similar ayahs (mutashabihat, task F6) in the five app languages: the mark by an ayah, the row of the ayah sheet, the
 * sheet "Similar verses", the line of the lesson, the switch of the "Aa" sheet and the question "Tell apart" of the quiz. Texts with
 * numbers and names are patterns with `{n}`, `{times}`, `{surah}`, `{place}`, `{a}` and `{pairs}` placeholders filled by the functions
 * below, so each language keeps its own word order. No star and no rub el hizb sign anywhere: the mark is two rings (`SimilarIcon`).
 */
@Immutable
data class SimilarCopy(
    val language: AppLanguage,

    // The mark, the row of the ayah sheet and the switch of the "Aa" sheet
    val markDescription: String,
    val sheetRowPattern: String,
    val switchTitle: String,
    val switchHint: String,

    // The sheet
    val title: String,
    val legend: String,
    val thisAyah: String,
    val learned: String,
    val repeatsPattern: String,
    val repeatsInSurahPattern: String,
    val places: String,
    val tellApart: String,
    val tellApartNeedsTwo: String,
    val openInReader: String,

    // The lesson
    val lessonHintPattern: String,

    // The question "Tell apart"
    val drillTitle: String,
    val drillHint: String,
    val placePattern: String,
    val knewApart: String,
    val mixedUp: String,
    val differencesNote: String,
    val entryPattern: String,
    val emptyTitle: String,
    val emptyBody: String,
) {
    private fun String.fill(vararg values: Pair<String, Any>): String =
        values.fold(this) { text, (key, value) -> text.replace("{$key}", value.toString()) }

    /** "Similar (3)": the row of the ayah sheet. */
    fun sheetRow(count: Int): String = sheetRowPattern.fill("n" to count)

    /** "Repeated 31 times in Ar-Rahman". */
    fun repeatsInSurah(count: Int, surahName: String): String =
        repeatsInSurahPattern.fill("times" to timesLabel(count, language), "surah" to surahName)

    /** "Repeated 3 times": a refrain that is in several surahs. */
    fun repeats(count: Int): String = repeatsPattern.fill("times" to timesLabel(count, language))

    /** "Similar to Al-A'raf 19 - compare": the line under the new ayah of the lesson. */
    fun lessonHint(place: String): String = lessonHintPattern.fill("place" to place)

    /** "Place 2": the label of a place of the question "Tell apart". */
    fun place(number: Int): String = placePattern.fill("a" to number)

    /** "Tell apart similar verses - 3 pairs". */
    fun entry(pairs: Int): String = entryPattern.fill("pairs" to pairCountLabel(pairs, language))
}

@Composable
fun rememberSimilarCopy(language: AppLanguage = LocalAppLanguage.current): SimilarCopy = remember(language) { similarCopy(language) }

/** "31 times"; Russian 1 раз / 2 раза / 5 раз; Tajik, Uzbek and Turkish do not inflect after a number. */
fun timesLabel(n: Int, language: AppLanguage): String = when (language) {
    AppLanguage.RU -> "$n ${russianPlural(n, "раз", "раза", "раз")}"
    AppLanguage.EN -> if (n == 1) "once" else "$n times"
    AppLanguage.TG -> "$n маротиба"
    AppLanguage.UZ -> "$n marta"
    AppLanguage.TR -> "$n kez"
}

/** "1 pair", "3 pairs"; Russian 1 пара / 2 пары / 5 пар. */
fun pairCountLabel(n: Int, language: AppLanguage): String = when (language) {
    AppLanguage.RU -> "$n ${russianPlural(n, "пара", "пары", "пар")}"
    AppLanguage.EN -> "$n ${if (n == 1) "pair" else "pairs"}"
    AppLanguage.TG -> "$n ҷуфт"
    AppLanguage.UZ -> "$n juft"
    AppLanguage.TR -> "$n çift"
}

fun similarCopy(language: AppLanguage): SimilarCopy = when (language) {
    AppLanguage.RU -> SimilarCopy(
        language = language,
        markDescription = "Есть похожие аяты",
        sheetRowPattern = "Похожие ({n})",
        switchTitle = "Метки похожих аятов",
        switchHint = "Значок у аята и строка в уроке, если он похож на уже выученный.",
        title = "Похожие аяты",
        legend = "Места, где слова почти совпадают (муташабихат). Отличия выделены цветом.",
        thisAyah = "Этот аят",
        learned = "выучен",
        repeatsPattern = "Повторяется {times}",
        repeatsInSurahPattern = "Повторяется {times} в суре {surah}",
        places = "Все места",
        tellApart = "Различить",
        tellApartNeedsTwo = "Чтобы различать, выучите хотя бы два места.",
        openInReader = "Открыть в ридере",
        lessonHintPattern = "Похож на {place} — сравнить",
        drillTitle = "Различи похожие",
        drillHint = "Вспомни оба аята по общему началу. Потом сравни.",
        placePattern = "Место {a}",
        knewApart = "Различил",
        mixedUp = "Перепутал",
        differencesNote = "Отличия выделены цветом.",
        entryPattern = "Различи похожие · {pairs}",
        emptyTitle = "Пока нечего различать",
        emptyBody = "Когда выучены оба места похожих аятов, они появятся здесь.",
    )
    AppLanguage.EN -> SimilarCopy(
        language = language,
        markDescription = "Has similar verses",
        sheetRowPattern = "Similar ({n})",
        switchTitle = "Similar verse marks",
        switchHint = "A mark by the verse, and a line in the lesson when it looks like one you know.",
        title = "Similar verses",
        legend = "Places where the words almost match (mutashabihat). The differences are highlighted.",
        thisAyah = "This verse",
        learned = "learned",
        repeatsPattern = "Repeated {times}",
        repeatsInSurahPattern = "Repeated {times} in {surah}",
        places = "All places",
        tellApart = "Tell apart",
        tellApartNeedsTwo = "Memorize at least two of the places to tell them apart.",
        openInReader = "Open in the reader",
        lessonHintPattern = "Similar to {place} — compare",
        drillTitle = "Tell similar verses apart",
        drillHint = "Recall both verses from their common start, then compare.",
        placePattern = "Place {a}",
        knewApart = "Told apart",
        mixedUp = "Mixed up",
        differencesNote = "The differences are highlighted.",
        entryPattern = "Tell apart · {pairs}",
        emptyTitle = "Nothing to tell apart yet",
        emptyBody = "Once both places of similar verses are memorized, they appear here.",
    )
    AppLanguage.TG -> SimilarCopy(
        language = language,
        markDescription = "Оятҳои монанд дорад",
        sheetRowPattern = "Монандҳо ({n})",
        switchTitle = "Нишонаи оятҳои монанд",
        switchHint = "Нишона дар оят ва сатр дар дарс, агар он ба оятҳои ёдгирифташуда монанд бошад.",
        title = "Оятҳои монанд",
        legend = "Ҷойҳое, ки калимаҳояшон қариб баробаранд (муташобиҳот). Фарқҳо бо ранг нишон дода шудаанд.",
        thisAyah = "Ин оят",
        learned = "ёд гирифта шудааст",
        repeatsPattern = "{times} такрор мешавад",
        repeatsInSurahPattern = "Дар сураи {surah} {times} такрор мешавад",
        places = "Ҳамаи ҷойҳо",
        tellApart = "Фарқ кардан",
        tellApartNeedsTwo = "Барои фарқ кардан ақаллан ду ҷойро ёд гиред.",
        openInReader = "Дар хонанда кушодан",
        lessonHintPattern = "Ба {place} монанд аст — муқоиса кунед",
        drillTitle = "Монандҳоро фарқ кун",
        drillHint = "Ҳар ду оятро аз оғози умумӣ ба ёд биёр. Сипас муқоиса кун.",
        placePattern = "Ҷои {a}",
        knewApart = "Фарқ кардам",
        mixedUp = "Омехта кардам",
        differencesNote = "Фарқҳо бо ранг нишон дода шудаанд.",
        entryPattern = "Монандҳоро фарқ кун · {pairs}",
        emptyTitle = "Ҳоло чизе барои фарқ кардан нест",
        emptyBody = "Вақте ҳар ду ҷойи оятҳои монанд ёд гирифта шаванд, дар ин ҷо пайдо мешаванд.",
    )
    AppLanguage.UZ -> SimilarCopy(
        language = language,
        markDescription = "O‘xshash oyatlari bor",
        sheetRowPattern = "O‘xshashlari ({n})",
        switchTitle = "O‘xshash oyatlar belgisi",
        switchHint = "Oyat yonidagi belgi va darsdagi satr: oyat yodlangan oyatga o‘xshasa.",
        title = "O‘xshash oyatlar",
        legend = "So‘zlari deyarli mos keladigan o‘rinlar (mutashobihot). Farqlar rang bilan ajratilgan.",
        thisAyah = "Bu oyat",
        learned = "yodlangan",
        repeatsPattern = "{times} takrorlanadi",
        repeatsInSurahPattern = "{surah} surasida {times} takrorlanadi",
        places = "Barcha o‘rinlar",
        tellApart = "Farqlash",
        tellApartNeedsTwo = "Farqlash uchun kamida ikkita o‘rinni yodlang.",
        openInReader = "O‘qish ekranida ochish",
        lessonHintPattern = "{place} oyatiga o‘xshash — solishtiring",
        drillTitle = "O‘xshashlarini farqlang",
        drillHint = "Ikkala oyatni umumiy boshlanishiga qarab eslang. Keyin solishtiring.",
        placePattern = "{a}-o‘rin",
        knewApart = "Farqladim",
        mixedUp = "Adashtirdim",
        differencesNote = "Farqlar rang bilan ajratilgan.",
        entryPattern = "O‘xshashlarni farqlang · {pairs}",
        emptyTitle = "Hozircha farqlaydigan narsa yo‘q",
        emptyBody = "O‘xshash oyatlarning ikkala o‘rni ham yodlanganda, ular shu yerda paydo bo‘ladi.",
    )
    AppLanguage.TR -> SimilarCopy(
        language = language,
        markDescription = "Benzer ayetleri var",
        sheetRowPattern = "Benzerleri ({n})",
        switchTitle = "Benzer ayet işaretleri",
        switchHint = "Ayetin yanında bir işaret ve derste, ezberlediğin bir ayete benziyorsa bir satır.",
        title = "Benzer ayetler",
        legend = "Kelimeleri neredeyse aynı olan yerler (müteşabihat). Farklar renkle gösterilir.",
        thisAyah = "Bu ayet",
        learned = "ezberlendi",
        repeatsPattern = "{times} tekrarlanır",
        repeatsInSurahPattern = "{surah} suresinde {times} tekrarlanır",
        places = "Tüm yerler",
        tellApart = "Ayır",
        tellApartNeedsTwo = "Ayırmak için en az iki yeri ezberle.",
        openInReader = "Okuyucuda aç",
        lessonHintPattern = "{place} ayetine benziyor — karşılaştır",
        drillTitle = "Benzerleri ayırt et",
        drillHint = "İki ayeti de ortak başlangıçtan hatırla. Sonra karşılaştır.",
        placePattern = "{a}. yer",
        knewApart = "Ayırt ettim",
        mixedUp = "Karıştırdım",
        differencesNote = "Farklar renkle gösterilir.",
        entryPattern = "Benzerleri ayırt et · {pairs}",
        emptyTitle = "Henüz ayırt edilecek bir şey yok",
        emptyBody = "Benzer ayetlerin iki yeri de ezberlenince burada görünür.",
    )
}
