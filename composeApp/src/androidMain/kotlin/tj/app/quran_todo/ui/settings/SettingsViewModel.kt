package tj.app.quran_todo.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.settings.AppSettings
import tj.app.quran_todo.common.settings.SettingsChangeNotifier
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.common.sync.SettingsSnapshot
import tj.app.quran_todo.presentation.home.HomeUiState
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.ui.state.HifzHintMode
import tj.app.quran_todo.ui.state.UiPrefs

/** Something the screen should tell the person with a toast. */
internal sealed interface SettingsEvent {
    data object OfflineStarted : SettingsEvent
    data object QuranNotReady : SettingsEvent
    data object BackupDone : SettingsEvent
    data object RestoreDone : SettingsEvent
    data object NoBackup : SettingsEvent
    data object BackupFailed : SettingsEvent
    data object RestoreFailed : SettingsEvent

    /** A status the shared ViewModel produced that we have no translation for. */
    data class Raw(val message: String) : SettingsEvent
}

/** The slice of [HomeUiState] the offline-download row draws. */
internal data class OfflineProgress(
    val running: Boolean,
    val done: Int,
    val total: Int,
    val status: String?,
)

/** Maps the offline slice to what the row shows. */
internal fun OfflineProgress.toUi(): OfflineUi = when {
    running -> OfflineUi.Running(done, total)
    status == "READY" -> OfflineUi.Ready
    status == "PARTIAL" -> OfflineUi.Partial
    else -> OfflineUi.Idle
}

/**
 * Turns the English status line of the shared `HomeViewModel` into an event we can localize.
 * Unknown lines are passed through as they are.
 */
internal fun classifySyncMessage(message: String): SettingsEvent = when {
    message.startsWith("Synced to", ignoreCase = true) -> SettingsEvent.BackupDone
    message.startsWith("Restored from", ignoreCase = true) -> SettingsEvent.RestoreDone
    message.startsWith("No cloud snapshot", ignoreCase = true) -> SettingsEvent.NoBackup
    message.contains("sync failed", ignoreCase = true) -> SettingsEvent.BackupFailed
    message.contains("restore failed", ignoreCase = true) -> SettingsEvent.RestoreFailed
    else -> SettingsEvent.Raw(message)
}

/**
 * State and actions of the settings screen. The shared `HomeViewModel` (offline package, backup,
 * restore) arrives through the constructor, never through `koinViewModel`, so clearing this
 * ViewModel does not touch it.
 *
 * Language, theme, palette, font and `AppSettings` are changed by the screen through the root's
 * `Local*Setter`s (so the whole UI follows at once); this class owns reader preferences, which have
 * no root state, and the Android-only `UiPrefs`.
 */
internal class SettingsViewModel(
    private val home: HomeViewModel,
    private val uiPrefs: UiPrefs,
) : ViewModel() {

    private val _readerPreferences = MutableStateFlow(SurahReaderPreferences.load())
    val readerPreferences: StateFlow<SurahReaderPreferences> = _readerPreferences.asStateFlow()

    val keepScreenOn: StateFlow<Boolean> = uiPrefs.keepScreenOn
    val hapticsEnabled: StateFlow<Boolean> = uiPrefs.hapticsEnabled
    val reduceMotion: StateFlow<Boolean> = uiPrefs.reduceMotion
    val hifzHintMode: StateFlow<HifzHintMode> = uiPrefs.hifzHintMode
    val themeFollowsSystem: StateFlow<Boolean> = uiPrefs.themeFollowsSystem

    val offline: StateFlow<OfflineProgress> = home.uiState
        .map { it.offlineProgress() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), home.uiState.value.offlineProgress())

    /** Settings that a cloud restore brought back; the screen applies them to the root and consumes them. */
    val restoredSettings: StateFlow<SettingsSnapshot?> = home.uiState
        .map { it.restoredSettings }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), home.uiState.value.restoredSettings)

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    /** True between the tap on "Backup" / "Restore" and the answer of the shared ViewModel. */
    private var awaitingSyncAnswer = false

    init {
        viewModelScope.launch {
            home.uiState
                .map { SyncSignal(it.syncStatusMessage, it.lastSyncAt, it.restoredSettings != null, it.hasCloudSnapshot) }
                .distinctUntilChanged()
                // The first value is whatever was there before this screen opened, not an answer.
                .drop(1)
                .collect { signal ->
                    if (!awaitingSyncAnswer) return@collect
                    val message = signal.message ?: return@collect
                    awaitingSyncAnswer = false
                    _events.tryEmit(classifySyncMessage(message))
                }
        }
    }

    private data class SyncSignal(
        val message: String?,
        val lastSyncAt: Long?,
        val hasRestoredSettings: Boolean,
        val hasCloudSnapshot: Boolean,
    )

    /** Applies [transform] to the reader preferences, saves them and tells the reader. */
    fun updateReaderPreferences(transform: (SurahReaderPreferences) -> SurahReaderPreferences) {
        val next = transform(_readerPreferences.value)
        if (next == _readerPreferences.value) return
        _readerPreferences.value = next
        next.save()
        SettingsChangeNotifier.notifyChanged()
    }

    fun setKeepScreenOn(value: Boolean) = uiPrefs.setKeepScreenOn(value)

    fun setHapticsEnabled(value: Boolean) = uiPrefs.setHapticsEnabled(value)

    fun setReduceMotion(value: Boolean) = uiPrefs.setReduceMotion(value)

    fun setHifzHintMode(value: HifzHintMode) = uiPrefs.setHifzHintMode(value)

    /** "System" in the theme control; picking light or dark goes through the root's mode setter. */
    fun setThemeFollowsSystem(value: Boolean) = uiPrefs.setThemeFollowsSystem(value)

    fun startOfflineDownload(language: AppLanguage) {
        if (home.uiState.value.completeQuran.isEmpty()) {
            _events.tryEmit(SettingsEvent.QuranNotReady)
            return
        }
        if (home.uiState.value.offlineDownloadRunning) return
        home.startOfflinePackage(language)
        _events.tryEmit(SettingsEvent.OfflineStarted)
    }

    /** Saves a backup of the progress together with the settings currently in force. */
    fun backup(settings: AppSettings) {
        awaitingSyncAnswer = true
        home.syncProgressToCloud(settings)
    }

    fun restore() {
        awaitingSyncAnswer = true
        home.restoreProgressFromCloud()
    }

    fun consumeRestoredSettings() = home.consumeRestoredSettings()

    private fun HomeUiState.offlineProgress() = OfflineProgress(
        running = offlineDownloadRunning,
        done = offlineDownloadDone,
        total = offlineDownloadTotal,
        status = offlineDownloadStatus,
    )
}
