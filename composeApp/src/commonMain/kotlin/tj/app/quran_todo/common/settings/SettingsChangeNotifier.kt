package tj.app.quran_todo.common.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Signals that a platform-native settings screen changed persisted app settings. */
object SettingsChangeNotifier {
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()

    fun notifyChanged() {
        _revision.value += 1
    }
}
