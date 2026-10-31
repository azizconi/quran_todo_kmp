package tj.app.quran_todo.common.words

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tj.app.quran_todo.common.words.WordFixtures.W

class WordMeaningsFormatTest {
    private fun page(surah: Int, ayahs: Map<Int, List<W>>, next: Int? = null, total: Int? = null) =
        assertNotNull(WordMeaningsFormat.parsePage(WordFixtures.answer(surah, ayahs, next, total), surah))

    private fun surahOne() = assertNotNull(WordMeaningsFormat.assemble(1, listOf(page(1, WordFixtures.surah1))))

    @Test
    fun theSignThatClosesTheAyahIsNotAWordAndTheOrderIsKept() {
        val parsed = page(1, WordFixtures.surah1)
        assertEquals(7, parsed.ayahs.size)
        assertEquals(WordFixtures.surah1.values.map { it.size }, parsed.ayahs.values.map { it.size })
        assertEquals(WordMeaning("In (the) name", "bis'mi"), parsed.ayahs.getValue(1)[0])
        assertEquals(WordMeaning("the Most Merciful", "l-raḥīmi"), parsed.ayahs.getValue(1).last())
        assertNull(parsed.nextPage)
    }

    @Test
    fun aWordWithoutATransliterationKeepsItsMeaning() {
        val parsed = page(5, mapOf(1 to listOf(W("one", ""), W("two", "tu"))))
        assertEquals(WordMeaning("one", null), parsed.ayahs.getValue(1)[0])
        assertEquals("tu", parsed.ayahs.getValue(1)[1].transliteration)
    }

    @Test
    fun anAnswerOfAnotherSurahOrADamagedOneIsRefused() {
        assertNull(WordMeaningsFormat.parsePage(WordFixtures.answer(2, WordFixtures.surah1), 1))
        assertNull(WordMeaningsFormat.parsePage("{}", 1))
        assertNull(WordMeaningsFormat.parsePage("nope", 1))
        // A word without a meaning, and a gap in the positions: the surah would be matched to wrong words.
        val noMeaning = WordFixtures.answer(1, mapOf(1 to listOf(W("a", "a")))).replace("\"text\":\"a\",\"language_name\":\"english\"}", "\"language_name\":\"english\"}")
        assertNull(WordMeaningsFormat.parsePage(noMeaning, 1))
        val gap = WordFixtures.answer(1, mapOf(1 to listOf(W("a", "a"), W("b", "b")))).replace("\"position\":2", "\"position\":3")
        assertNull(WordMeaningsFormat.parsePage(gap, 1))
    }

    @Test
    fun theSurahIsPutTogetherFromPagesAndAMissingPageMakesItUnusable() {
        val first = page(2, WordFixtures.surah2First10.filterKeys { it <= 5 }, next = 2, total = 10)
        val second = page(2, WordFixtures.surah2First10.filterKeys { it > 5 }, total = 10)
        val chapter = assertNotNull(WordMeaningsFormat.assemble(2, listOf(first, second)))
        assertEquals(10, chapter.words.size)
        assertNull(WordMeaningsFormat.assemble(2, listOf(first)))
        assertNull(WordMeaningsFormat.assemble(2, listOf(first, first)))
        assertNull(WordMeaningsFormat.assemble(2, emptyList()))
        assertNull(WordMeaningsFormat.assemble(3, listOf(first, second)))
    }

    @Test
    fun theCompactCopyIsSmallerThanTheAnswerAndReadsBackTheSame() {
        val answer = WordFixtures.answer(1, WordFixtures.surah1)
        val chapter = surahOne()
        val copy = WordMeaningsFormat.encode(chapter)
        assertTrue(copy.length < answer.length / 2, "${copy.length} vs ${answer.length}")
        val back = assertNotNull(WordMeaningsFormat.decode(copy, 1))
        assertEquals(chapter.words, back.words)
        assertNull(WordMeaningsFormat.decode(copy, 2))
        assertNull(WordMeaningsFormat.decode(copy.replace("\"v\":1", "\"v\":9"), 1))
        assertNull(WordMeaningsFormat.decode("{", 1))
    }

    @Test
    fun theWordOfTheScreenIsTheWordOfQuranComWithTheNextPosition() {
        val chapter = surahOne()
        val found = chapter.lookup(ayah = 1, index = 2, wordCount = 4) as WordLookup.Found
        assertEquals("the Most Gracious", found.meaning.text)
        assertEquals("l-raḥmāni", found.meaning.transliteration)
        assertEquals(3, found.number)
        assertEquals(4, found.total)
        val seven = WordFixtures.surah1.getValue(7)
        val last = chapter.lookup(ayah = 7, index = seven.size - 1, wordCount = seven.size) as WordLookup.Found
        assertEquals(seven.size, last.number)
        assertEquals(seven.size, last.total)
        assertEquals(seven.last().meaning, last.meaning.text)
    }

    @Test
    fun anAyahWhoseWordsDoNotLineUpHasNoMeanings() {
        val chapter = surahOne()
        // Our text has a different number of words than quran.com for this ayah.
        assertEquals(WordLookup.Unavailable, chapter.lookup(ayah = 1, index = 0, wordCount = 5))
        assertEquals(WordLookup.Unavailable, chapter.lookup(ayah = 1, index = 4, wordCount = 4))
        assertEquals(WordLookup.Unavailable, chapter.lookup(ayah = 1, index = -1, wordCount = 4))
        assertEquals(WordLookup.Unavailable, chapter.lookup(ayah = 99, index = 0, wordCount = 4))
    }
}
