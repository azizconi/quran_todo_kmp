package tj.app.quran_todo.hifz.quiz

import tj.app.quran_todo.data.bundled.QuranFacts
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The builder of the questions of the quiz "Check" (task F2b): kinds, fitness, variety, small pools, order, options. */
class QuizBuilderTest {
    private val eight: (Int, Int) -> Int = { _, _ -> 8 }

    /** All of juz Amma (surahs 78..114) is covered. */
    private val amma: Map<Int, Set<Int>> = (78..114).associateWith { (1..QuranFacts.ayahCount(it)).toSet() }

    private fun build(covered: Map<Int, Set<Int>>, seed: Int, count: Int = 5, words: (Int, Int) -> Int = eight) =
        QuizBuilder.build(covered, words, count, Random(seed))

    private fun used(quiz: List<QuizQuestion>): List<Pair<Int, Int>> = quiz.flatMap { q -> q.ayahsUsed.map { q.surah to it } }

    @Test
    fun aBigPoolGivesFiveQuestionsOfAtLeastThreeKindsInDifferentSurahs() {
        for (seed in 1..60) {
            val quiz = build(amma, seed)
            assertEquals(5, quiz.size, "seed $seed")
            assertTrue(quiz.map { it.kind }.distinct().size >= 3, "seed $seed: ${quiz.map { it.kind }}")
            assertEquals(5, quiz.map { it.surah }.distinct().size, "seed $seed: ${quiz.map { it.surah }}")
        }
    }

    @Test
    fun anAyahIsNeverInTwoQuestionsOfOneQuiz() {
        for (seed in 1..80) {
            val all = used(build(amma, seed))
            assertEquals(all.size, all.toSet().size, "seed $seed: $all")
        }
    }

    @Test
    fun theSameSeedGivesTheSameQuizAndAnotherSeedAnotherOne() {
        assertEquals(build(amma, 7), build(amma, 7))
        assertNotEquals(build(amma, 7), build(amma, 8))
    }

    @Test
    fun theQuizIsOfferedFromFiveCoveredAyahs() {
        assertFalse(QuizBuilder.isAvailable(emptyMap()))
        assertFalse(QuizBuilder.isAvailable(mapOf(2 to setOf(1, 2, 3, 4))))
        assertTrue(QuizBuilder.isAvailable(mapOf(2 to setOf(1, 2, 3), 3 to setOf(7, 9))))
        assertTrue(QuizBuilder.isAvailable(amma))
    }

    @Test
    fun anEmptyPoolOrAZeroCountGivesNoQuestions() {
        assertTrue(build(emptyMap(), 1).isEmpty())
        assertTrue(build(mapOf(5 to emptySet()), 1).isEmpty())
        assertTrue(build(amma, 1, count = 0).isEmpty())
    }

    // ---- Fitness of every kind ----

    /** Surah 2: ayahs 1-4 and 10; surah 3: 20; surah 4: 1. Few ayahs, so every condition of fitness is tested. */
    private val scattered = mapOf(2 to setOf(1, 2, 3, 4, 10), 3 to setOf(20), 4 to setOf(1))

    @Test
    fun everyQuestionFitsItsKind() {
        for (seed in 1..120) {
            val words: (Int, Int) -> Int = { _, ayah -> if (ayah % 2 == 0) 3 else 9 }
            val quiz = build(scattered, seed, words = words)
            quiz.forEach { question ->
                val covered = scattered.getValue(question.surah)
                when (question) {
                    is ContinueQuestion -> {
                        assertTrue(question.ayah in covered)
                        assertTrue(question.totalWords >= 4 && words(question.surah, question.ayah) == question.totalWords, "$question")
                        assertEquals((question.totalWords + 2) / 3, question.shownWords)
                        assertTrue(question.shownWords < question.totalWords)
                    }
                    is NextQuestion -> assertTrue(question.ayah in covered && question.answerAyah in covered, "$question")
                    is StartQuestion -> assertTrue(1 in covered, "$question")
                    is OrderQuestion -> {
                        assertTrue(question.inOrder.size in 3..4, "$question")
                        assertTrue(question.inOrder.all { it in covered }, "$question")
                        assertEquals(question.inOrder, question.inOrder.sorted())
                        assertTrue(question.inOrder.zipWithNext().all { (a, b) -> b == a + 1 }, "$question")
                        assertEquals(question.inOrder.toSet(), question.shuffled.toSet())
                    }
                    is WhereQuestion -> {
                        assertTrue(question.ayah in covered, "$question")
                        assertTrue(question.ayah >= 2, "the first ayah of a surah is not asked: $question")
                        assertTrue(question.surah in question.options)
                    }
                }
            }
        }
    }

    @Test
    fun theShownWordsAreAThirdRoundedUp() {
        assertEquals(2, QuizBuilder.shownWords(4))
        assertEquals(2, QuizBuilder.shownWords(6))
        assertEquals(3, QuizBuilder.shownWords(7))
        assertEquals(5, QuizBuilder.shownWords(13))
        assertEquals(9, QuizBuilder.shownWords(27))
    }

