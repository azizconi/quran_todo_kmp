package tj.app.quran_todo.hifz

import tj.app.quran_todo.hifz.drill.HardDrill
import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.Passage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.schedule.HifzStatuses
import tj.app.quran_todo.hifz.schedule.LessonBuilder
import tj.app.quran_todo.hifz.schedule.LessonInput
import tj.app.quran_todo.hifz.schedule.LessonItem
import tj.app.quran_todo.hifz.schedule.LessonPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The drill of the difficult ayahs (task F2a): `HifzRepositoryImpl.drillHard` writes exactly what [HardDrill.apply] returns, so
 * the rules are proved here on the pure function: the counter goes 2 -> 1 -> gone, "still hard" starts over at 2, and nothing
 * but the marks of the trained ayahs moves - the lesson, the ladder and the circle of the manzil are the same before and after.
 */
class HardDrillTest {
    private val m = Fx.flat
    private val today = 1_000
    private val plan = Fx.plan(listOf(114, 113, 112), daily = 5, started = today - 10)

    // A ladder passage with two hard ayahs, an overdue one, and old passages of another surah, one with a hard ayah.
    private val ladder = Fx.passage(1, 2, 1, 10, PassageStage.R3, due = today, last = today - 3, lapses = 1, learned = today - 5, hard = mapOf(3 to 2, 7 to 1))
    private val late = Fx.passage(2, 2, 11, 20, PassageStage.R1, due = today - 2, last = today - 3)
    private val old1 = Fx.passage(3, 3, 1, 10, PassageStage.OLD, last = today - 20, hard = mapOf(4 to 2))
    private val old2 = Fx.passage(4, 3, 11, 20, PassageStage.OLD, last = today - 15)
    private val passages = listOf(ladder, late, old1, old2)

    private fun hardOf(list: List<Passage>, id: Long): Map<Int, Int> = list.first { it.id == id }.hard

    private fun apply(remembered: Set<Int> = emptySet(), stillHard: Set<Int> = emptySet(), surah: Int = 2, from: List<Passage> = passages) =
        HardDrill.apply(from, surah, remembered, stillHard)

    // -- The counter ---------------------------------------------------------------------------------------------

    @Test
    fun aRememberedAyahGoesFromTwoToOneToGone() {
        val first = assertNotNull(apply(remembered = setOf(3)))
        assertEquals(1, hardOf(first.passages, 1)[3], "the second step of the counter")
        val second = assertNotNull(apply(remembered = setOf(3), from = first.passages))
        assertNull(hardOf(second.passages, 1)[3], "the mark is gone after the second time")
        assertEquals(setOf(7), hardOf(second.passages, 1).keys, "the other ayah keeps its mark")
    }

    @Test
    fun anAyahAtOneIsGoneAtOnce() {
        val outcome = assertNotNull(apply(remembered = setOf(7)))
        assertEquals(mapOf(3 to 2), hardOf(outcome.passages, 1))
    }

    @Test
    fun stillHardStartsOverAtTwo() {
        val outcome = assertNotNull(apply(stillHard = setOf(7, 3)))
        assertEquals(mapOf(3 to 2, 7 to 2), hardOf(outcome.passages, 1))
        // After one "remembered" (2 -> 1) it goes back to 2.
        val once = assertNotNull(apply(remembered = setOf(3)))
        val again = assertNotNull(apply(stillHard = setOf(3), from = once.passages))
        assertEquals(2, hardOf(again.passages, 1)[3])
    }

    @Test
    fun anAyahInBothSetsCountsAsStillHard() {
        val outcome = assertNotNull(apply(remembered = setOf(3), stillHard = setOf(3)))
        assertEquals(2, hardOf(outcome.passages, 1)[3])
        assertEquals(ReviewGrade.FORGOT, outcome.grade, "the one trained ayah is still hard")
    }

