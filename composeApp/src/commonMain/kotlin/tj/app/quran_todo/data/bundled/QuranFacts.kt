package tj.app.quran_todo.data.bundled

/**
 * Fixed facts about the Quran in the Hafs numbering used by every edition of api.alquran.cloud
 * (all editions have the same 114 surahs and 6236 ayahs, numbered 1..6236 in the whole Quran).
 *
 * The bundled text is checked against this table when it is loaded, and the Uzbek translation (which comes
 * from quran.com without ayah numbers) is mapped to global ayah numbers with it.
 */
object QuranFacts {
    const val SURAH_COUNT = 114
    const val AYAH_COUNT = 6236
    const val SAJDA_COUNT = 15

    private val ayahCounts = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
        112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
        54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
        14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
        29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
        11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6,
    )

    private val firstNumbers: IntArray = IntArray(SURAH_COUNT).also { first ->
        var next = 1
        for (index in 0 until SURAH_COUNT) {
            first[index] = next
            next += ayahCounts[index]
        }
    }

    /** Number of ayahs in [surah] (1..114); 0 for anything else. */
    fun ayahCount(surah: Int): Int = ayahCounts.getOrElse(surah - 1) { 0 }

    /** Global number (1..6236) of the first ayah of [surah] (1..114); 0 for anything else. */
    fun firstAyahNumber(surah: Int): Int = firstNumbers.getOrElse(surah - 1) { 0 }
}