    @Test
    fun anAyahOfFewerThanFourWordsIsNeverContinued() {
        val quiz = (1..40).flatMap { build(amma, it, words = { _, _ -> 3 }) }
        assertTrue(quiz.none { it is ContinueQuestion })
        assertTrue(quiz.isNotEmpty())
    }

    @Test
    fun aPoolWithoutAFollowingAyahHasNoNextOrOrderQuestion() {
        // Every second ayah of surah 2: nothing follows anything.
        val covered = mapOf(2 to setOf(1, 3, 5, 7, 9, 11))
        val quiz = (1..40).flatMap { build(covered, it) }
        assertTrue(quiz.none { it is NextQuestion || it is OrderQuestion })
        assertTrue(quiz.isNotEmpty())
    }

    @Test
    fun aSurahWhoseFirstAyahIsNotCoveredHasNoStartQuestion() {
        val covered = mapOf(2 to setOf(2, 3, 4, 5, 6, 7))
        val quiz = (1..40).flatMap { build(covered, it) }
        assertTrue(quiz.none { it is StartQuestion })
        assertTrue(quiz.isNotEmpty())
    }

    @Test
    fun aPoolOfFirstAyahsOnlyHasStartQuestionsAndNothingElse() {
        val covered = mapOf(1 to setOf(1), 105 to setOf(1), 106 to setOf(1), 107 to setOf(1), 108 to setOf(1))
        val quiz = build(covered, 3, words = { _, _ -> 2 })
        assertEquals(5, quiz.size)
        assertTrue(quiz.all { it is StartQuestion })
        assertEquals(setOf(1, 105, 106, 107, 108), quiz.map { it.surah }.toSet())
    }

    // ---- A small pool ----

    @Test
    fun aSmallPoolOfFiveAyahsOfOneSurahGivesAVariedQuizWithoutRepeatingAnAyah() {
        val covered = mapOf(67 to setOf(1, 2, 3, 4, 5))
        for (seed in 1..80) {
            val quiz = build(covered, seed)
            val all = used(quiz)
            assertEquals(all.size, all.toSet().size, "seed $seed: $quiz")
            assertTrue(all.all { (_, ayah) -> ayah in 1..5 })
            assertTrue(quiz.size >= 3, "seed $seed: only ${quiz.size}")
            assertTrue(quiz.map { it.kind }.distinct().size >= 3, "seed $seed: ${quiz.map { it.kind }}")
        }
    }

    @Test
    fun aSmallPoolMostlyFillsAllFiveQuestions() {
        val covered = mapOf(67 to setOf(1, 2, 3, 4, 5))
        val full = (1..80).count { build(covered, it).size == 5 }
        assertTrue(full >= 60, "only $full of 80 quizzes had five questions")
    }

    @Test
    fun threeCoveredAyahsGiveWhatTheyCan() {
        val quiz = build(mapOf(1 to setOf(1, 2, 3)), 5)
        assertTrue(quiz.isNotEmpty())
        val all = used(quiz)
        assertEquals(all.size, all.toSet().size)
    }

    // ---- Order ----

    @Test
    fun anOrderQuestionIsNeverInItsOriginalOrder() {
        // A surah of covered ayahs only: Order questions are plenty.
        val covered = mapOf(2 to (1..30).toSet())
        var seen = 0
        for (seed in 1..400) {
            build(covered, seed).filterIsInstance<OrderQuestion>().forEach {
                seen++
                assertNotEquals(it.inOrder, it.shuffled, "seed $seed")
                assertEquals(it.inOrder.sorted(), it.shuffled.sorted())
            }
        }
        assertTrue(seen > 100, "only $seen order questions")
    }

    @Test
    fun theShuffleOfThreeNeverReturnsTheOriginal() {
        for (seed in 1..500) {
            val ayahs = listOf(4, 5, 6)
            assertNotEquals(ayahs, QuizBuilder.shuffledNotInOrder(ayahs, Random(seed)))
        }
        for (seed in 1..200) {
            val ayahs = listOf(4, 5, 6, 7)
            assertNotEquals(ayahs, QuizBuilder.shuffledNotInOrder(ayahs, Random(seed)))
        }
    }

    @Test
    fun anOrderWindowStaysInsideARunOfCoveredAyahs() {
        // Runs of three with a hole between them: no window may bridge a hole.
        val covered = mapOf(2 to setOf(1, 2, 3, 5, 6, 7, 9, 10, 11, 13, 14, 15))
        val runs = listOf(listOf(1, 2, 3), listOf(5, 6, 7), listOf(9, 10, 11), listOf(13, 14, 15))
        var seen = 0
        for (seed in 1..300) {
            build(covered, seed).filterIsInstance<OrderQuestion>().forEach {
                seen++
                assertTrue(it.inOrder in runs, "${it.inOrder}")
            }
        }
        assertTrue(seen > 0)
    }

