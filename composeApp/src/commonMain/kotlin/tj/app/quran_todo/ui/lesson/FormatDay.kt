package tj.app.quran_todo.ui.lesson

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.platform.formatDayOfMonth

/** "14 March" in the language of the app. */
fun formatDay(epochDay: Int, language: AppLanguage): String = formatDayOfMonth(epochDay, language)
