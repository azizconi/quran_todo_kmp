package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/**
 * Words of the bottom bar and of the plan row under the lesson card (UX tasks U14, U15). The names of the "Quran" and "Progress"
 * tabs are the titles of those screens ([HomeCopy.title], [ProgressCopy.title]); the reading plan entry is [KhatmCopy.screenTitle].
 */
class TabsCopy(
    val language: AppLanguage,
    /** The "Lesson" tab. */
    val tabLesson: String,
    val tabQuran: String,
    val tabProgress: String,
    /** Spoken name of the bar. */
    val barDescription: String,
    /** The trailing word of the plan row. */
    val planChange: String,
    private val planRowPattern: String,
    private val planFinishPattern: String,
    private val planRowDescriptionPattern: String,
) {
    /**
     * "Juz Amma · covered 10 of 564 · finish ~2 March": [finish] is the date, null when the pace gives none (then the last part
     * is left out).
     */
    fun planRow(name: String, covered: Int, total: Int, finish: String?): String {
        val head = planRowPattern.fill("name" to name, "covered" to covered, "total" to total)
        return if (finish == null) head else head + " · " + planFinishPattern.fill("date" to finish)
    }

    /** The spoken form of the row: the text and that it leads to the plan. */
    fun planRowDescription(text: String): String = planRowDescriptionPattern.fill("text" to text, "change" to planChange)
}

private fun String.fill(vararg values: Pair<String, Any>): String {
    var result = this
    values.forEach { (key, value) -> result = result.replace("{$key}", value.toString()) }
    return result
}

fun tabsCopy(language: AppLanguage): TabsCopy = when (language) {
    AppLanguage.RU -> TabsCopy(
        language = language,
        tabLesson = "Урок",
        tabQuran = "Коран",
        tabProgress = "Прогресс",
        barDescription = "Разделы",
        planChange = "Изменить",
        planRowPattern = "{name} · пройдено {covered} из {total}",
        planFinishPattern = "закончите ~{date}",
        planRowDescriptionPattern = "{text}. {change}",
    )

    AppLanguage.EN -> TabsCopy(
        language = language,
        tabLesson = "Lesson",
        tabQuran = "Quran",
        tabProgress = "Progress",
        barDescription = "Sections",
        planChange = "Change",
        planRowPattern = "{name} · {covered} of {total} covered",
        planFinishPattern = "finish ~{date}",
        planRowDescriptionPattern = "{text}. {change}",
    )

    AppLanguage.TG -> TabsCopy(
        language = language,
        tabLesson = "Дарс",
        tabQuran = "Қуръон",
        tabProgress = "Пешрафт",
        barDescription = "Бахшҳо",
        planChange = "Тағйир додан",
        planRowPattern = "{name} · гузашта {covered} аз {total}",
        planFinishPattern = "анҷом ~{date}",
        planRowDescriptionPattern = "{text}. {change}",
    )

    AppLanguage.UZ -> TabsCopy(
        language = language,
        tabLesson = "Dars",
        tabQuran = "Qur'on",
        tabProgress = "Natija",
        barDescription = "Bo‘limlar",
        planChange = "O‘zgartirish",
        planRowPattern = "{name} · o‘tilgan {covered} / {total}",
        planFinishPattern = "tugatasiz ~{date}",
        planRowDescriptionPattern = "{text}. {change}",
    )

    AppLanguage.TR -> TabsCopy(
        language = language,
        tabLesson = "Ders",
        tabQuran = "Kur'an",
        tabProgress = "İlerleme",
        barDescription = "Bölümler",
        planChange = "Değiştir",
        planRowPattern = "{name} · geçilen {covered} / {total}",
        planFinishPattern = "bitiş ~{date}",
        planRowDescriptionPattern = "{text}. {change}",
    )
}

@Composable
fun rememberTabsCopy(): TabsCopy {
    val language = LocalAppLanguage.current
    return remember(language) { tabsCopy(language) }
}
