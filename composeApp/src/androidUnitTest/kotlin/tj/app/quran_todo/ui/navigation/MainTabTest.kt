package tj.app.quran_todo.ui.navigation

import tj.app.quran_todo.ui.state.OnboardingGoal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MainTabTest {
    @Test
    fun theBarHasTheThreeTabsInOrder() {
        assertEquals(listOf(MainTab.Lesson, MainTab.Quran, MainTab.Progress), MainTab.entries.toList())
    }

    @Test
    fun memorizingOrNoGoalOpensTheLesson() {
        assertEquals(MainTab.Lesson, MainTab.defaultFor(emptySet()))
        assertEquals(MainTab.Lesson, MainTab.defaultFor(setOf(OnboardingGoal.MEMORIZE)))
        assertEquals(MainTab.Lesson, MainTab.defaultFor(setOf(OnboardingGoal.READ, OnboardingGoal.MEMORIZE)))
    }

    @Test
    fun readingOrListeningOnlyOpensTheQuran() {
        assertEquals(MainTab.Quran, MainTab.defaultFor(setOf(OnboardingGoal.READ)))
        assertEquals(MainTab.Quran, MainTab.defaultFor(setOf(OnboardingGoal.LISTEN)))
        assertEquals(MainTab.Quran, MainTab.defaultFor(setOf(OnboardingGoal.READ, OnboardingGoal.LISTEN)))
    }

    @Test
    fun aSavedTabWinsOverTheDefaultAndAnInvalidOneDoesNot() {
        assertEquals(MainTab.Progress, MainTab.restore(MainTab.Progress.ordinal, MainTab.Lesson))
        assertEquals(MainTab.Lesson, MainTab.restore(null, MainTab.Lesson))
        assertEquals(MainTab.Quran, MainTab.restore(null, MainTab.Quran))
        assertEquals(MainTab.Quran, MainTab.restore(7, MainTab.Quran))
        assertEquals(MainTab.Lesson, MainTab.restore(-1, MainTab.Lesson))
    }

    @Test
    fun backFromAnotherTabGoesToTheDefaultAndFromTheDefaultLeavesTheApp() {
        assertEquals(MainTab.Lesson, MainTab.backTarget(MainTab.Quran, MainTab.Lesson))
        assertEquals(MainTab.Lesson, MainTab.backTarget(MainTab.Progress, MainTab.Lesson))
        assertNull(MainTab.backTarget(MainTab.Lesson, MainTab.Lesson))
        // For a reader the Quran tab is the root.
        assertEquals(MainTab.Quran, MainTab.backTarget(MainTab.Lesson, MainTab.Quran))
        assertNull(MainTab.backTarget(MainTab.Quran, MainTab.Quran))
    }

    @Test
    fun telemetryNamesKeepTheOldOnesAndAddOneForTheQuran() {
        assertEquals("home", MainTab.Lesson.screenName)
        assertEquals("progress", MainTab.Progress.screenName)
        assertEquals("quran", MainTab.Quran.screenName)
    }
}
