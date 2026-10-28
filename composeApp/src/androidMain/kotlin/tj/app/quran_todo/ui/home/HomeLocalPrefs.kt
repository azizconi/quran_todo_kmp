package tj.app.quran_todo.ui.home

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Small flags that belong to the home screen only (so they do not live in `UiPrefs`). Registered as
 * a Koin singleton in `homeUiModule`.
 */
class HomeLocalPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _hifzInviteDismissed = MutableStateFlow(prefs.getBoolean(KEY_HIFZ_INVITE_DISMISSED, false))

    /** The person closed the "Mark a surah as memorizing" invitation in the Today block. */
    val hifzInviteDismissed: StateFlow<Boolean> = _hifzInviteDismissed.asStateFlow()

    fun dismissHifzInvite() {
        prefs.edit().putBoolean(KEY_HIFZ_INVITE_DISMISSED, true).apply()
        _hifzInviteDismissed.value = true
    }

    private companion object {
        const val PREFS_NAME = "quran_hifz_home_prefs"
        const val KEY_HIFZ_INVITE_DISMISSED = "hifz_invite_dismissed"
    }
}
