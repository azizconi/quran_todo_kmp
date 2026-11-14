package tj.app.quran_todo.ui.platform

import org.koin.mp.KoinPlatform
import tj.app.quran_todo.common.audio.Reciters
import tj.app.quran_todo.ui.downloads.DownloadKind
import tj.app.quran_todo.ui.downloads.DownloadRequest
import tj.app.quran_todo.ui.downloads.DownloadsService
import tj.app.quran_todo.ui.downloads.Segment
import tj.app.quran_todo.ui.mushaf.data.MushafPageProvider
import tj.app.quran_todo.ui.progress.report.ReportExporter
import tj.app.quran_todo.ui.recording.VoiceService
import tj.app.quran_todo.ui.settings.TransferFiles
import tj.app.quran_todo.data.bundled.QuranFacts

/** Doors for the debug screen of the services (`Features/Debug/ServicesDebugView.swift`); the screens use the view models instead. */
object IosServicesDebug {
    private val koin get() = KoinPlatform.getKoin()

    fun downloads(): DownloadsService = koin.get()
    fun voice(): VoiceService = koin.get()
    fun mushaf(): MushafPageProvider = koin.get()
    fun transfer(): TransferFiles = koin.get()
    fun reports(): ReportExporter = koin.get()

    /** A request for the whole [surah] with the audio of the reciter of the app (without a translation). */
    fun surahRequest(surah: Int): DownloadRequest = DownloadRequest(
        id = DownloadRequest.idOf(DownloadKind.SURAH, surah, Reciters.default.id),
        kind = DownloadKind.SURAH,
        title = "Surah $surah",
        reciterId = Reciters.default.id,
        segments = listOf(Segment(surah, 1, QuranFacts.ayahCount(surah))),
    )
}
