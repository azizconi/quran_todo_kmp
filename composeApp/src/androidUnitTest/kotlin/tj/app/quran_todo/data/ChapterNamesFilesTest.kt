package tj.app.quran_todo.data

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.data.bundled.BundledChapterNames
import tj.app.quran_todo.data.bundled.QuranDataException
import tj.app.quran_todo.domain.model.ChapterNameModel
import tj.app.quran_todo.ui.common.SurahNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChapterNamesFilesTest {
    private fun names(language: AppLanguage): Map<Int, ChapterNameModel> =
        BundledChapterNames.parse(BundledFiles.file("chapter_names_${language.name.lowercase()}.json").readText())

    private fun String.isCyrillic() = any { it.code in 0x0400..0x04FF }
    private fun String.hasLatin() = any { it in 'a'..'z' || it in 'A'..'Z' }

    @Test
    fun bothFilesNameAll114Surahs() {
        listOf(AppLanguage.TG, AppLanguage.UZ).forEach { language ->
            val names = names(language)
            assertEquals((1..114).toList(), names.keys.sorted(), "surahs of $language")
            names.forEach { (number, name) ->
                assertTrue(name.transliteration.isNotBlank(), "$language $number transliteration")
                assertTrue(name.translated.isNotBlank(), "$language $number meaning")
                assertTrue(name.arabic.isNotBlank(), "$language $number arabic")
                assertEquals(name.transliteration.trim(), name.transliteration)
                assertEquals(name.translated.trim(), name.translated)
            }
        }
    }

    @Test
    fun tajikIsCyrillicAndUzbekIsLatin() {
        names(AppLanguage.TG).forEach { (number, name) ->
            assertTrue(name.transliteration.isCyrillic() && !name.transliteration.hasLatin(), "tg name $number: ${name.transliteration}")
            assertTrue(name.translated.isCyrillic() && !name.translated.hasLatin(), "tg meaning $number: ${name.translated}")
        }
        names(AppLanguage.UZ).forEach { (number, name) ->
            assertTrue(name.transliteration.hasLatin() && !name.transliteration.isCyrillic(), "uz name $number: ${name.transliteration}")
            assertTrue(name.translated.hasLatin() && !name.translated.isCyrillic(), "uz meaning $number: ${name.translated}")
        }
    }

    @Test
    fun thePlansExamplesAreThere() {
        assertEquals("Фотиҳа" to "Кушоянда", names(AppLanguage.TG).getValue(1).let { it.transliteration to it.translated })
        assertEquals("Fotiha" to "Ochuvchi", names(AppLanguage.UZ).getValue(1).let { it.transliteration to it.translated })
    }

    @Test
    fun theArabicNamesAreTheOnesOfQuranCom() {
        val tg = names(AppLanguage.TG)
        val uz = names(AppLanguage.UZ)
        assertEquals("الفاتحة", tg.getValue(1).arabic)
        assertEquals("الناس", tg.getValue(114).arabic)
        assertEquals(tg.mapValues { it.value.arabic }, uz.mapValues { it.value.arabic })
    }

    @Test
    fun aFileThatDoesNotCoverTheWholeQuranIsRejected() {
        assertFailsWith<QuranDataException> {
            BundledChapterNames.parse("""{"language":"tg","chapters":[{"number":1,"arabic":"a","transliteration":"b","translated":"c"}]}""")
        }
    }

    // -- The Android UI shows them as they are (list row, reader header, mini player all use SurahNames) -----------------

    @Test
    fun surahNamesShowsTheBundledNamesForTajikAndUzbek() {
        val tg = names(AppLanguage.TG).getValue(1)
        assertEquals(
            "Фотиҳа",
            SurahNames.displayName(AppLanguage.TG, russianName = "аль-Фатиха", transliteration = tg.transliteration, fallback = "Al-Faatiha"),
        )
        val uz = names(AppLanguage.UZ).getValue(1)
        assertEquals(
            "Fotiha",
            SurahNames.displayName(AppLanguage.UZ, russianName = "аль-Фатиха", transliteration = uz.transliteration, fallback = "Al-Faatiha"),
        )
        // Russian still uses its own bundled names, English the quran.com transliteration.
        assertEquals("Аль-Фатиха", SurahNames.displayName(AppLanguage.RU, "аль-Фатиха", "Al-Fatihah", "Al-Faatiha"))
        assertEquals("Al-Fatihah", SurahNames.displayName(AppLanguage.EN, "аль-Фатиха", "Al-Fatihah", "Al-Faatiha"))
        // Until the names are loaded the English name is the fallback.
        assertEquals("Al-Faatiha", SurahNames.displayName(AppLanguage.TG, "аль-Фатиха", null, "Al-Faatiha"))
    }
}
