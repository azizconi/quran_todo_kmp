package tj.app.quran_todo.ui.common

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The juz boundaries agree with the `juz` column of the bundled Quran text, ayah by ayah. */
class JuzIndexTest {
    private val tsv = File("src/commonMain/composeResources/files/quran/quran-uthmani.tsv")

    /** surah, ayah in surah, juz for every ayah row of the bundled text. */
    private val ayahs: List<Triple<Int, Int, Int>> = tsv.readLines()
        .filter { it.startsWith("A\t") }
        .map { line -> line.split('\t').let { Triple(it[2].toInt(), it[3].toInt(), it[4].toInt()) } }

    @Test
    fun everyAyahOfTheBundledTextIsInTheSameJuz() {
        assertEquals(6236, ayahs.size)
        ayahs.forEach { (surah, ayah, juz) -> assertEquals(juz, JuzIndex.juzOf(surah, ayah), "$surah:$ayah") }
    }

    @Test
    fun theRangesCoverTheQuranOnceInOrder() {
        assertEquals(6236, (1..JuzIndex.COUNT).sumOf(JuzIndex::ayahCount))
        val counts = ayahs.groupingBy { it.third }.eachCount()
        (1..JuzIndex.COUNT).forEach { juz -> assertEquals(counts[juz], JuzIndex.ayahCount(juz), "juz $juz") }
        assertEquals(JuzRange(30, 78, 1, 114, 6), JuzIndex.range(30))
        assertEquals(JuzRange(1, 1, 1, 2, 141), JuzIndex.range(1))
        assertEquals(JuzRange(13, 12, 53, 14, 52), JuzIndex.range(13))
    }

    @Test
    fun aJuzKnowsWhichAyahsOfASurahItHolds() {
        val second = JuzIndex.range(2)
        assertEquals(142..252, second.ayahsOf(2))
        assertEquals(null, second.ayahsOf(3))
        assertEquals(1..6, JuzIndex.range(30).ayahsOf(114))
        assertEquals("عم", JuzIndex.arabicName(30))
    }

    @Test
    fun coverageIsCountedInsideTheJuzOnly() {
        // Al-Baqarah 1..150 known: 141 ayahs in juz 1, 9 in juz 2; An-Nas whole in juz 30.
        val covered = mapOf(2 to (1..150).toSet(), 114 to (1..6).toSet())
        assertEquals(7 + 141, JuzIndex.coveredCount(1, covered + (1 to (1..7).toSet())))
        assertEquals(9, JuzIndex.coveredCount(2, covered))
        assertEquals(6, JuzIndex.coveredCount(30, covered))
        assertEquals(0, JuzIndex.coveredCount(15, covered))
    }
}
