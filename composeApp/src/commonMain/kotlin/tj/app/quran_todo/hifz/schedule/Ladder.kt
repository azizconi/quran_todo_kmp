package tj.app.quran_todo.hifz.schedule

import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade

/**
 * The ladder of a freshly learned passage: R1, R3, R7, R14, R30, then OLD. Pure functions, every day is an epoch day.
 *
 * | grade  | a ladder stage k                                  | OLD                          |
 * |--------|---------------------------------------------------|------------------------------|
 * | EASY   | k + 2 (past R30 -> OLD)                           | stays OLD                    |
 * | GOOD   | k + 1 (after R30 -> OLD)                          | stays OLD                    |
 * | HARD   | the same stage, due in interval(k) days           | R3                           |
 * | FORGOT | R1 (tomorrow), lapses + 1                         | R1 (tomorrow), lapses + 1    |
 *
 * The due day always counts from the day of the repetition, not from the day it was due.
 */
object Ladder {
    /** The state of a passage after a repetition. [nextDueEpochDay] is null for [PassageStage.OLD]. */
    data class Step(val stage: PassageStage, val nextDueEpochDay: Int?, val lapses: Int)

    /** A passage learned today: R1, due tomorrow. */
    fun firstStep(today: Int): Step = stepAt(PassageStage.R1, today, lapses = 0)

    fun afterReview(stage: PassageStage, grade: ReviewGrade, today: Int, lapses: Int): Step {
        if (stage == PassageStage.OLD) {
            return when (grade) {
                ReviewGrade.GOOD, ReviewGrade.EASY -> Step(PassageStage.OLD, null, lapses)
                ReviewGrade.HARD -> stepAt(PassageStage.R3, today, lapses)
                ReviewGrade.FORGOT -> stepAt(PassageStage.R1, today, lapses + 1)
            }
        }
        return when (grade) {
            ReviewGrade.FORGOT -> stepAt(PassageStage.R1, today, lapses + 1)
            ReviewGrade.HARD -> stepAt(stage, today, lapses)
            ReviewGrade.GOOD -> stepAt(PassageStage.ofRank(stage.rank + 1), today, lapses)
            ReviewGrade.EASY -> stepAt(PassageStage.ofRank(stage.rank + 2), today, lapses)
        }
    }

    private fun stepAt(stage: PassageStage, today: Int, lapses: Int): Step {
        val interval = stage.intervalDays ?: return Step(PassageStage.OLD, null, lapses)
        return Step(stage, today + interval, lapses)
    }
}

/** The difficult ayahs of a passage: `ayahInSurah -> clearsAfter`. */
object HardAyahs {
    /**
     * After a repetition: a GOOD or EASY one makes every mark one step closer to going away (0 removes it); then the
     * ayahs the person marked now get [HifzConstants.HARD_CLEARS_AFTER] again (already marked ones start over). A HARD or
     * FORGOT repetition does not count as a success, so the old marks stay as they are. Marks outside the passage are ignored.
     */
    fun afterReview(
        current: Map<Int, Int>,
        grade: ReviewGrade,
        marks: Set<Int>,
        startAyah: Int,
        endAyah: Int,
    ): Map<Int, Int> {
        val result = LinkedHashMap<Int, Int>()
        val success = grade == ReviewGrade.GOOD || grade == ReviewGrade.EASY
        for ((ayah, clears) in current) {
            val left = if (success) clears - 1 else clears
            if (left > 0) result[ayah] = left
        }
        for (ayah in marks) if (ayah in startAyah..endAyah) result[ayah] = HifzConstants.HARD_CLEARS_AFTER
        return result
    }

    /** The marks of a part of a passage after it was cut: they follow the ayah numbers. */
    fun restrictTo(hard: Map<Int, Int>, startAyah: Int, endAyah: Int): Map<Int, Int> =
        hard.filterKeys { it in startAyah..endAyah }
}
