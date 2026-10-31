package tj.app.quran_todo.common.tajweed

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tj.app.quran_todo.data.bundled.QuranFacts

/**
 * The tajweed markup of the whole Quran as the app keeps it, and how it gets there (task F3b).
 *
 * The source is ONE answer of api.alquran.cloud: `GET /v1/quran/quran-tajweed` (2 488 551 bytes, 6236 ayahs). [splitQuran] reads it and
 * checks it (114 surahs, the number of ayahs of every surah as in [QuranFacts], no ayah without text); the app writes every surah as its own
 * small block ([encodeSurah]) so that opening a surah reads a few kilobytes, not 2.5 MB. A block is the markup of the ayahs in order, one
 * per line (the markup holds no line break).
 */
object TajweedFormat {
    /** Where the whole Quran comes from. */
    const val URL: String = "https://api.alquran.cloud/v1/quran/quran-tajweed"

    /** The size of that answer, checked on 2026-10-31: the progress of the download is counted against it (the server may compress it, so its own length is not always known). */
    const val EXPECTED_BYTES: Long = 2_488_551L

    /**
     * The markup of every surah, `result[surah - 1][ayah - 1]`, or null when [json] is not a complete answer: another shape, a surah missing,
     * a surah with a wrong number of ayahs, an ayah without text. A partial or damaged file is never kept.
     */
    fun splitQuran(json: String): List<List<String>>? {
        val response = try {
            reader.decodeFromString<QuranTajweedResponse>(json)
        } catch (_: Exception) {
            return null
        }
        val surahs = response.data.surahs
        if (surahs.size != QuranFacts.SURAH_COUNT) return null
        val result = ArrayList<List<String>>(surahs.size)
        for ((index, surah) in surahs.withIndex()) {
            if (surah.number != index + 1) return null
            if (surah.ayahs.size != QuranFacts.ayahCount(surah.number)) return null
            val markups = ArrayList<String>(surah.ayahs.size)
            for ((ayahIndex, ayah) in surah.ayahs.withIndex()) {
                if (ayah.numberInSurah != ayahIndex + 1 || ayah.text.isEmpty()) return null
                markups += ayah.text.replace('\n', ' ').replace('\r', ' ')
            }
            result += markups
        }
        return result
    }

    /** One surah as the text of a file: the markup of its ayahs, one per line. */
    fun encodeSurah(markups: List<String>): String = markups.joinToString("\n")

    /** The markup of the ayahs of the surah file [text] ([encodeSurah]); null when it does not have [expected] ayahs. */
    fun decodeSurah(text: String, expected: Int): List<String>? {
        val lines = text.split('\n')
        return lines.takeIf { it.size == expected && it.none(String::isEmpty) }
    }

    private val reader = Json { ignoreUnknownKeys = true }

    @Serializable
    private class QuranTajweedResponse(val data: Data)

    @Serializable
    private class Data(val surahs: List<SurahDto>)

    @Serializable
    private class SurahDto(val number: Int, val ayahs: List<AyahDto>)

    @Serializable
    private class AyahDto(val numberInSurah: Int, val text: String)
}
