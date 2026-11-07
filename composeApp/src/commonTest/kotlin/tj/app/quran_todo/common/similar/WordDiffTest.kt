package tj.app.quran_todo.common.similar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WordDiffTest {
    private fun AyahWordDiff.differing(text: String): List<String> =
        differingRanges.map { text.substring(it.first, it.last + 1) }

    private fun AyahWordDiff.differingNormalized(text: String): List<String> = differing(text).map(::normalizeWord)

    @Test
    fun adamAndTheTreeInBaqara35AndAraf19() {
        val diff = wordDiff(SimilarFixtures.AL_BAQARA_35, SimilarFixtures.AL_ARAF_19)
        // "And We said: O Adam" against "And O Adam", "and eat of it plentifully" against "so eat from".
        assertEquals(listOf("وقلنا", "يادم", "وكلا", "منها", "رغدا"), diff.a.differingNormalized(SimilarFixtures.AL_BAQARA_35))
        assertEquals(listOf("ويادم", "فكلا", "من"), diff.b.differingNormalized(SimilarFixtures.AL_ARAF_19))
        assertFalse(diff.identical)
    }

    @Test
    fun theVillageInBaqara58AndAraf161() {
        val diff = wordDiff(SimilarFixtures.AL_BAQARA_58, SimilarFixtures.AL_ARAF_161)
        val a = diff.a.differingNormalized(SimilarFixtures.AL_BAQARA_58)
        val b = diff.b.differingNormalized(SimilarFixtures.AL_ARAF_161)
        assertTrue("قلنا" in a && "ادخلوا" in a, "$a")
        assertTrue("قيل" in b && "لهم" in b && "اسكنوا" in b, "$b")
        // "حيث شئتم" and "سجدا" are in both.
        assertFalse("حيث" in a || "حيث" in b)
    }

    @Test
    fun theRangesAreRangesOfTheTextThatWasGiven() {
        val text = SimilarFixtures.AL_BAQARA_35
        val diff = wordDiff(text, SimilarFixtures.AL_ARAF_19)
        assertEquals(diff.a.ranges.size, diff.a.differs.size)
        diff.a.differingRanges.forEach { range ->
            assertTrue(range.first >= 0 && range.last < text.length)
            assertFalse(text.substring(range.first, range.last + 1).contains(' '))
        }
        // The first word of the ayah starts at 0 and the ranges follow each other.
        assertEquals(0, diff.a.ranges.first().first)
        assertTrue(diff.a.ranges.zipWithNext().all { (x, y) -> x.last < y.first })
    }

    @Test
    fun theSameTextHasNoDifference() {
        val diff = wordDiff(SimilarFixtures.AL_ARAF_19, SimilarFixtures.AL_ARAF_19)
        assertTrue(diff.identical)
        assertTrue(diff.a.differingRanges.isEmpty())
    }

    @Test
    fun onlyMarksAndSpellingDoNotDifferInWords() {
        val diff = wordDiff("ٱلْجَنَّةَ وَكُلَا", "الجنه وكلا")
        assertTrue(diff.identical)
    }

    @Test
    fun theRubSignAndPauseMarksAreNotWords() {
        val diff = wordDiff("۞ إِنَّ ٱللَّهَ ۚ غَفُورٌ", "إِنَّ ٱللَّهَ غَفُورٌ")
        assertTrue(diff.identical)
        assertEquals(3, diff.a.ranges.size)
    }

    @Test
    fun aMissingWordDiffersOnTheLongerSideOnly() {
        val diff = wordDiff("قال رب اجعل هذا", "قال رب هذا")
        assertEquals(listOf(false, false, true, false), diff.a.differs)
        assertEquals(listOf(false, false, false), diff.b.differs)
        assertEquals(3, diff.a.matched)
    }

    @Test
    fun anEmptyTextIsAllDifferent() {
        val diff = wordDiff("", "قال رب")
        assertTrue(diff.a.ranges.isEmpty())
        assertEquals(listOf(true, true), diff.b.differs)
    }

    @Test
    fun theCommonStartIsCountedInWords() {
        // "وقلنا يا ادم" and "ويا ادم": the first word differs, so nothing is common at the start.
        assertEquals(0, commonPrefixWords(SimilarFixtures.AL_BAQARA_35, SimilarFixtures.AL_ARAF_19))
        assertEquals(1, commonPrefixWords(SimilarFixtures.AL_BAQARA_58, SimilarFixtures.AL_ARAF_161))
        assertEquals(3, commonPrefixWords("قال رب اجعل هذا", "قَالَ رَبِّ ٱجْعَلْ بلدا"))
        assertEquals(0, commonPrefixWords("", "قال"))
    }
}