    @Test
    fun anAyahThatIsNotHardAnyMoreOrNotCoveredIsSkipped() {
        // 5 is in the passage but not marked, 50 is not in any passage of the surah, 4 is hard but in another surah.
        assertNull(apply(remembered = setOf(5, 50, 4)))
        assertNull(apply(stillHard = setOf(5, 50, 4)))
        val mixed = assertNotNull(apply(remembered = setOf(5, 3), stillHard = setOf(50)))
        assertEquals(setOf(3), mixed.trained)
        assertEquals(AyahRange(2, 3, 3), mixed.range, "the range is the trained ayahs, not the asked ones")
    }

    @Test
    fun nothingToTrainIsNothingWritten() {
        assertNull(apply())
        assertNull(HardDrill.apply(emptyList(), 2, setOf(1), setOf(2)))
        assertNull(apply(remembered = setOf(3), surah = 9))
    }

    // -- Only the marks move -------------------------------------------------------------------------------------

    @Test
    fun stagesDueDaysLastReviewedAndLapsesDoNotChange() {
        val outcome = assertNotNull(apply(remembered = setOf(3), stillHard = setOf(7)))
        passages.zip(outcome.passages).forEach { (before, after) ->
            assertEquals(before.copy(hard = emptyMap()), after.copy(hard = emptyMap()), "passage ${before.id}")
        }
        // The same ids in the same order, nothing was added or removed.
        assertEquals(passages.map { it.id }, outcome.passages.map { it.id })
    }

    @Test
    fun theMarksOfOtherSurahsAndOtherAyahsStayAsTheyWere() {
        val outcome = assertNotNull(apply(remembered = setOf(3)))
        assertEquals(old1.hard, hardOf(outcome.passages, 3))
        assertEquals(mapOf(7 to 1), hardOf(outcome.passages, 1).filterKeys { it != 3 })
        assertEquals(late.hard, hardOf(outcome.passages, 2))
    }

    private fun LessonPlan.withoutHardMarks(): LessonPlan {
        fun List<LessonItem>.clean() = map { it.copy(hardAyahs = emptySet()) }
        return copy(recent = recent.clean(), old = old.clean())
    }

    private fun lesson(list: List<Passage>): LessonPlan = LessonBuilder.build(LessonInput(today, plan, list, emptyList(), m))

    @Test
    fun theLessonIsTheSameBeforeAndAfterExceptForTheHardAyahsOfItsCards() {
        val before = lesson(passages)
        val outcome = assertNotNull(apply(remembered = setOf(3, 7), stillHard = emptySet()))
        val after = lesson(outcome.passages)
        assertEquals(before.withoutHardMarks(), after.withoutHardMarks())
        // The cards differ in the marks only: the ladder passage had two hard ayahs; 7 (at 1) is gone, 3 (at 2) is at 1.
        assertEquals(setOf(3, 7), before.recent.first { it.passageId == 1L }.hardAyahs)
        assertEquals(setOf(3), after.recent.first { it.passageId == 1L }.hardAyahs)
    }

    @Test
    fun theLessonOfAnOldPassageIsUnchangedToo() {
        val before = lesson(passages)
        val outcome = assertNotNull(apply(stillHard = setOf(4), surah = 3))
        val after = lesson(outcome.passages)
        assertEquals(before.withoutHardMarks(), after.withoutHardMarks())
        assertEquals(before.totalMinutes, after.totalMinutes)
        assertEquals(before.tomorrow, after.tomorrow)
    }

    @Test
    fun theHardAyahsOfTheQuranShrinkExactlyByTheRememberedOnes() {
        val before = HifzStatuses.hardAyahs(passages)
        assertEquals(mapOf(2 to setOf(3, 7), 3 to setOf(4)), before)
        val after = HifzStatuses.hardAyahs(assertNotNull(apply(remembered = setOf(7), stillHard = setOf(3))).passages)
        assertEquals(mapOf(2 to setOf(3), 3 to setOf(4)), after)
    }

    // -- The record of the log -----------------------------------------------------------------------------------

