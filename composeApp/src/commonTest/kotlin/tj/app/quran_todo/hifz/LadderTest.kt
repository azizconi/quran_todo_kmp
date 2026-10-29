package tj.app.quran_todo.hifz

import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.PassageStage.OLD
import tj.app.quran_todo.hifz.model.PassageStage.R1
import tj.app.quran_todo.hifz.model.PassageStage.R14
import tj.app.quran_todo.hifz.model.PassageStage.R3
import tj.app.quran_todo.hifz.model.PassageStage.R30
import tj.app.quran_todo.hifz.model.PassageStage.R7
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.schedule.HardAyahs
import tj.app.quran_todo.hifz.schedule.HifzConstants
import tj.app.quran_todo.hifz.schedule.Ladder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LadderTest {
    private val today = 1_000

    private fun step(stage: PassageStage, grade: ReviewGrade, lapses: Int = 0) = Ladder.afterReview(stage, grade, today, lapses)

    @Test
    fun theIntervalsOfTheStagesAre_1_3_7_14_30() {
        assertEquals(listOf(1, 3, 7, 14, 30), listOf(R1, R3, R7, R14, R30).map { it.intervalDays })
        assertEquals(HifzConstants.LADDER_INTERVALS, listOf(R1, R3, R7, R14, R30).map { it.intervalDays })
        assertNull(OLD.intervalDays)
    }

    @Test
    fun aNewPassageStartsAtR1AndIsDueTomorrow() {
        val first = Ladder.firstStep(today)
        assertEquals(R1, first.stage)
        assertEquals(today + 1, first.nextDueEpochDay)
        assertEquals(0, first.lapses)
    }

    @Test
    fun goodMovesOneStageUpAndTheLastStageBecomesOld() {
        assertEquals(Ladder.Step(R3, today + 3, 0), step(R1, ReviewGrade.GOOD))
        assertEquals(Ladder.Step(R7, today + 7, 0), step(R3, ReviewGrade.GOOD))
        assertEquals(Ladder.Step(R14, today + 14, 0), step(R7, ReviewGrade.GOOD))
        assertEquals(Ladder.Step(R30, today + 30, 0), step(R14, ReviewGrade.GOOD))
        assertEquals(Ladder.Step(OLD, null, 0), step(R30, ReviewGrade.GOOD))
    }

    @Test
    fun easySkipsAStageAndAnythingPastR30IsOld() {
        assertEquals(Ladder.Step(R7, today + 7, 0), step(R1, ReviewGrade.EASY))
        assertEquals(Ladder.Step(R14, today + 14, 0), step(R3, ReviewGrade.EASY))
        assertEquals(Ladder.Step(R30, today + 30, 0), step(R7, ReviewGrade.EASY))
        assertEquals(Ladder.Step(OLD, null, 0), step(R14, ReviewGrade.EASY))
        assertEquals(Ladder.Step(OLD, null, 0), step(R30, ReviewGrade.EASY))
    }

    @Test
    fun hardRepeatsTheSameStageFromToday() {
        assertEquals(Ladder.Step(R1, today + 1, 0), step(R1, ReviewGrade.HARD))
        assertEquals(Ladder.Step(R3, today + 3, 0), step(R3, ReviewGrade.HARD))
        assertEquals(Ladder.Step(R7, today + 7, 0), step(R7, ReviewGrade.HARD))
        assertEquals(Ladder.Step(R14, today + 14, 0), step(R14, ReviewGrade.HARD))
        assertEquals(Ladder.Step(R30, today + 30, 0), step(R30, ReviewGrade.HARD))
    }

    @Test
    fun forgotGoesBackToR1AndCountsALapse() {
        for (stage in listOf(R1, R3, R7, R14, R30)) {
            assertEquals(Ladder.Step(R1, today + 1, 3), step(stage, ReviewGrade.FORGOT, lapses = 2), "from $stage")
        }
    }

    @Test
    fun lapsesAreNotTouchedByTheOtherGrades() {
        for (stage in listOf(R1, R3, R7, R14, R30)) {
            for (grade in listOf(ReviewGrade.HARD, ReviewGrade.GOOD, ReviewGrade.EASY)) {
                assertEquals(4, step(stage, grade, lapses = 4).lapses, "$stage $grade")
            }
        }
    }

    @Test
    fun theDueDayCountsFromTodayNotFromTheDueDay() {
        // A passage that is a week overdue and is repeated today gets its next interval from today.
        assertEquals(today + 7, step(R3, ReviewGrade.GOOD).nextDueEpochDay)
    }

    @Test
    fun anOldPassageStaysOldOnGoodAndEasy() {
        assertEquals(Ladder.Step(OLD, null, 1), step(OLD, ReviewGrade.GOOD, lapses = 1))
        assertEquals(Ladder.Step(OLD, null, 1), step(OLD, ReviewGrade.EASY, lapses = 1))
    }

    @Test
    fun anOldPassageGoesToR3OnHardWithoutALapseAndToR1OnForgotWithOne() {
        assertEquals(Ladder.Step(R3, today + 3, 1), step(OLD, ReviewGrade.HARD, lapses = 1))
        assertEquals(Ladder.Step(R1, today + 1, 2), step(OLD, ReviewGrade.FORGOT, lapses = 1))
    }

    @Test
    fun aPassageThatGoesThroughTheWholeLadderWithGoodBecomesOldAfterFiveRepetitions() {
        var stage = R1
        var day = today
        var repetitions = 0
        while (stage != OLD) {
            val next = Ladder.afterReview(stage, ReviewGrade.GOOD, day, 0)
            stage = next.stage
            day = next.nextDueEpochDay ?: day
            repetitions++
        }
        assertEquals(5, repetitions)
        // Repeated on the due day each time: R1 -> R3 (+3) -> R7 (+7) -> R14 (+14) -> R30 (+30), the last one is the day R30 falls due.
        assertEquals(today + 3 + 7 + 14 + 30, day)
    }
}

