package tj.app.quran_todo.data

import kotlinx.coroutines.runBlocking
import tj.app.quran_todo.data.bundled.BundledQuran
import tj.app.quran_todo.data.bundled.QuranDataException
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.bundled.QuranTsvParser
import tj.app.quran_todo.ui.reader.BismillahText
import tj.app.quran_todo.ui.reader.splitBismillah
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuranTsvParserTest {
    private val Bom: Char = 0xFEFF.toChar()

    private val text = BundledFiles.quranTsv()
    private val quran: BundledQuran = QuranTsvParser.parse(text)

    /** Letters only: harakat, Quranic annotation marks and tatweel removed. */
    private fun skeleton(text: String): String = text.filterNot { c ->
        c.code in 0x064B..0x065F || c.code == 0x0670 || c.code == 0x0640 || c.code in 0x06D6..0x06ED
    }

    private fun ayah(surah: Int, number: Int) =
        quran.ayahs.first { it.surahNumber == surah && it.numberInSurah == number }

    @Test
    fun theBundledFileHasTheWholeQuran() {
        assertEquals(114, quran.surahs.size)
        assertEquals(6236, quran.ayahs.size)
        assertEquals((1..114).toList(), quran.surahs.map { it.number })
        assertEquals((1..6236).toList(), quran.ayahs.map { it.number })
        assertEquals("quran-uthmani", quran.edition.identifier)
        assertTrue(quran.surahs.all { it.editionId == quran.edition.identifier })
        assertEquals("Al-Faatiha", quran.surahs.first().englishName)
        assertEquals("Meccan", quran.surahs.first().revelationType)
        assertEquals(114, quran.ayahs.last().surahNumber)
        assertEquals(6, quran.ayahs.last().numberInSurah)
    }

    @Test
    fun everySurahHasItsAyahCountAndTheGlobalNumbersLineUp() {
        quran.ayahs.groupBy { it.surahNumber }.forEach { (surah, ayahs) ->
            assertEquals(QuranFacts.ayahCount(surah), ayahs.size, "ayah count of surah $surah")
        }
        quran.ayahs.forEach { ayah ->
            assertEquals(
                ayah.number,
                QuranFacts.firstAyahNumber(ayah.surahNumber) + ayah.numberInSurah - 1,
                "global number of ${ayah.surahNumber}:${ayah.numberInSurah}",
            )
        }
        assertEquals(6236, (1..114).sumOf { QuranFacts.ayahCount(it) })
    }

    @Test
    fun noTextHasAByteOrderMark() {
        assertFalse(text.contains(Bom), "the file contains U+FEFF")
        assertTrue(quran.ayahs.none { it.text.contains(Bom) })
    }

    @Test
    fun theFirstAyahStartsWithTheBismillah() {
        val first = ayah(1, 1)
        assertTrue(first.text.startsWith("بِسْمِ"), "1:1 starts with ${first.text.take(6)}")
        assertEquals('ب', first.text.first(), "no byte order mark in front of the first letter")
        // the text of 1:1 is the Bismillah and nothing else (compared without the marks: they are encoded in varying order)
        assertEquals(skeleton(BismillahText), skeleton(first.text))
    }

    @Test
    fun theBismillahStaysInTheFirstAyahOfTheOtherSurahsLikeInTheApi() {
        // The old iOS interface shows it straight from the ayah text; Android splits it off itself.
        assertEquals(skeleton(BismillahText) + " الم", skeleton(ayah(2, 1).text))
        assertEquals(skeleton(BismillahText) + " قل هو ٱلله أحد", skeleton(ayah(112, 1).text))
        assertTrue(skeleton(ayah(114, 1).text).startsWith(skeleton(BismillahText) + " قل"))
        // At-Tawbah has no Bismillah.
        assertFalse(skeleton(ayah(9, 1).text).startsWith(skeleton(BismillahText)))
        // Every other surah (2..114 without 9) has it at the start of its first ayah.
        (2..114).filter { it != 9 }.forEach { surah ->
            assertTrue(skeleton(ayah(surah, 1).text).startsWith(skeleton(BismillahText)), "surah $surah")
        }
    }

    @Test
    fun theAndroidReaderStillSplitsTheBismillahOffTheBundledText() {
        // ui/reader/Bismillah.kt works on exactly this text.
        assertEquals("الم", skeleton(splitBismillah(2, 1, ayah(2, 1).text)))
        assertEquals("قل هو ٱلله أحد", skeleton(splitBismillah(112, 1, ayah(112, 1).text)))
        // Al-Fatiha and At-Tawbah are returned unchanged
        assertEquals(ayah(1, 1).text, splitBismillah(1, 1, ayah(1, 1).text))
        assertEquals(ayah(9, 1).text, splitBismillah(9, 1, ayah(9, 1).text))
    }

    @Test
    fun fifteenAyahsHaveAProstration() {
        val sajdas = quran.ayahs.filter { it.sajda != null }
        assertEquals(QuranFacts.SAJDA_COUNT, sajdas.size)
        assertEquals((1..15).toList(), sajdas.map { it.sajda!!.id })
        val a7 = ayah(7, 206).sajda
        assertNotNull(a7)
        assertTrue(a7.recommended)
        assertFalse(a7.obligatory)
        val a96 = ayah(96, 19).sajda
        assertNotNull(a96)
        assertTrue(a96.obligatory)
        assertNull(ayah(1, 1).sajda)
    }

    @Test
    fun theMetadataIsKept() {
        assertEquals(1, ayah(1, 1).page)
        assertEquals(604, quran.ayahs.maxOf { it.page })
        assertEquals((1..30).toList(), quran.ayahs.map { it.juz }.distinct())
        assertEquals(240, quran.ayahs.maxOf { it.hizbQuarter })
        assertEquals((1..7).toList(), quran.ayahs.map { it.manzil }.distinct())
        assertEquals(1, ayah(1, 1).ruku)
        assertEquals(30, ayah(78, 1).juz)
        assertEquals(2, ayah(2, 142).juz)
        // Pages never go back.
        quran.ayahs.zipWithNext().forEach { (a, b) -> assertTrue(b.page >= a.page, "page of ${b.number}") }
    }

    @Test
    fun parsingTheBundledFileIsFastAndOffTheMainThread() {
        val times = (1..3).map {
            val started = System.nanoTime()
            val parsed = QuranTsvParser.parse(text)
            assertEquals(6236, parsed.ayahs.size)
            (System.nanoTime() - started) / 1_000_000
        }
        println("QuranTsvParser: ${text.length} chars, parse times on the host JVM (ms): $times")
        assertTrue(times.min() < 3_000, "parsing took $times ms")

        // BundledQuranSource does the same work on Dispatchers.Default.
        val (loaded, timing) = runBlocking { BundledFiles.source().loadTimed() }
        assertEquals(6236, loaded.ayahs.size)
        println("BundledQuranSource.loadTimed (host JVM): read ${timing.readMs} ms, parse ${timing.parseMs} ms")
    }

    // -- Damaged files fail loudly instead of filling the database halfway ----------------------------------------

    private fun lines() = text.split('\n')

    @Test
    fun aMissingAyahIsRejected() {
        val damaged = lines().filterNot { it.startsWith("A\t6236\t") }.joinToString("\n")
        assertFailsWith<QuranDataException> { QuranTsvParser.parse(damaged) }
    }

    @Test
    fun aByteOrderMarkIsRejected() {
        // a BOM in front of the text of 1:1, the way the old download had it (the text is the last column)
        val damaged = lines().joinToString("\n") { line ->
            if (line.startsWith("A\t1\t")) line.substringBeforeLast('\t') + "\t" + Bom + line.substringAfterLast('\t') else line
        }
        assertFailsWith<QuranDataException> { QuranTsvParser.parse(damaged) }
    }

    @Test
    fun aWrongColumnCountIsRejected() {
        val damaged = lines().joinToString("\n") { line ->
            if (line.startsWith("A\t2\t")) line.substringBeforeLast('\t') else line
        }
        assertFailsWith<QuranDataException> { QuranTsvParser.parse(damaged) }
    }

    @Test
    fun anAyahOutOfOrderIsRejected() {
        val rows = lines().toMutableList()
        val first = rows.indexOfFirst { it.startsWith("A\t10\t") }
        val second = rows.indexOfFirst { it.startsWith("A\t11\t") }
        rows[first] = rows[second].also { rows[second] = rows[first] }
        assertFailsWith<QuranDataException> { QuranTsvParser.parse(rows.joinToString("\n")) }
    }

    @Test
    fun anEmptyFileIsRejected() {
        assertFailsWith<QuranDataException> { QuranTsvParser.parse("") }
        assertFailsWith<QuranDataException> { QuranTsvParser.parse("# only a comment\n") }
    }

    @Test
    fun windowsLineBreaksAndCommentsAreTolerated() {
        val crlf = "# comment\r\n\r\n" + text.replace("\n", "\r\n")
        val parsed = QuranTsvParser.parse(crlf)
        assertEquals(6236, parsed.ayahs.size)
        assertEquals(quran.ayahs.first().text, parsed.ayahs.first().text)
        assertEquals(quran.ayahs.map { it.text }, parsed.ayahs.map { it.text })
    }
}
