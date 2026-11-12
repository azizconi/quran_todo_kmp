package tj.app.quran_todo.ui.hifz

import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.schedule.Ladder
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

    /**
     * What a finished recall of [session] writes: the grade the rule above gives and the ayahs answered "hard" - or [override], the
     * choice the person made on the grade sheet. Null while some ayah is still to be answered.
     */
    fun resultOf(session: HifzSession, override: RecallResult? = null): RecallResult? {
        if (!session.finished) return null
        return override ?: RecallResult(session.suggestedGrade, session.hardAyahs)
    }

    /**
     * In how many days a passage comes back after [grade], counted from the day of the repetition: a new passage ([stage] null) always
     * enters the ladder at R1 (1 day), a passage on the ladder follows [Ladder.afterReview]. Null when the answer is not a plain number
     * of days: the passage is (or becomes) OLD and comes back with the manzil circle, which depends on the other passages.
     */
    fun returnDays(stage: PassageStage?, grade: ReviewGrade): Int? {
        val step = if (stage == null) Ladder.firstStep(0) else Ladder.afterReview(stage, grade, 0, 0)
        return step.nextDueEpochDay
    }
}

/** The grade of a passage and its difficult ayahs (numbers in the surah) as a recall hands them to the lesson. */
data class RecallResult(val grade: ReviewGrade, val hard: Set<Int>) {
    /** Text form for `rememberSaveable`. */
    fun encode(): String = "${grade.name}|${hard.sorted().joinToString(",")}"

    companion object {
        /** Reads [encode]'s output; null for anything else. */
        fun decode(text: String?): RecallResult? {
            if (text.isNullOrEmpty()) return null
            val parts = text.split('|')
            if (parts.size != 2) return null
            val grade = ReviewGrade.entries.firstOrNull { it.name == parts[0] } ?: return null
            val hard = if (parts[1].isEmpty()) {
                emptySet()
            } else {
                parts[1].split(',').mapTo(LinkedHashSet()) { it.toIntOrNull() ?: return null }
            }
            return RecallResult(grade, hard)
        }
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
