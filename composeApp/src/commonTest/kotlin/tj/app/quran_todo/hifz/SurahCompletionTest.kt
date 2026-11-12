package tj.app.quran_todo.hifz

import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.schedule.LessonNew
import tj.app.quran_todo.hifz.schedule.LessonNewState
import tj.app.quran_todo.hifz.schedule.SurahCompletion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SurahCompletionTest {
    private val counts = mapOf(112 to 4, 113 to 5, 114 to 6)
    private val count: (Int) -> Int = { counts[it] ?: 0 }
    private val plan = listOf(112, 113, 114)

    private fun new(surah: Int, start: Int, end: Int, state: LessonNewState = LessonNewState.DONE) =
        LessonNew(surah, start, end, state, ReviewGrade.GOOD, 1L, 60)

    private fun all(surah: Int) = (1..count(surah)).toSet()

    @Test
    fun theSurahCompletedByTodaysPassageIsReported() {
        val covered = mapOf(114 to all(114))
        assertEquals(114, SurahCompletion.finishedToday(new(114, 4, 6), plan, covered, count))
    }

    @Test
    fun aSurahLearnedInOnePassageIsReported() {
        assertEquals(112, SurahCompletion.finishedToday(new(112, 1, 4), plan, mapOf(112 to all(112)), count))
    }

    @Test
    fun aSurahCompletedEarlierIsNotReportedAgain() {
        // 112 was finished on an earlier day; today's passage is the start of 113.
        val covered = mapOf(112 to all(112), 113 to setOf(1, 2))
        assertNull(SurahCompletion.finishedToday(new(113, 1, 2), plan, covered, count))
    }

    @Test
    fun aSurahThatIsNotCompleteIsNotReported() {
        assertNull(SurahCompletion.finishedToday(new(114, 1, 3), plan, mapOf(114 to setOf(1, 2, 3)), count))
    }

    @Test
    fun nothingLearnedTodayOrNotInThePlanReportsNothing() {
        val covered = mapOf(114 to all(114))
        assertNull(SurahCompletion.finishedToday(null, plan, covered, count))
        assertNull(SurahCompletion.finishedToday(new(114, 4, 6, LessonNewState.PENDING), plan, covered, count))
        assertNull(SurahCompletion.finishedToday(new(114, 4, 6), listOf(112), covered, count))
    }
}
