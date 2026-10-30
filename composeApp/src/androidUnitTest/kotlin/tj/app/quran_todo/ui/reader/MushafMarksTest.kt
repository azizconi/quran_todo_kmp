package tj.app.quran_todo.ui.reader

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.BundledFiles
import tj.app.quran_todo.data.bundled.QuranTsvParser
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.Sajda
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import tj.app.quran_todo.ui.i18n.MarkLine
import tj.app.quran_todo.ui.i18n.mushafCopy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The mushaf marks of the reader, against the real juz / hizb quarter / page columns of the bundled text. */
class MushafMarksTest {
    private val quran = QuranTsvParser.parse(BundledFiles.quranTsv())
    private val bySurah: Map<Int, List<AyahEntity>> =
        quran.ayahs.groupBy { it.surahNumber }.mapValues { (_, ayahs) -> ayahs.sortedBy { it.numberInSurah } }
    private val complete: List<SurahWithAyahs> = quran.surahs.map { SurahWithAyahs(it, bySurah.getValue(it.number)) }

    private val en = mushafCopy(AppLanguage.EN)
    private val ru = mushafCopy(AppLanguage.RU)

    private fun marksOf(surah: Int): List<AyahMarks> = MushafMarks.marksOfSurah(surah, bySurah.getValue(surah), complete)

    private fun marks(surah: Int, ayah: Int): AyahMarks = marksOf(surah)[ayah - 1]

    private fun ayah(surah: Int, ayah: Int): AyahEntity = bySurah.getValue(surah)[ayah - 1]

    @Test
    fun theWholeQuranHasThirtyJuzTwoHundredFortyQuartersAndSixHundredFourPagesStarting() {
        val all = (1..114).flatMap(::marksOf)
        assertEquals(6236, all.size)
        assertEquals((1..30).toList(), all.mapNotNull { it.juz })
        assertEquals((1..240).toList(), all.mapNotNull { it.hizbQuarter })
        assertEquals((1..604).toList(), all.mapNotNull { it.page })
    }

    @Test
    fun aJuzThatStartsInsideASurahIsMarkedAtItsFirstAyah() {
        // Juz 2 starts at 2:142 (hizb 3, page 22), juz 3 at 2:253 (hizb 5, page 42).
        assertEquals(AyahMarks(juz = 2, hizbQuarter = 9, page = 22), marks(2, 142))
        assertEquals(AyahMarks(juz = 3, hizbQuarter = 17, page = 42), marks(2, 253))
        assertNull(marks(2, 141).juz)
        assertNull(marks(2, 143).juz)
        assertNull(marks(2, 252).juz)
    }

    @Test
    fun theQuartersOfAHizbAreNumberedFromTheStartOfTheQuran() {
        assertEquals(1, MushafMarks.hizbOf(1))
        assertEquals(1, MushafMarks.hizbOf(4))
        assertEquals(2, MushafMarks.hizbOf(5))
        assertEquals(3, MushafMarks.hizbOf(9))
        assertEquals(3, MushafMarks.hizbOf(12))
        assertEquals(60, MushafMarks.hizbOf(240))
        assertEquals(listOf(0, 1, 2, 3, 0), (9..13).map(MushafMarks::quarterOf))

        assertEquals("Hizb 3", en.hizbLabel(9))
        assertEquals("¼ Hizb 3", en.hizbLabel(10))
        assertEquals("½ Hizb 3", en.hizbLabel(11))
        assertEquals("¾ Hizb 3", en.hizbLabel(12))
        assertEquals("Hizb 4", en.hizbLabel(13))
        // Russian says "¼ хизба 3" (genitive), but "Хизб 3".
        assertEquals("Хизб 3", ru.hizbLabel(9))
        assertEquals("¼ хизба 3", ru.hizbLabel(10))
        assertEquals("½ хизба 3", ru.hizbLabel(11))
        assertEquals("¾ хизба 3", ru.hizbLabel(12))
    }

    @Test
    fun theQuartersInsideAJuzAreMarkedWhereTheyStart() {
        // In the data 2:158 starts the quarter 10 (a quarter of hizb 3), 2:177 the quarter 11 (a half) and 2:189 the quarter 12.
        assertEquals(AyahMarks(hizbQuarter = 10), marks(2, 158))
        assertEquals(MarkLine(start = "¼ Hizb 3", end = null), en.markLine(marks(2, 158)))
        assertEquals(MarkLine(start = "½ Hizb 3", end = "p. 27"), en.markLine(marks(2, 177)))
        assertEquals("¾ Hizb 3", en.markLine(marks(2, 189)).start)
    }

    @Test
    fun severalMarksOnOneAyahShareOneLine() {
        val line = en.markLine(marks(2, 142))
        assertEquals(MarkLine(start = "Juz 2 · Hizb 3", end = "p. 22"), line)
        assertEquals("Juz 2 · Hizb 3 · p. 22", line.spoken)
        assertEquals("Джуз 2 · Хизб 3 · стр. 22", ru.markLine(marks(2, 142)).spoken)
        // A page alone: only the right end.
        assertEquals(MarkLine(start = null, end = "p. 3"), en.markLine(marks(2, 6)))
        // A quarter alone: only the left end.
        assertEquals(MarkLine(start = "¼ Hizb 1", end = null), en.markLine(marks(2, 26)))
    }

