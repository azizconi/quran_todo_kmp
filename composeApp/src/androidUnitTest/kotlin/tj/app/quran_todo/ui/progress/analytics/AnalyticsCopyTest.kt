package tj.app.quran_todo.ui.progress.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.i18n.AnalyticsCopy
import tj.app.quran_todo.ui.i18n.analyticsCopy

class AnalyticsCopyTest {
    private val fields = AnalyticsCopy::class.java.declaredFields
        .filter { it.type == String::class.java }
        .onEach { it.isAccessible = true }

    @Test
    fun everyLanguageFillsEveryString() {
        assertTrue(fields.size > 50)
        for (language in AppLanguage.entries) {
            val copy = analyticsCopy(language)
            assertEquals(language, copy.language)
            for (field in fields) {
                assertTrue((field.get(copy) as String).isNotBlank(), "${language.name}.${field.name} is blank")
            }
        }
    }

    @Test
    fun otherLanguagesAreTranslatedNotCopiedFromEnglish() {
        val english = analyticsCopy(AppLanguage.EN)
        // A pattern that is only placeholders and punctuation, or a technical word, may be the same everywhere.
        val shared = setOf("formatPdf", "bubbleFmt")
        for (language in listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR)) {
            val copy = analyticsCopy(language)
            val same = fields.filter { it.name !in shared && it.get(copy) == it.get(english) }.map { it.name }
            assertTrue(same.size <= 2, "${language.name} repeats English in $same")
        }
    }

    @Test
    fun placeholdersAreFilledInEveryLanguage() {
        for (language in AppLanguage.entries) {
            val copy = analyticsCopy(language)
            val texts = listOf(
                copy.activeDays(12),
                copy.forecastLeft(7),
                copy.forecastDate("12 Mar"),
                copy.hardPlace("Name", 3),
                copy.hardMisses(4),
                copy.notesAyah("Name", 5),
                copy.bubble("12 Mar", 1, 3, 2, false),
                copy.bubble("12 Mar", 0, 0, 0, true),
                copy.bubble("12 Mar", 0, 0, 0, false),
            )
            for (text in texts) assertFalse(text.contains("{") || text.contains("}"), "${language.name}: unfilled placeholder in \"$text\"")
        }
    }

    @Test
    fun bubbleNamesOnlyWhatHappened() {
        val en = analyticsCopy(AppLanguage.EN)
        assertEquals("12 Mar: new 1, reviewed 3, listened 2", en.bubble("12 Mar", 1, 3, 2, false))
        assertEquals("12 Mar: reviewed 3", en.bubble("12 Mar", 0, 3, 0, false))
        assertEquals("12 Mar: plan pause", en.bubble("12 Mar", 0, 0, 0, true))
        assertEquals("12 Mar: no studying", en.bubble("12 Mar", 0, 0, 0, false))
        // The listened count alone is still something that happened that day.
        assertEquals("12 Mar: listened 2", en.bubble("12 Mar", 0, 0, 2, true))
        val ru = analyticsCopy(AppLanguage.RU)
        assertEquals("12 мар: новое 1, повторено 3, прослушано 2", ru.bubble("12 мар", 1, 3, 2, false))
    }
}
