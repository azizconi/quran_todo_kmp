package tj.app.quran_todo.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.audio.Reciter
import tj.app.quran_todo.common.i18n.AppLanguage
import tj.app.quran_todo.common.platform.AndroidContextHolder
import tj.app.quran_todo.common.settings.SettingsChangeNotifier
import tj.app.quran_todo.common.settings.SurahReaderPreferences
import tj.app.quran_todo.hifz.HifzRepository
import tj.app.quran_todo.hifz.backup.BackupCodec
import tj.app.quran_todo.hifz.backup.BackupError
import tj.app.quran_todo.hifz.backup.BackupFile
import tj.app.quran_todo.hifz.backup.BackupFormat
import tj.app.quran_todo.hifz.backup.BackupParse
import tj.app.quran_todo.hifz.backup.BackupPreview
import tj.app.quran_todo.hifz.backup.BackupSettings
import tj.app.quran_todo.hifz.backup.VerifiedBackup
import tj.app.quran_todo.hifz.backup.preview
import tj.app.quran_todo.presentation.home.HomeUiState
import tj.app.quran_todo.presentation.home.HomeViewModel
import tj.app.quran_todo.ui.reminder.ReminderController
import tj.app.quran_todo.ui.state.HifzHintMode
import tj.app.quran_todo.ui.state.ReaderLayout
import tj.app.quran_todo.ui.state.UiPrefs

/** Something the screen should tell the person with a toast or an Android screen. */
internal sealed interface SettingsEvent {
    data object OfflineStarted : SettingsEvent
    data object QuranNotReady : SettingsEvent

    /** The copy is in the file the person chose. */
    data object ExportSaved : SettingsEvent
    data object ExportFailed : SettingsEvent

    /** The copy is written to a temporary file: the screen opens the system share sheet for [uri]. */
    data class ShareReady(val uri: Uri, val fileName: String) : SettingsEvent

    /** The file the person picked is not accepted ([problem] says why); nothing was changed. */
    data class ImportRejected(val problem: ImportProblem) : SettingsEvent

    /** The data of the phone was replaced by the file. */
    data object ImportDone : SettingsEvent

    /** The replacement failed in the database; it was rolled back and the previous data is as it was. */
    data object ImportFailed : SettingsEvent
}

/** Why a picked file is not accepted; one toast text each. */
internal enum class ImportProblem { NotABackup, NewerVersion, Corrupted, OutOfRange, Unreadable }

internal fun BackupError.toProblem(): ImportProblem = when (this) {
    BackupError.NotABackup -> ImportProblem.NotABackup
    is BackupError.NewerVersion -> ImportProblem.NewerVersion
    is BackupError.Corrupted -> ImportProblem.Corrupted
    is BackupError.OutOfRange -> ImportProblem.OutOfRange
}

