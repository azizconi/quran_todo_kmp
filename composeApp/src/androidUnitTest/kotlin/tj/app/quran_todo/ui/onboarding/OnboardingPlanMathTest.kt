package tj.app.quran_todo.ui.onboarding

import tj.app.quran_todo.data.bundled.QuranFacts
import tj.app.quran_todo.hifz.HifzPresets
import tj.app.quran_todo.hifz.model.AyahRange
import tj.app.quran_todo.hifz.model.HifzScope
import tj.app.quran_todo.ui.plan.PaceMode
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The numbers of the plan steps: the first lesson around what is known, the pace and the date, the forecast. */
class OnboardingPlanMathTest {
    private val today = 20_000
    private val juzAmmaAyahs = HifzPresets.JUZ_30.sumOf { QuranFacts.ayahCount(it) }

    // -- First lesson ---------------------------------------------------------------------------

    @Test
    fun withNothingKnownJuzAmmaStartsWithAnNas() {
        // An-Nas has 6 ayahs, at most 5 * 1.5 = 7.5: the whole surah.
        assertEquals(AyahRange(114, 1, 6), OnboardingPlanMath.firstLesson(HifzPresets.JUZ_30, emptyList(), 5))
    }

    @Test
    fun theFirstLessonSkipsTheKnownSurahs() {
        assertEquals(AyahRange(113, 1, 5), OnboardingPlanMath.firstLesson(HifzPresets.JUZ_30, listOf(114), 5))
        assertEquals(AyahRange(111, 1, 5), OnboardingPlanMath.firstLesson(HifzPresets.JUZ_30, listOf(112, 113, 114), 5))
        // Known in the middle of the queue are skipped when their turn comes; the order of the list does not matter.
        assertEquals(AyahRange(114, 1, 6), OnboardingPlanMath.firstLesson(HifzPresets.JUZ_30, listOf(105, 112), 5))
    }

    @Test
    fun theFirstLessonNeverLandsInAKnownSurah() {
        val random = Random(7)
        val presets = listOf(HifzPresets.JUZ_30, HifzPresets.JUZ_29, HifzPresets.FROM_START, listOf(67, 2, 78, 36, 114))
        repeat(300) {
            val queue = presets.random(random)
            val known = (1..QuranFacts.SURAH_COUNT).filter { random.nextInt(100) < 45 }
            val perDay = random.nextInt(1, 21)
            val lesson = OnboardingPlanMath.firstLesson(queue, known, perDay)
            if (queue.all { it in known }) {
                assertNull(lesson, "everything known, queue=$queue")
            } else {
                val range = assertNotNull(lesson, "queue=$queue known=$known")
                assertFalse(range.surah in known, "the lesson ${range.surah} is a known surah")
                assertTrue(range.surah in queue)
                assertTrue(range.startAyah >= 1 && range.endAyah <= QuranFacts.ayahCount(range.surah))
                // It is the first of the queue that is not known.
                assertEquals(queue.first { it !in known }, range.surah)
            }
        }
    }

    @Test
    fun aFullyKnownPresetHasNoFirstLessonAndIsNotSelectable() {
        val info = OnboardingPlanMath.presetInfo(PresetChoice.JuzAmma, emptyList(), HifzPresets.JUZ_30, 5)
        assertTrue(info.allKnown)
        assertFalse(info.selectable)
        assertNull(info.firstLesson)
        // One surah short of everything: still selectable, the lesson is the missing one.
        val almost = OnboardingPlanMath.presetInfo(PresetChoice.JuzAmma, emptyList(), HifzPresets.JUZ_30 - 100, 5)
        assertTrue(almost.selectable)
        assertEquals(100, almost.firstLesson?.surah)
    }

    @Test
    fun presetCardsCountSurahsAndAyahs() {
        val amma = OnboardingPlanMath.presetInfo(PresetChoice.JuzAmma, emptyList(), emptyList(), 5)
        assertEquals(37, amma.surahCount)
        assertEquals(juzAmmaAyahs, amma.ayahCount)
        val tabarak = OnboardingPlanMath.presetInfo(PresetChoice.JuzTabarak, emptyList(), emptyList(), 5)
        assertEquals(11, tabarak.surahCount)
        val start = OnboardingPlanMath.presetInfo(PresetChoice.FromStart, emptyList(), emptyList(), 5)
        assertEquals(114, start.surahCount)
        assertEquals(QuranFacts.AYAH_COUNT, start.ayahCount)
    }

    @Test
    fun fromTheStartWithAlFatihaKnownBeginsAtAlBaqarah() {
        assertEquals(AyahRange(2, 1, 5), OnboardingPlanMath.firstLesson(HifzPresets.FROM_START, listOf(1), 5))
        assertEquals(AyahRange(1, 1, 7), OnboardingPlanMath.firstLesson(HifzPresets.FROM_START, emptyList(), 5))
    }

