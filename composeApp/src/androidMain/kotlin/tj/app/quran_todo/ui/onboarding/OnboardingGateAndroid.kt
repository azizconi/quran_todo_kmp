package tj.app.quran_todo.ui.onboarding

import android.content.Context
import tj.app.quran_todo.common.i18n.LanguageStorage
import tj.app.quran_todo.common.platform.AndroidContextHolder
import tj.app.quran_todo.common.theme.ThemeStorage
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.state.readOnboarding
import tj.app.quran_todo.ui.state.writeOnboardingCompleted

/**
 * Reads the stored state (no Koin, no Compose) and decides. An existing user is written down as having passed it on the
 * spot, so the answer does not change later in the same launch (the "What's new" sheet reads [OnboardingFlags.v2Started]
 * to tell this person from someone who just finished the onboarding).
 */
internal fun OnboardingGate.Companion.resolve(context: Context = AndroidContextHolder.context): OnboardingGate {
    val flags = UiPrefs.readOnboarding(context)
    val gate = decide(
        completed = flags.completed,
        languageSaved = LanguageStorage.getSavedLanguage() != null,
        fontSaved = ThemeStorage.getSavedReadingFontStyle() != null,
        v2Started = flags.v2Started,
    )
    if (gate == OnboardingGate.ExistingUser) UiPrefs.writeOnboardingCompleted(context)
    return gate
}
