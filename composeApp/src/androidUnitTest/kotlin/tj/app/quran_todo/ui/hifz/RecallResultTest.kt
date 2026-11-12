package tj.app.quran_todo.ui.hifz

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewGrade
import tj.app.quran_todo.ui.i18n.hifzCopy

/** One grading (task U5): the result of a finished recall, when the passage comes back, and the choice made on the sheet. */
class RecallResultTest {
    private fun session(vararg marks: AyahMark?): HifzSession {
        val items = marks.indices.map { HifzItem(number = 100 + it, inSurah = it + 1) }
        val start = HifzSession(surah = 7, items = items)
        return marks.filterNotNull().fold(start) { current, mark -> current.reveal().mark(mark) }
    }

    private val ok = AyahMark.REMEMBERED
    private val hard = AyahMark.HARD

    // -- The state "all answered, not saved yet" --------------------------------------------------------------

    @Test
    fun thereIsNoResultWhileAnAyahIsLeft() {
        assertNull(RecallGrading.resultOf(session(ok, ok, null)))
        assertNull(RecallGrading.resultOf(session(null)))
    }

    @Test
    fun theLastAnswerMakesTheSessionFinishedWithAResult() {
        val before = session(ok, ok).let { HifzSession(it.surah, it.items.dropLast(1).map { item -> item.copy(mark = ok) } + HifzItem(101, 2), index = 1) }
        assertNull(RecallGrading.resultOf(before))
        val after = before.reveal().mark(ok)
        assertEquals(true, after.finished)
        assertEquals(RecallResult(ReviewGrade.GOOD, emptySet()), RecallGrading.resultOf(after))
    }

    @Test
    fun theGradeFollowsTheAnswersAndTheHardAyahsAreTheOnesMarkedHard() {
        assertEquals(RecallResult(ReviewGrade.GOOD, emptySet()), RecallGrading.resultOf(session(ok, ok, ok, ok)))
        assertEquals(RecallResult(ReviewGrade.HARD, setOf(3)), RecallGrading.resultOf(session(ok, ok, hard, ok)))
        assertEquals(RecallResult(ReviewGrade.FORGOT, setOf(1, 4)), RecallGrading.resultOf(session(hard, ok, ok, hard)))
    }

    @Test
    fun theChoiceMadeOnTheSheetReplacesTheSuggestion() {
        val chosen = RecallResult(ReviewGrade.EASY, setOf(2))
        assertEquals(chosen, RecallGrading.resultOf(session(ok, ok, ok), chosen))
        // ... but not before the last ayah is answered.
        assertNull(RecallGrading.resultOf(session(ok, null), chosen))
    }

    @Test
    fun aFinishedSessionSurvivesTheTextFormOfTheLesson() {
        val finished = session(ok, hard, ok)
        val restored = HifzSession.decode(finished.encode())
        assertEquals(finished, restored)
        assertEquals(RecallGrading.resultOf(finished), RecallGrading.resultOf(restored!!))
    }

    @Test
    fun theChoiceSurvivesItsTextForm() {
        val chosen = RecallResult(ReviewGrade.HARD, setOf(5, 2))
        assertEquals(chosen, RecallResult.decode(chosen.encode()))
        val none = RecallResult(ReviewGrade.EASY, emptySet())
        assertEquals(none, RecallResult.decode(none.encode()))
        assertNull(RecallResult.decode(""))
        assertNull(RecallResult.decode("nonsense"))
        assertNull(RecallResult.decode("GOOD|a,b"))
    }

    // -- When the passage comes back --------------------------------------------------------------------------

    @Test
    fun aNewPassageComesBackTomorrowWhateverTheGrade() {
        ReviewGrade.entries.forEach { assertEquals(1, RecallGrading.returnDays(null, it)) }
    }

    @Test
    fun aPassageOnTheLadderFollowsTheLadder() {
        assertEquals(1, RecallGrading.returnDays(PassageStage.R7, ReviewGrade.FORGOT))
        assertEquals(7, RecallGrading.returnDays(PassageStage.R7, ReviewGrade.HARD))
        assertEquals(14, RecallGrading.returnDays(PassageStage.R7, ReviewGrade.GOOD))
        assertEquals(30, RecallGrading.returnDays(PassageStage.R7, ReviewGrade.EASY))
        assertEquals(3, RecallGrading.returnDays(PassageStage.R1, ReviewGrade.GOOD))
    }

    @Test
    fun whenThePassageBecomesOldTheDateIsLeftOut() {
        assertNull(RecallGrading.returnDays(PassageStage.R30, ReviewGrade.GOOD))
        assertNull(RecallGrading.returnDays(PassageStage.R14, ReviewGrade.EASY))
        assertNull(RecallGrading.returnDays(PassageStage.OLD, ReviewGrade.GOOD))
        assertEquals(1, RecallGrading.returnDays(PassageStage.OLD, ReviewGrade.FORGOT))
        assertEquals(3, RecallGrading.returnDays(PassageStage.OLD, ReviewGrade.HARD))
    }

    @Test
    fun theLineOfTheResultIsFilledInEveryLanguage() {
        AppLanguage.entries.forEach { language ->
            val copy = hifzCopy(language)
            assertEquals(false, copy.result(ReviewGrade.GOOD).contains("{"), "$language result")
            assertEquals(copy.returnsTomorrow, copy.returns(1), "$language tomorrow")
            assertEquals(false, copy.returns(7).contains("{"), "$language days")
            assertEquals(true, copy.returns(7).contains("7"), "$language days")
        }
    }
}
