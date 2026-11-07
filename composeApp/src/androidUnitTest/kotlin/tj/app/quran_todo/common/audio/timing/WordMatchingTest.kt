package tj.app.quran_todo.common.audio.timing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WordMatchingTest {
    private val spans = listOf(
        WordSpan(0, 100, 500),
        WordSpan(1, 510, 900),
        WordSpan(2, 910, 1500),
        WordSpan(3, 1510, 4000),
    )

    @Test
    fun theWordThatSoundsIsTheLastOneThatHasStarted() {
        assertEquals(-1, wordAt(spans, 0))
        assertEquals(-1, wordAt(spans, 99))
        assertEquals(0, wordAt(spans, 100))
        assertEquals(0, wordAt(spans, 499))
        assertEquals(1, wordAt(spans, 510))
        assertEquals(2, wordAt(spans, 1_499))
        assertEquals(3, wordAt(spans, 1_510))
        assertEquals(3, wordAt(spans, 3_999))
    }

    @Test
    fun aGapBetweenTwoWordsKeepsTheWordThatWasJustSaid() {
        // 500..510 is silence between the first and the second word: no blink.
        assertEquals(0, wordAt(spans, 505))
        // After the last word the highlight stays until the ayah ends.
        assertEquals(3, wordAt(spans, 10_000))
    }

    @Test
    fun aWordWithoutASpanIsJumpedOver() {
        val withHole = listOf(WordSpan(0, 0, 400), WordSpan(2, 500, 900))
        assertEquals(0, wordAt(withHole, 450))
        assertEquals(2, wordAt(withHole, 600))
    }

    @Test
    fun noSpansMeansNoWord() {
        assertEquals(-1, wordAt(emptyList(), 1_000))
    }

    @Test
    fun theSearchFindsTheRightWordInALongAyah() {
        val long = (0 until 130).map { WordSpan(it, it * 1_000, it * 1_000 + 900) }
        for (word in 0 until 130) {
            assertEquals(word, wordAt(long, word * 1_000L))
            assertEquals(word, wordAt(long, word * 1_000L + 950))
        }
    }

    @Test
    fun theStartOfAWordComesFromItsSpan() {
        assertEquals(910, startOfWord(spans, 2))
        assertNull(startOfWord(spans, 9))
        assertNull(startOfWord(listOf(WordSpan(0, 0, 400), WordSpan(2, 500, 900)), 1))
    }

    @Test
    fun spansThatCoverMostOfTheWordsAreUsable() {
        assertEquals(spans, usableSpans(spans, wordCount = 4))
        // 4 of 5 words, 4 of 6 words (66%).
        assertEquals(spans, usableSpans(spans, wordCount = 5))
        assertEquals(spans, usableSpans(spans, wordCount = 6))
    }

    @Test
    fun spansThatCoverTooLittleAreNotUsed() {
        // 4 of 7 words is 57%.
        assertEquals(emptyList(), usableSpans(spans, wordCount = 7))
        assertEquals(emptyList(), usableSpans(listOf(WordSpan(0, 5650, 7250)), wordCount = 3))
        // Exactly 60%: 3 of 5.
        assertEquals(3, usableSpans(spans.take(3), wordCount = 5).size)
    }

    @Test
    fun spansOfWordsTheAyahDoesNotHaveMeanTheTextAndTheTimingDisagree() {
        assertEquals(emptyList(), usableSpans(spans, wordCount = 3))
        assertEquals(emptyList(), usableSpans(spans, wordCount = 0))
        assertEquals(emptyList(), usableSpans(emptyList(), wordCount = 5))
    }
}
