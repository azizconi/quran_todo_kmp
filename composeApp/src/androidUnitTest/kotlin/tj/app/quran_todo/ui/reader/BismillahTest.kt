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

    // Ayahs that start a quarter of a hizb: the bundled text opens them with U+06DE and a space (written as an escape here).
    private val sign = "\u06DE"
    private val baqarah26 = "$sign إِنَّ ٱللَّهَ لَا يَسْتَحْىِۦٓ أَن يَضْرِبَ مَثَلًۭا مَّا بَعُوضَةً فَمَا فَوْقَهَا"
    private val baqarah26Shown = "إِنَّ ٱللَّهَ لَا يَسْتَحْىِۦٓ أَن يَضْرِبَ مَثَلًۭا مَّا بَعُوضَةً فَمَا فَوْقَهَا"

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

    // ---- the rub el hizb sign: hidden in display only ----

    @Test
    fun theSignIsHiddenFromTheAyahThatStartsAQuarter() {
        assertEquals(baqarah26Shown, splitBismillah(2, 26, baqarah26))
        assertEquals(baqarah26Shown, hideRubSign(baqarah26))
        assertFalse(splitBismillah(2, 26, baqarah26).contains(sign))
    }

    @Test
    fun theDatabaseTextIsNotTouchedAndCopyKeepsTheSign() {
        // Copy and share build their text with cutBismillah: the sign stays in it.
        assertEquals(baqarah26, cutBismillah(2, 26, baqarah26))
        assertTrue(cutBismillah(2, 26, baqarah26).startsWith("$sign "))
        // The Bismillah cut keeps the sign too: it is only about the Bismillah.
        assertEquals("الٓمٓ", cutBismillah(2, 1, baqarah1))
    }

    @Test
    fun anOrdinaryAyahIsReturnedAsItIs() {
        assertEquals(baqarah2, splitBismillah(2, 2, baqarah2))
        assertEquals(baqarah2, hideRubSign(baqarah2))
        assertEquals("", hideRubSign(""))
    }

    @Test
    fun theBismillahOfTheFirstAyahIsHandledAsBefore() {
        assertEquals("الٓمٓ", splitBismillah(2, 1, baqarah1))
        assertEquals("قُلْ أَعُوذُ بِرَبِّ ٱلنَّاسِ", splitBismillah(114, 1, nas1))
        assertEquals(bismillah, splitBismillah(1, 1, bismillah))
        assertEquals(tawbah1, splitBismillah(9, 1, tawbah1))
    }

    @Test
    fun theSignAndTheSpaceAfterItGoAnywhereInTheText() {
        assertEquals("ا ب", hideRubSign("ا $sign ب"))
        assertEquals("ا", hideRubSign("$sign   ا"))
        assertEquals("ا", hideRubSign("ا $sign"))
        assertEquals("ا", hideRubSign("$sign\u00A0ا"))
        assertEquals("ا", hideRubSign("$sign ا$sign"))
    }

    @Test
    fun aTextThatIsOnlyTheSignIsLeftEmpty() {
        assertEquals("", hideRubSign(sign))
    }
}