    @Test
    fun theOwnListKeepsTheOrderTheyWereChosenIn() {
        val custom = listOf(67, 112, 78)
        assertEquals(AyahRange(67, 1, 5), OnboardingPlanMath.firstLesson(custom, emptyList(), 5))
        assertEquals(AyahRange(112, 1, 4), OnboardingPlanMath.firstLesson(custom, listOf(67), 5))
        val info = OnboardingPlanMath.presetInfo(PresetChoice.Custom, custom, emptyList(), 5)
        assertEquals(3, info.surahCount)
        // An empty own list has no lesson and is not "all known".
        val empty = OnboardingPlanMath.presetInfo(PresetChoice.Custom, emptyList(), emptyList(), 5)
        assertNull(empty.firstLesson)
        assertFalse(empty.allKnown)
    }

    @Test
    fun theLessonFollowsThePace() {
        // Al-Mulk has 30 ayahs: at 10 a day the passage is 10 ayahs, at 1 a day one ayah.
        assertEquals(AyahRange(67, 1, 10), OnboardingPlanMath.firstLesson(listOf(67), emptyList(), 10))
        assertEquals(AyahRange(67, 1, 1), OnboardingPlanMath.firstLesson(listOf(67), emptyList(), 1))
    }

    @Test
    fun theFirstLessonFollowsThePaceTheDateNeeds() {
        // Al-Mulk has 30 ayahs: a date three days away needs 10 a day, whatever the wheel says.
        val selection = PlanSelection(
            preset = PresetChoice.Custom,
            customQueue = listOf(67),
            mode = PaceMode.ByDate,
            targetDay = today + 3,
            perDay = 1,
        )
        assertEquals(AyahRange(67, 1, 10), OnboardingPlanMath.firstLesson(selection, today))
        assertEquals(AyahRange(67, 1, 1), OnboardingPlanMath.firstLesson(selection.copy(mode = PaceMode.PerDay), today))
    }

    @Test
    fun theSelectionKnowsWhetherThereIsAnythingToLearn() {
        assertTrue(OnboardingPlanMath.canLearn(PlanSelection()))
        assertFalse(OnboardingPlanMath.canLearn(PlanSelection(known = HifzPresets.JUZ_30)))
        assertFalse(OnboardingPlanMath.canLearn(PlanSelection(preset = null)))
        assertFalse(OnboardingPlanMath.canLearn(PlanSelection(preset = PresetChoice.Custom)))
        assertTrue(OnboardingPlanMath.canLearn(PlanSelection(preset = PresetChoice.Custom, customQueue = listOf(114, 1), known = listOf(114))))
    }

    // -- Pace and date --------------------------------------------------------------------------

    @Test
    fun theForecastOfTheDefaultPlanIsFiveAyahsADayOverJuzAmma() {
        val forecast = OnboardingPlanMath.forecast(PlanSelection(), today)
        assertEquals(juzAmmaAyahs, forecast.remainingAyahs)
        assertEquals(juzAmmaAyahs, forecast.totalAyahs)
        assertEquals(5, forecast.perDay)
        assertEquals(today + (juzAmmaAyahs + 4) / 5, forecast.finishEpochDay)
        assertNull(forecast.requiredPerDay)
        assertFalse(forecast.tooFast)
    }

    @Test
    fun knownSurahsShortenTheForecast() {
        val none = OnboardingPlanMath.forecast(PlanSelection(), today)
        val some = OnboardingPlanMath.forecast(PlanSelection(known = listOf(113, 114)), today)
        assertEquals(juzAmmaAyahs - 6 - 5, some.remainingAyahs)
        assertTrue(some.finishEpochDay!! < none.finishEpochDay!!)
        // The known surahs are still part of the plan (the total), only not left to learn.
        assertEquals(juzAmmaAyahs, some.totalAyahs)
    }

    @Test
    fun aFasterPaceFinishesSooner() {
        val slow = OnboardingPlanMath.forecast(PlanSelection(perDay = 2), today).finishEpochDay!!
        val fast = OnboardingPlanMath.forecast(PlanSelection(perDay = 10), today).finishEpochDay!!
        assertTrue(fast < slow)
        assertEquals(today + (juzAmmaAyahs + 9) / 10, fast)
    }

    @Test
    fun aDateGivesThePaceItNeeds() {
        val target = today + 113
        val selection = PlanSelection(mode = PaceMode.ByDate, targetDay = target)
        val forecast = OnboardingPlanMath.forecast(selection, today)
        val needed = (juzAmmaAyahs + 112) / 113
        assertEquals(needed, forecast.requiredPerDay)
        assertEquals(needed, forecast.perDay)
        assertEquals(needed, OnboardingPlanMath.effectivePerDay(selection, today))
        assertFalse(forecast.tooFast)
        // The plan finishes by the date it was asked for.
        assertTrue(forecast.finishEpochDay!! <= target)
    }

