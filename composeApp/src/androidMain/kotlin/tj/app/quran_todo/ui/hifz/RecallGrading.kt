package tj.app.quran_todo.ui.hifz

import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.schedule.LessonPlan

/** The rules that turn the per-ayah answers of a recall session into a grade of the passage. */
object RecallGrading {
    /**
     * The grade the sheet at the end of a recall starts with (the person may choose another one):
     * - nothing was hard: [ReviewGrade.GOOD];
     * - some ayahs were hard: [ReviewGrade.HARD];
     * - half of the ayahs or more were hard: [ReviewGrade.FORGOT].
     *
     * [total] is the number of ayahs of the passage, [hard] how many of them were answered "hard".
     */
    fun suggest(total: Int, hard: Int): ReviewGrade = when {
        total <= 0 || hard <= 0 -> ReviewGrade.GOOD
        hard * 2 >= total -> ReviewGrade.FORGOT
        else -> ReviewGrade.HARD
    }
}

/** What the reader does with the result of "Check yourself". */
sealed interface CheckAction {
    /** The checked range is exactly a passage of today's lesson that is not done yet: it counts as that repetition. */
    data class Review(val passageId: Long) : CheckAction

    /** Anything else is only written to the log (stages and due days do not change). */
    data object LogCheck : CheckAction
}

/** Chooses between a lesson repetition and a plain check. */
object CheckRouting {
    /**
     * [CheckAction.Review] when [range] is exactly (same surah, same first and last ayah) a passage in the Recent or
     * Old block of [lesson] that is not done yet; [CheckAction.LogCheck] otherwise (also when the passage was done
     * today already, or when there is no lesson).
     */
    fun choose(lesson: LessonPlan?, range: AyahRange): CheckAction {
        lesson ?: return CheckAction.LogCheck
        val match = (lesson.recent + lesson.old).firstOrNull {
            !it.done && it.surah == range.surah && it.startAyah == range.startAyah && it.endAyah == range.endAyah
        }
        return if (match != null) CheckAction.Review(match.passageId) else CheckAction.LogCheck
    }
}
