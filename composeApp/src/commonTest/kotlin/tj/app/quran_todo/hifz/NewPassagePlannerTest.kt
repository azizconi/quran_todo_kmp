package tj.app.quran_todo.hifz

import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.schedule.Coverage
import tj.app.quran_todo.hifz.schedule.HifzStatuses
import tj.app.quran_todo.hifz.schedule.NewPassagePlanner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NewPassagePlannerTest {
    private val m = Fx.flat

    private fun next(order: List<Int>, daily: Int, covered: List<Pair<Int, IntRange>> = emptyList()): AyahRange? {
        val passages = covered.mapIndexed { index, (surah, range) ->
            Fx.passage(index + 1L, surah, range.first, range.last, PassageStage.OLD)
        }
        return NewPassagePlanner.next(Fx.plan(order, daily), Coverage(passages, m), m)
    }

    @Test
    fun aShortSurahIsTakenWholeWhenItIsNotLongerThanOneAndAHalfDays() {
        // Surah 114 has 6 ayahs.
        assertEquals(AyahRange(114, 1, 6), next(listOf(114), daily = 5)) // 6 <= 7.5
        assertEquals(AyahRange(114, 1, 6), next(listOf(114), daily = 4)) // 6 <= 6.0 exactly
        assertEquals(AyahRange(114, 1, 3), next(listOf(114), daily = 3)) // 6 > 4.5, and a tail of 3 is more than 1.5
    }

    @Test
    fun aStartedSurahIsNeverTakenWholeEvenIfShort() {
        // 114:1 is covered; 5 ayahs are left, daily 5 -> exactly the rest, but because of the pace, not of the "whole surah" rule.
        assertEquals(AyahRange(114, 2, 6), next(listOf(114), daily = 5, covered = listOf(114 to 1..1)))
        // daily 10: the whole-surah rule would give 1..6; with the first ayah covered the passage starts at 2.
        assertEquals(AyahRange(114, 2, 6), next(listOf(114), daily = 10, covered = listOf(114 to 1..1)))
    }

    @Test
    fun aPassageIsDailyNewAyahsLongFromTheFirstUncoveredAyah() {
        assertEquals(AyahRange(78, 1, 5), next(listOf(78), daily = 5))
        assertEquals(AyahRange(78, 11, 15), next(listOf(78), daily = 5, covered = listOf(78 to 1..10)))
        assertEquals(AyahRange(78, 1, 1), next(listOf(78), daily = 1))
        assertEquals(AyahRange(78, 1, 20), next(listOf(78), daily = 20))
    }

    @Test
    fun theTailIsTakenToo_whenAtMostHalfADayIsLeft() {
        // 40 ayahs, daily 5: after 1..32 and then 33..37 there would be 3 left (3 > 2.5): no tail.
        assertEquals(AyahRange(78, 33, 37), next(listOf(78), daily = 5, covered = listOf(78 to 1..32)))
        // after 34..38 there would be 2 left (2 <= 2.5): the tail is taken, the passage is 34..40.
        assertEquals(AyahRange(78, 34, 40), next(listOf(78), daily = 5, covered = listOf(78 to 1..33)))
        // after 36..40 nothing is left.
        assertEquals(AyahRange(78, 36, 40), next(listOf(78), daily = 5, covered = listOf(78 to 1..35)))
        // daily 4: half is exactly 2: 2 left are taken, 3 are not.
        assertEquals(AyahRange(78, 35, 40), next(listOf(78), daily = 4, covered = listOf(78 to 1..34)))
        assertEquals(AyahRange(78, 34, 37), next(listOf(78), daily = 4, covered = listOf(78 to 1..33)))
    }

    @Test
    fun aCoveredAyahInTheMiddleStopsThePassage() {
        // 8..9 are covered: the passage 1..? stops before 8.
        assertEquals(AyahRange(78, 1, 5), next(listOf(78), daily = 5, covered = listOf(78 to 8..9)))
        assertEquals(AyahRange(78, 1, 7), next(listOf(78), daily = 10, covered = listOf(78 to 8..9)))
        // After 1..7 are learned the next one starts after the covered block.
        assertEquals(AyahRange(78, 10, 14), next(listOf(78), daily = 5, covered = listOf(78 to 1..7, 78 to 8..9)))
    }

    @Test
    fun theTailIsNotTakenAcrossACoveredAyah() {
        // 1..33 covered, 40 covered: 34..38 would leave 39..40 but 40 is covered.
        assertEquals(AyahRange(78, 34, 38), next(listOf(78), daily = 5, covered = listOf(78 to 1..33, 78 to 40..40)))
    }

    @Test
    fun theCurrentSurahIsTheFirstOfTheQueueThatIsNotCovered() {
        val order = listOf(114, 113, 112)
        assertEquals(AyahRange(114, 1, 6), next(order, 5))
        assertEquals(AyahRange(113, 1, 5), next(order, 5, covered = listOf(114 to 1..6)))
        assertEquals(AyahRange(112, 1, 4), next(order, 5, covered = listOf(114 to 1..6, 113 to 1..5)))
        // A queue is a queue: a covered surah in the front is skipped even if a later one is partly covered.
        assertEquals(AyahRange(113, 3, 5), next(order, 5, covered = listOf(114 to 1..6, 113 to 1..2)))
    }

    @Test
    fun theQueueOrderIsTheOrderOfLearningNotOfTheMushaf() {
        assertEquals(AyahRange(112, 1, 4), next(listOf(112, 114), 5))
        assertEquals(114, HifzStatuses.currentSurah(Fx.plan(listOf(112, 114)), Coverage(listOf(Fx.passage(1, 112, 1, 4)), m)))
    }

    @Test
    fun aFinishedPlanHasNoNewPassage() {
        assertNull(next(listOf(114, 113), 5, covered = listOf(114 to 1..6, 113 to 1..5)))
        assertNull(HifzStatuses.currentSurah(Fx.plan(listOf(114)), Coverage(listOf(Fx.passage(1, 114, 1, 6)), m)))
    }

    @Test
    fun theLongSurahOfAnOnePageDayIsCutNotTakenWhole() {
        // Al-Baqarah with 5 ayahs a day: far longer than 7.5 ayahs.
        assertEquals(AyahRange(2, 1, 5), next(listOf(2), 5))
    }
}
