package tj.app.quran_todo.data.translation

import kotlinx.serialization.Serializable
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.database.entity.translation.AyahTranslationEntity
import tj.app.quran_todo.data.model.QuranComTranslationText

/** `GET /v1/quran/{edition}` of alquran.cloud: the whole Quran, every surah with its ayahs. */
@Serializable
data class QuranEditionResponse(
    val code: Int = 0,
    val data: QuranEditionData,
)

@Serializable
data class QuranEditionData(
    val surahs: List<QuranEditionSurah>,
)

@Serializable
data class QuranEditionSurah(
    val number: Int,
    val ayahs: List<QuranEditionAyah>,
)

@Serializable
data class QuranEditionAyah(
    /** Global ayah number (1..6236). */
    val number: Int,
    val text: String,
)

/** `GET /api/v1/translation/sura/{key}/{sura}` of quranenc.com: the ayahs of one surah (`sura` and `aya` are strings). */
@Serializable
data class QuranEncResponse(
    val result: List<QuranEncAyah> = emptyList(),
)

@Serializable
data class QuranEncAyah(
    val sura: String = "",
    val aya: String = "",
    val translation: String = "",
)

/** Turns the answers of the two sources for the whole Quran into rows of `ayah_translation`; a wrong shape is rejected, never shifted. */
object WholeTranslation {
    /** The surah (1..114) and the number inside it of the ayah [global] (1..6236); null outside the Quran. */
    fun locate(global: Int): Pair<Int, Int>? {
        if (global !in 1..QuranFacts.AYAH_COUNT) return null
        var low = 1
        var high = QuranFacts.SURAH_COUNT
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (QuranFacts.firstAyahNumber(middle) <= global) low = middle else high = middle - 1
        }
        return low to (global - QuranFacts.firstAyahNumber(low) + 1)
    }

    /** alquran.cloud: 114 surahs with the right number of ayahs each, numbered as in the Quran. */
    fun fromAlquranCloud(response: QuranEditionResponse, key: String): List<AyahTranslationEntity> {
        val surahs = response.data.surahs
        if (surahs.size != QuranFacts.SURAH_COUNT) throw IllegalStateException("${surahs.size} surahs, expected ${QuranFacts.SURAH_COUNT}")
        val rows = ArrayList<AyahTranslationEntity>(QuranFacts.AYAH_COUNT)
        for (surah in surahs) {
            val expected = QuranFacts.ayahCount(surah.number)
            if (expected == 0 || surah.ayahs.size != expected) {
                throw IllegalStateException("surah ${surah.number}: ${surah.ayahs.size} ayahs, expected $expected")
            }
            for (ayah in surah.ayahs) {
                rows += AyahTranslationEntity(ayahNumber = ayah.number, surahNumber = surah.number, languageCode = key, text = ayah.text)
            }
        }
        if (rows.map { it.ayahNumber }.distinct().size != QuranFacts.AYAH_COUNT) throw IllegalStateException("ayah numbers are not 1..6236")
        return rows
    }

    /** quran.com: the 6236 verses in order, without numbers; footnotes and tags are removed. */
    fun fromQuranCom(verses: List<QuranComTranslationText>, key: String): List<AyahTranslationEntity> {
        if (verses.size != QuranFacts.AYAH_COUNT) throw IllegalStateException("${verses.size} verses, expected ${QuranFacts.AYAH_COUNT}")
        return verses.mapIndexed { index, verse ->
            val global = index + 1
            val (surah, _) = locate(global) ?: throw IllegalStateException("ayah $global")
            AyahTranslationEntity(ayahNumber = global, surahNumber = surah, languageCode = key, text = TranslationText.cleanQuranCom(verse.text))
        }
    }

    /** quran.com, one chapter: the verses of [surah] in order. */
    fun fromQuranComChapter(verses: List<QuranComTranslationText>, surah: Int, key: String): List<AyahTranslationEntity> {
        val expected = QuranFacts.ayahCount(surah)
        if (expected == 0 || verses.size != expected) throw IllegalStateException("quran.com $key $surah: ${verses.size} verses, expected $expected")
        val first = QuranFacts.firstAyahNumber(surah)
        return verses.mapIndexed { index, verse ->
            AyahTranslationEntity(ayahNumber = first + index, surahNumber = surah, languageCode = key, text = TranslationText.cleanQuranCom(verse.text))
        }
    }

    /**
     * QuranEnc, one surah: the text goes in unchanged (its terms forbid cleaning it; the footnotes are not shown, never cut). The
     * ayahs must be exactly 1..n of the surah in order.
     */
    fun fromQuranEncSurah(response: QuranEncResponse, surah: Int, key: String): List<AyahTranslationEntity> {
        val expected = QuranFacts.ayahCount(surah)
        val verses = response.result
        if (expected == 0 || verses.size != expected) throw IllegalStateException("quranenc $key $surah: ${verses.size} ayahs, expected $expected")
        val first = QuranFacts.firstAyahNumber(surah)
        return verses.mapIndexed { index, verse ->
            if (verse.aya.toIntOrNull() != index + 1 || verse.sura.toIntOrNull() != surah) {
                throw IllegalStateException("quranenc $key $surah: ayah ${verse.sura}:${verse.aya} at position ${index + 1}")
            }
            AyahTranslationEntity(ayahNumber = first + index, surahNumber = surah, languageCode = key, text = verse.translation)
        }
    }
}
