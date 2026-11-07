package tj.app.quran_todo.ui.i18n

import tj.app.quran_todo.common.i18n.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranslationCopyTest {
    private fun TranslationCopy.strings(): Map<String, String> =
        TranslationCopy::class.java.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .associate { it.name to (it.get(this) as String) }

    @Test
    fun everyStringIsFilledInAllFiveLanguages() {
        AppLanguage.entries.forEach { language ->
            val strings = translationCopy(language).strings()
            assertTrue(strings.size >= 25, "copy of $language looks incomplete: ${strings.keys}")
            strings.forEach { (name, value) -> assertTrue(value.isNotBlank(), "$language.$name is blank") }
        }
    }

    @Test
    fun theLanguagesDifferFromEnglish() {
        val english = translationCopy(AppLanguage.EN).strings()
        listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR).forEach { language ->
            val other = translationCopy(language).strings()
            val same = english.filter { (name, value) -> other[name] == value }
            assertTrue(same.size <= 1, "$language shares too much with English: ${same.keys}")
        }
    }

    @Test
    fun everyPatternTakesItsValueAndLeavesNoPlaceholder() {
        AppLanguage.entries.forEach { language ->
            val copy = translationCopy(language)
            val filled = listOf(
                copy.row("NAME") to "NAME",
                copy.deleteBody("NAME") to "NAME",
                copy.offer("0.4 MB") to "0.4 MB",
                copy.searchIn("NAME") to "NAME",
                copy.needDownload("0.4 MB") to "0.4 MB",
                copy.truncatedLabel(200) to "200",
            )
            filled.forEach { (text, value) ->
                assertTrue(value in text, "$language: $text lacks $value")
                assertFalse('{' in text || '}' in text, "$language leaves a placeholder: $text")
            }
        }
    }

    @Test
    fun theTextsOfTheBriefAreThere() {
        val ru = translationCopy(AppLanguage.RU)
        assertEquals("Перевод: Кулиев", ru.row("Кулиев"))
        assertEquals("Другие языки", ru.otherLanguages)
        assertEquals("Переводы", ru.downloadsSection)
        assertEquals("Скачать целиком · ≈ 440 КБ", ru.offer("440 КБ"))
        assertEquals("Чтобы искать по переводу, скачайте его целиком · ≈ 440 КБ", ru.needDownload("440 КБ"))
        assertEquals("В переводе", ru.searchTab)
        assertEquals("Ничего не нашлось", ru.noResultsTitle)
        assertEquals("Показаны первые 200", ru.truncatedLabel(200))
        assertEquals("Translation: Saheeh International", translationCopy(AppLanguage.EN).row("Saheeh International"))
    }

    @Test
    fun noStarSignIsUsed() {
        AppLanguage.entries.forEach { language ->
            translationCopy(language).strings().values.forEach { value ->
                assertTrue(value.none { it in "★☆✦✧✩✪✫✬✭✮✯⭐✡" }, "$language: $value")
            }
        }
    }
}
