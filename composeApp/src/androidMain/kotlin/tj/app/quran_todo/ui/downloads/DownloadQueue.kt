package tj.app.quran_todo.ui.downloads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * What was left undone in a request when its pass ended: ayahs that did not come, surahs whose translation did not, and pages of the
 * mushaf that did not ([pages], task F3a; a queue file of an older version has none).
 */
@Serializable
data class RequestFailure(val requestId: String, val ayahs: Int, val translations: Int, val pages: Int = 0)

/**
 * Everything the person asked for, in order, and what became of it. This is the truth that the worker and the screen share: it is
 * stored in a file ([FileQueueStore]) so a work that starts again after the process died (or the phone restarted) carries on.
 *
 * A request leaves [requests] when it is done or cancelled. A request that ended with something missing stays, with a
 * [RequestFailure]: the worker skips it until the person taps "Retry".
 */
@Serializable
data class QueueState(
    val requests: List<DownloadRequest> = emptyList(),
    /** "Pause all": the worker stops after the file it is on and does nothing until "Resume". */
    val paused: Boolean = false,
    val failures: List<RequestFailure> = emptyList(),
) {
    /** The request the worker takes next: the first one that did not fail. */
    val active: DownloadRequest? get() = requests.firstOrNull { request -> failures.none { it.requestId == request.id } }

    /** Requests that wait behind [active]. */
    val waiting: Int
        get() = (requests.count { request -> failures.none { it.requestId == request.id } } - 1).coerceAtLeast(0)

    val failedAyahs: Int get() = failures.sumOf { it.ayahs }

    val failedTranslations: Int get() = failures.sumOf { it.translations }

    val failedPages: Int get() = failures.sumOf { it.pages }

    val hasFailures: Boolean get() = failures.isNotEmpty()

    /** Something that a running worker would do right now. */
    val hasWork: Boolean get() = !paused && active != null

    fun contains(requestId: String): Boolean = requests.any { it.id == requestId }
}

/** Where the queue lives between runs. A fake in tests. */
internal interface QueueStore {
    fun load(): QueueState

    fun save(state: QueueState)
}

/** The queue as one JSON file, written to a temporary file and renamed so a half-written file is never read. */
internal class FileQueueStore(private val file: File) : QueueStore {
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): QueueState = try {
        if (file.exists()) json.decodeFromString(QueueState.serializer(), file.readText()) else QueueState()
    } catch (_: Exception) {
        // A damaged file is the same as no queue: the files already downloaded are still there.
        QueueState()
    }

    override fun save(state: QueueState) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(json.encodeToString(QueueState.serializer(), state))
        if (!temporary.renameTo(file)) {
            file.delete()
            temporary.renameTo(file)
        }
    }
}

/** How a request ended; the analytics name goes into `download_finished`. */
internal enum class FinishResult(val analyticsName: String) {
    DONE("done"),
    FAILED("failed"),
    CANCELLED("cancelled"),
}

/**
 * The queue of requests: one list for everything that was asked, changed by the screen (add, pause, cancel, retry) and by the worker
 * (done, failed). Every change is stored at once. [onFinished] hears how each request ended.
 */
internal class DownloadQueue(
    private val store: QueueStore,
    private val onFinished: (DownloadRequest, FinishResult) -> Unit = { _, _ -> },
) {
    private val lock = Any()
    private val _state = MutableStateFlow(store.load())

    val state: StateFlow<QueueState> = _state.asStateFlow()

    /**
     * Adds [request] at the end. A request with the same id that is waiting or running is not added again (false); one that failed
     * is taken again (true): asking once more for something that failed is a retry.
     */
    fun enqueue(request: DownloadRequest): Boolean = synchronized(lock) {
        val current = _state.value
        if (current.contains(request.id)) {
            if (current.failures.none { it.requestId == request.id }) return false
            commit(current.copy(failures = current.failures.filterNot { it.requestId == request.id }))
            return true
        }
        commit(current.copy(requests = current.requests + request))
        true
    }

    fun pause() = update { it.copy(paused = true) }

    fun resume() = update { it.copy(paused = false) }

    /** Takes one request out. True when it was there. */
    fun cancel(requestId: String): Boolean = synchronized(lock) {
        val current = _state.value
        val request = current.requests.firstOrNull { it.id == requestId } ?: return false
        commit(
            current.copy(
                requests = current.requests - request,
                failures = current.failures.filterNot { it.requestId == requestId },
            ),
        )
        onFinished(request, FinishResult.CANCELLED)
        true
    }

    /** "Cancel all": the queue is empty and is not paused. What was downloaded stays. */
    fun cancelAll() = synchronized(lock) {
        val current = _state.value
        commit(QueueState())
        current.requests.forEach { onFinished(it, FinishResult.CANCELLED) }
    }

    /** The worker finished [requestId]: every ayah is there. */
    fun complete(requestId: String) = synchronized(lock) {
        val current = _state.value
        val request = current.requests.firstOrNull { it.id == requestId } ?: return@synchronized
        commit(
            current.copy(
                requests = current.requests - request,
                failures = current.failures.filterNot { it.requestId == requestId },
            ),
        )
        onFinished(request, FinishResult.DONE)
    }

    /** The worker went through [requestId] and [ayahs] ayahs, [translations] translations and [pages] pages did not come. */
    fun fail(requestId: String, ayahs: Int, translations: Int, pages: Int = 0) = synchronized(lock) {
        val current = _state.value
        val request = current.requests.firstOrNull { it.id == requestId } ?: return@synchronized
        val failure = RequestFailure(requestId, ayahs, translations, pages)
        commit(current.copy(failures = current.failures.filterNot { it.requestId == requestId } + failure))
        onFinished(request, FinishResult.FAILED)
    }

    /** "Retry": the failed requests are taken again. True when there was something to retry. */
    fun retryFailed(): Boolean = synchronized(lock) {
        val current = _state.value
        if (current.failures.isEmpty()) return false
        commit(current.copy(failures = emptyList(), paused = false))
        true
    }

    private fun update(change: (QueueState) -> QueueState) = synchronized(lock) {
        val next = change(_state.value)
        if (next != _state.value) commit(next)
    }

    private fun commit(next: QueueState) {
        store.save(next)
        _state.value = next
    }
}
