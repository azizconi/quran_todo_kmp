package tj.app.quran_todo.common.mushaf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tj.app.quran_todo.data.BundledFiles
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.bundled.QuranTsvParser

/** The table of the pages ([MushafPages]) against the bundled text (`page`, `juz`, `hizbQuarter` of every one of the 6236 ayahs). */
class MushafPagesTest {
    private val ayahs by lazy { QuranTsvParser.parse(BundledFiles.quranTsv()).ayahs }

    @Test
    fun everyAyahIsOnThePageTheBundledTextSaysItIsOn() {
        assertEquals(QuranFacts.AYAH_COUNT, ayahs.size)
        for (ayah in ayahs) {
            assertEquals(ayah.page, MushafPages.pageOf(ayah.surahNumber, ayah.numberInSurah), "${ayah.surahNumber}:${ayah.numberInSurah}")
        }
    }

    @Test
    fun theFirstAyahOfEveryPageIsTheFirstAyahOfThatPageInTheText() {
        val firstOfPage = ayahs.groupBy { it.page }.mapValues { (_, list) -> list.minBy { it.number } }
        assertEquals(604, firstOfPage.size)
        for ((page, first) in firstOfPage) {
            assertEquals(AyahKey(first.surahNumber, first.numberInSurah), MushafPages.firstAyahOf(page), "page $page")
        }
    }

    @Test
    fun theJuzAndTheHizbOfAPageAreThoseOfItsFirstAyah() {
        val firstOfPage = ayahs.groupBy { it.page }.mapValues { (_, list) -> list.minBy { it.number } }
        for ((page, first) in firstOfPage) {
            assertEquals(first.juz, MushafPages.juzOf(page), "juz of page $page")
            assertEquals(first.hizbQuarter, MushafPages.hizbQuarterOf(page), "quarter of page $page")
            assertEquals((first.hizbQuarter - 1) / 4 + 1, MushafPages.hizbOf(page), "hizb of page $page")
        }
    }

    @Test
    fun theJuzPartitionThe604Pages() {
        val pages = (1..30).flatMap { MushafPages.pagesOfJuz(it).toList() }
        assertEquals((1..604).toList(), pages)
        assertEquals(1..21, MushafPages.pagesOfJuz(1))
        assertEquals(22..41, MushafPages.pagesOfJuz(2))
        // The text starts Juz 4 at 3:93 and Juz 26 at 46:1, one page after the printed mushaf, so Juz 3 and 25 have a page more.
        assertEquals(42..62, MushafPages.pagesOfJuz(3))
        assertEquals(63..81, MushafPages.pagesOfJuz(4))
        assertEquals(482..502, MushafPages.pagesOfJuz(25))
        assertEquals(503..521, MushafPages.pagesOfJuz(26))
        assertEquals(582..604, MushafPages.pagesOfJuz(30))
        for (juz in 1..30) {
            // Every page of a juz is in that juz (the text agrees).
            for (page in MushafPages.pagesOfJuz(juz)) assertEquals(juz, MushafPages.juzOf(page), "page $page")
        }
    }

    @Test
    fun anAyahGoesToItsPageAndBackToTheFirstAyahOfThatPage() {
        assertEquals(1, MushafPages.pageOf(1, 1))
        assertEquals(2, MushafPages.pageOf(2, 1))
        assertEquals(3, MushafPages.pageOf(2, 6))
        assertEquals(50, MushafPages.pageOf(3, 1))
        assertEquals(604, MushafPages.pageOf(114, 6))
        assertEquals(604, MushafPages.pageOf(112, 1))
        // Ayat al-Kursi.
        assertEquals(42, MushafPages.pageOf(2, 255))
        assertEquals(AyahKey(1, 1), MushafPages.firstAyahOf(1))
        assertEquals(AyahKey(2, 6), MushafPages.firstAyahOf(3))
        assertEquals(AyahKey(112, 1), MushafPages.firstAyahOf(604))
        // A number out of range is taken to the nearest page, not a crash.
        assertEquals(1, MushafPages.pageOf(1, 0))
        assertEquals(AyahKey(1, 1), MushafPages.firstAyahOf(0))
        assertEquals(AyahKey(112, 1), MushafPages.firstAyahOf(900))
    }

    @Test
    fun theQuranFactsHaveTheSameSurahsAsThePages() {
        assertTrue(MushafPages.isAyah(114, 6))
        assertTrue(!MushafPages.isAyah(114, 7))
        assertTrue(!MushafPages.isAyah(0, 1))
        assertTrue(!MushafPages.isAyah(2, 0))
        assertTrue(MushafPages.isPage(604))
        assertTrue(!MushafPages.isPage(605))
    }
}
