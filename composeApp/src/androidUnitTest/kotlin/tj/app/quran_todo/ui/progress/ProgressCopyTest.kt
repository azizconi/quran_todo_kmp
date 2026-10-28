package tj.app.quran_todo.ui.progress

import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.ui.i18n.ProgressCopy
import tj.app.quran_todo.ui.i18n.progressCopy
import tj.app.quran_todo.ui.i18n.weakAyahsLabel
import tj.app.quran_todo.ui.i18n.weakCountLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProgressCopyTest {
    private val stringFields = ProgressCopy::class.java.declaredFields
        .filter { it.type == String::class.java }
        .onEach { it.isAccessible = true }

    @Test
    fun everyLanguageFillsEveryString() {
        assertTrue(stringFields.size > 25, "the copy should expose its strings as fields")
        for (language in AppLanguage.entries) {
            val copy = progressCopy(language)
            assertEquals(language, copy.language)
            for (field in stringFields) {
                val value = field.get(copy) as String
                assertTrue(value.isNotBlank(), "${language.name}.${field.name} is blank")
            }
        }
    }

    @Test
    fun otherLanguagesAreTranslatedNotCopiedFromEnglish() {
        val english = progressCopy(AppLanguage.EN)
        for (language in listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR)) {
            val copy = progressCopy(language)
            val untranslated = stringFields
                .filter { field -> field.get(copy) == field.get(english) }
                .map { it.name }
            // Patterns made only of placeholders can match; a wholesale copy cannot.
            assertTrue(untranslated.size <= 3, "${language.name} repeats English in $untranslated")
        }
    }

    @Test
    fun calendarsHaveSevenWeekdaysAndTwelveMonths() {
        for (language in AppLanguage.entries) {
            val copy = progressCopy(language)
            assertEquals(7, copy.weekdays.size, language.name)
            assertEquals(12, copy.months.size, language.name)
            assertTrue((copy.weekdays + copy.months).all { it.isNotBlank() }, language.name)
        }
    }

    @Test
    fun datesFollowTheOrderOfEachLanguage() {
        assertEquals("28 окт", progressCopy(AppLanguage.RU).date(28, 10))
        assertEquals("Oct 28", progressCopy(AppLanguage.EN).date(28, 10))
        assertEquals("28 Eki", progressCopy(AppLanguage.TR).date(28, 10))
        assertEquals("Mo", progressCopy(AppLanguage.EN).weekday(0))
        assertEquals("Su", progressCopy(AppLanguage.EN).weekday(6))
        assertEquals("Пн", progressCopy(AppLanguage.RU).weekday(0))
    }

    @Test
    fun placeholdersAreFilledInEveryLanguage() {
        for (language in AppLanguage.entries) {
            val copy = progressCopy(language)
            val texts = listOf(
                copy.ringDescription(7, 412, 6236),
                copy.summary(412, 3, 5),
                copy.ayahsOf(12, 83),
                copy.date(5, 3),
                copy.weakRepeatDescription("Al-Baqarah"),
                copy.weakMore(4),
                copy.weakCount(3),
                copy.weakAyahs(listOf(3, 7, 12)),
                copy.activityValue(5),
                copy.activityValue(0),
            )
            texts.forEach { text -> assertFalse(text.contains('{') || text.contains('}'), "${language.name}: $text") }
            assertTrue(copy.ringDescription(7, 412, 6236).let { "7" in it && "412" in it && "6236" in it }, language.name)
            assertTrue("Al-Baqarah" in copy.weakRepeatDescription("Al-Baqarah"), language.name)
            assertTrue("83" in copy.ayahsOf(12, 83) && "12" in copy.ayahsOf(12, 83), language.name)
        }
    }

    @Test
    fun theSummaryLeavesOutSurahPartsThatAreZero() {
        val en = progressCopy(AppLanguage.EN)
        assertEquals("412 ayahs · 3 surahs memorized · 5 surahs in progress", en.summary(412, 3, 5))
        assertEquals("7 ayahs", en.summary(7, 0, 0))
        assertEquals("1 ayah · 1 surah in progress", en.summary(1, 0, 1))
        assertEquals("0 ayahs · 2 surahs in progress", en.summary(0, 0, 2))
        val ru = progressCopy(AppLanguage.RU)
        assertEquals("412 аятов · 3 суры выучено · 5 сур в процессе", ru.summary(412, 3, 5))
        assertEquals("1 аят · 1 сура выучено", ru.summary(1, 1, 0))
    }

    @Test
    fun theDayCountsSayNoMarksForAnEmptyDay() {
        assertEquals("no marks", progressCopy(AppLanguage.EN).activityValue(0))
        assertEquals("5 ayahs", progressCopy(AppLanguage.EN).activityValue(5))
        assertEquals("1 аят", progressCopy(AppLanguage.RU).activityValue(1))
        assertEquals("без отметок", progressCopy(AppLanguage.RU).activityValue(0))
    }

    @Test
    fun russianWeakCountsFollowTheTwoForms() {
        assertEquals("1 слабый", weakCountLabel(1, AppLanguage.RU))
        assertEquals("2 слабых", weakCountLabel(2, AppLanguage.RU))
        assertEquals("5 слабых", weakCountLabel(5, AppLanguage.RU))
        assertEquals("11 слабых", weakCountLabel(11, AppLanguage.RU))
        assertEquals("21 слабый", weakCountLabel(21, AppLanguage.RU))
        assertEquals("3 weak", weakCountLabel(3, AppLanguage.EN))
        assertEquals("3 ta zaif", weakCountLabel(3, AppLanguage.UZ))
    }

    @Test
    fun weakAyahsAreListedOrCutWithAnEllipsis() {
        assertEquals("ayah 5", weakAyahsLabel(listOf(5), AppLanguage.EN))
        assertEquals("ayahs 3, 7, 12", weakAyahsLabel(listOf(3, 7, 12), AppLanguage.EN))
        assertEquals("аят 5", weakAyahsLabel(listOf(5), AppLanguage.RU))
        assertEquals("аяты 3, 7, 12", weakAyahsLabel(listOf(3, 7, 12), AppLanguage.RU))
        assertEquals("ayahs 1, 2, 3, 4, 5, 6…", weakAyahsLabel((1..9).toList(), AppLanguage.EN))
        assertEquals("", weakAyahsLabel(emptyList(), AppLanguage.EN))
        for (language in AppLanguage.entries) {
            assertTrue("3, 7, 12" in weakAyahsLabel(listOf(3, 7, 12), language), language.name)
            assertTrue("5" in weakAyahsLabel(listOf(5), language), language.name)
        }
    }

    @Test
    fun onlyTurkishPutsThePercentSignFirst() {
        for (language in AppLanguage.entries) {
            assertEquals(language == AppLanguage.TR, progressCopy(language).percentFirst, language.name)
        }
        assertTrue(progressCopy(AppLanguage.TR).ringDescription(12, 1, 2).startsWith("%12"))
    }
}
