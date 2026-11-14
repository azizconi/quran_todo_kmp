package tj.app.quran_todo.ui.home.components

import tj.app.quran_todo.ui.home.LessonTomorrowUi
import tj.app.quran_todo.ui.i18n.HomeCopy
import tj.app.quran_todo.ui.i18n.passageRangeLabel

/** "Tomorrow: An-Naba 6–10 · 2 to review". */
fun tomorrowText(tomorrow: LessonTomorrowUi, copy: HomeCopy): String = copy.lessonTomorrow(
    newRange = if (tomorrow.hasNew) passageRangeLabel(tomorrow.surahName, tomorrow.startAyah, tomorrow.endAyah) else null,
    dueCount = tomorrow.dueCount,
)
