package tj.app.quran_todo.ui.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.audio.AudioCacheStorage
import tj.app.quran_todo.common.audio.AudioFetch
import tj.app.quran_todo.common.audio.DownloadStorage
import tj.app.quran_todo.common.audio.httpAudioFetch
import tj.app.quran_todo.common.audio.timing.WordTimings
import tj.app.quran_todo.common.audio.timing.WordTimingsFormat
import tj.app.quran_todo.common.utils.Constants
import tj.app.quran_todo.data.translation.FailureThrottle
import tj.app.quran_todo.ios.File
import tj.app.quran_todo.ui.platform.SyncLock
import tj.app.quran_todo.ui.platform.withLock as withSyncLock
import tj.app.quran_todo.ui.words.WordTimingsLoader

/**
 * The iOS twin of the Android `WordTimingsStore`: where the word timings of a surah come from, in this order: memory, the pinned copy
 * (beside the downloads), the cache (beside the listening cache, under Caches), the network (ONE request for the whole surah,
 * quran.com `recitations/{id}/by_chapter/{surah}?fields=segments`, written to the cache). The file is `timings_{recitationId}_{surah}.json`.
 * Nothing here ever shows an error: without a copy and without network [load] is null and the reader has no word highlight. A request
 * that failed is not repeated for 30 s.
 */
internal class IosWordTimingsStore(
    private val pinned: File,
    private val cache: File,
    private val fetch: AudioFetch = ::httpAudioFetch,
    private val baseUrl: String = Constants.QURAN_COM_API_BASE_URL,
    private val throttle: FailureThrottle = FailureThrottle(),
    private val memorySize: Int = MEMORY_SURAHS,
) : WordTimingsLoader {
    // The most recently used surah is the last entry.
    private val memory = LinkedHashMap<String, WordTimings>()
    private val memoryLock = SyncLock()

    private fun recall(key: String): WordTimings? = memoryLock.withSyncLock {
        val timings = memory.remove(key) ?: return@withSyncLock null
        memory[key] = timings
        timings
    }

    private fun remember(key: String, timings: WordTimings) = memoryLock.withSyncLock {
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
            if (!file.isFile()) continue
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
    override suspend fun load(recitationId: Int, surah: Int): WordTimings? = withContext(Dispatchers.Default) {
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

    /** Makes sure the pinned copy of [surah] exists (from the cache when it is there, else fetched). True when the copy is there. */
    suspend fun pin(recitationId: Int, surah: Int): Boolean = withContext(Dispatchers.Default) {
        val key = fileName(recitationId, surah)
        val target = File(pinned, key)
        if (target.isFile() && readFile(target) != null) return@withContext true
        mutexFor(key).withLock {
            if (target.isFile() && readFile(target) != null) return@withLock true
            val cached = File(cache, key)
            if (cached.isFile() && readFile(cached) != null) {
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

    private suspend fun fetchInto(target: File, recitationId: Int, surah: Int): WordTimings? {
        val directory = target.parentFile ?: return null
        directory.mkdirs()
        val raw = File(directory, target.name + RAW_SUFFIX)
        val part = File(directory, target.name + PART_SUFFIX)
        raw.delete()
        part.delete()
        return try {
            if (!fetch(url(recitationId, surah), raw)) return null
            val timings = WordTimingsFormat.parseQuranCom(raw.readText(), recitationId, surah) ?: return null
            part.writeText(WordTimingsFormat.encode(timings))
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
        WordTimingsFormat.decode(file.readText())
    } catch (_: Exception) {
        null
    }

    companion object {
        const val MEMORY_SURAHS = 4
        private const val RAW_SUFFIX = ".raw"
        private const val PART_SUFFIX = ".part"

        /** The store the app uses: pinned copies beside the downloads, the cache beside the listening cache. */
        val shared: IosWordTimingsStore by lazy {
            IosWordTimingsStore(pinned = DownloadStorage.shared.directory, cache = AudioCacheStorage.shared.directory)
        }

        private val mutexMapGuard = Mutex()
        private val keyMutexes = mutableMapOf<String, Mutex>()

        private suspend fun mutexFor(key: String): Mutex = mutexMapGuard.withLock { keyMutexes.getOrPut(key) { Mutex() } }
    }
}