class HardAyahsTest {
    private val clears = HifzConstants.HARD_CLEARS_AFTER

    @Test
    fun markedAyahsGetTwoOnAnyGradeAndMarksOutsideThePassageAreIgnored() {
        for (grade in ReviewGrade.entries) {
            assertEquals(
                mapOf(3 to clears, 5 to clears),
                HardAyahs.afterReview(emptyMap(), grade, setOf(3, 5, 11, 0), startAyah = 1, endAyah = 10),
                "$grade",
            )
        }
    }

    @Test
    fun anAlreadyHardAyahStartsOverWhenItIsMarkedAgain() {
        val result = HardAyahs.afterReview(mapOf(4 to 1), ReviewGrade.HARD, setOf(4), 1, 10)
        assertEquals(mapOf(4 to clears), result)
        val onGood = HardAyahs.afterReview(mapOf(4 to 1), ReviewGrade.GOOD, setOf(4), 1, 10)
        assertEquals(mapOf(4 to clears), onGood, "a mark made in a good repetition is not counted down at once")
    }

    @Test
    fun goodAndEasyCountEveryMarkDownAndTheLastStepRemovesIt() {
        var hard = mapOf(2 to 2, 7 to 1)
        hard = HardAyahs.afterReview(hard, ReviewGrade.GOOD, emptySet(), 1, 10)
        assertEquals(mapOf(2 to 1), hard)
        hard = HardAyahs.afterReview(hard, ReviewGrade.EASY, emptySet(), 1, 10)
        assertEquals(emptyMap(), hard)
    }

    @Test
    fun anAyahClearsAfterTwoSuccessfulRepetitions() {
        var hard = HardAyahs.afterReview(emptyMap(), ReviewGrade.FORGOT, setOf(6), 1, 10)
        assertEquals(mapOf(6 to 2), hard)
        hard = HardAyahs.afterReview(hard, ReviewGrade.GOOD, emptySet(), 1, 10)
        assertEquals(mapOf(6 to 1), hard)
        hard = HardAyahs.afterReview(hard, ReviewGrade.GOOD, emptySet(), 1, 10)
        assertEquals(emptyMap(), hard)
    }

    @Test
    fun hardAndForgotDoNotCountDown() {
        val hard = mapOf(2 to 2, 7 to 1)
        assertEquals(hard, HardAyahs.afterReview(hard, ReviewGrade.HARD, emptySet(), 1, 10))
        assertEquals(hard, HardAyahs.afterReview(hard, ReviewGrade.FORGOT, emptySet(), 1, 10))
    }

    @Test
    fun theMarksOfAPartFollowTheAyahNumbers() {
        val hard = mapOf(2 to 2, 5 to 1, 9 to 2)
        assertEquals(mapOf(2 to 2), HardAyahs.restrictTo(hard, 1, 4))
        assertEquals(mapOf(5 to 1, 9 to 2), HardAyahs.restrictTo(hard, 5, 10))
    }
}
