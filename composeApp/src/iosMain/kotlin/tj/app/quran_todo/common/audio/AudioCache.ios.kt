package tj.app.quran_todo.common.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfURL

@OptIn(ExperimentalForeignApi::class)
actual class AudioCache {
    private val fileManager = NSFileManager.defaultManager
    private val cacheDir = resolveCacheDir()

    actual suspend fun getOrFetch(url: String, cacheKey: String): String? {
        val path = filePathForKey(cacheKey)
        if (fileManager.fileExistsAtPath(path)) return path
        return withContext(Dispatchers.Default) {
            val nsUrl = NSURL.URLWithString(url) ?: return@withContext null
            val data = NSData.dataWithContentsOfURL(nsUrl) ?: return@withContext null
            val ok = fileManager.createFileAtPath(path, contents = data, attributes = null)
            if (ok) path else null
        }
    }

    actual suspend fun prefetch(url: String, cacheKey: String) {
        if (isCached(cacheKey)) return
        getOrFetch(url, cacheKey)
    }

    actual fun isCached(cacheKey: String): Boolean {
        return fileManager.fileExistsAtPath(filePathForKey(cacheKey))
    }

    actual fun clear(cacheKey: String) {
        val path = filePathForKey(cacheKey)
        fileManager.removeItemAtPath(path, error = null)
    }

    private fun filePathForKey(cacheKey: String): String =
        "$cacheDir/$cacheKey.mp3"

    private fun resolveCacheDir(): String {
        val url = fileManager.URLForDirectory(
            NSCachesDirectory,
            NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null
        )
        val base = url?.path ?: ""
        val dir = "$base/audio_cache"
        if (!fileManager.fileExistsAtPath(dir)) {
            fileManager.createDirectoryAtPath(
                dir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }
        return dir
    }
}
