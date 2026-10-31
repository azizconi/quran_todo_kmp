package tj.app.quran_todo.common.similar

import tj.app.quran_todo.common.mushaf.AyahKey
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TellApartPairsTest {
    private val index = SimilarIndex.parse(SimilarFixtures.INDEX)
    private val text: (AyahKey) -> String? = { SimilarFixtures.texts[it] }

    private val all: Map<Int, Set<Int>> = mapOf(2 to setOf(35, 58), 7 to setOf(19, 161))

    private fun pick(covered: Map<Int, Set<Int>>, hard: Map<Int, Set<Int>> = emptyMap(), count: Int = 5, seed: Int = 1) =
        TellApartPairs.pick(index, covered, hard, text, count, Random(seed))

    @Test
    fun bothSidesMustBeMemorized() {
        assertEquals(emptyList(), pick(emptyMap()))
        assertEquals(emptyList(), pick(mapOf(2 to setOf(35))))
        assertEquals(emptyList(), pick(mapOf(7 to setOf(19))))
        val pairs = pick(mapOf(2 to setOf(35), 7 to setOf(19)))
        assertEquals(1, pairs.size)
        assertEquals(setOf(AyahKey(2, 35), AyahKey(7, 19)), pairs.single().places.toSet())
    }

    @Test
    fun aWeakLikenessIsNotOffered() {
        // 2:35 and 2:58 have a score of 20 in the index: below MIN_SCORE.
        assertEquals(emptyList(), pick(mapOf(2 to setOf(35, 58))))
        assertEquals(1, TellApartPairs.count(index, all.filterKeys { it == 7 || it == 2 }.mapValues { (_, v) -> v - 58 }))
    }

    @Test
    fun anAyahIsInOnePairOfAQuiz() {
        val pairs = pick(all)
        val places = pairs.flatMap { it.places }
        assertEquals(places.size, places.toSet().size)
        assertEquals(2, pairs.size)
    }

    @Test
    fun theCommonStartIsAtLeastOneWordAndShorterThanBothAyahs() {
        for (pair in pick(all)) {
            assertTrue(pair.shownWords >= 1)
            val a = normalizedWords(SimilarFixtures.texts.getValue(pair.first)).size
            val b = normalizedWords(SimilarFixtures.texts.getValue(pair.second)).size
            assertTrue(pair.shownWords < minOf(a, b))
        }
        // 2:58 and 7:161 begin with "وإذ": one word. 2:35 and 7:19 have no common start: still one word is shown.
        val byFirst = pick(all).associateBy { it.first }
        assertEquals(1, byFirst.getValue(SimilarFixtures.BAQARA_58).shownWords)
        assertEquals(1, byFirst.getValue(SimilarFixtures.BAQARA_35).shownWords)
    }

    @Test
    fun pairsWithADifficultAyahComeFirst() {
        for (seed in 1..20) {
            val first = pick(all, hard = mapOf(7 to setOf(161)), count = 1, seed = seed).single()
            assertTrue(SimilarFixtures.ARAF_161 in first.places, "seed $seed: $first")
        }
    }

    @Test
    fun theSameSeedGivesTheSamePairs() {
        assertEquals(pick(all, seed = 4), pick(all, seed = 4))
    }

    @Test
    fun aTextThatIsNotThereOrIdenticalMakesNoPair() {
        val missing = TellApartPairs.pick(index, all, emptyMap(), { null }, 5, Random(1))
        assertEquals(emptyList(), missing)
        val same = TellApartPairs.pick(index, all, emptyMap(), { SimilarFixtures.AL_ARAF_19 }, 5, Random(1))
        assertEquals(emptyList(), same)
    }

    @Test
    fun anAyahThatIsOnlyTheStartOfTheOtherMakesNoPair() {
        val short = "قال رب اجعل هذا"
        val long = "قال رب اجعل هذا البلد آمنا"
        val texts = mapOf(AyahKey(2, 35) to short, AyahKey(7, 19) to long)
        val pairs = TellApartPairs.pick(index, all, emptyMap(), { texts[it] }, 5, Random(1))
        assertTrue(pairs.none { AyahKey(2, 35) in it.places && AyahKey(7, 19) in it.places })
    }

    @Test
    fun theGroupOfOneAyahAsksAboutThatAyah() {
        val pairs = TellApartPairs.forAyah(index, SimilarFixtures.BAQARA_58, all, emptyMap(), text, 5, Random(2))
        assertTrue(pairs.isNotEmpty())
        assertTrue(pairs.all { it.first == SimilarFixtures.BAQARA_58 })
        // The group takes every score, so 2:35 (score 20) is in it as well as 7:161.
        assertEquals(setOf(SimilarFixtures.ARAF_161, SimilarFixtures.BAQARA_35), pairs.map { it.second }.toSet())
        assertEquals(emptyList(), TellApartPairs.forAyah(index, SimilarFixtures.BAQARA_58, mapOf(7 to setOf(161)), emptyMap(), text, 5, Random(2)))
        assertEquals(emptyList(), TellApartPairs.forAyah(index, SimilarFixtures.BAQARA_58, mapOf(2 to setOf(58)), emptyMap(), text, 5, Random(2)))
    }

    @Test
    fun anEmptyIndexOrAZeroCountGivesNothing() {
        assertEquals(emptyList(), TellApartPairs.pick(SimilarIndex.Empty, all, emptyMap(), text, 5, Random(1)))
        assertEquals(emptyList(), pick(all, count = 0))
        assertEquals(0, TellApartPairs.count(SimilarIndex.Empty, all))
        assertNotEquals(0, TellApartPairs.count(index, all))
    }
}
