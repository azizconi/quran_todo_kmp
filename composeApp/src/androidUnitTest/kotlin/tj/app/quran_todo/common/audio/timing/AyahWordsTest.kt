package tj.app.quran_todo.common.audio.timing

import tj.app.quran_todo.data.BundledFiles
import tj.app.quran_todo.data.bundled.QuranTsvParser
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.ui.reader.AyahWords
import tj.app.quran_todo.ui.reader.RubElHizbSign
import tj.app.quran_todo.ui.reader.displayWords
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The words of the real bundled text against the timings of real answers of quran.com (see [WordTimingsFixtures]). */
class AyahWordsTest {
    private val ayahs: List<AyahEntity> by lazy { QuranTsvParser.parse(BundledFiles.quranTsv()).ayahs }

    private fun ayah(surah: Int, number: Int): AyahEntity = ayahs.first { it.surahNumber == surah && it.numberInSurah == number }

    private fun words(surah: Int, number: Int): AyahWords = ayah(surah, number).let { displayWords(it.surahNumber, it.numberInSurah, it.text) }

    private fun AyahWords.word(index: Int): String = text.substring(ranges[index])

    @Test
    fun alFatihaCountsItsBismillahAsTheFirstAyah() {
        assertEquals(listOf(4, 4, 2, 3, 4, 3, 9), (1..7).map { words(1, it).wordCount })
        val first = words(1, 1)
        val tokens = ayah(1, 1).text.split(' ')
        assertEquals(tokens.first(), first.word(0))
        assertEquals(tokens.last(), first.word(3))
        // The whole text is the four words, nothing is cut.
        assertEquals(ayah(1, 1).text, first.text)
    }

    @Test
    fun theBismillahInFrontOfTheFirstAyahIsNotAWordOfIt() {
        // 2:1 is "Alif Lam Mim" and the recording starts with it, not with the Bismillah.
        val alifLamMim = words(2, 1)
        assertEquals(1, alifLamMim.wordCount)
        assertEquals("الٓمٓ", alifLamMim.text)
        assertEquals(0..alifLamMim.text.lastIndex, alifLamMim.ranges.single())
        // 112:1 has the Bismillah in the database text: four words of the ayah are left.
        val ikhlas = words(112, 1)
        assertEquals(4, ikhlas.wordCount)
        assertEquals("قُلْ", ikhlas.word(0))
        assertEquals("أَحَدٌ", ikhlas.word(3))
        // At-Tawbah has no Bismillah at all: its first ayah is whole.
        assertEquals(9, words(9, 1).wordCount)
        assertEquals(ayah(9, 1).text.split(' ').first(), words(9, 1).word(0))
        assertEquals(ayah(9, 1).text, words(9, 1).text)
    }

    @Test
    fun ayatAlKursiHasFiftyWordsAndEightPauseMarks() {
        val kursi = words(2, 255)
        assertEquals(50, kursi.wordCount)
        val tokens = ayah(2, 255).text.split(' ')
        assertEquals(tokens.first(), kursi.word(0))
        assertEquals("ٱلْعَظِيمُ", kursi.word(49))
        assertEquals(tokens.last(), kursi.word(49))
        // The text has 58 tokens: the other eight are the small pause signs that stand alone.
        assertEquals(58, kursi.text.split(' ').size)
    }

    @Test
    fun aStartOfAQuarterSignIsHiddenAndIsNotAWord() {
        // 2:26 starts with "۞ إِنَّ ٱللَّهَ ..." in the database.
        assertTrue(ayah(2, 26).text.startsWith(RubElHizbSign))
        val shown = words(2, 26)
        assertFalse(RubElHizbSign in shown.text)
        assertEquals(ayah(2, 26).text.removePrefix("$RubElHizbSign ").split(' ').first(), shown.word(0))
        assertEquals(39, shown.wordCount)
    }

