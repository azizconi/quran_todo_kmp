package tj.app.quran_todo.ui.i18n

import tj.app.quran_todo.common.i18n.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The strings of the reading plan, Ramadan and the widget (task F5b) exist in the five languages, have no star and fill their patterns. */
class KhatmCopyTest {
    private val stars = listOf('★', '☆', '✦', '✧', '✩', '✪', '⭐', '۞', '✶', '✷', '✯', '✫')

    private fun strings(copy: Any): Map<String, String> =
        copy.javaClass.declaredFields
            .filter { it.type == String::class.java }
            .onEach { it.isAccessible = true }
            .associate { it.name to it.get(copy) as String }

    @Test
    fun everyStringOfEveryLanguageIsWrittenAndHasNoStar() {
        AppLanguage.entries.forEach { language ->
            val all = strings(khatmCopy(language))
            assertTrue(all.size >= 55, "${language.name}: ${all.size} strings")
            all.forEach { (name, value) ->
                assertTrue(value.isNotBlank(), "${language.name}.$name is blank")
                assertTrue(stars.none { it in value }, "${language.name}.$name has a star: $value")
                assertFalse('!' in value, "${language.name}.$name has an exclamation mark: $value")
            }
        }
    }

    @Test
    fun theFiveLanguagesDoNotShareTheirTexts() {
        val byLanguage = AppLanguage.entries.associateWith { strings(khatmCopy(it)) }
        listOf("screenTitle", "cardPendingPattern", "deleteTitle", "monthPlan", "listenJuz", "markReadButton", "newPlanTitle").forEach { key ->
            val values = byLanguage.values.map { it.getValue(key) }
            assertEquals(values.size, values.toSet().size, "$key is the same in two languages: $values")
        }
    }

    @Test
    fun everyPatternFillsAllItsPlaceholders() {
        AppLanguage.entries.forEach { language ->
            val copy = khatmCopy(language)
            val filled = listOf(
                copy.pages(121, 140), copy.pages(5, 5), copy.minutes(25), copy.cardPending(121, 140, 25), copy.cardDone(141, 160),
                copy.progress(121, 604), copy.behind(12), copy.ahead(12), copy.preset(30), copy.fromCurrent(300),
                copy.summary(604, "14 Mar"), copy.daily(21), copy.daysLeft(5), copy.ramadanUpcoming(3),
                copy.ramadanNight(3, 27), copy.juzTitle(3),
            )
            filled.forEach { text ->
                assertFalse("{" in text || "}" in text, "${language.name}: $text")
                assertTrue(text.isNotBlank())
            }
            assertTrue("121" in copy.cardPending(121, 140, 25) && "140" in copy.cardPending(121, 140, 25))
            assertTrue("25" in copy.cardPending(121, 140, 25))
            assertTrue("3" in copy.juzTitle(3))
        }
    }

    @Test
    fun theOverdueStringsExistInEveryLanguage() {
        val titles = AppLanguage.entries.map { khatmCopy(it).overdueTitle }
        assertEquals(titles.size, titles.toSet().size, "overdueTitle is the same in two languages")
        val buttons = AppLanguage.entries.map { khatmCopy(it).newTarget }
        assertEquals(buttons.size, buttons.toSet().size, "newTarget is the same in two languages")
        assertEquals("Срок плана прошёл", khatmCopy(AppLanguage.RU).overdueTitle)
    }

    @Test
    fun theHeadlineOfTheCardReadsAsItShould() {
        assertEquals("Хатм: сегодня стр. 121–140 · ~25 мин", khatmCopy(AppLanguage.RU).cardPending(121, 140, 25))
        assertEquals("Khatm: today pages 121–140 · ~25 min", khatmCopy(AppLanguage.EN).cardPending(121, 140, 25))
        assertEquals("Хатм: сегодня прочитано · завтра стр. 141–160", khatmCopy(AppLanguage.RU).cardDone(141, 160))
        assertEquals("Ночь 3 · осталось 27 дней", khatmCopy(AppLanguage.RU).ramadanNight(3, 27))
        assertEquals("Рамадан начнётся через 1 день", khatmCopy(AppLanguage.RU).ramadanUpcoming(1))
        assertEquals("Отстаёте на 12 страниц", khatmCopy(AppLanguage.RU).behind(12))
        assertEquals("Джуз ночи: 12", khatmCopy(AppLanguage.RU).juzTitle(12))
    }

    @Test
    fun theWidgetStringsExistInFiveLanguages() {
        AppLanguage.entries.forEach { language ->
            val copy = widgetCopy(language)
            strings(copy).forEach { (name, value) ->
                assertTrue(value.isNotBlank(), "${language.name}.$name is blank")
                assertTrue(stars.none { it in value }, "${language.name}.$name has a star")
            }
            assertTrue("7" in copy.streak(7))
            assertTrue("2" in copy.repetitions(2))
        }
        val allDone = AppLanguage.entries.map { widgetCopy(it).allDone }
        assertEquals(allDone.size, allDone.toSet().size, "allDone is the same in two languages")
    }

    @Test
    fun russianCountsAgreeWithTheNumber() {
        val ru = widgetCopy(AppLanguage.RU)
        assertEquals("1 день подряд", ru.streak(1))
        assertEquals("2 дня подряд", ru.streak(2))
        assertEquals("7 дней подряд", ru.streak(7))
        assertEquals("1 повторение", ru.repetitions(1))
        assertEquals("2 повторения", ru.repetitions(2))
        assertEquals("5 повторений", ru.repetitions(5))
    }
}
