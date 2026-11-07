package tj.app.quran_todo.ui.i18n

import kotlinx.datetime.LocalDate
import tj.app.quran_todo.common.i18n.AppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlanCopyTest {
    private val stringFields = PlanCopy::class.java.declaredFields
        .filter { it.type == String::class.java }
        .onEach { it.isAccessible = true }

    private fun day(year: Int, month: Int, dayOfMonth: Int) = LocalDate(year, month, dayOfMonth).toEpochDays()

    @Test
    fun everyLanguageFillsEveryString() {
        assertTrue(stringFields.size > 60, "the copy should expose its strings as fields")
        for (language in AppLanguage.entries) {
            val copy = planCopy(language)
            assertEquals(language, copy.language)
            for (field in stringFields) {
                val value = field.get(copy) as String
                assertTrue(value.isNotBlank(), "${language.name}.${field.name} is blank")
            }
        }
    }

    @Test
    fun otherLanguagesAreTranslatedNotCopiedFromEnglish() {
        val english = planCopy(AppLanguage.EN)
        for (language in listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR)) {
            val copy = planCopy(language)
            val untranslated = stringFields.filter { it.get(copy) == it.get(english) }.map { it.name }
            assertTrue(untranslated.size <= 6, "${language.name} repeats English in $untranslated")
        }
    }

    @Test
    fun everyPatternIsFilled() {
        val today = day(2026, 10, 29)
        for (language in AppLanguage.entries) {
            val copy = planCopy(language)
            val texts = listOf(
                copy.needPerDay(7),
                copy.minutesPerDay(5),
                copy.longLesson(70),
                copy.tooFast(31),
                copy.showAll(114),
                copy.oldTitle(7),
                copy.oldPages(1.5f),
                copy.finishOn(day(2027, 3, 14), today),
                copy.behind(12),
                copy.remaining(120, 564),
                copy.scopeInfo(37, 564),
                copy.dateLabel(today + 3, today),
            )
            texts.forEach { text ->
                assertFalse(text.contains('{') || text.contains('}'), "${language.name}: $text")
                assertTrue(text.isNotBlank())
            }
            assertTrue(copy.needPerDay(7).contains("7"), language.name)
            assertTrue(copy.oldTitle(21).contains("21"), language.name)
            assertTrue(copy.behind(12).contains("12"), language.name)
            assertTrue(copy.longLesson(70).contains("70"), language.name)
        }
    }

    @Test
    fun theRussianForecastReadsLikeTheBrief() {
        val ru = planCopy(AppLanguage.RU)
        val today = day(2026, 10, 29)
        assertEquals("Закончите ~14 марта 2027", ru.finishOn(day(2027, 3, 14), today))
        assertEquals("нужно 7 аятов в день", ru.needPerDay(7))
        assertEquals("нужно 1 аят в день", ru.needPerDay(1))
        assertEquals("отстаёте на 12 аятов", ru.behind(12))
        assertEquals("отстаёте на 2 аята", ru.behind(2))
        assertEquals("Осталось 120 из 564 аятов", ru.remaining(120, 564))
        assertEquals("≈ 5 минут в день", ru.minutesPerDay(5))
        assertEquals("≈ 1 минуту в день", ru.minutesPerDay(1))
        assertEquals("≈ 22 минуты в день", ru.minutesPerDay(22))
        assertEquals("Это около 70 минут в день — урок будет длинным", ru.longLesson(70))
        assertEquals("37 сур · 564 аята", ru.scopeInfo(37, 564))
    }

    @Test
    fun theYearIsLeftOutForADateInThisYear() {
        val today = day(2027, 1, 10)
        assertEquals("14 марта", planCopy(AppLanguage.RU).dateLabel(day(2027, 3, 14), today))
        assertEquals("March 14", planCopy(AppLanguage.EN).dateLabel(day(2027, 3, 14), today))
        assertEquals("March 14, 2028", planCopy(AppLanguage.EN).dateLabel(day(2028, 3, 14), today))
        assertEquals("14 март", planCopy(AppLanguage.TG).dateLabel(day(2027, 3, 14), today))
        assertEquals("14 mart", planCopy(AppLanguage.UZ).dateLabel(day(2027, 3, 14), today))
        assertEquals("14 Mart", planCopy(AppLanguage.TR).dateLabel(day(2027, 3, 14), today))
    }

    @Test
    fun everyMonthHasItsOwnName() {
        for (language in AppLanguage.entries) {
            val copy = planCopy(language)
            val today = day(2027, 1, 1)
            val names = (1..12).map { month -> copy.dateLabel(day(2027, month, 5), today) }
            assertEquals(12, names.toSet().size, "${language.name}: $names")
        }
    }

    @Test
    fun pagesADayAreRoundedToATenthWithTheDecimalSignOfTheLanguage() {
        val ru = planCopy(AppLanguage.RU)
        assertEquals("≈ 1,5 страницы в день", ru.oldPages(1.5f))
        assertEquals("≈ 2 страницы в день", ru.oldPages(2f))
        assertEquals("≈ 5 страниц в день", ru.oldPages(5f))
        assertEquals("≈ 1 страницу в день", ru.oldPages(1f))
        assertEquals("≈ 0,1 страницы в день", ru.oldPages(0.01f), "never shown as zero")
        assertEquals("≈ 1.5 pages a day", planCopy(AppLanguage.EN).oldPages(1.5f))
        assertEquals("≈ 1 page a day", planCopy(AppLanguage.EN).oldPages(1f))
        assertEquals("≈ 2.3 pages a day", planCopy(AppLanguage.EN).oldPages(2.26f))
        assertTrue(planCopy(AppLanguage.TR).oldPages(2.5f).contains("2,5"))
    }

    @Test
    fun daysAndMinutesFollowTheRussianForms() {
        val ru = planCopy(AppLanguage.RU)
        assertEquals("Повторять всё выученное за 3 дня", ru.oldTitle(3))
        assertEquals("Повторять всё выученное за 7 дней", ru.oldTitle(7))
        assertEquals("Повторять всё выученное за 21 день", ru.oldTitle(21))
        assertEquals("Повторять всё выученное за 30 дней", ru.oldTitle(30))
    }

    @Test
    fun theHintOfALongCircleReadsLikeTheBriefInEveryLanguage() {
        val ru = planCopy(AppLanguage.RU)
        assertEquals("Это около 27 минут в день. Круг в 20 дней — около 10 минут.", ru.oldHint(27, 20, 10))
        assertEquals("Это около 21 минуты в день. Круг в 21 день — около 11 минут.", ru.oldHint(21, 21, 11))
        assertEquals("Это около 31 минуты в день. Круг в 22 дня — около 12 минут.", ru.oldHint(31, 22, 12))
        assertEquals("Поставить круг в 20 дней", ru.oldSetDays(20))
        assertEquals("20 дней", ru.daysLabel(20))
        assertEquals("That is about 27 minutes a day. A circle of 20 days is about 10 minutes.", planCopy(AppLanguage.EN).oldHint(27, 20, 10))
        assertEquals("Ин тақрибан 27 дақиқа дар як рӯз аст. Давра дар 20 рӯз — тақрибан 10 дақиқа.", planCopy(AppLanguage.TG).oldHint(27, 20, 10))
        assertEquals("Bu kuniga taxminan 27 daqiqa. 20 kunlik doira — taxminan 10 daqiqa.", planCopy(AppLanguage.UZ).oldHint(27, 20, 10))
        assertEquals("Bu günde yaklaşık 27 dakika. 20 günlük döngü yaklaşık 10 dakika sürer.", planCopy(AppLanguage.TR).oldHint(27, 20, 10))
        for (language in AppLanguage.entries) {
            val copy = planCopy(language)
            for (text in listOf(copy.oldHint(27, 20, 10), copy.oldSetDays(20))) {
                assertFalse(text.contains('{') || text.contains('}'), "${language.name}: $text")
                assertTrue(text.contains("20"), "${language.name}: $text")
            }
            assertTrue(copy.oldHint(27, 20, 10).contains("27") && copy.oldHint(27, 20, 10).contains("10"), language.name)
        }
    }

    @Test
    fun theMinutesOfTheCircleADayReuseTheMinutesPattern() {
        assertEquals("≈ 12 минут в день", planCopy(AppLanguage.RU).minutesPerDay(12))
        assertEquals("≈ 12 minutes a day", planCopy(AppLanguage.EN).minutesPerDay(12))
        assertEquals("≈ 12 дақиқа дар як рӯз", planCopy(AppLanguage.TG).minutesPerDay(12))
        assertEquals("≈ kuniga 12 daqiqa", planCopy(AppLanguage.UZ).minutesPerDay(12))
        assertEquals("≈ günde 12 dakika", planCopy(AppLanguage.TR).minutesPerDay(12))
    }
}
