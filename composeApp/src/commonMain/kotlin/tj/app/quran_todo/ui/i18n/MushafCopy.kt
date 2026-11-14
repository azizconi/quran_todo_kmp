package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.database.entity.quran.Sajda
import tj.app.quran_todo.ui.reader.AyahMarks
import tj.app.quran_todo.ui.reader.MushafMarks
import tj.app.quran_todo.ui.reader.MushafPlace

/**
 * Strings of the juz tab of Home and of the mushaf marks of the reader (D1): "Juz 2 · Hizb 3 · p. 22", the quarters
 * of a hizb, the sajda sign and the tab names. Texts with a number are patterns with `{n}` (each language keeps its
 * own word order: "Juz 2", "2-juz", "2. Cüz"); the functions below fill them.
 *
 * Why quarters have patterns of their own: Russian says "¼ хизба 3" (genitive) but "Хизб 3".
 */
@Immutable
data class MushafCopy(
    val language: AppLanguage,
    // Tabs of the home list
    val tabSurahs: String,
    val tabJuz: String,
    val juzSearchPlaceholder: String,
    /** What the empty "Juz" tab says under "Nothing found" when a search finds no juz. */
    val juzNoResultsBody: String,
    // Marks
    val juzPattern: String,
    val hizbPattern: String,
    val hizbQuarterPattern: String,
    val hizbHalfPattern: String,
    val hizbThreeQuartersPattern: String,
    val pagePattern: String,
    // Sajda
    val sajda: String,
    val sajdaObligatory: String,
    val sajdaRecommended: String,
    val sajdaExplain: String,
    /** The spoken action of the sajda sign: "Show what it means". */
    val sajdaWhat: String,
) {
    /** "Juz 2". */
    fun juzLabel(juz: Int): String = juzPattern.fill(juz)

    /** The hizb of a quarter [hizbQuarter] (1..240): "Hizb 3" where it starts, "¼ Hizb 3", "½ Hizb 3", "¾ Hizb 3" at the quarters. */
    fun hizbLabel(hizbQuarter: Int): String {
        val hizb = MushafMarks.hizbOf(hizbQuarter)
        val pattern = when (MushafMarks.quarterOf(hizbQuarter)) {
            0 -> hizbPattern
            1 -> hizbQuarterPattern
            2 -> hizbHalfPattern
            else -> hizbThreeQuartersPattern
        }
        return pattern.fill(hizb)
    }

    /** "p. 22" (the page of the mushaf). */
    fun pageLabel(page: Int): String = pagePattern.fill(page)

    /** "Juz 2 · Hizb 3 · p. 22": where the reader is, with the plain hizb (no quarter). */
    fun placeText(place: MushafPlace): String =
        listOf(juzLabel(place.juz), hizbPattern.fill(place.hizb), pageLabel(place.page)).joinToString(Separator)

    /** What the quiet line above an ayah says: [MarkLine.start] is "Juz 2 · Hizb 3" (or just the hizb), [MarkLine.end] the page. */
    fun markLine(marks: AyahMarks): MarkLine {
        val start = listOfNotNull(
            marks.juz?.let(::juzLabel),
            marks.hizbQuarter?.let(::hizbLabel),
        ).joinToString(Separator).ifEmpty { null }
        return MarkLine(start = start, end = marks.page?.let(::pageLabel))
    }

    /** "Sajdah · recommended" / "Sajdah · obligatory". */
    fun sajdaLabel(mark: Sajda): String = "$sajda$Separator${sajdaKind(mark)}"

    fun sajdaKind(mark: Sajda): String = if (mark.obligatory) sajdaObligatory else sajdaRecommended

    private fun String.fill(n: Int): String = replace("{n}", n.toString())

    companion object {
        const val Separator = " · "
    }
}

/** The two ends of the line above an ayah: the juz / hizb on the left, the page on the right; either may be missing. */
@Immutable
data class MarkLine(val start: String?, val end: String?) {
    /** The whole line as a screen reader says it: "Juz 2 · Hizb 3 · p. 22". */
    val spoken: String get() = listOfNotNull(start, end).joinToString(MushafCopy.Separator)
}

