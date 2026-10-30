package tj.app.quran_todo.ui.progress

import kotlinx.datetime.LocalDate
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.ui.i18n.ProgressCopy
import tj.app.quran_todo.ui.i18n.ReportCopy
import tj.app.quran_todo.ui.i18n.hardCountLabel
import tj.app.quran_todo.ui.i18n.progressCopy
import tj.app.quran_todo.ui.i18n.reportCopy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressCopyTest {
    private val stringFields = ProgressCopy::class.java.declaredFields
        .filter { it.type == String::class.java }
        .onEach { it.isAccessible = true }

    private val reportFields = ReportCopy::class.java.declaredFields
        .filter { it.type == String::class.java }
        .onEach { it.isAccessible = true }

    @Test
    fun everyLanguageFillsEveryString() {
        assertTrue(stringFields.size > 50, "the copy should expose its strings as fields")
        assertTrue(reportFields.size > 15, "the report copy should expose its strings as fields")
        for (language in AppLanguage.entries) {
            val copy = progressCopy(language)
            assertEquals(language, copy.language)
            for (field in stringFields) {
                val value = field.get(copy) as String
                assertTrue(value.isNotBlank(), "${language.name}.${field.name} is blank")
            }
            val report = reportCopy(language)
            assertEquals(language, report.language)
            for (field in reportFields) {
                val value = field.get(report) as String
                assertTrue(value.isNotBlank(), "${language.name}.report.${field.name} is blank")
            }
        }
    }

    @Test
    fun otherLanguagesAreTranslatedNotCopiedFromEnglish() {
        val english = progressCopy(AppLanguage.EN)
        val englishReport = reportCopy(AppLanguage.EN)
        for (language in listOf(AppLanguage.RU, AppLanguage.TG, AppLanguage.UZ, AppLanguage.TR)) {
            val copy = progressCopy(language)
            val untranslated = stringFields.filter { field -> field.get(copy) == field.get(english) }.map { it.name }
            // Patterns made only of placeholders can match; a wholesale copy cannot.
            assertTrue(untranslated.size <= 3, "${language.name} repeats English in $untranslated")
            val report = reportCopy(language)
            val untranslatedReport = reportFields.filter { field -> field.get(report) == field.get(englishReport) }.map { it.name }
            assertTrue(untranslatedReport.size <= 2, "${language.name} report repeats English in $untranslatedReport")
        }
    }

    @Test
    fun everyPatternKeepsItsPlaceholders() {
        // A placeholder lost in a translation would print the number or the name nowhere.
        val needs = mapOf(
            "ringDescriptionPattern" to listOf("{p}", "{a}", "{b}"),
            "ringDescriptionBelowOnePattern" to listOf("{a}", "{b}"),
            "knownPattern" to listOf("{n}"),
            "learningPattern" to listOf("{n}"),
            "planFinishPattern" to listOf("{scope}", "{date}"),
            "planFinishedPattern" to listOf("{scope}"),
            "planBehindPattern" to listOf("{ayahs}"),
            "planNeedPacePattern" to listOf("{ayahs}"),
            "lessonLeftPattern" to listOf("{a}", "{b}"),
            "datePattern" to listOf("{d}", "{m}"),
            "ayahsOfPattern" to listOf("{a}", "{b}"),
            "juzPattern" to listOf("{n}"),
            "hardMorePattern" to listOf("{n}"),
            "hardAyahDescriptionPattern" to listOf("{name}", "{n}"),
        )
        val reportNeeds = mapOf(
            "totalPattern" to listOf("{a}", "{b}"),
            "surahsPattern" to listOf("{list}"),
            "moreSurahsPattern" to listOf("{n}"),
            "finishPattern" to listOf("{date}"),
            "behindPattern" to listOf("{ayahs}"),
            "needPacePattern" to listOf("{ayahs}"),
        )
        for (language in AppLanguage.entries) {
            val copy = progressCopy(language)
            for ((name, placeholders) in needs) {
                val value = stringFields.first { it.name == name }.get(copy) as String
                placeholders.forEach { assertTrue(it in value, "${language.name}.$name lost $it: $value") }
            }
            val report = reportCopy(language)
            for ((name, placeholders) in reportNeeds) {
                val value = reportFields.first { it.name == name }.get(report) as String
                placeholders.forEach { assertTrue(it in value, "${language.name}.report.$name lost $it: $value") }
            }
        }
    }

    @Test
    fun calendarsHaveTwelveMonths() {
        for (language in AppLanguage.entries) {
            val copy = progressCopy(language)
            assertEquals(12, copy.months.size, language.name)
            assertTrue(copy.months.all { it.isNotBlank() }, language.name)
        }
    }

    @Test
    fun datesFollowTheOrderOfEachLanguage() {
        assertEquals("28 окт", progressCopy(AppLanguage.RU).date(28, 10))
        assertEquals("Oct 28", progressCopy(AppLanguage.EN).date(28, 10))
        assertEquals("28 Eki", progressCopy(AppLanguage.TR).date(28, 10))
    }

    @Test
    fun placeholdersAreFilledInEveryLanguage() {
        for (language in AppLanguage.entries) {
            val copy = progressCopy(language)
            val texts = listOf(
                copy.ringDescription(7, false, 412, 6236),
                copy.ringDescription(0, true, 12, 6236),
                copy.surahsLine(3, 5).orEmpty(),
                copy.ayahsOf(12, 83),
                copy.date(5, 3),
                copy.todayText(TodayUi.Left(2, 5)),
                copy.hardMore(4),
                copy.hardCount(3),
                copy.hardAyahDescription("Al-Baqarah", 255),
                copy.juzName(30),
                copy.juzDescription(30, 37),
                copy.activityValue(5, 12),
                copy.activityValue(0, 0),
                copy.retentionDescription(92, 24),
                copy.planHeadline(PlanLineUi(PlanStatus.Finish, HifzScope.JUZ_30, 20_500), today = 20_000).orEmpty(),
                copy.planStatusLine(PlanLineUi(PlanStatus.Behind, HifzScope.JUZ_30, 20_500, ayahs = 12)).orEmpty(),
                copy.planStatusLine(PlanLineUi(PlanStatus.NeedPace, HifzScope.JUZ_30, 20_500, ayahs = 7)).orEmpty(),
            )
            texts.forEach { text -> assertFalse(text.contains('{') || text.contains('}'), "${language.name}: $text") }
            assertTrue(copy.ringDescription(7, false, 412, 6236).let { "7" in it && "412" in it && "6236" in it }, language.name)
            assertTrue("83" in copy.ayahsOf(12, 83) && "12" in copy.ayahsOf(12, 83), language.name)
            assertTrue("Al-Baqarah" in copy.hardAyahDescription("Al-Baqarah", 255) && "255" in copy.hardAyahDescription("Al-Baqarah", 255), language.name)
            assertTrue("3" in copy.todayText(TodayUi.Left(3, 5)) && "5" in copy.todayText(TodayUi.Left(3, 5)), language.name)
        }
    }

    @Test
    fun theHeroNamesTheSurahsKnownAndBeingLearnedAndLeavesOutZeros() {
        val en = progressCopy(AppLanguage.EN)
        assertEquals("Know by heart: 3 surahs · Learning: 5 surahs", en.surahsLine(3, 5))
        assertEquals("Know by heart: 1 surah", en.surahsLine(1, 0))
        assertEquals("Learning: 1 surah", en.surahsLine(0, 1))
        assertNull(en.surahsLine(0, 0))
        val ru = progressCopy(AppLanguage.RU)
        assertEquals("Знаю наизусть: 3 суры · Учу: 5 сур", ru.surahsLine(3, 5))
        assertEquals("Знаю наизусть: 21 сура", ru.surahsLine(21, 0))
    }

    @Test
    fun theRingIsDescribedWithLessThanOnePercentInWords() {
        assertEquals("меньше 1 %: выучено 12 из 6236 аятов", progressCopy(AppLanguage.RU).ringDescription(0, true, 12, 6236))
        assertEquals("7%: 412 of 6236 ayahs memorized", progressCopy(AppLanguage.EN).ringDescription(7, false, 412, 6236))
        assertTrue(progressCopy(AppLanguage.TR).ringDescription(12, false, 1, 2).startsWith("%12"))
        assertTrue(progressCopy(AppLanguage.TR).ringDescription(0, true, 1, 2).startsWith("%1"))
    }

    @Test
    fun theTodayTileSaysDoneLeftOrNoLesson() {
        val ru = progressCopy(AppLanguage.RU)
        assertEquals("Урок выполнен", ru.todayText(TodayUi.Done))
        assertEquals("Осталось 2 из 5", ru.todayText(TodayUi.Left(2, 5)))
        assertEquals("Урока нет", ru.todayText(TodayUi.None))
        assertEquals("2 of 5 left", progressCopy(AppLanguage.EN).todayText(TodayUi.Left(2, 5)))
    }

    @Test
    fun retentionShowsADashWithoutData() {
        for (language in AppLanguage.entries) assertEquals("—", progressCopy(language).retentionValue(null), language.name)
        assertEquals("92%", progressCopy(AppLanguage.EN).retentionValue(92))
        assertEquals("%92", progressCopy(AppLanguage.TR).retentionValue(92))
    }

    @Test
    fun theActivityOfADayNamesBothKindsOrSaysNone() {
        val en = progressCopy(AppLanguage.EN)
        assertEquals("New 5 · Reviewed 12", en.activityValue(5, 12))
        assertEquals("New 5", en.activityValue(5, 0))
        assertEquals("Reviewed 12", en.activityValue(0, 12))
        assertEquals("no activity", en.activityValue(0, 0))
        assertNull(en.activitySummary(0, 0))
        assertEquals("New 14 · Reviewed 63", en.activitySummary(14, 63))
        assertEquals("без занятий", progressCopy(AppLanguage.RU).activityValue(0, 0))
    }

    @Test
    fun thePlanLineReadsLikeTheBriefInRussian() {
        val ru = progressCopy(AppLanguage.RU)
        val today = LocalDate(2026, 12, 2).toEpochDays()
        val finish = LocalDate(2026, 12, 16).toEpochDays()
        val line = PlanLineUi(PlanStatus.Finish, HifzScope.JUZ_30, finish)
        assertEquals("Джуз Амма: закончите ~16 декабря", ru.planHeadline(line, today))
        // Another year: the year is written.
        val nextYear = PlanLineUi(PlanStatus.Finish, HifzScope.JUZ_30, LocalDate(2027, 2, 16).toEpochDays())
        assertEquals("Джуз Амма: закончите ~16 февраля 2027", ru.planHeadline(nextYear, today))
        assertNull(ru.planStatusLine(line))
        assertEquals("Отстаёте на 12 аятов", ru.planStatusLine(PlanLineUi(PlanStatus.Behind, HifzScope.JUZ_30, finish, 12)))
        assertEquals("Идёте с опережением", ru.planStatusLine(PlanLineUi(PlanStatus.Ahead, HifzScope.JUZ_30, finish)))
        assertEquals("Идёте по графику", ru.planStatusLine(PlanLineUi(PlanStatus.OnTrack, HifzScope.JUZ_30, finish)))
        assertEquals("Джуз Амма: все суры выучены", ru.planHeadline(PlanLineUi(PlanStatus.Finished, HifzScope.JUZ_30), today))
        assertEquals("Свои суры", ru.planHeadline(PlanLineUi(PlanStatus.Finish, HifzScope.CUSTOM), today))
        assertNull(ru.planHeadline(PlanLineUi(PlanStatus.NoPlan), today))
    }

    @Test
    fun hardCountsFollowTheFormsOfRussian() {
        assertEquals("1 трудный", hardCountLabel(1, AppLanguage.RU))
        assertEquals("2 трудных", hardCountLabel(2, AppLanguage.RU))
        assertEquals("5 трудных", hardCountLabel(5, AppLanguage.RU))
        assertEquals("11 трудных", hardCountLabel(11, AppLanguage.RU))
        assertEquals("21 трудный", hardCountLabel(21, AppLanguage.RU))
        assertEquals("3 hard", hardCountLabel(3, AppLanguage.EN))
        assertEquals("3 ta qiyin", hardCountLabel(3, AppLanguage.UZ))
    }

    @Test
    fun onlyTurkishPutsThePercentSignFirst() {
        for (language in AppLanguage.entries) {
            assertEquals(language == AppLanguage.TR, progressCopy(language).percentFirst, language.name)
        }
        assertEquals("%12", progressCopy(AppLanguage.TR).percentText(12))
        assertEquals("12%", progressCopy(AppLanguage.RU).percentText(12))
    }
}
