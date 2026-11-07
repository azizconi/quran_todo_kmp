package tj.app.quran_todo.ui.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Who sees the onboarding v2: nobody who passed it, and nobody who updated from a version that saved the language and the font. */
class OnboardingGateTest {
    private fun decide(completed: Boolean = false, language: Boolean = false, font: Boolean = false, started: Boolean = false) =
        OnboardingGate.decide(completed, language, font, started)

    @Test
    fun aCleanInstallSeesTheOnboarding() {
        assertEquals(OnboardingGate.Show, decide())
        assertTrue(decide().showsOnboarding)
    }

    @Test
    fun aPassedOnboardingIsNeverShownAgain() {
        assertEquals(OnboardingGate.Passed, decide(completed = true))
        assertEquals(OnboardingGate.Passed, decide(completed = true, language = true, font = true, started = true))
        assertFalse(decide(completed = true).showsOnboarding)
    }

    @Test
    fun anUpdateFromAnOlderVersionIsAnExistingUserAndSkipsIt() {
        val gate = decide(language = true, font = true)
        assertEquals(OnboardingGate.ExistingUser, gate)
        assertFalse(gate.showsOnboarding)
    }

    @Test
    fun aVersionThatSavedOnlyTheLanguageOrOnlyTheFontIsNotAnExistingUser() {
        // The old onboarding saved the language at its first step and the font at its second: stopping in between is a new person.
        assertEquals(OnboardingGate.Show, decide(language = true))
        assertEquals(OnboardingGate.Show, decide(font = true))
    }

    @Test
    fun aStartedAndInterruptedOnboardingContinuesEvenWithTheLanguageAndTheFontSaved() {
        // The v2 saves the language at once and the font at the "reading" step; killed after that it must not be taken for an update.
        assertEquals(OnboardingGate.Show, decide(language = true, font = true, started = true))
        assertEquals(OnboardingGate.Show, decide(language = true, started = true))
        assertEquals(OnboardingGate.Show, decide(started = true))
    }
}
