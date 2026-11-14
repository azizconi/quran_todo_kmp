package tj.app.quran_todo.common.audio

/**
 * File access of the ayah recordings: one `.mp3` per key. The lookup order is downloads, then the listening cache (Caches, 1 GB, the
 * least recently used first), then the network ([AudioFileStore]); the downloads are kept until the person deletes them.
 */
actual class AudioCache {
    private val store = AudioFileStore.shared

    actual suspend fun getOrFetch(url: String, cacheKey: String): String? = store.getOrFetch(url, cacheKey)

    actual suspend fun prefetch(url: String, cacheKey: String): Boolean = store.prefetch(url, cacheKey)

    actual fun isCached(cacheKey: String): Boolean = store.isAvailable(cacheKey)

    actual fun clear(cacheKey: String) = store.clear(cacheKey)
}
