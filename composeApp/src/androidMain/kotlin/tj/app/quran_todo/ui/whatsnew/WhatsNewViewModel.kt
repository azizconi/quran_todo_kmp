package tj.app.quran_todo.ui.whatsnew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.ui.state.OnboardingFlags
import tj.app.quran_todo.ui.state.UiPrefs

/** The words of the `whats_new_action` event: what the person did with the sheet. */
internal enum class WhatsNewAction(val analyticsName: String) {
    Lesson("lesson"),
    Plan("plan"),
    Later("later"),
}

/** Who sees the sheet, as a pure rule. */
internal object WhatsNewGate {
    /**
     * Someone who updated from an older version: the onboarding is passed (the start of this version marked it) without the
     * onboarding v2 ever having been shown here, and the sheet was not shown yet. A new person who went through the v2 has
     * `v2Started` and never sees it.
     */
    fun pending(flags: OnboardingFlags): Boolean = flags.completed && !flags.v2Started && !flags.whatsNewShown
}

/**
 * Whether a plan exists decides the button of the sheet: with a plan it opens the lesson, without one it opens "My plan". The
 * migration of the older version's statuses makes the plan in the background right after the update, so this follows the
 * repository instead of reading it once.
 */
internal class WhatsNewViewModel(
    hifz: HifzRepository,
    private val uiPrefs: UiPrefs,
) : ViewModel() {
    /** Null until the repository has answered. */
    val hasPlan: StateFlow<Boolean?> = hifz.plan()
        .map<_, Boolean?> { it != null }
        .catch { emit(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), null)

    /** The sheet is on the screen: it is never shown again (the flag is written at once, so a killed process does not repeat it). */
    fun shown() {
        uiPrefs.setWhatsNewShown()
        AppTelemetry.logEvent("whats_new_shown")
    }

    fun acted(action: WhatsNewAction) {
        AppTelemetry.logEvent("whats_new_action", mapOf("action" to action.analyticsName))
    }

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}
