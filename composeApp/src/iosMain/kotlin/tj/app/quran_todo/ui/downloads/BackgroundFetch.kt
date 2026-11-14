package tj.app.quran_todo.ui.downloads

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDownloadDelegateProtocol
import platform.Foundation.NSURLSessionDownloadTask
import platform.Foundation.NSURLSessionTask
import platform.darwin.NSObject
import tj.app.quran_todo.common.audio.DownloadStorage
import tj.app.quran_todo.ios.File
import tj.app.quran_todo.ui.platform.SyncLock
import tj.app.quran_todo.ui.platform.withLock
import kotlin.coroutines.resume

/**
 * The audio of the downloads goes through one background `URLSession` (identifier [IDENTIFIER]): the transfers go on while the app is
 * suspended, and when the system wakes the app for the finished ones the same session is made again with the same identifier and
 * the delegate hears them.
 *
 * The task keeps where its file goes in `taskDescription`. A task that was started by an earlier process has no waiting coroutine
 * (it died with the process): when it finishes, the file that is a `<key>.mp3.download` of the downloads folder is committed here, so
 * the audio is not fetched twice.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal object BackgroundFetch {
    const val IDENTIFIER = "tj.app.quran-todo.downloads"

    private val lock = SyncLock()
    private val waiting = HashMap<ULong, CancellableContinuation<Boolean>>()
    private val moved = HashSet<ULong>()

    /** The completion handler the system gave to `handleEventsForBackgroundURLSession`, called when the events were delivered. */
    var eventsDone: (() -> Unit)? = null

    private val delegate = object : NSObject(), NSURLSessionDownloadDelegateProtocol {
        override fun URLSession(session: NSURLSession, downloadTask: NSURLSessionDownloadTask, didFinishDownloadingToURL: NSURL) {
            val status = (downloadTask.response as? NSHTTPURLResponse)?.statusCode
            val target = downloadTask.taskDescription?.let(::File) ?: return
            val from = didFinishDownloadingToURL.path ?: return
            if (status != 200L) return
            target.parentFile?.mkdirs()
            if (File(from).renameTo(target)) lock.withLock { moved += downloadTask.taskIdentifier }
        }

        override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
            val id = task.taskIdentifier
            val (continuation, ok) = lock.withLock {
                val wasMoved = moved.remove(id)
                waiting.remove(id) to (wasMoved && didCompleteWithError == null)
            }
            if (continuation != null) {
                if (continuation.isActive) continuation.resume(ok)
            } else if (ok) {
                adopt(task.taskDescription)
            }
        }

        override fun URLSessionDidFinishEventsForBackgroundURLSession(session: NSURLSession) {
            val done = eventsDone
            eventsDone = null
            done?.invoke()
        }
    }

    /** A file that arrived for a task nobody waits for: it belongs to the downloads when it is one of its `.mp3.download` files. */
    private fun adopt(description: String?) {
        val file = description?.let(::File) ?: return
        val storage = DownloadStorage.shared
        if (file.parentFile != storage.directory || !file.name.endsWith(DownloadStorage.TEMPORARY_SUFFIX)) return
        storage.commit(file.name.removeSuffix(DownloadStorage.TEMPORARY_SUFFIX), file)
    }

    /** The session; making it connects the delegate to the transfers of an earlier process. */
    val session: NSURLSession by lazy {
        val configuration = NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(IDENTIFIER)
        configuration.sessionSendsLaunchEvents = true
        configuration.discretionary = false
        configuration.allowsCellularAccess = true
        configuration.timeoutIntervalForResource = 24.0 * 3600.0
        NSURLSession.sessionWithConfiguration(configuration, delegate = delegate, delegateQueue = null)
    }

    /** Makes the session (so that the events of finished transfers reach the delegate); call it at the start of the app. */
    fun attach() {
        session
    }

    /** Writes the answer of [url] into [target]; true only when the whole file arrived (status 200). [cellular] false is "Wi-Fi only". */
    suspend fun fetch(url: String, target: File, cellular: Boolean): Boolean = suspendCancellableCoroutine { continuation ->
        val nsUrl = NSURL.URLWithString(url)
        if (nsUrl == null) {
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }
        val request = NSMutableURLRequest(uRL = nsUrl)
        request.setAllowsCellularAccess(cellular)
        val task = session.downloadTaskWithRequest(request)
        task.taskDescription = target.path
        lock.withLock { waiting[task.taskIdentifier] = continuation }
        continuation.invokeOnCancellation {
            lock.withLock { waiting.remove(task.taskIdentifier) }
            task.cancel()
        }
        task.resume()
    }
}
