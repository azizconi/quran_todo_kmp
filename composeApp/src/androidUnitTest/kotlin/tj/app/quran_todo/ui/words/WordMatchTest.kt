package tj.app.quran_todo.ui.words

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import tj.app.quran_todo.common.words.WordFixtures
import tj.app.quran_todo.common.words.WordLookup
import tj.app.quran_todo.common.words.WordMeaning
import tj.app.quran_todo.common.words.WordMeaningsChapter
import tj.app.quran_todo.ui.reader.displayWords

/**
 * The words of our text (`displayWords`, what a tap works with) against the words of quran.com: surah 1 and 2:1-10 line up word for word,
 * and the three ayahs the brief names (10:1, 13:1, 50:34) are the ones that do not.
 */
class WordMatchTest {
    private fun meanings(words: List<WordFixtures.W>) = words.map { WordMeaning(it.meaning, it.transliteration) }

    private fun text(surah: Int, ayah: Int) = assertNotNull(WordTextFixtures.texts[surah to ayah], "no text for $surah:$ayah")

    @Test
    fun theWordsOfSurahOneLineUpOneToOne() {
        val chapter = WordMeaningsChapter(1, WordFixtures.surah1.mapValues { meanings(it.value) })
        for ((ayah, source) in WordFixtures.surah1) {
            val words = displayWords(1, ayah, text(1, ayah))
            assertEquals(source.size, words.wordCount, "1:$ayah")
            for (index in 0 until words.wordCount) {
                val found = chapter.lookup(ayah, index, words.wordCount) as WordLookup.Found
                assertEquals(source[index].meaning, found.meaning.text, "1:$ayah word $index")
                assertEquals(index + 1, found.number)
                assertEquals(source.size, found.total)
            }
        }
    }

    @Test
    fun theWordsOfTheFirstTenAyahsOfAlBaqaraLineUpEvenWithTheBismillahCutOff() {
        val chapter = WordMeaningsChapter(2, WordFixtures.surah2First10.mapValues { meanings(it.value) })
        for ((ayah, source) in WordFixtures.surah2First10) {
            val words = displayWords(2, ayah, text(2, ayah))
            assertEquals(source.size, words.wordCount, "2:$ayah")
            assertTrue((chapter.lookup(ayah, words.wordCount - 1, words.wordCount) as WordLookup.Found).number == source.size)
        }
        // 2:1 is one word, "Alif Laam Meem": the Bismillah that opens the database text is not counted.
        assertEquals(1, displayWords(2, 1, text(2, 1)).wordCount)
        assertEquals("Alif Laam Meem", (chapter.lookup(1, 0, 1) as WordLookup.Found).meaning.text)
    }

    @Test
    fun theThreeAyahsOfTheBriefLineUpOnTheBundledTextAndShowTheirMeanings() {
        // 10:1, 13:1 and 50:34 have a pause sign next to a word; on the bundled text the counts equal quran.com's, so the rule shows them.
        for ((key, source) in WordFixtures.special) {
            val ours = displayWords(key.first, key.second, text(key.first, key.second)).wordCount
            assertEquals(source.size, ours, "${key.first}:${key.second}")
            val chapter = WordMeaningsChapter(key.first, mapOf(key.second to meanings(source)))
            for (index in 0 until ours) {
                val found = chapter.lookup(key.second, index, ours) as WordLookup.Found
                assertEquals(source[index].meaning, found.meaning.text)
            }
        }
    }

    @Test
    fun anAyahWhoseCountDiffersIsUnavailableByTheRuleAlone() {
        // 37:130: our text has four words ("Ilyasin" is two there), quran.com three. The only ayah of the whole Quran where it happens.
        val ours = displayWords(37, 130, text(37, 130)).wordCount
        assertEquals(4, ours)
        val chapter = WordMeaningsChapter(37, mapOf(130 to listOf(WordMeaning("Peace", "salamun"), WordMeaning("upon", "ʿalā"), WordMeaning("Ilyasin", "il-yasina"))))
        for (index in 0 until ours) assertEquals(WordLookup.Unavailable, chapter.lookup(130, index, ours))
    }
}