    @Test
    fun theLogRecordCoversFirstToLastTrainedAyahAndTheOnePassageThatHoldsThem() {
        val outcome = assertNotNull(apply(remembered = setOf(3, 7)))
        assertEquals(AyahRange(2, 3, 7), outcome.range)
        assertEquals(1L, outcome.passageId)
    }

    @Test
    fun aRangeSpanningTwoPassagesHasNoPassageId() {
        val left = Fx.passage(1, 5, 1, 6, PassageStage.OLD, hard = mapOf(2 to 2))
        val right = Fx.passage(2, 5, 7, 12, PassageStage.OLD, hard = mapOf(9 to 2))
        val outcome = assertNotNull(HardDrill.apply(listOf(left, right), 5, setOf(2, 9), emptySet()))
        assertEquals(AyahRange(5, 2, 9), outcome.range)
        assertNull(outcome.passageId)
        assertEquals(setOf(1L, 2L), outcome.passages.map { it.id }.toSet())
    }

    @Test
    fun theGradeFollowsTheRuleOfTheRecallStep() {
        // Nothing still hard: GOOD.
        assertEquals(ReviewGrade.GOOD, assertNotNull(apply(remembered = setOf(3, 7))).grade)
        // Half of the trained ayahs or more still hard: FORGOT (one of two, and one of one).
        assertEquals(ReviewGrade.FORGOT, assertNotNull(apply(remembered = setOf(3), stillHard = setOf(7))).grade)
        assertEquals(ReviewGrade.FORGOT, assertNotNull(apply(stillHard = setOf(3))).grade)
        assertEquals(ReviewGrade.FORGOT, assertNotNull(apply(stillHard = setOf(3, 7))).grade)
        // A skipped "still hard" ayah does not count: the drill trained one ayah and it was remembered.
        assertEquals(ReviewGrade.GOOD, assertNotNull(apply(remembered = setOf(3), stillHard = setOf(50))).grade)
    }

    @Test
    fun lessThanHalfStillHardIsHardAndExactlyHalfIsForgot() {
        val many = Fx.passage(1, 5, 1, 12, hard = mapOf(1 to 2, 2 to 2, 3 to 2, 4 to 2))
        fun grade(remembered: Set<Int>, stillHard: Set<Int>) =
            assertNotNull(HardDrill.apply(listOf(many), 5, remembered, stillHard)).grade
        assertEquals(ReviewGrade.HARD, grade(setOf(1, 2, 3), setOf(4)), "1 of 4")
        assertEquals(ReviewGrade.FORGOT, grade(setOf(1, 2), setOf(3, 4)), "2 of 4 is half")
        assertEquals(ReviewGrade.FORGOT, grade(setOf(1), setOf(2, 3, 4)), "3 of 4")
        assertEquals(ReviewGrade.HARD, grade(setOf(1, 2), setOf(3)), "1 of 3")
        assertEquals(ReviewGrade.GOOD, grade(setOf(1, 2, 3, 4), emptySet()))
        // The rule itself, as a table.
        assertEquals(ReviewGrade.GOOD, HardDrill.gradeOf(trained = 5, stillHard = 0))
        assertEquals(ReviewGrade.HARD, HardDrill.gradeOf(trained = 5, stillHard = 2))
        assertEquals(ReviewGrade.FORGOT, HardDrill.gradeOf(trained = 5, stillHard = 3))
        assertEquals(ReviewGrade.GOOD, HardDrill.gradeOf(trained = 0, stillHard = 0))
    }

    @Test
    fun aDrillOfAnAyahOfAnOldPassageKeepsItInTheCircle() {
        val outcome = assertNotNull(apply(remembered = setOf(4), surah = 3))
        val after = outcome.passages.first { it.id == 3L }
        assertEquals(PassageStage.OLD, after.stage)
        assertNull(after.nextDueEpochDay)
        assertEquals(old1.lastReviewedEpochDay, after.lastReviewedEpochDay)
        assertTrue(after.hard.isEmpty().not(), "from 2 to 1: still marked")
    }
}