fun mushafCopy(language: AppLanguage): MushafCopy = when (language) {
    AppLanguage.RU -> MushafCopy(
        language = language,
        tabSurahs = "Суры", tabJuz = "Джузы", juzSearchPlaceholder = "Номер или название джуза",
        juzNoResultsBody = "Попробуйте номер джуза, «джуз 30» или аят в виде 2:255.",
        juzPattern = "Джуз {n}", hizbPattern = "Хизб {n}",
        hizbQuarterPattern = "¼ хизба {n}", hizbHalfPattern = "½ хизба {n}", hizbThreeQuartersPattern = "¾ хизба {n}",
        pagePattern = "стр. {n}",
        sajda = "Саджда", sajdaObligatory = "обязательная", sajdaRecommended = "желательная",
        sajdaExplain = "В этом месте совершают земной поклон (саджда тилава)",
        sajdaWhat = "Что это значит",
    )
    AppLanguage.EN -> MushafCopy(
        language = language,
        tabSurahs = "Surahs", tabJuz = "Juz", juzSearchPlaceholder = "Juz number or name",
        juzNoResultsBody = "Try a juz number, \"juz 30\", or an ayah like 2:255.",
        juzPattern = "Juz {n}", hizbPattern = "Hizb {n}",
        hizbQuarterPattern = "¼ Hizb {n}", hizbHalfPattern = "½ Hizb {n}", hizbThreeQuartersPattern = "¾ Hizb {n}",
        pagePattern = "p. {n}",
        sajda = "Sajdah", sajdaObligatory = "obligatory", sajdaRecommended = "recommended",
        sajdaExplain = "A prostration (sajdah of recitation) is made at this place",
        sajdaWhat = "What this means",
    )
    AppLanguage.TG -> MushafCopy(
        language = language,
        tabSurahs = "Сураҳо", tabJuz = "Ҷузъҳо", juzSearchPlaceholder = "Рақам ё номи ҷузъ",
        juzNoResultsBody = "Рақами ҷузъ, «ҷузъи 30» ё оятро чун 2:255 нависед.",
        juzPattern = "Ҷузъи {n}", hizbPattern = "Ҳизби {n}",
        hizbQuarterPattern = "¼ ҳизби {n}", hizbHalfPattern = "½ ҳизби {n}", hizbThreeQuartersPattern = "¾ ҳизби {n}",
        pagePattern = "саҳ. {n}",
        sajda = "Саҷда", sajdaObligatory = "ҳатмӣ", sajdaRecommended = "тавсияшуда",
        sajdaExplain = "Дар ин ҷо саҷдаи тиловат мекунанд",
        sajdaWhat = "Ин чӣ маъно дорад",
    )
    AppLanguage.UZ -> MushafCopy(
        language = language,
        tabSurahs = "Suralar", tabJuz = "Juzlar", juzSearchPlaceholder = "Juz raqami yoki nomi",
        juzNoResultsBody = "Juz raqami, «30-juz» yoki 2:255 kabi oyatni kiriting.",
        juzPattern = "{n}-juz", hizbPattern = "{n}-hizb",
        hizbQuarterPattern = "¼ {n}-hizb", hizbHalfPattern = "½ {n}-hizb", hizbThreeQuartersPattern = "¾ {n}-hizb",
        pagePattern = "{n}-bet",
        sajda = "Sajda", sajdaObligatory = "majburiy", sajdaRecommended = "tavsiya etilgan",
        sajdaExplain = "Bu yerda tilovat sajdasi qilinadi",
        sajdaWhat = "Bu nimani anglatadi",
    )
    AppLanguage.TR -> MushafCopy(
        language = language,
        tabSurahs = "Sureler", tabJuz = "Cüzler", juzSearchPlaceholder = "Cüz numarası veya adı",
        juzNoResultsBody = "Cüz numarasını, «30. cüz» veya 2:255 gibi bir ayeti yazın.",
        juzPattern = "{n}. Cüz", hizbPattern = "{n}. Hizb",
        hizbQuarterPattern = "¼ {n}. Hizb", hizbHalfPattern = "½ {n}. Hizb", hizbThreeQuartersPattern = "¾ {n}. Hizb",
        pagePattern = "s. {n}",
        sajda = "Secde", sajdaObligatory = "zorunlu", sajdaRecommended = "tavsiye edilen",
        sajdaExplain = "Burada tilavet secdesi yapılır",
        sajdaWhat = "Bunun anlamı",
    )
}
