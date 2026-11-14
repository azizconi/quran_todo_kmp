package tj.app.quran_todo.di

import kotlinx.coroutines.flow.map
import org.koin.dsl.bind
import org.koin.dsl.module
import tj.app.quran_todo.common.audio.AudioCacheStorage
import tj.app.quran_todo.common.audio.DownloadStorage
import tj.app.quran_todo.ui.downloads.DownloadsPrefs
import tj.app.quran_todo.ui.downloads.DownloadsService
import tj.app.quran_todo.ui.downloads.IosDownloadsManager
import tj.app.quran_todo.ui.downloads.ListeningCache
import tj.app.quran_todo.ui.downloads.RecordingsStorage
import tj.app.quran_todo.ui.downloads.StorageSpace
import tj.app.quran_todo.ui.mushaf.data.MushafPageProvider
import tj.app.quran_todo.ui.mushaf.data.MushafPageStore
import tj.app.quran_todo.ui.platform.IosLocaleApplier
import tj.app.quran_todo.ui.platform.IosReportExporter
import tj.app.quran_todo.ui.platform.IosReviewPrompt
import tj.app.quran_todo.ui.platform.IosStorageSpace
import tj.app.quran_todo.ui.platform.IosWidgetRefresher
import tj.app.quran_todo.ui.platform.LocaleApplier
import tj.app.quran_todo.ui.platform.iosPrefs
import tj.app.quran_todo.ui.playback.PlaybackController
import tj.app.quran_todo.ui.progress.report.ReportExporter
import tj.app.quran_todo.ui.recording.IosVoiceService
import tj.app.quran_todo.ui.recording.RecordingStore
import tj.app.quran_todo.ui.recording.VoiceService
import tj.app.quran_todo.ui.reminder.IosReminderPlanner
import tj.app.quran_todo.ui.reminder.ReminderPlanner
import tj.app.quran_todo.ui.store.ReviewPrompt
import tj.app.quran_todo.ui.tafsir.TafsirChapters
import tj.app.quran_todo.ui.tafsir.TafsirStore
import tj.app.quran_todo.ui.widget.WidgetRefresher

/**
 * The real iOS services of task IOS-S2: downloads, the stores of mushaf pages and commentaries, the voice recorder, reminders, the
 * widget snapshot, the review gate, locale, report export. Loaded after [iosUiPlatformModule] by `InitKoinIos`.
 */
val iosServicesModule = module {
    single { DownloadsPrefs(iosPrefs(DownloadsPrefs.PREFS_NAME)) }
    single { IosDownloadsManager.create(get()) } bind DownloadsService::class
    single<MushafPageProvider> { MushafPageStore.shared }
    single<TafsirChapters> { TafsirStore.shared }
    single<StorageSpace> { IosStorageSpace }
    single<ListeningCache> { AudioCacheStorage.shared }
    single { RecordingStore.shared }
    single {
        val playback = get<PlaybackController>()
        IosVoiceService(
            store = get(),
            silenceReciter = { playback.stop() },
            reciterPlaying = playback.state.map { it.isPlaying },
        )
    } bind VoiceService::class
    single<RecordingsStorage> { get<VoiceService>() }
    single { IosReminderPlanner() } bind ReminderPlanner::class
    single<ReviewPrompt> { IosReviewPrompt() }
    single<WidgetRefresher> { IosWidgetRefresher(get()) }
    single<LocaleApplier> { IosLocaleApplier }
    single<ReportExporter> { IosReportExporter() }
}
