package tj.app.quran_todo.ui.i18n

import tj.app.quran_todo.common.i18n.AppLanguage

/**
 * Strings of the "Lesson of the day" home screen widget (task F5b) that the lesson card of the home screen does not have: the line of
 * a finished day, the invitation to choose a plan, the streak and the count of repetitions. The rest of the widget is said with the
 * words of the card ([HomeCopy], [PauseCopy]).
 */
data class WidgetCopy(
    val language: AppLanguage,
    /** "Everything is done today"; the widget adds "· tomorrow: ..." after it. */
    val allDone: String,
    /** The line of the widget when there is no plan. */
    val choosePlan: String,
    /** The label of the widget in TalkBack. */
    val contentDescription: String,
) {
    /** "7 days in a row". */
    fun streak(days: Int): String = when (language) {
        AppLanguage.RU -> "$days ${russianPlural(days, "день", "дня", "дней")} подряд"
        AppLanguage.EN -> "$days ${if (days == 1) "day" else "days"} in a row"
        AppLanguage.TG -> "$days рӯз пай дар пай"
        AppLanguage.UZ -> "$days kun ketma-ket"
        AppLanguage.TR -> "$days gün üst üste"
    }

    /** "2 repetitions". */
    fun repetitions(count: Int): String = when (language) {
        AppLanguage.RU -> "$count ${russianPlural(count, "повторение", "повторения", "повторений")}"
        AppLanguage.EN -> "$count ${if (count == 1) "review" else "reviews"}"
        AppLanguage.TG -> "$count такрор"
        AppLanguage.UZ -> "$count ta takror"
        AppLanguage.TR -> "$count tekrar"
    }
}

fun widgetCopy(language: AppLanguage): WidgetCopy = when (language) {
    AppLanguage.RU -> WidgetCopy(language, allDone = "Сегодня всё", choosePlan = "Выбрать план", contentDescription = "Урок дня")
    AppLanguage.EN -> WidgetCopy(language, allDone = "All done today", choosePlan = "Choose a plan", contentDescription = "Today’s lesson")
    AppLanguage.TG -> WidgetCopy(language, allDone = "Имрӯз ҳама анҷом ёфт", choosePlan = "Интихоби нақша", contentDescription = "Дарси рӯз")
    AppLanguage.UZ -> WidgetCopy(language, allDone = "Bugun hammasi tayyor", choosePlan = "Reja tanlash", contentDescription = "Kunlik dars")
    AppLanguage.TR -> WidgetCopy(language, allDone = "Bugün her şey tamam", choosePlan = "Plan seç", contentDescription = "Günün dersi")
}
