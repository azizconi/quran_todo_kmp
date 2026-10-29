package tj.app.quran_todo.ui.hifz

import kotlin.test.Test
import kotlin.test.assertEquals
import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.hifz.schedule.LessonItem
import tj.app.quran_todo.hifz.schedule.LessonPlan
import tj.app.quran_todo.hifz.schedule.LessonTomorrow

class RecallGradingTest {
    // -- The suggested grade -----------------------------------------------------------------------------------

    @Test
    fun allRememberedIsGood() {
        assertEquals(ReviewGrade.GOOD, RecallGrading.suggest(total = 5, hard = 0))
        assertEquals(ReviewGrade.GOOD, RecallGrading.suggest(total = 1, hard = 0))
    }

    @Test
    fun someHardIsHard() {
        assertEquals(ReviewGrade.HARD, RecallGrading.suggest(total = 5, hard = 1))
        assertEquals(ReviewGrade.HARD, RecallGrading.suggest(total = 5, hard = 2))
        assertEquals(ReviewGrade.HARD, RecallGrading.suggest(total = 10, hard = 4))
    }

    @Test
    fun halfHardOrMoreIsForgot() {
        assertEquals(ReviewGrade.FORGOT, RecallGrading.suggest(total = 4, hard = 2))
        assertEquals(ReviewGrade.FORGOT, RecallGrading.suggest(total = 5, hard = 3))
        assertEquals(ReviewGrade.FORGOT, RecallGrading.suggest(total = 5, hard = 5))
        assertEquals(ReviewGrade.FORGOT, RecallGrading.suggest(total = 10, hard = 5))
    }

    @Test
    fun aSingleHardAyahOfOneIsHalfOrMore() {
        assertEquals(ReviewGrade.FORGOT, RecallGrading.suggest(total = 1, hard = 1))
    }

    @Test
    fun theTippingPointIsExactlyHalf() {
        assertEquals(ReviewGrade.HARD, RecallGrading.suggest(total = 7, hard = 3))
        assertEquals(ReviewGrade.FORGOT, RecallGrading.suggest(total = 7, hard = 4))
    }

    @Test
    fun anEmptyPassageIsGood() {
        assertEquals(ReviewGrade.GOOD, RecallGrading.suggest(total = 0, hard = 0))
    }

    // -- Review or only a check --------------------------------------------------------------------------------

    private fun item(id: Long, surah: Int, start: Int, end: Int, done: Boolean = false) = LessonItem(
        passageId = id, surah = surah, startAyah = start, endAyah = end, stage = PassageStage.R3,
        hardAyahs = emptySet(), done = done, grade = if (done) ReviewGrade.GOOD else null,
        estimateSeconds = 60, overdueDays = 0,
    )

    private fun lesson(recent: List<LessonItem> = emptyList(), old: List<LessonItem> = emptyList()) = LessonPlan(
        today = 20_000, hasPlan = true, planFinished = false, newBlock = null,
        recent = recent, old = old, totalMinutes = 5, remainingMinutes = 5,
        catchingUp = false, deferredCount = 0, isComplete = false, isEmpty = false,
        tomorrow = LessonTomorrow(newPassage = null, dueCount = 0),
    )

    @Test
    fun anExactRangeOfADuePassageCountsAsItsRepetition() {
        val plan = lesson(recent = listOf(item(5, 78, 1, 5)))
        assertEquals(CheckAction.Review(5), CheckRouting.choose(plan, AyahRange(78, 1, 5)))
    }

    @Test
    fun aPassageOfTheOldBlockCountsToo() {
        val plan = lesson(old = listOf(item(9, 67, 1, 8)))
        assertEquals(CheckAction.Review(9), CheckRouting.choose(plan, AyahRange(67, 1, 8)))
    }

    @Test
    fun aRangeThatIsNotExactlyThePassageIsOnlyACheck() {
        val plan = lesson(recent = listOf(item(5, 78, 1, 5)))
        assertEquals(CheckAction.LogCheck, CheckRouting.choose(plan, AyahRange(78, 1, 4)))
        assertEquals(CheckAction.LogCheck, CheckRouting.choose(plan, AyahRange(78, 2, 5)))
        assertEquals(CheckAction.LogCheck, CheckRouting.choose(plan, AyahRange(78, 1, 6)))
        assertEquals(CheckAction.LogCheck, CheckRouting.choose(plan, AyahRange(79, 1, 5)))
    }

    @Test
    fun aPassageThatIsDoneTodayIsNotRepeatedTwice() {
        val plan = lesson(recent = listOf(item(5, 78, 1, 5, done = true)))
        assertEquals(CheckAction.LogCheck, CheckRouting.choose(plan, AyahRange(78, 1, 5)))
    }

    @Test
    fun withoutALessonOrAMatchItIsACheck() {
        assertEquals(CheckAction.LogCheck, CheckRouting.choose(null, AyahRange(78, 1, 5)))
        assertEquals(CheckAction.LogCheck, CheckRouting.choose(lesson(), AyahRange(78, 1, 5)))
    }

    @Test
    fun theFirstPassageThatIsNotDoneWinsWhenTwoShareARange() {
        val plan = lesson(
            recent = listOf(item(1, 78, 1, 5, done = true), item(2, 78, 1, 5)),
        )
        assertEquals(CheckAction.Review(2), CheckRouting.choose(plan, AyahRange(78, 1, 5)))
    }
}
