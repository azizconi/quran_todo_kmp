package tj.app.quran_todo.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BismillahTest {
    // Real strings of the `quran-uthmani` edition.
    private val bismillah = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"
    private val baqarah1 = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ الٓمٓ"
    private val baqarah2 = "ذَٰلِكَ ٱلْكِتَٰبُ لَا رَيْبَ ۛ فِيهِ ۛ هُدًى لِّلْمُتَّقِينَ"
    private val tawbah1 = "بَرَآءَةٌ مِّنَ ٱللَّهِ وَرَسُولِهِۦٓ إِلَى ٱلَّذِينَ عَـٰهَدتُّم مِّنَ ٱلْمُشْرِكِينَ"
    private val nas1 = "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ قُلْ أَعُوذُ بِرَبِّ ٱلنَّاسِ"

    @Test
    fun theBismillahIsCutOffTheFirstAyahOfAlBaqarah() {
        assertEquals("الٓمٓ", splitBismillah(2, 1, baqarah1))
    }

    @Test
    fun theBismillahIsCutOffTheFirstAyahOfTheLastSurah() {
        assertEquals("قُلْ أَعُوذُ بِرَبِّ ٱلنَّاسِ", splitBismillah(114, 1, nas1))
    }

    @Test
    fun alFatihaKeepsItsBismillahBecauseItIsTheFirstAyah() {
        assertEquals(bismillah, splitBismillah(1, 1, bismillah))
    }

    @Test
    fun atTawbahHasNoBismillahAndStaysUntouched() {
        assertEquals(tawbah1, splitBismillah(9, 1, tawbah1))
        // Even a text that happened to start with it is left alone: the surah has none.
        assertEquals(baqarah1, splitBismillah(9, 1, baqarah1))
    }

    @Test
    fun otherAyahsStayUntouched() {
        assertEquals(baqarah2, splitBismillah(2, 2, baqarah2))
        assertEquals(baqarah1, splitBismillah(2, 2, baqarah1))
    }

    @Test
    fun aFirstAyahThatDoesNotStartWithTheBismillahStaysUntouched() {
        val text = "الٓمٓ ذَٰلِكَ ٱلْكِتَٰبُ لَا رَيْبَ فِيهِ"
        assertEquals(text, splitBismillah(2, 1, text))
    }

    @Test
    fun theFirstWordIsComparedWithoutMarks() {
        val plain = "بسم الله الرحمن الرحيم الم"
        assertEquals("الم", splitBismillah(2, 1, plain))
        val tatweel = "بـسـم ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ الٓمٓ"
        assertEquals("الٓمٓ", splitBismillah(2, 1, tatweel))
        // Small high sukun and other annotation marks do not matter either.
        val annotated = "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ الٓمٓ"
        assertEquals("الٓمٓ", splitBismillah(2, 1, annotated))
    }

    @Test
    fun spacingAroundTheCutIsNormalised() {
        assertEquals("الٓمٓ", splitBismillah(2, 1, "  $bismillah   الٓمٓ"))
    }

    @Test
    fun aTextThatWouldBeLeftEmptyIsReturnedWhole() {
        assertEquals(bismillah, splitBismillah(2, 1, bismillah))
    }

    @Test
    fun aTextShorterThanTheBismillahIsReturnedWhole() {
        assertEquals("بِسْمِ ٱللَّهِ", splitBismillah(2, 1, "بِسْمِ ٱللَّهِ"))
        assertEquals("", splitBismillah(2, 1, ""))
    }

    @Test
    fun theBismillahLineIsShownForAllSurahsExceptTheFirstAndTheNinth() {
        assertFalse(hasBismillahLine(1))
        assertFalse(hasBismillahLine(9))
        assertTrue(hasBismillahLine(2))
        assertTrue(hasBismillahLine(8))
        assertTrue(hasBismillahLine(10))
        assertTrue(hasBismillahLine(114))
        assertFalse(hasBismillahLine(0))
        assertFalse(hasBismillahLine(115))
    }
}
