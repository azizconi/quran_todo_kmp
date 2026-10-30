package tj.app.quran_todo.ui.i18n

import tj.app.quran_todo.common.i18n.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The strings of the echo mode and of "Listen to the old" (task F1b) exist in the five languages and fill their patterns. */
class ListenCopyTest {
    private val stars = listOf('★', '☆', '✦', '✧', '✩', '✪', '⭐', '۞', '✶', '✷')

    private fun strings(copy: ListenCopy): Map<String, String> =
        ListenCopy::class.java.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .associate { it.name to it.get(copy) as String }

    @Test
    fun everyStringOfEveryLanguageIsWrittenAndHasNoStar() {
        AppLanguage.entries.forEach { language ->
            val all = strings(listenCopy(language))
            assertTrue(all.size >= 30, "${language.name}: ${all.size} strings")
            all.forEach { (name, value) ->
                assertTrue(value.isNotBlank(), "${language.name}.$name is blank")
                assertTrue(stars.none { it in value }, "${language.name}.$name has a star: $value")
            }
        }
    }

    @Test
    fun theFiveLanguagesDoNotShareTheirTexts() {
        val byLanguage = AppLanguage.entries.associateWith { strings(listenCopy(it)) }
        listOf("echoSwitch", "oldTitle", "markHeard", "notRecallNote", "listenedBadge").forEach { key ->
            val values = byLanguage.values.map { it.getValue(key) }
            assertEquals(values.size, values.toSet().size, "$key is the same in two languages: $values")
        }
    }

    @Test
    fun thePatternsFillAllTheirPlaceholders() {
        AppLanguage.entries.forEach { language ->
            val copy = listenCopy(language)
            val filled = listOf(
                copy.oldButton(7), copy.oldPlaying(2, 5), copy.oldSubtitle(3, 9), copy.passageOf(2, 5), copy.heardCount(2),
                copy.marked(4), copy.dockQueue(2, 5), copy.progressListened(3), copy.progressListenedMonth(11), copy.minutes(3),
            )
            filled.forEach { text ->
                assertFalse("{" in text || "}" in text, "${language.name}: $text")
                assertTrue(text.any { it.isDigit() }, "${language.name}: $text has no number")
            }
        }
    }

    @Test
    fun theButtonOfTheCardAndTheLineOfTheProgressScreenReadAsTheyShould() {
        assertEquals("Слушать старое · ~10 мин", listenCopy(AppLanguage.RU).oldButton(10))
        assertEquals("Listen to the old · ~10 min", listenCopy(AppLanguage.EN).oldButton(10))
        assertEquals("Прослушано: 1 отрывок", listenCopy(AppLanguage.RU).progressListened(1))
        assertEquals("Прослушано: 3 отрывка", listenCopy(AppLanguage.RU).progressListened(3))
        assertEquals("Прослушано: 5 отрывков", listenCopy(AppLanguage.RU).progressListened(5))
        assertEquals("Listened: 2 passages", listenCopy(AppLanguage.EN).progressListened(2))
        assertEquals("Прослушано ✓", listenCopy(AppLanguage.RU).listenedBadge)
    }

    @Test
    fun theWordsOfTheTurnAreTheOnesOfTheReader() {
        AppLanguage.entries.forEach { language ->
            val reader = readerCopy(language)
            assertEquals(reader.yourTurn, listenCopy(language).yourTurn)
            assertEquals(reader.secondsShort, listenCopy(language).secondsShort)
        }
    }
}
