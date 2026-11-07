package tj.app.quran_todo.ui.common

import tj.app.quran_todo.data.bundled.QuranFacts

/** A juz as a range of ayahs: from [startSurah]:[startAyah] to [endSurah]:[endAyah] inclusive (ayah numbers in the surah). */
internal data class JuzRange(
    val juz: Int,
    val startSurah: Int,
    val startAyah: Int,
    val endSurah: Int,
    val endAyah: Int,
) {
    /** The surahs the juz touches, in mushaf order (the first and the last may be partial). */
    val surahs: IntRange get() = startSurah..endSurah

    /** The ayahs of [surah] that lie in this juz, or null when the surah is outside it. */
    fun ayahsOf(surah: Int): IntRange? {
        if (surah !in surahs) return null
        val first = if (surah == startSurah) startAyah else 1
        val last = if (surah == endSurah) endAyah else QuranFacts.ayahCount(surah)
        return first..last
    }
}

/**
 * The 30 juz of the Madani mushaf (the same boundaries as the `juz` column of the bundled text; `JuzIndexTest` checks it), with
 * their traditional Arabic names (the opening words). Shared by the juz list of Home (D1) and the progress by juz (D2).
 */
internal object JuzIndex {
    const val COUNT = 30

    /** Where each juz starts: surah and ayah in the surah. */
    private val starts: List<Pair<Int, Int>> = listOf(
        1 to 1, 2 to 142, 2 to 253, 3 to 93, 4 to 24, 4 to 148, 5 to 82, 6 to 111, 7 to 88, 8 to 41,
        9 to 93, 11 to 6, 12 to 53, 15 to 1, 17 to 1, 18 to 75, 21 to 1, 23 to 1, 25 to 21, 27 to 56,
        29 to 46, 33 to 31, 36 to 28, 39 to 32, 41 to 47, 46 to 1, 51 to 31, 58 to 1, 67 to 1, 78 to 1,
    )

    /** The traditional name of each juz: its opening words, in Arabic (not translated). */
    private val arabicNames: List<String> = listOf(
        "الم", "سيقول", "تلك الرسل", "لن تنالوا", "والمحصنات", "لا يحب الله", "وإذا سمعوا", "ولو أننا", "قال الملأ", "واعلموا",
        "يعتذرون", "وما من دابة", "وما أبرئ", "ربما", "سبحان الذي", "قال ألم", "اقترب للناس", "قد أفلح", "وقال الذين", "أمن خلق",
        "اتل ما أوحي", "ومن يقنت", "وما لي", "فمن أظلم", "إليه يرد", "حم", "قال فما خطبكم", "قد سمع الله", "تبارك الذي", "عم",
    )

    val all: List<JuzRange> = (1..COUNT).map(::range)

    /** The range of juz [juz] (1..30). */
    fun range(juz: Int): JuzRange {
        require(juz in 1..COUNT) { "juz $juz" }
        val (startSurah, startAyah) = starts[juz - 1]
        val (endSurah, endAyah) = if (juz == COUNT) {
            114 to QuranFacts.ayahCount(114)
        } else {
            val (nextSurah, nextAyah) = starts[juz]
            if (nextAyah == 1) (nextSurah - 1) to QuranFacts.ayahCount(nextSurah - 1) else nextSurah to (nextAyah - 1)
        }
        return JuzRange(juz, startSurah, startAyah, endSurah, endAyah)
    }

    /** The juz that holds [surah]:[ayah] (ayah number in the surah). */
    fun juzOf(surah: Int, ayah: Int): Int {
        var juz = 1
        for (index in starts.indices) {
            val (s, a) = starts[index]
            if (surah > s || (surah == s && ayah >= a)) juz = index + 1 else break
        }
        return juz
    }

    /** The number of ayahs in juz [juz]. */
    fun ayahCount(juz: Int): Int = range(juz).let { r -> r.surahs.sumOf { s -> r.ayahsOf(s)!!.count() } }

    /** How many ayahs of juz [juz] are covered, from `HifzRepository.coveredAyahs()` (surah -> covered ayah numbers). */
    fun coveredCount(juz: Int, covered: Map<Int, Set<Int>>): Int {
        val r = range(juz)
        return r.surahs.sumOf { surah ->
            val ayahs = covered[surah] ?: return@sumOf 0
            val inJuz = r.ayahsOf(surah)!!
            if (inJuz.first == 1 && inJuz.last == QuranFacts.ayahCount(surah)) ayahs.size else ayahs.count { it in inJuz }
        }
    }

    /** The traditional Arabic name of juz [juz] ("عم" for the 30th). */
    fun arabicName(juz: Int): String = arabicNames[juz - 1]
}
