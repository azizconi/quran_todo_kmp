package tj.app.quran_todo.ui.downloads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import tj.app.quran_todo.common.analytics.AppTelemetry
import tj.app.quran_todo.common.audio.AudioFetch
import tj.app.quran_todo.common.audio.DownloadStorage
import tj.app.quran_todo.common.audio.Reciters
import tj.app.quran_todo.data.translation.TranslationEdition
import tj.app.quran_todo.domain.repository.QuranReferenceRepository
import tj.app.quran_todo.domain.use_case.GetAyahTranslationsUseCase
import tj.app.quran_todo.ios.AppFolders
import tj.app.quran_todo.ios.File
import tj.app.quran_todo.ui.mushaf.data.MushafPageStore
import tj.app.quran_todo.ui.platform.SyncLock
import tj.app.quran_todo.ui.platform.withLock

/**
 * The downloads of iOS (the counterpart of `DownloadsManager` + `WorkManagerScheduler` + `DownloadWorker` of Android): the same queue
 * file, the same [DownloadRunner]; the audio goes through the background `URLSession` of [BackgroundFetch] (it continues while the app
 * is suspended), the translations and the mushaf pages through the foreground requests of their own stores (small, the coroutine
 * runner is enough). A run that finds no network waits and tries again ([RETRY_SECONDS]); the app tries again at every foreground
 * ([resumeIfNeeded]); the last attempt marks the request failed so the screen offers "Retry".
 */
internal class IosDownloadsManager(
    override val queue: DownloadQueue,
    override val storage: DownloadStorage,
    override val prefs: DownloadsPrefs,
    private val translations: () -> TranslationSource,
    private val fetch: AudioFetch,
    private val timings: () -> TimingsSource = { IosDownloadsHooks.timings },
    override val pages: MushafPageStore = MushafPageStore.shared,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : DownloadsService {
    private val lock = SyncLock()
    private var running = false
    private val _phase = MutableStateFlow(WorkPhase.NONE)

    override val phase: StateFlow<WorkPhase> = _phase.asStateFlow()

    override fun enqueue(request: DownloadRequest, estimatedBytes: Long): Boolean {
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

    override fun enqueueTranslation(edition: TranslationEdition, title: String): Boolean = enqueue(
        DownloadRequest(
            id = DownloadRequest.translationId(edition.key),
            kind = DownloadKind.TRANSLATION,
            title = title,
            reciterId = Reciters.default.id,
            segments = emptyList(),
            edition = edition.key,
        ),
        estimatedBytes = edition.wireBytes,
    )

    override fun pauseAll() {
        queue.pause()
    }

    override fun resume() {
        queue.resume()
        schedule()
    }

    override fun cancel(requestId: String) {
        queue.cancel(requestId)
    }

    override fun cancelAll() {
        queue.cancelAll()
    }

    override fun retry() {
        if (queue.retryFailed()) schedule()
    }

    override fun onNetworkChoiceChanged() {
        if (queue.state.value.hasWork) schedule()
    }

    override fun delete(keys: Collection<String>): Long {
        val freed = storage.delete(keys)
        AppTelemetry.logEvent("downloads_deleted")
        return freed
    }

    override fun deletePages(pageNumbers: Collection<Int>): Long {
        val freed = pages.deletePinned(pageNumbers)
        AppTelemetry.logEvent("downloads_deleted")
        return freed
    }

    override fun deleteAll(): Long {
        val freed = storage.deleteAll()
        pages.changed()
        AppTelemetry.logEvent("downloads_deleted")
        return freed
    }

    /** The app started or came to the foreground: work that was left in the queue goes on (a restart, a run that waited for the network). */
    fun resumeIfNeeded() {
        if (queue.state.value.hasWork) schedule()
    }

    private fun schedule() {
        val start = lock.withLock {
            if (running) false else {
                running = true
                true
            }
        }
        if (!start) return
        _phase.value = WorkPhase.RUNNING
        scope.launch { loop() }
    }

    private suspend fun loop() {
        var attempt = 0
        while (true) {
            _phase.value = WorkPhase.RUNNING
            val final = attempt >= MAX_ATTEMPTS - 1
            val outcome = try {
                DownloadRunner(queue, storage, fetch, translations(), timings = timings(), pages = pages).run(final)
            } catch (error: Exception) {
                AppTelemetry.logError(throwable = error, context = "download_run_failed")
                RunOutcome.FINISHED
            }
            if (outcome == RunOutcome.NO_NETWORK) {
                _phase.value = WorkPhase.WAITING
                delay(RETRY_SECONDS * 1_000L * (attempt + 1))
                attempt++
                if (!queue.state.value.hasWork) break
                continue
            }
            attempt = 0
            val again = lock.withLock {
                if (outcome != RunOutcome.PAUSED && queue.state.value.hasWork) {
                    true
                } else {
                    running = false
                    false
                }
            }
            if (!again) break
        }
        lock.withLock { running = false }
        _phase.value = WorkPhase.NONE
    }

    companion object {
        const val MAX_ATTEMPTS = 4
        const val RETRY_SECONDS = 15L

        fun create(prefs: DownloadsPrefs): IosDownloadsManager {
            val queue = DownloadQueue(
                store = FileQueueStore(File(AppFolders.noBackup("downloads_state"), "queue.json")),
                onFinished = { _, result ->
                    AppTelemetry.logEvent("download_finished", mapOf("result" to result.analyticsName))
                },
            )
            return IosDownloadsManager(
                queue = queue,
                storage = DownloadStorage.shared,
                prefs = prefs,
                translations = {
                    val koin = KoinPlatform.getKoin()
                    UseCaseTranslations(koin.get<GetAyahTranslationsUseCase>(), koin.get<QuranReferenceRepository>())
                },
                fetch = { url, target -> BackgroundFetch.fetch(url, target, cellular = !prefs.wifiOnly.value) },
            )
        }
    }
}

/** The part of the downloads that another piece of iOS code may plug in. */
object IosDownloadsHooks {
    /** Pins the word timings of a surah next to its audio; the playback code sets it when it has the store of timings. */
    internal var timings: TimingsSource = TimingsSource { _, _ -> true }

    fun setTimingsPinner(pin: suspend (recitationId: Int, surah: Int) -> Boolean) {
        timings = TimingsSource { recitationId, surah -> pin(recitationId, surah) }
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
