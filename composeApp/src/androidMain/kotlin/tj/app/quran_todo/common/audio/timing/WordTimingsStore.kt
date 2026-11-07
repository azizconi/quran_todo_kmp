package tj.app.quran_todo.common.audio.timing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.audio.AudioCacheStorage
import tj.app.quran_todo.common.audio.AudioFetch
import tj.app.quran_todo.common.audio.DownloadStorage
import tj.app.quran_todo.common.audio.httpAudioFetch
import tj.app.quran_todo.common.utils.Constants
import tj.app.quran_todo.data.translation.FailureThrottle
import java.io.File

/**
 * Where the word timings of a surah come from, in this order:
 *  1. memory: the last few surahs that were read;
 *  2. the pinned copy: the timings of a surah that "Downloads and storage" fetched together with its audio (`downloads/`, deleted
 *     only with the downloads);
 *  3. the cache: the copy that listening fetched (`offline_audio/`, next to the audio of the listening cache);
 *  4. the network: ONE request for the whole surah (`recitations/{id}/by_chapter/{surah}?fields=segments`), written to the cache.
 *
 * The file is `timings_{recitationId}_{surah}.json`, the compact copy of [WordTimingsFormat]. Nothing here ever shows an error:
 * without a copy and without network [load] is null and the reader simply has no word highlight. A request that failed is not repeated
 * for [FailureThrottle.DEFAULT_WINDOW_MS] (30 s), the way translations are; the downloads ([pin]) are not held back by that, the
 * download work has its own backoff.
 */
internal class WordTimingsStore(
    private val pinned: File,
    private val cache: File,
    private val fetch: AudioFetch = ::httpAudioFetch,
    private val baseUrl: String = Constants.QURAN_COM_API_BASE_URL,
    private val throttle: FailureThrottle = FailureThrottle(),
    private val memorySize: Int = MEMORY_SURAHS,
) {
    // The most recently used surah is the last entry.
    private val memory = LinkedHashMap<String, WordTimings>()

    private fun recall(key: String): WordTimings? = synchronized(memory) {
        val timings = memory.remove(key) ?: return@synchronized null
        memory[key] = timings
        timings
    }

    private fun remember(key: String, timings: WordTimings) = synchronized(memory) {
        memory.remove(key)
        memory[key] = timings
        while (memory.size > memorySize) memory.remove(memory.keys.first())
    }

    fun fileName(recitationId: Int, surah: Int): String = "timings_${recitationId}_$surah.json"

    fun url(recitationId: Int, surah: Int): String =
        "${baseUrl}recitations/$recitationId/by_chapter/$surah?fields=segments&per_page=300"

    /** The timings that are on the device (memory, pinned, cache), or null. Never touches the network. */
    fun local(recitationId: Int, surah: Int): WordTimings? {
        val key = fileName(recitationId, surah)
        recall(key)?.let { return it }
        for (directory in listOf(pinned, cache)) {
            val file = File(directory, key)
            if (!file.isFile) continue
            val timings = readFile(file)
            if (timings != null && timings.recitationId == recitationId && timings.surah == surah) {
                remember(key, timings)
                return timings
            }
            // Damaged or of another version: gone, it is fetched again.
            file.delete()
        }
        return null
    }

    /** The timings of [surah] for [recitationId]: from the device, else from the network (cached). Null when neither works. */
    suspend fun load(recitationId: Int, surah: Int): WordTimings? = withContext(Dispatchers.IO) {
        local(recitationId, surah)?.let { return@withContext it }
        val key = fileName(recitationId, surah)
        if (throttle.isBlocked(key)) return@withContext null
        mutexFor(key).withLock {
            // Another caller may have finished the same request while this one waited.
            local(recitationId, surah)?.let { return@withLock it }
            val timings = fetchInto(File(cache, key), recitationId, surah)
            if (timings == null) throttle.markFailed(key) else throttle.clear(key)
            timings
        }
    }

    /**
     * Makes sure the pinned copy of [surah] exists (taken from the cache when it is there, else fetched): the timings go along with
     * the audio of a download. True when the copy is there afterwards.
     */
    suspend fun pin(recitationId: Int, surah: Int): Boolean = withContext(Dispatchers.IO) {
        val key = fileName(recitationId, surah)
        val target = File(pinned, key)
        if (target.isFile && readFile(target) != null) return@withContext true
        mutexFor(key).withLock {
            if (target.isFile && readFile(target) != null) return@withLock true
            val cached = File(cache, key)
            if (cached.isFile && readFile(cached) != null) {
                return@withLock try {
                    pinned.mkdirs()
                    cached.copyTo(target, overwrite = true)
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
            }
            fetchInto(target, recitationId, surah) != null
        }
    }

    /** Downloads the answer of quran.com, turns it into the compact copy and writes it to [target]; the timings, or null. */
    private suspend fun fetchInto(target: File, recitationId: Int, surah: Int): WordTimings? {
        val directory = target.parentFile ?: return null
        directory.mkdirs()
        val raw = File(directory, target.name + RAW_SUFFIX)
        val part = File(directory, target.name + PART_SUFFIX)
        raw.delete()
        part.delete()
        return try {
            if (!fetch(url(recitationId, surah), raw)) return null
            val timings = WordTimingsFormat.parseQuranCom(raw.readText(Charsets.UTF_8), recitationId, surah) ?: return null
            part.writeText(WordTimingsFormat.encode(timings), Charsets.UTF_8)
            if (!part.renameTo(target)) return null
            remember(target.name, timings)
            timings
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            raw.delete()
            part.delete()
        }
    }

    private fun readFile(file: File): WordTimings? = try {
        WordTimingsFormat.decode(file.readText(Charsets.UTF_8))
    } catch (_: Exception) {
        null
    }

    companion object {
        /** Decoded surahs kept in memory (Al-Baqarah is a few hundred kilobytes decoded). */
        const val MEMORY_SURAHS = 4
        private const val RAW_SUFFIX = ".raw"
        private const val PART_SUFFIX = ".part"

        /** The store the app uses: pinned copies beside the downloads, the cache beside the listening cache. */
        val shared: WordTimingsStore by lazy {
            WordTimingsStore(pinned = DownloadStorage.shared.directory, cache = AudioCacheStorage.shared.directory)
        }

        private val mutexMapGuard = Mutex()
        private val keyMutexes = mutableMapOf<String, Mutex>()

        private suspend fun mutexFor(key: String): Mutex = mutexMapGuard.withLock { keyMutexes.getOrPut(key) { Mutex() } }
    }
}
