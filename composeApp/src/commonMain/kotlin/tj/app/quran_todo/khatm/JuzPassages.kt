package tj.app.quran_todo.khatm

import tj.app.quran_todo.common.mushaf.MushafPages
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.hifz.model.AyahRange

/**
 * A juz as passages by surah (task F5b, "Juz of the night"): the ayahs from the first ayah of its first page to the ayah before the first page
 * of the next juz, one [AyahRange] for each surah it touches. The thirty juz laid one after another cover all 6236 ayahs once.
 */
object JuzPassages {
    fun of(juz: Int): List<AyahRange> {
        val number = juz.coerceIn(1, 30)
        val start = MushafPages.firstAyahOf(MushafPages.pagesOfJuz(number).first)
        val end = if (number < 30) MushafPages.firstAyahOf(MushafPages.pagesOfJuz(number + 1).first) else null
        val lastSurah = end?.surah ?: QuranFacts.SURAH_COUNT
        return buildList {
            for (surah in start.surah..lastSurah) {
                val from = if (surah == start.surah) start.ayah else 1
                val to = if (end != null && surah == end.surah) end.ayah - 1 else QuranFacts.ayahCount(surah)
                if (to >= from) add(AyahRange(surah, from, to))
            }
        }
    }
}
