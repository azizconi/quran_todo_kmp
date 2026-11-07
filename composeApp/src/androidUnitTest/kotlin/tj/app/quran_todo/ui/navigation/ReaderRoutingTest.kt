package tj.app.quran_todo.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import tj.app.quran_todo.ui.state.LastReadPosition
import tj.app.quran_todo.ui.state.ReaderLayout

class ReaderRoutingTest {
    @Test
    fun inTheTextModeEveryRequestStaysTheTextReader() {
        val requests = listOf(
            Dest.Reader(surah = 112, origin = Origin.RECENT),
            Dest.Reader(surah = 2, ayah = 255, origin = Origin.SEARCH),
            Dest.Reader(surah = 2, hifz = true, rangeStart = 1, rangeEnd = 5),
        )
        for (request in requests) assertSame(request, ReaderRouting.route(request, ReaderLayout.LIST))
    }

    @Test
    fun inThePagesModeARequestForAnAyahOpensTheMushafOnItsPageAndMarksTheAyah() {
        val routed = ReaderRouting.route(Dest.Reader(surah = 2, ayah = 255, origin = Origin.SEARCH), ReaderLayout.MUSHAF)
        assertEquals(Dest.Mushaf(page = 42, ayahKey = "2:255"), routed)
        assertEquals(Dest.Mushaf(page = 3, ayahKey = "2:6"), ReaderRouting.route(Dest.Reader(surah = 2, ayah = 6), ReaderLayout.MUSHAF))
    }

    @Test
    fun aSurahOpenedWithNoAyahOpensWhereThePersonStoppedInIt_OtherwiseOnItsFirstPage() {
        val stoppedInBaqara = LastReadPosition(surah = 2, ayahInSurah = 255, legacyGlobalAyah = null)
        // The surah the person stopped in: the page of that ayah, nothing marked (it is not a search result).
        assertEquals(
            Dest.Mushaf(page = 42, ayahKey = null),
            ReaderRouting.route(Dest.Reader(surah = 2, origin = Origin.LIST), ReaderLayout.MUSHAF, stoppedInBaqara),
        )
        // Another surah: its first page.
        assertEquals(
            Dest.Mushaf(page = 50, ayahKey = null),
            ReaderRouting.route(Dest.Reader(surah = 3, origin = Origin.LIST), ReaderLayout.MUSHAF, stoppedInBaqara),
        )
        // No position at all, or only a legacy global number: the first page too.
        assertEquals(Dest.Mushaf(page = 604, ayahKey = null), ReaderRouting.route(Dest.Reader(surah = 112), ReaderLayout.MUSHAF))
        assertEquals(
            Dest.Mushaf(page = 604, ayahKey = null),
            ReaderRouting.route(Dest.Reader(surah = 112), ReaderLayout.MUSHAF, LastReadPosition(112, null, legacyGlobalAyah = 6104)),
        )
    }

    @Test
    fun theCheckOfTheLessonAndARangeOfAyahsStayInTheTextWhateverTheModeIs() {
        val check = Dest.Reader(surah = 2, ayah = 10, hifz = true)
        val range = Dest.Reader(surah = 2, rangeStart = 10, rangeEnd = 20)
        val onlyStart = Dest.Reader(surah = 2, rangeStart = 10)
        assertSame(check, ReaderRouting.route(check, ReaderLayout.MUSHAF))
        assertSame(range, ReaderRouting.route(range, ReaderLayout.MUSHAF))
        assertSame(onlyStart, ReaderRouting.route(onlyStart, ReaderLayout.MUSHAF))
        assertEquals(false, ReaderRouting.canOpenAsPage(check))
        assertEquals(true, ReaderRouting.canOpenAsPage(Dest.Reader(surah = 2)))
    }

    @Test
    fun anAyahThatDoesNotExistStaysTheTextReader() {
        val broken = Dest.Reader(surah = 112, ayah = 40)
        assertSame(broken, ReaderRouting.route(broken, ReaderLayout.MUSHAF))
    }

    @Test
    fun theSwitchesOpenTheTextOnTheFirstAyahOfThePageAndThePageOfTheAyahOnTop() {
        val text = ReaderRouting.textFor(604)
        assertEquals(112, text.surah)
        assertEquals(1, text.ayah)
        assertEquals(ReaderRouting.FROM_PAGES, text.origin)
        assertEquals(false, text.hifz)

        assertEquals(Dest.Mushaf(page = 42), ReaderRouting.pagesFor(2, 255))
        assertEquals(Dest.Mushaf(page = 1), ReaderRouting.pagesFor(1, 1))
    }
}
