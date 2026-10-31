package tj.app.quran_todo.ui.quiz

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.hifz.quiz.ContinueQuestion
import tj.app.quran_todo.hifz.quiz.NextQuestion
import tj.app.quran_todo.hifz.quiz.OrderQuestion
import tj.app.quran_todo.hifz.quiz.QuizAnswer
import tj.app.quran_todo.hifz.quiz.QuizAnswers
import tj.app.quran_todo.hifz.quiz.QuizQuestion
import tj.app.quran_todo.hifz.quiz.StartQuestion
import tj.app.quran_todo.hifz.quiz.WhereQuestion

/**
 * A quiz in progress (task F2b): the questions, the answers given so far and what the current question shows. Pure and immutable:
 * every step returns a new session, a step that does not fit the moment returns the same one. The ViewModel holds it, the screen
 * draws it, and `QuizSessionTest` runs through it without any screen.
 *
 * - A **self-graded** question ([ContinueQuestion], [NextQuestion], [StartQuestion]): [reveal] shows the answer, then
 *   [answerSelf] records "knew / did not know" and goes on to the next question at once.
 * - [OrderQuestion]: [pick] numbers the ayahs one by one (a second tap takes the number back), [checkOrder] records the answer and
 *   the question stays on screen with its verdict until [next].
 * - [WhereQuestion]: [choose] records the answer, the verdict stays until [next].
 */
@Immutable
data class QuizSession(
    val questions: List<QuizQuestion>,
    val answers: List<QuizAnswer> = emptyList(),
    /** The question on screen (0-based); equal to the number of questions when the quiz is over. */
    val position: Int = 0,
    /** The answer of the current self-graded question is shown. */
    val revealed: Boolean = false,
    /** The ayahs of the current "put in order" question in the order they were tapped. */
    val picks: List<Int> = emptyList(),
    /** The surah chosen for the current "where" question. */
    val chosen: Int? = null,
) {
    val total: Int get() = questions.size

    val finished: Boolean get() = position >= questions.size

    val current: QuizQuestion? get() = questions.getOrNull(position)

    /** The answer of the current question, once given (an order or a where question waits for [next] after it). */
    val currentAnswer: QuizAnswer? get() = answers.getOrNull(position)

    /** How many questions the person knew (for the result). */
    val known: Int get() = QuizAnswers.known(answers)

    /** Shows the answer of a self-graded question. */
    fun reveal(): QuizSession {
        val question = current ?: return this
        return if (isSelfGraded(question) && currentAnswer == null && !revealed) copy(revealed = true) else this
    }

    /** "I knew it" / "I did not know it": records the answer and goes on to the next question. */
    fun answerSelf(knew: Boolean): QuizSession {
        val question = current ?: return this
        if (!isSelfGraded(question) || currentAnswer != null || !revealed) return this
        return advance(answers + QuizAnswers.selfGraded(question, knew))
    }

    /** A tap on an ayah of a "put in order" question: numbers it with the next place, or takes its number back. */
    fun pick(ayah: Int): QuizSession {
        val question = current as? OrderQuestion ?: return this
        if (currentAnswer != null || ayah !in question.shuffled) return this
        return copy(picks = if (ayah in picks) picks - ayah else picks + ayah)
    }

    /** Takes all the numbers back. */
    fun clearPicks(): QuizSession {
        if (current !is OrderQuestion || currentAnswer != null || picks.isEmpty()) return this
        return copy(picks = emptyList())
    }

    /** Every ayah is numbered: records the answer; the verdict stays on screen. */
    fun checkOrder(): QuizSession {
        val question = current as? OrderQuestion ?: return this
        if (currentAnswer != null || picks.size != question.inOrder.size) return this
        return copy(answers = answers + QuizAnswers.order(question, picks))
    }

    /** The surah chosen for a "where" question: records the answer; the verdict stays on screen. */
    fun choose(surah: Int): QuizSession {
        val question = current as? WhereQuestion ?: return this
        if (currentAnswer != null || surah !in question.options) return this
        return copy(answers = answers + QuizAnswers.where(question, surah), chosen = surah)
    }

    /** Leaves an answered question (after its verdict was seen) for the next one, or for the result after the last. */
    fun next(): QuizSession = if (currentAnswer != null) advance(answers) else this

    private fun advance(newAnswers: List<QuizAnswer>): QuizSession =
        copy(answers = newAnswers, position = position + 1, revealed = false, picks = emptyList(), chosen = null)

    private fun isSelfGraded(question: QuizQuestion) =
        question is ContinueQuestion || question is NextQuestion || question is StartQuestion
}
