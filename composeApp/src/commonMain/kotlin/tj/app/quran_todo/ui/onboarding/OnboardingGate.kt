package tj.app.quran_todo.ui.onboarding


/** What the start of the app does about the onboarding. */
enum class OnboardingGate {
    /** The onboarding was passed: never shown again. */
    Passed,

    /**
     * Someone who used an older version (the language and the reading font were saved by it) and has not seen the onboarding v2:
     * it is not shown, and the person is marked as having passed it.
     */
    ExistingUser,

    /** A first launch, or an onboarding that was started and not finished: show it. */
    Show,
    ;

    val showsOnboarding: Boolean get() = this == Show

    companion object {
        /**
         * The decision, as a pure function (see `OnboardingGateTest`).
         *
         * - passed: do not show;
         * - not passed, but a previous version saved the language and the font, and the onboarding v2 never started: an existing
         *   user, do not show;
         * - everything else: show. A started and interrupted onboarding has saved the language (and maybe the font) too, so
         *   [v2Started] is what tells it from an old install.
         */
        fun decide(completed: Boolean, languageSaved: Boolean, fontSaved: Boolean, v2Started: Boolean): OnboardingGate = when {
            completed -> Passed
            languageSaved && fontSaved && !v2Started -> ExistingUser
            else -> Show
        }
    }
}
