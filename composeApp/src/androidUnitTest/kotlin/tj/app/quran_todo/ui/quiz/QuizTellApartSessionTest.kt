package tj.app.quran_todo.ui.quiz

import tj.app.quran_todo.hifz.quiz.ContinueQuestion
import tj.app.quran_todo.hifz.quiz.QuizAnswers
import tj.app.quran_todo.hifz.quiz.TellApartQuestion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The question "Tell apart" (task F6) in a session: "Show", then "Told apart" / "Mixed up"; only a miss marks, and only the two ayahs. */
class QuizTellApartSessionTest {
    private val tell = TellApartQuestion(surah = 82, ayah = 6, otherSurah = 84, otherAyah = 6, shownWords = 1)
    private val other = ContinueQuestion(surah = 90, ayah = 4, shownWords = 3, totalWords = 8)

    @Test
    fun theAnswerWaitsForShow() {
        val session = QuizSession(listOf(tell, other))
        assertEquals(session, session.answerSelf(true), "no answer before the places are shown")
        assertTrue(session.reveal().revealed)
    }

    @Test
    fun toldApartGoesOnAndMarksNothing() {
        val after = QuizSession(listOf(tell, other)).reveal().answerSelf(true)
        assertEquals(1, after.position)
        assertTrue(after.answers.single().knew)
        assertEquals(emptyMap(), QuizAnswers.hardBySurah(after.answers))
        assertFalse(after.revealed)
    }

    @Test
    fun mixedUpMarksBothAyahsEachInItsSurah() {
        val after = QuizSession(listOf(tell, other)).reveal().answerSelf(false)
        assertFalse(after.answers.single().knew)
        assertEquals(mapOf(82 to setOf(6), 84 to setOf(6)), QuizAnswers.hardBySurah(after.answers))
    }

    @Test
    fun theLastQuestionFinishesTheQuiz() {
        val after = QuizSession(listOf(tell)).reveal().answerSelf(true)
        assertTrue(after.finished)
        assertEquals(1, after.known)
    }
}
