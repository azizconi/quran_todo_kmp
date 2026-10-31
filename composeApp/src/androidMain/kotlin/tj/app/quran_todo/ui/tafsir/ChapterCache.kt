package tj.app.quran_todo.ui.tafsir

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.audio.CacheEntry
import tj.app.quran_todo.common.audio.selectEvictions
import tj.app.quran_todo.data.translation.FailureThrottle

/**
 * Where the text of one surah (a commentary, the meanings of the words) comes from, in this order, the same order as the pages of the
 * mushaf (`MushafPageStore`):
 *  1. memory: the last [memoryEntries] surahs that were read;
 *  2. the disk: one compact JSON file per [key] in [directory], the least recently used file goes first once the folder is over
 *     [limitBytes];
 *  3. the network: [load] is given the fetch and writes what it returns to the disk.
 *
 * A file is written as `*.part` and renamed, so a file that is there is whole. Nothing here shows an error: [load] is null for a surah
 * that is neither on the phone nor reachable. A request that failed is not repeated for [FailureThrottle.DEFAULT_WINDOW_MS] (30 s)
 * unless the person taps "Retry" (`force`).
 *
 * @param decode reads a file; null for a damaged copy or one of another version, which is then deleted and fetched again.
 */
internal class ChapterCache<V : Any>(
    private val directory: File,
    private val limitBytes: Long,
    private val memoryEntries: Int,
    private val encode: (V) -> String,
    private val decode: (key: String, text: String) -> V?,
    private val throttle: FailureThrottle = FailureThrottle(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    // The most recently used entry is the last one.
    private val memory = LinkedHashMap<String, V>()
    private val mutexGuard = Mutex()
    private val keyMutexes = HashMap<String, Mutex>()

    init {
        directory.mkdirs()
    }

    private fun fileOf(key: String) = File(directory, "$key.json")

    private fun recall(key: String): V? = synchronized(memory) {
        val found = memory.remove(key) ?: return@synchronized null
        memory[key] = found
        found
    }

    private fun remember(key: String, value: V) = synchronized(memory) {
        memory.remove(key)
        memory[key] = value
        while (memory.size > memoryEntries) memory.remove(memory.keys.first())
    }

    /** The surah from memory or the disk; never the network. */
    fun local(key: String): V? {
        recall(key)?.let { return it }
        val file = fileOf(key)
        if (!file.isFile) return null
        val decoded = try {
            decode(key, file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
        if (decoded == null) {
            // Damaged or of another version: gone, it is fetched again.
            file.delete()
            return null
        }
        file.setLastModified(now())
        remember(key, decoded)
        return decoded
    }

    /** The surah: from the device, else from [fetch] (written to the disk). Null when neither works. */
    suspend fun load(key: String, force: Boolean = false, fetch: suspend () -> V?): V? = withContext(Dispatchers.IO) {
        local(key)?.let { return@withContext it }
        if (force) throttle.clear(key) else if (throttle.isBlocked(key)) return@withContext null
        val mutex = mutexGuard.withLock { keyMutexes.getOrPut(key) { Mutex() } }
        mutex.withLock {
            local(key)?.let { return@withLock it }
            val fetched = try {
                fetch()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (fetched == null) {
                throttle.markFailed(key)
                null
            } else {
                throttle.clear(key)
                write(key, fetched)
                remember(key, fetched)
                evict(keep = fileOf(key).name)
                fetched
            }
        }
    }

    private fun write(key: String, value: V) {
        directory.mkdirs()
        val target = fileOf(key)
        val part = File(directory, target.name + PART_SUFFIX)
        try {
            part.writeText(encode(value), Charsets.UTF_8)
            target.delete()
            if (part.renameTo(target)) target.setLastModified(now())
        } catch (_: Exception) {
            // The disk is full or gone: the surah stays in memory, the next start fetches it again.
        } finally {
            part.delete()
        }
    }

    /** Bytes the files take on disk. */
    fun bytes(): Long = files().sumOf { it.length() }

    /** Deletes every file; returns the bytes that were freed. */
    fun clear(): Long {
        var freed = 0L
        for (file in files()) {
            val size = file.length()
            if (file.delete()) freed += size
        }
        synchronized(memory) { memory.clear() }
        return freed
    }

    private fun files(): List<File> =
        directory.listFiles { file -> file.isFile && file.name.endsWith(".json") }?.toList().orEmpty()

    private fun evict(keep: String) {
        val files = files()
        val entries = files.map { CacheEntry(it.name, it.length(), it.lastModified()) }
        val doomed = selectEvictions(entries, limitBytes, limitBytes / 100 * 90, setOf(keep)).map { it.name }.toSet()
        files.filter { it.name in doomed }.forEach { it.delete() }
        synchronized(memory) {
            memory.keys.toList().forEach { key -> if (fileOf(key).name in doomed) memory.remove(key) }
        }
    }

    private companion object {
        const val PART_SUFFIX = ".part"
    }
}
