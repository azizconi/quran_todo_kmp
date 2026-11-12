package tj.app.quran_todo.ui.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The step machine: four steps, forward and back. */
class OnboardingFlowTest {
    private fun forward(): List<FlowPosition> {
        val seen = mutableListOf(FlowPosition.First)
        var position: FlowPosition? = FlowPosition.First
        while (position != null) {
            position = OnboardingFlow.next(position)
            if (position != null) seen += position
            check(seen.size < 20) { "no end" }
        }
        return seen
    }

    @Test
    fun theMemorizePathWalksFourSteps() {
        assertEquals(
            listOf(OnboardingStep.Language, OnboardingStep.Preset, OnboardingStep.Known, OnboardingStep.Ready),
            forward().map { it.step },
        )
        assertEquals(4, OnboardingFlow.segmentCount)
    }

    @Test
    fun theProgressBarFollowsTheSteps() {
        assertEquals(listOf(0, 1, 2, 3), forward().map { OnboardingFlow.segmentIndex(it) })
    }

    @Test
    fun theLastStepHasNoNext() {
        assertNull(OnboardingFlow.next(FlowPosition(OnboardingStep.Ready)))
    }

    @Test
    fun backRetracesEveryStep() {
        val there = forward()
        var position: FlowPosition? = there.last()
        val back = mutableListOf<FlowPosition>()
        while (position != null) {
            back += position
            position = OnboardingFlow.back(position)
            check(back.size < 20) { "no start" }
        }
        assertEquals(there.reversed(), back)
    }

    @Test
    fun backOnTheFirstStepLeavesTheApp() {
        assertNull(OnboardingFlow.back(FlowPosition.First))
    }

    @Test
    fun theKnownStepComesAfterThePreset() {
        assertEquals(FlowPosition(OnboardingStep.Known), OnboardingFlow.next(FlowPosition(OnboardingStep.Preset)))
        assertEquals(FlowPosition(OnboardingStep.Preset), OnboardingFlow.back(FlowPosition(OnboardingStep.Known)))
    }

    @Test
    fun everyPositionHasAnAnalyticsNameFromTheFixedList() {
        val names = forward().map { it.analyticsName }
        assertEquals(listOf("language", "preset", "known", "ready"), names)
        assertTrue(names.all { name -> name.all { it.isLowerCase() || it == '_' } })
    }
}
