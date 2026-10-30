package tj.app.quran_todo.ui.downloads

import tj.app.quran_todo.common.audio.Reciters
import tj.app.quran_todo.data.BundledFiles
import tj.app.quran_todo.data.bundled.QuranTsvParser
import tj.app.quran_todo.hifz.schedule.AyahMetric
import tj.app.quran_todo.hifz.schedule.TextMetrics
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadSizeTest {
    private val mib = 1024.0 * 1024.0

    /** The text metrics of the real bundled text: what the app builds from the table `ayahs`. */
    private val metrics: TextMetrics by lazy {
        val quran = QuranTsvParser.parse(BundledFiles.quranTsv())
        TextMetrics(
            quran.ayahs.map { AyahMetric(it.surahNumber, it.numberInSurah, it.page, it.text.codePointCount(0, it.text.length)) },
        )
    }

    @Test
    fun theEstimateIsCharactersTimesBytesPerCharacter() {
        assertEquals(0L, DownloadSize.estimateBytes(0, 2583.0))
        assertEquals(258_300L, DownloadSize.estimateBytes(100, 2583.0))
        assertEquals(3_907L, DownloadSize.estimateBytes(1, 3907.0))
    }

    @Test
    fun everyReciterHasItsOwnCoefficientAndJuzAmmaIsRecitedMoreSlowly() {
        for (reciter in Reciters.all) {
            val rest = DownloadSize.bytesPerChar(reciter, 2)
            val amma = DownloadSize.bytesPerChar(reciter, 112)
            // Ash-Shuraim (about 1.5 KB per character) to Al-Minshawi mujawwad (about 7 KB).
            assertTrue(rest in 1_000.0..7_500.0, "${reciter.id}: $rest")
            // Measured: Juz 30 is recited more slowly for every reciter except Al-Minshawi mujawwad, who draws the long surahs out most.
            if (reciter != Reciters.MinshawiMujawwad) assertTrue(amma >= rest, "${reciter.id}: Juz 30 $amma, the rest $rest")
        }
        // Every reciter has a coefficient of its own: none falls back to the Alafasy one.
        val coefficients = Reciters.all.map { DownloadSize.bytesPerChar(it, 2) }
        assertEquals(coefficients.size, coefficients.toSet().size)
        // The 192 kbit/s edition is bigger than the 128 kbit/s one with the same pace.
        assertTrue(DownloadSize.bytesPerChar(Reciters.AbdulBasit, 2) > DownloadSize.bytesPerChar(Reciters.Alafasy, 2))
        // The first surah of Juz 30 and the last use the same coefficient; the one before does not.
        assertEquals(DownloadSize.bytesPerChar(Reciters.Husary, 78), DownloadSize.bytesPerChar(Reciters.Husary, 114))
        assertTrue(DownloadSize.bytesPerChar(Reciters.Husary, 77) < DownloadSize.bytesPerChar(Reciters.Husary, 78))
    }

    @Test
    fun theBasmalaInFrontOfTheFirstAyahIsNotCounted() {
        // 1:1 is the Basmala itself (and is recited), 9:1 has none; the first ayah of every other surah starts with it.
        assertEquals(metrics.chars(1, 1), DownloadSize.recitedChars(metrics, 1, 1))
        assertEquals(metrics.chars(9, 1), DownloadSize.recitedChars(metrics, 9, 1))
        assertEquals(metrics.chars(2, 1) - DownloadSize.BASMALA_CHARS, DownloadSize.recitedChars(metrics, 2, 1))
        assertEquals(metrics.chars(112, 1) - DownloadSize.BASMALA_CHARS, DownloadSize.recitedChars(metrics, 112, 1))
        // Only the first ayah.
        assertEquals(metrics.chars(112, 2), DownloadSize.recitedChars(metrics, 112, 2))
        assertEquals(
            DownloadSize.recitedChars(metrics, 112, 1) + (2..4).sumOf { metrics.chars(112, it) },
            DownloadSize.recitedChars(metrics, Segment(112, 1, 4)),
        )
    }

    @Test
    fun theBasmalaLengthIsTheLengthOfTheTextOfTheFirstAyah() {
        assertTrue(abs(metrics.chars(1, 1) - DownloadSize.BASMALA_CHARS) <= 1, "1:1 has ${metrics.chars(1, 1)} characters")
    }

    @Test
    fun juzAmmaOfAlafasyIsAboutFiftyToSixtyMegabytes() {
        val bytes = DownloadSize.segmentsBytes(metrics, DownloadSelection.juz(30), Reciters.Alafasy)
        assertTrue(bytes / mib in 50.0..60.0, "Juz Amma of Alafasy: ${bytes / mib} MiB")
        // The measured size of its 564 files is 51.0 MiB (see the KDoc of DownloadSize).
        assertTrue(abs(bytes / mib - 51.0) < 2.0, "Juz Amma of Alafasy: ${bytes / mib} MiB")
    }

    @Test
    fun juzAmmaOfEveryReciterIsCloseToItsMeasuredSize() {
        // The sizes of the files of Juz 30 as the CDN reported them (Content-Length), in MiB.
        val measured = mapOf(
            Reciters.Alafasy to 51.0, Reciters.Husary to 77.1, Reciters.Minshawi to 56.4, Reciters.AbdulBasit to 74.3,
            Reciters.HusaryMuallim to 84.2, Reciters.Sudais to 56.2, Reciters.Shatri to 41.9, Reciters.Rifai to 60.0,
            Reciters.Shuraym to 30.2, Reciters.Tablawi to 65.1, Reciters.AbdulBasitMujawwad to 101.1,
            Reciters.MinshawiMujawwad to 119.1,
        )
        for ((reciter, expected) in measured) {
            val bytes = DownloadSize.segmentsBytes(metrics, DownloadSelection.juz(30), reciter)
            assertTrue(abs(bytes / mib - expected) / expected < 0.03, "${reciter.id}: ${bytes / mib} MiB, measured $expected")
        }
    }

    @Test
    fun theWholeQuranOfAlafasyIsAboutOnePointSixGigabytes() {
        val all = (1..30).flatMap { DownloadSelection.juz(it) }
        val bytes = DownloadSize.segmentsBytes(metrics, all, Reciters.Alafasy)
        val gib = bytes / (mib * 1024)
        assertTrue(gib in 1.4..1.9, "the whole Quran: $gib GiB")
    }

    @Test
    fun aShortSurahIsAFewHundredKilobytes() {
        // The four files of Al-Ikhlas by Alafasy are 215 KB together (Content-Length).
        val bytes = DownloadSize.segmentsBytes(metrics, DownloadSelection.surah(112), Reciters.Alafasy)
        assertTrue(bytes / mib in 0.15..0.35, "Al-Ikhlas: ${bytes / mib} MiB")
    }

    @Test
    fun whatIsLeftCountsEveryMissingAyahOnceAndSkipsWhatIsThere() {
        val juz = DownloadRequest(
            id = "juz-30-alafasy", kind = DownloadKind.JUZ, title = "t", reciterId = "alafasy", segments = DownloadSelection.juz(30),
        )
        val surah = DownloadRequest(
            id = "surah-112-alafasy", kind = DownloadKind.SURAH, title = "t", reciterId = "alafasy", segments = DownloadSelection.surah(112),
        )
        val both = DownloadSize.remainingBytes(listOf(juz, surah), emptyMap(), metrics)
        val juzOnly = DownloadSize.remainingBytes(listOf(juz), emptyMap(), metrics)
        assertEquals(juzOnly, both, "Al-Ikhlas is inside the juz and is counted once")

        // Everything of Al-Ikhlas is there: only the rest of the juz remains.
        val have = surah.keys().associateWith { 100_000L }
        val left = DownloadSize.remainingBytes(listOf(juz), have, metrics)
        assertClose(juzOnly - DownloadSize.segmentsBytes(metrics, surah.segments, Reciters.Alafasy), left, 4.0)
        assertEquals(0L, DownloadSize.remainingBytes(listOf(juz), juz.keys().associateWith { 1L }, metrics))
    }

    private fun assertClose(expected: Long, actual: Long, tolerance: Double) {
        assertTrue(abs(expected - actual) <= tolerance, "expected $expected, was $actual")
    }
}
