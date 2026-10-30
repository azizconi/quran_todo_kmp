package tj.app.quran_todo.ui.downloads

import tj.app.quran_todo.common.audio.Reciter
import tj.app.quran_todo.common.audio.Reciters
import tj.app.quran_todo.hifz.schedule.TextMetrics
import kotlin.math.roundToLong

/**
 * Size of the recordings before they are downloaded: a pure function of the length of the Arabic text and a coefficient of bytes per
 * character that is measured for each reciter ([estimateBytes]). A recording is an MP3 of the ayah read aloud, so its size follows the
 * time the ayah takes and that follows its length.
 *
 * **How the coefficients were measured** (task D3, 2026-10-30): the real size of the file of every ayah of Juz 30 (564 ayahs) and of
 * every ninth ayah of the rest of the Quran (about 630 ayahs), for each of the four reciters, from the `Content-Length` of
 * `curl -sI https://cdn.islamic.network/quran/audio/{bitrate}/{edition}/{ayah}.mp3` (a few answers that failed on the way were left
 * out). The text length is the number of Unicode characters of the bundled `quran-uthmani` text (what `TextMetrics` counts), without
 * the Basmala that the text puts in front of the first ayah of a surah (the recording of that ayah does not contain it, see
 * [BASMALA_CHARS]). A coefficient is the sum of the sizes divided by the sum of the characters, so the estimate of a large set is not
 * biased. Ten to twenty ayahs of different length would have given the same order of magnitude; the full Juz 30 was measured because
 * it is the set people download first, and the numbers show that its surahs are recited more slowly (more bytes per character) than the
 * rest for three of the four reciters, so Juz 30 has its own coefficient:
 *
 * | reciter | bytes per character, surahs 78..114 | bytes per character, the rest | Juz 30, measured |
 * |---|---|---|---|
 * | Alafasy (128 kbit/s) | 2583 | 2394 | 51.0 MiB |
 * | Husary (128) | 3907 | 3467 | 77.1 MiB |
 * | Minshawi (128) | 2856 | 2306 | 56.4 MiB |
 * | Abdul Basit (192) | 3762 | 3761 | 74.3 MiB |
 *
 * The estimate of a juz or a surah outside Juz 30 can be off by 10-20% (the pace of a recitation changes from surah to surah); of a
 * large set (a plan, the whole Quran, about 1.6 GiB for Alafasy) by less. Inside Juz 30 the whole juz is exact (its coefficient is
 * its measured mean) and one surah is off by 11% on average (Alafasy) and by up to 37%: An-Nas is estimated at 404 KB and its six
 * files are 642 KB, because that surah is recited slowly. A per-ayah overhead (bytes = a + b x characters) was tried and is not
 * better per surah, so the model stays one coefficient. On the emulator Juz 1 came to 48 MB (estimate 53) and Juz 2 to 46 MB
 * (estimate 55). The screen always writes "about" ("≈ 54 MB") and, once files are there, their real size.
 */
internal object DownloadSize {
    /**
     * Characters of "bismillahi r-rahmani r-rahim" with its vowel marks. In the bundled text the first ayah of every surah except
     * Al-Fatihah and At-Tawbah starts with it, while its recording is only the ayah itself.
     */
    const val BASMALA_CHARS = 38

    /** The first surah of Juz 30 (An-Naba); the surahs from it to the end of the Quran use [Coefficients.juzAmma]. */
    private const val FIRST_SURAH_OF_JUZ_AMMA = 78

    private class Coefficients(val juzAmma: Double, val rest: Double)

    /** Bytes of MP3 per character of text, for each reciter (the `id` of [Reciter]). */
    private val coefficients: Map<String, Coefficients> = mapOf(
        "alafasy" to Coefficients(juzAmma = 2583.0, rest = 2394.0),
        "husary" to Coefficients(juzAmma = 3907.0, rest = 3467.0),
        "minshawi" to Coefficients(juzAmma = 2856.0, rest = 2306.0),
        "abdulbasit" to Coefficients(juzAmma = 3762.0, rest = 3761.0),
    )

    /** Bytes per character of the recitation of [surah] by [reciter]. */
    fun bytesPerChar(reciter: Reciter, surah: Int): Double {
        val measured = coefficients[reciter.id] ?: coefficients.getValue("alafasy")
        return if (surah >= FIRST_SURAH_OF_JUZ_AMMA) measured.juzAmma else measured.rest
    }

    /** The size of a recording of [chars] characters: the whole of the estimate. */
    fun estimateBytes(chars: Int, bytesPerChar: Double): Long = (chars * bytesPerChar).roundToLong()

    /** The characters that are recited in ayah [ayah] of [surah]: its text without the Basmala that opens the text of a surah. */
    fun recitedChars(metrics: TextMetrics, surah: Int, ayah: Int): Int {
        val chars = metrics.chars(surah, ayah)
        val opensWithBasmala = ayah == 1 && surah != 1 && surah != 9
        return if (opensWithBasmala) (chars - BASMALA_CHARS).coerceAtLeast(0) else chars
    }

    /** The recited characters of a [segment]. */
    fun recitedChars(metrics: TextMetrics, segment: Segment): Int =
        (segment.first..segment.last).sumOf { ayah -> recitedChars(metrics, segment.surah, ayah) }

    /** Estimated size of the recordings of [segments] read by [reciter]. */
    fun segmentsBytes(metrics: TextMetrics, segments: List<Segment>, reciter: Reciter): Long =
        segments.sumOf { segment ->
            estimateBytes(recitedChars(metrics, segment), bytesPerChar(reciter, segment.surah))
        }

    /**
     * Estimated size of what is still to download for [requests]: every missing ayah once, even when a juz and a surah inside it
     * (or a plan) ask for it. [have] are the downloaded files (key to bytes).
     */
    fun remainingBytes(requests: List<DownloadRequest>, have: Map<String, Long>, metrics: TextMetrics): Long {
        val counted = HashSet<String>()
        var total = 0L
        for (request in requests) {
            val reciter = Reciters.byId(request.reciterId)
            for (segment in request.segments) {
                val perChar = bytesPerChar(reciter, segment.surah)
                for (ayah in segment.first..segment.last) {
                    val key = reciter.cacheKey(segment.surah, ayah)
                    if (key in have || !counted.add(key)) continue
                    total += estimateBytes(recitedChars(metrics, segment.surah, ayah), perChar)
                }
            }
        }
        return total
    }
}
