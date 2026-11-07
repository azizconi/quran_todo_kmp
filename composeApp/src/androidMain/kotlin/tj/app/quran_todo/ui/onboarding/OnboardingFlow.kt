package tj.app.quran_todo.ui.onboarding

/**
 * The steps of the onboarding v2 (docs/redesign/tasks/PC1-onboarding-v2.md). The three slides of the story are ONE step with
 * three pages ([StoryPageCount]); they are one segment of the progress bar.
 */
internal enum class OnboardingStep {
    Language,
    Story,
    Goal,
    Known,
    Preset,
    Pace,
    Reminder,
    Comfort,
    Ready,
}

/** What the person wants from the app; chosen at the "goal" step and decides the rest of the path. */
internal enum class GoalChoice(val analyticsName: String) {
    Memorize("memorize"),
    Read("read"),
}

/** Slides "Read beautifully", "Listen and repeat", "Memorize and do not forget". */
internal const val StoryPageCount = 3

/** Where the person is. [storyPage] counts only while [step] is [OnboardingStep.Story]. */
internal data class FlowPosition(val step: OnboardingStep, val storyPage: Int = 0) {
    /** The name the `onboarding_step` event carries: one of the fixed list, never free text. */
    val analyticsName: String
        get() = when (step) {
            OnboardingStep.Language -> "language"
            OnboardingStep.Story -> when (storyPage) {
                0 -> "story_read"
                1 -> "story_listen"
                else -> "story_memorize"
            }
            OnboardingStep.Goal -> "goal"
            OnboardingStep.Known -> "known"
            OnboardingStep.Preset -> "preset"
            OnboardingStep.Pace -> "pace"
            OnboardingStep.Reminder -> "reminder"
            OnboardingStep.Comfort -> "comfort"
            OnboardingStep.Ready -> "ready"
        }

    companion object {
        val First = FlowPosition(OnboardingStep.Language)
    }
}

/**
 * The step machine, without Compose. The path depends on the goal: "Memorize" goes through the whole plan (what is known, what
 * to learn, the pace, the reminder), "Read and listen" skips those. Until a goal is chosen the long path is assumed (it is the
 * product's main one), so the progress bar only changes length if the person picks the short one.
 */
internal object OnboardingFlow {
    fun path(goal: GoalChoice?): List<OnboardingStep> = when (goal) {
        GoalChoice.Read -> listOf(
            OnboardingStep.Language,
            OnboardingStep.Story,
            OnboardingStep.Goal,
            OnboardingStep.Comfort,
            OnboardingStep.Ready,
        )
        else -> listOf(
            OnboardingStep.Language,
            OnboardingStep.Story,
            OnboardingStep.Goal,
            OnboardingStep.Known,
            OnboardingStep.Preset,
            OnboardingStep.Pace,
            OnboardingStep.Reminder,
            OnboardingStep.Comfort,
            OnboardingStep.Ready,
        )
    }

    /** Segments of the progress bar: the story counts once. */
    fun segmentCount(goal: GoalChoice?): Int = path(goal).size

    /** The segment that is current; a step that is not on the path (cannot happen in normal use) counts as the goal step. */
    fun segmentIndex(position: FlowPosition, goal: GoalChoice?): Int =
        path(goal).indexOf(position.step).takeIf { it >= 0 } ?: path(goal).indexOf(OnboardingStep.Goal)

    /** The position after "Next"; null on the last step. */
    fun next(position: FlowPosition, goal: GoalChoice?): FlowPosition? {
        if (position.step == OnboardingStep.Story && position.storyPage < StoryPageCount - 1) {
            return position.copy(storyPage = position.storyPage + 1)
        }
        val path = path(goal)
        val index = path.indexOf(position.step)
        if (index < 0) return FlowPosition(OnboardingStep.Goal)
        return path.getOrNull(index + 1)?.let { FlowPosition(it) }
    }

    /** The position after "Back" (the arrow and the system gesture); null on the first step: back leaves the app. */
    fun back(position: FlowPosition, goal: GoalChoice?): FlowPosition? {
        if (position.step == OnboardingStep.Story && position.storyPage > 0) {
            return position.copy(storyPage = position.storyPage - 1)
        }
        val path = path(goal)
        val index = path.indexOf(position.step)
        if (index < 0) return FlowPosition(OnboardingStep.Goal)
        val previous = path.getOrNull(index - 1) ?: return null
        // Coming back to the story lands on its last slide, the one the person has just left.
        return if (previous == OnboardingStep.Story) FlowPosition(previous, StoryPageCount - 1) else FlowPosition(previous)
    }

    /** "Skip" on a slide: straight to the goal. */
    fun skipStory(position: FlowPosition): FlowPosition =
        if (position.step == OnboardingStep.Story) FlowPosition(OnboardingStep.Goal) else position
}
