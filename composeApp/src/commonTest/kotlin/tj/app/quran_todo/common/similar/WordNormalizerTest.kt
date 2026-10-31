package tj.app.quran_todo.common.similar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordNormalizerTest {
    @Test
    fun harakatAndSignsAreRemoved() {
        // fatha, damma, kasra, shadda, sukun, tanwin, the dagger alef, small high signs, tatweel
        assertEquals("الرحمن", normalizeWord("ٱلرَّحۡمَٰنِ"))
        assertEquals("ربكما", normalizeWord("رَبِّكُمَا"))
        assertEquals("يادم", normalizeWord("يَٰٓـَٔادَمُ"))
        assertEquals("قلنا", normalizeWord("قُلۡنَا۟"))
        assertEquals("حطه", normalizeWord("حِطَّةٌۭ"))
        assertEquals("كتب", normalizeWord("كـِـتَـٰـب"))
    }

    @Test
    fun theLettersOfOneSoundBecomeOne() {
        assertEquals("ا", normalizeWord("أ"))
        assertEquals("ا", normalizeWord("إ"))
        assertEquals("ا", normalizeWord("آ"))
        assertEquals("ا", normalizeWord("ٱ"))
        assertEquals("ي", normalizeWord("ى"))
        assertEquals("ي", normalizeWord("ٮ"))
        assertEquals("ه", normalizeWord("ة"))
        assertEquals("ء", normalizeWord("ؤ"))
        assertEquals("ء", normalizeWord("ئ"))
        assertEquals("ء", normalizeWord("ء"))
    }

    @Test
    fun spellingsOfTheSameWordAreEqual() {
        assertEquals(normalizeWord("ٱلْجَنَّةَ"), normalizeWord("الجنه"))
        assertEquals(normalizeWord("ٱلنَّصَٰرَىٰ"), normalizeWord("النصري"))
        assertEquals(normalizeWord("أَنتَ"), normalizeWord("انت"))
    }

    @Test
    fun theUpperAlefIsRemovedNotTurnedIntoAnAlef() {
        assertEquals("هذه", normalizeWord("هَٰذِهِ"))
    }

    @Test
    fun aTokenThatIsOnlyASignIsEmpty() {
        assertEquals("", normalizeWord("۞"))
        assertEquals("", normalizeWord("ۚ"))
        assertEquals("", normalizeWord("۩"))
        assertEquals("", normalizeWord(""))
    }

    @Test
    fun normalizedWordsKeepTheRangesOfTheText() {
        val text = "وَقُلْنَا ۚ ٱدْخُلُوا"
        val words = normalizedWords(text)
        assertEquals(listOf("وقلنا", "ادخلوا"), words.words)
        assertEquals(words.words.size, words.ranges.size)
        assertEquals("وَقُلْنَا", text.substring(words.ranges[0].first, words.ranges[0].last + 1))
        assertEquals("ٱدْخُلُوا", text.substring(words.ranges[1].first, words.ranges[1].last + 1))
    }

    @Test
    fun theRubSignIsNoWordEvenIfItIsLeftInTheText() {
        val words = normalizedWords("۞ إِنَّ ٱللَّهَ")
        assertEquals(listOf("ان", "الله"), words.words)
        assertTrue(words.ranges.all { it.first > 0 })
    }
}
