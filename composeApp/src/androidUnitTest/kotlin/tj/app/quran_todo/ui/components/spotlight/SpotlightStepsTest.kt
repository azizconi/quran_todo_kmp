package tj.app.quran_todo.ui.components.spotlight

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which steps of a tour are shown: the ones whose targets are on screen. */
class SpotlightStepsTest {
    private val viewport = Rect(0f, 0f, 1080f, 2400f)

    private fun bounds(rect: Rect, full: Size = Size(rect.width, rect.height), revealable: Boolean = false) =
        SpotlightBounds(rect, full, revealable)

    private val tour = SpotlightTour(
        id = "t",
        screen = SpotlightScreen.Home,
        steps = listOf(
            SpotlightStep("a", SpotlightTip.HomeLesson),
            SpotlightStep("b", SpotlightTip.HomeContinue),
            SpotlightStep("c", SpotlightTip.HomeSurah),
            SpotlightStep("d", SpotlightTip.HomeProgress),
        ),
    )

    @Test
    fun aTargetInsideTheViewportIsVisible() {
        assertTrue(SpotlightSteps.isVisible(bounds(Rect(100f, 200f, 500f, 400f)), viewport))
    }

    @Test
    fun noBoundsMeansNotVisible() {
        assertFalse(SpotlightSteps.isVisible(null, viewport))
    }

    @Test
    fun aTargetBelowTheScreenIsNotVisible() {
        assertFalse(SpotlightSteps.isVisible(bounds(Rect(100f, 2500f, 500f, 2700f)), viewport))
    }

    @Test
    fun aTargetMostlyOffTheScreenIsNotVisibleButMostlyOnIsEnough() {
        // 50 % of it is below the bottom edge.
        assertFalse(SpotlightSteps.isVisible(bounds(Rect(100f, 2300f, 500f, 2500f)), viewport))
        // 90 % inside.
        assertTrue(SpotlightSteps.isVisible(bounds(Rect(100f, 2200f, 500f, 2444f)), viewport))
    }

    @Test
    fun aTargetCutByAScrollingContainerCountsByWhatIsLeftOfIt() {
        // Its full size is 400 x 200 but only the top 80 px are inside the list.
        assertFalse(SpotlightSteps.isVisible(bounds(Rect(100f, 1000f, 500f, 1080f), full = Size(400f, 200f)), viewport))
        assertTrue(SpotlightSteps.isVisible(bounds(Rect(100f, 1000f, 500f, 1180f), full = Size(400f, 200f)), viewport))
    }

    @Test
    fun anEmptyTargetIsNotVisible() {
        assertFalse(SpotlightSteps.isVisible(bounds(Rect(100f, 100f, 100f, 100f), full = Size(0f, 0f)), viewport))
    }

    @Test
    fun stepsWithoutATargetAreLeftOut() {
        val present = mapOf("a" to bounds(Rect(0f, 100f, 400f, 300f)), "d" to bounds(Rect(900f, 100f, 1000f, 200f)))
        val selected = SpotlightSteps.select(tour.steps, viewport) { present[it] }
        assertEquals(listOf("a", "d"), selected.map { it.targetId })
    }

    @Test
    fun stepsOutsideTheVisibleAreaAreLeftOut() {
        val present = mapOf(
            "a" to bounds(Rect(0f, 100f, 400f, 300f)),
            "b" to bounds(Rect(0f, 500f, 400f, 700f)),
            "c" to bounds(Rect(0f, 2600f, 400f, 2800f)),
            "d" to bounds(Rect(900f, 100f, 1000f, 200f)),
        )
        val selected = SpotlightSteps.select(tour.steps, viewport) { present[it] }
        assertEquals(listOf("a", "b", "d"), selected.map { it.targetId })
    }

    @Test
    fun aTargetThatCanScrollItselfIntoViewKeepsItsStep() {
        val present = mapOf(
            "a" to bounds(Rect(0f, 2600f, 400f, 2800f), revealable = true),
            "b" to bounds(Rect(0f, 2600f, 400f, 2800f), revealable = false),
        )
        val selected = SpotlightSteps.select(tour.steps, viewport) { present[it] }
        assertEquals(listOf("a"), selected.map { it.targetId })
    }

    @Test
    fun withNothingOnScreenNoStepIsSelected() {
        assertTrue(SpotlightSteps.select(tour.steps, viewport) { null }.isEmpty())
    }

    @Test
    fun theOrderOfTheTourIsKept() {
        val present = tour.steps.associate { it.targetId to bounds(Rect(0f, 100f, 100f, 200f)) }
        assertEquals(listOf("a", "b", "c", "d"), SpotlightSteps.select(tour.steps, viewport) { present[it] }.map { it.targetId })
    }
}
