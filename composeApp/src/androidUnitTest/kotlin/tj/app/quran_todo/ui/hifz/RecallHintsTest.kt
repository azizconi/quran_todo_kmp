package tj.app.quran_todo.ui.hifz

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tj.app.quran_todo.data.BundledFiles
import tj.app.quran_todo.data.bundled.QuranTsvParser
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.ui.reader.displayWords
import tj.app.quran_todo.ui.state.HifzHintMode

/** The levels of the "Recall" hint on the words of real ayahs of the bundled text. */
class RecallHintsTest {
    private val ayahs: List<AyahEntity> by lazy { QuranTsvParser.parse(BundledFiles.quranTsv()).ayahs }

    /** The words of the ayah as the screens draw them (what `displayWords` finds). */
    private fun words(surah: Int, number: Int): List<String> {
        val ayah = ayahs.first { it.surahNumber == surah && it.numberInSurah == number }
        val shown = displayWords(ayah.surahNumber, ayah.numberInSurah, ayah.text)
        return shown.ranges.map { shown.text.substring(it) }
    }

    private fun letters(surah: Int, number: Int): String = words(surah, number).joinToString(" ") { RecallHints.firstLetter(it) ?: "?" }

    // -- First letters -----------------------------------------------------------------------------------------

    @Test
    fun theFirstLettersOfAlFatihaOneAreBaAndThreeAlifs() {
        // The alif of the article is an alif wasla in the text; it is shown as a plain alif.
        assertEquals("ب ا ا ا", letters(1, 1))
    }

    @Test
    fun theFirstLettersOfAyatAlKursiAreFiftyLettersOfFiftyWords() {
        val words = words(2, 255)
        assertEquals(50, words.size)
        val letters = letters(2, 255).split(' ')
        assertEquals(50, letters.size)
        assertEquals("ا ل إ إ ه ا ا ل ت س و ن", letters.take(12).joinToString(" "))
        // The word that has a tatweel inside ("yaud") starts with ya, the hamza above the waw is not its first letter.
        val withTatweel = words.indexOfFirst { 'ـ' in it }
        assertTrue(withTatweel > 0)
        assertEquals("ي", letters[withTatweel])
        assertEquals("ا", letters.last())
    }

    @Test
    fun theFirstLettersOfSurahIkhlasStandInPlaceOfItsFourWords() {
        // The Bismillah in front of the ayah is the reader's own line, not a word of the ayah.
        assertEquals("ق ه ا أ", letters(112, 1))
        assertEquals(4, words(112, 1).size)
    }

    @Test
    fun aPauseMarkOnItsOwnIsNotAWordSoItGetsNoLetter() {
        // 2:2 has two small marks between its words: 7 words, 7 letters.
        assertEquals("ذ ا ل ر ف ه ل", letters(2, 2))
        assertEquals(7, words(2, 2).size)
    }

    @Test
    fun theFirstLetterSkipsHarakatReadingMarksAndTatweel() {
        assertEquals("ب", RecallHints.firstLetter("بِسْمِ"))
        assertEquals("ا", RecallHints.firstLetter("ٱلرَّحْمَٰنِ"))
        assertEquals("ا", RecallHints.firstLetter("ا"))
        assertEquals("أ", RecallHints.firstLetter("أَحَدٌ"))
        assertEquals("م", RecallHints.firstLetter("ـَّم"))
        assertEquals("ف", RecallHints.firstLetter("ۛفِيهِ"))
        assertNull(RecallHints.firstLetter("ۛ"))
        assertNull(RecallHints.firstLetter(""))
        assertNull(RecallHints.firstLetter("ـ"))
    }

    // -- Which words stay sharp --------------------------------------------------------------------------------

    @Test
    fun noneAndFirstLettersLeaveNoWordSharp() {
        assertEquals(emptySet(), RecallHints.shownWords(HifzHintMode.NONE, 8))
        assertEquals(emptySet(), RecallHints.shownWords(HifzHintMode.FIRST_LETTERS, 8))
        assertTrue(RecallHints.showsLetters(HifzHintMode.FIRST_LETTERS))
        assertEquals(false, RecallHints.showsLetters(HifzHintMode.FIRST_WORD))
    }

    @Test
    fun theFirstWordLevelLeavesOnlyTheFirstWord() {
        assertEquals(setOf(0), RecallHints.shownWords(HifzHintMode.FIRST_WORD, 8))
        // An ayah of one word would show everything: nothing stays.
        assertEquals(emptySet(), RecallHints.shownWords(HifzHintMode.FIRST_WORD, 1))
        assertEquals(setOf(0), RecallHints.shownWords(HifzHintMode.FIRST_WORD, 2))
    }

    @Test
    fun theEveryOtherLevelShowsTheFirstThirdFifthWords() {
        assertEquals(setOf(0, 2, 4, 6), RecallHints.shownWords(HifzHintMode.EVERY_OTHER, 7))
        assertEquals(setOf(0, 2, 4), RecallHints.shownWords(HifzHintMode.EVERY_OTHER, 6))
        assertEquals(setOf(0), RecallHints.shownWords(HifzHintMode.EVERY_OTHER, 2))
        assertEquals(emptySet(), RecallHints.shownWords(HifzHintMode.EVERY_OTHER, 1))
        // Ayat al-Kursi: 25 of its 50 words.
        assertEquals(25, RecallHints.shownWords(HifzHintMode.EVERY_OTHER, words(2, 255).size).size)
    }

