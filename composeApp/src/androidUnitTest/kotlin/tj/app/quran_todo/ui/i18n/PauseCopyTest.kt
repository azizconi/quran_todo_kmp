package tj.app.quran_todo.ui.i18n

import tj.app.quran_todo.common.i18n.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The strings of the pause of the plan (task F2b) exist in the five languages, have no star and fill their patterns. */
class PauseCopyTest {
    private val stars = listOf('★', '☆', '✦', '✧', '✩', '✪', '⭐', '۞', '✶', '✷')

    private fun strings(copy: PauseCopy): Map<String, String> =
        PauseCopy::class.java.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .associate { it.name to it.get(copy) as String }

    @Test
    fun everyStringOfEveryLanguageIsWrittenAndHasNoStar() {
        AppLanguage.entries.forEach { language ->
            val all = strings(pauseCopy(language))
            assertTrue(all.size >= 24, "${language.name}: ${all.size} strings")
            all.forEach { (name, value) ->
                assertTrue(value.isNotBlank(), "${language.name}.$name is blank")
                assertTrue(stars.none { it in value }, "${language.name}.$name has a star: $value")
                assertFalse('!' in value, "${language.name}.$name has an exclamation mark: $value")
            }
        }
    }

    @Test
    fun theFiveLanguagesDoNotShareTheirTexts() {
        val byLanguage = AppLanguage.entries.associateWith { strings(pauseCopy(it)) }
        listOf("rowTitle", "resumeNow", "sheetTitle", "confirmPause", "resumeBody", "cardTitlePattern").forEach { key ->
            val values = byLanguage.values.map { it.getValue(key) }
            assertEquals(values.size, values.toSet().size, "$key is the same in two languages: $values")
        }
    }

    @Test
    fun thePatternsFillAllTheirPlaceholders() {
        AppLanguage.entries.forEach { language ->
            val copy = pauseCopy(language)
            val filled = listOf(
                copy.rowActive("11 Nov"), copy.info("12 Nov", 7), copy.extendInfo("12 Nov", 3), copy.cardTitle("11 Nov"),
                copy.cardBody("12 Nov"), copy.lessonBody("12 Nov"), copy.pausedToast("11 Nov"),
            )
            filled.forEach { text ->
                assertFalse("{" in text || "}" in text, "${language.name}: $text")
                assertTrue("Nov" in text, "${language.name}: $text has no date")
            }
            assertTrue("7" in copy.info("12 Nov", 7), "${language.name}: ${copy.info("12 Nov", 7)}")
        }
    }

    @Test
    fun theLineOfTheSheetReadsAsItShould() {
        assertEquals(
            "Уроки вернутся 12 ноября. Повторения сдвинутся на 7 дней, серия сохранится.",
            pauseCopy(AppLanguage.RU).info("12 ноября", 7),
        )
        assertEquals(
            "Уроки вернутся 12 ноября. Повторения сдвинутся на 1 день, серия сохранится.",
            pauseCopy(AppLanguage.RU).info("12 ноября", 1),
        )
        assertEquals(
            "Уроки вернутся 12 ноября. Повторения сдвинутся ещё на 3 дня, серия сохранится.",
            pauseCopy(AppLanguage.RU).extendInfo("12 ноября", 3),
        )
        assertEquals("На паузе до 11 ноября", pauseCopy(AppLanguage.RU).rowActive("11 ноября"))
        assertEquals("План на паузе до 11 ноября", pauseCopy(AppLanguage.RU).cardTitle("11 ноября"))
        assertEquals(
            "Lessons return on 12 November. Reviews will move by 7 days; your streak is kept.",
            pauseCopy(AppLanguage.EN).info("12 November", 7),
        )
        assertEquals("7 kunga", pauseCopy(AppLanguage.UZ).days(7) + "ga")
    }
}
