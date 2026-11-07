package tj.app.quran_todo.ui.quiz

import tj.app.quran_todo.hifz.quiz.ContinueQuestion
import tj.app.quran_todo.hifz.quiz.NextQuestion
import tj.app.quran_todo.hifz.quiz.OrderQuestion
import tj.app.quran_todo.hifz.quiz.QuizQuestion
import tj.app.quran_todo.hifz.quiz.StartQuestion
import tj.app.quran_todo.hifz.quiz.WhereQuestion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The steps of a quiz in progress (task F2b): what each tap does at each moment, and what it does not. */
class QuizSessionTest {
    private val cont = ContinueQuestion(surah = 67, ayah = 5, shownWords = 2, totalWords = 6)
    private val next = NextQuestion(surah = 2, ayah = 10)
    private val start = StartQuestion(surah = 110)
    private val order = OrderQuestion(surah = 3, inOrder = listOf(4, 5, 6), shuffled = listOf(6, 4, 5))
    private val where = WhereQuestion(surah = 67, ayah = 12, options = listOf(66, 67, 68, 69))

    private fun session(vararg questions: QuizQuestion) = QuizSession(questions.toList())

    // ---- Self-graded: continue, next, start ----

    @Test
    fun aSelfGradedQuestionIsRevealedThenAnsweredAndTheQuizGoesOn() {
        val begun = session(cont, next)
        assertEquals(0, begun.position)
        assertFalse(begun.revealed)

        val shown = begun.reveal()
        assertTrue(shown.revealed)

        val answered = shown.answerSelf(knew = false)
        assertEquals(1, answered.position)
        assertFalse(answered.revealed, "the next question starts hidden")
        assertEquals(1, answered.answers.size)
        assertFalse(answered.answers.single().knew)
        assertEquals(setOf(5), answered.answers.single().hardAyahs)
        assertEquals(next, answered.current)
    }

    @Test
    fun knewItMarksNothing() {
        val answered = session(next).reveal().answerSelf(knew = true)
        assertTrue(answered.answers.single().knew)
        assertTrue(answered.answers.single().hardAyahs.isEmpty())
        assertTrue(answered.finished)
        assertEquals(1, answered.known)
    }

    @Test
    fun theAnswerCannotBeGivenBeforeItIsShown() {
        val begun = session(start)
        assertSame(begun, begun.answerSelf(knew = true))
        assertTrue(begun.answers.isEmpty())
    }

    @Test
    fun theLastSelfGradedAnswerEndsTheQuiz() {
        val done = session(start).reveal().answerSelf(knew = false)
        assertTrue(done.finished)
        assertNull(done.current)
        assertEquals(1, done.total)
        assertEquals(setOf(1), done.answers.single().hardAyahs)
    }

    @Test
    fun revealingTwiceOrAnOrderQuestionDoesNothing() {
        val shown = session(cont).reveal()
        assertSame(shown, shown.reveal())
        val ordering = session(order)
        assertSame(ordering, ordering.reveal())
        val asking = session(where)
        assertSame(asking, asking.reveal())
        assertSame(asking, asking.answerSelf(true))
    }

    // ---- Put in order ----

    @Test
    fun anOrderIsMadeByTappingAndTheCheckNeedsEveryAyah() {
        var s = session(order, cont)
        s = s.pick(5)
        assertEquals(listOf(5), s.picks)
        assertSame(s, s.checkOrder(), "not every ayah is numbered yet")
        s = s.pick(4).pick(6)
        assertEquals(listOf(5, 4, 6), s.picks)
        val checked = s.checkOrder()
        assertEquals(0, checked.position, "the question stays on screen with its verdict")
        assertEquals(1, checked.answers.size)
        assertFalse(checked.currentAnswer!!.knew)
        assertEquals(setOf(4, 5), checked.currentAnswer!!.hardAyahs, "only the ayahs in a wrong place")
    }

    @Test
    fun aSecondTapTakesTheNumberBackAndTheOthersCloseUp() {
        val s = session(order).pick(6).pick(4).pick(5)
        assertEquals(listOf(6, 4, 5), s.picks)
        assertEquals(listOf(6, 5), s.pick(4).picks)
        assertEquals(listOf(4, 5), s.pick(6).picks)
        assertEquals(emptyList(), s.clearPicks().picks)
    }

    @Test
    fun anAyahOfAnotherQuestionIsNotAPick() {
        val s = session(order)
        assertSame(s, s.pick(99))
        assertSame(s, s.pick(1))
    }

    @Test
    fun theRightOrderIsKnownAndMarksNothing() {
        val checked = session(order).pick(4).pick(5).pick(6).checkOrder()
        assertTrue(checked.currentAnswer!!.knew)
        assertTrue(checked.currentAnswer!!.hardAyahs.isEmpty())
    }

    @Test
    fun afterTheVerdictTheNumbersAreFrozenAndNextGoesOn() {
        val checked = session(order, start).pick(6).pick(5).pick(4).checkOrder()
        assertSame(checked, checked.pick(4))
        assertSame(checked, checked.clearPicks())
        assertSame(checked, checked.checkOrder())
        val moved = checked.next()
        assertEquals(1, moved.position)
        assertTrue(moved.picks.isEmpty())
        assertEquals(start, moved.current)
        assertEquals(1, moved.answers.size)
    }

    // ---- Where ----

    @Test
    fun aChoiceOfWhereIsRecordedAndMarksNothingWhenWrong() {
        val wrong = session(where, start).choose(68)
        assertEquals(68, wrong.chosen)
        assertFalse(wrong.currentAnswer!!.knew)
        assertTrue(wrong.currentAnswer!!.hardAyahs.isEmpty())
        assertEquals(0, wrong.position)
        // A second choice does not replace the first.
        assertSame(wrong, wrong.choose(67))

        val right = session(where).choose(67)
        assertTrue(right.currentAnswer!!.knew)
        assertEquals(67, right.chosen)
    }

    @Test
    fun aSurahThatIsNotAnOptionIsNotAChoice() {
        val s = session(where)
        assertSame(s, s.choose(2))
        val asking = session(cont)
        assertSame(asking, asking.choose(67))
    }

    // ---- The whole quiz ----

    @Test
    fun nextBeforeTheAnswerDoesNothingAndTheQuizEndsAfterTheLastNext() {
        val s = session(where)
        assertSame(s, s.next())
        val done = s.choose(67).next()
        assertTrue(done.finished)
        assertNull(done.current)
        assertEquals(1, done.known)
    }

    @Test
    fun aWholeQuizCountsWhatWasKnown() {
        val done = session(cont, next, start, order, where)
            .reveal().answerSelf(true)
            .reveal().answerSelf(false)
            .reveal().answerSelf(true)
            .pick(4).pick(5).pick(6).checkOrder().next()
            .choose(66).next()
        assertTrue(done.finished)
        assertEquals(5, done.answers.size)
        assertEquals(3, done.known)
        assertEquals(setOf(11), done.answers[1].hardAyahs)
    }
}
