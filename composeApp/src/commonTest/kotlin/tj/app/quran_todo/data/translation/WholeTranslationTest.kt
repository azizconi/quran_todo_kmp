package tj.app.quran_todo.data.translation

import kotlinx.serialization.json.Json
import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.data.model.QuranComTranslationsResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The answers of the two sources for the whole Quran, as fixtures with the real shape (cut to short texts). */
class WholeTranslationTest {
    private val json = Json { ignoreUnknownKeys = true }

    /** What `GET /v1/quran/{edition}` of alquran.cloud answers: surahs with their ayahs (with all the fields the real one has). */
    private fun alquranWhole(surahCount: Int = 114, drop: Pair<Int, Int>? = null): String {
        val surahs = (1..surahCount).joinToString(",") { surah ->
            val count = QuranFacts.ayahCount(surah) - if (drop?.first == surah) drop.second else 0
            val ayahs = (1..count).joinToString(",") { n ->
                val global = QuranFacts.firstAyahNumber(surah) + n - 1
                """{"number":$global,"text":"s$surah a$n","numberInSurah":$n,"juz":1,"manzil":1,"page":1,"ruku":1,"hizbQuarter":1,"sajda":false}"""
            }
            """{"number":$surah,"name":"x","englishName":"x","englishNameTranslation":"x","revelationType":"Meccan","numberOfAyahs":$count,"ayahs":[$ayahs]}"""
        }
        return """{"code":200,"status":"OK","data":{"surahs":[$surahs],"edition":{"identifier":"ru.abuadel","language":"ru","name":"x"}}}"""
    }

    /** What `GET /quran/translations/{id}` of quran.com answers: the verses in order, with footnote markers. */
    private fun quranComWhole(count: Int = QuranFacts.AYAH_COUNT): String {
        val verses = (1..count).joinToString(",") { """{"resource_id":139,"text":"verse $it<sup foot_note=$it>1</sup>"}""" }
        return """{"translations":[$verses],"meta":{"translation_name":"x","author_name":"y"}}"""
    }

    @Test
    fun alquranCloudAnswersBecomeTheRowsOfOneEdition() {
        val response = json.decodeFromString<QuranEditionResponse>(alquranWhole())
        val rows = WholeTranslation.fromAlquranCloud(response, "ru.abuadel")

        assertEquals(QuranFacts.AYAH_COUNT, rows.size)
        assertTrue(rows.all { it.languageCode == "ru.abuadel" })
        assertEquals((1..QuranFacts.AYAH_COUNT).toList(), rows.map { it.ayahNumber }.sorted())
        // Every row sits in the right surah.
        rows.forEach { assertEquals(WholeTranslation.locate(it.ayahNumber)?.first, it.surahNumber) }
        assertEquals("s2 a255", rows.first { it.ayahNumber == 262 }.text)
    }

    @Test
    fun anAlquranCloudAnswerWithMissingSurahsOrAyahsIsRejected() {
        assertFailsWith<IllegalStateException> {
            WholeTranslation.fromAlquranCloud(json.decodeFromString<QuranEditionResponse>(alquranWhole(surahCount = 113)), "ru")
        }
        assertFailsWith<IllegalStateException> {
            // Al-Baqarah with one ayah missing.
            WholeTranslation.fromAlquranCloud(json.decodeFromString<QuranEditionResponse>(alquranWhole(drop = 2 to 1)), "ru")
        }
    }

    @Test
    fun quranComVersesAreNumberedByPositionAndCleaned() {
        val response = json.decodeFromString<QuranComTranslationsResponse>(quranComWhole())
        val rows = WholeTranslation.fromQuranCom(response.translations, "qc:139")

        assertEquals(QuranFacts.AYAH_COUNT, rows.size)
        assertTrue(rows.all { it.languageCode == "qc:139" })
        assertEquals("verse 1", rows.first().text, "the footnote marker is gone")
        assertTrue(rows.none { '<' in it.text || "foot_note" in it.text })
        // 2:255 is the verse number 262.
        val ayatAlKursi = rows.first { it.ayahNumber == 262 }
        assertEquals(2, ayatAlKursi.surahNumber)
        assertEquals("verse 262", ayatAlKursi.text)
        assertEquals(114, rows.last().surahNumber)
    }

    @Test
    fun quranComAnswerOfTheWrongLengthIsRejectedNotShifted() {
        val short = json.decodeFromString<QuranComTranslationsResponse>(quranComWhole(count = 6235))
        assertFailsWith<IllegalStateException> { WholeTranslation.fromQuranCom(short.translations, "qc:139") }
        val long = json.decodeFromString<QuranComTranslationsResponse>(quranComWhole(count = 6237))
        assertFailsWith<IllegalStateException> { WholeTranslation.fromQuranCom(long.translations, "qc:139") }
    }

    @Test
    fun aQuranComChapterIsNumberedFromTheFirstAyahOfItsSurah() {
        val verses = (1..4).map { tj.app.quran_todo.data.model.QuranComTranslationText("v$it") }
        val rows = WholeTranslation.fromQuranComChapter(verses, 112, "qc:210")
        assertEquals(listOf(6222, 6223, 6224, 6225), rows.map { it.ayahNumber })
        assertTrue(rows.all { it.surahNumber == 112 && it.languageCode == "qc:210" })
        assertFailsWith<IllegalStateException> { WholeTranslation.fromQuranComChapter(verses.take(3), 112, "qc:210") }
    }

    private fun quranEnc(surah: Int, count: Int = QuranFacts.ayahCount(surah), text: (Int) -> String = { "аят $it" }): String {
        val rows = (1..count).joinToString(",") {
            """{"id":"${QuranFacts.firstAyahNumber(surah) + it - 1}","sura":"$surah","aya":"$it","arabic_text":"x","translation":"${text(it)}","footnotes":""}"""
        }
        return """{"result":[$rows]}"""
    }

    @Test
    fun quranEncSurahIsStoredUnchanged() {
        val response = json.decodeFromString<QuranEncResponse>(quranEnc(112) { "  [Эй Паёмбар] <b>Бигӯ</b> &amp; $it  " })
        val rows = WholeTranslation.fromQuranEncSurah(response, 112, "qe:tajik_arifi")
        assertEquals(listOf(6222, 6223, 6224, 6225), rows.map { it.ayahNumber })
        assertTrue(rows.all { it.surahNumber == 112 && it.languageCode == "qe:tajik_arifi" })
        // The terms of QuranEnc: the text is not cleaned, not trimmed, not transliterated.
        assertEquals("  [Эй Паёмбар] <b>Бигӯ</b> &amp; 1  ", rows.first().text)
    }

    @Test
    fun quranEncSurahWithTheWrongShapeIsRejected() {
        assertFailsWith<IllegalStateException> { WholeTranslation.fromQuranEncSurah(json.decodeFromString(quranEnc(112, count = 3)), 112, "qe:x") }
        assertFailsWith<IllegalStateException> { WholeTranslation.fromQuranEncSurah(json.decodeFromString(quranEnc(113)), 112, "qe:x") }
        assertFailsWith<IllegalStateException> { WholeTranslation.fromQuranEncSurah(json.decodeFromString("{\"result\":[]}"), 112, "qe:x") }
    }
}
