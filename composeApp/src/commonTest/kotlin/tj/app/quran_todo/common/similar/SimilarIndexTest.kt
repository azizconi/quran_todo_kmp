package tj.app.quran_todo.common.similar

import tj.app.quran_todo.common.mushaf.AyahKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SimilarIndexTest {
    private val index = SimilarIndex.parse(SimilarFixtures.INDEX)

    @Test
    fun readsThePairsBestFirst() {
        assertEquals(listOf(SimilarAyah(AyahKey(7, 19), 81), SimilarAyah(AyahKey(2, 58), 20)), index.similar(AyahKey(2, 35)))
        assertEquals(listOf(SimilarAyah(AyahKey(7, 161), 60), SimilarAyah(AyahKey(2, 35), 20)), index.similar(AyahKey(2, 58)))
    }

    @Test
    fun aPairIsSymmetric() {
        for (ref in index.ayahs) {
            for (other in index.similar(ref)) {
                assertTrue(index.similar(other.ref).any { it.ref == ref && it.score == other.score }, "$ref <-> ${other.ref}")
            }
        }
    }

    @Test
    fun readsARefrainWithAllItsPlaces() {
        val group = index.repeats(AyahKey(55, 16))
        assertEquals(listOf(AyahKey(55, 13), AyahKey(55, 16), AyahKey(55, 18)), group)
        assertEquals(group, index.repeats(AyahKey(55, 13)))
        assertNull(index.repeats(AyahKey(2, 35)))
        assertTrue(index.similar(AyahKey(55, 13)).isEmpty())
        assertEquals(2, index.count(AyahKey(55, 13)))
        assertTrue(index.has(AyahKey(55, 18)))
    }

    @Test
    fun aDamagedLineOrEntryCostsOnlyItself() {
        assertFalse(index.has(AyahKey(3, 4)))
        assertEquals(4, index.ayahs.size)
        assertEquals(3, index.pairCount)
    }

    @Test
    fun anEmptyIndexKnowsNothing() {
        for (empty in listOf(SimilarIndex.Empty, SimilarIndex.parse(""), SimilarIndex.parse("# only a comment\n\n"))) {
            assertTrue(empty.similar(AyahKey(2, 35)).isEmpty())
            assertNull(empty.repeats(AyahKey(55, 13)))
            assertEquals(0, empty.count(AyahKey(2, 35)))
            assertFalse(empty.has(AyahKey(2, 35)))
            assertTrue(empty.ayahs.isEmpty())
        }
    }

    @Test
    fun aScoreIsKeptInZeroToHundred() {
        val odd = SimilarIndex.parse("P\t2:1\t3:1=250\t3:2=-5\n")
        assertEquals(listOf(100, 0), odd.similar(AyahKey(2, 1)).map { it.score })
    }
}
