package tj.app.quran_todo.ui.lesson

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.i18n.LessonCopy
import tj.app.quran_todo.ui.i18n.lessonCopy

class LessonCopyTest {
    private fun LessonCopy.strings(): Map<String, String> =
        LessonCopy::class.java.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .associate { it.name to (it.get(this) as String) }

    @Test
    fun everyStringIsFilledInAllFiveLanguages() {
        AppLanguage.entries.forEach { language ->
            val strings = lessonCopy(language).strings()
            assertTrue(strings.size > 50, "copy of $language looks incomplete")
            strings.forEach { (name, value) -> assertTrue(value.isNotBlank(), "$language.$name is blank") }
        }
    }

    @Test
    fun theOtherLanguagesAreTranslated() {
        val english = lessonCopy(AppLanguage.EN).strings()
        listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR).forEach { language ->
            val other = lessonCopy(language).strings()
            val same = english.filter { (name, value) -> other[name] == value }
            // Patterns that are only placeholders ("{name}") are the same everywhere.
            val realWords = same.filterValues { value -> value.replace(Regex("\\{[a-z]+}"), "").isNotBlank() }
            assertTrue(realWords.size < 4, "$language shares too much with English: ${realWords.keys}")
        }
    }

    @Test
    fun placeholdersAreFilledInEveryLanguage() {
        AppLanguage.entries.forEach { language ->
            val copy = lessonCopy(language)
            val texts = listOf(
                copy.minutesLeft(12), copy.stepProgress(2, 5), copy.stepDescription("x", 1, 3), copy.listenHint(3, 2),
                copy.listenHint(3, 0), copy.range(3, 3), copy.range(3, 9), copy.overdue(4), copy.hardCount(2),
                copy.streak(7), copy.forecastFinish("14"), copy.forecastBehind(12), copy.repetitions(3),
                copy.tomorrow("x 6–10", 3), copy.tomorrow(null, 2), copy.tomorrow("x 1–5", 0),
            )
            texts.forEach { assertTrue('{' !in it && '}' !in it, "$language leaves a placeholder: $it") }
            assertTrue("12" in copy.minutesLeft(12), "$language minutes")
            assertTrue("3" in copy.listenHint(3, 2) && "2" in copy.listenHint(3, 2), "$language listen hint")
        }
    }

    @Test
    fun theHeaderStepsFollowTheBrief() {
        val ru = lessonCopy(AppLanguage.RU)
        assertEquals(listOf("Новое", "Недавнее", "Старое"), listOf(ru.stepNew, ru.stepRecent, ru.stepOld))
        assertEquals("Урок дня", ru.title)
        assertEquals(listOf("Слушать", "Читать", "Вспомнить"), listOf(ru.phaseListen, ru.phaseRead, ru.phaseRecall))
    }

    @Test
    fun russianRepetitionsUseThePluralForms() {
        val ru = lessonCopy(AppLanguage.RU)
        assertEquals("1 повторение", ru.repetitions(1))
        assertEquals("2 повторения", ru.repetitions(2))
        assertEquals("5 повторений", ru.repetitions(5))
        assertEquals("11 повторений", ru.repetitions(11))
        assertEquals("21 повторение", ru.repetitions(21))
        assertEquals("22 повторения", ru.repetitions(22))
    }

    @Test
    fun rangesOfOneAyahAreNotRanges() {
        assertEquals("Ayah 4", lessonCopy(AppLanguage.EN).range(4, 4))
        assertEquals("Ayahs 4–9", lessonCopy(AppLanguage.EN).range(4, 9))
        assertEquals("Аят 4", lessonCopy(AppLanguage.RU).range(4, 4))
        assertEquals("Аяты 4–9", lessonCopy(AppLanguage.RU).range(4, 9))
    }
}
