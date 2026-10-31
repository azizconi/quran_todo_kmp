package tj.app.quran_todo.ui.similar

import tj.app.quran_todo.common.mushaf.AyahKey
import tj.app.quran_todo.common.similar.SimilarIndex
import tj.app.quran_todo.common.similar.normalizedWords
import tj.app.quran_todo.common.similar.wordDiff
import tj.app.quran_todo.ui.reader.RubElHizbSign
import tj.app.quran_todo.ui.reader.splitBismillah
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The index of similar ayahs that ships in the app (`files/quran/similar.tsv`) against the text that ships next to it
 * (`quran-uthmani.tsv`), through the real code of the app: the words the script saw as equal are equal for [wordDiff] too, the
 * Bismillah and the rub el hizb sign are not counted, the known pairs are there, the refrains are whole.
 */
class SimilarIndexDataTest {
    private val resources = File("src/commonMain/composeResources/files/quran")
    private val index: SimilarIndex = SimilarIndex.parse(File(resources, "similar.tsv").readText())

    /** The text of every ayah as the screens draw it: `splitBismillah`. */
    private val texts: Map<AyahKey, String> by lazy {
        File(resources, "quran-uthmani.tsv").readLines().filter { it.startsWith("A\t") }.associate { line ->
            val fields = line.split('\t')
            val surah = fields[2].toInt()
            val ayah = fields[3].toInt()
            AyahKey(surah, ayah) to splitBismillah(surah, ayah, fields.last())
        }
    }

    @Test
    fun theIndexHasThePairsOfTheScriptAndEachAyahKeepsAtMostTen() {
        assertTrue(index.pairCount in 4_000..8_000, "pairs: ${index.pairCount}")
        assertTrue(index.ayahs.size in 2_500..4_000, "ayahs with likes: ${index.ayahs.size}")
        assertTrue(index.ayahs.all { index.similar(it).size in 1..10 })
    }

    @Test
    fun everyPairIsSymmetric() {
        for (ref in index.ayahs) {
            for (other in index.similar(ref)) {
                val back = index.similar(other.ref).firstOrNull { it.ref == ref }
                assertNotNull(back, "$ref -> ${other.ref} has no way back")
                assertEquals(other.score, back.score)
            }
        }
    }

    @Test
    fun theKnownPairsAreThere() {
        val known = listOf(
            2 to 35 to (7 to 19), 2 to 58 to (7 to 161), 2 to 49 to (7 to 141), 7 to 141 to (14 to 6), 2 to 173 to (16 to 115),
            2 to 173 to (6 to 145), 2 to 62 to (5 to 69), 3 to 126 to (8 to 10), 2 to 136 to (3 to 84), 2 to 57 to (7 to 160),
            14 to 35 to (2 to 126), 6 to 151 to (17 to 31), 7 to 80 to (27 to 54), 3 to 62 to (38 to 65), 2 to 120 to (2 to 145),
        )
        for ((first, second) in known.map { (a, b) -> AyahKey(a.first, a.second) to AyahKey(b.first, b.second) }) {
            assertTrue(index.similar(first).any { it.ref == second }, "$first and $second are not similar in the index")
        }
    }

    @Test
    fun theRefrainsAreWhole() {
        val rahman = index.repeats(AyahKey(55, 13))
        assertNotNull(rahman)
        assertEquals(31, rahman.size)
        assertEquals(AyahKey(55, 77), rahman.last())
        assertTrue(rahman.all { it.surah == 55 })
        assertEquals(rahman, index.repeats(AyahKey(55, 40)))
        // "Woe that Day to the deniers" is in Al-Mursalat ten times and once in Al-Mutaffifin.
        val woe = assertNotNull(index.repeats(AyahKey(77, 15)))
        assertEquals(11, woe.size)
        assertTrue(AyahKey(83, 10) in woe)
        // a refrain is not listed as similar to itself
        assertTrue(index.similar(AyahKey(55, 13)).none { it.ref.surah == 55 && it.ref in rahman })
    }

    @Test
    fun theWordsOfThePairsMatchInTheCodeOfTheApp() {
        // The script and the app normalize alike: for every pair of the index the app finds at least the three shared words that made it a pair.
        var checked = 0
        for (ref in index.ayahs) {
            for (other in index.similar(ref)) {
                if (other.ref.surah < ref.surah || (other.ref.surah == ref.surah && other.ref.ayah < ref.ayah)) continue
                val diff = wordDiff(texts.getValue(ref), texts.getValue(other.ref))
                assertTrue(diff.a.matched >= 3, "$ref and ${other.ref}: ${diff.a.matched} words match")
                checked++
            }
        }
        assertTrue(checked > 4_000, "only $checked pairs checked")
    }

    @Test
    fun aDifferenceIsAWholeWordOfTheShownText() {
        val first = texts.getValue(AyahKey(2, 35))
        val second = texts.getValue(AyahKey(7, 19))
        val diff = wordDiff(first, second)
        val words = normalizedWords(first)
        assertEquals(words.ranges, diff.a.ranges)
        for (range in diff.a.differingRanges + diff.b.differingRanges) assertTrue(range.first >= 0 && range.last >= range.first)
        assertTrue(diff.a.differingRanges.isNotEmpty() && diff.b.differingRanges.isNotEmpty())
        assertTrue(diff.a.matched >= 10)
    }

    @Test
    fun theBismillahOfTheFirstAyahIsNotAWord() {
        // Al-Baqara 1 begins with the Bismillah in the text of the database; what is shown and compared is "الم" only.
        val shown = texts.getValue(AyahKey(2, 1))
        assertEquals(1, normalizedWords(shown).size)
        // Al-Fatiha 1 IS the Bismillah: its four words stay.
        assertEquals(4, normalizedWords(texts.getValue(AyahKey(1, 1))).size)
        // So the four words of the Bismillah never make a pair of the first ayahs of two surahs.
        val firstAyahs = index.ayahs.filter { it.ayah == 1 && it.surah > 1 }
        for (ref in firstAyahs) {
            for (other in index.similar(ref)) {
                val diff = wordDiff(texts.getValue(ref), texts.getValue(other.ref))
                assertTrue(diff.a.matched >= 3, "$ref and ${other.ref}")
            }
        }
    }

    @Test
    fun theRubElHizbSignIsNotInTheComparedText() {
        val withSign = File(resources, "quran-uthmani.tsv").readLines().filter { it.startsWith("A\t") && RubElHizbSign in it.split('\t').last() }
        assertTrue(withSign.size > 150)
        for (line in withSign.take(40)) {
            val fields = line.split('\t')
            val shown = splitBismillah(fields[2].toInt(), fields[3].toInt(), fields.last())
            assertFalse(RubElHizbSign in shown)
            assertEquals(normalizedWords(fields.last()).words, normalizedWords(shown).words)
        }
    }

    @Test
    fun anAyahWithoutLikesHasNone() {
        assertNull(index.repeats(AyahKey(2, 35)))
        assertTrue(index.similar(AyahKey(1, 2)).isEmpty() || index.similar(AyahKey(1, 2)).all { it.score in 0..100 })
        assertEquals(0, SimilarIndex.Empty.count(AyahKey(2, 35)))
    }
}
