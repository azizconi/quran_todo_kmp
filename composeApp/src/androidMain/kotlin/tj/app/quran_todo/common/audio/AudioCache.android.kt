package tj.app.quran_todo.common.audio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tj.app.quran_todo.common.platform.AndroidContextHolder
import java.io.File
import java.io.FileOutputStream
import java.net.URL

actual class AudioCache {
    private val cacheDir: File = File(AndroidContextHolder.context.cacheDir, "audio_cache").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    actual suspend fun getOrFetch(url: String, cacheKey: String): String? {
        val file = fileForKey(cacheKey)
        if (file.exists() && file.length() > 0) return file.absolutePath
        return withContext(Dispatchers.IO) {
            try {
                URL(url).openStream().use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
                file.absolutePath
            } catch (_: Exception) {
                file.delete()
                null
            }
        }
    }

    actual suspend fun prefetch(url: String, cacheKey: String) {
        if (isCached(cacheKey)) return
        getOrFetch(url, cacheKey)
    }

    actual fun isCached(cacheKey: String): Boolean {
        val file = fileForKey(cacheKey)
        return file.exists() && file.length() > 0
    }

    actual fun clear(cacheKey: String) {
        fileForKey(cacheKey).delete()
    }

    private fun fileForKey(cacheKey: String): File {
        return File(cacheDir, "$cacheKey.mp3")
    }
}
