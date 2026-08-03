package tj.app.quran_todo.common.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataTaskWithURL
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
actual class AudioCache {
    private val fileManager = NSFileManager.defaultManager
    private val cacheDir = resolveCacheDir()

    actual suspend fun getOrFetch(url: String, cacheKey: String): String? {
        return withContext(Dispatchers.Default) {
            mutexFor(cacheKey).withLock {
                val path = filePathForKey(cacheKey)
                if (isValidCacheFile(path)) return@withLock path
                fileManager.removeItemAtPath(path, error = null)

                val nsUrl = NSURL.URLWithString(url) ?: return@withLock null
                val data = downloadData(nsUrl) ?: return@withLock null
                if (data.length.toLong() < minimumAudioBytes) return@withLock null

                val temporaryPath = temporaryFilePathForKey(cacheKey)
                fileManager.removeItemAtPath(temporaryPath, error = null)
                val created = fileManager.createFileAtPath(
                    temporaryPath,
                    contents = data,
                    attributes = null
                )
                if (!created) return@withLock null

                val moved = fileManager.moveItemAtPath(
                    temporaryPath,
                    toPath = path,
                    error = null
                )
                if (moved) {
                    path
                } else {
                    fileManager.removeItemAtPath(temporaryPath, error = null)
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
        return isValidCacheFile(filePathForKey(cacheKey))
    }

    actual fun clear(cacheKey: String) {
        val path = filePathForKey(cacheKey)
        fileManager.removeItemAtPath(path, error = null)
        fileManager.removeItemAtPath(temporaryFilePathForKey(cacheKey), error = null)
    }

    private fun filePathForKey(cacheKey: String): String =
        "$cacheDir/$cacheKey.mp3"

    private fun temporaryFilePathForKey(cacheKey: String): String =
        "$cacheDir/$cacheKey.mp3.download"

    private fun isValidCacheFile(path: String): Boolean {
        if (!fileManager.fileExistsAtPath(path)) return false
        val attributes = fileManager.attributesOfItemAtPath(path, error = null)
        val size = attributes?.get(NSFileSize) as? NSNumber
        return (size?.longLongValue ?: 0L) >= minimumAudioBytes
    }

    private fun resolveCacheDir(): String {
        val url = fileManager.URLForDirectory(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null
        )
        val base = url?.path ?: ""
        val dir = "$base/offline_audio"
        if (!fileManager.fileExistsAtPath(dir)) {
            fileManager.createDirectoryAtPath(
                dir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }
        NSURL.fileURLWithPath(dir).setResourceValue(
            true,
            forKey = NSURLIsExcludedFromBackupKey,
            error = null
        )
        return dir
    }

    private suspend fun downloadData(url: NSURL): NSData? =
        suspendCancellableCoroutine { continuation ->
            val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration
            configuration.timeoutIntervalForRequest = requestTimeoutSeconds
            configuration.timeoutIntervalForResource = resourceTimeoutSeconds
            val session = NSURLSession.sessionWithConfiguration(configuration)
            val task = session.dataTaskWithURL(url) { data, _, error ->
                if (continuation.isActive) {
                    continuation.resume(if (error == null) data else null)
                }
                session.finishTasksAndInvalidate()
            }
            continuation.invokeOnCancellation {
                task.cancel()
                session.invalidateAndCancel()
            }
            task.resume()
        }

    private companion object {
        const val minimumAudioBytes = 512L
        const val requestTimeoutSeconds = 30.0
        const val resourceTimeoutSeconds = 90.0
        val mutexMapGuard = Mutex()
        val keyMutexes = mutableMapOf<String, Mutex>()

        suspend fun mutexFor(cacheKey: String): Mutex =
            mutexMapGuard.withLock {
                keyMutexes.getOrPut(cacheKey) { Mutex() }
            }
    }
}
