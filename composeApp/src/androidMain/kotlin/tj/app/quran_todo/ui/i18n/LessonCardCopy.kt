package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/**
 * Strings that only the faces of the lesson card (task U16) need, in the five app languages: the "Returning" button, the link to the
 * summary of a finished lesson and the calm "no plan" line for people whose goal is reading and listening. The title and the
 * "Missed 3 days" line of the returning face come from [WelcomeBackCopy].
 */
@Immutable
class LessonCardCopy(
    val continueMemorizing: String,
    val lessonSummary: String,
    val quietInvite: String,
    /** One line under the lesson: what "Recent" means (the passages of the last days). */
    val recentHint: String,
    /** One line under the lesson: what "Old" means (the review circle). */
    val oldHint: String,
)

@Composable
fun rememberLessonCardCopy(language: AppLanguage = LocalAppLanguage.current): LessonCardCopy = remember(language) { lessonCardCopy(language) }

fun lessonCardCopy(language: AppLanguage): LessonCardCopy = when (language) {
    AppLanguage.RU -> LessonCardCopy(
        continueMemorizing = "Продолжить заучивание",
        lessonSummary = "Итоги урока",
        quietInvite = "Хотите заучивать? Составьте план — урок появится здесь",
        recentHint = "Недавнее — то, что вы выучили за последние дни",
        oldHint = "Старое — давно выученное, чтобы не забылось",
    )

    AppLanguage.EN -> LessonCardCopy(
        continueMemorizing = "Continue memorizing",
        lessonSummary = "Lesson summary",
        quietInvite = "Want to memorize? Make a plan — the lesson will appear here",
        recentHint = "Recent — what you learned in the last few days",
        oldHint = "Old — learned long ago, reviewed so it is not forgotten",
    )

    AppLanguage.TG -> LessonCardCopy(
        continueMemorizing = "Идомаи ҳифз",
        lessonSummary = "Хулосаи дарс",
        quietInvite = "Мехоҳед ҳифз кунед? Нақша созед — дарс дар ин ҷо пайдо мешавад",
        recentHint = "Навакак — он чӣ дар рӯзҳои охир аз бар кардед",
        oldHint = "Кӯҳна — аз бар кардаи қаблӣ, то фаромӯш нашавад",
    )

    AppLanguage.UZ -> LessonCardCopy(
        continueMemorizing = "Yodlashni davom ettirish",
        lessonSummary = "Dars yakunlari",
        quietInvite = "Yodlamoqchimisiz? Reja tuzing — dars shu yerda paydo bo‘ladi",
        recentHint = "Yaqinda — so‘nggi kunlarda yodlaganlaringiz",
        oldHint = "Eski — anchadan beri yodlanganlar, esdan chiqmasligi uchun",
    )

    AppLanguage.TR -> LessonCardCopy(
        continueMemorizing = "Ezbere devam et",
        lessonSummary = "Ders özeti",
        quietInvite = "Ezberlemek ister misiniz? Bir plan yapın — ders burada görünecek",
        recentHint = "Yakın zamanda — son günlerde ezberledikleriniz",
        oldHint = "Eski — uzun zaman önce ezberlenenler, unutulmasın diye",
    )
}
