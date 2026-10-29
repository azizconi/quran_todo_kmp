package tj.app.quran_todo.hifz.schedule

import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.database.entity.hifz.AyahMetricRow
import tj.app.quran_todo.hifz.model.Passage

/** The size of one ayah: its characters (the weight of the ayah in every estimate) and the mushaf page it starts on. */
class AyahMetric(val surah: Int, val ayah: Int, val page: Int, val chars: Int)

/**
 * The text metrics the scheduler needs: for every ayah `(surah, ayahInSurah, page, chars)`. Built once from one
 * projection of the table `ayahs` and kept in memory (6236 rows). Ranges are answered with prefix sums.
 *
 * A surah that is absent has no ayahs. In the app all 114 surahs are there; tests may build a smaller set.
 */
class TextMetrics(metrics: List<AyahMetric>) {
    private val pages = arrayOfNulls<IntArray>(QuranFacts.SURAH_COUNT + 1)
    private val prefix = arrayOfNulls<IntArray>(QuranFacts.SURAH_COUNT + 1)

    init {
        val bySurah = metrics.groupBy { it.surah }
        for ((surah, rows) in bySurah) {
            require(surah in 1..QuranFacts.SURAH_COUNT) { "surah $surah" }
            val sorted = rows.sortedBy { it.ayah }
            require(sorted.withIndex().all { (index, row) -> row.ayah == index + 1 }) {
                "the ayahs of surah $surah are not numbered 1..${sorted.size}"
            }
            val surahPages = IntArray(sorted.size + 1)
            val sums = IntArray(sorted.size + 1)
            for (row in sorted) {
                surahPages[row.ayah] = row.page
                sums[row.ayah] = sums[row.ayah - 1] + row.chars
            }
            pages[surah] = surahPages
            prefix[surah] = sums
        }
    }

    /** Number of ayahs of [surah]; 0 if there is no such surah in the metrics. */
    fun ayahCount(surah: Int): Int = (pages.getOrNull(surah)?.size ?: 1) - 1

    /** Mushaf page the ayah starts on. */
    fun page(surah: Int, ayah: Int): Int = pages[surah]!![ayah]

    /** Characters of one ayah. */
    fun chars(surah: Int, ayah: Int): Int = chars(surah, ayah, ayah)

    /** Characters of the range [start]..[end] of [surah] (the weight of a passage). */
    fun chars(surah: Int, start: Int, end: Int): Int {
        val sums = prefix.getOrNull(surah) ?: return 0
        if (end < start) return 0
        return sums[end.coerceAtMost(sums.size - 1)] - sums[(start - 1).coerceAtLeast(0)]
    }

    /** The weight of a passage. */
    fun weight(passage: Passage): Int = chars(passage.surah, passage.startAyah, passage.endAyah)

    /** Characters of the whole surah. */
    fun surahChars(surah: Int): Int = chars(surah, 1, ayahCount(surah))

    companion object {
        fun fromRows(rows: List<AyahMetricRow>): TextMetrics =
            TextMetrics(rows.map { AyahMetric(it.surah, it.ayah, it.page, it.chars) })
    }
}
