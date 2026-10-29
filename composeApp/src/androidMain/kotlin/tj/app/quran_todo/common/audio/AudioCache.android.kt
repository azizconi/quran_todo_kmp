package tj.app.quran_todo.common.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * File cache of the ayah recordings, one `.mp3` per key. Bounded by [AudioCacheStorage]: 1 GB, the least
 * recently used files are evicted first, a cache hit refreshes the use time.
 */
actual class AudioCache {
    private val storage = AudioCacheStorage.shared

    actual suspend fun getOrFetch(url: String, cacheKey: String): String? {
        return withContext(Dispatchers.IO) {
            mutexFor(cacheKey).withLock {
                val file = storage.fileFor(cacheKey)
                if (isValidCacheFile(file)) {
                    storage.markUsed(file)
                    return@withLock file.absolutePath
                }
                file.delete()

                val temporary = storage.temporaryFileFor(cacheKey)
                temporary.delete()
                try {
                    val connection = URL(url).openConnection().apply {
                        connectTimeout = 15_000
                        readTimeout = 30_000
                    }
                    connection.getInputStream().use { input ->
                        FileOutputStream(temporary).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (!isValidCacheFile(temporary) || !temporary.renameTo(file)) {
                        temporary.delete()
                        null
                    } else {
                        storage.recordAdded(file)
                        file.absolutePath
                    }
                } catch (_: Exception) {
                    temporary.delete()
                    null
                }
            }
        }
    }

    actual suspend fun prefetch(url: String, cacheKey: String): Boolean {
        if (isCached(cacheKey)) return true
        return getOrFetch(url, cacheKey) != null
    }

    actual fun isCached(cacheKey: String): Boolean {
        return isValidCacheFile(storage.fileFor(cacheKey))
    }

    actual fun clear(cacheKey: String) {
        storage.fileFor(cacheKey).delete()
        storage.temporaryFileFor(cacheKey).delete()
        storage.invalidateTotals()
    }

    private fun isValidCacheFile(file: File): Boolean =
        file.exists() && file.length() >= minimumAudioBytes

    private companion object {
        const val minimumAudioBytes = 512L
        val mutexMapGuard = Mutex()
        val keyMutexes = mutableMapOf<String, Mutex>()

        suspend fun mutexFor(cacheKey: String): Mutex =
            mutexMapGuard.withLock {
                keyMutexes.getOrPut(cacheKey) { Mutex() }
            }
    }
}
