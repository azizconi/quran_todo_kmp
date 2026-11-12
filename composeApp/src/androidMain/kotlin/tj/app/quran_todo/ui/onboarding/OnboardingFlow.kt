package tj.app.quran_todo.ui.onboarding

/**
 * The steps of the onboarding (docs/redesign/UX_PLAN.md, U1): four screens. The "read and listen" exit is a link on the preset
 * step, not a step of its own.
 */
internal enum class OnboardingStep {
    Language,
    Preset,
    Known,
    Ready,
}

/** What the person wants from the app: "Memorize" is the main path, "Read" is the link under the preset cards. */
internal enum class GoalChoice(val analyticsName: String) {
    Memorize("memorize"),
    Read("read"),
}

/** Where the person is. */
internal data class FlowPosition(val step: OnboardingStep) {
    /** The name the `onboarding_step` event carries: one of the fixed list, never free text. */
    val analyticsName: String
        get() = when (step) {
            OnboardingStep.Language -> "language"
            OnboardingStep.Preset -> "preset"
            OnboardingStep.Known -> "known"
            OnboardingStep.Ready -> "ready"
        }

    companion object {
        val First = FlowPosition(OnboardingStep.Language)
    }
}

/** The step machine, without Compose: one straight path of four steps. */
internal object OnboardingFlow {
    val path: List<OnboardingStep> = listOf(
        OnboardingStep.Language,
        OnboardingStep.Preset,
        OnboardingStep.Known,
        OnboardingStep.Ready,
    )

    /** Segments of the progress bar. */
    val segmentCount: Int get() = path.size

    fun segmentIndex(position: FlowPosition): Int = path.indexOf(position.step).coerceAtLeast(0)

    /** The position after "Next"; null on the last step. */
    fun next(position: FlowPosition): FlowPosition? = path.getOrNull(segmentIndex(position) + 1)?.let { FlowPosition(it) }

    /** The position after "Back" (the arrow and the system gesture); null on the first step: back leaves the app. */
    fun back(position: FlowPosition): FlowPosition? = path.getOrNull(segmentIndex(position) - 1)?.let { FlowPosition(it) }
}