    @Test
    fun pauseMarksStandingAloneAreNotWords() {
        // "ذَٰلِكَ ٱلْكِتَٰبُ لَا رَيْبَ ۛ فِيهِ ۛ هُدًى لِّلْمُتَّقِينَ": nine tokens, seven words.
        val second = words(2, 2)
        assertEquals(7, second.wordCount)
        assertEquals(9, second.text.split(' ').size)
        for (index in 0 until second.wordCount) assertTrue(second.word(index).any { it.isLetter() })
        assertEquals("رَيْبَ", second.word(3))
        assertEquals("فِيهِ", second.word(4))
        // 10:1 and 13:1: the sign right after the opening letters is a token of its own too.
        assertEquals(5, words(10, 1).wordCount)
        assertEquals(15, words(13, 1).wordCount)
    }

    @Test
    fun theWordsOfTheWholeQuranAreTheWordsOfQuranCom() {
        // quran.com counts 77430 words in the Quran. Every range is inside its text, in order, holds a letter and
        // never swallows a space.
        var total = 0
        for (ayah in ayahs) {
            val shown = displayWords(ayah.surahNumber, ayah.numberInSurah, ayah.text)
            var previousEnd = -1
            for (range in shown.ranges) {
                assertTrue(range.first > previousEnd, "${ayah.surahNumber}:${ayah.numberInSurah}: ranges overlap")
                assertTrue(range.last < shown.text.length)
                val word = shown.text.substring(range)
                assertTrue(word.any { it.isLetter() }, "${ayah.surahNumber}:${ayah.numberInSurah}: '$word' has no letter")
                assertFalse(word.any { it.isWhitespace() }, "${ayah.surahNumber}:${ayah.numberInSurah}: '$word' has a space")
                previousEnd = range.last
            }
            total += shown.wordCount
        }
        assertEquals(77_430, total)
    }

    @Test
    fun theRangesOfAnEmptyOrBlankTextAreEmpty() {
        assertEquals(emptyList(), wordRanges(""))
        assertEquals(emptyList(), wordRanges("   "))
        assertEquals(emptyList(), wordRanges("ۖ ۚ"))
        assertEquals(listOf(0..2, 4..5), wordRanges("abc de"))
        // A sign glued to a word stays part of it.
        assertEquals(listOf(0..3), wordRanges("abۖc"))
        assertEquals(1, wordIndexAt(listOf(0..2, 4..5), 5))
        assertEquals(-1, wordIndexAt(listOf(0..2, 4..5), 3))
    }

    @Test
    fun theTimingsOfRealAnswersFitTheTextOfTheirAyah() {
        fun check(answer: String, recitation: Int, surah: Int) {
            val timings = WordTimingsFormat.parseQuranCom(answer, recitation, surah)!!
            for ((number, spans) in timings.ayahs) {
                val count = words(surah, number).wordCount
                assertEquals(spans, usableSpans(spans, count), "$surah:$number ($count words) must be usable as it is")
            }
        }
        check(WordTimingsFixtures.AlafasyFatiha, 7, 1)
        check(WordTimingsFixtures.AlafasyAyatAlKursi, 7, 2)
        check(WordTimingsFixtures.AlafasyAlifLamMim, 7, 2)
        check(WordTimingsFixtures.MuallimThe26th, 12, 2)
        check(WordTimingsFixtures.SudaisFatiha, 3, 1)
        check(WordTimingsFixtures.AlafasyIkhlasFirst, 7, 112)
    }

    @Test
    fun theLastWordOfAyatAlKursiEndsTheRecordingAndTheFirstStartsIt() {
        val spans = WordTimingsFormat.parseQuranCom(WordTimingsFixtures.AlafasyAyatAlKursi, 7, 2)!!.spans(255)
        assertEquals(0, wordAt(spans, 40))
        assertEquals(49, wordAt(spans, 51_000))
        assertEquals(49, wordAt(spans, 99_000))
    }
}
