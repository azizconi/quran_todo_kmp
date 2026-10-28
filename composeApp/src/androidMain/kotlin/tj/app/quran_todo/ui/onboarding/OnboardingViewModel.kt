package tj.app.quran_todo.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.ui.settings.PreviewAyah
import tj.app.quran_todo.ui.state.UiPrefs

/**
 * Data and persistence the onboarding needs beyond the root's setters. The shared `HomeViewModel`
 * arrives through the constructor (never `koinViewModel`): its Quran text, once loaded, replaces the
 * built-in sample in the font preview, and creating it here starts the load while the person is
 * still choosing a language.
 */
internal class OnboardingViewModel(
    home: HomeViewModel,
    private val uiPrefs: UiPrefs,
) : ViewModel() {

    /** Ar-Rahman 1-4 from the loaded Quran; null until it is there. */
    val rahmanAyahs: StateFlow<List<PreviewAyah>?> = home.uiState
        .map { rahmanPreview(it.completeQuran) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), rahmanPreview(home.uiState.value.completeQuran))

    val themeFollowsSystem: StateFlow<Boolean> = uiPrefs.themeFollowsSystem

    /** "System" in the theme control; light and dark go through the root's mode setter. */
    fun setThemeFollowsSystem(value: Boolean) = uiPrefs.setThemeFollowsSystem(value)

    fun saveGoals(goals: Set<String>) = uiPrefs.setOnboardingGoals(goals)
}
