package tj.app.quran_todo.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.i18n.LocalAppLanguage

/**
 * Strings of the pause of the plan (task F2b) in the five app languages: the row and the sheet of "My plan", the card of the home
 * screen and the empty lesson screen. Texts with dates and days are patterns with `{date}` and `{days}` placeholders, so each
 * language keeps its own word order; dates are formatted by the caller (`formatDay`), day counts by [days].
 */
@Immutable
data class PauseCopy(
    val language: AppLanguage,

    // The row of "My plan"
    val rowTitle: String,
    val rowSubtitle: String,
    val rowActivePattern: String,
    val extend: String,
    val resumeNow: String,

    // The sheet
    val sheetTitle: String,
    val extendTitle: String,
    val daysQuestion: String,
    val less: String,
    val more: String,
    val infoPattern: String,
    val extendInfoPattern: String,
    val confirmPause: String,
    val confirmExtend: String,

    // "Continue now?"
    val resumeTitle: String,
    val resumeBody: String,
    val resumeConfirm: String,
    val cancel: String,

    // The home card and the lesson screen
    val cardTitlePattern: String,
    val cardBodyPattern: String,
    val lessonBodyPattern: String,

    // Toasts
    val pausedToastPattern: String,
    val resumedToast: String,
    val failed: String,
) {
    private fun String.fill(vararg values: Pair<String, Any>): String =
        values.fold(this) { text, (key, value) -> text.replace("{$key}", value.toString()) }

    /** "3 days" in the grammar of the language ([PlanCopy.daysLabel]). */
    fun days(count: Int): String = planCopy(language).daysLabel(count)

    /** "On pause until 11 November": the row of "My plan" while the plan is paused. */
    fun rowActive(untilDate: String): String = rowActivePattern.fill("date" to untilDate)

    /** "Lessons return on 12 November. Reviews will move by 7 days; your streak is kept." */
    fun info(resumeDate: String, count: Int): String = infoPattern.fill("date" to resumeDate, "days" to days(count))

    /** The same for an extension: "... will move by 3 more days ...". */
    fun extendInfo(resumeDate: String, count: Int): String = extendInfoPattern.fill("date" to resumeDate, "days" to days(count))

    /** "Plan paused until 11 November": the title of the card of the home screen and of the empty lesson screen. */
    fun cardTitle(untilDate: String): String = cardTitlePattern.fill("date" to untilDate)

    /** "Lessons return on 12 November. Your streak is kept." */
    fun cardBody(resumeDate: String): String = cardBodyPattern.fill("date" to resumeDate)

    /** "Lessons return on 12 November." */
    fun lessonBody(resumeDate: String): String = lessonBodyPattern.fill("date" to resumeDate)

    fun pausedToast(untilDate: String): String = pausedToastPattern.fill("date" to untilDate)
}

@Composable
fun rememberPauseCopy(language: AppLanguage = LocalAppLanguage.current): PauseCopy = remember(language) { pauseCopy(language) }

