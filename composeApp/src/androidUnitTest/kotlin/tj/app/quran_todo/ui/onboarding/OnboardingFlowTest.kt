package tj.app.quran_todo.ui.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The step machine of both paths: forward, back, and skipping the story. */
class OnboardingFlowTest {
    private fun forward(goal: GoalChoice?): List<FlowPosition> {
        val seen = mutableListOf(FlowPosition.First)
        var position: FlowPosition? = FlowPosition.First
        while (position != null) {
            position = OnboardingFlow.next(position, goal)
            if (position != null) seen += position
            check(seen.size < 40) { "no end" }
        }
        return seen
    }

    @Test
    fun theMemorizePathWalksAllNineStepsAndTheThreeSlides() {
        val steps = forward(GoalChoice.Memorize).map { it.step }
        assertEquals(
            listOf(
                OnboardingStep.Language,
                OnboardingStep.Story, OnboardingStep.Story, OnboardingStep.Story,
                OnboardingStep.Goal,
                OnboardingStep.Known,
                OnboardingStep.Preset,
                OnboardingStep.Pace,
                OnboardingStep.Reminder,
                OnboardingStep.Comfort,
                OnboardingStep.Ready,
            ),
            steps,
        )
        assertEquals(9, OnboardingFlow.segmentCount(GoalChoice.Memorize))
    }

    @Test
    fun theReadPathSkipsThePlanSteps() {
        val steps = forward(GoalChoice.Read).map { it.step }.distinct()
        assertEquals(
            listOf(OnboardingStep.Language, OnboardingStep.Story, OnboardingStep.Goal, OnboardingStep.Comfort, OnboardingStep.Ready),
            steps,
        )
        assertEquals(5, OnboardingFlow.segmentCount(GoalChoice.Read))
    }

    @Test
    fun beforeAGoalIsChosenTheLongPathIsAssumed() {
        assertEquals(OnboardingFlow.path(GoalChoice.Memorize), OnboardingFlow.path(null))
    }

    @Test
    fun theSlidesAreOneSegment() {
        val story = (0 until StoryPageCount).map { OnboardingFlow.segmentIndex(FlowPosition(OnboardingStep.Story, it), GoalChoice.Memorize) }
        assertEquals(listOf(1, 1, 1), story)
        assertEquals(2, OnboardingFlow.segmentIndex(FlowPosition(OnboardingStep.Goal), GoalChoice.Memorize))
        assertEquals(3, OnboardingFlow.segmentIndex(FlowPosition(OnboardingStep.Comfort), GoalChoice.Read))
        assertEquals(8, OnboardingFlow.segmentIndex(FlowPosition(OnboardingStep.Ready), GoalChoice.Memorize))
    }

    @Test
    fun theLastStepHasNoNext() {
        assertNull(OnboardingFlow.next(FlowPosition(OnboardingStep.Ready), GoalChoice.Memorize))
        assertNull(OnboardingFlow.next(FlowPosition(OnboardingStep.Ready), GoalChoice.Read))
    }

    @Test
    fun backRetracesEveryStepOfBothPaths() {
        for (goal in listOf(GoalChoice.Memorize, GoalChoice.Read)) {
            val there = forward(goal)
            var position: FlowPosition? = there.last()
            val back = mutableListOf<FlowPosition>()
            while (position != null) {
                back += position
                position = OnboardingFlow.back(position, goal)
                check(back.size < 40) { "no start" }
            }
            assertEquals(there.reversed(), back, "back is forward in reverse for $goal")
        }
    }

    @Test
    fun backOnTheFirstStepLeavesTheApp() {
        assertNull(OnboardingFlow.back(FlowPosition.First, GoalChoice.Memorize))
        assertNull(OnboardingFlow.back(FlowPosition.First, null))
    }

    @Test
    fun backFromTheFirstSlideGoesToTheLanguage() {
        assertEquals(FlowPosition(OnboardingStep.Language), OnboardingFlow.back(FlowPosition(OnboardingStep.Story, 0), null))
    }

    @Test
    fun backFromTheGoalLandsOnTheLastSlide() {
        assertEquals(FlowPosition(OnboardingStep.Story, StoryPageCount - 1), OnboardingFlow.back(FlowPosition(OnboardingStep.Goal), null))
    }

    @Test
    fun backFromTheReadingStepFollowsThePathTheGoalChose() {
        assertEquals(FlowPosition(OnboardingStep.Reminder), OnboardingFlow.back(FlowPosition(OnboardingStep.Comfort), GoalChoice.Memorize))
        assertEquals(FlowPosition(OnboardingStep.Goal), OnboardingFlow.back(FlowPosition(OnboardingStep.Comfort), GoalChoice.Read))
    }

    @Test
    fun skipLeadsFromAnySlideStraightToTheGoal() {
        for (page in 0 until StoryPageCount) {
            assertEquals(FlowPosition(OnboardingStep.Goal), OnboardingFlow.skipStory(FlowPosition(OnboardingStep.Story, page)))
        }
        // It means nothing anywhere else.
        assertEquals(FlowPosition(OnboardingStep.Pace), OnboardingFlow.skipStory(FlowPosition(OnboardingStep.Pace)))
    }

    @Test
    fun changingTheGoalOnTheGoalStepRedirectsTheNextStep() {
        val goal = FlowPosition(OnboardingStep.Goal)
        assertEquals(FlowPosition(OnboardingStep.Known), OnboardingFlow.next(goal, GoalChoice.Memorize))
        assertEquals(FlowPosition(OnboardingStep.Comfort), OnboardingFlow.next(goal, GoalChoice.Read))
    }

    @Test
    fun aStepOutsideThePathFallsBackToTheGoal() {
        // A restored state that does not match the goal can not trap the person.
        assertEquals(FlowPosition(OnboardingStep.Goal), OnboardingFlow.next(FlowPosition(OnboardingStep.Pace), GoalChoice.Read))
        assertEquals(FlowPosition(OnboardingStep.Goal), OnboardingFlow.back(FlowPosition(OnboardingStep.Pace), GoalChoice.Read))
    }

    @Test
    fun everyPositionHasAnAnalyticsNameFromTheFixedList() {
        val names = forward(GoalChoice.Memorize).map { it.analyticsName }
        assertEquals(
            listOf("language", "story_read", "story_listen", "story_memorize", "goal", "known", "preset", "pace", "reminder", "comfort", "ready"),
            names,
        )
        assertTrue(names.all { name -> name.all { it.isLowerCase() || it == '_' } })
    }
}
