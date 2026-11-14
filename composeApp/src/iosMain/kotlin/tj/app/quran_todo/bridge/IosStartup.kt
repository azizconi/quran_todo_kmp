package tj.app.quran_todo.bridge

import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.theme.ThemeStorage
import tj.app.quran_todo.ui.onboarding.OnboardingGate
import tj.app.quran_todo.ui.platform.iosPrefs
import tj.app.quran_todo.ui.state.OnboardingFlags
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.whatsnew.WhatsNewGate

/**
 * The start-of-app decisions for SwiftUI (the iOS twin of `OnboardingGateAndroid` / `OnboardingStartup`): reads the stored flags
 * straight from the preferences file (no Koin needed, so it can run before the first frame) and decides. Call once per launch.
 */
object IosStartup {
    private val prefs get() = iosPrefs(UiPrefs.PREFS_NAME)

    fun flags(): OnboardingFlags = OnboardingFlags(
        completed = prefs.getBoolean(UiPrefs.KEY_ONBOARDING_COMPLETED, false),
        v2Started = prefs.getBoolean(UiPrefs.KEY_ONBOARDING_V2_STARTED, false),
        whatsNewShown = prefs.getBoolean(UiPrefs.KEY_WHATS_NEW_SHOWN, false),
    )

    /** The gate; an existing user (an older version saved language and font) is written down as having passed it on the spot. */
    fun resolveGate(): OnboardingGate {
        val flags = flags()
        val gate = OnboardingGate.decide(
            completed = flags.completed,
            languageSaved = LanguageStorage.getSavedLanguage() != null,
            fontSaved = ThemeStorage.getSavedReadingFontStyle() != null,
            v2Started = flags.v2Started,
        )
        if (gate == OnboardingGate.ExistingUser) prefs.putBoolean(UiPrefs.KEY_ONBOARDING_COMPLETED, true)
        return gate
    }

    /** Whether the one-time "What's new" sheet is due: read after [resolveGate] (it marks an existing user as passed). */
    fun whatsNewPending(): Boolean = WhatsNewGate.pending(flags())
}
