package tj.app.quran_todo.ui.components.spotlight

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The life of a tour: start, steps, the end, the marks and the events. */
class SpotlightControllerTest {
    private class Log {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        val analytics = SpotlightAnalytics { name, params -> events += name to params }
    }

    private val store = InMemorySpotlightStore()
    private val flags = SpotlightFlags(store)
    private val log = Log()
    private val controller = SpotlightController(flags, log.analytics)
    private val tour = SpotlightTours.Home

    private fun ready() {
        controller.onNavigation("home", idle = true)
        controller.request(tour)
    }

    @Test
    fun aTourWithNoStepOnScreenDoesNotStartAndIsNotMarked() {
        ready()
        assertFalse(controller.start(tour, emptyList()))
        assertNull(controller.run)
        assertFalse(flags.isSeen(tour))
        assertTrue(store.read().isEmpty())
        assertTrue(log.events.isEmpty())
    }

    @Test
    fun aTourThatCouldNotStartIsTriedAgainWhenTheScreenAsksAgain() {
        ready()
        controller.markExhausted(tour)
        // The same request is not tried again and again ...
        assertNull(controller.nextCandidate())
        // ... but the screen asking anew (it was left and opened again) gets it going.
        controller.withdraw(tour)
        controller.request(tour)
        assertEquals(tour, controller.nextCandidate())
    }

    @Test
    fun startingShowsTheFirstStep() {
        ready()
        assertTrue(controller.start(tour, tour.steps))
        val run = assertNotNull(controller.run)
        assertEquals(0, run.index)
        assertEquals(tour.steps.first(), run.step)
        assertEquals(4, run.total)
        assertFalse(run.isLast)
    }

    @Test
    fun aRunningTourIsNotMarkedYetSoItShowsAgainIfTheProcessDies() {
        ready()
        controller.start(tour, tour.steps)
        controller.stepShown(0)
        controller.advance()
        assertFalse(flags.isSeen(tour))
        assertTrue(store.read().isEmpty())
        // A fresh start of the app (new controller on the same store) offers it again.
        val restarted = SpotlightController(SpotlightFlags(store), log.analytics)
        restarted.onNavigation("home", idle = true)
        restarted.request(tour)
        assertEquals(tour, restarted.nextCandidate())
    }

    @Test
    fun advancingWalksThroughTheStepsAndTheLastOneEndsTheTourAsDone() {
        ready()
        controller.start(tour, tour.steps)
        controller.stepShown(0)
        controller.advance()
        assertEquals(1, controller.run?.index)
        controller.advance()
        controller.advance()
        assertTrue(controller.run!!.isLast)
        controller.advance()
        assertNull(controller.run)
        assertTrue(flags.isSeen(tour))
        assertEquals(setOf("home"), store.read())

        val finished = log.events.last()
        assertEquals("spotlight_finished", finished.first)
        assertEquals(mapOf("tour" to "home", "result" to "done", "step" to "4"), finished.second)
    }

    @Test
    fun skipEndsTheTourAndRemembersWhereItWasClosed() {
        ready()
        controller.start(tour, tour.steps)
        controller.stepShown(0)
        controller.advance()
        controller.skip()
        assertNull(controller.run)
        assertTrue(flags.isSeen(tour))
        assertEquals(
            "spotlight_finished" to mapOf("tour" to "home", "result" to "skipped", "step" to "2"),
            log.events.last(),
        )
    }

    @Test
    fun theShownEventIsSentOnceWithTheTourOnly() {
        ready()
        controller.start(tour, tour.steps)
        controller.stepShown(0)
        controller.stepShown(0)
        controller.advance()
        controller.stepShown(1)
        assertEquals(1, log.events.count { it.first == "spotlight_shown" })
        assertEquals(mapOf("tour" to "home"), log.events.first { it.first == "spotlight_shown" }.second)
    }

    @Test
    fun eventsCarryNoFreeText() {
        ready()
        controller.start(tour, tour.steps)
        controller.stepShown(0)
        controller.skip()
        val allowed = setOf("tour", "result", "step")
        log.events.forEach { (_, params) -> assertTrue(allowed.containsAll(params.keys), params.toString()) }
    }

