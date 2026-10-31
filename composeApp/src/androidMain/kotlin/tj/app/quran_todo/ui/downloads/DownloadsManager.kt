package tj.app.quran_todo.ui.downloads

import android.content.Context
import kotlinx.coroutines.flow.Flow
import org.koin.mp.KoinPlatform
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.audio.AudioFetch
import tj.app.quran_todo.common.audio.DownloadStorage
import tj.app.quran_todo.common.audio.httpAudioFetch
import tj.app.quran_todo.common.audio.timing.WordTimingsStore
import tj.app.quran_todo.domain.use_case.GetAyahTranslationsUseCase
import tj.app.quran_todo.ui.mushaf.data.MushafPageStore
import java.io.File

/**
 * The one object the screen, the worker and the notification button share: the persistent queue, the downloaded files, the two
 * switches, the scheduler of the work. It is a Koin single and has no screen in it.
 *
 * "Resume after a restart" needs nothing here: the queue is a file, the unique work is persisted by WorkManager, and the worker
 * reads the queue when it starts.
 */
internal class DownloadsManager(
    val queue: DownloadQueue,
    val storage: DownloadStorage,
    val prefs: DownloadsPrefs,
    private val scheduler: DownloadScheduler,
    private val translations: () -> TranslationSource,
    private val fetch: AudioFetch = ::httpAudioFetch,
    private val timings: () -> TimingsSource = { TimingsSource { recitationId, surah -> WordTimingsStore.shared.pin(recitationId, surah) } },
    /** The pages of the mushaf that are pinned next to the audio ("Mushaf pages", task F3a). */
    val pages: MushafPageStore = MushafPageStore.shared,
) {
    /** What the work is doing: running, waiting for the network, or not scheduled. */
    val phase: Flow<WorkPhase> get() = scheduler.phase

    /**
     * Adds [request] to the queue and makes sure the work runs; a request that is queued already is not added twice, one that failed
     * is tried again. Asking for a download while the queue is paused takes the pause off: the person wants this one now.
     * [estimatedBytes] only goes into the analytics bucket.
     */
    fun enqueue(request: DownloadRequest, estimatedBytes: Long): Boolean {
        val added = queue.enqueue(request)
        if (added) {
            queue.resume()
            AppTelemetry.logEvent(
                "download_started",
                mapOf("kind" to request.kind.analyticsName, "size_mb_bucket" to sizeMbBucket(estimatedBytes)),
            )
            schedule()
        }
        return added
    }

    fun pauseAll() {
        // The worker sees the flag before the next file and ends; nothing is lost.
        queue.pause()
    }

    fun resume() {
        queue.resume()
        schedule()
    }

    /** Takes one request out of the queue; the work stops when nothing is left. */
    fun cancel(requestId: String) {
        queue.cancel(requestId)
        if (queue.state.value.requests.isEmpty()) scheduler.cancel()
    }

    /** "Cancel": the queue is empty, the work is stopped, what was downloaded stays. */
    fun cancelAll() {
        queue.cancelAll()
        scheduler.cancel()
        DownloadNotifications.dismiss()
    }

    /** "Retry": the failed requests are taken again. */
    fun retry() {
        if (queue.retryFailed()) schedule()
    }

    /** "Wi-Fi only" was switched: a waiting work gets the new constraint. */
    fun onNetworkChoiceChanged() {
        if (queue.state.value.hasWork) schedule()
    }

    /** Deletes the downloaded files of [keys]; returns the bytes that were freed. */
    fun delete(keys: Collection<String>): Long {
        val freed = storage.delete(keys)
        AppTelemetry.logEvent("downloads_deleted")
        return freed
    }

    /** Deletes the pinned copies of mushaf [pageNumbers]; returns the bytes that were freed. */
    fun deletePages(pageNumbers: Collection<Int>): Long {
        val freed = pages.deletePinned(pageNumbers)
        AppTelemetry.logEvent("downloads_deleted")
        return freed
    }

    /** "Delete all downloads": every downloaded file goes (the audio, the timings and the pinned pages), the queue stays as it is. */
    fun deleteAll(): Long {
        // The pinned pages live in the folder of the downloads: the storage deletes their files with the rest; the store is told.
        val freed = storage.deleteAll()
        pages.changed()
        AppTelemetry.logEvent("downloads_deleted")
        return freed
    }

    /** Called by the worker. */
    suspend fun run(finalAttempt: Boolean, onProgress: (DownloadProgress) -> Unit): RunOutcome =
        DownloadRunner(queue, storage, fetch, translations(), onProgress, timings = timings(), pages = pages).run(finalAttempt)

    private fun schedule() {
        scheduler.schedule(wifiOnly = prefs.wifiOnly.value)
    }

    companion object {
        fun create(context: Context): DownloadsManager {
            val app = context.applicationContext
            val queue = DownloadQueue(
                store = FileQueueStore(File(File(app.noBackupFilesDir, "downloads_state"), "queue.json")),
                onFinished = { _, result ->
                    AppTelemetry.logEvent("download_finished", mapOf("result" to result.analyticsName))
                },
            )
            return DownloadsManager(
                queue = queue,
                storage = DownloadStorage.shared,
                prefs = DownloadsPrefs(app),
                scheduler = WorkManagerScheduler(app),
                translations = { UseCaseTranslations(KoinPlatform.getKoin().get<GetAyahTranslationsUseCase>()) },
            )
        }
    }
}

/** The size bucket of `download_started` (no free text, no exact size). */
internal fun sizeMbBucket(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        mb < 1 -> "lt_1"
        mb < 10 -> "1_10"
        mb < 50 -> "10_50"
        mb < 100 -> "50_100"
        mb < 500 -> "100_500"
        else -> "500_plus"
    }
}
