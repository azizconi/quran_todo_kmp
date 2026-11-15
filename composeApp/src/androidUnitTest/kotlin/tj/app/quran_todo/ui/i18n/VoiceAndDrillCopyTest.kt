package tj.app.quran_todo.ui.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tj.app.quran_todo.common.i18n.AppLanguage

/** The strings of the drill of the difficult ayahs (task F2a): all five languages, filled, without stars. */
class VoiceAndDrillCopyTest {
    private val stars = listOf('★', '☆', '✦', '✧', '✩', '✪', '⭐', '۞', '✶', '✷')

    private fun strings(copy: Any): Map<String, String> =
        copy.javaClass.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .associate { it.name to it.get(copy) as String }

    @Test
    fun everyDrillStringOfEveryLanguageIsWrittenAndHasNoStar() {
        AppLanguage.entries.forEach { language ->
            val all = strings(drillCopy(language))
            assertTrue(all.size >= 15, "${language.name}: ${all.size} strings")
            all.forEach { (name, value) ->
                assertTrue(value.isNotBlank(), "${language.name}.$name is blank")
                assertTrue(stars.none { it in value }, "${language.name}.$name has a star: $value")
            }
        }
    }

    @Test
    fun theOtherLanguagesAreTranslated() {
        listOf(::drillCopy).forEach { build ->
            val english = strings(build(AppLanguage.EN))
            listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR).forEach { language ->
                val other = strings(build(language))
                val same = english.filter { (name, value) -> other[name] == value }
                assertTrue(same.size < 3, "$language shares too much with English: ${same.keys}")
            }
        }
    }

    @Test
    fun thePatternsFillTheirNumbers() {
        AppLanguage.entries.forEach { language ->
            val drill = drillCopy(language)
            val filled = listOf(
                drill.progress(2, 10), drill.ayah(5), drill.summary(7, 3), drill.more(4), drill.train(5),
            )
            filled.forEach { text ->
                assertFalse("{" in text || "}" in text, "${language.name}: $text")
                assertTrue(text.any { it.isDigit() }, "${language.name}: $text has no number")
            }
        }
        val ru = drillCopy(AppLanguage.RU)
        assertEquals("2 из 10", ru.progress(2, 10))
        assertEquals("Помню: 7 · Ещё трудные: 3", ru.summary(7, 3))
        assertEquals("Ещё 4 в следующий раз", ru.more(4))
        assertEquals("Тренировать трудные · 5", ru.train(5))
    }
}
