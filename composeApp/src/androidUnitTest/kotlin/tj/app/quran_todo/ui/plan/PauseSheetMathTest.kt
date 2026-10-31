package tj.app.quran_todo.ui.plan

import tj.app.quran_todo.hifz.model.PlanPause
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The numbers of the pause sheet of "My plan" (task F2b). */
class PauseSheetMathTest {
    private val today = 1_000

    @Test
    fun aNewPauseStartsAtThreeDaysAndMayBeAnythingFromOneToFourteen() {
        assertEquals(3, PauseSheetMath.initialDays(null, today))
        assertEquals(1, PauseSheetMath.minDays(null, today))
        assertEquals(1, PauseSheetMath.clamp(0, null, today))
        assertEquals(14, PauseSheetMath.clamp(40, null, today))
        assertEquals(listOf(1, 3, 7, 14), PauseSheetMath.Presets)
    }

    @Test
    fun anExtensionNeverGoesBelowTheDaysThatAreLeft() {
        val pause = PlanPause(today - 1, today + 4) // today, +1 .. +4: five days left, today included
        assertEquals(5, PauseSheetMath.minDays(pause, today))
        assertEquals(5, PauseSheetMath.initialDays(pause, today))
        assertEquals(5, PauseSheetMath.clamp(2, pause, today))
        assertEquals(9, PauseSheetMath.clamp(9, pause, today))
        assertEquals(14, PauseSheetMath.clamp(20, pause, today))
    }

    @Test
    fun theLessonsComeBackTheDayAfterTheLastDayOfThePause() {
        // A pause of 3 days from today: today, +1, +2; the lessons are back on today + 3.
        assertEquals(today + 3, PauseSheetMath.resumeDay(today, 3))
        assertEquals(today + 1, PauseSheetMath.resumeDay(today, 1))
    }

    @Test
    fun theRepetitionsMoveByTheWholePauseOrByTheDifferenceOfTheEnds() {
        assertEquals(7, PauseSheetMath.shiftDays(null, today, 7))
        val pause = PlanPause(today, today + 2) // ends today + 2
        assertEquals(0, PauseSheetMath.shiftDays(pause, today, 3), "the same length: nothing to move")
        assertEquals(4, PauseSheetMath.shiftDays(pause, today, 7))
        assertEquals(11, PauseSheetMath.shiftDays(pause, today, 14))
    }

    @Test
    fun aPauseThatReachesTheLimitCannotBeExtended() {
        assertFalse(PauseSheetMath.canExtend(PlanPause(today, today + 13), today), "14 days from today: the end is at the limit")
        assertTrue(PauseSheetMath.canExtend(PlanPause(today - 1, today + 12), today), "13 days left: one more is possible")
        assertTrue(PauseSheetMath.canExtend(PlanPause(today, today + 2), today))
    }
}
