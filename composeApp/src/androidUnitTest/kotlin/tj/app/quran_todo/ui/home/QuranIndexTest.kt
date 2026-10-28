package tj.app.quran_todo.ui.home

import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahEntity
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuranIndexTest {
    private fun surah(number: Int, firstGlobal: Int, count: Int) = SurahWithAyahs(
        surah = SurahEntity(
            number = number,
            name = "سورة $number",
            englishName = "Surah $number",
            englishNameTranslation = "Translation $number",
            revelationType = "Meccan",
            editionId = "quran-uthmani",
        ),
        // Deliberately not in reading order: the index must sort by the number inside the surah.
        ayahs = (1..count).reversed().map { inSurah ->
            AyahEntity(
                number = firstGlobal + inSurah - 1,
                numberInSurah = inSurah,
                juz = 1,
                hizbQuarter = 1,
                manzil = 1,
                page = 1,
                ruku = 1,
                sajda = null,
                text = "text",
                surahNumber = number,
            )
        },
    )

    private val index = QuranIndex.from(listOf(surah(2, firstGlobal = 8, count = 10), surah(1, firstGlobal = 1, count = 7)))

    @Test
    fun surahsAreSortedAndCarryTheirNames() {
        assertEquals(listOf(1, 2), index.surahs.map { it.number })
        val second = index.meta(2)!!
        assertEquals("سورة 2", second.arabicName)
        assertEquals("Surah 2", second.englishName)
        assertEquals("Translation 2", second.englishTranslation)
        assertEquals(10, second.ayahCount)
        assertNull(index.meta(3))
    }

    @Test
    fun globalAyahsAreListedInReadingOrder() {
        assertContentEquals(intArrayOf(8, 9, 10, 11, 12, 13, 14, 15, 16, 17), index.globalAyahs(2))
        assertContentEquals(IntArray(0), index.globalAyahs(99))
    }

    @Test
    fun globalNumbersMapToTheNumberInTheSurah() {
        assertEquals(1, index.localAyah(1))
        assertEquals(7, index.localAyah(7))
        assertEquals(1, index.localAyah(8))
        assertEquals(10, index.localAyah(17))
        // Unknown ayahs come back unchanged rather than failing.
        assertEquals(500, index.localAyah(500))
    }

    @Test
    fun aGlobalAyahIsOnlyFoundInItsOwnSurah() {
        assertEquals(5, index.localAyahInSurah(2, 12))
        assertNull(index.localAyahInSurah(1, 12))
        assertNull(index.localAyahInSurah(2, 7))
        assertNull(index.localAyahInSurah(9, 12))
    }

    @Test
    fun ayahCountsFallBackToTheStandardNumbering() {
        assertEquals(10, index.ayahCount(2))
        assertEquals(QuranMeta.ayahCount(3), index.ayahCount(3))
    }

    @Test
    fun anEmptyQuranGivesTheEmptyIndex() {
        assertTrue(QuranIndex.from(emptyList()).isEmpty)
        assertTrue(QuranIndex.Empty.surahs.isEmpty())
    }

    @Test
    fun theStandardIndexFollowsTheRealNumbering() {
        val standard = QuranIndex.standard()
        assertEquals(114, standard.surahs.size)
        // Al-Baqarah starts right after Al-Fatihah's 7 ayahs and has 286.
        assertEquals(8, standard.globalAyahs(2).first())
        assertEquals(293, standard.globalAyahs(2).last())
        assertEquals(6236, standard.globalAyahs(114).last())
        assertEquals(255, standard.localAyah(standard.globalAyahs(2)[254]))
    }
}
