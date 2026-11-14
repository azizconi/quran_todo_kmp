package tj.app.quran_todo.ui.reader

import androidx.compose.runtime.Immutable
import tj.app.quran_todo.data.database.entity.quran.AyahEntity
import tj.app.quran_todo.data.database.entity.quran.Sajda
import tj.app.quran_todo.data.database.entity.quran.SurahWithAyahs

/** Where an ayah sits in the mushaf: its juz (1..30), hizb (1..60) and page (1..604). */
@Immutable
data class MushafPlace(val juz: Int, val hizb: Int, val page: Int)

/**
 * What the reader marks at one ayah: the juz, the quarter of a hizb or the page that START here (each null when the
 * ayah continues the one before it), and the sajda sign. The marks are worked out once when a surah is loaded
 * ([MushafMarks.build]), so no row calculates anything while it scrolls.
 */
@Immutable
data class AyahMarks(
    /** The juz that starts at this ayah (1..30). */
    val juz: Int? = null,
    /** The quarter of a hizb that starts here, counted over the whole Quran (1..240); see [MushafMarks.hizbOf]. */
    val hizbQuarter: Int? = null,
    /** The page of the mushaf that starts here (1..604). */
    val page: Int? = null,
    val sajda: Sajda? = null,
) {
    /** Something to say in the quiet line above the ayah (the sajda has its own sign next to the number). */
    val hasLine: Boolean get() = juz != null || hizbQuarter != null || page != null

    companion object {
        val None = AyahMarks()
    }
}

/** The juz, hizb and page of the bundled text (`AyahEntity.juz`, `hizbQuarter`, `page`), as the reader marks them. */
object MushafMarks {
    /** The hizb (1..60) of a quarter counted over the whole Quran (1..240): quarters 1..4 are hizb 1, 5..8 hizb 2, ... */
    fun hizbOf(hizbQuarter: Int): Int = (hizbQuarter - 1) / 4 + 1

    /** Which quarter of its hizb [hizbQuarter] is: 0 where the hizb starts, then 1 (a quarter), 2 (a half), 3 (three quarters). */
    fun quarterOf(hizbQuarter: Int): Int = (hizbQuarter - 1) % 4

    /** The place of an ayah, for the line "Juz 2 · Hizb 3 · p. 22" in the top bar. */
    fun placeOf(ayah: AyahEntity): MushafPlace = MushafPlace(ayah.juz, hizbOf(ayah.hizbQuarter), ayah.page)

    /**
     * The marks of surah [surahNumber] whose ayahs are [ayahs], with the previous surah looked up in [quran] (the whole Quran
     * as the home screen holds it). Should the previous surah be missing, the first ayah is taken to continue it: nothing is
     * marked there rather than a juz that does not start.
     */
    fun marksOfSurah(surahNumber: Int, ayahs: List<AyahEntity>, quran: List<SurahWithAyahs>): List<AyahMarks> {
        val previous = when {
            surahNumber <= 1 -> null
            else -> quran.firstOrNull { it.surah.number == surahNumber - 1 }?.ayahs?.maxByOrNull { it.numberInSurah }
                ?: ayahs.firstOrNull()
        }
        return build(ayahs, previous)
    }

    /**
     * The marks of every ayah of one surah, in the order of [ayahs] (sorted by number in the surah). An ayah is marked
     * where its juz, quarter or page differs from the ayah before it; for the first ayah that is [previous], the last
     * ayah of the previous surah (null for Al-Fatiha: the Quran starts there, so juz 1, hizb 1 and page 1 start at it).
     */
    fun build(ayahs: List<AyahEntity>, previous: AyahEntity?): List<AyahMarks> {
        var before = previous
        return ayahs.map { ayah ->
            val last = before
            before = ayah
            AyahMarks(
                juz = ayah.juz.takeIf { last == null || last.juz != it },
                hizbQuarter = ayah.hizbQuarter.takeIf { last == null || last.hizbQuarter != it },
                page = ayah.page.takeIf { last == null || last.page != it },
                sajda = ayah.sajda,
            )
        }
    }
}
