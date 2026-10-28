package tj.app.quran_todo.common.audio

import tj.app.quran_todo.common.platform.AndroidContextHolder
import java.io.File

/** One cached file as the eviction rule sees it. */
internal data class CacheEntry(val name: String, val sizeBytes: Long, val lastUsedMs: Long)

/**
 * Which entries to delete so that the cache shrinks to [targetBytes] once it is over [limitBytes]: the
 * least recently used first. Nothing is evicted while the total is within the limit. Entries named in
 * [keep] (the file that has just been written) are never chosen.
 */
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
 * The audio files of the cache: one `.mp3` per ayah and reciter in [directory]. The size is limited:
 * when a newly written file takes the total over [limitBytes], the least recently used files go until
 * the total is at [EVICTION_TARGET_PERCENT] of the limit (so it does not rescan on every new file).
 * Files of the offline package are evicted like any other: they are downloaded again when needed.
 *
 * "Used" is the file's modification time: it is refreshed on every cache hit by [markUsed].
 */
internal class AudioCacheStorage(
    val directory: File,
    private val limitBytes: Long = DEFAULT_LIMIT_BYTES,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()

    // Size of the cached files, -1 until the directory has been scanned once.
    private var knownBytes = -1L

    init {
        if (!directory.exists()) directory.mkdirs()
    }

    fun fileFor(cacheKey: String): File = File(directory, "$cacheKey.mp3")

    fun temporaryFileFor(cacheKey: String): File = File(directory, "$cacheKey.mp3.download")

    /** A cache hit: the file becomes the most recently used one. */
    fun markUsed(file: File) {
        file.setLastModified(now())
    }

    /** [file] has just been written: counts it and evicts when the limit is exceeded. */
    fun recordAdded(file: File) {
        synchronized(lock) {
            val total = (if (knownBytes < 0) scan() else knownBytes) + file.length()
            knownBytes = total
            file.setLastModified(now())
            if (total > limitBytes) evict(keep = setOf(file.name))
        }
    }

    /** A file was deleted outside of the eviction: the counted size is no longer reliable. */
    fun invalidateTotals() {
        synchronized(lock) { knownBytes = -1 }
    }

    /** Bytes the cached audio takes on disk. */
    fun totalBytes(): Long = synchronized(lock) { scan().also { knownBytes = it } }

    /** Deletes all cached audio (and unfinished downloads); returns the bytes that were freed. */
    fun clear(): Long = synchronized(lock) {
        var freed = 0L
        directory.listFiles()?.forEach { file ->
            val size = file.length()
            if (file.delete()) freed += size
        }
        knownBytes = 0
        freed
    }

    private fun scan(): Long = audioFiles().sumOf { it.length() }

    private fun audioFiles(): List<File> =
        directory.listFiles { file -> file.isFile && file.name.endsWith(".mp3") }?.toList().orEmpty()

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

        /** The cache of the app, in the no-backup directory (cached audio must not be restored to another device). */
        val shared: AudioCacheStorage by lazy {
            AudioCacheStorage(File(AndroidContextHolder.context.noBackupFilesDir, "offline_audio"))
        }
    }
}