    @Test
    fun anAyahInTheMiddleOfAPageHasNoLine() {
        assertFalse(marks(2, 2).hasLine)
        assertFalse(marks(2, 100).hasLine)
        assertEquals(AyahMarks.None, marks(2, 3))
        assertTrue(marks(2, 142).hasLine)
        assertTrue(marks(2, 6).hasLine)
    }

    @Test
    fun theFirstAyahOfASurahIsComparedWithTheLastAyahOfThePreviousOne() {
        // The Quran starts at 1:1: juz 1, the first quarter and page 1 all start there.
        assertEquals(AyahMarks(juz = 1, hizbQuarter = 1, page = 1), marks(1, 1))
        // 2:1 is on page 2 but still in juz 1 and in the first quarter.
        assertEquals(AyahMarks(page = 2), marks(2, 1))
        // Juz 30 starts with the surah An-Naba, juz 14 with Al-Hijr, juz 15 with Al-Isra.
        assertEquals(AyahMarks(juz = 30, hizbQuarter = 233, page = 582), marks(78, 1))
        assertEquals(14, marks(15, 1).juz)
        assertEquals(15, marks(17, 1).juz)
    }

    @Test
    fun withoutThePreviousSurahTheFirstAyahCarriesOnWithoutMarks() {
        assertEquals(AyahMarks.None, MushafMarks.marksOfSurah(3, bySurah.getValue(3), emptyList()).first())
        // Surah 1 needs no previous one: it is the start of the Quran.
        assertEquals(
            AyahMarks(juz = 1, hizbQuarter = 1, page = 1),
            MushafMarks.marksOfSurah(1, bySurah.getValue(1), emptyList()).first(),
        )
    }

    @Test
    fun theCurrentPlaceIsTheJuzTheHizbAndThePageOfTheAyahOnTop() {
        assertEquals(MushafPlace(juz = 2, hizb = 3, page = 22), MushafMarks.placeOf(ayah(2, 142)))
        // A quarter inside a hizb still reads as that hizb.
        assertEquals(MushafPlace(juz = 2, hizb = 3, page = ayah(2, 158).page), MushafMarks.placeOf(ayah(2, 158)))
        assertEquals("Juz 2 · Hizb 3 · p. 22", en.placeText(MushafMarks.placeOf(ayah(2, 142))))
        assertEquals("Джуз 2 · Хизб 3 · стр. 22", ru.placeText(MushafMarks.placeOf(ayah(2, 142))))
        assertEquals(MushafPlace(juz = 30, hizb = 60, page = 604), MushafMarks.placeOf(ayah(114, 6)))
        // Equal places compare equal: the line of the bar only changes when the value does.
        assertEquals(MushafMarks.placeOf(ayah(2, 142)), MushafMarks.placeOf(ayah(2, 142)))
        assertEquals(MushafMarks.placeOf(ayah(2, 143)).juz, MushafMarks.placeOf(ayah(2, 142)).juz)
    }

    @Test
    fun theFifteenSajdahAyahsCarryTheirKind() {
        val all = (1..114).flatMap { surah ->
            marksOf(surah).mapIndexedNotNull { index, mark -> mark.sajda?.let { Triple(surah, index + 1, it) } }
        }
        assertEquals(15, all.size)
        val at = all.associate { (surah, ayah, sajda) -> (surah to ayah) to sajda }
        assertEquals(Sajda(id = 1, recommended = true, obligatory = false), at[7 to 206])
        assertEquals(Sajda(id = 10, recommended = false, obligatory = true), at[32 to 15])
        assertEquals(Sajda(id = 15, recommended = false, obligatory = true), at[96 to 19])

        val recommended = assertNotNull(marks(7, 206).sajda)
        val obligatory = assertNotNull(marks(32, 15).sajda)
        assertEquals("Sajdah · recommended", en.sajdaLabel(recommended))
        assertEquals("Sajdah · obligatory", en.sajdaLabel(obligatory))
        assertEquals("Саджда · желательная", ru.sajdaLabel(recommended))
        assertEquals("Саджда · обязательная", ru.sajdaLabel(obligatory))
        // The sajda alone is no line: it has its own sign next to the number.
        assertFalse(AyahMarks(sajda = recommended).hasLine)
    }

    @Test
    fun marksOfSyntheticAyahsFollowTheChangesBetweenNeighbours() {
        fun a(n: Int, juz: Int, quarter: Int, page: Int) = AyahEntity(n, n, juz, quarter, 1, page, 1, null, "x", 1)
        val ayahs = listOf(a(1, 1, 1, 1), a(2, 1, 1, 1), a(3, 1, 2, 1), a(4, 1, 2, 2), a(5, 2, 9, 2))
        val result = MushafMarks.build(ayahs, previous = a(0, 1, 1, 1))
        assertEquals(
            listOf(
                AyahMarks.None,
                AyahMarks.None,
                AyahMarks(hizbQuarter = 2),
                AyahMarks(page = 2),
                AyahMarks(juz = 2, hizbQuarter = 9),
            ),
            result,
        )
    }
}