/** A checked file waiting for the person's "Replace": the dialog shows its [preview]; [replacing] while the transaction runs. */
internal class ImportPrompt(
    val backup: VerifiedBackup,
    val preview: BackupPreview,
    val replacing: Boolean = false,
) {
    fun copy(replacing: Boolean) = ImportPrompt(backup, preview, replacing)
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
 * State and actions of the settings screen. The shared `HomeViewModel` (offline package) arrives through the constructor,
 * never through `koinViewModel`, so clearing this ViewModel does not touch it.
 *
 * Language, theme, palette, font and `AppSettings` are changed by the screen through the root's
 * `Local*Setter`s (so the whole UI follows at once); this class owns reader preferences, which have
 * no root state, and the Android-only `UiPrefs`.
 *
 * "Transfer and backup" (task B5): the copy is made from [HifzRepository.exportBackupData] and the settings in force, checked
 * by [BackupCodec.verify] and only then written; an import is read and checked off the main thread, shown to the person as an
 * [ImportPrompt] and replaces the data in one transaction ([HifzRepository.replaceWithBackup]). The settings of the file wait
 * in [pendingSettings] until the screen has applied them through the root's setters.
 */
internal class SettingsViewModel(
    private val home: HomeViewModel,
    private val uiPrefs: UiPrefs,
    private val hifz: HifzRepository,
    private val io: TransferIo,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val _readerPreferences = MutableStateFlow(SurahReaderPreferences.load())
    val readerPreferences: StateFlow<SurahReaderPreferences> = _readerPreferences.asStateFlow()

    val keepScreenOn: StateFlow<Boolean> = uiPrefs.keepScreenOn
    val hapticsEnabled: StateFlow<Boolean> = uiPrefs.hapticsEnabled
    val reduceMotion: StateFlow<Boolean> = uiPrefs.reduceMotion
    val hifzHintMode: StateFlow<HifzHintMode> = uiPrefs.hifzHintMode
    val themeFollowsSystem: StateFlow<Boolean> = uiPrefs.themeFollowsSystem
    val analyticsEnabled: StateFlow<Boolean> = uiPrefs.analyticsEnabled

    /** When a copy was last saved or sent (epoch milliseconds); null if never. */
    val lastExportAt: StateFlow<Long?> = uiPrefs.lastExportAt

    val offline: StateFlow<OfflineProgress> = home.uiState
        .map { it.offlineProgress() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), home.uiState.value.offlineProgress())

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    private val _importPrompt = MutableStateFlow<ImportPrompt?>(null)

    /** The file waiting for "Replace" (the dialog is shown while this is not null). */
    val importPrompt: StateFlow<ImportPrompt?> = _importPrompt.asStateFlow()

    private val _pendingSettings = MutableStateFlow<BackupSettings?>(null)

    /** Settings of an imported file that the screen has not applied yet. */
    val pendingSettings: StateFlow<BackupSettings?> = _pendingSettings.asStateFlow()

    private val _transferBusy = MutableStateFlow(false)

    /** A copy is being made or a file is being read: the transfer rows ignore taps. */
    val transferBusy: StateFlow<Boolean> = _transferBusy.asStateFlow()

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

    fun setLessonAyahRepeats(value: Int) = uiPrefs.setLessonAyahRepeats(value)

    fun setLessonPassageRepeats(value: Int) = uiPrefs.setLessonPassageRepeats(value)

    /**
     * The reminder time and switch of an imported copy. The alarm is planned (or cancelled) at once; the notification permission is
     * not asked here, so a reminder that comes in on, on a phone that never allowed notifications, stays quiet and the reminder
     * section of the screen shows why.
     */
    fun setReminderMinuteOfDay(value: Int) =
        ReminderController(AndroidContextHolder.context.applicationContext, uiPrefs).setMinuteOfDay(value, track = false)

    fun setReminderEnabled(value: Boolean) =
        ReminderController(AndroidContextHolder.context.applicationContext, uiPrefs).setEnabled(value, track = false)

    /** "System" in the theme control; picking light or dark goes through the root's mode setter. */
    fun setThemeFollowsSystem(value: Boolean) = uiPrefs.setThemeFollowsSystem(value)

    /** Stores the choice and applies it to Firebase right away (see [UiPrefs.setAnalyticsEnabled]). */
    fun setAnalyticsEnabled(value: Boolean) = uiPrefs.setAnalyticsEnabled(value)

    fun setReaderLayout(value: ReaderLayout) = uiPrefs.setReaderLayout(value)

    fun setReciter(value: Reciter) = uiPrefs.setReciter(value)

    fun startOfflineDownload(language: AppLanguage) {
        if (home.uiState.value.completeQuran.isEmpty()) {
            _events.tryEmit(SettingsEvent.QuranNotReady)
            return
        }
        if (home.uiState.value.offlineDownloadRunning) return
        home.startOfflinePackage(language)
        _events.tryEmit(SettingsEvent.OfflineStarted)
    }

    // ------------------------------------------------------------------------------------------------------------
    // Transfer and backup
    // ------------------------------------------------------------------------------------------------------------

    /** The system picker returned the document the person created for the copy ([uri] is null when they cancelled). */
    fun exportToFile(uri: Uri?) {
        if (uri == null) return
        runTransfer {
            try {
                val made = makeBackup()
                io.writeText(uri, made.text)
                uiPrefs.setLastExportAt(made.exportedAt)
                // The temporary file of an earlier "Send a copy" is not needed any more.
                runCatching { io.clearShareFiles() }
                AppTelemetry.logEvent("backup_exported", mapOf("via" to "file"))
                _events.tryEmit(SettingsEvent.ExportSaved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AppTelemetry.logError(throwable = error, context = "backup_export_failed", params = mapOf("via" to "file"))
                _events.tryEmit(SettingsEvent.ExportFailed)
            }
        }
    }

    /** Makes the copy in a temporary file of the cache and asks the screen to open the share sheet for it. */
    fun exportForShare() {
        runTransfer {
            try {
                val made = makeBackup()
                val uri = io.createShareFile(made.fileName, made.text)
                uiPrefs.setLastExportAt(made.exportedAt)
                AppTelemetry.logEvent("backup_exported", mapOf("via" to "share"))
                _events.tryEmit(SettingsEvent.ShareReady(uri, made.fileName))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AppTelemetry.logError(throwable = error, context = "backup_export_failed", params = mapOf("via" to "share"))
                _events.tryEmit(SettingsEvent.ExportFailed)
            }
        }
    }

    private class MadeBackup(val text: String, val fileName: String, val exportedAt: Long)

    /** The JSON of the copy of now. A copy that the app itself would refuse to import is never written. */
    private suspend fun makeBackup(): MadeBackup {
        val data = hifz.exportBackupData()
        val exportedAt = clock()
        val file = BackupFile(
            format = BackupFormat.NAME,
            formatVersion = BackupFormat.VERSION,
            appVersion = io.appVersion(),
            exportedAt = exportedAt,
            dbVersion = BackupFormat.DB_VERSION,
            hifz = data.hifz,
            notes = data.notes,
            settings = TransferSettings.collect(uiPrefs),
        )
        val text = withContext(Dispatchers.Default) {
            when (val parse = BackupCodec.verify(file)) {
                is BackupParse.Ok -> BackupCodec.encode(parse.backup)
                is BackupParse.Failed -> error("the copy does not verify: ${parse.error}")
            }
        }
        return MadeBackup(text, BackupFormat.fileName(exportedAt), exportedAt)
    }

    /** The system picker returned the file the person chose to restore from ([uri] is null when they cancelled). */
    fun importFrom(uri: Uri?) {
        if (uri == null) return
        runTransfer {
            try {
                when (val read = io.readText(uri)) {
                    ReadOutcome.Unreadable -> _events.tryEmit(SettingsEvent.ImportRejected(ImportProblem.Unreadable))
                    // A file this big is not a copy of ours.
                    ReadOutcome.TooLarge -> _events.tryEmit(SettingsEvent.ImportRejected(ImportProblem.NotABackup))
                    is ReadOutcome.Text -> when (val parse = withContext(Dispatchers.Default) { BackupCodec.decode(read.text) }) {
                        is BackupParse.Failed -> _events.tryEmit(SettingsEvent.ImportRejected(parse.error.toProblem()))
                        is BackupParse.Ok -> _importPrompt.value = ImportPrompt(parse.backup, parse.backup.preview())
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AppTelemetry.logError(throwable = error, context = "backup_import_read_failed")
                _events.tryEmit(SettingsEvent.ImportRejected(ImportProblem.Unreadable))
            }
        }
    }

    /** "Replace": one transaction. On an error the previous data is as it was, and the settings are not touched. */
    fun confirmReplace() {
        val prompt = _importPrompt.value ?: return
        if (prompt.replacing) return
        _importPrompt.value = prompt.copy(replacing = true)
        viewModelScope.launch {
            try {
                hifz.replaceWithBackup(prompt.backup)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AppTelemetry.logError(throwable = error, context = "backup_import_failed")
                _importPrompt.value = null
                _events.tryEmit(SettingsEvent.ImportFailed)
                return@launch
            }
            AppTelemetry.logEvent("backup_imported")
            _importPrompt.value = null
            _pendingSettings.value = prompt.backup.settings
            _events.tryEmit(SettingsEvent.ImportDone)
        }
    }

    /** "Cancel" in the dialog. Ignored while the replacement runs. */
    fun dismissImportPrompt() {
        if (_importPrompt.value?.replacing == true) return
        _importPrompt.value = null
    }

    /** The screen applied [pendingSettings] to the app root and the preferences. */
    fun consumePendingSettings() {
        _pendingSettings.value = null
    }

    /** One transfer action at a time: a second tap while the first runs is ignored. */
    private fun runTransfer(block: suspend () -> Unit) {
        if (_transferBusy.value) return
        _transferBusy.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                _transferBusy.value = false
            }
        }
    }

    private fun HomeUiState.offlineProgress() = OfflineProgress(
        running = offlineDownloadRunning,
        done = offlineDownloadDone,
        total = offlineDownloadTotal,
        status = offlineDownloadStatus,
    )
}