fun pauseCopy(language: AppLanguage): PauseCopy = when (language) {
    AppLanguage.RU -> PauseCopy(
        language = language,
        rowTitle = "Пауза",
        rowSubtitle = "Остановить уроки на несколько дней",
        rowActivePattern = "На паузе до {date}",
        extend = "Продлить",
        resumeNow = "Продолжить сейчас",
        sheetTitle = "Пауза плана",
        extendTitle = "Продлить паузу",
        daysQuestion = "На сколько дней",
        less = "Меньше дней",
        more = "Больше дней",
        infoPattern = "Уроки вернутся {date}. Повторения сдвинутся на {days}, серия сохранится.",
        extendInfoPattern = "Уроки вернутся {date}. Повторения сдвинутся ещё на {days}, серия сохранится.",
        confirmPause = "Поставить на паузу",
        confirmExtend = "Продлить паузу",
        resumeTitle = "Продолжить сейчас?",
        resumeBody = "Уроки вернутся сегодня, повторения встанут на прежние места. Серия сохранится.",
        resumeConfirm = "Продолжить",
        cancel = "Отмена",
        cardTitlePattern = "План на паузе до {date}",
        cardBodyPattern = "Уроки вернутся {date}. Серия сохранена.",
        lessonBodyPattern = "Уроки вернутся {date}.",
        pausedToastPattern = "План на паузе до {date}",
        resumedToast = "Уроки возобновлены",
        failed = "Не удалось сохранить",
    )
    AppLanguage.EN -> PauseCopy(
        language = language,
        rowTitle = "Pause",
        rowSubtitle = "Stop the lessons for a few days",
        rowActivePattern = "On pause until {date}",
        extend = "Extend",
        resumeNow = "Continue now",
        sheetTitle = "Pause the plan",
        extendTitle = "Extend the pause",
        daysQuestion = "For how many days",
        less = "Fewer days",
        more = "More days",
        infoPattern = "Lessons return on {date}. Reviews will move by {days}; your streak is kept.",
        extendInfoPattern = "Lessons return on {date}. Reviews will move by {days} more; your streak is kept.",
        confirmPause = "Pause the plan",
        confirmExtend = "Extend the pause",
        resumeTitle = "Continue now?",
        resumeBody = "Lessons come back today and reviews return to their places. Your streak is kept.",
        resumeConfirm = "Continue",
        cancel = "Cancel",
        cardTitlePattern = "Plan paused until {date}",
        cardBodyPattern = "Lessons return on {date}. Your streak is kept.",
        lessonBodyPattern = "Lessons return on {date}.",
        pausedToastPattern = "Plan paused until {date}",
        resumedToast = "Lessons are back",
        failed = "Couldn’t save",
    )
    AppLanguage.TG -> PauseCopy(
        language = language,
        rowTitle = "Таваққуф",
        rowSubtitle = "Дарсҳоро барои чанд рӯз қатъ кардан",
        rowActivePattern = "Таваққуф то {date}",
        extend = "Дароз кардан",
        resumeNow = "Ҳозир идома додан",
        sheetTitle = "Таваққуфи нақша",
        extendTitle = "Дароз кардани таваққуф",
        daysQuestion = "Барои чанд рӯз",
        less = "Камтар рӯз",
        more = "Бештар рӯз",
        infoPattern = "Дарсҳо {date} бармегарданд. Такрорҳо {days} ба таъхир меафтанд, силсила нигоҳ дошта мешавад.",
        extendInfoPattern = "Дарсҳо {date} бармегарданд. Такрорҳо боз {days} ба таъхир меафтанд, силсила нигоҳ дошта мешавад.",
        confirmPause = "Таваққуф гузоштан",
        confirmExtend = "Таваққуфро дароз кардан",
        resumeTitle = "Ҳозир идома дода шавад?",
        resumeBody = "Дарсҳо имрӯз бармегарданд, такрорҳо ба ҷои пештараашон меистанд. Силсила нигоҳ дошта мешавад.",
        resumeConfirm = "Идома додан",
        cancel = "Бекор кардан",
        cardTitlePattern = "Нақша то {date} дар таваққуф аст",
        cardBodyPattern = "Дарсҳо {date} бармегарданд. Силсила нигоҳ дошта шудааст.",
        lessonBodyPattern = "Дарсҳо {date} бармегарданд.",
        pausedToastPattern = "Нақша то {date} дар таваққуф аст",
        resumedToast = "Дарсҳо аз нав оғоз шуданд",
        failed = "Сабт кардан нашуд",
    )
    AppLanguage.UZ -> PauseCopy(
        language = language,
        rowTitle = "Pauza",
        rowSubtitle = "Darslarni bir necha kunga to‘xtatish",
        rowActivePattern = "{date} gacha pauzada",
        extend = "Uzaytirish",
        resumeNow = "Hozir davom ettirish",
        sheetTitle = "Rejani pauzaga qo‘yish",
        extendTitle = "Pauzani uzaytirish",
        daysQuestion = "Necha kunga",
        less = "Kamroq kun",
        more = "Ko‘proq kun",
        infoPattern = "Darslar {date} kuni qaytadi. Takrorlar {days}ga suriladi, seriya saqlanadi.",
        extendInfoPattern = "Darslar {date} kuni qaytadi. Takrorlar yana {days}ga suriladi, seriya saqlanadi.",
        confirmPause = "Pauzaga qo‘yish",
        confirmExtend = "Pauzani uzaytirish",
        resumeTitle = "Hozir davom ettiramizmi?",
        resumeBody = "Darslar bugun qaytadi, takrorlar oldingi joyiga turadi. Seriya saqlanadi.",
        resumeConfirm = "Davom ettirish",
        cancel = "Bekor qilish",
        cardTitlePattern = "Reja {date} gacha pauzada",
        cardBodyPattern = "Darslar {date} kuni qaytadi. Seriya saqlangan.",
        lessonBodyPattern = "Darslar {date} kuni qaytadi.",
        pausedToastPattern = "Reja {date} gacha pauzada",
        resumedToast = "Darslar qayta boshlandi",
        failed = "Saqlab bo‘lmadi",
    )
    AppLanguage.TR -> PauseCopy(
        language = language,
        rowTitle = "Ara ver",
        rowSubtitle = "Dersleri birkaç günlüğüne durdur",
        rowActivePattern = "{date} tarihine kadar ara verildi",
        extend = "Uzat",
        resumeNow = "Şimdi devam et",
        sheetTitle = "Plana ara ver",
        extendTitle = "Arayı uzat",
        daysQuestion = "Kaç gün",
        less = "Daha az gün",
        more = "Daha çok gün",
        infoPattern = "Dersler {date} tarihinde geri gelir. Tekrarlar {days} ileri kayar, seri korunur.",
        extendInfoPattern = "Dersler {date} tarihinde geri gelir. Tekrarlar {days} daha ileri kayar, seri korunur.",
        confirmPause = "Ara ver",
        confirmExtend = "Arayı uzat",
        resumeTitle = "Şimdi devam edilsin mi?",
        resumeBody = "Dersler bugün geri gelir, tekrarlar eski yerlerine döner. Seri korunur.",
        resumeConfirm = "Devam et",
        cancel = "Vazgeç",
        cardTitlePattern = "Plana {date} tarihine kadar ara verildi",
        cardBodyPattern = "Dersler {date} tarihinde geri gelir. Seri korundu.",
        lessonBodyPattern = "Dersler {date} tarihinde geri gelir.",
        pausedToastPattern = "Plana {date} tarihine kadar ara verildi",
        resumedToast = "Dersler yeniden başladı",
        failed = "Kaydedilemedi",
    )
}
