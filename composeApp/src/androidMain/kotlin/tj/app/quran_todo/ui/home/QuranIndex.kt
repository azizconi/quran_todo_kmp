package tj.app.quran_todo.ui.home

import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs

/** What the home screen needs to know about one surah of the loaded Quran. */
internal class SurahMeta(
    val number: Int,
    val arabicName: String,
    val englishName: String,
    val englishTranslation: String,
    val ayahCount: Int,
    /** Global ayah numbers (1..6236) of this surah in reading order. */
    val globalAyahs: IntArray,
)

/**
 * A compact copy of the facts the planner and the rows need, so they do not walk the 6236 ayah texts:
 * ayah numbers of every surah and the global -> in-surah number mapping
 * (`AyahEntity.number` is global, `numberInSurah` is the number inside the surah).
 */
internal class QuranIndex private constructor(
    private val metas: Map<Int, SurahMeta>,
    private val localByGlobal: Map<Int, Int>,
) {
    val surahs: List<SurahMeta> = metas.values.sortedBy { it.number }

    val isEmpty: Boolean get() = metas.isEmpty()

    fun meta(surah: Int): SurahMeta? = metas[surah]

    fun ayahCount(surah: Int): Int = metas[surah]?.ayahCount ?: QuranMeta.ayahCount(surah)

    /** Global ayah number -> number inside its surah; the global number itself when it is unknown. */
    fun localAyah(globalAyah: Int): Int = localByGlobal[globalAyah] ?: globalAyah

    /** Number inside [surah] of the global ayah, or null when that ayah belongs to another surah. */
    fun localAyahInSurah(surah: Int, globalAyah: Int): Int? {
        val position = metas[surah]?.globalAyahs?.indexOf(globalAyah) ?: return null
        return if (position >= 0) position + 1 else null
    }

    /** Global ayah numbers of [surah] in reading order. */
    fun globalAyahs(surah: Int): IntArray = metas[surah]?.globalAyahs ?: IntArray(0)

    companion object {
        val Empty = QuranIndex(emptyMap(), emptyMap())

        fun from(completeQuran: List<SurahWithAyahs>): QuranIndex {
            if (completeQuran.isEmpty()) return Empty
            val metas = HashMap<Int, SurahMeta>(completeQuran.size * 2)
            val local = HashMap<Int, Int>(6400)
            for (complete in completeQuran) {
                val ordered = complete.ayahs.sortedBy { it.numberInSurah }
                ordered.forEach { local[it.number] = it.numberInSurah }
                metas[complete.surah.number] = SurahMeta(
                    number = complete.surah.number,
                    arabicName = complete.surah.name,
                    englishName = complete.surah.englishName,
                    englishTranslation = complete.surah.englishNameTranslation,
                    ayahCount = ordered.size,
                    globalAyahs = IntArray(ordered.size) { ordered[it].number },
                )
            }
            return QuranIndex(metas, local)
        }

        /** The standard numbering built from [QuranMeta]. */
        fun standard(): QuranIndex =
            fromCounts((1..QuranMeta.SurahCount).associateWith { QuranMeta.ayahCount(it) })

        /**
         * Numbering from ayah counts per surah, ascending by surah: ayahs of surah N follow those of
         * the previous surah in the map (tests, and a stand-in before the text is loaded).
         */
        fun fromCounts(counts: Map<Int, Int>): QuranIndex {
            val metas = HashMap<Int, SurahMeta>()
            val local = HashMap<Int, Int>(6400)
            var next = 1
            for ((surah, count) in counts.toSortedMap()) {
                val globals = IntArray(count) { next + it }
                globals.forEachIndexed { index, global -> local[global] = index + 1 }
                next += count
                metas[surah] = SurahMeta(surah, "", "", "", count, globals)
            }
            return QuranIndex(metas, local)
        }
    }
}
