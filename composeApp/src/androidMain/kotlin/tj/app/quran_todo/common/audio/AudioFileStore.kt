package tj.app.quran_todo.common.audio

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Writes the answer of [url] into [target] (a temporary file); true only when the whole file arrived. */
internal typealias AudioFetch = suspend (url: String, target: File) -> Boolean

/**
 * The plain HTTP download of one recording. Cancelling the coroutine stops it between two chunks.
 *
 * The connection is only disconnected when the download did not finish: after a whole body that was read to the end, closing the
 * stream returns the connection to the keep-alive pool, so the next file of the same host skips the DNS lookup and the TLS handshake
 * (`disconnect()` would close the socket; on a slow network that made every file of a download take seconds).
 */
internal suspend fun httpAudioFetch(url: String, target: File): Boolean = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    var finished = false
    try {
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext false
        val expected = connection.contentLengthLong
        var written = 0L
        connection.inputStream.use { input ->
            FileOutputStream(target).use { output ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    written += count
                }
            }
        }
        // A body that stopped early is not a recording.
        finished = expected < 0 || written == expected
        finished
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    } finally {
        if (!finished) connection.disconnect()
    }
}

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000
private const val BUFFER_BYTES = 16 * 1024

/**
 * Where playback finds the recording of an ayah, in this order:
 *  1. the downloads ([DownloadStorage]): audio the person asked for, never evicted;
 *  2. the listening cache ([AudioCacheStorage]): 1 GB, the least recently used goes first; a hit refreshes the use time;
 *  3. the network: the file is fetched into the cache.
 *
 * Only the cache is ever written by playback; the downloads are written by the download worker and deleted by the person.
 */
internal class AudioFileStore(
    private val downloads: DownloadStorage,
    private val cache: AudioCacheStorage,
    private val fetch: AudioFetch = ::httpAudioFetch,
) {
    /** The file of [cacheKey] that is on the device (downloads first, then the cache), or null. A cache hit counts as a use. */
    fun localFile(cacheKey: String): File? {
        downloads.existing(cacheKey)?.let { return it }
        val file = cache.fileFor(cacheKey)
        if (isValid(file)) {
            cache.markUsed(file)
            return file
        }
        return null
    }

    fun isAvailable(cacheKey: String): Boolean =
        downloads.contains(cacheKey) || isValid(cache.fileFor(cacheKey))

    /** The path of the file of [cacheKey]; fetched from [url] into the cache when it is on the device nowhere. Null when that fails. */
    suspend fun getOrFetch(url: String, cacheKey: String): String? = withContext(Dispatchers.IO) {
        localFile(cacheKey)?.let { return@withContext it.absolutePath }
        mutexFor(cacheKey).withLock {
            // Somebody may have finished the same file while this call waited for the lock.
            localFile(cacheKey)?.let { return@withLock it.absolutePath }
            val file = cache.fileFor(cacheKey)
            file.delete()
            val temporary = cache.temporaryFileFor(cacheKey)
            temporary.delete()
            try {
                if (fetch(url, temporary) && isValid(temporary) && temporary.renameTo(file)) {
                    cache.recordAdded(file)
                    file.absolutePath
                } else {
                    temporary.delete()
                    null
                }
            } catch (cancelled: CancellationException) {
                temporary.delete()
                throw cancelled
            } catch (_: Exception) {
                temporary.delete()
                null
            }
        }
    }

    suspend fun prefetch(url: String, cacheKey: String): Boolean {
        if (isAvailable(cacheKey)) return true
        return getOrFetch(url, cacheKey) != null
    }

    /**
     * Forgets the file of [cacheKey] everywhere: a recording that does not play is deleted so that the next attempt fetches it again
     * (a damaged download too: the ayah is then shown as not downloaded).
     */
    fun clear(cacheKey: String) {
        val hadDownload = downloads.fileFor(cacheKey).exists()
        if (hadDownload) downloads.delete(listOf(cacheKey))
        cache.fileFor(cacheKey).delete()
        cache.temporaryFileFor(cacheKey).delete()
        cache.invalidateTotals()
    }

    private fun isValid(file: File): Boolean = file.exists() && file.length() >= MIN_AUDIO_BYTES

    companion object {
        const val MIN_AUDIO_BYTES = DownloadStorage.MIN_VALID_BYTES

        /** The store the app plays from. */
        val shared: AudioFileStore by lazy { AudioFileStore(DownloadStorage.shared, AudioCacheStorage.shared) }

        private val mutexMapGuard = Mutex()
        private val keyMutexes = mutableMapOf<String, Mutex>()

        private suspend fun mutexFor(cacheKey: String): Mutex =
            mutexMapGuard.withLock { keyMutexes.getOrPut(cacheKey) { Mutex() } }
    }
}
