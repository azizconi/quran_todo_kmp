package tj.app.quran_todo.hifz

import tj.app.quran_todo.hifz.model.PassageStage
import tj.app.quran_todo.hifz.model.ReviewKind
import tj.app.quran_todo.hifz.schedule.Coverage
import tj.app.quran_todo.hifz.schedule.Forecast
import tj.app.quran_todo.hifz.schedule.Streak
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ForecastTest {
    private val m = Fx.flat // surah 78 has 40 ayahs
    private val today = 1_000

    private fun coverage(vararg ranges: IntRange) =
        Coverage(ranges.mapIndexed { i, r -> Fx.passage(i + 1L, 78, r.first, r.last, PassageStage.OLD) }, m)

    private fun newLog(range: IntRange, day: Int) =
        Fx.log(null, 78, range.first, range.last, ReviewKind.NEW, day = day)

    @Test
    fun withoutATargetTheFinishIsTodayPlusTheDaysTheRemainingAyahsNeed() {
        val plan = Fx.plan(listOf(78), daily = 5, started = today)
        val forecast = Forecast.compute(plan, coverage(), emptyList(), today)
        assertEquals(40, forecast.totalAyahs)
        assertEquals(40, forecast.remainingAyahs)
        assertEquals(today + 8, forecast.finishEpochDay)
        assertFalse(forecast.finished)
        assertNull(forecast.requiredPerDay)
        assertEquals(0, forecast.behindAyahs)
    }

    @Test
    fun theDaysAreRoundedUp() {
        val plan = Fx.plan(listOf(78), daily = 6, started = today)
        assertEquals(today + 7, Forecast.compute(plan, coverage(), emptyList(), today).finishEpochDay) // 40 / 6 = 6.67
    }

    @Test
    fun ifTodaysNewPassageIsDoneTheCountStartsTomorrow() {
        val plan = Fx.plan(listOf(78), daily = 5, started = today)
        val done = Forecast.compute(plan, coverage(1..5), listOf(newLog(1..5, today)), today)
        assertEquals(35, done.remainingAyahs)
        assertEquals(today + 1 + 7, done.finishEpochDay)
        // Learned yesterday: today is still open.
        val yesterday = Forecast.compute(plan, coverage(1..5), listOf(newLog(1..5, today - 1)), today)
        assertEquals(today + 7, yesterday.finishEpochDay)
    }

    @Test
    fun aFinishedPlanHasNoFinishDay() {
        val plan = Fx.plan(listOf(78), daily = 5, started = today - 10)
        val forecast = Forecast.compute(plan, coverage(1..40), emptyList(), today)
        assertTrue(forecast.finished)
        assertEquals(0, forecast.remainingAyahs)
        assertNull(forecast.finishEpochDay)
    }

    @Test
    fun withATargetTheRequiredPaceIsTheRemainingOverTheDaysLeft() {
        val plan = Fx.plan(listOf(78), daily = 5, started = today, target = today + 20)
        val forecast = Forecast.compute(plan, coverage(), emptyList(), today)
        assertEquals(2, forecast.requiredPerDay) // 40 / 20
        assertEquals(0, forecast.behindAyahs)
        assertEquals(today + 20, forecast.targetEpochDay)
        val later = Forecast.compute(plan, coverage(1..15), listOf(newLog(1..15, today + 5)), today + 10)
        assertEquals(3, later.requiredPerDay) // 25 / 10 = 2.5
    }

    @Test
    fun behindIsTheStraightLineMinusWhatWasLearned() {
        val plan = Fx.plan(listOf(78), daily = 5, started = today, target = today + 20)
        // Ten days of twenty, nothing learned: the line says 20 ayahs.
        assertEquals(20, Forecast.compute(plan, coverage(), emptyList(), today + 10).behindAyahs)
        // 15 learned since the start: 20 - 15 = 5 behind.
        val log = listOf(newLog(1..5, today + 1), newLog(6..10, today + 3), newLog(11..15, today + 5))
        assertEquals(5, Forecast.compute(plan, coverage(1..15), log, today + 10).behindAyahs)
        // 25 learned: ahead of the line, never negative.
        val ahead = log + newLog(16..25, today + 8)
        assertEquals(0, Forecast.compute(plan, coverage(1..25), ahead, today + 10).behindAyahs)
    }

    @Test
    fun ayahsKnownBeforeThePlanDoNotMakeThePlanLookLate() {
        // 1..10 were known when the plan started (covered, no NEW record): the line goes over the remaining 30 only.
        val plan = Fx.plan(listOf(78), daily = 5, started = today, target = today + 30)
        val forecast = Forecast.compute(plan, coverage(1..10), emptyList(), today + 15)
        assertEquals(15, forecast.behindAyahs) // 30 * 15 / 30
        assertEquals(30, forecast.remainingAyahs)
    }

    @Test
    fun afterTheTargetEverythingRemainingIsBehindAndTheRateIsTheRest() {
        val plan = Fx.plan(listOf(78), daily = 5, started = today, target = today + 10)
        val forecast = Forecast.compute(plan, coverage(1..4), listOf(newLog(1..4, today + 2)), today + 30)
        assertEquals(36, forecast.remainingAyahs)
        assertEquals(36, forecast.requiredPerDay)
        assertEquals(36, forecast.behindAyahs)
    }

    @Test
    fun onlyNewRecordsOfTheSurahsOfThePlanCount() {
        val plan = Fx.plan(listOf(78), daily = 5, started = today, target = today + 20)
        val other = Fx.log(null, 114, 1, 6, ReviewKind.NEW, day = today + 2)
        val oldRecord = newLog(1..5, today - 3) // before the start of the plan
        val forecast = Forecast.compute(plan, coverage(1..5), listOf(other, oldRecord), today + 10)
        // 1..5 were known before the plan; of the 35 left, a half of the way to the target: 35 * 10 / 20 = 17, nothing learned since the start.
        assertEquals(17, forecast.behindAyahs)
    }
}

class StreakTest {
    @Test
    fun consecutiveDaysEndingTodayCount() {
        assertEquals(3, Streak.compute(listOf(100, 99, 98, 96), today = 100))
    }

    @Test
    fun ifTodayIsNotStudiedYetTheStreakIsAlive() {
        assertEquals(3, Streak.compute(listOf(100, 99, 98), today = 101))
        assertEquals(1, Streak.compute(listOf(100), today = 101))
    }

    @Test
    fun aMissedDayBreaksTheStreak() {
        assertEquals(0, Streak.compute(listOf(100, 99, 98), today = 102))
        assertEquals(1, Streak.compute(listOf(100, 98, 97), today = 100))
    }

    @Test
    fun noRecordsNoStreak() {
        assertEquals(0, Streak.compute(emptyList(), today = 100))
    }

    @Test
    fun duplicatesAndOrderDoNotMatter() {
        assertEquals(4, Streak.compute(listOf(97, 100, 99, 99, 98, 100), today = 100))
    }

    @Test
    fun recordsFromTheFutureAreNotCountedAsToday() {
        assertEquals(0, Streak.compute(listOf(105, 104), today = 100))
    }
}
