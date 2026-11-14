package tj.app.quran_todo.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.bind
import org.koin.dsl.module
import tj.app.quran_todo.common.audio.ReaderAudioSession
import tj.app.quran_todo.khatm.HijriConverter
import tj.app.quran_todo.ui.downloads.DownloadsPrefs
import tj.app.quran_todo.ui.downloads.DownloadsService
import tj.app.quran_todo.ui.downloads.ListeningCache
import tj.app.quran_todo.ui.downloads.RecordingsStorage
import tj.app.quran_todo.ui.downloads.StorageSpace
import tj.app.quran_todo.ui.mushaf.data.MushafPageProvider
import tj.app.quran_todo.ui.platform.IosDownloadsService
import tj.app.quran_todo.ui.platform.IosHijriConverter
import tj.app.quran_todo.ui.platform.IosListeningCache
import tj.app.quran_todo.ui.platform.IosMushafPageProvider
import tj.app.quran_todo.ui.platform.IosVoiceService
import tj.app.quran_todo.ui.platform.LocaleApplier
import tj.app.quran_todo.ui.platform.NoLocaleApplier
import tj.app.quran_todo.ui.recording.VoiceService
import tj.app.quran_todo.ui.store.NoReviewPrompt
import tj.app.quran_todo.ui.store.ReviewPrompt
import tj.app.quran_todo.ui.widget.NoWidgetRefresher
import tj.app.quran_todo.ui.widget.WidgetRefresher
import tj.app.quran_todo.ui.platform.IosReminderPlanner
import tj.app.quran_todo.ui.platform.IosStorageSpace
import tj.app.quran_todo.ui.platform.IosTafsirChapters
import tj.app.quran_todo.ui.platform.IosTransferFiles
import tj.app.quran_todo.ui.platform.IosWordTimingsLoader
import tj.app.quran_todo.ui.platform.iosPrefs
import tj.app.quran_todo.ui.playback.PlaybackController
import tj.app.quran_todo.ui.reminder.ReminderPlanner
import tj.app.quran_todo.ui.settings.TransferFiles
import tj.app.quran_todo.ui.state.UiPrefs
import tj.app.quran_todo.ui.tafsir.TafsirChapters
import tj.app.quran_todo.ui.words.WordTimingsLoader

/**
 * The iOS bindings of the platform interfaces that `sharedUiModule` needs. The ones marked TODO(IOS-S2) in `IosPlatformStubs.kt` are
 * minimal or no-op until the services of the iOS app are written; replace a binding here (or in `KoinInitializer`) to switch it.
 */
val iosUiPlatformModule = module {
    single { UiPrefs(iosPrefs(UiPrefs.PREFS_NAME)) }
    single {
        // The app audio on the player that exists on iOS already (AudioPlayer). TODO(IOS-S2): the AVPlayer output with Now Playing,
        // remote commands, interruptions and background audio (ReaderAudioOutput + OutputExtras + PlaybackServiceLink).
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        PlaybackController(
            session = ReaderAudioSession(scope),
            timerScope = scope,
            reciter = get<UiPrefs>().reciter,
        )
    }
    single { DownloadsPrefs(iosPrefs(DownloadsPrefs.PREFS_NAME)) }
    single<DownloadsService> { IosDownloadsService(get()) }
    single<TafsirChapters> { IosTafsirChapters }
    single<MushafPageProvider> { IosMushafPageProvider }
    single<WordTimingsLoader> { IosWordTimingsLoader }
    single<TransferFiles> { IosTransferFiles }
    single<ReminderPlanner> { IosReminderPlanner }
    single<StorageSpace> { IosStorageSpace }
    single<ListeningCache> { IosListeningCache }
    single<VoiceService> { IosVoiceService }
    single<RecordingsStorage> { get<VoiceService>() }
    // TODO(IOS-S2): AppStore.requestReview, WidgetCenter reload, nothing to apply for the locale.
    single<ReviewPrompt> { NoReviewPrompt }
    single<WidgetRefresher> { NoWidgetRefresher }
    single<LocaleApplier> { NoLocaleApplier }
    single<HijriConverter> { IosHijriConverter }
}
