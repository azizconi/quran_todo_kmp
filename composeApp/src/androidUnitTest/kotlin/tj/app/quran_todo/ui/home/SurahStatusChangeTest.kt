package tj.app.quran_todo.ui.home

import tj.app.quran_todo.hifz.model.SurahHifzStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the three choices of the surah sheet do, and when they ask first. */
class SurahStatusChangeTest {
    private val learning = SurahHifzStatus.LEARNING
    private val learned = SurahHifzStatus.LEARNED

    @Test
    fun notStartedWithMemorizedAyahsAsksHowManyWillLeaveTheRepetitions() {
        assertEquals(23, SurahStatusChange.lostAyahs(learning, coveredAyahs = 23, target = SurahTarget.NotStarted))
        assertTrue(SurahStatusChange.needsConfirmation(learning, 23, SurahTarget.NotStarted))
        assertEquals(40, SurahStatusChange.lostAyahs(learned, coveredAyahs = 40, target = SurahTarget.NotStarted))
        assertTrue(SurahStatusChange.needsConfirmation(learned, 40, SurahTarget.NotStarted))
    }

    @Test
    fun notStartedWithNothingMemorizedNeedsNoQuestion() {
        // A surah that is "learning" only because the plan is on it.
        assertFalse(SurahStatusChange.needsConfirmation(learning, coveredAyahs = 0, target = SurahTarget.NotStarted))
        assertFalse(SurahStatusChange.needsConfirmation(null, 0, SurahTarget.NotStarted))
    }

    @Test
    fun learningAMemorizedSurahAsksButTheOtherWaysToLearningDoNot() {
        assertTrue(SurahStatusChange.needsConfirmation(learned, 40, SurahTarget.Learning))
        assertEquals(40, SurahStatusChange.lostAyahs(learned, 40, SurahTarget.Learning))
        assertFalse(SurahStatusChange.needsConfirmation(null, 0, SurahTarget.Learning))
        assertFalse(SurahStatusChange.needsConfirmation(learning, 23, SurahTarget.Learning))
    }

    @Test
    fun knowByHeartNeverTakesAnythingAway() {
        for (current in listOf(null, learning, learned)) {
            assertFalse(SurahStatusChange.needsConfirmation(current, coveredAyahs = 20, target = SurahTarget.Known), "from $current")
        }
    }

    @Test
    fun theChoiceThatShowsTheCurrentStatusChangesNothing() {
        assertTrue(SurahStatusChange.isNoop(null, SurahTarget.NotStarted))
        assertTrue(SurahStatusChange.isNoop(learning, SurahTarget.Learning))
        assertTrue(SurahStatusChange.isNoop(learned, SurahTarget.Known))
        for (current in listOf(null, learning, learned)) {
            assertEquals(emptyList(), SurahStatusChange.steps(current, SurahStatusChange.targetOf(current), inPlan = true))
        }
        assertEquals(null, SurahStatusChange.resulting(SurahTarget.NotStarted))
        assertEquals(learning, SurahStatusChange.resulting(SurahTarget.Learning))
        assertEquals(learned, SurahStatusChange.resulting(SurahTarget.Known))
    }

    @Test
    fun knowByHeartMarksTheRestKnown() {
        assertEquals(listOf(StatusStep.MarkKnown), SurahStatusChange.steps(null, SurahTarget.Known, inPlan = false))
        // A surah being learned has covered ayahs on the first steps of the ladder: they are forgotten first so that the
        // whole surah becomes OLD (learned) at once.
        assertEquals(
            listOf(StatusStep.Forget, StatusStep.MarkKnown),
            SurahStatusChange.steps(learning, SurahTarget.Known, inPlan = true),
        )
    }

    @Test
    fun learningPutsTheSurahInThePlanAndAMemorizedOneIsForgottenFirst() {
        assertEquals(listOf(StatusStep.LearnNext), SurahStatusChange.steps(null, SurahTarget.Learning, inPlan = false))
        assertEquals(
            listOf(StatusStep.Forget, StatusStep.LearnNext),
            SurahStatusChange.steps(learned, SurahTarget.Learning, inPlan = false),
        )
    }

    @Test
    fun notStartedForgetsAndLeavesThePlanOnlyWhenItIsInTheQueue() {
        assertEquals(listOf(StatusStep.Forget), SurahStatusChange.steps(learned, SurahTarget.NotStarted, inPlan = false))
        assertEquals(
            listOf(StatusStep.Forget, StatusStep.LeaveThePlan),
            SurahStatusChange.steps(learning, SurahTarget.NotStarted, inPlan = true),
        )
    }

    @Test
    fun theSheetShowsTheCardOfTheDerivedStatus() {
        assertEquals(SurahTarget.NotStarted, SurahStatusChange.targetOf(null))
        assertEquals(SurahTarget.Learning, SurahStatusChange.targetOf(learning))
        assertEquals(SurahTarget.Known, SurahStatusChange.targetOf(learned))
    }
}
