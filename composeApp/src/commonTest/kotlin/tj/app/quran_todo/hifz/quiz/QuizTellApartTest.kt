package tj.app.quran_todo.hifz.quiz

import tj.app.quran_todo.common.mushaf.AyahKey
import tj.app.quran_todo.common.similar.TellApartPair
import tj.app.quran_todo.data.bundled.QuranFacts
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The question "Tell apart" of the quiz (task F6): the draw, the answer, and what is marked difficult. */
class QuizTellApartTest {
    private val amma: Map<Int, Set<Int>> = (78..114).associateWith { (1..QuranFacts.ayahCount(it)).toSet() }

    private fun pair(a: AyahKey, b: AyahKey, shown: Int = 1) = TellApartPair(a, b, shown, 70)

    private val pairs = listOf(
        pair(AyahKey(82, 6), AyahKey(84, 6)),
        pair(AyahKey(101, 1), AyahKey(102, 1)),
        pair(AyahKey(90, 4), AyahKey(95, 4)),
        pair(AyahKey(100, 3), AyahKey(103, 3)),
    )

    private fun build(seed: Int, pairs: List<TellApartPair> = this.pairs) =
        QuizBuilder.build(amma, { _, _ -> 8 }, 5, Random(seed), pairs)

    @Test
    fun withoutPairsThereIsNoTellApartQuestion() {
        for (seed in 1..40) assertTrue(build(seed, emptyList()).none { it.kind == QuizKind.TELL_APART }, "seed $seed")
    }

    @Test
    fun aQuizHasUpToTwoTellApartQuestionsAndOftenOne() {
        var withOne = 0
        for (seed in 1..80) {
            val quiz = build(seed)
            val count = quiz.count { it.kind == QuizKind.TELL_APART }
            assertTrue(count <= QuizBuilder.MAX_TELL_APART, "seed $seed: $count")
            if (count > 0) withOne++
            assertEquals(5, quiz.size, "seed $seed")
        }
        assertTrue(withOne >= 40, "only $withOne of 80 quizzes had a tell-apart question")
    }

    @Test
    fun anAyahOfATellApartQuestionIsInNoOtherQuestion() {
        for (seed in 1..80) {
            val quiz = build(seed)
            val places = quiz.flatMap { it.places }
            assertEquals(places.size, places.toSet().size, "seed $seed: $places")
        }
    }

    @Test
    fun theQuestionKeepsBothPlacesAndTheShownWords() {
        val question = pair(AyahKey(82, 6), AyahKey(84, 6), shown = 3).toQuestion()
        assertEquals(setOf(AyahKey(82, 6), AyahKey(84, 6)), question.places)
        assertEquals(3, question.shownWords)
        assertEquals(QuizKind.TELL_APART, question.kind)
        assertEquals(82, question.surah)
        assertEquals(6, question.focusAyah)
    }

    @Test
    fun mixingThemUpMarksBothAyahsInTheirOwnSurahs() {
        val question = pair(AyahKey(82, 6), AyahKey(84, 6)).toQuestion()
        val missed = QuizAnswers.selfGraded(question, knew = false)
        assertFalse(missed.knew)
        assertEquals(mapOf(82 to setOf(6), 84 to setOf(6)), QuizAnswers.hardBySurah(listOf(missed)))
    }

    @Test
    fun tellingThemApartMarksNothing() {
        val question = pair(AyahKey(82, 6), AyahKey(84, 6)).toQuestion()
        val right = QuizAnswers.selfGraded(question, knew = true)
        assertTrue(right.knew)
        assertEquals(emptyMap(), QuizAnswers.hardBySurah(listOf(right)))
    }

    @Test
    fun theMarksOfSeveralAnswersAreCollectedBySurah() {
        val a = QuizAnswers.selfGraded(pair(AyahKey(82, 6), AyahKey(84, 6)).toQuestion(), knew = false)
        val b = QuizAnswers.selfGraded(pair(AyahKey(84, 9), AyahKey(95, 4)).toQuestion(), knew = false)
        val c = QuizAnswers.selfGraded(ContinueQuestion(90, 4, 3, 8), knew = false)
        assertEquals(
            mapOf(82 to setOf(6), 84 to setOf(6, 9), 95 to setOf(4), 90 to setOf(4)),
            QuizAnswers.hardBySurah(listOf(a, b, c)),
        )
    }

    @Test
    fun knownAndTypesCountTellApartToo() {
        val answers = listOf(
            QuizAnswers.selfGraded(pair(AyahKey(82, 6), AyahKey(84, 6)).toQuestion(), knew = true),
            QuizAnswers.selfGraded(ContinueQuestion(90, 4, 3, 8), knew = true),
        )
        assertEquals(2, QuizAnswers.types(answers))
        assertEquals(2, QuizAnswers.known(answers))
    }
}
