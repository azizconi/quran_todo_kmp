package tj.app.quran_todo.khatm

import tj.app.quran_todo.data.bundled.QuranFacts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The juz of the night (task F5b) as passages: the thirty juz tile the Quran with no gap and no overlap. */
class JuzPassagesTest {
    @Test
    fun theThirtyJuzCoverEveryAyahOnce() {
        val all = (1..30).flatMap { JuzPassages.of(it) }
        assertEquals(6236, all.sumOf { it.ayahCount })
        var surah = 1
        var ayah = 1
        all.forEach { range ->
            assertEquals(surah, range.surah)
            assertEquals(ayah, range.startAyah)
            assertTrue(range.endAyah in range.startAyah..QuranFacts.ayahCount(range.surah))
            if (range.endAyah == QuranFacts.ayahCount(range.surah)) {
                surah++
                ayah = 1
            } else {
                ayah = range.endAyah + 1
            }
        }
        assertEquals(115, surah)
    }

    @Test
    fun theFirstAndTheLastJuzAreWhereTheyShouldBe() {
        val first = JuzPassages.of(1)
        assertEquals(1, first.first().surah)
        assertEquals(1, first.first().startAyah)
        // Juz 1 holds Al-Fatiha and the first of Al-Baqarah.
        assertEquals(listOf(1, 2), first.map { it.surah })
        val last = JuzPassages.of(30)
        assertEquals(114, last.last().surah)
        assertEquals(6, last.last().endAyah)
        assertEquals(78, last.first().surah)
        assertEquals(JuzPassages.of(30), JuzPassages.of(31))
        assertEquals(JuzPassages.of(1), JuzPassages.of(0))
    }
}
