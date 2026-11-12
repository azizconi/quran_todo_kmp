package tj.app.quran_todo.hifz

import tj.app.quran_todo.hifz.model.PlanPause
import tj.app.quran_todo.hifz.schedule.LessonBuilder
import tj.app.quran_todo.hifz.schedule.LessonInput
import tj.app.quran_todo.hifz.schedule.LighterWeek
import tj.app.quran_todo.hifz.schedule.Streak
import tj.app.quran_todo.hifz.schedule.WelcomeBack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "Welcome back" and "a lighter week" (task U17): when the screen shows, how missed days are counted, the lighter pace. */
class WelcomeBackTest {
    private fun show(days: List<Int>, today: Int, pauses: List<PlanPause> = emptyList(), answered: Int? = null): Boolean =
        WelcomeBack.shouldShow(WelcomeBack.info(days, today, pauses), answered)

    @Test
    fun missedDaysAreTheFullDaysBetweenTheLastStudyAndToday() {
        // Studied on day 10.
        assertEquals(0, WelcomeBack.info(listOf(10), 11, emptyList()).missedDays) // yesterday
        assertEquals(1, WelcomeBack.info(listOf(10), 12, emptyList()).missedDays)
        assertEquals(2, WelcomeBack.info(listOf(10), 13, emptyList()).missedDays)
        assertEquals(5, WelcomeBack.info(listOf(10), 16, emptyList()).missedDays)
    }

    @Test
    fun zeroOneTwoAndFiveMissedDays() {
        assertFalse(show(listOf(10), 11))
        assertFalse(show(listOf(10), 12))
        assertTrue(show(listOf(10), 13))
        assertTrue(show(listOf(10), 16))
    }

    @Test
    fun noStudyAtAllNeverShows() {
        assertFalse(show(emptyList(), 100))
    }

    @Test
    fun startedTodayNeverShows() {
        assertFalse(show(listOf(10, 20), 20))
    }

    @Test
    fun pauseDaysAreNotMissed() {
        // Studied on 10, paused 11..14, today 15: nothing was missed.
        val pause = listOf(PlanPause(11, 14))
        assertEquals(0, WelcomeBack.info(listOf(10), 15, pause).missedDays)
        assertFalse(show(listOf(10), 15, pause))
        // Two more days after the pause: 15 and 16 are missed.
        assertEquals(2, WelcomeBack.info(listOf(10), 17, pause).missedDays)
        assertTrue(show(listOf(10), 17, pause))
        // A pause in the middle of a longer gap only takes its own days out.
        assertEquals(3, WelcomeBack.info(listOf(10), 17, listOf(PlanPause(12, 14))).missedDays)
    }

    @Test
    fun anAnswerBelongsToItsReturn() {
        // Absent since day 10, answered on day 13: the same return does not ask again on day 14.
        assertFalse(show(listOf(10), 14, answered = 13))
        assertFalse(show(listOf(10), 20, answered = 13))
        // An answer older than the last study asks again.
        assertTrue(show(listOf(10), 13, answered = 5))
        // Never answered.
        assertTrue(show(listOf(10), 13, answered = null))
    }

    @Test
    fun aSecondAbsenceAsksAgain() {
        // Answered on 13, studied on 14 (and 15), absent again, today 18.
        assertTrue(show(listOf(10, 14, 15), 18, answered = 13))
        // After answering this one too, no more.
        assertFalse(show(listOf(10, 14, 15), 19, answered = 18))
    }

    @Test
    fun theBestStreakIsTheLongestRun() {
        assertEquals(0, Streak.best(emptyList()))
        assertEquals(1, Streak.best(listOf(5)))
        assertEquals(3, Streak.best(listOf(1, 2, 3, 7, 8, 20)))
        // Duplicates and any order.
        assertEquals(3, Streak.best(listOf(3, 1, 2, 2, 9)))
        // A pause bridges a gap; a gap that is only partly a pause breaks the run.
        assertEquals(6, Streak.best(listOf(1, 2, 3, 7, 8, 9), listOf(PlanPause(4, 6))))
        assertEquals(3, Streak.best(listOf(1, 2, 3, 7, 8, 9), listOf(PlanPause(4, 5))))
        assertEquals(WelcomeBack.info(listOf(1, 2, 3, 7, 8), 20, emptyList()).bestStreak, 3)
    }

    @Test
    fun lighterPaceIsHalfRoundedDownAtLeastOne() {
        assertEquals(1, LighterWeek.halfPace(1))
        assertEquals(1, LighterWeek.halfPace(2))
        assertEquals(1, LighterWeek.halfPace(3))
        assertEquals(2, LighterWeek.halfPace(5))
        assertEquals(5, LighterWeek.halfPace(10))
        assertEquals(10, LighterWeek.halfPace(20))
    }

    @Test
    fun lighterWeekLastsSevenDaysThenThePlanPaceReturns() {
        val today = 100
        val until = LighterWeek.untilFor(today)
        assertEquals(106, until)
        for (pace in listOf(1, 2, 5, 10)) {
            for (day in today..today + 6) {
                assertEquals(LighterWeek.halfPace(pace), LighterWeek.effectivePace(pace, until, day), "pace $pace day $day")
            }
            assertEquals(pace, LighterWeek.effectivePace(pace, until, today + 7), "pace $pace after 7 days")
            assertEquals(pace, LighterWeek.effectivePace(pace, until, today + 30))
            assertEquals(pace, LighterWeek.effectivePace(pace, null, today))
        }
        assertEquals(5, LighterWeek.effectivePace(10, until, today))
        assertTrue(LighterWeek.isActive(until, 106))
        assertFalse(LighterWeek.isActive(until, 107))
        assertFalse(LighterWeek.isActive(null, 100))
    }

    @Test
    fun theStoredPlanKeepsItsPace() {
        val plan = Fx.plan(listOf(114, 113, 112), daily = 10)
        assertEquals(5, LighterWeek.applied(plan, 106, 100).dailyNewAyahs)
        assertEquals(10, LighterWeek.applied(plan, 106, 107).dailyNewAyahs)
        assertEquals(10, plan.dailyNewAyahs)
    }

    @Test
    fun theLessonPlansTheNewPassageAtTheLighterPace() {
        val plan = Fx.plan(listOf(2), daily = 10)
        fun newEnd(lighterUntil: Int?, day: Int) = LessonBuilder.build(
            LessonInput(day, plan, emptyList(), emptyList(), Fx.flat, lighterUntil = lighterUntil),
        ).newBlock!!.let { it.endAyah - it.startAyah + 1 }
        assertEquals(10, newEnd(null, 100))
        assertEquals(5, newEnd(106, 100))
        assertEquals(5, newEnd(106, 106))
        assertEquals(10, newEnd(106, 107))
    }
}
