package tj.app.quran_todo.ui.downloads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tj.app.quran_todo.ui.platform.KeyValueStore

/**
 * The two switches of "Downloads and storage" (both on by default) and whether the notification permission was asked for. The reciter
 * is not here: downloads follow the reciter of the sound settings ([tj.app.quran_todo.ui.state.UiPrefs.reciter]).
 */
class DownloadsPrefs(private val prefs: KeyValueStore) {
    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, true))

    /** Only on an unmetered network (Wi-Fi): `NetworkType.UNMETERED`; off: any connection. */
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    fun setWifiOnly(value: Boolean) {
        prefs.putBoolean(KEY_WIFI_ONLY, value)
        _wifiOnly.value = value
    }

    private val _withTranslation = MutableStateFlow(prefs.getBoolean(KEY_WITH_TRANSLATION, true))

    /** The translation (in the language of the app) is downloaded together with the audio. */
    val withTranslation: StateFlow<Boolean> = _withTranslation.asStateFlow()

    fun setWithTranslation(value: Boolean) {
        prefs.putBoolean(KEY_WITH_TRANSLATION, value)
        _withTranslation.value = value
    }

    /** The permission to show notifications is asked for once, at the first download. */
    var notificationPermissionAsked: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_ASKED, false)
        set(value) {
            prefs.putBoolean(KEY_PERMISSION_ASKED, value)
        }

    companion object {
        const val PREFS_NAME = "downloads_prefs"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_WITH_TRANSLATION = "with_translation"
        const val KEY_PERMISSION_ASKED = "notification_permission_asked"
    }
}
