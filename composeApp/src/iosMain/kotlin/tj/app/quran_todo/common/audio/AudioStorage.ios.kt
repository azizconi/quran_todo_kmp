package tj.app.quran_todo.common.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.downloadTaskWithURL
import tj.app.quran_todo.ios.AppFolders
import tj.app.quran_todo.ios.File
import tj.app.quran_todo.ios.nowMillis
import tj.app.quran_todo.ui.downloads.DownloadedFiles
import tj.app.quran_todo.ui.downloads.ListeningCache
import tj.app.quran_todo.ui.platform.SyncLock
import tj.app.quran_todo.ui.platform.withLock
import kotlin.coroutines.resume

// The iOS side of the audio files: the same three layers as Android (downloads, then the listening cache, then the network).
// Ported from AudioFileStore / DownloadStorage / AudioCacheStorage of androidMain onto the File of IosFile.kt.

/** Writes the answer of [url] into [target]; true only when the whole file arrived (status 200). */
internal typealias AudioFetch = suspend (url: String, target: File) -> Boolean

/** One plain foreground session for every small request of the stores (audio files, layouts, fonts, timings, commentary). */
internal object IosHttp {
    val session: NSURLSession by lazy {
        val configuration = NSURLSessionConfiguration.defaultSessionConfiguration
        configuration.timeoutIntervalForRequest = 30.0
        configuration.timeoutIntervalForResource = 120.0
        configuration.requestCachePolicy = NSURLRequestReloadIgnoringLocalCacheData
        configuration.waitsForConnectivity = false
        NSURLSession.sessionWithConfiguration(configuration)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal suspend fun httpAudioFetch(url: String, target: File): Boolean = suspendCancellableCoroutine { continuation ->
    val nsUrl = NSURL.URLWithString(url)
    if (nsUrl == null) {
        continuation.resume(false)
        return@suspendCancellableCoroutine
    }
    val task = IosHttp.session.downloadTaskWithURL(nsUrl) { location, response, error ->
        var ok = false
        val status = (response as? NSHTTPURLResponse)?.statusCode
        val path = location?.path
        if (error == null && status == 200L && path != null) {
            target.parentFile?.mkdirs()
            ok = File(path).renameTo(target)
        }
        if (continuation.isActive) continuation.resume(ok)
    }
    continuation.invokeOnCancellation { task.cancel() }
    task.resume()
}

/** One cached file as the eviction rule sees it. */
internal data class CacheEntry(val name: String, val sizeBytes: Long, val lastUsedMs: Long)

/** The least recently used entries to delete so that the cache shrinks to [targetBytes] once it is over [limitBytes]. */
internal fun selectEvictions(
    entries: List<CacheEntry>,
    limitBytes: Long,
    targetBytes: Long = limitBytes,
    keep: Set<String> = emptySet(),
): List<CacheEntry> {
    var total = entries.sumOf { it.sizeBytes }
    if (total <= limitBytes) return emptyList()
    val evicted = ArrayList<CacheEntry>()
    for (entry in entries.filter { it.name !in keep }.sortedWith(compareBy({ it.lastUsedMs }, { it.name }))) {
        if (total <= targetBytes) break
        evicted += entry
        total -= entry.sizeBytes
    }
    return evicted
}

/**
 * The audio the person asked to download: one `.mp3` per ayah and reciter in Application Support (excluded from the iCloud backup),
 * named like the files of the cache. Never evicted; see the Android class of the same name.
 */
internal class DownloadStorage(val directory: File) : DownloadedFiles {
    private val _revision = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = _revision.asStateFlow()

    init {
        if (!directory.exists()) directory.mkdirs()
        directory.excludeFromBackup()
    }

    fun fileFor(key: String): File = File(directory, "$key.mp3")

    fun temporaryFileFor(key: String): File = File(directory, "$key.mp3.download")

    fun contains(key: String): Boolean = isValid(fileFor(key))

    fun existing(key: String): File? = fileFor(key).takeIf(::isValid)

    /** Moves the finished [temporary] file in place of [key]'s file. False when the file is too small to be audio or cannot be moved. */
    fun commit(key: String, temporary: File): Boolean {
        if (!isValid(temporary)) {
            temporary.delete()
            return false
        }
        val target = fileFor(key)
        target.delete()
        if (!temporary.renameTo(target)) {
            temporary.delete()
            return false
        }
        changed()
        return true
    }

    override fun snapshot(): Map<String, Long> {
        val files = directory.listFiles { it.name.endsWith(SUFFIX) } ?: return emptyMap()
        val result = HashMap<String, Long>(files.size * 2)
        for (file in files) {
            val size = file.length()
            if (size >= MIN_VALID_BYTES) result[file.name.removeSuffix(SUFFIX)] = size
        }
        return result
    }

    fun totalBytes(): Long = snapshot().values.sum()

    fun delete(keys: Collection<String>): Long {
        var freed = 0L
        for (key in keys) {
            val file = fileFor(key)
            val size = if (file.exists()) file.length() else 0L
            if (file.delete()) freed += size
            temporaryFileFor(key).delete()
        }
        changed()
        return freed
    }

    fun deleteAll(): Long {
        var freed = 0L
        directory.listFiles()?.forEach { file ->
            val size = file.length()
            if (file.isFile() && file.delete()) freed += size
        }
        changed()
        return freed
    }

    fun deleteUnfinished() {
        directory.listFiles { it.name.endsWith(TEMPORARY_SUFFIX) }?.forEach { it.delete() }
    }

    fun changed() {
        _revision.value = _revision.value + 1
    }

    private fun isValid(file: File): Boolean = file.exists() && file.length() >= MIN_VALID_BYTES

    companion object {
        const val SUFFIX = ".mp3"
        const val TEMPORARY_SUFFIX = ".mp3.download"
        const val MIN_VALID_BYTES = 512L

        val shared: DownloadStorage by lazy { DownloadStorage(File(AppFolders.applicationSupport, "downloads")) }
    }
}

/** The 1 GB listening cache in Caches, least recently used first; see the Android class of the same name. */
internal class AudioCacheStorage(
    val directory: File,
    private val limitBytes: Long = DEFAULT_LIMIT_BYTES,
    private val now: () -> Long = ::nowMillis,
) : ListeningCache {
    private val lock = SyncLock()
    private var knownBytes = -1L

    init {
        if (!directory.exists()) directory.mkdirs()
    }

    fun fileFor(cacheKey: String): File = File(directory, "$cacheKey.mp3")

    fun temporaryFileFor(cacheKey: String): File = File(directory, "$cacheKey.mp3.download")

    fun markUsed(file: File) {
        file.setLastModified(now())
    }

    fun recordAdded(file: File) = lock.withLock {
        val total = (if (knownBytes < 0) scan() else knownBytes) + file.length()
        knownBytes = total
        file.setLastModified(now())
        if (total > limitBytes) evict(keep = setOf(file.name))
    }

    fun invalidateTotals() = lock.withLock { knownBytes = -1 }

    override fun totalBytes(): Long = lock.withLock { scan().also { knownBytes = it } }

    override fun clear(): Long = lock.withLock {
        var freed = 0L
        directory.listFiles()?.forEach { file ->
            val size = file.length()
            if (file.delete()) freed += size
        }
        knownBytes = 0
        freed
    }

    private fun scan(): Long = audioFiles().sumOf { it.length() }

    private fun audioFiles(): List<File> = directory.listFiles { it.isFile() && it.name.endsWith(".mp3") }.orEmpty()

    private fun evict(keep: Set<String>) {
        val files = audioFiles()
        val entries = files.map { CacheEntry(it.name, it.length(), it.lastModified()) }
        val target = limitBytes / 100 * EVICTION_TARGET_PERCENT
        val doomed = selectEvictions(entries, limitBytes, target, keep).map { it.name }.toSet()
        var remaining = entries.sumOf { it.sizeBytes }
        files.filter { it.name in doomed }.forEach { file ->
            val size = file.length()
            if (file.delete()) remaining -= size
        }
        knownBytes = remaining
    }

    companion object {
        const val DEFAULT_LIMIT_BYTES = 1024L * 1024L * 1024L
        const val EVICTION_TARGET_PERCENT = 90L

        val shared: AudioCacheStorage by lazy { AudioCacheStorage(File(AppFolders.caches, "offline_audio")) }
    }
}

/** Where playback finds the recording of an ayah: downloads, then the listening cache, then the network (into the cache). */
internal class AudioFileStore(
    private val downloads: DownloadStorage,
    private val cache: AudioCacheStorage,
    private val fetch: AudioFetch = ::httpAudioFetch,
) {
    fun localFile(cacheKey: String): File? {
        downloads.existing(cacheKey)?.let { return it }
        val file = cache.fileFor(cacheKey)
        if (isValid(file)) {
            cache.markUsed(file)
            return file
        }
        return null
    }

    fun isAvailable(cacheKey: String): Boolean = downloads.contains(cacheKey) || isValid(cache.fileFor(cacheKey))

    suspend fun getOrFetch(url: String, cacheKey: String): String? = withContext(Dispatchers.Default) {
        localFile(cacheKey)?.let { return@withContext it.path }
        mutexFor(cacheKey).withLock {
            localFile(cacheKey)?.let { return@withLock it.path }
            val file = cache.fileFor(cacheKey)
            file.delete()
            val temporary = cache.temporaryFileFor(cacheKey)
            temporary.delete()
            try {
                if (fetch(url, temporary) && isValid(temporary) && temporary.renameTo(file)) {
                    cache.recordAdded(file)
                    file.path
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

    fun clear(cacheKey: String) {
        if (downloads.fileFor(cacheKey).exists()) downloads.delete(listOf(cacheKey))
        cache.fileFor(cacheKey).delete()
        cache.temporaryFileFor(cacheKey).delete()
        cache.invalidateTotals()
    }

    private fun isValid(file: File): Boolean = file.exists() && file.length() >= DownloadStorage.MIN_VALID_BYTES

    companion object {
        val shared: AudioFileStore by lazy { AudioFileStore(DownloadStorage.shared, AudioCacheStorage.shared) }

        private val mutexMapGuard = Mutex()
        private val keyMutexes = mutableMapOf<String, Mutex>()

        private suspend fun mutexFor(cacheKey: String): Mutex = mutexMapGuard.withLock { keyMutexes.getOrPut(cacheKey) { Mutex() } }
    }
}