    // ---- Where ----

    @Test
    fun theOptionsOfWhereAreFourDifferentSurahsWithTheRightOne() {
        for (seed in 1..200) {
            build(amma, seed).filterIsInstance<WhereQuestion>().forEach {
                assertEquals(4, it.options.size, "seed $seed")
                assertEquals(4, it.options.toSet().size, "seed $seed: ${it.options}")
                assertTrue(it.surah in it.options)
                assertTrue(it.options.all { surah -> surah in 78..114 }, "the others are covered surahs: ${it.options}")
            }
        }
    }

    @Test
    fun fewerThanThreeOtherCoveredSurahsAreFilledUpWithNeighbours() {
        // One covered surah: the three others are its nearest neighbours by number (the one before first).
        assertEquals(setOf(66, 67, 68, 65), QuizBuilder.whereOptions(67, listOf(67), Random(1)).toSet())
        // Two covered surahs: the other one and two neighbours.
        val options = QuizBuilder.whereOptions(67, listOf(67, 110), Random(1))
        assertEquals(setOf(67, 110, 66, 68), options.toSet())
        assertEquals(4, options.size)
    }

    @Test
    fun neighboursOfTheFirstAndTheLastSurahStayInsideTheQuran() {
        assertEquals(setOf(1, 2, 3, 4), QuizBuilder.whereOptions(1, listOf(1), Random(2)).toSet())
        assertEquals(setOf(114, 113, 112, 111), QuizBuilder.whereOptions(114, listOf(114), Random(2)).toSet())
        for (surah in 1..114) {
            val options = QuizBuilder.whereOptions(surah, listOf(surah), Random(surah))
            assertEquals(4, options.toSet().size, "surah $surah")
            assertTrue(options.all { it in 1..114 })
            assertTrue(surah in options)
        }
    }

    @Test
    fun aWhereQuestionOfASingleSurahPoolHasNoRepeatedOption() {
        val covered = mapOf(2 to setOf(5, 6, 7, 8, 9, 10))
        for (seed in 1..100) {
            build(covered, seed).filterIsInstance<WhereQuestion>().forEach {
                assertEquals(4, it.options.toSet().size, "seed $seed: ${it.options}")
            }
        }
    }

    // ---- Answers ----

    @Test
    fun aMissedSelfGradedQuestionMarksItsAnswerAyah() {
        val next = NextQuestion(2, 10)
        assertEquals(setOf(11), QuizAnswers.selfGraded(next, knew = false).hardAyahs)
        assertTrue(QuizAnswers.selfGraded(next, knew = true).hardAyahs.isEmpty())
        assertEquals(setOf(1), QuizAnswers.selfGraded(StartQuestion(67), knew = false).hardAyahs)
        assertEquals(setOf(5), QuizAnswers.selfGraded(ContinueQuestion(2, 5, 3, 8), knew = false).hardAyahs)
    }

    @Test
    fun aWrongOrderMarksOnlyTheAyahsThatWereInAWrongPlace() {
        val question = OrderQuestion(2, listOf(4, 5, 6), listOf(5, 4, 6))
        val wrong = QuizAnswers.order(question, listOf(5, 4, 6))
        assertFalse(wrong.knew)
        assertEquals(setOf(4, 5), wrong.hardAyahs)
        val right = QuizAnswers.order(question, listOf(4, 5, 6))
        assertTrue(right.knew)
        assertTrue(right.hardAyahs.isEmpty())
        // Reversed: the middle ayah happens to stay where it belongs.
        assertEquals(setOf(4, 6), QuizAnswers.order(question, listOf(6, 5, 4)).hardAyahs)
    }

    @Test
    fun aWrongSurahOfWhereMarksNothing() {
        val question = WhereQuestion(67, 12, listOf(67, 68, 69, 70))
        val wrong = QuizAnswers.where(question, 68)
        assertFalse(wrong.knew)
        assertTrue(wrong.hardAyahs.isEmpty())
        assertTrue(QuizAnswers.where(question, 67).knew)
        assertTrue(question.hardOnMiss.isEmpty())
    }

    @Test
    fun theHardAyahsAreGroupedBySurahAndTheSummaryCounts() {
        val answers = listOf(
            QuizAnswers.selfGraded(NextQuestion(2, 10), knew = false),
            QuizAnswers.selfGraded(StartQuestion(2), knew = false),
            QuizAnswers.selfGraded(StartQuestion(67), knew = true),
            QuizAnswers.where(WhereQuestion(3, 9, listOf(3, 4, 5, 6)), 4),
            QuizAnswers.order(OrderQuestion(110, listOf(1, 2, 3), listOf(2, 3, 1)), listOf(2, 3, 1)),
        )
        assertEquals(mapOf(2 to setOf(11, 1), 110 to setOf(1, 2, 3)), QuizAnswers.hardBySurah(answers))
        assertEquals(1, QuizAnswers.known(answers))
        assertEquals(4, QuizAnswers.types(answers))
    }
}
