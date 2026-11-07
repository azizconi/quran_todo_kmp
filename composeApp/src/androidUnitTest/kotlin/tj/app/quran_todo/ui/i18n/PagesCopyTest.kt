package tj.app.quran_todo.ui.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tj.app.quran_todo.common.i18n.AppLanguage

class PagesCopyTest {
    private fun PagesCopy.strings(): Map<String, String> =
        PagesCopy::class.java.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .associate { it.name to (it.get(this) as String) }

    @Test
    fun everyStringIsFilledInAllFiveLanguages() {
        AppLanguage.entries.forEach { language ->
            val strings = pagesCopy(language).strings()
            assertTrue(strings.size >= 10, "copy of $language looks incomplete: ${strings.keys}")
            strings.forEach { (name, value) -> assertTrue(value.isNotBlank(), "$language.$name is blank") }
        }
    }

    @Test
    fun theLanguagesDifferFromEnglish() {
        val english = pagesCopy(AppLanguage.EN).strings()
        listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR).forEach { language ->
            val other = pagesCopy(language).strings()
            val same = english.filter { (name, value) -> other[name] == value }
            assertTrue(same.size <= 1, "$language shares too much with English: ${same.keys}")
        }
    }

    @Test
    fun everyPatternTakesItsNumberAndLeavesNoPlaceholder() {
        AppLanguage.entries.forEach { language ->
            val copy = pagesCopy(language)
            listOf(copy.pageOfPattern, copy.downloadsJuzTitle, copy.downloadsPagesCount, copy.downloadsFailedPages)
                .forEach { assertTrue("{n}" in it, "$language pattern without a number: $it") }
            val texts = listOf(copy.pageOf(604), copy.juzRowTitle(30), copy.pagesCount(21), copy.failedPages(3))
            texts.forEach { assertFalse('{' in it || '}' in it, "$language leaves a placeholder: $it") }
            assertTrue("604" in texts[0] && "30" in texts[1] && "21" in texts[2] && "3" in texts[3], "$language: $texts")
            assertEquals("", copy.failedPages(0), "nothing failed, nothing is said")
        }
    }

    @Test
    fun theTextsOfTheBriefAreThere() {
        assertEquals("Страница не загружена. Нужен интернет один раз", pagesCopy(AppLanguage.RU).notLoaded)
        assertEquals("Страницы мусхафа", pagesCopy(AppLanguage.RU).downloadsSection)
        assertEquals("Страницы джуза 30", pagesCopy(AppLanguage.RU).juzRowTitle(30))
        assertEquals("Все 604 страницы", pagesCopy(AppLanguage.RU).downloadsAllTitle)
        assertEquals("Mushaf pages", pagesCopy(AppLanguage.EN).downloadsSection)
    }

    @Test
    fun theNumbersOfTheJuzRowFollowTheWordOrderOfTheLanguage() {
        assertEquals("30-juz sahifalari", pagesCopy(AppLanguage.UZ).juzRowTitle(30))
        assertEquals("30. cüzün sayfaları", pagesCopy(AppLanguage.TR).juzRowTitle(30))
        assertEquals("Саҳифаҳои ҷузъи 30", pagesCopy(AppLanguage.TG).juzRowTitle(30))
    }
}
