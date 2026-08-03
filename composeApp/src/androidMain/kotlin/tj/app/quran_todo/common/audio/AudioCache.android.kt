package tj.app.quran_todo.common.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.platform.AndroidContextHolder
import java.io.File
import java.io.FileOutputStream
import java.net.URL

actual class AudioCache {
    private val cacheDir: File = File(
        AndroidContextHolder.context.noBackupFilesDir,
        "offline_audio"
    ).apply {
        if (!exists()) {
            mkdirs()
        }
    }

    actual suspend fun getOrFetch(url: String, cacheKey: String): String? {
        return withContext(Dispatchers.IO) {
            mutexFor(cacheKey).withLock {
                val file = fileForKey(cacheKey)
                if (isValidCacheFile(file)) return@withLock file.absolutePath
                file.delete()

                val temporary = temporaryFileForKey(cacheKey)
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
        return isValidCacheFile(fileForKey(cacheKey))
    }

    actual fun clear(cacheKey: String) {
        fileForKey(cacheKey).delete()
        temporaryFileForKey(cacheKey).delete()
    }

    private fun fileForKey(cacheKey: String): File {
        return File(cacheDir, "$cacheKey.mp3")
    }

    private fun temporaryFileForKey(cacheKey: String): File =
        File(cacheDir, "$cacheKey.mp3.download")

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
