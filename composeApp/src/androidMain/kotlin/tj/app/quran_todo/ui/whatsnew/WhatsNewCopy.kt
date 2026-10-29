package tj.app.quran_todo.ui.whatsnew

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/**
 * Strings of the one-time "What's new: the lesson of the day" sheet for people who updated the app (task C1), in the five app
 * languages. "Make a plan" is [tj.app.quran_todo.ui.i18n.HomeCopy.lessonMakePlan]. The Tajik, Uzbek and Turkish texts are to be
 * checked by native speakers.
 */
@Immutable
internal data class WhatsNewCopy(
    val title: String,
    val body: String,
    /** The plan exists: it was built from what the person marked in the older version. */
    val withPlan: String,
    val withoutPlan: String,
    val openLesson: String,
    val later: String,
)

@Composable
internal fun rememberWhatsNewCopy(language: AppLanguage = LocalAppLanguage.current): WhatsNewCopy =
    remember(language) { whatsNewCopy(language) }

internal fun whatsNewCopy(language: AppLanguage): WhatsNewCopy = when (language) {
    AppLanguage.EN -> WhatsNewCopy(
        title = "What’s new: Today’s lesson",
        body = "One ready lesson every day: a little new, and a review of what is recent and old. The app keeps the plan and tells you when you will finish.",
        withPlan = "Your plan is already built from what you marked before.",
        withoutPlan = "Make a plan and the lesson appears on the home screen.",
        openLesson = "Open the lesson",
        later = "Later",
    )

    AppLanguage.RU -> WhatsNewCopy(
        title = "Новое: Урок дня",
        body = "Каждый день — один готовый урок: немного нового, повторение недавнего и старого. Приложение само ведёт план и подсказывает, когда вы закончите.",
        withPlan = "Ваш план уже собран из того, что вы отмечали раньше.",
        withoutPlan = "Составьте план — и урок появится на главном экране.",
        openLesson = "Открыть урок",
        later = "Позже",
    )

    AppLanguage.TG -> WhatsNewCopy(
        title = "Нав: Дарси рӯз",
        body = "Ҳар рӯз як дарси тайёр: андаке нав ва такрори навакак ва кӯҳна. Барнома худаш нақшаро пеш мебарад ва мегӯяд, ки кай анҷом медиҳед.",
        withPlan = "Нақшаи шумо аз он чизе, ки пештар қайд карда будед, ҷамъ шудааст.",
        withoutPlan = "Нақша созед — ва дарс дар саҳифаи асосӣ пайдо мешавад.",
        openLesson = "Кушодани дарс",
        later = "Баъдтар",
    )

    AppLanguage.UZ -> WhatsNewCopy(
        title = "Yangilik: Kunlik dars",
        body = "Har kuni bitta tayyor dars: ozgina yangi material, yaqinda va eski o‘rganilganlarning takrori. Ilova rejani o‘zi yuritadi va qachon tugatishingizni aytadi.",
        withPlan = "Rejangiz avval belgilaganlaringiz asosida tayyorlangan.",
        withoutPlan = "Reja tuzing — dars bosh ekranda paydo bo‘ladi.",
        openLesson = "Darsni ochish",
        later = "Keyinroq",
    )

    AppLanguage.TR -> WhatsNewCopy(
        title = "Yenilik: Günün dersi",
        body = "Her gün tek bir hazır ders: biraz yeni, yakın zamanda ve eskiden öğrendiklerinizin tekrarı. Uygulama planı kendisi yürütür ve ne zaman bitireceğinizi söyler.",
        withPlan = "Planınız daha önce işaretlediklerinizden hazırlandı.",
        withoutPlan = "Bir plan yapın — ders ana ekranda görünür.",
        openLesson = "Dersi aç",
        later = "Sonra",
    )
}
