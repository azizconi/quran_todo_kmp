package tj.app.quran_todo.ui.components.spotlight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "May this tour start now?": not over a sheet or a dialog, not during a transition, not during the onboarding. */
class SpotlightGateTest {
    private val open = SpotlightGateState(
        route = "home",
        navigationIdle = true,
        sheetsOpen = 0,
        windowFocused = true,
        tourRunning = false,
    )

    @Test
    fun aToursStartsOnItsOwnScreenWhenNothingIsInTheWay() {
        assertTrue(SpotlightGate.canStart(SpotlightTours.Home, open))
        assertTrue(SpotlightGate.canStart(SpotlightTours.Reader, open.copy(route = "reader")))
        assertTrue(SpotlightGate.canStart(SpotlightTours.LessonHeader, open.copy(route = "lesson")))
    }

    @Test
    fun aVisibleSheetHoldsTheTourBack() {
        assertFalse(SpotlightGate.canStart(SpotlightTours.Home, open.copy(sheetsOpen = 1)))
        assertFalse(SpotlightGate.canStart(SpotlightTours.Home, open.copy(sheetsOpen = 2)))
    }

    @Test
    fun aDialogHoldsTheTourBackBecauseTheWindowLosesTheFocus() {
        assertFalse(SpotlightGate.canStart(SpotlightTours.Home, open.copy(windowFocused = false)))
    }

    @Test
    fun aRunningNavigationTransitionHoldsTheTourBack() {
        assertFalse(SpotlightGate.canStart(SpotlightTours.Home, open.copy(navigationIdle = false)))
    }

    @Test
    fun theOnboardingNeverGetsATour() {
        SpotlightTours.all.forEach { tour ->
            assertFalse(SpotlightGate.canStart(tour, open.copy(route = SpotlightGate.OnboardingRoute)), tour.id)
        }
    }

    @Test
    fun aTourDoesNotStartOnAnotherScreen() {
        assertFalse(SpotlightGate.canStart(SpotlightTours.Home, open.copy(route = "reader")))
        assertFalse(SpotlightGate.canStart(SpotlightTours.Reader, open.copy(route = "home")))
        assertFalse(SpotlightGate.canStart(SpotlightTours.LessonPeek, open.copy(route = "settings")))
        assertFalse(SpotlightGate.canStart(SpotlightTours.Home, open.copy(route = null)))
    }

    @Test
    fun onlyOneTourRunsAtATime() {
        assertFalse(SpotlightGate.canStart(SpotlightTours.Home, open.copy(tourRunning = true)))
    }

    @Test
    fun theControllerGatesOnWhatTheAppReports() {
        val controller = SpotlightController(SpotlightFlags(InMemorySpotlightStore()), SpotlightAnalytics { _, _ -> })
        controller.request(SpotlightTours.Home)
        // Nothing is known about the navigation yet.
        assertEquals(null, controller.nextCandidate())

        controller.onNavigation("home", idle = false)
        assertEquals(null, controller.nextCandidate())

        controller.onNavigation("home", idle = true)
        assertEquals(SpotlightTours.Home, controller.nextCandidate())

        controller.sheetShown()
        assertEquals(null, controller.nextCandidate())
        controller.sheetHidden()
        assertEquals(SpotlightTours.Home, controller.nextCandidate())

        controller.windowFocused = false
        assertEquals(null, controller.nextCandidate())
        controller.windowFocused = true

        controller.onNavigation("onboarding", idle = true)
        assertEquals(null, controller.nextCandidate())
    }

    @Test
    fun twoSheetsNeedTwoCloses() {
        val controller = SpotlightController(SpotlightFlags(InMemorySpotlightStore()), SpotlightAnalytics { _, _ -> })
        controller.request(SpotlightTours.Home)
        controller.onNavigation("home", idle = true)
        controller.sheetShown()
        controller.sheetShown()
        controller.sheetHidden()
        assertEquals(null, controller.nextCandidate())
        controller.sheetHidden()
        assertEquals(SpotlightTours.Home, controller.nextCandidate())
        // An unbalanced close does not go below zero.
        controller.sheetHidden()
        controller.sheetShown()
        assertEquals(null, controller.nextCandidate())
    }
}