    @Test
    fun aSeenTourIsNeverOfferedAgain() {
        ready()
        controller.start(tour, tour.steps)
        controller.stepShown(0)
        controller.skip()
        controller.request(tour)
        assertNull(controller.nextCandidate())
        assertFalse(controller.start(tour, tour.steps))
    }

    @Test
    fun showingTipsAgainBringsEveryTourBack() {
        ready()
        controller.start(tour, tour.steps)
        controller.stepShown(0)
        controller.skip()
        flags.markSeen(SpotlightTours.Reader)
        controller.resetAll()
        assertFalse(flags.isSeen(tour))
        assertFalse(flags.isSeen(SpotlightTours.Reader))
        assertTrue(store.read().isEmpty())
        controller.request(tour)
        assertEquals(tour, controller.nextCandidate())
    }

    @Test
    fun resettingAlsoGivesBackTheToursThatCouldNotStart() {
        ready()
        controller.markExhausted(tour)
        controller.resetAll()
        assertEquals(tour, controller.nextCandidate())
    }

    @Test
    fun aTargetThatIsGoneMovesOnToTheNextStep() {
        ready()
        controller.start(tour, tour.steps)
        controller.stepShown(0)
        controller.advance()
        controller.dropStep()
        assertEquals(2, controller.run?.index)
        assertFalse(flags.isSeen(tour))
    }

    @Test
    fun whenTheLastTargetIsGoneTheTourEndsAsDoneBecauseSomethingWasShown() {
        ready()
        controller.start(tour, listOf(tour.steps[0], tour.steps[3]))
        controller.stepShown(0)
        controller.advance()
        controller.dropStep()
        assertNull(controller.run)
        assertTrue(flags.isSeen(tour))
    }

    @Test
    fun whenNoStepWasEverShownTheTourLeavesNoMark() {
        ready()
        controller.start(tour, listOf(tour.steps[0]))
        controller.dropStep()
        assertNull(controller.run)
        assertFalse(flags.isSeen(tour))
        assertTrue(log.events.isEmpty())
        // It is not offered again until the screen asks anew.
        assertNull(controller.nextCandidate())
    }

    @Test
    fun withdrawingARunningTourDropsItWithoutAMark() {
        ready()
        controller.start(tour, tour.steps)
        controller.stepShown(0)
        controller.withdraw(tour)
        assertNull(controller.run)
        assertFalse(flags.isSeen(tour))
        assertNull(controller.nextCandidate())
    }

    @Test
    fun withdrawingAnotherTourLeavesTheRunningOneAlone() {
        ready()
        controller.start(tour, tour.steps)
        controller.withdraw(SpotlightTours.Reader)
        assertNotNull(controller.run)
    }

    @Test
    fun toursAreOfferedInTheOrderTheScreensAsked() {
        controller.onNavigation("reader", idle = true)
        controller.request(SpotlightTours.Player)
        controller.request(SpotlightTours.Reader)
        assertEquals(SpotlightTours.Player, controller.nextCandidate())
    }

    @Test
    fun askingTwiceIsTheSameAsAskingOnce() {
        ready()
        controller.request(tour)
        controller.request(tour)
        controller.start(tour, tour.steps)
        controller.stepShown(0)
        controller.skip()
        assertNull(controller.nextCandidate())
    }

    @Test
    fun aTourStaysWantedUntilEveryoneWhoAskedHasWithdrawn() {
        // A card that leaves and the card that comes in both ask for the same tour for a moment.
        ready()
        controller.request(tour)
        controller.withdraw(tour)
        assertEquals(tour, controller.nextCandidate())
        controller.withdraw(tour)
        assertNull(controller.nextCandidate())
    }

    @Test
    fun nothingStartsWhileAnotherTourRuns() {
        ready()
        controller.start(tour, tour.steps)
        controller.onNavigation("reader", idle = true)
        controller.request(SpotlightTours.Reader)
        assertNull(controller.nextCandidate())
        assertFalse(controller.start(SpotlightTours.Reader, SpotlightTours.Reader.steps))
    }
}