    @Test
    fun theProgressiveLevelHidesQuartersFromTheEnd() {
        val shown = (0..4).map { step -> RecallHints.shownWords(HifzHintMode.PROGRESSIVE, 8, step) }
        assertEquals((0 until 8).toSet(), shown[0])
        assertEquals((0 until 6).toSet(), shown[1])
        assertEquals((0 until 4).toSet(), shown[2])
        assertEquals((0 until 2).toSet(), shown[3])
        assertEquals(emptySet(), shown[4])
        assertEquals(listOf(0, 2, 4, 6, 8), (0..4).map { RecallHints.hiddenCount(8, it) })
        assertEquals(listOf(0, 25, 50, 75, 100), (0..4).map { RecallHints.percentAt(it) })
    }

    @Test
    fun aQuarterIsRoundedUpAndAWordStaysUntilTheLastStep() {
        // Ten words: 3, 5, 8 hidden, then all.
        assertEquals(listOf(0, 3, 5, 8, 10), (0..4).map { RecallHints.hiddenCount(10, it) })
        // Three words: 1, 2, and the third step has nothing more to hide before the end.
        assertEquals(listOf(0, 1, 2, 2, 3), (0..4).map { RecallHints.hiddenCount(3, it) })
        assertEquals(listOf(0, 0, 0, 0, 1), (0..4).map { RecallHints.hiddenCount(1, it) })
        assertEquals(0, RecallHints.hiddenCount(0, 4))
    }

    @Test
    fun hideMoreSkipsTheStepsThatWouldChangeNothing() {
        assertEquals(1, RecallHints.nextStep(10, 0))
        assertEquals(2, RecallHints.nextStep(10, 1))
        assertEquals(4, RecallHints.nextStep(10, 3))
        assertEquals(4, RecallHints.nextStep(10, 4))
        // Three words: step 3 hides what step 2 hides, so it is skipped.
        assertEquals(1, RecallHints.nextStep(3, 0))
        assertEquals(2, RecallHints.nextStep(3, 1))
        assertEquals(4, RecallHints.nextStep(3, 2))
        // One word: whole, then hidden.
        assertEquals(4, RecallHints.nextStep(1, 0))
        assertTrue(RecallHints.canHideMore(10, 3))
        assertEquals(false, RecallHints.canHideMore(10, 4))
    }

    @Test
    fun everyLevelOnEveryRealAyahLeavesSomethingHiddenExceptTheProgressiveStart() {
        listOf(HifzHintMode.NONE, HifzHintMode.FIRST_WORD, HifzHintMode.FIRST_LETTERS, HifzHintMode.EVERY_OTHER).forEach { mode ->
            ayahs.forEach { ayah ->
                val count = displayWords(ayah.surahNumber, ayah.numberInSurah, ayah.text).wordCount
                assertTrue(RecallHints.shownWords(mode, count).size < count, "$mode leaves ${ayah.surahNumber}:${ayah.numberInSurah} whole")
            }
        }
    }

    @Test
    fun theLettersOfEveryAyahOfTheQuranAreFoundForEveryWord() {
        // No word of the Quran is left without a first letter: the hint never draws an empty place.
        ayahs.forEach { ayah ->
            val shown = displayWords(ayah.surahNumber, ayah.numberInSurah, ayah.text)
            shown.ranges.forEach { range ->
                assertNotNull(RecallHints.firstLetter(shown.text.substring(range)), "${ayah.surahNumber}:${ayah.numberInSurah}")
            }
        }
    }

    // -- The line of the sheet ---------------------------------------------------------------------------------

    @Test
    fun thePreviewShowsWhatEachLevelLeaves() {
        val kursi = words(2, 255)
        val head = kursi.take(5)
        assertEquals("··· ··· ··· ··· ··· ···", RecallHints.preview(HifzHintMode.NONE, kursi))
        assertEquals("${head[0]} ··· ··· ··· ··· ···", RecallHints.preview(HifzHintMode.FIRST_WORD, kursi))
        assertEquals("ا ل إ إ ه ···", RecallHints.preview(HifzHintMode.FIRST_LETTERS, kursi))
        assertEquals("${head[0]} ··· ${head[2]} ··· ${head[4]} ···", RecallHints.preview(HifzHintMode.EVERY_OTHER, kursi))
        // Progressive is drawn at half of the words that are shown: the end of the line is gone.
        assertEquals("${head[0]} ${head[1]} ··· ··· ··· ···", RecallHints.preview(HifzHintMode.PROGRESSIVE, kursi))
    }

    @Test
    fun aShortAyahIsNotCutAndAnEmptyOneHasNoPreview() {
        val ikhlas = words(112, 1)
        assertEquals("${ikhlas[0]} ··· ··· ···", RecallHints.preview(HifzHintMode.FIRST_WORD, ikhlas))
        assertEquals("", RecallHints.preview(HifzHintMode.NONE, emptyList()))
    }
}
