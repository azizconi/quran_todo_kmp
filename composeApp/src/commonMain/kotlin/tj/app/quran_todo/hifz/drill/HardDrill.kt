package tj.app.quran_todo.hifz.drill

import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.schedule.HifzConstants
import tj.app.quran_todo.hifz.schedule.PassageOps

/**
 * The drill of the difficult ayahs (task F2a) as a pure function: what [tj.app.quran_todo.hifz.HifzRepository.drillHard] writes.
 *
 * Only the marks of the trained ayahs change. The stage, the due day, `lastReviewed`, the lapses and the marks of every other
 * ayah stay exactly as they were: a drill is a help to the hard places, not a repetition of the passage, so the ladder and the
 * circle of the manzil do not see it.
 */
object HardDrill {
    /**
     * What a drill of one surah did: the new list of all [passages], and what the one `CHECK` record of the log is made of -
     * the [range] of the trained ayahs (first to last), the [passageId] when one passage covers the whole range (null when the
     * range spans several), and the [grade] by the rule of the Recall step ([gradeOf]).
     */
    class Outcome(
        val passages: List<Passage>,
        val range: AyahRange,
        val passageId: Long?,
        val grade: ReviewGrade,
        /** The ayahs (numbers in the surah) that were trained: marked, covered and so counted. */
        val trained: Set<Int>,
    )

    /**
     * The grade of a drill group, by the same rule as `RecallGrading.suggest` of the Recall step: nothing is still hard - `GOOD`; less
     * than half of the [trained] ayahs are still hard - `HARD`; half or more - `FORGOT`. So a drill that went badly lowers the
     * retention of the progress screen instead of raising it.
     */
    fun gradeOf(trained: Int, stillHard: Int): ReviewGrade = when {
        trained <= 0 || stillHard <= 0 -> ReviewGrade.GOOD
        stillHard * 2 >= trained -> ReviewGrade.FORGOT
        else -> ReviewGrade.HARD
    }

    /**
     * Trains the difficult ayahs of [surah]. An ayah in [remembered] gets its counter `clearsAfter` one lower (at 0 the mark is
     * gone); an ayah in [stillHard] starts over at [HifzConstants.HARD_CLEARS_AFTER]. An ayah in both counts as still hard. An ayah
     * that is not marked difficult any more (or is not covered by a passage) is skipped. Null when nothing is left to train: then
     * nothing is written at all, not even a record of the log.
     */
    fun apply(passages: List<Passage>, surah: Int, remembered: Set<Int>, stillHard: Set<Int>): Outcome? {
        val hardNow = passages.filter { it.surah == surah }.flatMapTo(HashSet()) { it.hard.keys }
        val still = stillHard.filterTo(LinkedHashSet()) { it in hardNow }
        val gone = remembered.filterTo(LinkedHashSet()) { it in hardNow && it !in still }
        val ordered = (still + gone).sorted()
        if (ordered.isEmpty()) return null
        val trained = ordered.toSet()

        val after = passages.map { passage ->
            if (passage.surah != surah) return@map passage
            val own = passage.hard.keys.filter { it in trained }
            if (own.isEmpty()) return@map passage
            val marks = LinkedHashMap(passage.hard)
            for (ayah in own) {
                if (ayah in still) {
                    marks[ayah] = HifzConstants.HARD_CLEARS_AFTER
                } else {
                    val left = (marks.getValue(ayah) - 1)
                    if (left > 0) marks[ayah] = left else marks.remove(ayah)
                }
            }
            passage.copy(hard = marks)
        }
        val range = AyahRange(surah, ordered.first(), ordered.last())
        return Outcome(
            passages = after,
            range = range,
            passageId = PassageOps.coveringPassageId(after, surah, range.startAyah, range.endAyah),
            grade = gradeOf(trained = ordered.size, stillHard = still.size),
            trained = trained,
        )
    }
}
