package tj.app.quran_todo.ui.reader

import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.ui.reader.components.RangePosition
import tj.app.quran_todo.ui.reader.components.rangePositionOf
import tj.app.quran_todo.ui.state.LastReadPosition
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderPositionTest {
    /** A surah of 10 ayahs whose first ayah has the global number 100. */
    private val ayahs = (1..10).map { inSurah ->
        AyahEntity(
            number = 99 + inSurah,
            numberInSurah = inSurah,
            juz = 1, hizbQuarter = 1, manzil = 1, page = 1, ruku = 1, sajda = null,
            text = "text $inSurah",
            surahNumber = 7,
        )
    }

    private fun resolve(
        requestedAyah: Int? = null,
        rangeStart: Int? = null,
        lastRead: LastReadPosition? = null,
    ) = resolveInitialAyahIndex(7, requestedAyah, rangeStart, lastRead, ayahs)

    @Test
    fun theRequestedAyahWinsOverEverythingElse() {
        val lastRead = LastReadPosition(surah = 7, ayahInSurah = 9, legacyGlobalAyah = null)
        assertEquals(3, resolve(requestedAyah = 4, rangeStart = 6, lastRead = lastRead))
    }

    @Test
    fun theRangeStartComesNext() {
        val lastRead = LastReadPosition(surah = 7, ayahInSurah = 9, legacyGlobalAyah = null)
        assertEquals(5, resolve(rangeStart = 6, lastRead = lastRead))
    }

    @Test
    fun theReadingPositionOfThisSurahIsUsedWhenNothingWasRequested() {
        assertEquals(8, resolve(lastRead = LastReadPosition(surah = 7, ayahInSurah = 9, legacyGlobalAyah = null)))
    }

    @Test
    fun aLegacyPositionIsConvertedFromTheGlobalAyahNumber() {
        // Global 104 is the 5th ayah of this surah.
        assertEquals(4, resolve(lastRead = LastReadPosition(surah = 7, ayahInSurah = null, legacyGlobalAyah = 104)))
    }

    @Test
    fun aPositionInAnotherSurahIsIgnored() {
        assertEquals(0, resolve(lastRead = LastReadPosition(surah = 2, ayahInSurah = 9, legacyGlobalAyah = null)))
        assertEquals(0, resolve(lastRead = LastReadPosition(surah = 2, ayahInSurah = null, legacyGlobalAyah = 104)))
    }

    @Test
    fun withoutAnyHintTheReaderOpensAtTheTop() {
        assertEquals(0, resolve())
    }

    @Test
    fun anAyahThatDoesNotExistFallsThroughToTheNextHint() {
        val lastRead = LastReadPosition(surah = 7, ayahInSurah = 3, legacyGlobalAyah = null)
        assertEquals(2, resolve(requestedAyah = 99, rangeStart = 0, lastRead = lastRead))
        assertEquals(0, resolve(requestedAyah = 99))
    }

    @Test
    fun theFirstAyahMeansTheTop() {
        assertEquals(0, resolve(requestedAyah = 1))
    }

    @Test
    fun theShareTextHasArabicTranslationAndReference() {
        assertEquals(
            "الٓمٓ\n\nAlif, Lam, Meem.\n\n— Quran 2:1",
            ayahShareText("الٓمٓ", "Alif, Lam, Meem.", "Quran", 2, 1),
        )
        assertEquals(
            "الٓمٓ\n\n— Quran 2:1",
            ayahShareText(" الٓمٓ ", null, "Quran", 2, 1),
        )
        assertEquals(
            "الٓمٓ\n\n— Quran 2:1",
            ayahShareText("الٓمٓ", "  ", "Quran", 2, 1),
        )
    }

    @Test
    fun audioTimeIsFormattedAsMinutesAndSeconds() {
        assertEquals("0:00", formatAudioTime(0))
        assertEquals("0:07", formatAudioTime(7))
        assertEquals("1:05", formatAudioTime(65))
        assertEquals("12:30", formatAudioTime(750))
        assertEquals("0:00", formatAudioTime(-3))
    }

    @Test
    fun theBracketOfARepeatedPassageHasItsEnds() {
        val range = 2..4
        assertEquals(RangePosition.None, rangePositionOf(1, range))
        assertEquals(RangePosition.Start, rangePositionOf(2, range))
        assertEquals(RangePosition.Middle, rangePositionOf(3, range))
        assertEquals(RangePosition.End, rangePositionOf(4, range))
        assertEquals(RangePosition.None, rangePositionOf(5, range))
        assertEquals(RangePosition.Single, rangePositionOf(6, 6..6))
        assertEquals(RangePosition.None, rangePositionOf(0, null))
    }
}
