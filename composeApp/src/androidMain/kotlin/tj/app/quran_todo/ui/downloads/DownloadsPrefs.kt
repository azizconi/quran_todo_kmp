package tj.app.quran_todo.ui.downloads

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The two switches of "Downloads and storage" (both on by default) and whether the notification permission was asked for. The reciter
 * is not here: downloads follow the reciter of the sound settings ([tj.app.quran_todo.ui.state.UiPrefs.reciter]).
 */
internal class DownloadsPrefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, true))

    /** Only on an unmetered network (Wi-Fi): `NetworkType.UNMETERED`; off: any connection. */
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    fun setWifiOnly(value: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
        _wifiOnly.value = value
    }

    private val _withTranslation = MutableStateFlow(prefs.getBoolean(KEY_WITH_TRANSLATION, true))

    /** The translation (in the language of the app) is downloaded together with the audio. */
    val withTranslation: StateFlow<Boolean> = _withTranslation.asStateFlow()

    fun setWithTranslation(value: Boolean) {
        prefs.edit().putBoolean(KEY_WITH_TRANSLATION, value).apply()
        _withTranslation.value = value
    }

    /** The permission to show notifications is asked for once, at the first download. */
    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PERMISSION_ASKED, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "downloads_prefs"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_WITH_TRANSLATION = "with_translation"
        const val KEY_PERMISSION_ASKED = "notification_permission_asked"
    }
}
