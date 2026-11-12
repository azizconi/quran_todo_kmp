package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.common.i18n.AppLanguage

/**
 * Strings of "Welcome back" at the start of the lesson (task U17) and the quiet line of a lighter week, in the five app languages.
 * Calm wording, no blame; counts are formatted by the functions so each language keeps its own plural forms.
 */
@Immutable
class WelcomeBackCopy(
    val language: AppLanguage,
    val title: String,
    private val missedPattern: String,
    private val todayPattern: String,
    private val newSuffix: String,
    private val bestPattern: String,
    val catchUp: String,
    val lighter: String,
    val lighterNote: String,
    private val lighterLinePattern: String,
) {
    /** "Missed 3 days". */
    fun missed(days: Int): String = missedPattern.replace("{n}", daysWord(days))

    /** "Today: 12 repetitions · ~25 min", with " · plus a new passage" when the lesson has one. */
    fun today(repetitions: Int, minutes: Int, hasNew: Boolean): String =
        todayPattern.replace("{k}", repetitionsWord(repetitions)).replace("{m}", minutes.toString()) +
            if (hasNew) newSuffix else ""

    fun best(days: Int): String = bestPattern.replace("{n}", daysWord(days))

    /** "Lighter week: until 18 November". */
    fun lighterLine(date: String): String = lighterLinePattern.replace("{date}", date)

    private fun daysWord(n: Int): String = when (language) {
        AppLanguage.RU -> "$n дн."
        AppLanguage.EN -> "$n ${if (n == 1) "day" else "days"}"
        AppLanguage.TG -> "$n рӯз"
        AppLanguage.UZ -> "$n kun"
        AppLanguage.TR -> "$n gün"
    }

    private fun repetitionsWord(n: Int): String = when (language) {
        AppLanguage.RU -> "$n ${russianPlural(n, "повторение", "повторения", "повторений")}"
        AppLanguage.EN -> "$n ${if (n == 1) "repetition" else "repetitions"}"
        AppLanguage.TG -> "$n такрор"
        AppLanguage.UZ -> "$n takror"
        AppLanguage.TR -> "$n tekrar"
    }
}

fun welcomeBackCopy(language: AppLanguage): WelcomeBackCopy = when (language) {
    AppLanguage.RU -> WelcomeBackCopy(
        language = language,
        title = "С возвращением",
        missedPattern = "Пропущено {n}",
        todayPattern = "Сегодня: {k} · ~{m} мин",
        newSuffix = " · и новое",
        bestPattern = "Лучшая серия: {n}",
        catchUp = "Догнать за несколько дней",
        lighter = "Облегчить неделю",
        lighterNote = "7 дней — вдвое меньше нового, повторения как обычно",
        lighterLinePattern = "Лёгкая неделя: до {date}",
    )
    AppLanguage.EN -> WelcomeBackCopy(
        language = language,
        title = "Welcome back",
        missedPattern = "Missed {n}",
        todayPattern = "Today: {k} · ~{m} min",
        newSuffix = " · plus a new passage",
        bestPattern = "Best streak: {n}",
        catchUp = "Catch up over a few days",
        lighter = "A lighter week",
        lighterNote = "7 days: half as much new, repetitions as usual",
        lighterLinePattern = "Lighter week: until {date}",
    )
    AppLanguage.TG -> WelcomeBackCopy(
        language = language,
        title = "Хуш омадед",
        missedPattern = "Нагузашт: {n}",
        todayPattern = "Имрӯз: {k} · ~{m} дақ",
        newSuffix = " · ва порчаи нав",
        bestPattern = "Беҳтарин силсила: {n}",
        catchUp = "Дар чанд рӯз ҷуброн кардан",
        lighter = "Ҳафтаи сабуктар",
        lighterNote = "7 рӯз — нав ду баробар камтар, такрорҳо мисли ҳамеша",
        lighterLinePattern = "Ҳафтаи сабук: то {date}",
    )
    AppLanguage.UZ -> WelcomeBackCopy(
        language = language,
        title = "Xush kelibsiz",
        missedPattern = "O‘tkazilgan: {n}",
        todayPattern = "Bugun: {k} · ~{m} daq",
        newSuffix = " · va yangi qism",
        bestPattern = "Eng yaxshi seriya: {n}",
        catchUp = "Bir necha kunda yetib olish",
        lighter = "Haftani yengillashtirish",
        lighterNote = "7 kun — yangisi ikki barobar kam, takrorlar odatdagidek",
        lighterLinePattern = "Yengil hafta: {date} gacha",
    )
    AppLanguage.TR -> WelcomeBackCopy(
        language = language,
        title = "Tekrar hoş geldin",
        missedPattern = "Ara verilen: {n}",
        todayPattern = "Bugün: {k} · ~{m} dk",
        newSuffix = " · ve yeni bölüm",
        bestPattern = "En iyi seri: {n}",
        catchUp = "Birkaç günde telafi et",
        lighter = "Haftayı hafiflet",
        lighterNote = "7 gün — yeni kısım yarıya iner, tekrarlar her zamanki gibi",
        lighterLinePattern = "Hafif hafta: {date} tarihine kadar",
    )
}