    @Test
    fun aDateCloserThanTwentyAyahsADayAllowsIsFlaggedAndCapped() {
        val selection = PlanSelection(mode = PaceMode.ByDate, targetDay = today + 10)
        val forecast = OnboardingPlanMath.forecast(selection, today)
        assertTrue(forecast.tooFast)
        assertEquals(20, forecast.perDay)
        assertEquals((juzAmmaAyahs + 9) / 10, forecast.requiredPerDay)
    }

    @Test
    fun aDateMeansTheKnownSurahsAreNotCounted() {
        val target = today + 60
        val without = OnboardingPlanMath.effectivePerDay(PlanSelection(mode = PaceMode.ByDate, targetDay = target), today)
        val with = OnboardingPlanMath.effectivePerDay(PlanSelection(mode = PaceMode.ByDate, targetDay = target, known = listOf(114, 113, 112, 111, 110, 109, 108)), today)
        assertTrue(with < without)
    }

    @Test
    fun theSuggestedDateIsAboutWhereThePaceEnds() {
        val suggested = OnboardingPlanMath.suggestedTarget(PlanSelection(), today)
        assertEquals(today + (juzAmmaAyahs + 4) / 5, suggested)
        // Never closer than a week: 6 ayahs are left, two days of work.
        assertEquals(today + 7, OnboardingPlanMath.suggestedTarget(PlanSelection(known = HifzPresets.JUZ_30 - 114), today))
    }

    @Test
    fun theSavedPlanOfAPresetIsThePresetItself() {
        val plan = assertNotNull(OnboardingPlanMath.planToSave(PlanSelection(known = listOf(114)), today))
        assertEquals(HifzScope.JUZ_30, plan.scope)
        assertEquals(HifzPresets.JUZ_30, plan.surahOrder)
        assertEquals(5, plan.dailyNewAyahs)
        assertNull(plan.targetEpochDay)
        assertEquals(7, plan.manzilDays)
    }

    @Test
    fun theSavedPlanOfADateCarriesTheDateAndTheNeededPace() {
        val target = today + 200
        val plan = assertNotNull(OnboardingPlanMath.planToSave(PlanSelection(mode = PaceMode.ByDate, targetDay = target, perDay = 9), today))
        assertEquals(target, plan.targetEpochDay)
        assertEquals((juzAmmaAyahs + 199) / 200, plan.dailyNewAyahs)
    }

    @Test
    fun theSavedPlanOfTheOwnListIsCustomInTheChosenOrder() {
        val plan = assertNotNull(OnboardingPlanMath.planToSave(PlanSelection(preset = PresetChoice.Custom, customQueue = listOf(67, 114, 78)), today))
        assertEquals(HifzScope.CUSTOM, plan.scope)
        assertEquals(listOf(67, 114, 78), plan.surahOrder)
        assertNull(OnboardingPlanMath.planToSave(PlanSelection(preset = PresetChoice.Custom), today))
        assertNull(OnboardingPlanMath.planToSave(PlanSelection(preset = null), today))
    }

    // -- Words for the forecast -----------------------------------------------------------------

    @Test
    fun spansReadAsDaysWeeksMonthsAndYears() {
        assertEquals(PaceSpan(SpanUnit.Day, 1), OnboardingPlanMath.paceSpan(0))
        assertEquals(PaceSpan(SpanUnit.Day, 13), OnboardingPlanMath.paceSpan(13))
        assertEquals(PaceSpan(SpanUnit.Week, 2), OnboardingPlanMath.paceSpan(14))
        assertEquals(PaceSpan(SpanUnit.Week, 8), OnboardingPlanMath.paceSpan(59))
        assertEquals(PaceSpan(SpanUnit.Month, 2), OnboardingPlanMath.paceSpan(60))
        // 564 ayahs at 5 a day: 113 days, the "about 4 months" of the screen.
        assertEquals(PaceSpan(SpanUnit.Month, 4), OnboardingPlanMath.paceSpan(113))
        assertEquals(PaceSpan(SpanUnit.Month, 12), OnboardingPlanMath.paceSpan(365))
        assertEquals(PaceSpan(SpanUnit.Year, 2), OnboardingPlanMath.paceSpan(730))
        assertEquals(PaceSpan(SpanUnit.Year, 17), OnboardingPlanMath.paceSpan(6236))
    }

    @Test
    fun theKnownCountFallsIntoTheBucketsOfTheEvent() {
        assertEquals("0", OnboardingPlanMath.knownBucket(0))
        assertEquals("1-3", OnboardingPlanMath.knownBucket(1))
        assertEquals("1-3", OnboardingPlanMath.knownBucket(3))
        assertEquals("4-10", OnboardingPlanMath.knownBucket(4))
        assertEquals("4-10", OnboardingPlanMath.knownBucket(10))
        assertEquals("11+", OnboardingPlanMath.knownBucket(11))
        assertEquals("11+", OnboardingPlanMath.knownBucket(114))
    }
}
